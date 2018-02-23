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

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.net.Uri
import android.provider.CalendarContract

class SyncContext(context: Context, account: Account, accessToken: String,
                  providerClient: ContentProviderClient, syncResult: SyncResult, logger: Logger) {
    val context: Context? = null
    val account: Account? = null
    val accessToken: String? = null
    var contentProviderClient: ContentProviderClient? = null
    val syncResult: SyncResult? = null
    val logger: Logger? = null
    var preferences: Preferences? = null

    init {
        this.context = context
        this.account = account
        this.accessToken = accessToken
        contentProviderClient = providerClient
        this.syncResult = syncResult
        this.logger = logger
        preferences = Preferences(context)
    }

    internal fun contentUri(provider: Uri): Uri {
        return provider.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account!!.name)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, context!!.getString(R.string.account_type))
                .build()
    }
}