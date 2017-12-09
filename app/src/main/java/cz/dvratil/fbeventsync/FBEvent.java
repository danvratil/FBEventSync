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

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.text.format.Time;
import android.util.Log;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import biweekly.component.VEvent;
import biweekly.property.DateEnd;
import biweekly.property.DateStart;
import biweekly.property.Description;
import biweekly.property.Location;
import biweekly.property.Organizer;
import biweekly.property.RawProperty;
import biweekly.util.ICalDate;

public class FBEvent {

    private ContentValues mValues = null;
    private FBCalendar.CalendarType mRSVP = null;
    private FBCalendar mCalendar = null;

    private FBEvent() {
        mValues = new ContentValues();
    }

    public ContentValues getValues() {
        return mValues;
    }

    public FBCalendar.CalendarType getRSVP() {
        return mRSVP;
    }

    public String eventId() {
        return mValues.getAsString(CalendarContract.Events.UID_2445);
    }

    public void setCalendar(FBCalendar calendar) {
        mCalendar = calendar;
        mValues.put(CalendarContract.Events.CALENDAR_ID, calendar.localId());
    }

    protected static String parsePlace(JSONObject event) throws org.json.JSONException {
        List<String> placeStr = new ArrayList<>();
        if (event.has("place")) {
            JSONObject place = event.getJSONObject("place");
            if (place.has("name")) {
                placeStr.add(place.getString("name"));
            }
            if (place.has("location")) {
                List<String> locationStr = new ArrayList<>();
                JSONObject location = place.getJSONObject("location");
                String[] locs = {"street", "city", "zip", "country"};
                for (String loc : locs) {
                    if (location.has(loc)) {
                        locationStr.add(location.getString(loc));
                    }
                }
                if (locationStr.isEmpty()) {
                    if (location.has("longitude")) {
                        locationStr.add(String.valueOf(location.getDouble("longitude")));
                    }
                    if (location.has("latitude")) {
                        locationStr.add(String.valueOf(location.getDouble("latitude")));
                    }
                }
                placeStr.addAll(locationStr);
            }
        }
        return StringUtils.join(placeStr, ", ");
    }

    private static long parseDateTime(String dt) throws java.text.ParseException {
        SimpleDateFormat format;
        if (dt.length() > 8) {
            format = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssZ", Locale.US);
        } else {
            format = new SimpleDateFormat("yyyyMMdd", Locale.US);
        }

        Date date = format.parse(dt);
        return date.getTime();
    }

    public static FBEvent parse(org.json.JSONObject event) throws org.json.JSONException,
                                                                  java.text.ParseException {
        FBEvent fbEvent = new FBEvent();
        ContentValues values = fbEvent.mValues;

        // FIXME: Right now we are abusing UID_2445 to store the Facebook ID - maybe there's a
        // better field for that (ideally an integer-based one)?
        values.put(CalendarContract.Events.UID_2445, event.getString("id"));
        if (event.has("owner")) {
            values.put(CalendarContract.Events.ORGANIZER, event.getJSONObject("owner").getString("name"));
        }
        values.put(CalendarContract.Events.TITLE, event.getString("name"));
        if (event.has("place")) {
            values.put(CalendarContract.Events.EVENT_LOCATION, parsePlace(event));
        }
        if (event.has("description")) {
            String description = event.getString("description");
            description += "\n\nhttps://www.facebook.com/events/" + event.getString("id");
            values.put(CalendarContract.Events.DESCRIPTION, description);
        }
        values.put(CalendarContract.Events.DTSTART, parseDateTime(event.getString("start_time")));
        values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
        if (event.has("end_time")) {
            values.put(CalendarContract.Events.DTEND, parseDateTime(event.getString("end_time")));
            values.put(CalendarContract.Events.EVENT_END_TIMEZONE, TimeZone.getDefault().getID());
        } else {
            // If there's no dt_end, assume 1 hour duration
            values.put(CalendarContract.Events.DURATION, "P1H");
        }
        values.put(CalendarContract.Events.CUSTOM_APP_URI, "fb://event?id=" + event.getString("id"));

        if (event.has("rsvp_status")) {
            String status = event.getString("rsvp_status");
            switch (status) {
                case "attending":
                    fbEvent.mRSVP = FBCalendar.CalendarType.TYPE_ATTENDING;
                    break;
                case "unsure":
                    fbEvent.mRSVP = FBCalendar.CalendarType.TYPE_MAYBE;
                    break;
                case "declined":
                    fbEvent.mRSVP = FBCalendar.CalendarType.TYPE_DECLINED;
                    break;
                case "not_replied":
                    fbEvent.mRSVP = FBCalendar.CalendarType.TYPE_NOT_REPLIED;
                    break;
                default:
                    //logger.warning("SYNC.EVENT", "Unknown RSVP status: %s", status);
            }
        }
        return fbEvent;
    }

    public static FBEvent parse(VEvent vevent) {
        FBEvent fbEvent = new FBEvent();
        ContentValues values = fbEvent.mValues;

        String uid = vevent.getUid().getValue();
        boolean isBirthday = true;
        if (uid.startsWith("e")) { // events
            uid = uid.substring(1, uid.indexOf("@"));
            isBirthday = false;
        }

        values.put(CalendarContract.Events.UID_2445, uid);
        values.put(CalendarContract.Events.TITLE, vevent.getSummary().getValue());
        Description desc = vevent.getDescription();
        if (desc != null) {
            values.put(CalendarContract.Events.DESCRIPTION, desc.getValue());
        }
        Organizer organizer = vevent.getOrganizer();
        if (organizer != null) {
            values.put(CalendarContract.Events.ORGANIZER, organizer.getCommonName());
        }
        Location location = vevent.getLocation();
        if (location != null) {
            values.put(CalendarContract.Events.EVENT_LOCATION, location.getValue());
        }
        values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());

        if (isBirthday) {
            ICalDate date = vevent.getDateStart().getValue();
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.set(date.getYear() + 1900, date.getMonth(), date.getDate(), 0, 0, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            values.put(CalendarContract.Events.DTSTART, calendar.getTimeInMillis());
            // Those are identical for all birthdays, so we hardcode them
            values.put(CalendarContract.Events.ALL_DAY, true);
            values.put(CalendarContract.Events.RRULE, "FREQ=YEARLY");
            values.put(CalendarContract.Events.DURATION, "P1D");

            fbEvent.mRSVP = FBCalendar.CalendarType.TYPE_BIRTHDAY;
        } else {
            DateStart start = vevent.getDateStart();
            values.put(CalendarContract.Events.DTSTART, start.getValue().getTime());
            DateEnd end = vevent.getDateEnd();
            if (end != null) {
                values.put(CalendarContract.Events.DTEND, end.getValue().getTime());
            }

            RawProperty prop = vevent.getExperimentalProperty("PARTSTAT");
            if (prop != null) {
                switch (prop.getValue()) {
                    case "ACCEPTED":
                        fbEvent.mRSVP = FBCalendar.CalendarType.TYPE_ATTENDING;
                        break;
                    case "TENTATIVE":
                        fbEvent.mRSVP = FBCalendar.CalendarType.TYPE_MAYBE;
                        break;
                    case "DECLINED":
                        fbEvent.mRSVP = FBCalendar.CalendarType.TYPE_DECLINED;
                        break;
                    case "NEEDS-ACTION":
                        fbEvent.mRSVP = FBCalendar.CalendarType.TYPE_NOT_REPLIED;
                        break;
                }
            }
        }

        return fbEvent;
    }


    public long create(SyncContext context)
        throws android.os.RemoteException,
               android.database.sqlite.SQLiteException
    {
        Uri uri = context.getContentProviderClient().insert(
                CalendarContract.Events.CONTENT_URI, mValues);
        if (uri != null) {
            long eventId = Long.parseLong(uri.getLastPathSegment());
            Set<Integer> reminders = mCalendar.getReminderIntervals();
            if (!reminders.isEmpty()) {
                createReminders(context, eventId, reminders);
            }

            context.getSyncResult().stats.numInserts++;
            return eventId;
        }

        return -1;
    }

    private HashMap<Integer /* minutes */, Long /* reminder ID */> getLocalReminders(SyncContext context, long localEventId)
        throws android.os.RemoteException,
               android.database.sqlite.SQLiteException
    {
        Cursor cur = context.getContentProviderClient().query(
                CalendarContract.Reminders.CONTENT_URI,
                new String[]{
                        CalendarContract.Reminders._ID,
                        CalendarContract.Reminders.MINUTES },
                String.format("(%s = ?)", CalendarContract.Reminders.EVENT_ID),
                new String[]{ String.valueOf(localEventId) },
                null);
        @SuppressLint("UseSparseArrays")
        HashMap<Integer /* minutes */, Long /* reminder ID */> localReminders = new HashMap<>();
        if (cur != null) {
            while (cur.moveToNext()) {
                localReminders.put(cur.getInt(1), cur.getLong(0));
            }
            cur.close();
        }

        return localReminders;
    }

    private void createReminders(SyncContext context, long localEventId, Set<Integer> reminders)
        throws android.os.RemoteException,
               android.database.sqlite.SQLiteException
    {
        ArrayList<ContentValues> reminderValues = new ArrayList<>();
        for (int reminder : reminders) {
            ContentValues values = new ContentValues();
            values.put(CalendarContract.Reminders.EVENT_ID, localEventId);
            values.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
            values.put(CalendarContract.Reminders.MINUTES, reminder);
            reminderValues.add(values);
        }

        context.getContentProviderClient().bulkInsert(
                CalendarContract.Reminders.CONTENT_URI,
                reminderValues.toArray(new ContentValues[0]));
    }

    private void removeReminder(SyncContext context, long localReminderId)
        throws android.os.RemoteException,
               android.database.sqlite.SQLiteException
    {
        context.getContentProviderClient().delete(
                CalendarContract.Reminders.CONTENT_URI,
                String.format("(%s = ?)", CalendarContract.Reminders._ID ),
                new String[]{ String.valueOf(localReminderId) });
    }

    public void update(SyncContext context, long localEventId)
        throws android.os.RemoteException,
               android.database.sqlite.SQLiteException
    {
        ContentValues values = new ContentValues(mValues);
        values.remove(CalendarContract.Events._ID);
        values.remove(CalendarContract.Events.UID_2445);

        context.getContentProviderClient().update(
                CalendarContract.Events.CONTENT_URI,
                values,
                String.format("(%s = ?)", CalendarContract.Events._ID),
                new String[] { String.valueOf(localEventId) });

        HashMap<Integer /* minutes */, Long /* reminder ID */> reminders = getLocalReminders(context, localEventId);
        Set<Integer> localReminderSet = reminders.keySet();
        Set<Integer> configuredReminders = mCalendar.getReminderIntervals();

        // Silly Java can't even subtract Sets...*sigh*
        Set<Integer> toAdd = new HashSet<>();
        toAdd.addAll(configuredReminders);
        toAdd.removeAll(localReminderSet);

        Set<Integer> toRemove = new HashSet<>();
        toRemove.addAll(localReminderSet);
        toRemove.removeAll(configuredReminders);

        if (!toAdd.isEmpty()) {
            createReminders(context, localEventId, toAdd);
        }
        for (int reminder : toRemove) {
            removeReminder(context, reminders.get(reminder));
        }
    }

    static public void remove(SyncContext context, long localEventId)
        throws android.os.RemoteException,
               android.database.sqlite.SQLiteException
    {
        context.getContentProviderClient().delete(
                CalendarContract.Events.CONTENT_URI,
                String.format("(%s = ?)", CalendarContract.Events._ID),
                new String[] { String.valueOf(localEventId) });
    }
}
