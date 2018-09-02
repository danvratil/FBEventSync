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
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.FragmentActivity
import android.support.v7.preference.ListPreference
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceFragmentCompat
import android.support.v7.preference.PreferenceManager
import android.support.v7.widget.Toolbar
import android.util.Log

import com.kizitonwose.colorpreferencecompat.ColorPreferenceCompat
import com.larswerkman.lobsterpicker.LobsterPicker
import com.larswerkman.lobsterpicker.sliders.LobsterShadeSlider

import cz.dvratil.fbeventsync.preferences.Preferences as FBPreferences

import java.util.Locale

class SettingsActivity : FragmentActivity() {

    private var mShouldForceSync = false
    private var mShouldRescheduleSync = false
    private var mObserver = object: Preferences.PreferencesObserver() {
        override fun onChange(selfChange: Boolean, uri: Uri) {
            if (uri.pathSegments.last() == getString(R.string.pref_sync_frequency)) {
                mShouldRescheduleSync = true
            }
            mShouldForceSync = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setTheme(R.style.SettingsTheme)

        PreferencesMigrator.migrate(this)

        var fragment: BasePreferenceFragment?
        val action = intent.action
        var fragmentTitleId: Int
        when (action) {
            CONFIGURE_CALENDARS -> {
                fragment = CalendarPreferenceFragment()
                fragmentTitleId = R.string.pref_calendar_settings_title
            }
            CONFIGURE_SYNC_ACTION -> {
                fragment = SyncPreferenceFragment()
                fragmentTitleId = R.string.pref_sync_settings_title
            }
            else -> throw Exception("Invalid action")
        }

        fragment.preferences = FBPreferences(this)

        Preferences(this).registerChangeListener(mObserver)

        supportFragmentManager.beginTransaction().replace(R.id.settings_content, fragment).commit()
        findViewById<Toolbar>(R.id.settings_toolbar).title = getString(fragmentTitleId)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        findViewById<Toolbar>(R.id.settings_toolbar).setNavigationOnClickListener { finish() }
    }

    private fun maybeSync() {
        if (mShouldRescheduleSync) {
            CalendarSyncAdapter.updateSync(this, null)
            mShouldRescheduleSync = false
        }
        if (mShouldForceSync) {
            CalendarSyncAdapter.requestSync(this, null)
            mShouldForceSync = false
        }
    }

    override fun onPause() {
        super.onPause()
        maybeSync()
        Preferences(this).unregisterChangeListener(mObserver)
    }

    override fun onStop() {
        super.onStop()
        maybeSync()
        Preferences(this).unregisterChangeListener(mObserver)
    }

    override fun onResume() {
        super.onResume()
        Preferences(this).registerChangeListener(mObserver)
    }

    open class BasePreferenceFragment : PreferenceFragmentCompat() {
        var preferences : FBPreferences? = null

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.preferenceDataStore = preferences!!
        }
    }

    class CalendarPreferenceFragment : BasePreferenceFragment() {
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
            val inflater = activity!!.layoutInflater
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
                    .setPositiveButton(getString(R.string.color_dlg_save_btn_title)) { _, _ -> (preference as ColorPreferenceCompat).value = lobsterPicker.color }
                    .setNegativeButton(getString(R.string.color_dlg_close_btn_title), null)
                    .show()
        }
    }

    class SyncPreferenceFragment : BasePreferenceFragment() {
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
                    is java.io.IOException -> Logger.getInstance(context!!).error(TAG, "SyncPreferenceFragment: $e")
                    else -> Logger.getInstance(context!!).error(TAG, "SyncPreferenceFragment: unhandled $e")
                }
            }

            pref.entries = entries.toTypedArray<CharSequence>()
            pref.entryValues = entryValues.toTypedArray<CharSequence>()
        }
    }

    companion object {
        private const val TAG = "PREFS"
        const val CONFIGURE_CALENDARS = "cz.dvratil.fbeventsync.Settings.CONFIGURE_CALENDARS"
        const val CONFIGURE_SYNC_ACTION = "cz.dvratil.fbeventsync.Settings.CONFIGURE_SYNC"
    }
}
