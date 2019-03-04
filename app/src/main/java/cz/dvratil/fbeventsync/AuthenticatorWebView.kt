/*
    Copyright (C) 2019  Daniel Vrátil <me@dvratil.cz>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package cz.dvratil.fbeventsync

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Handler

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

abstract class AuthenticatorWebView(val activity: Activity) : WebViewClient() {

    private var mLastUri: String? = null
    private var mLogger = Logger.getInstance(activity)

    private fun runJS(webView: WebView, code: String, cb: (s: String) -> Unit)
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            class JSObject {
                @Suppress("unused")
                @JavascriptInterface
                fun callCallback(s: String) {
                    cb(s)
                }
            }

            webView.addJavascriptInterface(JSObject(), "fbeventsync")
            webView.loadUrl("javascript:(function() { fbeventsync.callCallback($code()); })();")
        } else {
            webView.evaluateJavascript(
                    "(function() { return $code(); })();"
            ) { cb(it as String) }
        }

    }

    abstract fun onLoginPageReached(webView: WebView, uri: Uri)
    abstract fun onLoginSuccess(webView: WebView, uri: Uri)
    abstract fun onEventPageReached(webView: WebView, uri: Uri)
    abstract fun onEventsPageReached(webView: WebView, uri: Uri)


    // Deprecated in API level 23
    @Suppress("OverridingDeprecatedMember")
    override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
        Toast.makeText(activity, "Authentication error: $description", Toast.LENGTH_LONG)
                .show()
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        mLogger.info("AUTH", "Loaded $url")

        if (activity.isFinishing) {
            return
        }
        if (mLastUri == url || url.endsWith('#')) { // internal navigation does not concern us
            return
        }
        mLastUri = url

        val uri = Uri.parse(url)
        if (uri?.path?.contains("/login.php") == true) {
            onLoginPageReached(view, uri)
        } else if (uri?.path == "/connect/login_success.html") {
            onLoginSuccess(view, uri)

            // Use a desktop user-agent to make sure we get a desktop version, otherwise who
            // knows what response we might get...
            view.settings.userAgentString = "Mozilla/5.0 (X11;Linux x86_64;rv:58.0) Gecko/20100101 Firefox/58.0"
            view.loadUrl("https://www.facebook.com/events")
        } else if (uri?.path == "/events/") {
            onEventsPageReached(view, uri)
        } else if (uri?.path == "/events/${AuthenticatorActivity.EXPORT_EVENT_FBID}") {
            onEventPageReached(view, uri)
        } else {
            // 2FA flow, likely
        }
    }


    fun subscribeToEvent(view: WebView, success: () -> Unit, failure: (String) -> Unit) {
        fun joinEvent() {
            mLogger.debug("AUTH", "Attempting to subscribe to Auth event.")
            runJS(view,
                    "(function() {" +
                            "  var btn = document.querySelector(\"#event_button_bar div div a\");" +
                            "  if (btn === null) {" +
                            "    if (document.querySelector(\"#admin_button_bar\")) {" +
                            "      return true;" +
                            "    }" +
                            "    return false;" +
                            "  }" +
                            "  if (btn.getAttribute(\"aria-haspopup\") === \"true\") {" +
                            "    return true;" +
                            "  }" +
                            "  var event = document.createEvent('Events');" +
                            "  event.initEvent('click', true, false);" +
                            "  btn.dispatchEvent(event);" +
                            "  return true;" +
                            "})"
            ){ s: String ->
                if (s == "false") {
                    failure("Failed to find event actions.")
                } else {
                    mLogger.debug("AUTH", "Auth event subscription request success.")
                    Handler().postDelayed({ success(); }, 200)
                }
            }
        }

        Handler().postDelayed({ joinEvent() }, 200)
    }

    private fun findExportLink(attempts: Int, view: WebView, success: (String) -> Unit, failure: (String) -> Unit) {
        mLogger.debug("AUTH","Looking for export link.")
        runJS(view,
                "(function() {" +
                        "  if (link = document.querySelector(\"a[href^='https://www.facebook.com/events/ical/upcoming']\")) {" +
                        "    return link.href;" +
                        "  } else if (link = document.querySelector(\"a[href^='https://web.facebook.com/events/ical/upcoming']\")) {" +
                        "    return link.href;" +
                        "  } else {" +
                        "    return false;" +
                        "  }" +
                        "})"
        ) { s: String ->
            if (s.isEmpty() || s == "false") {
                if (attempts == 0) {
                    failure("Timeout while waiting for calendar export link.")
                } else {
                    Handler().postDelayed({ findExportLink(attempts - 1, view, success, failure); }, 500)
                }
            } else {
                mLogger.debug("AUTH", "Export link found.")
                success(s.removeSurrounding("\""))
            }
        }
    }

    private fun findExportDialog(attempts: Int, view: WebView, success: (String) -> Unit, failure: (String) -> Unit) {
        mLogger.debug("AUTH", "Requesting export event dialog.")
        runJS(view,
                "(function() {" +
                        "  var elem = document.querySelector(\"a[ajaxify^='/ajax/events/export.php']\");" +
                        "  if (elem == null) { return false; }" +
                        "  var event = document.createEvent('Events');" +
                        "  event.initEvent('click', true, false);" +
                        "  elem.dispatchEvent(event);" +
                        "  return true;" +
                        "})"
        ) { result: String ->
            if (result == "false") {
                if (attempts == 0) {
                    failure("Failed to retrieve calendar export link.")
                } else {
                    Handler().postDelayed({ findMenuLink(attempts - 1, view, success, failure) }, 200)
                }
            } else {
                mLogger.debug("AUTH","Request for event dialog sent.")
                Handler().postDelayed({ findExportLink(10, view, success, failure) }, 500)
            }
        }
    }

    private fun findMenuLink(attempts: Int, view: WebView, success: (String) -> Unit, failure: (String) -> Unit) {
        mLogger.debug("AUTH", "Requesting event action menu.")
        runJS(view,
                "(function() {" +
                        "  function findButton(group) {" +
                        "    if (elems = document.querySelectorAll(group + \" a[role='button']\")) {" +
                        "      for (var i = 0; i < elems.length; i++) {" +
                        "        if (elems[i].innerText === '') {" +
                        "          return elems[i];" +
                        "        }" +
                        "      }" +
                        "    }" +
                        "    return null;" +
                        "  }" +
                        "  var btn = findButton(\"#admin_button_bar\");" +
                        "  if (btn === null) {" +
                        "    btn = findButton(\"#event_button_bar\");" +
                        "  }" +
                        "  if (btn === null) {" +
                        "    return false;" +
                        "  }" +
                        "  var event = document.createEvent('Events');" +
                        "  event.initEvent('click', true, false);" +
                        "  btn.dispatchEvent(event);" +
                        "  return true;" +
                        "})"
        ){ s: String ->
            if (s == "false") {
                failure("Failed to find event export menu.")
            } else {
                mLogger.debug("AUTH", "Event action menu requested.")
                Handler().postDelayed({ findExportDialog(attempts, view, success, failure); }, 200)
            }
        }
    }

    fun findExportUri(view: WebView, success: (String) -> Unit, failure: (String) -> Unit) {
        findMenuLink(10, view, success, failure)
    }

    fun unsubscribeFromEvent(webView: WebView, cb: () -> Unit) {
        fun doUnsubscribe(attempts: Int) {
            mLogger.debug("AUTH", "Unsubscribing from Auth event.")
            runJS(webView,
                    "(function() {" +
                            "  var btn = document.querySelector(\"div.uiContextualLayerPositioner.uiLayer:not(.hidden_elem) ul>li:last-child a\");" +
                            "  if (btn === null) {" +
                            "    return false;" +
                            "  }" +
                            "  var event = document.createEvent('Events');" +
                            "  event.initEvent('click', true, false);" +
                            "  btn.dispatchEvent(event);" +
                            "  return true;" +
                            "})"
            ){ s: String ->
                if (s == "true") {
                    mLogger.debug("AUTH", "Successfully unsubscribed from Auth event.")
                    cb()
                } else {
                    if (attempts == 0) {
                        mLogger.debug("AUTH", "Failed to unsubscribe from Auth event.")
                        cb()
                    } else {
                        Handler().postDelayed({ doUnsubscribe(attempts - 1) }, 100)
                    }
                }
            }
        }

        mLogger.debug("AUTH", "Requesting event subscription menu.")
        runJS(webView,
                "(function() {" +
                        "  var btn = document.querySelector(\"#event_button_bar div div a\");" +
                        "  if (btn === null) {" +
                        "    return false;" +
                        "  }" +
                        "  if (btn.getAttribute(\"aria-haspopup\") !== \"true\") {" +
                        "    return false;" +
                        "  }" +
                        "  var event = document.createEvent('Events');" +
                        "  event.initEvent('click', true, false);" +
                        "  btn.dispatchEvent(event);" +
                        "  return true;" +
                        "})"
        ){ s: String ->
            if (s == "true") {
                mLogger.debug("AUTH", "Successfully requested subscription menu.")
                Handler().postDelayed({ doUnsubscribe(10) }, 200)
            } else {
                mLogger.debug("AUTH", "Failed to request subscription menu.")
                cb()
            }
        }
    }

    fun findWebCalUri(webView: WebView, success: (String) -> Unit, failure: (String) -> Unit) {
        mLogger.debug("AUTH", "Finding webcal link...")
        findExportLink(10, webView, success, failure)
    }
}
