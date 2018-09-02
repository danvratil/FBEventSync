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
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.view.MenuItem


class MainActivity : AppCompatActivity() {

    private lateinit var mRecyclerView: RecyclerView
    private lateinit var mDrawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<View>(R.id.main_toolbar) as Toolbar)
        supportActionBar!!.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_menu_white)
        }

        mDrawerLayout = findViewById(R.id.main_drawer_layout)
        findViewById<NavigationView>(R.id.nav_view).setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.nav_sync_all -> {
                    mDrawerLayout.closeDrawers()
                    AccountManager.get(this).getAccountsByType(getString(R.string.account_type)).forEach {
                        CalendarSyncAdapter.requestSync(this, it)
                    }
                    true
                }
                R.id.nav_calendar_settings -> {
                    mDrawerLayout.closeDrawers()
                    startActivity(
                            Intent(this, SettingsActivity::class.java).apply {
                                action = SettingsActivity.CONFIGURE_CALENDARS
                            })
                    true
                }
                R.id.nav_sync_settings -> {
                    mDrawerLayout.closeDrawers()
                    startActivity(
                            Intent(this, SettingsActivity::class.java).apply {
                                action = SettingsActivity.CONFIGURE_SYNC_ACTION
                            })
                    true
                }
                R.id.nav_sync_logs -> {
                    mDrawerLayout.closeDrawers()
                    startActivity(Intent(this, LogViewActivity::class.java))
                    true
                }
                R.id.nav_report_bug -> {
                    mDrawerLayout.closeDrawers()
                    startActivity(
                            Intent(Intent.ACTION_SENDTO).apply {
                                type = "message/rfc822"
                                data = Uri.parse("mailto:me@dvratil.cz")
                                putExtra(Intent.EXTRA_EMAIL, arrayOf("me@dvratil.cz"))
                                putExtra(Intent.EXTRA_SUBJECT, "FBEventSync Issue")
                                putExtra(Intent.EXTRA_TEXT, getString(R.string.log_email_template))
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                    true
                }
                R.id.nav_faq -> {
                    mDrawerLayout.closeDrawers()
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/danvratil/FBEventSync/wiki/FAQ")))
                    true
                }
                R.id.nav_about -> {
                    mDrawerLayout.closeDrawers()
                    startActivity(Intent(this, AboutActivity::class.java))
                    true
                }
                else -> false
            }
        }

        mRecyclerView = findViewById<RecyclerView>(R.id.main_card_view).apply {
            setHasFixedSize(true)
            adapter = AccountAdapter(context)
            layoutManager = LinearLayoutManager(context)
        }

        registerSyncIfNeeded();
    }

    override fun onResume() {
        AccountManager.get(this).addOnAccountsUpdatedListener(mRecyclerView.adapter as AccountAdapter, null, true)
        super.onResume()
    }


    override fun onPause() {
        AccountManager.get(this).removeOnAccountsUpdatedListener(mRecyclerView.adapter as AccountAdapter)
        super.onPause()
    }

    fun onAddAccountClicked(@Suppress("UNUSED_PARAMETER") view: View) {
        val intent = Intent(Settings.ACTION_ADD_ACCOUNT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            intent.putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf(getString(R.string.account_type)))
        }
        startActivity(intent)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                mDrawerLayout.openDrawer(GravityCompat.START)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun registerSyncIfNeeded() {
        val syncs = ContentResolver.getCurrentSyncs()
        val accounts = AccountManager.get(this).getAccountsByType(getString(R.string.account_type));
        accounts.forEach { account ->
            if (syncs.find { it.account == account } == null) {
                CalendarSyncAdapter.updateSync(this, account)
            }
        }
    }
}
