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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.CalendarContract;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.text.format.Time;

import com.loopj.android.http.DataAsyncHttpResponseHandler;
import com.loopj.android.http.RequestHandle;
import com.loopj.android.http.RequestParams;

import cz.msebera.android.httpclient.Header;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Date;
import java.util.Set;
import java.util.TimeZone;
import java.text.SimpleDateFormat;
import java.util.HashSet;

import org.apache.commons.lang3.StringUtils;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import biweekly.util.ICalDate;
import cz.msebera.android.httpclient.client.HttpClient;

public class CalendarSyncAdapter extends AbstractThreadedSyncAdapter {

    private FBCalendar[] FB_CALENDARS = null;

    private FBCalendar mAttendingCalendar = null;
    private FBCalendar mMaybeCalendar = null;
    private FBCalendar mDeclinedCalendar = null;
    private FBCalendar mNoAnswerCalendar = null;
    private FBCalendar mBirthdayCalendar = null;


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
            return;
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

        FBCalendar.initializeCalendars(mSyncContext);

        String cursor = null;
        do {
            JSONObject response = fetchEvents(cursor);
            try {
                if (response.has("data")) {
                    JSONArray data = response.getJSONArray("data");
                    int len = data.length();
                    for (int i = 0; i < len; i++) {
                        FBEvent event = FBEvent.parse(data.getJSONObject(i));
                        if (event != null) {
                            FBCalendar calendar = FBCalendar.getForEvent(event);
                            if (calendar == null) {
                                logger.error("SYNC", "Failed to find calendar for event!");
                                continue;
                            }
                            event.setCalendar(calendar);
                            calendar.syncEvent(event);
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

        syncBirthdayCalendar();

        FBCalendar.releaseCalendars();

        mSyncContext = null;

        logger.info("SYNC", "Sync for %s done", account.name);
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

    private void syncBirthdayCalendar() {
        AccountManager accManager = AccountManager.get(mSyncContext.getContext());
        String uri = accManager.getUserData(mSyncContext.getAccount(), Authenticator.DATA_BDAY_URI);
        if (uri == null || uri.isEmpty()) {
            // We don't have the URI, possibly we did not migrate from the old authentication system
            // yet, let's schedule it now
            logger.info("SYNC.BDAY","Birthday iCal URL not set, forcing re-authentication");
            accManager.invalidateAuthToken(
                    mSyncContext.getContext().getString(R.string.account_type),
                    mSyncContext.getAccessToken());
            return;
        }
        uri = uri.replace("webcal", "https");
        Graph.fetchBirthdayICal(uri, new DataAsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                if (responseBody == null) {
                    logger.error("SYNC.BDAY", "Response body is empty!!!!!");
                    return;
                }

                ICalendar cal = Biweekly.parse(new String(responseBody)).first();
                for (VEvent vevent : cal.getEvents()) {
                    FBEvent event = FBEvent.parse(vevent);
                    FBCalendar calendar = FBCalendar.getForEvent(event);
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
