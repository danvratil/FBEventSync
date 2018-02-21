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

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;

public class SyncContext {
    private Context mContext = null;
    private Account mAccount = null;
    private String mAccessToken = null;
    private ContentProviderClient mProviderClient = null;
    private SyncResult mSyncResult = null;
    private Logger mLogger = null;
    private Preferences mPreferences = null;

    public SyncContext(Context context, Account account, String accessToken,
                       ContentProviderClient providerClient, SyncResult syncResult, Logger logger) {
        mContext = context;
        mAccount = account;
        mAccessToken = accessToken;
        mProviderClient = providerClient;
        mSyncResult = syncResult;
        mLogger = logger;
        mPreferences = new Preferences(context);
    }

    public Context getContext() {
        return mContext;
    }

    public Account getAccount() {
        return mAccount;
    }

    public String getAccessToken() {
        return mAccessToken;
    }

    public ContentProviderClient getContentProviderClient() {
        return mProviderClient;
    }

    public SyncResult getSyncResult() {
        return mSyncResult;
    }

    public Preferences getPreferences() {
        return mPreferences;
    }

    public Logger getLogger() {
        return mLogger;
    }
}