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
import org.apache.commons.lang3.BooleanUtils
import cz.dvratil.fbeventsync.preferences.Preferences as FBPrefs

class Preferences(private var mContext: Context) {

    private var mPrefs = FBPrefs(mContext)

    private fun getStringArray(id: Int): Set<String> {
        return mContext.resources.getStringArray(id).toSet()
    }

    internal fun syncFrequency(): Int {
        return Integer.parseInt(mPrefs.getString(mContext.getString(R.string.pref_sync_frequency),
                mContext.getString(R.string.pref_sync_frequency_default_value)))
    }

    internal fun setSyncFrequency(frequency: Int) {
        // For compatiblity with ListPreference sync_freq must be stored as string
        mPrefs.putString(mContext.getString(R.string.pref_sync_frequency), frequency.toString())
    }

    internal fun language(): String {
        return mPrefs.getString(mContext.getString(R.string.pref_language), null) ?: mContext.getString(R.string.pref_language_default_value)
    }

    internal fun setLanguage(lang: String) {
        mPrefs.putString(mContext.getString(R.string.pref_language), lang)
    }

    internal fun fbLink(): Boolean {
        return mPrefs.getBoolean(mContext.getString(R.string.pref_sync_fblink), true)
    }

    internal fun setFbLink(link: Boolean) {
        mPrefs.putBoolean(mContext.getString(R.string.pref_sync_fblink), link)
    }

    internal fun attendingCalendarEnabled(): Boolean {
        return mPrefs.getBoolean(mContext.getString(R.string.pref_calendar_attending_enabled), true)
    }

    internal fun setAttendingCalendarEnabled(enabled: Boolean) {
        mPrefs.putBoolean(mContext.getString(R.string.pref_calendar_attending_enabled), enabled)
    }

    internal fun attendingCalendarReminders(): List<FBReminder> {
        return mPrefs.getStringSet(mContext.getString(R.string.pref_calendar_attending_reminders),
                getStringArray(R.array.pref_reminders_default_value))?.map{ FBReminder.fromString(it) } ?: emptyList()
    }

    internal fun setAttendingCalendarReminders(reminders : List<FBReminder>) {
        mPrefs.putStringSet(mContext.getString(R.string.pref_calendar_attending_reminders),
                reminders.map { it.serialize() }.toSet())
    }

    internal fun attendingCalendarAllDayReminders(): List<FBReminder> {
        return mPrefs.getStringSet(mContext.getString(R.string.pref_calendar_attending_allday_reminders),
                getStringArray(R.array.pref_allday_reminders_default_value))?.map{ FBReminder.fromString(it) } ?: emptyList()
    }

    internal fun setAttendingCalendarAllDayReminders(reminders: List<FBReminder>) {
        mPrefs.putStringSet(mContext.getString(R.string.pref_calendar_attending_allday_reminders),
                reminders.map { it.serialize() }.toSet())
    }

    internal fun attendingCalendarColor(): Int {
        @Suppress("DEPRECATION")
        return mPrefs.getInt(mContext.getString(R.string.pref_calendar_attending_color),
                mContext.resources.getColor(R.color.colorFBBlue))
    }

    internal fun setAttendingCalendarColor(color: Int) {
        mPrefs.putInt(mContext.getString(R.string.pref_calendar_attending_color), color)
    }

    internal fun maybeAttendingCalendarEnabled(): Boolean {
        return mPrefs.getBoolean(mContext.getString(R.string.pref_calendar_tentative_enabled), true)
    }

    internal fun setMaybeAttendingCalendarEnabled(enabled: Boolean) {
        mPrefs.putBoolean(mContext.getString(R.string.pref_calendar_tentative_enabled), enabled)
    }

    internal fun maybeAttendingCalendarReminders(): List<FBReminder> {
        return mPrefs.getStringSet(mContext.getString(R.string.pref_calendar_tentative_reminders),
                getStringArray(R.array.pref_reminders_default_value))?.map{ FBReminder.fromString(it) } ?: emptyList()
    }

    internal fun setMaybeAttendingCalendarReminders(reminders: List<FBReminder>) {
        mPrefs.putStringSet(mContext.getString(R.string.pref_calendar_tentative_reminders),
                reminders.map { it.serialize() }.toSet())
    }

    internal fun maybeAttendingCalendarAllDayReminders(): List<FBReminder> {
        return mPrefs.getStringSet(mContext.getString(R.string.pref_calendar_tentative_allday_reminders),
                getStringArray(R.array.pref_allday_reminders_default_value))?.map{ FBReminder.fromString(it) } ?: emptyList()
    }

    internal fun setMaybeAttendingCalendarAllDayReminders(reminders: List<FBReminder>) {
        mPrefs.putStringSet(mContext.getString(R.string.pref_calendar_tentative_allday_reminders),
                reminders.map{ it.serialize() }.toSet())
    }

    internal fun maybeAttendingCalendarColor(): Int {
        @Suppress("DEPRECATION")
        return mPrefs.getInt(mContext.getString(R.string.pref_calendar_tentative_color),
                mContext.resources.getColor(R.color.colorFBBlue))
    }

    internal fun setMaybeAttendingCalendarColor(color: Int) {
        mPrefs.putInt(mContext.getString(R.string.pref_calendar_tentative_color), color)
    }

    internal fun notRespondedCalendarEnabled(): Boolean {
        return mPrefs.getBoolean(mContext.getString(R.string.pref_calendar_not_responded_enabled), true)
    }

    internal fun setNotRespondedCalendarEnabled(enabled: Boolean) {
        mPrefs.putBoolean(mContext.getString(R.string.pref_calendar_not_responded_enabled), enabled)
    }

    internal fun notRespondedCalendarReminders(): List<FBReminder> {
        return mPrefs.getStringSet(mContext.getString(R.string.pref_calendar_not_responded_reminders),
                getStringArray(R.array.pref_reminders_default_value))?.map{ FBReminder.fromString(it) } ?: emptyList()
    }

    internal fun setNotRespondedCalendarReminders(reminders: List<FBReminder>) {
        mPrefs.putStringSet(mContext.getString(R.string.pref_calendar_not_responded_reminders),
                reminders.map{ it.serialize() }.toSet())
    }

    internal fun notRespondedCalendarAllDayReminders(): List<FBReminder> {
        return mPrefs.getStringSet(mContext.getString(R.string.pref_calendar_not_responded_allday_reminders),
                getStringArray(R.array.pref_allday_reminders_default_value))?.map{ FBReminder.fromString(it) } ?: emptyList()
    }

    internal fun setNotRespondedCalendarAllDayReminders(reminders: List<FBReminder>) {
        mPrefs.putStringSet(mContext.getString(R.string.pref_calendar_not_responded_allday_reminders),
                reminders.map{ it.serialize() }.toSet())
    }

    internal fun notRespondedCalendarColor(): Int {
        @Suppress("DEPRECATION")
        return mPrefs.getInt(mContext.getString(R.string.pref_calendar_not_responded_color),
                mContext.resources.getColor(R.color.colorFBBlue))
    }

    internal fun setNotRespondedCalendarColor(color: Int) {
        mPrefs.putInt(mContext.getString(R.string.pref_calendar_not_responded_color), color)
    }

    internal fun declinedCalendarEnabled(): Boolean {
        return mPrefs.getBoolean(mContext.getString(R.string.pref_calendar_declined_enabled), true)
    }

    internal fun setDeclinedCalendarEnabled(enabled: Boolean) {
        mPrefs.putBoolean(mContext.getString(R.string.pref_calendar_declined_enabled), enabled)
    }

    internal fun declinedCalendarReminders(): List<FBReminder> {
        return mPrefs.getStringSet(mContext.getString(R.string.pref_calendar_declined_reminders),
                getStringArray(R.array.pref_reminders_default_value))?.map{ FBReminder.fromString(it) } ?: emptyList()
    }

    internal fun setDeclinedCalendarReminders(reminders: List<FBReminder>) {
        mPrefs.putStringSet(mContext.getString(R.string.pref_calendar_declined_reminders),
                reminders.map{ it.serialize() }.toSet())
    }

    internal fun declinedCalendarAllDayReminders(): List<FBReminder> {
        return mPrefs.getStringSet(mContext.getString(R.string.pref_calendar_declined_allday_reminders),
                getStringArray(R.array.pref_allday_reminders_default_value))?.map{ FBReminder.fromString(it) } ?: emptyList()
    }

    internal fun setDeclinedCalendarAllDayReminders(reminders: List<FBReminder>) {
        mPrefs.putStringSet(mContext.getString(R.string.pref_calendar_declined_allday_reminders),
                reminders.map{ it.serialize() }.toSet())
    }

    internal fun declinedCalendarColor(): Int {
        @Suppress("DEPRECATION")
        return mPrefs.getInt(mContext.getString(R.string.pref_calendar_declined_color),
                mContext.resources.getColor(R.color.colorFBBlue))
    }

    internal fun setDeclinedCalendarColor(color: Int) {
        mPrefs.putInt(mContext.getString(R.string.pref_calendar_declined_color), color)
    }

    internal fun birthdayCalendarEnabled(): Boolean {
        return mPrefs.getBoolean(mContext.getString(R.string.pref_calendar_birthday_enabled), true)
    }

    internal fun setBirthdayCalendarEnabled(enabled: Boolean) {
        mPrefs.putBoolean(mContext.getString(R.string.pref_calendar_birthday_enabled), enabled)
    }

    internal fun birthdayCalendarAllDayReminders(): List<FBReminder> {
        return mPrefs.getStringSet(mContext.getString(R.string.pref_calendar_birthday_allday_reminders),
                getStringArray(R.array.pref_allday_reminders_default_value))?.map{ FBReminder.fromString(it) } ?: emptyList()
    }

    internal fun setBirthdayCalendarAllDayReminders(reminders: List<FBReminder>) {
        mPrefs.putStringSet(mContext.getString(R.string.pref_calendar_birthday_allday_reminders),
                reminders.map{ it.serialize() }.toSet())
    }

    internal fun birthdayCalendarColor(): Int {
        @Suppress("DEPRECATION")
        return mPrefs.getInt(mContext.getString(R.string.pref_calendar_birthday_color),
                mContext.resources.getColor(R.color.colorFBBlue))
    }

    internal fun setBirthdayCalendarColor(color: Int) {
        mPrefs.putInt(mContext.getString(R.string.pref_calendar_birthday_color), color)
    }

    internal fun lastSync(): Long {
        return mPrefs.getLong(mContext.getString(R.string.cfg_last_sync), 0)
    }

    internal fun setLastSync(lastSync: Long) {
        mPrefs.putLong(mContext.getString(R.string.cfg_last_sync), lastSync)
    }

    internal fun syncsPerHour(): Int {
        return mPrefs.getInt(mContext.getString(R.string.cfg_syncs_per_hour), 0)
    }

    internal fun setSyncsPerHour(syncsPerHour: Int) {
        mPrefs.putInt(mContext.getString(R.string.cfg_syncs_per_hour), syncsPerHour)
    }

    internal fun lastVersion(): Int {
        return mPrefs.getInt(mContext.getString(R.string.cfg_last_version), 0)
    }

    internal fun setLastVersion(lastVersion: Int) {
        mPrefs.putInt(mContext.getString(R.string.cfg_last_version), lastVersion)
    }

    internal fun prefsVersion(): Int {
        return mPrefs.getInt(mContext.getString(R.string.cfg_prefs_version), 0)
    }

    internal fun setPrefsVersion(version: Int) {
        mPrefs.putInt(mContext.getString(R.string.cfg_prefs_version), version)
    }
}
