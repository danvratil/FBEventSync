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
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.content.SyncResult;
import android.content.res.Resources;
import android.database.Cursor;
import android.icu.text.SimpleDateFormat;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.util.Log;

import com.facebook.AccessToken;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import java.util.TimeZone;

import org.apache.commons.lang3.StringUtils;

public class CalendarSyncAdapter extends AbstractThreadedSyncAdapter {

    private class FBCalendar {
        private String mId;
        private String mType;
        private String mName;

        public static final String TYPE_NOT_REPLIED = "not_replied";
        public static final String TYPE_DECLINED = "declined";
        public static final String TYPE_MAYBE = "maybe";
        public static final String TYPE_ATTENDING = "attending";

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
            if (mType == TYPE_NOT_REPLIED || mType == TYPE_DECLINED) {
                return CalendarContract.Events.AVAILABILITY_FREE;
            } else if (mType == TYPE_MAYBE) {
                return CalendarContract.Events.AVAILABILITY_TENTATIVE;
            } else if (mType == TYPE_ATTENDING) {
                return CalendarContract.Events.AVAILABILITY_BUSY;
            }
            // FIXME: Unreachable
            return CalendarContract.Events.AVAILABILITY_FREE;
        }
    }
    private FBCalendar[] FB_CALENDARS = null;

    private static final String[] CALENDAR_COLUMNS = new String[] {
            CalendarContract.Calendars._ID,                           // 0
            CalendarContract.Calendars.ACCOUNT_NAME,                  // 1
            CalendarContract.Calendars.NAME,                          // 2
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,         // 3
            CalendarContract.Calendars.OWNER_ACCOUNT                  // 4
    };
    private static final int CALENDAR_ID_COLUMN = 0;
    private static final int CALENDAR_ACCOUNT_NAME_COLUMN = 1;
    private static final int CALENDAR_NAME_COLUMN = 2;
    private static final int CALENDAR_DISPLAY_NAME_COLUMN = 3;
    private static final int CALENDAR_OWNER_ACCOUNT_COLUMN = 4;

    public CalendarSyncAdapter(Context context, boolean autoInitialize) {
        super(context,  autoInitialize);
        Log.d("SYNC", "Sync adapter created");

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

    @Override
    public void onPerformSync(Account account, Bundle bundle, String authority,
                              ContentProviderClient provider, SyncResult syncResult) {
        Log.d("SYNC", "Perform sync request for account " + account.name + ", authority:" + authority);

        for (final FBCalendar calendar : FB_CALENDARS) {
            syncCalendar(calendar, account, provider, syncResult);
        }
    }

    private void syncCalendar(FBCalendar calendar, Account account, ContentProviderClient provider,
                              SyncResult syncResult) {

        long calendarId = findLocalCalendar(calendar, account, provider, syncResult);
        if (calendarId < 0) {
            calendarId = createLocalCalendar(calendar, account, provider, syncResult);
        }
        if (calendarId < 0) {
            return;
        }

        syncCalendarEvents(calendar, calendarId, account, provider, syncResult);
    }

    private long findLocalCalendar(FBCalendar calendar, Account account, ContentProviderClient provider,
                                     SyncResult syncResult) {
        String selection = "((" + CalendarContract.Calendars.ACCOUNT_NAME + " = ?) AND " +
                "(" + CalendarContract.Calendars.ACCOUNT_TYPE + " = ?) AND " +
                "(" + CalendarContract.Calendars.OWNER_ACCOUNT + " = ?) AND " +
                "(" + CalendarContract.Calendars.NAME + "= ?))";

        String[] selectionArgs = new String[] {
                account.name, getContext().getResources().getString(R.string.app_name),
                account.name, calendar.id()
        };

        try {
            Cursor cur = provider.query(CalendarContract.Calendars.CONTENT_URI,
                                        new String[]{ CalendarContract.Calendars._ID },
                                        selection, selectionArgs, null);
            if (!cur.moveToNext()) {
                return -1;
            }

            return cur.getLong(CALENDAR_ID_COLUMN);
        } catch (android.os.RemoteException e) {
            Log.d("SYNC", "RemoteException: " + e.getMessage());
            syncResult.stats.numIoExceptions++;
            return -1;
        }
    }

    private long createLocalCalendar(FBCalendar calendar, Account account, ContentProviderClient provider, SyncResult syncResult) {

        String accountType = getContext().getResources().getString(R.string.app_name);
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Calendars.ACCOUNT_NAME, account.name);
        values.put(CalendarContract.Calendars.ACCOUNT_TYPE, accountType);
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
            Log.e("SYNC", "Failed to create calendar: " + e.getMessage());
            return -1;
        }
    }

    private String getNextCursor(JSONObject obj) {
        try {
            return obj.getJSONObject("paging").getJSONObject("cursor").getString("after");
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    private void syncCalendarEvents(FBCalendar calendar, long calendarId, Account account,
                                    ContentProviderClient provider, SyncResult result) {

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
            GraphResponse response = request.executeAndWait();

            try {
                JSONObject obj = response.getJSONObject();
                JSONArray data = obj.getJSONArray("data");
                for (int i = 0, c = data.length(); i < c; ++i) {
                    createLocalEvent(data.getJSONObject(i), calendar, calendarId, account, provider, result);
                }

                nextCursor = getNextCursor(obj);
            } catch (org.json.JSONException e) {
                break;
            }
        } while (nextCursor != null);
    }

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
            Log.d("SYNC", "Location parsins error: " + e.getMessage());
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

    private void createLocalEvent(JSONObject event, FBCalendar calendar, long calendarId, Account account,
                                  ContentProviderClient provider, SyncResult result)
    {
        ContentValues values = new ContentValues();
        try {
            values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
            if (event.has("owner")) {
                values.put(CalendarContract.Events.ORGANIZER, event.getJSONObject("owner").getString("name"));
            }
            values.put(CalendarContract.Events.TITLE, event.getString("name"));
            if (event.has("location")) {
                values.put(CalendarContract.Events.EVENT_LOCATION, parseLocation(event));
            }
            if (event.has("description")) {
                String description = event.getString("description");
                description += "\n\nhttps://www.facebook.com/events/" + String.valueOf(event.getLong("id"));
                values.put(CalendarContract.Events.DESCRIPTION, description);
            }
            long dtstart = parseDateTime(event.getString("start_time"));
            if (dtstart < 0) {
                Log.e("SYNC", "Failed to prase start_time: " + event.getString("start_time"));
                return;
            }
            values.put(CalendarContract.Events.DTSTART, dtstart);
            values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
            if (event.has("end_time")) {
                long dtend = parseDateTime(event.getString("end_time"));
                if (dtend < 0) {
                    Log.e("SYNC", "Failed to parse end_time: " + event.getString("end_time"));
                    return;
                }
                values.put(CalendarContract.Events.DTEND, dtend);
                values.put(CalendarContract.Events.EVENT_END_TIMEZONE, TimeZone.getDefault().getID());
            } else {
                // If there's no dt_end, assume 1 hour duration
                values.put(CalendarContract.Events.DURATION, "P1H");
            }
            values.put(CalendarContract.Events.AVAILABILITY, calendar.availability());
            values.put(CalendarContract.Events.CUSTOM_APP_URI, "fb://event?id=" + String.valueOf(event.getLong("id")));
        } catch (org.json.JSONException e) {
            Log.d("SYNC", "Event parsing error: " + e.getMessage());
            return;
        }

        try {
            provider.insert(CalendarContract.Events.CONTENT_URI, values);
        } catch (android.os.RemoteException e) {
            Log.e("SYNC", "Failed to create an event: " + e.getMessage());
        }
    }

}
