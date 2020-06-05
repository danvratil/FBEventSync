/*
    Copyright (C) 2017 - 2018  Daniel Vrátil <me@dvratil.cz>

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

package cz.dvratil.fbeventsync

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SyncRequest
import android.content.SyncResult
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat

import com.loopj.android.http.DataAsyncHttpResponseHandler

import java.util.Calendar
import java.util.Locale

import biweekly.Biweekly
import cz.msebera.android.httpclient.Header
import cz.msebera.android.httpclient.client.utils.URIBuilder

class CalendarSyncAdapter(context: Context, autoInitialize: Boolean) : AbstractThreadedSyncAdapter(context, autoInitialize) {

    private var logger: Logger

    private var mSyncContext: SyncContext? = null

    init {
        PreferencesMigrator.migrate(context)
        logger = Logger.getInstance(context)
        checkPermissions()
        logger.info(TAG, "CalendarSyncAdapter initialized")
    }


    override fun onPerformSync(account: Account, bundle: Bundle, authority: String,
                               provider: ContentProviderClient, syncResult: SyncResult) {
        logger.info(TAG, "performSync request for account ${account.name}, authority $authority")

        if (mSyncContext != null) {
            logger.warning(TAG, "SyncContext not null, another sync already running? Aborting this one")
            return
        }

        if (!checkPermissions()) {
            logger.info(TAG, "Skipping sync, missing permissions")
            return
        }

        val prefs = Preferences(context)

        // Don't sync more often than every minute
        val now = (Calendar.getInstance().timeInMillis / 1000).toInt()
        val lastSync = prefs.lastSyncTime()
        if (!BuildConfig.DEBUG) {
            if (now - lastSync < 60) {
                logger.info(TAG, "Skipping sync, last sync was only ${(now - lastSync)} seconds ago")
                return
            }
            prefs.setLastSyncTime(now)
        }

        // Allow up to 5 syncs per hour
        var syncsPerHour = prefs.syncsPerHour()
        if (!BuildConfig.DEBUG) {
            if (now - lastSync < 3600) {
                val calendar = Calendar.getInstance()
                val hour = calendar.get(Calendar.HOUR)
                logger.debug(TAG, "Lasy sync hour: ${calendar.get(Calendar.HOUR)}, now sync hour: $hour")
                if (calendar.get(Calendar.HOUR) != hour) {
                    syncsPerHour = 1
                } else {
                    syncsPerHour++
                }
                if (syncsPerHour > 5) {
                    logger.info(TAG, "Skipping sync, too many syncs per hour")
                    return
                }
            } else {
                syncsPerHour = 1
                prefs.setSyncsPerHour(syncsPerHour)
            }
        }

        val mgr = AccountManager.get(context)
        val cookies: String?
        try {
            cookies = mgr.getUserData(account, Authenticator.FB_COOKIES)
            if (cookies == null) {
                logger.debug(TAG, "Needs to re-authenticate, will wait for user")
                createAuthNotification()
                return
            } else {
                logger.debug(TAG, "Access token received")
            }
        } catch (e: Exception) {
            when (e) {
                is android.accounts.OperationCanceledException,
                is android.accounts.AuthenticatorException,
                is java.io.IOException -> {
                    logger.error(TAG, "getAuthToken: $e")
                    syncResult.stats.numAuthExceptions++
                    return
                }
                else -> {
                    logger.error(TAG, "getAuthToken: unhandled $e")
                    throw e
                }
            }
        }

        val preferences = Preferences(context)
        val syncContext = SyncContext(context, account, cookies, provider, syncResult, preferences, logger)
        mSyncContext = syncContext

        val calendars = FBCalendar.Set()
        calendars.initialize(syncContext)
        if (prefs.lastVersion() != BuildConfig.VERSION_CODE) {
            logger.info(TAG, "New version detected: deleting all calendars")
            removeOldBirthdayCalendar(syncContext)
            try {
                calendars.values.forEach {
                    it.deleteLocalCalendar()
                }
            } catch (e: Exception) {
                when (e) {
                    is android.os.RemoteException,
                    is android.database.sqlite.SQLiteException -> {
                        logger.error(TAG, "removeOldCalendars: $e")
                        syncResult.stats.numIoExceptions++
                        return
                    }
                    else -> {
                        logger.error(TAG, "removeOldCalendars: unhandled $e")
                        throw e
                    }
                }
            }

            prefs.setLastVersion(BuildConfig.VERSION_CODE)

            // We have to re-initialize calendars now so that they get re-created
            calendars.initialize(syncContext)
        }

        /*
        try {
            if (syncEventsViaWeb(calendars, syncContext)) {
                calendars.forEach {
                    if (it.value.type() != FBCalendar.CalendarType.TYPE_BIRTHDAY) {
                        it.value.finalizeSync()
                    }
                }
            }

            if (calendars[FBCalendar.CalendarType.TYPE_BIRTHDAY]?.isEnabled == true) {
                if (syncBirthdaysViaWeb(calendars)) {
                    calendars[FBCalendar.CalendarType.TYPE_BIRTHDAY]!!.finalizeSync()
                }
            }
        } catch (e: EventScraper.CookiesExpiredException) {
            logger.info(TAG, "Cookies have expired, requesting authentication")
            createAuthNotification()
            return
        }
        */

        if (syncEventsViaICal(calendars)) {
            calendars.forEach {
                if (it.value.type() != FBCalendar.CalendarType.TYPE_BIRTHDAY) {
                    it.value.finalizeSync()
                }
            }
        }

        if (calendars[FBCalendar.CalendarType.TYPE_BIRTHDAY]?.isEnabled == true) {
            if (syncBirthdayCalendar(calendars)) {
                calendars[FBCalendar.CalendarType.TYPE_BIRTHDAY]!!.finalizeSync()
            }
        }

        mSyncContext = null

        logger.info(TAG, "Sync for ${account.name} done")
    }

    private enum class ICalURIType {
        EVENTS,
        BIRTHDAYS
    }

    private fun getICalSyncURI(uriType: ICalURIType): Uri? {
        val syncContext = mSyncContext ?: return null
        val accManager = AccountManager.get(syncContext.context)
        val uid: String?
        val key: String?
        try {
            // This block will automatically trigger authentication if the tokens are missing, so
            // no explicit migration from the old bday_uri is needed
            uid = accManager.blockingGetAuthToken(syncContext.account, Authenticator.FB_UID_TOKEN, false)
            key = accManager.blockingGetAuthToken(syncContext.account, Authenticator.FB_KEY_TOKEN, false)
        } catch (e: Exception) {
            when (e) {
                is android.accounts.OperationCanceledException,
                is java.io.IOException,
                is android.accounts.AuthenticatorException -> {
                    logger.error(TAG, "getIcalSyncURI: $e")
                    return null
                }
                else -> {
                    logger.error(TAG, "getIcalSyncUri: unhandled $e")
                    throw e
                }

            }
        }

        if (uid == null || key == null || uid.isEmpty() || key.isEmpty()) {
            logger.error(TAG, "Failed to obtain UID/KEY tokens from account manager")
            // We only need to invalidate one token to force re-sync
            accManager.invalidateAuthToken(syncContext.context.getString(R.string.account_type), syncContext.accessToken)
            return null
        }

        var userLocale = syncContext.preferences.language()
        if (userLocale == syncContext.context.getString(R.string.pref_language_default_value)) {
            val locale = Locale.getDefault()
            userLocale = "${locale.language}_${locale.country}"
        }

        return Uri.parse("https://www.facebook.com").buildUpon()
                .path(if (uriType == ICalURIType.EVENTS) "/events/ical/upcoming/" else "/events/ical/birthdays/")
                .appendQueryParameter("uid", uid)
                .appendQueryParameter("key", key)
                .appendQueryParameter("locale", userLocale)
                .build()
    }

    private fun sanitizeICalUri(uri: Uri): String {
        return try {
            val b = URIBuilder(uri.toString())
            val params = b.queryParams
            b.clearParameters()
            for (param in params) {
                if (param.name == "uid" || param.name == "key") {
                    b.addParameter(param.name, "hidden")
                } else {
                    b.addParameter(param.name, param.value)
                }
            }
            b.toString()
        } catch (e: java.net.URISyntaxException) {
            "<URI parsing error>"
        }

    }
/*
    private fun syncEventsViaWeb(calendars: FBCalendar.Set, context: SyncContext): Boolean {
        val accountManager = AccountManager.get(context.context)
        val cookies = accountManager.getUserData(context.account, Authenticator.FB_COOKIES)

        val calendar = calendars.get(FBCalendar.CalendarType.TYPE_NOT_REPLIED) ?: return false
        var invites = emptyList<FBEvent>()
        if (calendar.isEnabled) {
            invites = EventScraper().fetchInvites(context, cookies)
            invites.forEach {
                it.setCalendar(calendar)
                calendar.syncEvent(it)
            }
        }

        EventScraper().fetchEvents(invites.map { it.values.getAsString(CalendarContract.Events.UID_2445) }, context, cookies).forEach {
            val calendar = calendars.getCalendarForEvent(it) ?: return false
            if (calendar.isEnabled) {
                it.setCalendar(calendar)
                calendar.syncEvent(it)
            }
        }

        val declinedCalendar = calendars.get(FBCalendar.CalendarType.TYPE_DECLINED) ?: return false
        if (declinedCalendar.isEnabled) {
            EventScraper().fetchDeclined(context, cookies).forEach {
                it.setCalendar(declinedCalendar)
                declinedCalendar.syncEvent(it)
            }
        }

        logger.debug(TAG, "Web sync done")
        return true
    }

    private fun syncBirthdaysViaWeb(calendars: FBCalendar.Set): Boolean {
        val context = mSyncContext ?: return false
        val accountManager = AccountManager.get(context.context)
        val cookies = accountManager.getUserData(context.account, Authenticator.FB_COOKIES)
        val events = EventScraper().fetchBirthdays(context, cookies)
        events.forEach {
            val calendar = calendars.getCalendarForEvent(it) ?: return false
            if (calendar.isEnabled) {
                it.setCalendar(calendar)
                calendar.syncEvent(it)
            }
        }
        logger.debug(TAG, "Web sync of birthdays done")
        return true
    }
*/
    private fun syncEventsViaICal(calendars: FBCalendar.Set): Boolean {
        val uri = getICalSyncURI(ICalURIType.EVENTS) ?: return false

        logger.debug(TAG, "Syncing event iCal from ${sanitizeICalUri(uri)}")
        return syncICalCalendar(calendars, uri.toString())
    }

    private fun syncBirthdayCalendar(calendars: FBCalendar.Set): Boolean {
        val uri = getICalSyncURI(ICalURIType.BIRTHDAYS) ?: return false

        logger.debug(TAG, "Syncing birthday iCal from ${sanitizeICalUri(uri)}")
        return syncICalCalendar(calendars, uri.toString())
    }

    private fun syncICalCalendar(calendars: FBCalendar.Set, uri: String): Boolean {
        var success = false
        Graph.fetchBirthdayICal(uri, object : DataAsyncHttpResponseHandler() {
            override fun onSuccess(statusCode: Int, headers: Array<Header>, responseBody: ByteArray?) {
                val syncContext = mSyncContext ?: return
                if (responseBody == null) {
                    logger.error(TAG, "Response body is empty!")
                    return
                }

                // content-type: text/html and no content-disposition indicates an error - let's assume
                // it's just invalid key and try to re-authenticate
                if (headers.any { it.name.equals("content-type", true) && it.value.contains("text/html",true) }
                    && headers.none { it.name.equals("content-disposition", true) }) {
                    logger.debug(TAG, "Response indicates expired iCal URI, offering reauthentication")
                    AccountManager.get(context).invalidateAuthToken(context.getString(R.string.account_type), syncContext.accessToken)
                    createAuthNotification()
                    return
                }

                val cal = Biweekly.parse(String(responseBody)).first()
                cal.events.forEach {
                    val event = FBEvent.parse(it, syncContext)
                    val calendar = calendars.getCalendarForEvent(event) ?: return
                    if (calendar.isEnabled) {
                        event.setCalendar(calendar)
                        calendar.syncEvent(event)
                    }
                }
                logger.debug(TAG, "iCal sync done")
                success = true
            }

            override fun onFailure(statusCode: Int, headers: Array<Header>?, responseBody: ByteArray?, error: Throwable?) {
                val err = if (responseBody == null) "Unknown error" else String(responseBody)
                logger.error(TAG, "Error retrieving iCal file: $statusCode, $err")
                logger.error(TAG, "URI: $uri")
                headers?.forEach {
                    logger.error(TAG, "    ${it.name}: ${it.value}")
                }
                if (error != null) {
                    logger.error(TAG, "Throwable: $error")
                }
            }

            override fun onProgressData(responseBody: ByteArray?) {
                // silence debug output
            }
        })

        return success
    }

    private fun removeOldBirthdayCalendar(context: SyncContext) {
        // remove old "birthday" calendar
        logger.debug(TAG, "Removing legacy birthday calendar")
        try {
            context.contentProviderClient.delete(
                    context.contentUri(CalendarContract.Calendars.CONTENT_URI),
                    "(${CalendarContract.Calendars.NAME} = ?)",
                    arrayOf("birthday") // old name for the fb_birthday_calendar calendar
            )
        } catch (e: Exception) {
            when (e) {
                is android.os.RemoteException,
                is android.database.sqlite.SQLiteException,
                is java.lang.IllegalArgumentException -> logger.error(TAG, "removeOldBDayCalendar: $e")
                else -> {
                    logger.error(TAG, "removeOldBdayCalendar unhandled: $e")
                    throw e
                }
            }
        }
    }

    private fun checkPermissions(): Boolean {
        val missingPermissions = arrayListOf<String>()
        var permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.READ_CALENDAR)
            missingPermissions.add(Manifest.permission.WRITE_CALENDAR)
        }
        permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SYNC_SETTINGS)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.ACCOUNT_MANAGER)
            missingPermissions.add(Manifest.permission.READ_SYNC_SETTINGS)
            missingPermissions.add(Manifest.permission.WRITE_SYNC_SETTINGS)
        }

        if (!missingPermissions.isEmpty()) {
            logger.info("SYNC.PERM", "Missing permissions: $missingPermissions")
            val extras = Bundle()
            extras.putStringArrayList(PermissionRequestActivity.MISSING_PERMISSIONS, missingPermissions)

            val intent = Intent(context, PermissionRequestActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtras(extras)
            context.startActivity(intent)
            return false
        }

        logger.info("SYNC.PERM", "All permissions granted")
        return true
    }

    private fun createAuthNotification() {
        mSyncContext?.logger?.debug(TAG, "Sending \"Authentication required\" notification.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
           val channel = NotificationChannel(AuthenticatorActivity.AUTH_NOTIFICATION_CHANNEL_ID,
                   context.getString(R.string.sync_ntf_needs_reauthentication_title),
                   NotificationManager.IMPORTANCE_HIGH)
            channel.description = context.getString(R.string.sync_ntf_needs_reauthentication_description)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, AuthenticatorActivity.AUTH_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(context.getString(R.string.sync_ntf_needs_reauthentication_title))
                .setContentText(context.getString(R.string.sync_ntf_needs_reauthentication_description))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setAutoCancel(true)

        val intent = Intent(context, AuthenticatorActivity::class.java)
        intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, context.getString(R.string.account_type))
        intent.putExtra(AuthenticatorActivity.ARG_AUTH_TOKEN_TYPE, AuthenticatorActivity.ARG_AUTH_TOKEN_TYPE)
        intent.putExtra(AuthenticatorActivity.ARG_IS_ADDING_NEW_ACCOUNT, false)

        val stackBuilder = TaskStackBuilder.create(context)
        stackBuilder.addParentStack(AuthenticatorActivity::class.java)
        stackBuilder.addNextIntent(intent)
        val resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
        builder.setContentIntent(resultPendingIntent)

        val ntfMgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ntfMgr.notify(AuthenticatorActivity.AUTH_NOTIFICATION_ID, builder.build())
    }

    companion object {

        private const val TAG = "SYNC"

        fun requestSync(context: Context, account: Account?) {
            val logger = Logger.getInstance(context)
            val type = context.getString(R.string.account_type)
            val accounts = if (account == null) AccountManager.get(context).getAccountsByType(type) else arrayOf(account)
            accounts.forEach { acc ->
                val extras = Bundle()
                extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                ContentResolver.requestSync(acc, CalendarContract.AUTHORITY, extras)
                logger.info(TAG, "Explicitly requested sync for account ${acc.name}")
            }
        }

        fun updateSync(context: Context, account : Account?) {
            val accountType = context.resources.getString(R.string.account_type)

            val pref = Preferences(context)
            val syncInterval = pref.syncFrequency()
            val logger = Logger.getInstance(context)
            val accounts = if (account == null) AccountManager.get(context).getAccountsByType(accountType) else arrayOf(account)
            accounts.forEach { acc ->
                val extras = Bundle()
                extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)

                ContentResolver.cancelSync(acc, CalendarContract.AUTHORITY)
                if (syncInterval > 0) {
                    // Schedule periodic sync based on current configuration
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        // we can enable inexact timers in our periodic sync
                        val request = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            SyncRequest.Builder()
                                    .syncPeriodic(syncInterval.toLong(), (syncInterval / 3).toLong())
                                    .setSyncAdapter(acc, CalendarContract.AUTHORITY)
                                    .setExtras(Bundle()).build()
                        } else {
                            null
                        }
                        ContentResolver.requestSync(request!!)
                        logger.info(TAG, "Scheduled periodic sync using requestSync, interval: $syncInterval")
                    } else {
                        ContentResolver.addPeriodicSync(acc, CalendarContract.AUTHORITY, Bundle(), syncInterval.toLong())
                        logger.info(TAG, "Scheduled periodic sync using addPeriodicSync, interval: $syncInterval")
                    }
                }
            }
        }
    }
}
