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

import android.accounts.Account;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.CalendarContract;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FBCalendar {

    public enum CalendarType {
        TYPE_ATTENDING("fb_attending_calendar"),
        TYPE_MAYBE("fb_tentative_calendar"),
        TYPE_DECLINED("fb_declined_calendar"),
        TYPE_NOT_REPLIED("fb_not_responded"),
        TYPE_BIRTHDAY("fb_birthday_calendar");

        private final String id;

        CalendarType(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    private CalendarType mType = null;
    private SyncContext mContext = null;
    private List<FBEvent> mEventsToSync = null;
    private HashMap<String /* FBID */, Long /* local ID */ > mLocalIds = null;
    private long mLocalCalendarId = -1;

    public static class Set extends HashMap<CalendarType, FBCalendar> {
        public void initialize(SyncContext ctx) {
            put(CalendarType.TYPE_ATTENDING, new FBCalendar(ctx, CalendarType.TYPE_ATTENDING));
            put(CalendarType.TYPE_MAYBE, new FBCalendar(ctx, CalendarType.TYPE_MAYBE));
            put(CalendarType.TYPE_DECLINED, new FBCalendar(ctx, CalendarType.TYPE_DECLINED));
            put(CalendarType.TYPE_NOT_REPLIED, new FBCalendar(ctx, CalendarType.TYPE_NOT_REPLIED));
            put(CalendarType.TYPE_BIRTHDAY, new FBCalendar(ctx, CalendarType.TYPE_BIRTHDAY));
        }

        FBCalendar getCalendarForEvent(FBEvent event) {
            return get(event.getRSVP());
        }

        public void release() {
            for (Map.Entry<CalendarType, FBCalendar> entry : entrySet()) {
                entry.getValue().finalizeSync();
            }
        }
    }

    private long createLocalCalendar() throws android.os.RemoteException,
                                              android.database.sqlite.SQLiteException,
                                              NumberFormatException {
        Account account = mContext.getAccount();
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Calendars.ACCOUNT_NAME, account.name);
        values.put(CalendarContract.Calendars.ACCOUNT_TYPE, mContext.getContext().getString(R.string.account_type));
        values.put(CalendarContract.Calendars.NAME, id());
        values.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, name());
        values.put(CalendarContract.Calendars.CALENDAR_COLOR, 0x3b5998 /* Facebook blue */);
        values.put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.CAL_ACCESS_READ);
        values.put(CalendarContract.Calendars.OWNER_ACCOUNT, account.name);
        values.put(CalendarContract.Calendars.SYNC_EVENTS, 1);
        // TODO: Figure out how to get local timezone
        //values.put(CalendarContract.Calendars.CALENDAR_TIMEZONE, tz);
        values.put(CalendarContract.Calendars.ALLOWED_REMINDERS,
                String.format("%d,%d", CalendarContract.Reminders.METHOD_DEFAULT,
                        CalendarContract.Reminders.METHOD_ALERT));
        values.put(CalendarContract.Calendars.ALLOWED_AVAILABILITY,
                String.format("%d,%d,%d", CalendarContract.Events.AVAILABILITY_BUSY,
                        CalendarContract.Events.AVAILABILITY_FREE,
                        CalendarContract.Events.AVAILABILITY_TENTATIVE));
        values.put(CalendarContract.Calendars.ALLOWED_ATTENDEE_TYPES,
                String.valueOf(CalendarContract.Attendees.TYPE_NONE));

        Uri uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account.name)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                .build();

        Uri calUri = mContext.getContentProviderClient().insert(uri, values);
        if (calUri != null) {
            return Long.parseLong(calUri.getLastPathSegment());
        } else {
            return -1;
        }
    }

    private long findLocalCalendar() throws android.os.RemoteException,
                                            android.database.sqlite.SQLiteException {
        Cursor cur = mContext.getContentProviderClient().query(
                CalendarContract.Calendars.CONTENT_URI,
                new String[]{ CalendarContract.Calendars._ID },
                String.format("((%s = ?) AND (%s = ?) AND (%s = ?) AND (%s = ?))",
                        CalendarContract.Calendars.ACCOUNT_NAME,
                        CalendarContract.Calendars.ACCOUNT_TYPE,
                        CalendarContract.Calendars.OWNER_ACCOUNT,
                        CalendarContract.Calendars.NAME),
                new String[]{
                        mContext.getAccount().name,
                        mContext.getContext().getString(R.string.account_type),
                        mContext.getAccount().name,
                        mType.id()},
                null);
        long result = -1;
        if (cur != null) {
            if (cur.moveToNext()) {
                result = cur.getLong(0);
            }
            cur.close();
        }
        return result;
    }

    private HashMap<String /* FBID */, Long /* local ID */ > fetchLocalEvents()
            throws android.os.RemoteException,
                   android.database.sqlite.SQLiteException
    {
        HashMap<String, Long> localIds = new HashMap<>();

        Cursor cur = mContext.getContentProviderClient().query(
                CalendarContract.Events.CONTENT_URI,
                new String[]{
                        CalendarContract.Events.UID_2445,
                        CalendarContract.Events._ID },
                String.format("(%s = ?)", CalendarContract.Events.CALENDAR_ID),
                new String[] { String.valueOf(mLocalCalendarId) },
                null);
        if (cur != null) {
            while (cur.moveToNext()) {
                localIds.put(cur.getString(0), cur.getLong(1));
            }
            cur.close();
        }
        return localIds;
    }


    protected FBCalendar(SyncContext context, CalendarType type) {
        mType = type;
        mContext = context;
        mEventsToSync = new ArrayList<>();

        try {
            mLocalCalendarId = findLocalCalendar();
            if (mLocalCalendarId < 0) {
                mLocalCalendarId = createLocalCalendar();
                mLocalIds = new HashMap<>();
            } else {
                mLocalIds = fetchLocalEvents();
            }
        } catch (android.os.RemoteException e) {

        } catch (android.database.sqlite.SQLiteException e) {

        } catch (NumberFormatException e) {

        }
    }

    public String id() {
        return mType.id();
    }

    public long localId() {
        return mLocalCalendarId;
    }

    public CalendarType type() {
        return mType;
    }

    public String name() {
        switch (mType) {
            case TYPE_ATTENDING:
                return mContext.getContext().getString(R.string.cz_dvratil_fbeventsync_attending_calendar);
            case TYPE_MAYBE:
                return mContext.getContext().getString(R.string.cz_dvratil_fbeventsync_tentative_calendar);
            case TYPE_DECLINED:
                return mContext.getContext().getString(R.string.cz_dvratil_fbeventsync_declined_calendar);
            case TYPE_NOT_REPLIED:
                return mContext.getContext().getString(R.string.cz_dvratil_fbeventsync_not_responded_calendar);
            case TYPE_BIRTHDAY:
                return mContext.getContext().getString(R.string.cz_dvratil_fbeventsync_birthday_calendar);
        }
        return null;
    }

    public int availability() {
        switch (mType) {
            case TYPE_NOT_REPLIED:
            case TYPE_DECLINED:
            case TYPE_BIRTHDAY:
                return CalendarContract.Events.AVAILABILITY_FREE;
            case TYPE_MAYBE:
                return CalendarContract.Events.AVAILABILITY_TENTATIVE;
            case TYPE_ATTENDING:
                return CalendarContract.Events.AVAILABILITY_BUSY;
        }
        return CalendarContract.Events.AVAILABILITY_BUSY;
    }

    public java.util.Set<Integer> getReminderIntervals() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext.getContext());
        java.util.Set<String> defaultReminder = new HashSet<>();
        defaultReminder.add(mContext.getContext().getString(R.string.pref_reminder_default));

        java.util.Set<String> as = null;
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
        }
        java.util.Set<Integer> rv = new HashSet<>();
        for (String s : as) {
            rv.add(Integer.parseInt(s));
        }
        return rv;
    }

    public void syncEvent(FBEvent event) {
        mEventsToSync.add(event);
        if (mEventsToSync.size() > 50) {
            sync();
            mEventsToSync.clear();
        }
    }

    private void sync() {
        for (FBEvent event : mEventsToSync) {
            Long localId = mLocalIds.get(event.eventId());
            try {
                if (localId == null) {
                    event.create(mContext);
                } else {
                    event.update(mContext, localId);
                }
            } catch (android.os.RemoteException e) {

            } catch (android.database.sqlite.SQLiteException e) {

            }
            mLocalIds.remove(event.eventId());
        }
    }

    public void finalizeSync() {
        sync();
        for (HashMap.Entry<String, Long> localId : mLocalIds.entrySet()) {
            try {
                FBEvent.remove(mContext, localId.getValue());
            } catch (android.os.RemoteException e) {

            } catch (android.database.sqlite.SQLiteException e) {

            }
        }
        mLocalIds.clear();
    }
}
