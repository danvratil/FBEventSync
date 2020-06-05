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
    private var mCookies = Cookies()
    private var mUserId = String()
    private var mKey = String()
    private var mAccountName = String()

    private lateinit var mWebView: WebView
    private lateinit var mWebClient: AuthenticatorWebView
    private lateinit var mProgressBar: ProgressBar
    private lateinit var mProgressLabel: TextView
    private lateinit var mLogger: Logger

    private fun linkExtractionFailed(s: String) {
        mLogger.debug("AUTH", "Link extraction failed: $s")
        Toast.makeText(this, "Authentication error: $s", Toast.LENGTH_LONG)
                .show()
    }

    private fun linkExtracted(s: String) {
        mLogger.debug("AUTH", "Key extraction done!")

        val link = Uri.parse(s)
        val key = link.getQueryParameter("key")
        if (key == null || key.isEmpty()) {
            linkExtractionFailed("Failed to parse calendar URI.")
        } else {
            mKey = key

            // Once we've reached this point we no longer need the webview, so reset cookies
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().removeAllCookies(null)
            } else {
                CookieManager.getInstance().removeAllCookie()
            }

            createAccount()
        }
    }

    private fun userNameFetched(s: String) {
        mLogger.debug("AUTH", "Username detected!")
        mAccountName = s
        mProgressLabel.text = getString(R.string.auth_progress_retrieving_calendars)
        mWebClient.loadEventsPage(mWebView)
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

        mWebClient = object : AuthenticatorWebView(activity) {

            // Deprecated in API level 23
            @Suppress("OverridingDeprecatedMember")
            override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                Toast.makeText(activity, "Authentication error: $description", Toast.LENGTH_LONG)
                        .show()
            }

            override fun onLoginPageReached(webView: WebView, uri: Uri) {
                mLogger.debug("AUTH", "Reached login.php")
                mWebView.visibility = View.VISIBLE
                mProgressBar.visibility = View.GONE
                mProgressLabel.visibility = View.GONE
            }

            override fun onLoginSuccess(webView: WebView, uri: Uri) {
                mLogger.debug("AUTH", "Logged in.")
                mWebView.visibility = if (DEBUG_WEBVIEW) View.VISIBLE else View.GONE
                mProgressBar.visibility = if (DEBUG_WEBVIEW) View.GONE else View.VISIBLE
                mProgressLabel.visibility = if (DEBUG_WEBVIEW) View.GONE else View.VISIBLE
                mProgressLabel.text = getString(R.string.auth_progress_retrieving_userinfo)

                mCookies = Cookies(CookieManager.getInstance().getCookie(uri.toString()), uri.toString())

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
            }

            override fun onHomePageReached(webView: WebView, uri: Uri) {
                mLogger.debug("AUTH", "Home page loaded.")
                loadProfilePage(mWebView) {
                    mLogger.error("AUTH", it)
                    Toast.makeText(activity, getString(R.string.auth_account_creation_error_toast), Toast.LENGTH_LONG).show()
                    if (!DEBUG_WEBVIEW) {
                        finish()
                    }
                }
            }

            override fun onUserPageReached(webView: WebView, uri: Uri) {
                mLogger.debug("AUTH", "User page loaded.")
                findUserName(webView,
                        { userNameFetched(it) },
                        {
                            Toast.makeText(activity, getString(R.string.auth_account_creation_error_toast), Toast.LENGTH_SHORT)
                                .show()
                            if (!DEBUG_WEBVIEW) {
                                finish()
                            }
                        })
            }

            override fun onEventsPageReached(webView: WebView, uri: Uri) {
                mLogger.debug("AUTH", "Events page loaded.")
                findWebCalUri(webView,
                        { linkExtracted(it) },
                        { webView.loadUrl("https://www.facebook.com/events/${AuthenticatorActivity.EXPORT_EVENT_FBID}") }
                )
            }


        }

        mWebView.webViewClient = mWebClient

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

        mWebView.loadUrl(Uri.Builder()
                    .scheme("https")
                    .authority("mbasic.facebook.com")
                    .path("login.php")
                    .build().toString())
    }

    private fun createAccount() {
        if (isFinishing) {
            return
        }
        mLogger.debug("AUTH", "Creating account $mAccountName")
        val intent = intent
        val account = Account(mAccountName, intent.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE))
        if (intent.getBooleanExtra(ARG_IS_ADDING_NEW_ACCOUNT, false)) {
            mAccountManager.addAccountExplicitly(account, null, null)

            ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 1)
            ContentResolver.setSyncAutomatically(account, CalendarContract.AUTHORITY, true)
        }

        if (mKey.isEmpty() || mUserId.isEmpty()) {
            mLogger.error("AUTH", "Failed to retrieve UID ($mUserId)")
            Toast.makeText(this, getString(R.string.auth_calendar_uri_error_toast), Toast.LENGTH_SHORT)
                    .show()
            if (!DEBUG_WEBVIEW) {
                finish()
            }
            return
        }
        mAccountManager.setUserData(account, Authenticator.DATA_BDAY_URI, null) // clear the legacy storage
        mAccountManager.setAuthToken(account, Authenticator.FB_KEY_TOKEN, mKey)
        mAccountManager.setAuthToken(account, Authenticator.FB_UID_TOKEN, mUserId)
        mAccountManager.setUserData(account, Authenticator.FB_COOKIES, mCookies.cookies)

        val result = Intent()
        result.putExtra(AccountManager.KEY_ACCOUNT_NAME, mAccountName)
        result.putExtra(AccountManager.KEY_ACCOUNT_TYPE, intent.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE))
        val authTokenType = intent.getStringExtra(ARG_AUTH_TOKEN_TYPE)
        if (authTokenType != null && authTokenType == Authenticator.FB_KEY_TOKEN) {
            result.putExtra(AccountManager.KEY_AUTHTOKEN, mKey)
        } else if (authTokenType != null && authTokenType == Authenticator.FB_UID_TOKEN) {
            result.putExtra(AccountManager.KEY_AUTHTOKEN, mUserId)
        } else if (authTokenType != null && authTokenType == Authenticator.FB_COOKIES) {
            result.putExtra(AccountManager.KEY_AUTHTOKEN, mCookies.cookies)
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
