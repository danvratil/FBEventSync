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

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.accounts.NetworkErrorException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log


class Authenticator(private val mContext: Context) : AbstractAccountAuthenticator(mContext) {

    @Throws(NetworkErrorException::class)
    override fun addAccount(response: AccountAuthenticatorResponse, accountType: String,
                            authTokenType: String?, requiredFeatures: Array<String>?, options: Bundle?): Bundle? {
        val intent = Intent(mContext, AuthenticatorActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, accountType)
        intent.putExtra(AuthenticatorActivity.ARG_AUTH_TOKEN_TYPE, authTokenType)
        intent.putExtra(AuthenticatorActivity.ARG_IS_ADDING_NEW_ACCOUNT, true)
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)

        val bundle = Bundle()
        bundle.putParcelable(AccountManager.KEY_INTENT, intent)
        return bundle
    }

    @Throws(NetworkErrorException::class)
    override fun getAuthToken(response: AccountAuthenticatorResponse, account: Account,
                              authTokenType: String, options: Bundle?): Bundle? {

        val accMgr = AccountManager.get(mContext)
        val token = accMgr.peekAuthToken(account, authTokenType)
        if (token == null || token.isEmpty()) {
            val intent = Intent(mContext, AuthenticatorActivity::class.java)
            intent.action = Intent.ACTION_VIEW
            intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, account.type)
            intent.putExtra(AuthenticatorActivity.ARG_AUTH_TOKEN_TYPE, authTokenType)
            intent.putExtra(AuthenticatorActivity.ARG_IS_ADDING_NEW_ACCOUNT, false)
            intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)

            val bundle = Bundle()
            bundle.putParcelable(AccountManager.KEY_INTENT, intent)
            return bundle
        } else {
            val bundle = Bundle()
            bundle.putString(AccountManager.KEY_ACCOUNT_NAME, account.name)
            bundle.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type)
            bundle.putString(AccountManager.KEY_AUTHTOKEN, token)
            return bundle
        }
    }

    override fun getAuthTokenLabel(authTokenType: String): String? {
        Log.d(TAG, "GetAuthTokenLabel: " + authTokenType)
        return null
    }

    @Throws(NetworkErrorException::class)
    override fun confirmCredentials(response: AccountAuthenticatorResponse, account: Account,
                                    options: Bundle?): Bundle? {
        Log.d(TAG, "Confirm credentials")
        return null
    }

    @Throws(NetworkErrorException::class)
    override fun updateCredentials(response: AccountAuthenticatorResponse, account: Account,
                                   authTokenType: String?, options: Bundle?): Bundle? {
        Log.d(TAG, "Update crendentials")
        return null
    }

    @Throws(NetworkErrorException::class)
    override fun hasFeatures(response: AccountAuthenticatorResponse, account: Account,
                             features: Array<String>): Bundle? {
        val result = Bundle()
        result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false)
        return result
    }

    override fun editProperties(response: AccountAuthenticatorResponse, accountType: String): Bundle? {
        Log.d(TAG, "Edit properties")
        return null
    }

    companion object {

        private const val TAG = "AUTH"

        const val FB_OAUTH_TOKEN = "fb_oauth"
        const val FB_UID_TOKEN = "fb_uid"
        const val FB_KEY_TOKEN = "fb_key"
        const val FB_COOKIES = "fb_cookies"

        // DON'T USE! Preserved for legacy reason so that we can clear the data when migrating to the
        // new tokens
        const val DATA_BDAY_URI = "bday_uri"
    }
}
