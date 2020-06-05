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
import android.graphics.Bitmap
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
    private var mProfileUri: Uri? = null

    private fun runJS(webView: WebView, code: String, cb: (s: String) -> Unit) {
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
    abstract fun onHomePageReached(webView: WebView, uri: Uri)
    abstract fun onEventsPageReached(webView: WebView, uri: Uri)
    abstract fun onUserPageReached(webView: WebView, uri: Uri)

    // Deprecated in API level 23
    @Suppress("OverridingDeprecatedMember")
    override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
        Toast.makeText(activity, "Authentication error: $description", Toast.LENGTH_LONG)
                .show()
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)

        mLogger.info("AUTH", "Started loading $url")

        if (activity.isFinishing) {
            return
        }

        val uri = Uri.parse(url)
        if (uri.path == "/home.php") {
            onLoginSuccess(view, uri)
        }
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
        } else if (uri?.path == "/home.php") {
            onHomePageReached(view, uri)
        } else if (uri?.path == "/events/") {
            onEventsPageReached(view, uri)
        } else if (uri.path == mProfileUri?.path) {
            onUserPageReached(view, uri)
        } else {
            // 2FA flow, likely
        }
    }

    private fun findExportLink(attempts: Int, view: WebView, success: (String) -> Unit, failure: (String) -> Unit) {
        mLogger.debug("AUTH", "Looking for export link.")
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

    fun loadProfilePage(view: WebView, failure: (String) -> Unit) {
        runJS(view,
            "(function() {" +
                    "  if (link = document.querySelector('nav>a:nth-child(2)')) {" +
                    "     return link.href;" +
                    "  } else if (link = document.querySelector('div[role=\"navigation\"]>a:nth-child(2)')) {" +
                    "     return link.href;" +
                    " } else {" +
                    "    return false;" +
                    "  }" +
                    "})"
        ) { s: String ->
            if (s.isEmpty() || s == "false") {
                failure("Failed to load profile page.")
            } else {
                val fixed = s.removeSurrounding("\"")
                mProfileUri = Uri.parse(fixed)
                view.loadUrl(fixed)
            }
        }
    }

    fun loadEventsPage(view: WebView) {
        // Use a desktop user-agent to make sure we get a desktop version, otherwise who
        // knows what response we might get...
        view.settings.userAgentString = "Mozilla/5.0 (X11;Linux x86_64;rv:58.0) Gecko/20100101 Firefox/58.0"
        view.loadUrl("https://www.facebook.com/events")
    }

    fun findWebCalUri(webView: WebView, success: (String) -> Unit, failure: (String) -> Unit) {
        mLogger.debug("AUTH", "Finding webcal link...")
        findExportLink(10, webView, success, failure)
    }

    fun findUserName(webView: WebView, success: (String) -> Unit, failure: (String) -> Unit) {
        mLogger.debug("AUTH", "Finding user name")
        runJS(webView,
        "(function() {" +
                "  if (elem = document.querySelector('div>span>strong')) {" +
                "    return elem.textContent;" +
                "  } else {" +
                "    return false;" +
                "  }" +
                "})"
        ) { s: String ->
            if (s.isEmpty() || s == "false") {
                failure("Failed to find user name")
            } else {
                mLogger.debug("AUTH", "User name found.")
                success(s.removeSurrounding("\""))
            }
        }
    }

}
