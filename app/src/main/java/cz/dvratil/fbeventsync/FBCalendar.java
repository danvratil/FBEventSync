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

package cz.dvratil.fbeventsync;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.CalendarContract;

import java.util.HashSet;
import java.util.Set;

public class FBCalendar {
    private String mId;
    private String mType;
    private String mName;

    public static final String TYPE_NOT_REPLIED = "not_replied";
    public static final String TYPE_DECLINED = "declined";
    public static final String TYPE_MAYBE = "maybe";
    public static final String TYPE_ATTENDING = "attending";
    public static final String TYPE_BIRTHDAY = "birthday";

    public FBCalendar(String id, String type, String name) {
        mId = id;
        mType = type;
        mName = name;
    }

    public String id() {
        return mId;
    }

    public String type() {
        return mType;
    }

    public String name() {
        return mName;
    }

    public int availability() {
        switch (mType) {
            case TYPE_NOT_REPLIED:
            case TYPE_DECLINED:
                return CalendarContract.Events.AVAILABILITY_FREE;
            case TYPE_MAYBE:
                return CalendarContract.Events.AVAILABILITY_TENTATIVE;
            case TYPE_ATTENDING:
                return CalendarContract.Events.AVAILABILITY_BUSY;
            default:
                assert(false);
                return 0;
        }
    }

    public Set<Integer> reminderIntervals(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> defaultReminder = new HashSet<>();
        defaultReminder.add(context.getResources().getString(R.string.pref_reminder_default));

        Set<String> as = null;
        switch (mType) {
            case TYPE_NOT_REPLIED:
                as = prefs.getStringSet("pref_not_responded_reminders", defaultReminder);
                break;
            case TYPE_DECLINED:
                as = prefs.getStringSet("pref_declined_reminders", defaultReminder);
                break;
            case TYPE_MAYBE:
                as = prefs.getStringSet("pref_maybe_reminders", defaultReminder);
                break;
            case TYPE_ATTENDING:
                as = prefs.getStringSet("pref_attending_reminders", defaultReminder);
                break;
            case TYPE_BIRTHDAY:
                as = prefs.getStringSet("pref_birthday_reminders", defaultReminder);
        };
        assert(as != null);
        Set<Integer> rv = new HashSet<>();
        for (String s : as) {
            rv.add(Integer.parseInt(s));
        }
        return rv;
    }
}
