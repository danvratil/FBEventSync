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
import android.accounts.AccountManagerFuture;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncRequest;
import android.content.SyncResult;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.CalendarContract;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;

import com.loopj.android.http.DataAsyncHttpResponseHandler;
import com.loopj.android.http.RequestHandle;
import com.loopj.android.http.RequestParams;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.utils.URIBuilder;

public class CalendarSyncAdapter extends AbstractThreadedSyncAdapter {

    private Logger logger = null;

    private SyncContext mSyncContext = null;

    private static String TAG = "SYNC";

    public CalendarSyncAdapter(Context context, boolean autoInitialize) {
        super(context,  autoInitialize);

        PreferencesMigrator.migrate(context);

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
            logger.info(TAG, "Explicitly requested sync for account %s", account.name);
        }
    }

    static public void updateSync(Context context) {
        String accountType = context.getResources().getString(R.string.account_type);

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        String syncFreq = pref.getString(context.getResources().getString(R.string.pref_sync_frequency),
                                         context.getResources().getString(R.string.pref_sync_frequency_default_value));
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
                logger.info(TAG, "Scheduled periodic sync for account %s using requestSync, interval: %d",
                            account.name, syncInterval);
            } else {
                ContentResolver.addPeriodicSync(account, CalendarContract.AUTHORITY, new Bundle(), syncInterval);
                logger.info(TAG, "Scheduled periodic sync for account %s using addPeriodicSync, interval: %d",
                            account.name, syncInterval);
            }
        }
    }



    @Override
    public void onPerformSync(Account account, Bundle bundle, String authority,
                              ContentProviderClient provider, SyncResult syncResult) {
        logger.info(TAG, "performSync request for account %s, authority %s", account.name, authority);

        if (mSyncContext != null) {
            logger.warning(TAG, "SyncContext not null, another sync already running? Aborting this one");
            return;
        }

        if (!checkPermissions()) {
            logger.info(TAG, "Skipping sync, missing permissions");
            return;
        }

        SharedPreferences prefs = getContext().getSharedPreferences(
                getContext().getString(R.string.cz_dvratil_fbeventsync_preferences), Context.MODE_MULTI_PROCESS);

        // Don't sync more often than every minute
        Calendar calendar = Calendar.getInstance();
        long lastSync = prefs.getLong(getContext().getString(R.string.cfg_last_sync), 0);
        if (!BuildConfig.DEBUG) {
            if (calendar.getTimeInMillis() - lastSync < 60 * 1000) {
                logger.info(TAG, "Skipping sync, last sync was only %d seconds ago",
                        (calendar.getTimeInMillis() - lastSync) / 1000);
                return;
            }
        }

        // Allow up to 5 syncs per hour
        int syncsPerHour = prefs.getInt(getContext().getString(R.string.cfg_syncs_per_hour), 0);
        if (!BuildConfig.DEBUG) {
            if (calendar.getTimeInMillis() - lastSync < 3600 * 1000) {
                int hour = calendar.get(Calendar.HOUR);
                calendar.setTimeInMillis(lastSync);
                logger.debug(TAG, "Lasy sync hour: %d, now sync hour: %d", calendar.get(Calendar.HOUR), hour);
                if (calendar.get(Calendar.HOUR) != hour) {
                    syncsPerHour = 1;
                } else {
                    syncsPerHour++;
                }
                if (syncsPerHour > 5) {
                    logger.info(TAG, "Skipping sync, too many syncs per hour");
                    return;
                }
            } else {
                syncsPerHour = 1;
            }
        }


        Context ctx = getContext();
        AccountManager mgr = AccountManager.get(ctx);
        String accessToken;
        try {
            Bundle result = mgr.getAuthToken(account, Authenticator.FB_OAUTH_TOKEN, null, false, null, null).getResult();
            accessToken = result.getString(AccountManager.KEY_AUTHTOKEN);
            if (accessToken == null) {
                logger.debug(TAG, "Needs to reauthenticate, will wait for user");
                createAuthNotification();
                return;
            } else {
                logger.debug(TAG, "Access token received");
            }
        } catch (android.accounts.OperationCanceledException e) {
            logger.error(TAG, "Failed to obtain auth token: %s", e.getMessage());
            syncResult.stats.numAuthExceptions++;
            return;
        } catch (android.accounts.AuthenticatorException e) {
            logger.error(TAG, "Failed to obtain auth token: %s", e.getMessage());
            syncResult.stats.numAuthExceptions++;
            return;
        } catch (java.io.IOException e) {
            logger.error(TAG, "Failed to obtain auth token: %s", e.getMessage());
            syncResult.stats.numAuthExceptions++;
            return;
        }

        mSyncContext = new SyncContext(getContext(), account, accessToken, provider, syncResult, logger);

        removeOldBirthdayCalendar(mSyncContext);
        FBCalendar.Set calendars = new FBCalendar.Set();
        calendars.initialize(mSyncContext);
        if (prefs.getInt(getContext().getString(R.string.cfg_last_version), 0) != BuildConfig.VERSION_CODE) {
            logger.info(TAG, "New version detected: deleting all calendars");
            for (FBCalendar cal : calendars.values()) {
                try {
                    cal.deleteLocalCalendar();
                } catch (android.os.RemoteException e) {
                    // FIXME: Handle exceptions
                    logger.error(TAG, "Failed to cleanup calendars: %s", e.getMessage());
                    syncResult.stats.numIoExceptions++;
                    return;
                } catch (android.database.sqlite.SQLiteException e) {
                    logger.error(TAG, "Failed to cleanup calendars: %s", e.getMessage());
                    syncResult.stats.numIoExceptions++;
                    return;
                }
            }
            // We have to re-initialize calendars now so that they get re-created
            calendars.release();
            calendars.initialize(mSyncContext);
        }

        // Sync via iCal only - not avoiding Graph calls, but it gives us access to private group
        // events which are otherwise missing from Graph
        syncEventsViaICal(calendars);
        //syncEventsViaGraph(calendars);

        syncBirthdayCalendar(calendars);

        calendars.release();

        mSyncContext = null;

        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(getContext().getString(R.string.cfg_last_version), BuildConfig.VERSION_CODE);
        editor.putLong(getContext().getString(R.string.cfg_last_sync), Calendar.getInstance().getTimeInMillis());
        editor.putInt(getContext().getString(R.string.cfg_syncs_per_hour), syncsPerHour);
        editor.apply();

        logger.info(TAG, "Sync for %s done", account.name);
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
                                logger.error(TAG, "Failed to find calendar for event!");
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
                logger.error(TAG, "Text parse exception: %s", e.getMessage());
                mSyncContext.getSyncResult().stats.numParseExceptions++;
            } catch (org.json.JSONException e) {
                cursor = null;
                logger.error(TAG, "JSON exception in main loop: %s", e.getMessage());
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

        logger.debug(TAG, "Sending Graph request...");
        Graph.events(mSyncContext.getAccessToken(), params, handler);
        logger.debug(TAG, "Graph response received");

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

    private enum ICalURIType {
        EVENTS,
        BIRTHDAYS
    };
    private Uri getICalSyncURI(ICalURIType uriType) {
        AccountManager accManager = AccountManager.get(mSyncContext.getContext());
        String uid, key;
        try {
            // This block will automatically trigger authentication if the tokens are missing, so
            // no explicit migration from the old bday_uri is needed
            uid = accManager.blockingGetAuthToken(mSyncContext.getAccount(), Authenticator.FB_UID_TOKEN, false);
            key = accManager.blockingGetAuthToken(mSyncContext.getAccount(), Authenticator.FB_KEY_TOKEN, false);
        } catch (android.accounts.OperationCanceledException e) {
            logger.error(TAG, "User cancelled obtaining UID/KEY token: %s", e.getMessage());
            return null;
        } catch (java.io.IOException e) {
            logger.error(TAG, "IO Exception while obtaining UID/KEY token: %s", e.getMessage());
            return null;
        } catch (android.accounts.AuthenticatorException e) {
            logger.error(TAG, "Authenticator exception while obtaining UID/KEY token: %s", e.getMessage());
            return null;
        }

        if (uid == null || key == null || uid.isEmpty() || key.isEmpty()) {
            logger.error(TAG, "Failed to obtain UID/KEY tokens from account manager");
            // We only need to invalidate one token to force re-sync
            accManager.invalidateAuthToken(mSyncContext.getContext().getString(R.string.account_type), mSyncContext.getAccessToken());
            return null;
        }

        String userLocale = mSyncContext.getPreferences().getString(getContext().getString(R.string.pref_language), null);
        if (userLocale == null || userLocale.equals(mSyncContext.getContext().getString(R.string.pref_language_default_value))) {
            Locale locale = Locale.getDefault();
            userLocale = String.format("%s_%s", locale.getLanguage(), locale.getCountry());
        }

        return Uri.parse("https://www.facebook.com").buildUpon()
                .path(uriType == ICalURIType.EVENTS ? "/ical/u.php" : "/ical/b.php")
                .appendQueryParameter("uid", uid)
                .appendQueryParameter("key", key)
                .appendQueryParameter("locale", userLocale)
                .build();
    }

    private String sanitizeICalUri(Uri uri) {
        try {
            URIBuilder b = new URIBuilder(uri.toString());
            List<NameValuePair> params = b.getQueryParams();
            b.clearParameters();
            for (NameValuePair param : params) {
                if (param.getName().equals("uid") || param.getName().equals("key")) {
                    b.addParameter(param.getName(), "hidden");
                } else {
                    b.addParameter(param.getName(), param.getValue());
                }
            }
            return b.toString();
        } catch (java.net.URISyntaxException e) {
            return "<URI parsing error>";
        }
    }

    private void syncEventsViaICal(FBCalendar.Set calendars) {
        Uri uri = getICalSyncURI(ICalURIType.EVENTS);
        if (uri == null) {
            return;
        }

        logger.debug(TAG, "Syncing event iCal from %s", sanitizeICalUri(uri));
        syncICalCalendar(calendars, uri.toString());
    }

    private void syncBirthdayCalendar(FBCalendar.Set calendars) {
        Uri uri = getICalSyncURI(ICalURIType.BIRTHDAYS);
        if (uri == null) {
            return;
        }

        logger.debug(TAG, "Syncing birthday iCal from %s", sanitizeICalUri(uri));
        syncICalCalendar(calendars, uri.toString());
    }

    private void syncICalCalendar(final FBCalendar.Set calendars, final String uri) {
        Graph.fetchBirthdayICal(uri, new DataAsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                if (responseBody == null) {
                    logger.error(TAG, "Response body is empty!!!!!");
                    return;
                }

                ICalendar cal = Biweekly.parse(new String(responseBody)).first();
                for (VEvent vevent : cal.getEvents()) {
                    FBEvent event = FBEvent.parse(vevent);
                    FBCalendar calendar = calendars.getCalendarForEvent(event);
                    if (calendar.isEnabled()) {
                        event.setCalendar(calendar);
                        calendar.syncEvent(event);
                    }
                }
                logger.debug(TAG, "iCal sync done");
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                String err = responseBody == null ? "Unknown error" : new String(responseBody);
                logger.error(TAG, "Error retrieving iCal file: %d, %s", statusCode, err);
                logger.error(TAG, "URI: %s", uri);
                if (headers != null) {
                    for (Header header : headers) {
                        logger.error(TAG, "    %s: %s", header.getName(), header.getValue());
                    }
                }
                if (error != null) {
                    logger.error(TAG, "Throwable: %s", error.toString());
                }
            }

            @Override
            public void onProgressData(byte[] responseBody) {
                // silence debug output
            }
        });
    }

    private void removeOldBirthdayCalendar(SyncContext context) {
        // remove old "birthday" calendar
        logger.debug(TAG, "Removing legacy birthday calendar");
        try {
            context.getContentProviderClient().delete(
                    CalendarContract.Calendars.CONTENT_URI.buildUpon()
                            .appendQueryParameter(CalendarContract.SyncState.ACCOUNT_TYPE, context.getContext().getString(R.string.account_type))
                            .appendQueryParameter(CalendarContract.SyncState.ACCOUNT_NAME, context.getAccount().name)
                            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                            .build(),
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
            logger.error(TAG, "RemoteException when removing legacy calendar: %s", e.getMessage());
        } catch (android.database.sqlite.SQLiteException e) {
            logger.error(TAG, "SQLiteException when removing legacy calendar: %s", e.getMessage());
        } catch (java.lang.IllegalArgumentException e) {
            logger.error(TAG, "IllegalArgumentException when removing legacy calendar: %s", e.getMessage());
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

    private void createAuthNotification()
    {
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(getContext(), AuthenticatorActivity.AUTH_NOTIFICATION_CHANNEL_ID)
                    .setContentTitle(getContext().getString(R.string.sync_ntf_needs_reauthentication_title))
                    .setContentText(getContext().getString(R.string.sync_ntf_needs_reauthentication_description))
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setAutoCancel(true);

        Intent intent = new Intent(getContext(), AuthenticatorActivity.class);
        intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, getContext().getString(R.string.account_type));
        intent.putExtra(AuthenticatorActivity.ARG_AUTH_TOKEN_TYPE, AuthenticatorActivity.ARG_AUTH_TOKEN_TYPE);
        intent.putExtra(AuthenticatorActivity.ARG_IS_ADDING_NEW_ACCOUNT, false);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(getContext());
        stackBuilder.addParentStack(AuthenticatorActivity.class);
        stackBuilder.addNextIntent(intent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(resultPendingIntent);

        NotificationManager ntfMgr =
                (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        ntfMgr.notify(AuthenticatorActivity.AUTH_NOTIFICATION_ID, builder.build());
    }
}
