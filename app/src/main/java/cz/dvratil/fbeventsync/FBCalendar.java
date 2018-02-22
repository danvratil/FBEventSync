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
import android.database.Cursor;
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

    private static String TAG = "FBCalendar";

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

    protected CalendarType mType = null;
    protected SyncContext mContext = null;
    protected List<FBEvent> mEventsToSync = null;
    protected HashMap<String /* FBID */, Long /* local ID */ > mPastLocalIds = null;
    protected HashMap<String /* FBID */, Long /* local ID */ > mFutureLocalIds = null;
    protected long mLocalCalendarId = -1;
    protected boolean mIsEnabled = false;

    protected class SyncStats {
        int added = 0 ;
        int removed = 0;
        int modified = 0;
    }
    protected SyncStats mSyncStats = new SyncStats();

    public static class Set extends HashMap<CalendarType, FBCalendar> {
        public void initialize(SyncContext ctx) {
            put(CalendarType.TYPE_ATTENDING, new FBCalendar(ctx, CalendarType.TYPE_ATTENDING));
            put(CalendarType.TYPE_MAYBE, new FBCalendar(ctx, CalendarType.TYPE_MAYBE));
            put(CalendarType.TYPE_DECLINED, new FBCalendar(ctx, CalendarType.TYPE_DECLINED));
            put(CalendarType.TYPE_NOT_REPLIED, new FBCalendar(ctx, CalendarType.TYPE_NOT_REPLIED));
            put(CalendarType.TYPE_BIRTHDAY, new FBBirthdayCalendar(ctx));
        }

        FBCalendar getCalendarForEvent(FBEvent event) {
            return get(event.getRSVP());
        }

        public void release() {
            for (FBCalendar calendar : values()) {
                calendar.finalizeSync();
            }
            clear();
        }
    }

    private int getCalendarColor() {
        switch (mType) {
            case TYPE_ATTENDING:
                return mContext.getPreferences().attendingCalendarColor();
            case TYPE_MAYBE:
                return mContext.getPreferences().maybeAttendingCalendarColor();
            case TYPE_NOT_REPLIED:
                return mContext.getPreferences().notRespondedCalendarColor();
            case TYPE_DECLINED:
                return mContext.getPreferences().declinedCalendarColor();
            case TYPE_BIRTHDAY:
                return mContext.getPreferences().birthdayCalendarColor();
            default:
                return -1;
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

        switch (mType) {
            case TYPE_ATTENDING:
                mIsEnabled = mContext.getPreferences().attendingCalendarEnabled();
                break;
            case TYPE_MAYBE:
                mIsEnabled = mContext.getPreferences().maybeAttendingCalendarEnabled();
                break;
            case TYPE_DECLINED:
                mIsEnabled = mContext.getPreferences().declinedCalendarEnabled();
                break;
            case TYPE_NOT_REPLIED:
                mIsEnabled = mContext.getPreferences().notRespondedCalendarEnabled();
                break;
            case TYPE_BIRTHDAY:
                mIsEnabled = mContext.getPreferences().birthdayCalendarEnabled();
                break;
        }

        try {
            mLocalCalendarId = findLocalCalendar();
            if (mLocalCalendarId < 0) {
                if (mIsEnabled) {
                    mLocalCalendarId = createLocalCalendar();
                    mPastLocalIds = new HashMap<>();
                    mFutureLocalIds = new HashMap<>();
                }
            } else {
                if (mIsEnabled) {
                    updateLocalCalendar();
                    mPastLocalIds = fetchLocalPastEvents();
                    mFutureLocalIds = fetchLocalFutureEvents();
                } else {
                    deleteLocalCalendar();
                    mLocalCalendarId = -1;
                }
            }
        } catch (android.os.RemoteException e) {
            mContext.getLogger().error(TAG,"Remote exception on creation: %s", e.getMessage());
        } catch (android.database.sqlite.SQLiteException e) {
            mContext.getLogger().error(TAG,"SQL exception on creation: %s", e.getMessage());
        } catch (NumberFormatException e) {
            mContext.getLogger().error(TAG,"Number exception on creation: %s", e.getMessage());
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
        return mIsEnabled;
    }

    public String name() {
        switch (mType) {
            case TYPE_ATTENDING:
                return mContext.getContext().getString(R.string.calendar_attending_title);
            case TYPE_MAYBE:
                return mContext.getContext().getString(R.string.calendar_tentative_title);
            case TYPE_DECLINED:
                return mContext.getContext().getString(R.string.calendar_declined_title);
            case TYPE_NOT_REPLIED:
                return mContext.getContext().getString(R.string.calendar_not_responded_title);
            case TYPE_BIRTHDAY:
                return mContext.getContext().getString(R.string.calendar_birthday_title);
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
        java.util.Set<String> reminders;
        switch (mType) {
            case TYPE_NOT_REPLIED:
                reminders = mContext.getPreferences().notRespondedCalendarReminders();
                break;
            case TYPE_DECLINED:
                reminders = mContext.getPreferences().declinedCalendarReminders();
                break;
            case TYPE_MAYBE:
                reminders = mContext.getPreferences().maybeAttendingCalendarReminders();
                break;
            case TYPE_ATTENDING:
                reminders = mContext.getPreferences().attendingCalendarReminders();
                break;
            case TYPE_BIRTHDAY:
                reminders = mContext.getPreferences().birthdayCalendarReminders();
                break;
            default:
                return null;
        }
        java.util.Set<Integer> rv = new HashSet<>();
        for (String reminder : reminders) {
            try {
                rv.add(Integer.parseInt(reminder));
            } catch (java.lang.NumberFormatException e) {
                mContext.getLogger().error(TAG, "NumberFormatException when loading reminders. Value was '%s': %s", reminder, e.getMessage());
            }
        }
        return rv;
    }

    public void syncEvent(FBEvent event) {
        if (!mIsEnabled) {
            return;
        }

        mEventsToSync.add(event);
        if (mEventsToSync.size() > 50) {
            sync();
        }
    }

    protected void sync() {
        if (!mIsEnabled) {
            return;
        }

        for (FBEvent event : mEventsToSync) {
            doSyncEvent(event);
        }
        mEventsToSync.clear();
    }

    protected void doSyncEvent(FBEvent event) {
        Long localId = mFutureLocalIds.get(event.eventId());
        if (localId == null) {
            localId = mPastLocalIds.get(event.eventId());
        }
        try {
            if (localId == null) {
                event.create(mContext);
                mSyncStats.added += 1;
            } else {
                event.update(mContext, localId.longValue());
                mSyncStats.modified += 1;
            }
        } catch (android.os.RemoteException e) {
            mContext.getLogger().error(TAG,"Remote exception during FBCalendar sync: %s", e.getMessage());
            // continue with remaining events
        } catch (android.database.sqlite.SQLiteException e) {
            mContext.getLogger().error(TAG,"SQL exception during FBCalendar sync: %s", e.getMessage());
            // continue with remaining events
        }
        mFutureLocalIds.remove(event.eventId());
    }

    public void finalizeSync() {
        if (!mIsEnabled) {
            return;
        }

        sync();
        // Only delete from future events, we want to keep past events at any cost
        Logger log = mContext.getLogger();
        for (Long localId : mFutureLocalIds.values()) {
            try {
                FBEvent.remove(mContext, localId.longValue());
                mSyncStats.removed += 1;
            } catch (android.os.RemoteException e) {
                log.error(TAG,"Remote exception during FBCalendar finalizeSync: %s", e.getMessage());
                // continue with remaining events
            } catch (android.database.sqlite.SQLiteException e) {
                log.error(TAG,"SQL exception during FBCalendar fynalize sync: %s", e.getMessage());
                // continue with remaining events
            }
        }
        mFutureLocalIds.clear();
        mPastLocalIds.clear();

        log.info(TAG, "Sync stats for %s", name());
        log.info(TAG, "    Events added: %d", mSyncStats.added);
        log.info(TAG, "    Events modified: %d", mSyncStats.modified);
        log.info(TAG, "    Events removed: %d", mSyncStats.removed);
    }
}
