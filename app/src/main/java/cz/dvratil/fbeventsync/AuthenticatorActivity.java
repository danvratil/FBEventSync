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

import com.facebook.AccessToken;
import com.facebook.GraphResponse;
import com.facebook.login.LoginBehavior;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.GraphRequest;


import android.Manifest;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.Arrays;

public class AuthenticatorActivity extends AccountAuthenticatorActivity
                                   implements FacebookCallback<LoginResult> {

    public static String ARG_AUTH_TYPE = "cz.dvratil.fbeventsync.AuthenticatorActivity.AuthType";
    public static String ARG_IS_ADDING_NEW_ACCOUNT = "cz.dvratil.fbeventsync.AuthenticatorActivity.IsAddingNewAccount";

    private static final int PERMISSION_REQUEST_INTERNET = 1;

    private CallbackManager mCallback;
    private AccountManager mAccountManager;
    private String mAuthType;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);


        mAccountManager = AccountManager.get(getBaseContext());
        mAuthType = getIntent().getStringExtra(ARG_AUTH_TYPE);

        mCallback = CallbackManager.Factory.create();
        LoginManager manager = LoginManager.getInstance();
        manager.setLoginBehavior(LoginBehavior.NATIVE_WITH_FALLBACK);
        manager.registerCallback(mCallback, this);

        // Callback to permission check will trigger authentication
        checkInternetPermission();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mCallback.onActivityResult(requestCode, resultCode, data);
    }

    private void startLogin() {
        LoginManager.getInstance().logInWithReadPermissions(this, Arrays.asList("user_events"));
    }

    @Override
    public void onSuccess(LoginResult loginResult) {
        Log.d("AUTH", "Authentication success");

        final AccessToken accessToken = loginResult.getAccessToken();
        if (accessToken.getDeclinedPermissions().contains("user_events")) {
            Log.d("AUTH","User rejected access to user_events, aborting");
            Toast.makeText(this, getString(R.string.toast_fb_user_events_denied), Toast.LENGTH_SHORT)
                    .show();
            finish();
            return;
        }

        GraphRequest request = GraphRequest.newMeRequest(
                loginResult.getAccessToken(),
                new GraphRequest.GraphJSONObjectCallback() {
                    @Override
                    public void onCompleted(JSONObject object, GraphResponse response) {
                        try {
                            String accountName = object.getString("name");
                            createAccount(accessToken, accountName);
                        } catch (org.json.JSONException e) {
                            Log.e("AUTH", "JSON error: " + e.getMessage());
                            finish();
                        }
                    }
                });
        Bundle parameters = new Bundle();
        parameters.putString("fields", "name");
        request.setParameters(parameters);
        request.executeAsync();
    }

    private void createAccount(AccessToken accessToken, String accountName) {
        Log.d("AUTH", "Creating account " + accountName);
        Intent intent = getIntent();
        Account account = new Account(accountName, intent.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE));
        if (intent.getBooleanExtra(ARG_IS_ADDING_NEW_ACCOUNT, false)) {
            mAccountManager.addAccountExplicitly(account, null, null);

            ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 1);
            ContentResolver.setSyncAutomatically(account, CalendarContract.AUTHORITY, true);
        }

        mAccountManager.setAuthToken(account, intent.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE),
                accessToken.getToken());

        AccessToken.setCurrentAccessToken(accessToken);
        setAccountAuthenticatorResult(intent.getExtras());
        setResult(RESULT_OK, intent);

        CalendarSyncAdapter.updateSync(this);

        Toast.makeText(this, R.string.toast_account_creation_success, Toast.LENGTH_SHORT)
                .show();
        finish();
    }

    @Override
    public void onCancel() {
        Log.d("AUTH", "Authentication cancelled by user");
        setResult(RESULT_CANCELED, getIntent());
        Toast.makeText(this, R.string.toast_account_creation_cancelled, Toast.LENGTH_SHORT)
                .show();
        finish();
    }

    @Override
    public void onError(FacebookException error) {
        Log.d("AUTH", "Authentication error: " + error.getMessage());
        Toast.makeText(this, R.string.toast_account_creation_error, Toast.LENGTH_SHORT)
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
