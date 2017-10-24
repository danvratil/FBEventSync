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


import android.accounts.AccountAuthenticatorActivity;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.util.Log;

import org.json.JSONObject;

import java.util.Arrays;

public class AuthenticatorActivity extends AccountAuthenticatorActivity
                                   implements FacebookCallback<LoginResult> {

    static public String ARG_AUTH_TYPE = "cz.dvratil.fbeventsync.AuthenticatorActivity.AuthType";
    static public String ARG_IS_ADDING_NEW_ACCOUNT = "cz.dvratil.fbeventsync.AuthenticatorActivity.IsAddingNewAccount";

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
        LoginManager.getInstance().logInWithReadPermissions(this, Arrays.asList("user_events"));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mCallback.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onSuccess(LoginResult loginResult) {
        Log.d("AUTH", "Authentication success");

        final AccessToken accessToken = loginResult.getAccessToken();
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

        mAccountManager.setAuthToken(account, intent.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE), accessToken.getToken());

        AccessToken.setCurrentAccessToken(accessToken);
        setAccountAuthenticatorResult(intent.getExtras());
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void onCancel() {
        Log.d("AUTH", "Authentication cancelled by user");
        setResult(RESULT_CANCELED, getIntent());
    }

    @Override
    public void onError(FacebookException error) {
        Log.d("AUTH", "Authentication error: " + error.getMessage());
        finish();
    }
}
