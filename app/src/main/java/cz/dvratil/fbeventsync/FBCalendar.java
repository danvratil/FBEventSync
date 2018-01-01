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
import android.graphics.Color;
import android.net.Uri;
import android.provider.CalendarContract;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private HashMap<String /* FBID */, Long /* local ID */ > mPastLocalIds = null;
    private HashMap<String /* FBID */, Long /* local ID */ > mFutureLocalIds = null;
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
            clear();
        }
    }

    private int getCalendarColor() {
        int defaultColor = mContext.getContext().getResources().getColor(R.color.colorFBBlue);
        String key;
        switch (mType) {
            case TYPE_ATTENDING:
                key = "pref_attending_color";
                break;
            case TYPE_MAYBE:
                key = "pref_maybe_color";
                break;
            case TYPE_NOT_REPLIED:
                key = "pref_not_replied_color";
                break;
            case TYPE_DECLINED:
                key = "pref_declined_color";
                break;
            case TYPE_BIRTHDAY:
                key = "pref_birthday_color";
                break;
            default:
                return defaultColor;
        }

        return mContext.getPreferences().getInt(key, defaultColor);
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
        values.put(CalendarContract.Calendars.CALENDAR_COLOR, getCalendarColor());
        values.put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.CAL_ACCESS_READ);
        values.put(CalendarContract.Calendars.OWNER_ACCOUNT, account.name);
        values.put(CalendarContract.Calendars.SYNC_EVENTS, 1);
        // TODO: Figure out how to get local timezone
        //values.put(CalendarContract.Calendars.CALENDAR_TIMEZONE, tz);
        values.put(CalendarContract.Calendars.ALLOWED_REMINDERS,
                String.format(Locale.US, "%d,%d", CalendarContract.Reminders.METHOD_DEFAULT,
                        CalendarContract.Reminders.METHOD_ALERT));
        values.put(CalendarContract.Calendars.ALLOWED_AVAILABILITY,
                String.format(Locale.US, "%d,%d,%d", CalendarContract.Events.AVAILABILITY_BUSY,
                        CalendarContract.Events.AVAILABILITY_FREE,
                        CalendarContract.Events.AVAILABILITY_TENTATIVE));
        values.put(CalendarContract.Calendars.ALLOWED_ATTENDEE_TYPES,
                String.valueOf(CalendarContract.Attendees.TYPE_NONE));
        // +2 allows for up to 2 custom reminders set by the user
        values.put(CalendarContract.Calendars.MAX_REMINDERS, getReminderIntervals().size() + 2);

        Uri uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account.name)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, mContext.getContext().getString(R.string.account_type))
                .build();

        Uri calUri = mContext.getContentProviderClient().insert(uri, values);
        if (calUri != null) {
            return Long.parseLong(calUri.getLastPathSegment());
        } else {
            return -1;
        }
    }

    private void updateLocalCalendar() throws android.os.RemoteException,
                                              android.database.sqlite.SQLiteException {
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Calendars.CALENDAR_COLOR, getCalendarColor());
        mContext.getContentProviderClient().update(
                CalendarContract.Calendars.CONTENT_URI,
                values,
                String.format("(%s = ?)", CalendarContract.Calendars._ID),
                new String[] { String.valueOf(mLocalCalendarId) });
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

    public void deleteLocalCalendar() throws android.os.RemoteException,
                                             android.database.sqlite.SQLiteException {
        mContext.getContentProviderClient().delete(
                CalendarContract.Calendars.CONTENT_URI,
                String.format("(%s = ?)", CalendarContract.Calendars._ID),
                new String[] { String.valueOf(mLocalCalendarId) });
    }

    private HashMap<String /* FBID */, Long /* local ID */ > fetchLocalPastEvents()
            throws android.os.RemoteException,
                   android.database.sqlite.SQLiteException

    {
        return fetchLocalEvents(
                String.format("((%s = ?) AND (%s < ?))", CalendarContract.Events.CALENDAR_ID, CalendarContract.Events.DTSTART),
                new String[]{
                        String.valueOf(mLocalCalendarId),
                        String.valueOf(Calendar.getInstance().getTimeInMillis())
                });
    }

    private HashMap<String /* FBID */, Long /* local ID */ > fetchLocalFutureEvents()
            throws android.os.RemoteException,
                   android.database.sqlite.SQLiteException
    {
        return fetchLocalEvents(
                String.format("((%s = ?) AND (%s >= ?))", CalendarContract.Events.CALENDAR_ID, CalendarContract.Events.DTSTART),
                new String[] {
                        String.valueOf(mLocalCalendarId),
                        String.valueOf(Calendar.getInstance().getTimeInMillis())
                });
    }

    private HashMap<String /* FBID */, Long /* local ID */ > fetchLocalEvents(
            String selectorQuery, String[] selectorValues)
            throws android.os.RemoteException,
                   android.database.sqlite.SQLiteException
    {
        HashMap<String, Long> localIds = new HashMap<>();

        // HACK: Only select future events: Facebook will remove the events from the listing once
        // they pass, but it's desirable that we keep them in the calendar. The only way to achieve
        // so is to ignore them
        Cursor cur = mContext.getContentProviderClient().query(
                CalendarContract.Events.CONTENT_URI,
                new String[]{
                        CalendarContract.Events.UID_2445,
                        CalendarContract.Events._ID },
                selectorQuery, selectorValues,
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
                if (isEnabled()) {
                    mLocalCalendarId = createLocalCalendar();
                    mPastLocalIds = new HashMap<>();
                    mFutureLocalIds = new HashMap<>();
                }
            } else {
                if (isEnabled()) {
                    updateLocalCalendar();
                    mPastLocalIds = fetchLocalPastEvents();
                    mFutureLocalIds = fetchLocalFutureEvents();
                } else {
                    deleteLocalCalendar();
                    mLocalCalendarId = -1;
                }
            }
        } catch (android.os.RemoteException e) {
            mContext.getLogger().error("FBCalendar","Remote exception on creation: %s", e.getMessage());
        } catch (android.database.sqlite.SQLiteException e) {
            mContext.getLogger().error("FBCalendar","SQL exception on creation: %s", e.getMessage());
        } catch (NumberFormatException e) {
            mContext.getLogger().error("FBCalendar","Number exception on creation: %s", e.getMessage());
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

    public boolean isEnabled() {
        String name;
        switch (mType) {
            case TYPE_ATTENDING:
                name = "pref_attending_enabled";
                break;
            case TYPE_MAYBE:
                name = "pref_tentative_enabled";
                break;
            case TYPE_DECLINED:
                name = "pref_declined_enabled";
                break;
            case TYPE_NOT_REPLIED:
                name = "pref_not_responded_enabled";
                break;
            case TYPE_BIRTHDAY:
                name = "pref_birthday_enabled";
                break;
            default:
                mContext.getLogger().error("FBCalendar", "Unhandled calendar type");
                return true;
        }
        return mContext.getPreferences().getBoolean(name, true);
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
        SharedPreferences prefs = mContext.getPreferences();
        java.util.Set<String> defaultReminder = new HashSet<>();
        for (String reminder : mContext.getContext().getResources().getStringArray(R.array.pref_reminder_default)) {
            defaultReminder.add(reminder);
        }

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
        if (!isEnabled()) {
            return;
        }

        mEventsToSync.add(event);
        if (mEventsToSync.size() > 50) {
            sync();
            mEventsToSync.clear();
        }
    }

    private void sync() {
        if (!isEnabled()) {
            return;
        }

        for (FBEvent event : mEventsToSync) {
            Long localId = mFutureLocalIds.get(event.eventId());
            if (localId == null) {
                localId = mPastLocalIds.get(event.eventId());
            }
            try {
                if (localId == null) {
                    event.create(mContext);
                } else {
                    event.update(mContext, localId.longValue());
                }
            } catch (android.os.RemoteException e) {
                mContext.getLogger().error("FBCalendar","Remote exception during FBCalendar sync: %s", e.getMessage());
                // continue with remaining events
            } catch (android.database.sqlite.SQLiteException e) {
                mContext.getLogger().error("FBCalendar","SQL exception during FBCalendar sync: %s", e.getMessage());
                // continue with remaining events
            }
            mFutureLocalIds.remove(event.eventId());
        }
    }

    public void finalizeSync() {
        if (!isEnabled()) {
            return;
        }

        sync();
        // Only delete from future events, we want to keep past events at any cost
        for (Long localId : mFutureLocalIds.values()) {
            try {
                FBEvent.remove(mContext, localId.longValue());
            } catch (android.os.RemoteException e) {
                mContext.getLogger().error("FBCalendar","Remote exception during FBCalendar finalizeSync: %s", e.getMessage());
                // continue with remaining events
            } catch (android.database.sqlite.SQLiteException e) {
                mContext.getLogger().error("FBCalendar","SQL exception during FBCalendar fynalize sync: %s", e.getMessage());
                // continue with remaining events
            }
        }
        mFutureLocalIds.clear();
        mPastLocalIds.clear();
    }
}
