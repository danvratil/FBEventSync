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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncRequest;
import android.content.SyncResult;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.CalendarContract;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;

import com.loopj.android.http.DataAsyncHttpResponseHandler;
import com.loopj.android.http.RequestHandle;
import com.loopj.android.http.RequestParams;

import cz.msebera.android.httpclient.Header;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;

public class CalendarSyncAdapter extends AbstractThreadedSyncAdapter {

    private Logger logger = null;

    SyncContext mSyncContext = null;

    public CalendarSyncAdapter(Context context, boolean autoInitialize) {
        super(context,  autoInitialize);

        logger = Logger.getInstance(context);

        checkPermissions();
    }

    static public void requestSync(Context context) {
        String accountType = context.getResources().getString(R.string.account_type);
        Logger logger = Logger.getInstance(context);
        for (Account account : AccountManager.get(context).getAccountsByType(accountType)){
            Bundle extras = new Bundle();
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
            ContentResolver.requestSync(account, CalendarContract.AUTHORITY, extras);
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

            ContentResolver.cancelSync(account, CalendarContract.AUTHORITY);
            if (syncInterval == 0) {
                continue;
            }

            // Schedule periodic sync based on current configuration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                // we can enable inexact timers in our periodic sync
                SyncRequest request = new SyncRequest.Builder()
                        .syncPeriodic(syncInterval, syncInterval / 3)
                        .setSyncAdapter(account, CalendarContract.AUTHORITY)
                        .setExtras(new Bundle()).build();
                ContentResolver.requestSync(request);
                logger.info("SYNC.SCHED",
                            "Scheduled periodic sync for account %s using requestSync, interval: %d",
                            account.name, syncInterval);
            } else {
                ContentResolver.addPeriodicSync(account, CalendarContract.AUTHORITY, new Bundle(), syncInterval);
                logger.info("SYNC.SCHED",
                            "Scheduled periodic sync for account %s using addPeriodicSync, interval: %d",
                            account.name, syncInterval);
            }
        }
    }



    @Override
    public void onPerformSync(Account account, Bundle bundle, String authority,
                              ContentProviderClient provider, SyncResult syncResult) {
        logger.info("SYNC", "performSync request for account %s, authority %s", account.name, authority);

        if (!checkPermissions()) {
            logger.info("SYNC","Skipping sync, missing permissions");
            return;
        }

        SharedPreferences prefs = getContext().getSharedPreferences(
                getContext().getString(R.string.cz_dvratil_fbeventsync_preferences), Context.MODE_PRIVATE);

        // Don't sync more often than every minute
        Calendar calendar = Calendar.getInstance();
        long lastSync = prefs.getLong("lastSync", 0);
        if (!BuildConfig.DEBUG) {
            if (calendar.getTimeInMillis() - lastSync < 60 * 1000) {
                logger.info("SYNC", "Skipping sync, last sync was only %d seconds ago",
                        (calendar.getTimeInMillis() - lastSync) / 1000);
                return;
            }
        }

            // Allow up to 5 syncs per hour
        int syncsPerHour = prefs.getInt("syncsPerHour", 0);
        if (!BuildConfig.DEBUG) {
            if (calendar.getTimeInMillis() - lastSync < 3600 * 1000) {
                int hour = calendar.get(Calendar.HOUR);
                calendar.setTimeInMillis(lastSync);
                logger.debug("SYNC", "Lasy sync hour: %d, now sync hour: %d", calendar.get(Calendar.HOUR), hour);
                if (calendar.get(Calendar.HOUR) != hour) {
                    syncsPerHour = 1;
                } else {
                    syncsPerHour++;
                }
                if (syncsPerHour > 5) {
                    logger.info("SYNC", "Skipping sync, too many syncs per hour");
                    return;
                }
            } else {
                syncsPerHour = 1;
            }
        }


        Context ctx = getContext();
        AccountManager mgr = AccountManager.get(ctx);
        String accessToken = null;
        try {
            accessToken = mgr.blockingGetAuthToken(account, ctx.getString(R.string.account_type), false);
        } catch (android.accounts.OperationCanceledException e) {
            logger.error("SYNC", "Failed to obtain auth token: %s", e.getMessage());
            syncResult.stats.numAuthExceptions++;
            return;
        } catch (android.accounts.AuthenticatorException e) {
            logger.error("SYNC", "Failed to obtain auth token: %s", e.getMessage());
            syncResult.stats.numAuthExceptions++;
            return;
        } catch (java.io.IOException e) {
            logger.error("SYNC", "Failed to obtain auth token: %s", e.getMessage());
            syncResult.stats.numAuthExceptions++;
            return;
        }


        mSyncContext = new SyncContext(getContext(), account, accessToken, provider, syncResult);

        removeOldBirthdayCalendar(mSyncContext);

        FBCalendar.Set calendars = new FBCalendar.Set();
        calendars.initialize(mSyncContext);

        // Sync via iCal only - not avoiding Graph calls, but it gives us access to private group
        // events which are otherwise missing from Graph
        syncEventsViaICal(calendars);
        //syncEventsViaGraph(calendars);

        syncBirthdayCalendar(calendars);

        calendars.release();

        mSyncContext = null;

        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong("lastSync", Calendar.getInstance().getTimeInMillis());
        editor.putInt("syncsPerHour", syncsPerHour);
        editor.commit();

        logger.info("SYNC", "Sync for %s done", account.name);
    }

    private void syncEventsViaGraph(FBCalendar.Set calendars) {
        String cursor = null;
        do {
            JSONObject response = fetchEvents(cursor);
            try {
                if (response.has("data")) {
                    JSONArray data = response.getJSONArray("data");
                    int len = data.length();
                    FBEvent lastEvent = null;
                    for (int i = 0; i < len; i++) {
                        FBEvent event = FBEvent.parse(data.getJSONObject(i));
                        if (event != null) {
                            FBCalendar calendar = calendars.getCalendarForEvent(event);
                            if (calendar == null) {
                                logger.error("SYNC", "Failed to find calendar for event!");
                                continue;
                            }
                            event.setCalendar(calendar);
                            calendar.syncEvent(event);
                            lastEvent = event;
                        }
                    }

                    // Only sync events back one year, don't go any further in the past to save
                    // bandwidth
                    if (lastEvent != null) {
                        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
                        cal.add(Calendar.YEAR, -1);
                        long lastEventStart = lastEvent.getValues().getAsLong(CalendarContract.Events.DTSTART);
                        if (lastEventStart < cal.getTimeInMillis()) {
                            break;
                        }
                    }
                }
                cursor = getNextCursor(response);
            } catch (java.text.ParseException e) {
                cursor = null;
                logger.error("SYNC", "Text parse exception: %s", e.getMessage());
                mSyncContext.getSyncResult().stats.numParseExceptions++;
            } catch (org.json.JSONException e) {
                cursor = null;
                logger.error("SYNC", "JSON exception in main loop: %s", e.getMessage());
                mSyncContext.getSyncResult().stats.numParseExceptions++;
            }
        } while (cursor != null);
    }

    private JSONObject fetchEvents(String cursor) {
        RequestParams params = new RequestParams();
        params.add(Graph.FIELDS_PARAM, "id,name,description,place,start_time,end_time,owner,is_canceled,rsvp_status");
        params.add(Graph.LIMIT_PARAM, "100");
        if (cursor != null) {
            params.add(Graph.AFTER_PARAM, cursor);
        }

        GraphResponseHandler handler = new GraphResponseHandler(mSyncContext.getContext());

        logger.debug("SYNC.EVENTS","Sending Graph request...");
        RequestHandle handle = Graph.events(mSyncContext.getAccessToken(), params, handler);
        logger.debug("SYNC.EVENTS","Graph response received");

        return handler.getResponse();
    }

    @Nullable
    private String getNextCursor(JSONObject obj) {
        try {
            JSONObject paging = obj.getJSONObject("paging");
            if (paging == null) {
                return null;
            }
            JSONObject cursor = paging.getJSONObject("cursors");
            if (cursor == null) {
                return null;
            }
            return cursor.getString("after");
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    private String getICalSyncURI() {
        AccountManager accManager = AccountManager.get(mSyncContext.getContext());
        String uri = accManager.getUserData(mSyncContext.getAccount(), Authenticator.DATA_BDAY_URI);
        if (uri == null || uri.isEmpty()) {
            // We don't have the URI, possibly we did not migrate from the old authentication system
            // yet, let's schedule it now
            logger.info("SYNC.BDAY","Birthday iCal URL not set, forcing re-authentication");
            accManager.invalidateAuthToken(
                    mSyncContext.getContext().getString(R.string.account_type),
                    mSyncContext.getAccessToken());
            return null;
        }
        uri = uri.replace("webcal", "https");
        return uri;
    }

    private void syncEventsViaICal(FBCalendar.Set calendars) {
        String uri = getICalSyncURI();
        if (uri == null) {
            return;
        }

        uri = uri.replace("/b.php", "/u.php");
        syncICalCalendar(calendars, uri);
    }

    private void syncBirthdayCalendar(FBCalendar.Set calendars) {
        String uri = getICalSyncURI();
        if (uri == null) {
            return;
        }
        syncICalCalendar(calendars, uri);
    }

    private void syncICalCalendar(final FBCalendar.Set calendars, String uri) {
        Graph.fetchBirthdayICal(uri, new DataAsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                if (responseBody == null) {
                    logger.error("SYNC.ICAL", "Response body is empty!!!!!");
                    return;
                }

                ICalendar cal = Biweekly.parse(new String(responseBody)).first();
                for (VEvent vevent : cal.getEvents()) {
                    FBEvent event = FBEvent.parse(vevent);
                    FBCalendar calendar = calendars.getCalendarForEvent(event);
                    event.setCalendar(calendar);
                    calendar.syncEvent(event);
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                String err = responseBody == null ? "Unknown error" : new String(responseBody);
                logger.error("SYNC.BDAY", "Error retrieving birthday iCal file: %s", err);
            }

            @Override
            public void onProgressData(byte[] responseBody) {
                // silence debug output
            }
        });
    }

    private void removeOldBirthdayCalendar(SyncContext context) {
        // remove old "birthday" calendar
        try {
            context.getContentProviderClient().delete(
                    CalendarContract.Calendars.CONTENT_URI,
                    String.format("((%s = ?) AND (%s = ?) AND (%s = ?))",
                            CalendarContract.Calendars.NAME,
                            CalendarContract.Calendars.ACCOUNT_NAME,
                            CalendarContract.Calendars.ACCOUNT_TYPE),
                    new String[]{
                            "birthday", // old name for the fb_birthday_calendar calendar
                            context.getAccount().name,
                            context.getAccount().type
                    });
        } catch (android.os.RemoteException e) {
            logger.error("SYNC", "RemoteException when removing legacy calendar: %s", e.getMessage());
        } catch (android.database.sqlite.SQLiteException e) {
            logger.error("SYNC","SQLiteException when removing legacy calendar: %s", e.getMessage());
        }
    }

    private boolean checkPermissions() {
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
