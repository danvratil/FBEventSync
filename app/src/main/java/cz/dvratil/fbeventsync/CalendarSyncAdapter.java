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

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncRequest;
import android.content.SyncResult;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.CalendarContract;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;

import com.facebook.AccessToken;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Date;
import java.util.Set;
import java.util.TimeZone;
import java.text.SimpleDateFormat;
import java.util.HashSet;

import org.apache.commons.lang3.StringUtils;

public class CalendarSyncAdapter extends AbstractThreadedSyncAdapter {

    private FBCalendar[] FB_CALENDARS = null;

    private Logger logger = null;

    public CalendarSyncAdapter(Context context, boolean autoInitialize) {
        super(context,  autoInitialize);

        logger = Logger.getInstance(context);

        checkPermissions();

        Resources res = context.getResources();
        FB_CALENDARS = new FBCalendar[] {
            new FBCalendar("fb_not_responded",      FBCalendar.TYPE_NOT_REPLIED,
                           res.getString(R.string.cz_dvratil_fbeventsync_not_responded_calendar)),
            new FBCalendar("fb_declined_calendar",  FBCalendar.TYPE_DECLINED,
                           res.getString(R.string.cz_dvratil_fbeventsync_declined_calendar)),
            new FBCalendar("fb_tentative_calendar", FBCalendar.TYPE_MAYBE,
                           res.getString(R.string.cz_dvratil_fbeventsync_tentative_calendar)),
            new FBCalendar("fb_attending_calendar", FBCalendar.TYPE_ATTENDING,
                           res.getString(R.string.cz_dvratil_fbeventsync_attending_calendar))
        };
    }

    static public void requestSync(Context context) {
        String accountType = context.getResources().getString(R.string.account_type);
        Logger logger = Logger.getInstance(context);
        for (Account account : AccountManager.get(context).getAccountsByType(accountType)){
            Bundle extras = new Bundle();
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
            ContentResolver.requestSync(account, context.getString(R.string.content_authority), extras);
            logger.info("SYNC", "Explicitly requested sync for account %s", account.name);
        }
    }

    static public void updateSync(Context context) {
        String accountType = context.getResources().getString(R.string.account_type);

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        String syncFreq = pref.getString("pref_sync_frequency",
                                         context.getResources().getString(R.string.pref_sync_frequency_default));
        assert(syncFreq != null);
        int syncInterval = Integer.parseInt(syncFreq);

        Logger logger = Logger.getInstance(context);

        for (Account account : AccountManager.get(context).getAccountsByType(accountType)) {
            Bundle extras = new Bundle();
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL,true);
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
            ContentResolver.requestSync(account, context.getString(R.string.content_authority), extras);

            // Schedule periodic sync based on current configuration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                // we can enable inexact timers in our periodic sync
                SyncRequest request = new SyncRequest.Builder()
                        .syncPeriodic(syncInterval, syncInterval / 3)
                        .setSyncAdapter(account, context.getString(R.string.content_authority))
                        .setExtras(new Bundle()).build();
                ContentResolver.requestSync(request);
                logger.info("SYNC.SCHED",
                            "Scheduled periodic sync for account %s using requestSync",
                            account.name);
            } else {
                ContentResolver.addPeriodicSync(account, context.getString(R.string.content_authority), new Bundle(), syncInterval);
                logger.info("SYNC.SCHED",
                            "Scheduled periodic sync for account %s using addPeriodicSync",
                            account.name);
            }
        }
    }

    @Override
    public void onPerformSync(Account account, Bundle bundle, String authority,
                              ContentProviderClient provider, SyncResult syncResult) {
        logger.info("SYNC", "performSync request for account %s, authority %s", account.name, authority);

        if (!checkPermissions()) {
            return;
        }

        for (final FBCalendar calendar : FB_CALENDARS) {
            syncCalendar(calendar, account, provider, syncResult);
        }

        logger.info("SYNC", "Sync for %s done", account.name);
    }

    private void syncCalendar(FBCalendar calendar, Account account, ContentProviderClient provider,
                              SyncResult syncResult) {

        logger.debug("SYNC.CAL", "=== START: Calendar sync for \"%s\"", calendar.id());

        long calendarId = findLocalCalendar(calendar, account, provider, syncResult);
        if (calendarId < 0) {
            logger.debug("SYNC.CAL","Local calendar does not exist, will create a new one");
            calendarId = createLocalCalendar(calendar, account, provider, syncResult);
        } else {
            logger.debug("SYNC.CAL","Found local calendar (ID: %d)", calendarId);
        }
        if (calendarId < 0) {
            logger.debug("SYNC.CAL","Creating local calendar failed, skipping event sync");
            return;
        }

        syncCalendarEvents(calendar, calendarId, account, provider, syncResult);

        logger.debug("SYNC.CAL", "=== END: Calendar sync for \"%s\"", calendar.id());
    }

    private long findLocalCalendar(FBCalendar calendar, Account account, ContentProviderClient provider,
                                     SyncResult syncResult) {
        String selection = "((" + CalendarContract.Calendars.ACCOUNT_NAME + " = ?) AND " +
                "(" + CalendarContract.Calendars.ACCOUNT_TYPE + " = ?) AND " +
                "(" + CalendarContract.Calendars.OWNER_ACCOUNT + " = ?) AND " +
                "(" + CalendarContract.Calendars.NAME + "= ?))";

        String[] selectionArgs = new String[] {
                account.name, getContext().getResources().getString(R.string.account_type),
                account.name, calendar.id()
        };

        try {
            Cursor cur = provider.query(CalendarContract.Calendars.CONTENT_URI,
                                        new String[]{ CalendarContract.Calendars._ID },
                                        selection, selectionArgs, null);
            if (!cur.moveToNext()) {
                return -1;
            }

            return cur.getLong(0);
        } catch (android.os.RemoteException e) {
            logger.error("SYNC.CAL", "findLocalCalendar RemoteException: %s", e.getMessage());
            syncResult.stats.numIoExceptions++;
            return -1;
        } catch (android.database.sqlite.SQLiteException e) {
            logger.error("SYNC.CAL","findLocalCalendar SQLiteException: %s", e.getMessage());
            syncResult.stats.numIoExceptions++;
            return -1;
        }
    }

    @Nullable
    private HashMap<Long /* FBID */, Long /* DBID */> findLocalEvents(long calendarId, Account account, ContentProviderClient provider) {
        Cursor cur = null;
        try {
            cur = provider.query(CalendarContract.Events.CONTENT_URI,
                                 new String[]{ CalendarContract.Events.UID_2445,
                                               CalendarContract.Events._ID },
                                 "(" + CalendarContract.Events.CALENDAR_ID + " = ?)",
                                 new String[]{ String.valueOf(calendarId) }, null);
        } catch (android.os.RemoteException e) {
            logger.error("SYNC.EVENT", "findLocalEvents RemoteException: %s", e.getMessage());
            return null;
        } catch (android.database.sqlite.SQLiteException e) {
            logger.error("SYNC.EVENT", "findLocalEvents SQLiteException: %s", e.getMessage());
            return null;
        }

        HashMap<Long, Long> ids = new HashMap<Long, Long>();
        while (cur != null && cur.moveToNext()) {
            ids.put(Long.parseLong(cur.getString(0)),
                    cur.getLong(1));
        }
        return ids;
    }

    private long createLocalCalendar(FBCalendar calendar, Account account, ContentProviderClient provider, SyncResult syncResult) {

        ContentValues values = new ContentValues();
        values.put(CalendarContract.Calendars.ACCOUNT_NAME, account.name);
        values.put(CalendarContract.Calendars.ACCOUNT_TYPE, getContext().getString(R.string.account_type));
        values.put(CalendarContract.Calendars.NAME, calendar.id());
        values.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, calendar.name());
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

        try {
            Uri calUri = provider.insert(uri, values);
            return Long.parseLong(calUri.getLastPathSegment());
        } catch (android.os.RemoteException e) {
            logger.error("SYNC.CAL", "createLocalCalendar RemoteException: %s", e.getMessage());
            return -1;
        } catch (android.database.sqlite.SQLiteException e) {
            logger.error("SYNC.CAL","createLocalCalendar SQLiteException: %s", e.getMessage());
            return -1;
        }
    }

    @Nullable
    private String getNextCursor(JSONObject obj) {
        try {
            JSONObject paging = obj.getJSONObject("paging");
            if (paging == null) {
                return null;
            }
            JSONObject cursor = paging.getJSONObject("cursor");
            if (cursor == null) {
                return null;
            }
            return cursor.getString("after");
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    private void syncCalendarEvents(FBCalendar calendar, long localCalendarId, Account account,
                                    ContentProviderClient provider, SyncResult result) {

        logger.debug("SYNC.EVENTS","==== START event sync");

        // TODO: Query existing event IDs, so we can decide whether to insert, modify or remove
        HashMap<Long /* FBID */, Long /* DBID */> knownIds = findLocalEvents(localCalendarId, account, provider);
        if (knownIds == null) {
            // We failed to query events, so don't event attempt to sync them
            logger.debug("SYNC.EVENTS", "==== END event sync");
            return;
        }

        String nextCursor = null;
        do {
            Bundle params = new Bundle();
            params.putString(GraphRequest.FIELDS_PARAM, "id,name,description,place,start_time,end_time,owner,is_canceled");
            params.putString("type", calendar.type());
            if (nextCursor != null) {
                params.putString("after", nextCursor);
            }

            GraphRequest request = new GraphRequest();
            request.setAccessToken(AccessToken.getCurrentAccessToken());
            request.setGraphPath("/me/events");
            request.setParameters(params);
            logger.debug("SYNC.EVENTS","Sending Graph request...");
            GraphResponse response = request.executeAndWait();
            logger.debug("SYNC.EVENTS","Response from Facebook arrived");

            try {
                JSONObject obj = response.getJSONObject();
                if (obj != null) {
                    JSONArray data = obj.getJSONArray("data");
                    for (int i = 0, c = data.length(); i < c; ++i) {
                        JSONObject event = data.getJSONObject(i);
                        long id = Long.parseLong(event.getString("id"));
                        if (knownIds.containsKey(id)) {
                            updateLocalEvent(event, knownIds.get(id), calendar, localCalendarId, account, provider, result);
                            knownIds.remove(id);
                        } else {
                            createLocalEvent(event, calendar, localCalendarId, account, provider, result);
                        }
                    }

                    nextCursor = getNextCursor(obj);
                }
            } catch (org.json.JSONException e) {
                logger.error("SYNC.EVENTS","JSON Exception: %s" + e.getMessage());
                break;
            }
        } while (nextCursor != null);

        removeLocalEvents(knownIds, localCalendarId, account, provider, result);

        logger.debug("SYNC.EVENTS","==== END event sync");
    }

    @Nullable
    private String parseLocation(JSONObject event) {
        try {
            List<String> locationStr = new ArrayList<String>();
            if (event.has("place")) {
                JSONObject place = event.getJSONObject("place");
                if (place.has("name")) {
                    locationStr.add(place.getString("name"));
                }
                if (place.has("location")) {
                    JSONObject location = place.getJSONObject("location");
                    String[] locs = {"street", "city", "zip", "country"};
                    for (String loc : locs) {
                        if (location.has(loc)) {
                            locationStr.add(location.getString(loc));
                        }
                    }
                    if (locationStr.isEmpty()) {
                        if (place.has("longitude")) {
                            locationStr.add(String.valueOf(place.getDouble("longitude")));
                        }
                        if (place.has("latitude")) {
                            locationStr.add(String.valueOf(place.getDouble("latitude")));
                        }
                    }
                }
            }
            return StringUtils.join(locationStr, ", ");
        } catch (org.json.JSONException e) {
            logger.error("SYNC.EVENT", "parseLocation JSONException: %s", e.getMessage());
            return null;
        }
    }

    private long parseDateTime(String dt)
    {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssZ");
        try {
            Date date = format.parse(dt);
            return date.getTime();
        } catch (java.text.ParseException e) {
            return -1;
        }
    }

    @Nullable
    private ContentValues parseEvent(JSONObject event, FBCalendar calendar, long localCalendarId,
                                     Account account)
    {
        ContentValues values =  new ContentValues();
        try {
            // FIXME: Right now we are abusing UID_2445 to store the Facebook ID - maybe there's a
            // better field for that (ideally an integer-based one)?
            values.put(CalendarContract.Events.UID_2445, event.getString("id"));
            values.put(CalendarContract.Events.CALENDAR_ID, localCalendarId);
            if (event.has("owner")) {
                values.put(CalendarContract.Events.ORGANIZER, event.getJSONObject("owner").getString("name"));
            }
            values.put(CalendarContract.Events.TITLE, event.getString("name"));
            if (event.has("location")) {
                values.put(CalendarContract.Events.EVENT_LOCATION, parseLocation(event));
            }
            if (event.has("description")) {
                String description = event.getString("description");
                description += "\n\nhttps://www.facebook.com/events/" + event.getString("id");
                values.put(CalendarContract.Events.DESCRIPTION, description);
            }
            long dtstart = parseDateTime(event.getString("start_time"));
            if (dtstart < 0) {
                logger.error("SYNC.EVENT", "Failed to parse start_time: %s", event.getString("start_time"));
                return null;
            }
            values.put(CalendarContract.Events.DTSTART, dtstart);
            values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
            if (event.has("end_time")) {
                long dtend = parseDateTime(event.getString("end_time"));
                if (dtend < 0) {
                    logger.error("SYNC.EVENT", "Failed to parse end_time: %s", event.getString("end_time"));
                    return null;
                }
                values.put(CalendarContract.Events.DTEND, dtend);
                values.put(CalendarContract.Events.EVENT_END_TIMEZONE, TimeZone.getDefault().getID());
            } else {
                // If there's no dt_end, assume 1 hour duration
                values.put(CalendarContract.Events.DURATION, "P1H");
            }
            values.put(CalendarContract.Events.AVAILABILITY, calendar.availability());
            values.put(CalendarContract.Events.CUSTOM_APP_URI, "fb://event?id=" + event.getString("id"));

        } catch (org.json.JSONException e) {
            logger.error("SYNC.EVENT", "parseEvent JSONException: %s", e.getMessage());
            return null;
        }
        return values;
    }

    private void updateLocalEvent(JSONObject event, Long localEventId, FBCalendar calendar, long localCalendarId,
                                  Account account, ContentProviderClient provider, SyncResult result)
    {
        logger.debug("SYNC.EVENT","====== START Update local event %d", localEventId);
        ContentValues values = parseEvent(event, calendar, localCalendarId, account);
        if (values == null) {
            logger.debug("SYNC.EVENT","====== END Update local event %d", localEventId);
            result.stats.numParseExceptions++;
            return;
        }

        try {
            String selection = "((" + CalendarContract.Events.CALENDAR_ID + " = ?) AND " +
                                "(" + CalendarContract.Events.UID_2445 + " = ?))";
            String selectionArgs[] = { String.valueOf(localCalendarId),
                                       event.getString("id") };
            provider.update(CalendarContract.Events.CONTENT_URI, values, selection, selectionArgs);
            logger.debug("SYNC.EVENT","Event updated");

            logger.debug("SYNC.EVENT", "Querying reminders...");
            Cursor cur = provider.query(CalendarContract.Reminders.CONTENT_URI,
                                         new String[]{ CalendarContract.Reminders._ID,
                                                       CalendarContract.Reminders.MINUTES },
                                         "(" + CalendarContract.Reminders.EVENT_ID + " = ?)",
                                         new String[]{ String.valueOf(localEventId) }, null);
            HashMap<Integer /* minutes */, Long /* reminder ID */> localReminders = new HashMap<Integer, Long>();
            while (cur.moveToNext()) {
                localReminders.put(cur.getInt(1), cur.getLong(0));
            }


            Set<Integer> localReminderSet = localReminders.keySet();
            Set<Integer> configuredReminders = calendar.reminderIntervals(getContext());

            // Silly Java can't even subtract Sets...*sigh*
            Set<Integer> toAdd = new HashSet<Integer>();
            toAdd.addAll(configuredReminders);
            toAdd.removeAll(localReminderSet);

            Set<Integer> toRemove = new HashSet<Integer>();
            toRemove.addAll(localReminderSet);
            toRemove.removeAll(configuredReminders);

            if (!toAdd.isEmpty()) {
                createReminders(localEventId, toAdd, provider);
            }
            if (!toRemove.isEmpty()) {
                for (int reminder : toRemove) {
                    removeReminder(localEventId, localReminders.get(reminder), provider);
                }
            }

            result.stats.numUpdates++;
        } catch (android.os.RemoteException e) {
            logger.error("SYNC.EVENT", "updateLocalEvent RemoteException: %s", e.getMessage());
            result.stats.numIoExceptions++;
        } catch (org.json.JSONException e) {
            logger.error("SYNC.EVENT", "updateLocalEvent JSONException: %s", e.getMessage());
            result.stats.numParseExceptions++;
        } catch (android.database.sqlite.SQLiteException e) {
            logger.error("SYNC.EVENT", "updateLocalEvent SQLiteException: %s", e.getMessage());
            result.stats.numParseExceptions++;
        }

        logger.debug("SYNC.EVENT","====== END Update local event %d", localEventId);
    }

    private void createReminders(long localEventId, Set<Integer> reminders, ContentProviderClient provider) {
        ArrayList<ContentValues> reminderValues = new ArrayList<ContentValues>();
        for (int reminder : reminders) {
            ContentValues values = new ContentValues();
            values.put(CalendarContract.Reminders.EVENT_ID, localEventId);
            values.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
            values.put(CalendarContract.Reminders.MINUTES, reminder);
            reminderValues.add(values);
        }
        try {
            logger.debug("SYNC.REM", "Creating reminders %s for event %d", reminders.toString(), localEventId);
            provider.bulkInsert(CalendarContract.Reminders.CONTENT_URI, reminderValues.toArray(new ContentValues[0]));
        } catch (android.os.RemoteException e) {
            logger.error("SYNC.REM", "createReminders RemoteException: %s", e.getMessage());
        } catch (android.database.sqlite.SQLiteException e) {
            logger.error("SYNC.REM", "createReminders SQLiteException: %s", e.getMessage());
        }
    }

    private void removeReminder(long localEventId, long reminderId, ContentProviderClient provider) {
        try {
            logger.debug("SYNC.REM", "Removing reminder ID %d from event %d", reminderId, localEventId);
            provider.delete(CalendarContract.Reminders.CONTENT_URI,
                            "((" + CalendarContract.Reminders.EVENT_ID + " = ?) AND (" + CalendarContract.Reminders._ID + " = ?))",
                            new String[]{String.valueOf(localEventId), String.valueOf(reminderId)});
        } catch (android.os.RemoteException e) {
            logger.error("SYNC.REM", "removeReminder RemoteException: %s", e.getMessage());
        } catch (android.database.sqlite.SQLiteException e) {
            logger.error("SYNC.REM", "removeReminder SQLiteException: %s", e.getMessage());
        }
    }

    private void createLocalEvent(JSONObject event, FBCalendar calendar, long localCalendarId, Account account,
                                  ContentProviderClient provider, SyncResult result)
    {
        logger.debug("SYNC.EVENT","====== START Creating new local event");
        ContentValues values = parseEvent(event, calendar, localCalendarId, account);
        if (values == null) {
            result.stats.numParseExceptions++;
            logger.debug("SYNC.EVENT","====== END Creating new local event");
            return;
        }

        try {
            Uri uri = provider.insert(CalendarContract.Events.CONTENT_URI, values);
            long eventId = Long.parseLong(uri.getLastPathSegment());
            logger.debug("SYNC.EVENT", "Stored new event as %d", eventId);
            Set<Integer> reminders = calendar.reminderIntervals(getContext());
            if (!reminders.isEmpty()) {
                createReminders(eventId, reminders, provider);
            }

            result.stats.numInserts++;
        } catch (android.os.RemoteException e) {
            logger.error("SYNC.EVENT", "createLocalEvent RemoteException: %s", e.getMessage());
            result.stats.numIoExceptions++;
        } catch (android.database.sqlite.SQLiteException e) {
            logger.error("SYNC.EVENT", "createLocalEvent SQLiteException: %s", e.getMessage());
            result.stats.numIoExceptions++;
        }

    }

    private void removeLocalEvents(HashMap<Long /* FBID */, Long /* DBID */> eventIds, long localCalendarId, Account account,
                                   ContentProviderClient provider, SyncResult result)
    {
        String selection = "((" + CalendarContract.Events.CALENDAR_ID + " = ?) AND " +
                            "(" + CalendarContract.Events._ID + " = ?))";
        Iterator it = eventIds.entrySet().iterator();
        logger.debug("SYNC.EVENTS","Removing local events %s", eventIds.values().toString());
        while (it.hasNext()) {
            try {
                HashMap.Entry entry = (HashMap.Entry)it.next();
                String selectionArgs[] = { String.valueOf(localCalendarId),
                                           String.valueOf(entry.getValue()) };
                provider.delete(CalendarContract.Events.CONTENT_URI, selection, selectionArgs);
                result.stats.numDeletes++;
            } catch (android.os.RemoteException e) {
                logger.error("SYNC.EVENTS", "removeLocalEvents RemoteException: %s", e.getMessage());
                result.stats.numIoExceptions++;
            } catch (android.database.sqlite.SQLiteException e) {
                logger.error("SYNC.EVENTS", "removeLocalEvents SQLiteException: %s ", e.getMessage());
                result.stats.numIoExceptions++;
            }
        }
    }

    private boolean checkPermissions()
    {
        ArrayList<String> missingPermissions = new ArrayList<String>();
        int permissionCheck = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_CALENDAR);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.READ_CALENDAR);
            missingPermissions.add(Manifest.permission.WRITE_CALENDAR);
        }
        permissionCheck = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_SYNC_SETTINGS);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.ACCOUNT_MANAGER);
            missingPermissions.add(Manifest.permission.READ_SYNC_SETTINGS);
            missingPermissions.add(Manifest.permission.WRITE_SYNC_SETTINGS);
        }

        logger.info("SYNC.PERM", "Missing permissions: " + missingPermissions.toString());

        if (!missingPermissions.isEmpty()) {
            Bundle extras = new Bundle();
            extras.putStringArrayList(PermissionRequestActivity.MISSING_PERMISSIONS, missingPermissions);

            Intent intent = new Intent(getContext(), PermissionRequestActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtras(extras);
            getContext().startActivity(intent);
            return false;
        }
        return true;
    }
}
