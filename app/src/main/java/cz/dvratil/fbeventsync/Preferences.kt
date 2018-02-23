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

import android.content.Context
import android.content.SharedPreferences

import java.util.Arrays
import java.util.HashSet

class Preferences(private var mContext: Context) {

    private var mPrefs: SharedPreferences = mContext.getSharedPreferences(
            mContext.getString(R.string.cz_dvratil_fbeventsync_preferences),
            Context.MODE_MULTI_PROCESS)

    private fun getStringArray(id: Int): Set<String> {
        return HashSet(Arrays.asList(*mContext.resources.getStringArray(id)))
    }

    internal fun syncFrequency(): Int {
        return Integer.parseInt(mPrefs.getString(mContext.getString(R.string.pref_sync_frequency),
                mContext.getString(R.string.pref_sync_frequency_default_value)))
    }

    internal fun language(): String {
        return mPrefs.getString(mContext.getString(R.string.pref_language), mContext.getString(R.string.pref_language_default_value))
    }

    internal fun fbLink(): Boolean {
        return mPrefs.getBoolean(mContext.getString(R.string.pref_sync_fblink), true)
    }

    internal fun attendingCalendarEnabled(): Boolean {
        return mPrefs.getBoolean(mContext.getString(R.string.pref_calendar_attending_enabled), true)
    }

    internal fun attendingCalendarReminders(): Set<String> {
        return mPrefs.getStringSet(mContext.getString(R.string.pref_calendar_attending_reminders),
                getStringArray(R.array.pref_reminders_default_value))
    }

    internal fun attendingCalendarColor(): Int {
        return mPrefs.getInt(mContext.getString(R.string.pref_calendar_attending_color),
                mContext.resources.getColor(R.color.colorFBBlue))
    }

    internal fun maybeAttendingCalendarEnabled(): Boolean {
        return mPrefs.getBoolean(mContext.getString(R.string.pref_calendar_tentative_enabled), true)
    }

    internal fun maybeAttendingCalendarReminders(): Set<String> {
        return mPrefs.getStringSet(mContext.getString(R.string.pref_calendar_tentative_reminders),
                getStringArray(R.array.pref_reminders_default_value))
    }

    internal fun maybeAttendingCalendarColor(): Int {
        return mPrefs.getInt(mContext.getString(R.string.pref_calendar_tentative_color),
                mContext.resources.getColor(R.color.colorFBBlue))
    }

    internal fun notRespondedCalendarEnabled(): Boolean {
        return mPrefs.getBoolean(mContext.getString(R.string.pref_calendar_not_responded_enabled), true)
    }

    internal fun notRespondedCalendarReminders(): Set<String> {
        return mPrefs.getStringSet(mContext.getString(R.string.pref_calendar_not_responded_reminders),
                getStringArray(R.array.pref_reminders_default_value))
    }

    internal fun notRespondedCalendarColor(): Int {
        return mPrefs.getInt(mContext.getString(R.string.pref_calendar_not_responded_color),
                mContext.resources.getColor(R.color.colorFBBlue))
    }

    internal fun declinedCalendarEnabled(): Boolean {
        return mPrefs.getBoolean(mContext.getString(R.string.pref_calendar_declined_enabled), true)
    }

    internal fun declinedCalendarReminders(): Set<String> {
        return mPrefs.getStringSet(mContext.getString(R.string.pref_calendar_declined_reminders),
                getStringArray(R.array.pref_reminders_default_value))
    }

    internal fun declinedCalendarColor(): Int {
        return mPrefs.getInt(mContext.getString(R.string.pref_calendar_declined_color),
                mContext.resources.getColor(R.color.colorFBBlue))
    }

    internal fun birthdayCalendarEnabled(): Boolean {
        return mPrefs.getBoolean(mContext.getString(R.string.pref_calendar_birthday_enabled), true)
    }

    internal fun birthdayCalendarReminders(): Set<String> {
        return mPrefs.getStringSet(mContext.getString(R.string.pref_calendar_birthday_reminders),
                getStringArray(R.array.pref_reminders_default_value))
    }

    internal fun birthdayCalendarColor(): Int {
        return mPrefs.getInt(mContext.getString(R.string.pref_calendar_birthday_color),
                mContext.resources.getColor(R.color.colorFBBlue))
    }

    internal fun lastSync(): Long {
        return mPrefs.getLong(mContext.getString(R.string.cfg_last_sync), 0)
    }

    internal fun setLastSync(lastSync: Long) {
        mPrefs.edit().putLong(mContext.getString(R.string.cfg_last_sync), lastSync).apply()
    }

    internal fun syncsPerHour(): Int {
        return mPrefs.getInt(mContext.getString(R.string.cfg_syncs_per_hour), 0)
    }

    internal fun setSyncsPerHour(syncsPerHour: Int) {
        mPrefs.edit().putInt(mContext.getString(R.string.cfg_syncs_per_hour), syncsPerHour).apply()
    }

    internal fun lastVersion(): Int {
        return mPrefs.getInt(mContext.getString(R.string.cfg_last_version), 0)
    }

    internal fun setLastVersion(lastVersion: Int) {
        mPrefs.edit().putInt(mContext.getString(R.string.cfg_last_version), lastVersion).apply()
    }
}
