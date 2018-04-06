/*
    Copyright (C) 2018  Daniel Vrátil <me@dvratil.cz>

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

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceActivity
import android.preference.PreferenceFragment
import android.support.v7.widget.Toolbar

class AboutActivity : PreferenceActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        fragmentManager.beginTransaction().replace(R.id.about_content, AboutFragment()).commit()

        findViewById<Toolbar>(R.id.about_toolbar).title = getString(R.string.about_activity_title)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        findViewById<Toolbar>(R.id.about_toolbar).setNavigationOnClickListener { finish() }
    }

    class AboutFragment : PreferenceFragment() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.about)

            findPreference(getString(R.string.pref_about_version)).summary =
                "${BuildConfig.VERSION_NAME} (v${BuildConfig.VERSION_CODE})"
            findPreference(getString(R.string.pref_about_license)).setOnPreferenceClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gnu.org/licenses/gpl.html")))
                true
            }
            findPreference(getString(R.string.pref_about_privacy)).setOnPreferenceClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/danvratil/FBEventSync/wiki/Privacy-Policy")))
                true
            }
            findPreference(getString(R.string.pref_about_rate_app)).setOnPreferenceClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${activity.packageName}")))

                } catch (e: android.content.ActivityNotFoundException) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${activity.packageName}")))
                }
                true
            }
        }
    }

    companion object {
        private const val TAG = "PREFS"
        const val CONFIGURE_CALENDARS = "cz.dvratil.fbeventsync.Settings.CONFIGURE_CALENDARS"
        const val CONFIGURE_SYNC_ACTION = "cz.dvratil.fbeventsync.Settings.CONFIGURE_SYNC"
    }
}
