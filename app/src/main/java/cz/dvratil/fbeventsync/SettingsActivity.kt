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

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.res.XmlResourceParser
import android.os.Bundle
import android.preference.ListPreference
import android.preference.Preference
import android.preference.PreferenceActivity
import android.preference.PreferenceFragment
import android.preference.PreferenceManager
import android.support.v7.widget.Toolbar

import com.kizitonwose.colorpreference.ColorPreference
import com.larswerkman.lobsterpicker.LobsterPicker
import com.larswerkman.lobsterpicker.sliders.LobsterShadeSlider

import java.util.Locale

class SettingsActivity : PreferenceActivity() {

    private var mShouldForceSync = false
    private var mShouldRescheduleSync = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        PreferencesMigrator.migrate(this)

        var fragment: PreferenceFragment? = null
        val action = intent.action
        var fragmentTitleId = 0
        when (action) {
            CONFIGURE_CALENDARS -> {
                fragment = CalendarPreferenceFragment()
                fragmentTitleId = R.string.pref_calendar_settings_title
            }
            CONFIGURE_SYNC_ACTION -> {
                fragment = SyncPreferenceFragment()
                fragmentTitleId = R.string.pref_sync_settings_title
            }
            CONFIGURE_MISC_ACTION -> {
                fragment = MiscPreferenceFragment()
                fragmentTitleId = R.string.pref_misc_settings_title
            }
        }

        // API 21
        //String preferencesName = PreferenceManager.getDefaultSharedPreferencesName(this);
        val prefs = getSharedPreferences(getString(R.string.cz_dvratil_fbeventsync_preferences), Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == getString(R.string.pref_sync_frequency)) {
                mShouldRescheduleSync = true
            }
            mShouldForceSync = true
        }

        fragmentManager.beginTransaction().replace(R.id.settings_content, fragment).commit()
        findViewById<Toolbar>(R.id.settings_toolbar).title = getString(fragmentTitleId)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        findViewById<Toolbar>(R.id.settings_toolbar).setNavigationOnClickListener { finish() }
    }

    private fun maybeSync() {
        if (mShouldRescheduleSync) {
            CalendarSyncAdapter.updateSync(this)
            mShouldRescheduleSync = false
        }
        if (mShouldForceSync) {
            CalendarSyncAdapter.requestSync(this)
            mShouldForceSync = false
        }
    }

    override fun onPause() {
        super.onPause()
        maybeSync()
    }

    override fun onStop() {
        super.onStop()
        maybeSync()
    }

    class CalendarPreferenceFragment : PreferenceFragment() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.calendar_preferences)

            installColorDialogHandler(R.string.pref_calendar_attending_color)
            installColorDialogHandler(R.string.pref_calendar_tentative_color)
            installColorDialogHandler(R.string.pref_calendar_declined_color)
            installColorDialogHandler(R.string.pref_calendar_not_responded_color)
            installColorDialogHandler(R.string.pref_calendar_birthday_color)
        }

        private fun installColorDialogHandler(keyId: Int) {
            val key = getString(keyId)
            findPreference(key).onPreferenceClickListener = Preference.OnPreferenceClickListener { preference ->
                showColorDialog(key, preference)
                true
            }
        }

        private fun showColorDialog(key: String, preference: Preference) {
            val inflater = activity.layoutInflater
            @SuppressLint("InflateParams")
            val colorView = inflater.inflate(R.layout.color_dialog, null)

            @Suppress("DEPRECATION")
            val color = PreferenceManager.getDefaultSharedPreferences(activity)
                    .getInt(key, resources.getColor(R.color.colorFBBlue))
            val lobsterPicker = colorView.findViewById<LobsterPicker>(R.id.colordialog_lobsterpicker)
            val shadeSlider = colorView.findViewById<LobsterShadeSlider>(R.id.colordialog_shadeslider)

            lobsterPicker.addDecorator(shadeSlider)
            lobsterPicker.setColorHistoryEnabled(true)
            lobsterPicker.history = color
            lobsterPicker.color = color

            AlertDialog.Builder(activity)
                    .setView(colorView)
                    .setTitle(getString(R.string.color_dlg_title))
                    .setPositiveButton(getString(R.string.color_dlg_save_btn_title)) { _, _ -> (preference as ColorPreference).value = lobsterPicker.color }
                    .setNegativeButton(getString(R.string.color_dlg_close_btn_title), null)
                    .show()
        }


    }

    class SyncPreferenceFragment : PreferenceFragment() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.sync_preferences)
            val pref = findPreference(getString(R.string.pref_language)) as ListPreference
            val parser = resources.getXml(R.xml.fb_languages)
            val entryValues = mutableListOf(getString(R.string.pref_language_default_value))
            val entries = mutableListOf(getString(R.string.pref_language_default_entry))
            try {
                var ev = parser.eventType
                while (ev != XmlResourceParser.END_DOCUMENT) {
                    if (ev == XmlResourceParser.START_TAG && parser.name == "language") {
                        val code = parser.getAttributeValue(null, "code")
                        val lang = code.substring(0, 2)
                        val locale = Locale(lang, code.substring(3, 5))
                        entries.add(when (locale.displayLanguage) {
                            lang -> "${parser.getAttributeValue(null, "name")} (${locale.displayCountry})"
                            else -> locale.displayName
                        })
                        entryValues.add(code)
                    }
                    ev = parser.next()
                }
            } catch (e: Exception) {
                when (e) {
                    is org.xmlpull.v1.XmlPullParserException,
                    is java.io.IOException -> Logger.getInstance(activity).error(TAG, "SyncPreferenceFragment: $e")
                    else -> Logger.getInstance(activity).error(TAG, "SyncPreferenceFragment: unhandled $e")
                }
            }

            pref.entries = entries.toTypedArray<CharSequence>()
            pref.entryValues = entryValues.toTypedArray<CharSequence>()
        }
    }

    class MiscPreferenceFragment : PreferenceFragment() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.misc_preferences)
        }
    }

    companion object {
        private const val TAG = "PREFS"
        const val CONFIGURE_CALENDARS = "cz.dvratil.fbeventsync.Settings.CONFIGURE_CALENDARS"
        const val CONFIGURE_SYNC_ACTION = "cz.dvratil.fbeventsync.Settings.CONFIGURE_SYNC"
        const val CONFIGURE_MISC_ACTION = "cz.dvratil.fbeventsync.Settings.CONFIGURE_MISC"
    }
}
