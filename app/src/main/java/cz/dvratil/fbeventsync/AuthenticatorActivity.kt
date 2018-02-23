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
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.view.View
import android.view.Window
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

import com.loopj.android.http.JsonHttpResponseHandler

import org.json.JSONObject

import cz.msebera.android.httpclient.Header

class AuthenticatorActivity : AccountAuthenticatorActivity() {

    private var mAccountManager: AccountManager? = null
    private var mAuthTokenType: String? = null
    private var mAccessToken: String? = null
    private var mBDayCalendar: String? = null

    private var mWebView: WebView? = null
    private var mProgressBar: ProgressBar? = null
    private var mProgressLabel: TextView? = null

    private var mLogger: Logger? = null

    protected fun onBirthdayLinkExtracted(s: String) {
        mLogger!!.debug("AUTH", "Found iCal URL")

        if (!s.startsWith("\"webcal")) {
            mLogger!!.debug("AUTH", "Failed to find iCal, debug: %s", s)
            Toast.makeText(this, "Authentication error: failed to retrieve birthday calendar", Toast.LENGTH_LONG)
                    .show()
            finish()
            return
        }

        // Remove opening and trailing quotes that come from JavaScript
        mBDayCalendar = s.substring(1, s.length - 1)
        mProgressLabel!!.text = getString(R.string.auth_progress_retrieving_userinfo)
        fetchUserInfo(mAccessToken)
    }

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        window.requestFeature(Window.FEATURE_PROGRESS)
        setContentView(R.layout.activity_authenticator)

        val activity = this

        mLogger = Logger.getInstance(this)
        mProgressBar = findViewById(R.id.authProgressBar)
        mProgressLabel = findViewById(R.id.authProgressString)
        mWebView = findViewById(R.id.webview)
        mWebView!!.settings.javaScriptEnabled = true
        mWebView!!.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                activity.setProgress(newProgress)
            }
        }
        mWebView!!.webViewClient = object : WebViewClient() {

            override// Deprecated in API level 23
            fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                Toast.makeText(activity, "Authentication error: " + description, Toast.LENGTH_LONG)
                        .show()
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                val uri = Uri.parse(url)

                if (uri.path.contains("/login.php")) {
                    mLogger!!.debug("AUTH", "Reached login.php")
                    mWebView!!.visibility = View.VISIBLE
                    mProgressBar!!.visibility = View.GONE
                    mProgressLabel!!.visibility = View.GONE
                } else if (uri.path == "/connect/login_success.html") {
                    // TODO: Check if all privileges were granted
                    mLogger!!.debug("AUTH", "Reached login_success with token")
                    mWebView!!.visibility = View.GONE
                    mProgressBar!!.visibility = View.VISIBLE
                    mProgressLabel!!.visibility = View.VISIBLE
                    mProgressLabel!!.text = getString(R.string.auth_progress_retrieving_calendars)

                    mAccessToken = Uri.parse("http://localhost/?" + uri.fragment).getQueryParameter("access_token")

                    // Use a desktop user-agent to make sure we get a desktop version - otherwise we
                    // won't be able to get to the birthday link
                    mWebView!!.settings.userAgentString = "Mozilla/5.0 (X11;Linux x86_64;rv:58.0) Gecko/20100101 Firefox/58.0"
                    mWebView!!.loadUrl("https://www.facebook.com/events")
                } else if (uri.path == "/events/") {
                    mLogger!!.debug("AUTH", "Reached /events/ page, extracting iCal link")
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                        class JSObject {
                            @JavascriptInterface
                            fun linkExtracted(s: String) {
                                onBirthdayLinkExtracted(s)
                            }
                        }

                        view.addJavascriptInterface(JSObject(), "fbeventsync")
                        view.loadUrl(
                                "javascript:(function() { " +
                                        "  var dash = document.getElementById(\"events_dashboard_export\");" +
                                        "  if (dash === null) {" +
                                        "    fbeventsync.linkExtracted(\"\");" +
                                        "  } else {" +
                                        "    var elems = dash.getElementsByTagName(\"a\");" +
                                        "    for (var i = 0; i < elems.length; i++) {" +
                                        "      var link = elems[i];" +
                                        "      if (link.href.startsWith(\"webcal://\")) {" +
                                        "        fbeventsync.linkExtracted(link.href); " +
                                        "        return;" +
                                        "      }" +
                                        "    }" +
                                        "    fbeventsync.linkExtracted(dash.outerHTML);" +
                                        "  }" +
                                        "})();")
                    } else {
                        view.evaluateJavascript(
                                "(function() { " +
                                        "  var dash = document.getElementById(\"events_dashboard_export\");" +
                                        "  if (dash === null) {" +
                                        "    return \"\";" +
                                        "  } else {" +
                                        "    var elems = dash.getElementsByTagName(\"a\");" +
                                        "    for (var i = 0; i < elems.length; i++) {" +
                                        "      var link = elems[i];" +
                                        "      if (link.href.startsWith(\"webcal://\")) {" +
                                        "        return link.href;" +
                                        "      }" +
                                        "    }" +
                                        "    return dash.outerHTML;" +
                                        "  }" +
                                        "})();"
                        ) { s -> onBirthdayLinkExtracted(s) }
                    }
                }
            }
        }

        mAccountManager = AccountManager.get(baseContext)
        mAuthTokenType = intent.getStringExtra(ARG_AUTH_TOKEN_TYPE)

        // Once the check is finished it will call startLogin()
        checkInternetPermission()
    }


    private fun startLogin() {
        val uri = Uri.Builder()
                .scheme("https")
                .authority("www.facebook.com")
                .path("/v2.9/dialog/oauth")
                .appendQueryParameter("client_id", getString(R.string.facebook_app_id))
                .appendQueryParameter("redirect_uri", "https://www.facebook.com/connect/login_success.html")
                .appendQueryParameter("response_type", "token")
                .appendQueryParameter("scopes", TOKEN_SCOPE)
                .build()
        mWebView!!.loadUrl(uri.toString())
    }

    private fun fetchUserInfo(accessToken: String?) {
        val activity = this
        Graph.me(accessToken, object : JsonHttpResponseHandler() {
            override fun onSuccess(statusCode: Int, headers: Array<Header>?, response: JSONObject?) {
                try {
                    val accountName = response!!.getString("name")
                    createAccount(accessToken, accountName)
                } catch (e: org.json.JSONException) {
                    mLogger!!.error("AUTH", "JSON exception: %s", e.message)
                    Toast.makeText(activity, getString(R.string.auth_account_creation_error_toast),
                            Toast.LENGTH_SHORT)
                            .show()
                    activity.finish()
                }

            }

            override fun onFailure(statusCode: Int, headers: Array<Header>?, throwable: Throwable, errorResponse: JSONObject?) {
                if (errorResponse != null) {
                    try {
                        val err = errorResponse.getJSONObject("error")
                        val errCode = err.getInt("code")
                        if (errCode == 4) {
                            mLogger!!.error("AUTH", "FetchUserInfo: rate limiting error")
                            Toast.makeText(activity, getString(R.string.auth_rate_limiting_toast), Toast.LENGTH_SHORT)
                                    .show()
                            activity.finish()
                            return
                        }
                    } catch (e: org.json.JSONException) {
                        // pass
                    }

                    mLogger!!.error("AUTH", "FetchUserInfo failure: %s", errorResponse.toString())
                } else {
                    mLogger!!.error("AUTH", "FetchUserInfo failure: unknown error")
                }
                Toast.makeText(activity, getString(R.string.auth_account_creation_error_toast), Toast.LENGTH_SHORT)
                        .show()
                activity.finish()
            }
        })
    }

    private fun createAccount(accessToken: String?, accountName: String) {
        mLogger!!.debug("AUTH", "Creating account %s", accountName)
        val intent = intent
        val account = Account(accountName, intent.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE))
        if (intent.getBooleanExtra(ARG_IS_ADDING_NEW_ACCOUNT, false)) {
            mAccountManager!!.addAccountExplicitly(account, null, null)

            ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 1)
            ContentResolver.setSyncAutomatically(account, CalendarContract.AUTHORITY, true)
        }

        val icalUri = Uri.parse(mBDayCalendar)
        val key = icalUri.getQueryParameter("key")
        val uid = icalUri.getQueryParameter("uid")
        if (key == null || key.isEmpty() || uid == null || uid.isEmpty()) {
            mLogger!!.error("AUTH", "Failed to retrieve iCal URL! The raw URL was " + mBDayCalendar!!)
            Toast.makeText(this, getString(R.string.auth_calendar_uri_error_toast), Toast.LENGTH_SHORT)
                    .show()
            finish()
            return
        }
        mAccountManager!!.setUserData(account, Authenticator.DATA_BDAY_URI, null) // clear the legacy storage
        mAccountManager!!.setAuthToken(account, Authenticator.FB_OAUTH_TOKEN, accessToken)
        mAccountManager!!.setAuthToken(account, Authenticator.FB_KEY_TOKEN, key)
        mAccountManager!!.setAuthToken(account, Authenticator.FB_UID_TOKEN, uid)

        val result = Intent()
        result.putExtra(AccountManager.KEY_ACCOUNT_NAME, accountName)
        result.putExtra(AccountManager.KEY_ACCOUNT_TYPE, intent.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE))
        val authTokenType = intent.getStringExtra(AuthenticatorActivity.ARG_AUTH_TOKEN_TYPE)
        if (authTokenType != null && authTokenType == Authenticator.FB_KEY_TOKEN) {
            result.putExtra(AccountManager.KEY_AUTHTOKEN, key)
        } else if (authTokenType != null && authTokenType == Authenticator.FB_UID_TOKEN) {
            result.putExtra(AccountManager.KEY_AUTHTOKEN, uid)
        } else {
            result.putExtra(AccountManager.KEY_AUTHTOKEN, accessToken)
        }

        setAccountAuthenticatorResult(result.extras)
        setResult(Activity.RESULT_OK, result)

        CalendarSyncAdapter.updateSync(this)

        Toast.makeText(this, R.string.auth_account_creation_success_toast, Toast.LENGTH_SHORT)
                .show()
        finish()
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
        when (requestCode) {
            PERMISSION_REQUEST_INTERNET -> if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLogin()
            } else {
                // TODO: What to do when we don't get the permissions?
                finish()
            }
        }
    }

    companion object {

        var ARG_AUTH_TOKEN_TYPE = "cz.dvratil.fbeventsync.AuthenticatorActivity.AuthType"
        var ARG_IS_ADDING_NEW_ACCOUNT = "cz.dvratil.fbeventsync.AuthenticatorActivity.IsAddingNewAccount"

        var AUTH_NOTIFICATION_ID = 1
        var AUTH_NOTIFICATION_CHANNEL_ID = "FBEventSync_Auth_NtfChannel"

        val TOKEN_SCOPE = "me,user_events"

        private val PERMISSION_REQUEST_INTERNET = 1
    }
}
