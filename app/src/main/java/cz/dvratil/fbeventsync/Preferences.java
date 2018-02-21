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

package cz.dvratil.fbeventsync;

import android.content.Context;
import android.content.SharedPreferences;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Preferences {

    private SharedPreferences mPrefs = null;
    private Context mContext = null;

    private Set<String> getStringArray(int id) {
        return new HashSet<>(Arrays.asList(mContext.getResources().getStringArray(id)));
    }

    public Preferences(Context context) {
        mContext = context;
        mPrefs = context.getSharedPreferences(context.getString(R.string.cz_dvratil_fbeventsync_preferences),
                                              Context.MODE_MULTI_PROCESS);
    }

    int syncFrequency() {
        return Integer.parseInt(mPrefs.getString(mContext.getString(R.string.pref_sync_frequency),
                                                 mContext.getString(R.string.pref_sync_frequency_default_value)));
    }

    String language() {
        return mPrefs.getString(mContext.getString(R.string.pref_language),
                                mContext.getString(R.string.pref_language_default_value));
    }

    boolean fbLink() {
        return mPrefs.getBoolean(mContext.getString(R.string.pref_sync_fblink), true);
    }

    boolean attendingCalendarEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.pref_calendar_attending_enabled), true);
    }

    Set<String> attendingCalendarReminders() {
        return mPrefs.getStringSet(mContext.getString(R.string.pref_calendar_attending_reminders),
                                   getStringArray(R.array.pref_reminders_default_value));
    }

    int attendingCalendarColor() {
        return mPrefs.getInt(mContext.getString(R.string.pref_calendar_attending_color),
                             mContext.getResources().getColor(R.color.colorFBBlue));
    }

    boolean maybeAttendingCalendarEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.pref_calendar_tentative_enabled), true);
    }

    Set<String> maybeAttendingCalendarReminders() {
        return mPrefs.getStringSet(mContext.getString(R.string.pref_calendar_tentative_reminders),
                                   getStringArray(R.array.pref_reminders_default_value));
    }

    int maybeAttendingCalendarColor() {
        return mPrefs.getInt(mContext.getString(R.string.pref_calendar_tentative_color),
                             mContext.getResources().getColor(R.color.colorFBBlue));
    }

    boolean notRespondedCalendarEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.pref_calendar_not_responded_enabled), true);
    }

    Set<String> notRespondedCalendarReminders() {
        return mPrefs.getStringSet(mContext.getString(R.string.pref_calendar_not_responded_reminders),
                                   getStringArray(R.array.pref_reminders_default_value));
    }

    int notRespondedCalendarColor() {
        return mPrefs.getInt(mContext.getString(R.string.pref_calendar_not_responded_color),
                             mContext.getResources().getColor(R.color.colorFBBlue));
    }

    boolean declinedCalendarEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.pref_calendar_declined_enabled), true);
    }

    Set<String> declinedCalendarReminders() {
        return mPrefs.getStringSet(mContext.getString(R.string.pref_calendar_declined_reminders),
                                   getStringArray(R.array.pref_reminders_default_value));
    }

    int declinedCalendarColor() {
        return mPrefs.getInt(mContext.getString(R.string.pref_calendar_declined_color),
                             mContext.getResources().getColor(R.color.colorFBBlue));
    }

    boolean birthdayCalendarEnabled() {
        return mPrefs.getBoolean(mContext.getString(R.string.pref_calendar_birthday_enabled), true);
    }

    Set<String> birthdayCalendarReminders() {
        return mPrefs.getStringSet(mContext.getString(R.string.pref_calendar_birthday_reminders),
                                    getStringArray(R.array.pref_reminders_default_value));
    }

    int birthdayCalendarColor() {
        return mPrefs.getInt(mContext.getString(R.string.pref_calendar_birthday_color),
                             mContext.getResources().getColor(R.color.colorFBBlue));
    }

    long lastSync() {
        return mPrefs.getLong(mContext.getString(R.string.cfg_last_sync), 0);
    }

    void setLasySync(long lastSync) {
        mPrefs.edit().putLong(mContext.getString(R.string.cfg_last_sync), lastSync).apply();
    }

    int syncsPerHour() {
        return mPrefs.getInt(mContext.getString(R.string.cfg_syncs_per_hour), 0);
    }

    void setSyncsPerHour(int syncsPerHour) {
        mPrefs.edit().putInt(mContext.getString(R.string.cfg_syncs_per_hour), syncsPerHour).apply();
    }

    int lastVersion() {
        return mPrefs.getInt(mContext.getString(R.string.cfg_last_version), 0);
    }

    void setLastVersion(int lastVersion) {
        mPrefs.edit().putInt(mContext.getString(R.string.cfg_last_version), lastVersion).apply();
    }
}
