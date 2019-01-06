/*
    Copyright (C) 2017 - 2018  Daniel Vrátil <me@dvratil.cz>

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

import android.Manifest
import android.accounts.Account
import android.accounts.AccountAuthenticatorActivity
import android.accounts.AccountManager
import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.provider.CalendarContract
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

import com.loopj.android.http.JsonHttpResponseHandler

import org.json.JSONObject

import cz.msebera.android.httpclient.Header

class AuthenticatorActivity : AccountAuthenticatorActivity() {

    data class Cookies(val cookies: String = String(), val url: String = String())

    private lateinit var mAccountManager: AccountManager
    private var mAccessToken = String()
    private var mCookies = Cookies()
    private var mUserId = String()
    private var mKey = String()

    private lateinit var mWebView: WebView
    private lateinit var mProgressBar: ProgressBar
    private lateinit var mProgressLabel: TextView
    private lateinit var mLogger: Logger

    private fun linkExtractionFailed(s: String) {
        mLogger.debug("AUTH", "Link extraction failed: $s")
        Toast.makeText(this, "Authentication error: $s", Toast.LENGTH_LONG)
                .show()
        if (!DEBUG_WEBVIEW) {
            finish()
        }
    }

    private fun linkExtracted(s: String) {
        mLogger.debug("AUTH", "Key extraction done: $s")

        val link = Uri.parse(s)
        val key = link.getQueryParameter("key")
        if (key == null || key.isEmpty()) {
            linkExtractionFailed("Failed to parse calendar URI.")
        } else {
            mKey = key
            mProgressLabel.text = getString(R.string.auth_progress_retrieving_userinfo)
            fetchUserInfo(mAccessToken)
        }
    }

    private fun loadUrl(url: String) {
        if (DEBUG_WEBVIEW) {
            mLogger.info("AUTH", "Loading $url")
        }
        mWebView.loadUrl(url)
    }

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        setContentView(R.layout.activity_authenticator)

        val activity = this

        mLogger = Logger.getInstance(this)
        mProgressBar = findViewById(R.id.authProgressBar)
        mProgressLabel = findViewById(R.id.authProgressString)
        mWebView = findViewById(R.id.webview)
        mWebView.settings.javaScriptEnabled = true
        if (BuildConfig.DEBUG) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                WebView.setWebContentsDebuggingEnabled(true)
            }
        }

        mAccountManager = AccountManager.get(baseContext)

        mWebView.webViewClient = object : WebViewClient() {

            private var mLastUri: String? = null

            private fun runJS(code: String, cb: (s: String) -> Unit)
            {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                    class JSObject {
                        @Suppress("unused")
                        @JavascriptInterface
                        fun callCallback(s: String) {
                            cb(s)
                        }
                    }
                    mWebView.addJavascriptInterface(JSObject(), "fbeventsync")
                    loadUrl("javascript:(function() { fbeventsync.callCallback($code()); })();")
                } else {
                    mWebView.evaluateJavascript(
                            "(function() { return $code(); })();"
                    ) { cb(it as String) }
                }

            }

            // Deprecated in API level 23
            @Suppress("OverridingDeprecatedMember")
            override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                Toast.makeText(activity, "Authentication error: $description", Toast.LENGTH_LONG)
                        .show()
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (DEBUG_WEBVIEW) {
                    mLogger.info("AUTH", "Loaded $url")
                }
                if (activity.isFinishing) {
                    return
                }
                if (mLastUri == url || url.endsWith('#')) { // internal navigation does not concern us
                    return
                }
                mLastUri = url

                val uri = Uri.parse(url)
                if (uri?.path?.contains("/login.php") == true) {
                    mLogger.debug("AUTH", "Reached login.php")
                    mWebView.visibility = View.VISIBLE
                    mProgressBar.visibility = View.GONE
                    mProgressLabel.visibility = View.GONE
                } else if (uri?.path == "/connect/login_success.html") {
                    // TODO: Check if all privileges were granted
                    mLogger.debug("AUTH", "Reached login_success with token")
                    mWebView.visibility = if (DEBUG_WEBVIEW) View.VISIBLE else View.GONE
                    mProgressBar.visibility = if (DEBUG_WEBVIEW) View.GONE else View.VISIBLE
                    mProgressLabel.visibility = if (DEBUG_WEBVIEW) View.GONE else View.VISIBLE
                    mProgressLabel.text = getString(R.string.auth_progress_retrieving_calendars)

                    val token = Uri.parse("http://localhost/?${uri.fragment}")?.getQueryParameter("access_token")
                    if (token == null) {
                        mLogger.error("AUTH", "Failed to extract access_token, the URI was '$uri'")
                        Toast.makeText(activity, getString(R.string.auth_account_creation_error_toast), Toast.LENGTH_SHORT)
                                .show()
                        if (!DEBUG_WEBVIEW) {
                            finish()
                        }
                        return
                    }
                    mCookies = Cookies(CookieManager.getInstance().getCookie(url), url)
                    mAccessToken = token

                    var cuser = mCookies.cookies
                            .split(';')
                            .find { it.trim().startsWith("c_user=") }
                            ?.split("=")
                            ?.getOrNull(1)
                    if (cuser == null) {
                        mLogger.error("AUTH", "Failed to extract userId from cookies string")
                        Toast.makeText(activity, getString(R.string.auth_account_creation_error_toast), Toast.LENGTH_SHORT)
                                .show()
                        if (!DEBUG_WEBVIEW) {
                            finish()
                        }
                        return
                    }

                    mUserId = cuser

                    // Use a desktop user-agent to make sure we get a desktop version, otherwise who
                    // knows what response we might get...
                    mWebView.settings.userAgentString = "Mozilla/5.0 (X11;Linux x86_64;rv:58.0) Gecko/20100101 Firefox/58.0"
                    loadUrl("https://www.facebook.com/events/$EXPORT_EVENT_FBID")
                } else if (uri?.path == "/events/$EXPORT_EVENT_FBID") {
                    fun findExportLink(attempts: Int) {
                        runJS("(function() {" +
                                "  if (link = document.querySelector(\"a[href^='https://www.facebook.com/events/ical/upcoming']\")) {" +
                                "    return link.href;" +
                                "  } else {" +
                                "    return false;" +
                                "  }" +
                                "})"
                        ) { s: String ->
                            if (s.isEmpty() || s == "false") {
                                if (attempts == 0) {
                                    linkExtractionFailed("Timeout while waiting for calendar export link.")
                                } else {
                                    Handler().postDelayed({ findExportLink(attempts - 1); }, 500)
                                }
                            } else {
                                linkExtracted(s.removeSurrounding("\""))
                            }
                        }
                    }

                    fun findExportDialog(attempts: Int) {
                        runJS("(function() {" +
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
                                    linkExtractionFailed("Failed to retrieve calendar export link.")
                                } else {
                                    Handler().postDelayed({ findExportDialog(attempts - 1) }, 200)
                                }
                            } else {
                                Handler().postDelayed({ findExportLink(10) }, 500)
                            }
                        }
                    }

                    fun findMenuLink() {
                        runJS("(function() {" +
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
                                linkExtractionFailed("Failed to find event export menu.");
                            } else {
                                Handler().postDelayed({ findExportDialog(10); }, 200)
                            }
                        }
                    }

                    Handler().postDelayed({ findMenuLink() }, 200)
                } else {
                    // likely 2FA Auth flow, ignore
                }
            }
        }

        // Once the check is finished it will call startLogin()
        checkInternetPermission()
    }

    private fun startLogin() {
        if (isFinishing) {
            return
        }

        // Try to restore cookies from account settings. If KEY_ACCOUNT_NAME wasn't set or points
        // to an invalid account no cookies will be set user will be prompted with Facebook login
        val accountName = intent.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (accountName != null) {
            val account = mAccountManager.getAccountsByType(getString(R.string.account_type)).find { it.name == accountName }
            if (account != null) {
                val cookies = mAccountManager.getUserData(account, Authenticator.FB_COOKIES)
                if (cookies != null) {
                    CookieManager.getInstance().setCookie("https://www.facebook.com", cookies)
                }
            }
        }

        loadUrl(Uri.Builder()
                .scheme("https")
                .authority("www.facebook.com")
                .path("/v2.9/dialog/oauth")
                .appendQueryParameter("client_id", getString(R.string.facebook_app_id))
                .appendQueryParameter("redirect_uri", "https://www.facebook.com/connect/login_success.html")
                .appendQueryParameter("response_type", "token")
                .appendQueryParameter("scopes", TOKEN_SCOPE)
                .build().toString())
    }

    private fun fetchUserInfo(accessToken: String) {
        if (isFinishing) {
            return
        }

        // Once we've reached this point we no longer need the webview, so reset cookies
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().removeAllCookies(null)
        } else {
            CookieManager.getInstance().removeAllCookie()
        }

        val activity = this
        Graph.me(accessToken, object : JsonHttpResponseHandler() {
            override fun onSuccess(statusCode: Int, headers: Array<Header>?, response: JSONObject?) {
                if (activity.isFinishing) {
                    return
                }
                if (response != null) {
                    try {
                        val accountName = response.getString("name")
                        createAccount(accessToken, accountName)
                    } catch (e: org.json.JSONException) {
                        mLogger.error("AUTH", "JSON exception: ${e.message}")
                        Toast.makeText(activity, getString(R.string.auth_account_creation_error_toast),
                                Toast.LENGTH_SHORT)
                                .show()
                        activity.finish()
                    }
                } else {
                    mLogger.error("AUTH", "Empty fetchUserInfo response")
                    Toast.makeText(activity, getString(R.string.auth_account_creation_error_toast),
                            Toast.LENGTH_SHORT)
                            .show()
                    activity.finish()
                }

            }

            override fun onFailure(statusCode: Int, headers: Array<Header>?, throwable: Throwable, errorResponse: JSONObject?) {
                if (activity.isFinishing) {
                    return
                }
                if (errorResponse != null) {
                    try {
                        val err = errorResponse.getJSONObject("error")
                        val errCode = err.getInt("code")
                        if (errCode == 4) {
                            mLogger.error("AUTH", "FetchUserInfo: rate limiting error")
                            Toast.makeText(activity, getString(R.string.auth_rate_limiting_toast), Toast.LENGTH_SHORT)
                                    .show()
                            activity.finish()
                            return
                        }
                    } catch (e: org.json.JSONException) {
                        // pass
                    }

                    mLogger.error("AUTH", "FetchUserInfo failure: $errorResponse")
                } else {
                    mLogger.error("AUTH", "FetchUserInfo failure: unknown error")
                }
                Toast.makeText(activity, getString(R.string.auth_account_creation_error_toast), Toast.LENGTH_SHORT)
                        .show()
                activity.finish()
            }
        })
    }

    private fun createAccount(accessToken: String, accountName: String) {
        if (isFinishing) {
            return
        }
        mLogger.debug("AUTH", "Creating account $accountName")
        val intent = intent
        val account = Account(accountName, intent.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE))
        if (intent.getBooleanExtra(ARG_IS_ADDING_NEW_ACCOUNT, false)) {
            mAccountManager.addAccountExplicitly(account, null, null)

            ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 1)
            ContentResolver.setSyncAutomatically(account, CalendarContract.AUTHORITY, true)
        }

        if (mKey.isEmpty() || mUserId.isEmpty()) {
            mLogger.error("AUTH", "Failed to retrieve UID ($mUserId) or KEY ($mKey)")
            Toast.makeText(this, getString(R.string.auth_calendar_uri_error_toast), Toast.LENGTH_SHORT)
                    .show()
            if (!DEBUG_WEBVIEW) {
                finish()
            }
            return
        }
        mAccountManager.setUserData(account, Authenticator.DATA_BDAY_URI, null) // clear the legacy storage
        mAccountManager.setAuthToken(account, Authenticator.FB_OAUTH_TOKEN, accessToken)
        mAccountManager.setAuthToken(account, Authenticator.FB_KEY_TOKEN, mKey)
        mAccountManager.setAuthToken(account, Authenticator.FB_UID_TOKEN, mUserId)
        mAccountManager.setUserData(account, Authenticator.FB_COOKIES, mCookies.cookies)

        val result = Intent()
        result.putExtra(AccountManager.KEY_ACCOUNT_NAME, accountName)
        result.putExtra(AccountManager.KEY_ACCOUNT_TYPE, intent.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE))
        val authTokenType = intent.getStringExtra(AuthenticatorActivity.ARG_AUTH_TOKEN_TYPE)
        if (authTokenType != null && authTokenType == Authenticator.FB_KEY_TOKEN) {
            result.putExtra(AccountManager.KEY_AUTHTOKEN, mKey)
        } else if (authTokenType != null && authTokenType == Authenticator.FB_UID_TOKEN) {
            result.putExtra(AccountManager.KEY_AUTHTOKEN, mUserId)
        } else {
            result.putExtra(AccountManager.KEY_AUTHTOKEN, accessToken)
        }

        setAccountAuthenticatorResult(result.extras)
        setResult(Activity.RESULT_OK, result)

        CalendarSyncAdapter.updateSync(this, account)

        Toast.makeText(this, R.string.auth_account_creation_success_toast, Toast.LENGTH_SHORT)
                .show()
        if (!DEBUG_WEBVIEW) {
            finish()
        }
    }

    private fun checkInternetPermission() {
        val permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.INTERNET),
                    PERMISSION_REQUEST_INTERNET)
        } else {
            startLogin()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_INTERNET) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLogin()
            } else {
                // TODO: What to do when we don't get the permissions?
                if (!DEBUG_WEBVIEW) {
                    finish()
                }
            }
        }
    }

    companion object {
        const val ARG_AUTH_TOKEN_TYPE = "cz.dvratil.fbeventsync.AuthenticatorActivity.AuthType"
        const val ARG_IS_ADDING_NEW_ACCOUNT = "cz.dvratil.fbeventsync.AuthenticatorActivity.IsAddingNewAccount"

        const val AUTH_NOTIFICATION_ID = 1
        const val AUTH_NOTIFICATION_CHANNEL_ID = "FBEventSync_Auth_NtfChannel"

        const val TOKEN_SCOPE = "me"

        private const val PERMISSION_REQUEST_INTERNET = 1

        const val EXPORT_EVENT_FBID = "258046665055853"

        private const val DEBUG_WEBVIEW = false
    }
}
