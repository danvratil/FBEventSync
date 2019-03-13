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

import android.content.Context
import android.content.SharedPreferences

object PreferencesMigrator {

    private const val PREFERENCES_VERSION = 2

    @Synchronized
    fun migrate(context: Context) {

        val prefs = Preferences(context)
        val oldPrefs = context.getSharedPreferences(context.getString(R.string.cz_dvratil_fbeventsync_preferences), Context.MODE_PRIVATE)
        val editor = oldPrefs.edit()

        var version = prefs.prefsVersion()
        if (version == 0) {
            version = oldPrefs.getInt(context.getString(R.string.cfg_prefs_version), 0)
        }

        // Nothing to do
        if (version == PREFERENCES_VERSION) {
            return
        }

        if (version < 1) {
            updateToVersion1(oldPrefs, editor, context)
        }
        if (version < 2) {
            updateToVersion2(prefs, oldPrefs, context)
        }

        // Store new version in both old and new config
        editor.putInt(context.getString(R.string.cfg_prefs_version), PREFERENCES_VERSION).apply()
        prefs.setPrefsVersion(PREFERENCES_VERSION)
    }


    // Version 1 introduced versioning and contains fix for default calendar color values
    private fun updateToVersion1(prefs: SharedPreferences, editor: SharedPreferences.Editor, context: Context) {
        val wrongColor = 3889560
        @Suppress("DEPRECATION")
        val correctColor = context.resources.getColor(R.color.colorFBBlue)

        arrayOf(R.string.pref_calendar_attending_color,
                R.string.pref_calendar_tentative_color,
                R.string.pref_calendar_declined_color,
                R.string.pref_calendar_not_responded_color,
                R.string.pref_calendar_birthday_color).forEach {
            if (prefs.getInt(context.getString(it), correctColor) == wrongColor) {
                editor.putInt(context.getString(it), correctColor)
            }
        }
    }

    // Version 2 migrated from SharedPreferences to custom PreferencesProvider (hidden behind the
    // Preferences class)
    private fun updateToVersion2(new: Preferences, old: SharedPreferences, context: Context) {
        new.setAttendingCalendarAllDayReminders(
                old.getStringSet(
                        context.getString(R.string.pref_calendar_attending_allday_reminders),
                        new.attendingCalendarAllDayReminders().map{ it.serialize() }.toSet())
                .map{ FBReminder.fromString(it) })
        new.setAttendingCalendarColor(old.getInt(context.getString(R.string.pref_calendar_attending_color),
                new.attendingCalendarColor()))
        new.setAttendingCalendarEnabled(old.getBoolean(context.getString(R.string.pref_calendar_attending_enabled),
                new.attendingCalendarEnabled()))
        new.setAttendingCalendarReminders(old.getStringSet(
                    context.getString(R.string.pref_calendar_attending_reminders),
                    new.attendingCalendarReminders().map{ it.serialize() }.toSet())
                .map{ FBReminder.fromString(it) })

        new.setBirthdayCalendarAllDayReminders(old.getStringSet(
                    context.getString(R.string.pref_calendar_birthday_allday_reminders),
                    new.birthdayCalendarAllDayReminders().map{ it.serialize() }.toSet())
                .map{ FBReminder.fromString(it) })
        new.setBirthdayCalendarColor(old.getInt(context.getString(R.string.pref_calendar_birthday_color),
                new.birthdayCalendarColor()))
        new.setBirthdayCalendarEnabled(old.getBoolean(context.getString(R.string.pref_calendar_birthday_enabled),
                new.birthdayCalendarEnabled()))

        new.setDeclinedCalendarAllDayReminders(
                    old.getStringSet(context.getString(R.string.pref_calendar_declined_allday_reminders),
                    new.declinedCalendarAllDayReminders().map{ it.serialize() }.toSet())
                .map{ FBReminder.fromString(it) })
        new.setDeclinedCalendarColor(old.getInt(context.getString(R.string.pref_calendar_declined_color),
                new.declinedCalendarColor()))
        new.setDeclinedCalendarEnabled(old.getBoolean(context.getString(R.string.pref_calendar_declined_enabled),
                new.declinedCalendarEnabled()))
        new.setDeclinedCalendarReminders(
                    old.getStringSet(context.getString(R.string.pref_calendar_declined_reminders),
                    new.declinedCalendarReminders().map{ it.serialize() }.toSet())
                .map{ FBReminder.fromString(it) })

        new.setMaybeAttendingCalendarAllDayReminders(old.getStringSet(context.getString(R.string.pref_calendar_tentative_allday_reminders),
                new.maybeAttendingCalendarAllDayReminders().map{ it.serialize() }.toSet())
                .map{ FBReminder.fromString(it) })
        new.setMaybeAttendingCalendarColor(old.getInt(context.getString(R.string.pref_calendar_tentative_color),
                new.maybeAttendingCalendarColor()))
        new.setMaybeAttendingCalendarEnabled(old.getBoolean(context.getString(R.string.pref_calendar_tentative_enabled),
                new.maybeAttendingCalendarEnabled()))
        new.setMaybeAttendingCalendarReminders(old.getStringSet(context.getString(R.string.pref_calendar_tentative_reminders),
                new.maybeAttendingCalendarReminders().map{ it.serialize() }.toSet())
                .map{ FBReminder.fromString(it) })

        new.setNotRespondedCalendarAllDayReminders(old.getStringSet(context.getString(R.string.pref_calendar_not_responded_allday_reminders),
                new.notRespondedCalendarAllDayReminders().map{ it.serialize() }.toSet())
                .map{ FBReminder.fromString(it) })
        new.setNotRespondedCalendarColor(old.getInt(context.getString(R.string.pref_calendar_not_responded_color),
                new.notRespondedCalendarColor()))
        new.setNotRespondedCalendarEnabled(old.getBoolean(context.getString(R.string.pref_calendar_not_responded_enabled),
                new.notRespondedCalendarEnabled()))
        new.setNotRespondedCalendarReminders(old.getStringSet(context.getString(R.string.pref_calendar_not_responded_reminders),
                new.notRespondedCalendarReminders().map{ it.serialize() }.toSet())
                .map{ FBReminder.fromString(it) })

        new.setFbLink(old.getBoolean(context.getString(R.string.pref_sync_fblink), new.fbLink()))
        new.setLanguage(old.getString(context.getString(R.string.pref_language), new.language()))
        new.setLastVersion(old.getInt(context.getString(R.string.cfg_last_version), new.lastVersion()))
        new.setSyncsPerHour(old.getInt(context.getString(R.string.cfg_syncs_per_hour), new.syncsPerHour()))
        new.setSyncFrequency(Integer.parseInt(old.getString(context.getString(R.string.pref_sync_frequency), new.syncFrequency().toString())))
    }
}
