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

package cz.dvratil.fbeventsync

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Intent
import android.content.SyncStatusObserver
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val view = findViewById<TextView>(R.id.versionLabel)
        view.setText(String.format(getString(R.string.main_version_label), BuildConfig.VERSION_NAME))

        checkAccounts()

        ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE
        ) { runOnUiThread { checkSyncStatus() } }
        checkSyncStatus()
    }

    fun onAddAccountClicked(view: View) {
        val intent = Intent(Settings.ACTION_ADD_ACCOUNT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            intent.putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf(getString(R.string.account_type)))
        }
        startActivity(intent)
    }

    fun onConfigureCalendarsClicked(view: View) {
        openSettings(SettingsActivity.CONFIGURE_CALENDARS)
    }

    fun onConfigureSyncClicked(view: View) {
        openSettings(SettingsActivity.CONFIGURE_SYNC_ACTION)
    }

    fun onConfigureMiscClicked(view: View) {
        openSettings(SettingsActivity.CONFIGURE_MISC_ACTION)
    }

    fun onSyncNowClicked(view: View) {
        CalendarSyncAdapter.requestSync(this)
    }

    private fun openSettings(action: String) {
        val intent = Intent(this, SettingsActivity::class.java)
        intent.action = action
        startActivity(intent)
    }

    fun onShowLogsClicked(view: View) {
        val intent = Intent(this, LogViewActivity::class.java)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        checkAccounts()
    }

    private fun checkAccounts() {
        val accounts = AccountManager.get(this).getAccountsByType(getString(R.string.account_type))
        if (accounts.size == 0) {
            findViewById<View>(R.id.calendar_prefs_btn).visibility = View.GONE
            findViewById<View>(R.id.sync_prefs_btn).visibility = View.GONE
            findViewById<View>(R.id.misc_prefs_btn).visibility = View.GONE
            findViewById<View>(R.id.sync_now_btn).visibility = View.GONE
            findViewById<View>(R.id.add_account_btn).visibility = View.VISIBLE
        } else {
            findViewById<View>(R.id.calendar_prefs_btn).visibility = View.VISIBLE
            findViewById<View>(R.id.sync_prefs_btn).visibility = View.VISIBLE
            findViewById<View>(R.id.misc_prefs_btn).visibility = View.VISIBLE
            findViewById<View>(R.id.sync_now_btn).visibility = View.VISIBLE
            findViewById<View>(R.id.add_account_btn).visibility = View.GONE
        }
    }

    private fun checkSyncStatus() {
        val am = AccountManager.get(this)
        val accounts = am.getAccountsByType(getString(R.string.account_type))
        var active: Boolean? = false
        for (acc in accounts) {
            active = active or ContentResolver.isSyncActive(acc, CalendarContract.AUTHORITY)
        }

        findViewById<View>(R.id.sync_layout).visibility = if (active) View.VISIBLE else View.GONE
        findViewById<View>(R.id.sync_now_btn).isEnabled = (!active)!!
    }

}
