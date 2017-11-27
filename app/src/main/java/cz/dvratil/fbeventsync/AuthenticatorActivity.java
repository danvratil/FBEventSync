/*
    Copyright (C) 2017  Daniel Vrátil <me@dvratil.cz>

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

package cz.dvratil.fbeventsync;

import android.Manifest;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.loopj.android.http.JsonHttpResponseHandler;

import org.json.JSONObject;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.auth.AUTH;

public class AuthenticatorActivity extends AccountAuthenticatorActivity {

    public static String ARG_AUTH_TYPE = "cz.dvratil.fbeventsync.AuthenticatorActivity.AuthType";
    public static String ARG_IS_ADDING_NEW_ACCOUNT = "cz.dvratil.fbeventsync.AuthenticatorActivity.IsAddingNewAccount";

    public static final String TOKEN_SCOPE = "me,user_events";

    private static final int PERMISSION_REQUEST_INTERNET = 1;

    private AccountManager mAccountManager = null;
    private String mAuthType = null;
    private String mAccessToken = null;
    private String mBDayCalendar = null;

    private WebView mWebView = null;
    private ProgressBar mProgressBar = null;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().requestFeature(Window.FEATURE_PROGRESS);
        setContentView(R.layout.activity_authenticator);

        final AccountAuthenticatorActivity activity = this;

        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mWebView = (WebView) findViewById(R.id.webview);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                activity.setProgress(newProgress);
            }
        });
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                Toast.makeText(activity, "Authentication error: " + error.getDescription(), Toast.LENGTH_SHORT)
                        .show();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Uri uri = Uri.parse(url);

                if (uri.getPath().contains("/login.php")) {

                } else if (uri.getPath().equals("/connect/login_success.html")) {
                    // TODO: Check if all privileges were granted

                    mWebView.setVisibility(View.GONE);
                    mProgressBar.setVisibility(View.VISIBLE);

                    mAccessToken = Uri.parse("http://localhost/?" + uri.getFragment()).getQueryParameter("access_token");

                    // Use a desktop user-agent to make sure we get a desktop version - otherwise we
                    // won't be able to get to the birthday link
                    mWebView.getSettings().setUserAgentString("Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:25.0) Gecko/20100101 Firefox/25.0");
                    mWebView.loadUrl("https://www.facebook.com/events");
                } else if (uri.getPath().equals("/events/")) {
                    view.evaluateJavascript(
                            "(function() { " +
                                   "  var elems = document.getElementsByTagName(\"a\");" +
                                   "  for (var i = 0; i < elems.length; i++) {" +
                                   "    var link = elems[i];" +
                                   "    if (link.href.startsWith(\"webcal://www.facebook.com/ical/b.php\")) {" +
                                   "      return link.href;" +
                                   "    }" +
                                   "  }" +
                                   "})();",
                            new ValueCallback<String>() {
                                @Override
                                public void onReceiveValue(String s) {
                                    Log.d("BDAY","Found bday URL: " + s);

                                    // Remove opening and trailing quotes that come from JavaScript
                                    mBDayCalendar = s.substring(1, s.length() - 1);
                                    fetchUserInfo(mAccessToken);
                                }
                            });

                }
            }
        });

        mAccountManager = AccountManager.get(getBaseContext());
        mAuthType = getIntent().getStringExtra(ARG_AUTH_TYPE);

        // Once the check is finished it will call startLogin()
        checkInternetPermission();
    }

    private void startLogin() {
        Uri uri = new Uri.Builder()
                    .scheme("https")
                    .authority("www.facebook.com")
                    .path("/v2.9/dialog/oauth")
                    .appendQueryParameter("client_id", getString(R.string.facebook_app_id))
                    .appendQueryParameter("redirect_uri", "https://www.facebook.com/connect/login_success.html")
                    .appendQueryParameter("response_type", "token")
                    .appendQueryParameter("scopes", TOKEN_SCOPE)
                    .build();
        Log.d("AUTH", uri.toString());
        mWebView.loadUrl(uri.toString());
    }

    private void fetchUserInfo(final String accessToken) {
        final AccountAuthenticatorActivity activity = this;
        Graph.me(accessToken, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                try {
                    String accountName = response.getString("name");
                    createAccount(accessToken, accountName);
                } catch (org.json.JSONException e) {
                    Log.e("AUTH", "fetchUserInfo JSONException: " + e.getMessage());
                    Toast.makeText(activity, getString(R.string.toast_account_creation_error),
                            Toast.LENGTH_SHORT)
                            .show();
                    activity.finish();
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                Log.e("AUTH", "fetchUserInfo failure: " + errorResponse.toString());
                Toast.makeText(activity, getString(R.string.toast_account_creation_error), Toast.LENGTH_SHORT)
                        .show();
                activity.finish();
            }
        });
    }

    private void createAccount(String accessToken, String accountName) {
        Log.d("AUTH", "Creating account " + accountName);
        Intent intent = getIntent();
        Account account = new Account(accountName, intent.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE));
        if (intent.getBooleanExtra(ARG_IS_ADDING_NEW_ACCOUNT, false)) {
            mAccountManager.addAccountExplicitly(account, null, null);

            ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 1);
            ContentResolver.setSyncAutomatically(account, CalendarContract.AUTHORITY, true);
        }

        mAccountManager.setAuthToken(account, intent.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE),
                accessToken);
        mAccountManager.setUserData(account, Authenticator.DATA_BDAY_URI, mBDayCalendar);

        setAccountAuthenticatorResult(intent.getExtras());
        setResult(RESULT_OK, intent);

        CalendarSyncAdapter.updateSync(this);

        Toast.makeText(this, R.string.toast_account_creation_success, Toast.LENGTH_SHORT)
                .show();
        finish();
    }

    private void checkInternetPermission() {
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{ Manifest.permission.INTERNET },
                    PERMISSION_REQUEST_INTERNET);
        } else {
            startLogin();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_REQUEST_INTERNET:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startLogin();
                } else {
                    // TODO: What to do when we don't get the permissions?
                    finish();
                }
                break;
        }
    }
}
