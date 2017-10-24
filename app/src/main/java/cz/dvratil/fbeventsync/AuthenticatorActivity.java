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

import com.facebook.FacebookSdk;
import com.facebook.login.LoginBehavior;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import java.util.Arrays;

public class AuthenticatorActivity extends AppCompatActivity {

    static public String ARG_ACCOUNT_TYPE = "cz.dvratil.fbeventsync.AuthenticatorActivity.AccountType";
    static public String ARG_AUTH_TYPE = "cz.dvratil.fbeventsync.AuthenticatorActivity.AuthType";
    static public String ARG_IS_ADDING_NEW_ACCOUNT = "cz.dvratil.fbeventsync.AuthenticatorActivity.IsAddingNewAccount";

    private CallbackManager mCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FacebookSdk.sdkInitialize(getApplicationContext());
        Log.d("AUTH", "Activity created");

        mCallback = CallbackManager.Factory.create();

        LoginManager manager = LoginManager.getInstance();
        manager.setLoginBehavior(LoginBehavior.NATIVE_WITH_FALLBACK);
        manager.registerCallback(mCallback, new FbAuthenticationCallback(this));
        LoginManager.getInstance().logInWithReadPermissions(this, Arrays.asList("user_events"));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mCallback.onActivityResult(requestCode, resultCode, data);
    }
}
