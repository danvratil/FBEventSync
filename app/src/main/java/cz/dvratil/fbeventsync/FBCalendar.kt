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

import android.content.ContentValues
import android.provider.CalendarContract

import java.util.Calendar

typealias FBIDLocalIDMap = HashMap<String, Long>

open class FBCalendar protected constructor(protected var mContext: SyncContext,
                                            private var mType: CalendarType) {

    private var mEventsToSync = mutableListOf<FBEvent>()
    protected var mPastLocalIds = FBIDLocalIDMap()
    private var mFutureLocalIds = FBIDLocalIDMap()
    private var mLocalCalendarId = -1L
    var isEnabled  = false
    protected var mSyncStats = SyncStats()

    private val calendarColor: Int
        get() = when (mType) {
                FBCalendar.CalendarType.TYPE_ATTENDING -> mContext.preferences.attendingCalendarColor()
                FBCalendar.CalendarType.TYPE_MAYBE -> mContext.preferences.maybeAttendingCalendarColor()
                FBCalendar.CalendarType.TYPE_NOT_REPLIED -> mContext.preferences.notRespondedCalendarColor()
                FBCalendar.CalendarType.TYPE_DECLINED -> mContext.preferences.declinedCalendarColor()
                FBCalendar.CalendarType.TYPE_BIRTHDAY -> mContext.preferences.birthdayCalendarColor()
            }

    val reminderIntervals: kotlin.collections.Set<Int>
        get() {
            val reminders = when (mType) {
                FBCalendar.CalendarType.TYPE_NOT_REPLIED -> mContext.preferences.notRespondedCalendarReminders()
                FBCalendar.CalendarType.TYPE_DECLINED -> mContext.preferences.declinedCalendarReminders()
                FBCalendar.CalendarType.TYPE_MAYBE -> mContext.preferences.maybeAttendingCalendarReminders()
                FBCalendar.CalendarType.TYPE_ATTENDING -> mContext.preferences.attendingCalendarReminders()
                FBCalendar.CalendarType.TYPE_BIRTHDAY -> mContext.preferences.birthdayCalendarReminders()
            }
            val rv = HashSet<Int>()
            for (reminder in reminders) {
                try {
                    rv.add(Integer.parseInt(reminder))
                } catch (e: java.lang.NumberFormatException) {
                    mContext.logger.error(TAG, "reminderIntervals: $e. (value was '$reminder')")
                }
            }
            return rv
        }

    enum class CalendarType constructor(private val id: String) {
        TYPE_ATTENDING("fb_attending_calendar"),
        TYPE_MAYBE("fb_tentative_calendar"),
        TYPE_DECLINED("fb_declined_calendar"),
        TYPE_NOT_REPLIED("fb_not_responded"),
        TYPE_BIRTHDAY("fb_birthday_calendar");

        fun id() = id
    }

    protected inner class SyncStats {
        var added = 0
        var removed = 0
        var modified = 0
    }

    class Set : HashMap<CalendarType, FBCalendar>() {
        fun initialize(ctx: SyncContext) {
            clear()
            put(CalendarType.TYPE_ATTENDING, FBCalendar(ctx, CalendarType.TYPE_ATTENDING))
            put(CalendarType.TYPE_MAYBE, FBCalendar(ctx, CalendarType.TYPE_MAYBE))
            put(CalendarType.TYPE_DECLINED, FBCalendar(ctx, CalendarType.TYPE_DECLINED))
            put(CalendarType.TYPE_NOT_REPLIED, FBCalendar(ctx, CalendarType.TYPE_NOT_REPLIED))
            put(CalendarType.TYPE_BIRTHDAY, FBBirthdayCalendar(ctx))
        }

        fun getCalendarForEvent(event: FBEvent) = get(event.rsvp)
    }

    @Throws(android.os.RemoteException::class,
            android.database.sqlite.SQLiteException::class,
            NumberFormatException::class)
    private fun createLocalCalendar(): Long {
        val account = mContext.account
        val values = ContentValues()
        values.put(CalendarContract.Calendars.ACCOUNT_NAME, account.name)
        values.put(CalendarContract.Calendars.ACCOUNT_TYPE, mContext.context.getString(R.string.account_type))
        values.put(CalendarContract.Calendars.NAME, id())
        values.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, name())
        values.put(CalendarContract.Calendars.CALENDAR_COLOR, calendarColor)
        values.put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.CAL_ACCESS_READ)
        values.put(CalendarContract.Calendars.OWNER_ACCOUNT, account.name)
        values.put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        // TODO: Figure out how to get local timezone
        //values.put(CalendarContract.Calendars.CALENDAR_TIMEZONE, tz);
        values.put(CalendarContract.Calendars.ALLOWED_REMINDERS,
                "${CalendarContract.Reminders.METHOD_DEFAULT},${CalendarContract.Reminders.METHOD_ALERT}")
        values.put(CalendarContract.Calendars.ALLOWED_AVAILABILITY,
                "${CalendarContract.Events.AVAILABILITY_BUSY}," +
                        "${CalendarContract.Events.AVAILABILITY_FREE}," +
                        "${CalendarContract.Events.AVAILABILITY_TENTATIVE}")
        values.put(CalendarContract.Calendars.ALLOWED_ATTENDEE_TYPES,
                CalendarContract.Attendees.TYPE_NONE.toString())
        // +2 allows for up to 2 custom reminders set by the user
        values.put(CalendarContract.Calendars.MAX_REMINDERS, reminderIntervals.size + 2)

        val calUri = mContext.contentProviderClient.insert(
                mContext.contentUri(CalendarContract.Calendars.CONTENT_URI), values)
        return calUri?.lastPathSegment?.toLong() ?: -1
    }

    @Throws(android.os.RemoteException::class,
            android.database.sqlite.SQLiteException::class)
    private fun updateLocalCalendar() {
        val values = ContentValues()
        values.put(CalendarContract.Calendars.CALENDAR_COLOR, calendarColor)
        mContext.contentProviderClient.update(
                mContext.contentUri(CalendarContract.Calendars.CONTENT_URI),
                values,
                "(${CalendarContract.Calendars._ID} = ?)",
                arrayOf(mLocalCalendarId.toString()))
    }

    @Throws(android.os.RemoteException::class,
            android.database.sqlite.SQLiteException::class)
    private fun findLocalCalendar(): Long {
        val cur = mContext.contentProviderClient.query(
                mContext.contentUri(CalendarContract.Calendars.CONTENT_URI),
                arrayOf(CalendarContract.Calendars._ID),
                "((${CalendarContract.Calendars.ACCOUNT_NAME} = ?) AND " +
                         "(${CalendarContract.Calendars.ACCOUNT_TYPE} = ?) AND " +
                         "(${CalendarContract.Calendars.OWNER_ACCOUNT} = ?) AND " +
                         "(${CalendarContract.Calendars.NAME} = ?))",
                arrayOf(mContext.account.name, mContext.context.getString(R.string.account_type), mContext.account.name, mType.id()), null)

        var result = if (cur?.moveToNext() == true) cur.getLong(0) else -1L
        cur?.close()
        return result
    }

    @Throws(android.os.RemoteException::class,
            android.database.sqlite.SQLiteException::class)
    fun deleteLocalCalendar() {
        mContext.contentProviderClient.delete(
                mContext.contentUri(CalendarContract.Calendars.CONTENT_URI),
                "(${CalendarContract.Calendars._ID} = ?)",
                arrayOf(mLocalCalendarId.toString()))
    }

    @Throws(android.os.RemoteException::class,
            android.database.sqlite.SQLiteException::class)
    private fun fetchLocalPastEvents(): HashMap<String /* FBID */, Long>/* local ID */ {
        return fetchLocalEvents(
                "((${CalendarContract.Events.CALENDAR_ID} = ?) AND (${CalendarContract.Events.DTSTART} < ?))",
                arrayOf(mLocalCalendarId.toString(), Calendar.getInstance().timeInMillis.toString()))
    }

    @Throws(android.os.RemoteException::class,
            android.database.sqlite.SQLiteException::class)
    private fun fetchLocalFutureEvents(): HashMap<String /* FBID */, Long>/* local ID */ {
        return fetchLocalEvents(
                "((${CalendarContract.Events.CALENDAR_ID} = ?) AND (${CalendarContract.Events.DTSTART} >= ?))",
                arrayOf(mLocalCalendarId.toString(), Calendar.getInstance().timeInMillis.toString()))
    }

    @Throws(android.os.RemoteException::class,
            android.database.sqlite.SQLiteException::class)
    private fun fetchLocalEvents(selectorQuery: String, selectorValues: Array<String>): HashMap<String /* FBID */, Long>/* local ID */ {
        val cur = mContext.contentProviderClient.query(
                mContext.contentUri(CalendarContract.Events.CONTENT_URI),
                arrayOf(CalendarContract.Events.UID_2445, CalendarContract.Events._ID),
                selectorQuery, selectorValues, null)
        val localIds = HashMap<String, Long>()
        while (cur?.moveToNext() == true) {
            localIds[cur.getString(0)] = cur.getLong(1)
        }
        cur?.close()
        return localIds
    }


    init {
        isEnabled = when (mType) {
            FBCalendar.CalendarType.TYPE_ATTENDING -> mContext.preferences.attendingCalendarEnabled()
            FBCalendar.CalendarType.TYPE_MAYBE -> mContext.preferences.maybeAttendingCalendarEnabled()
            FBCalendar.CalendarType.TYPE_DECLINED -> mContext.preferences.declinedCalendarEnabled()
            FBCalendar.CalendarType.TYPE_NOT_REPLIED -> mContext.preferences.notRespondedCalendarEnabled()
            FBCalendar.CalendarType.TYPE_BIRTHDAY -> mContext.preferences.birthdayCalendarEnabled()
        }

        try {
            mLocalCalendarId = findLocalCalendar()
            if (mLocalCalendarId < 0) {
                if (isEnabled) {
                    mLocalCalendarId = createLocalCalendar()
                }
            } else {
                if (isEnabled) {
                    updateLocalCalendar()
                    mPastLocalIds = fetchLocalPastEvents()
                    mFutureLocalIds = fetchLocalFutureEvents()
                } else {
                    deleteLocalCalendar()
                    mLocalCalendarId = -1L
                }
            }
        } catch (e: Exception) {
            when (e) {
                is android.os.RemoteException,
                is android.database.sqlite.SQLiteException,
                is NumberFormatException -> mContext.logger.error(TAG, "init: $e")
                else -> {
                    mContext.logger.error(TAG, "init: unhandled $e")
                    throw e
                }
            }
        }
    }

    fun id() = mType.id()
    fun localId() = mLocalCalendarId
    fun type() = mType

    fun name(): String = when (mType) {
        FBCalendar.CalendarType.TYPE_ATTENDING -> mContext.context.getString(R.string.calendar_attending_title)
        FBCalendar.CalendarType.TYPE_MAYBE -> mContext.context.getString(R.string.calendar_tentative_title)
        FBCalendar.CalendarType.TYPE_DECLINED -> mContext.context.getString(R.string.calendar_declined_title)
        FBCalendar.CalendarType.TYPE_NOT_REPLIED -> mContext.context.getString(R.string.calendar_not_responded_title)
        FBCalendar.CalendarType.TYPE_BIRTHDAY -> mContext.context.getString(R.string.calendar_birthday_title)

    }

    fun availability() = when (mType) {
            FBCalendar.CalendarType.TYPE_NOT_REPLIED,
            FBCalendar.CalendarType.TYPE_DECLINED,
            FBCalendar.CalendarType.TYPE_BIRTHDAY -> CalendarContract.Events.AVAILABILITY_FREE
            FBCalendar.CalendarType.TYPE_MAYBE -> CalendarContract.Events.AVAILABILITY_TENTATIVE
            FBCalendar.CalendarType.TYPE_ATTENDING -> CalendarContract.Events.AVAILABILITY_BUSY
    }

    fun syncEvent(event: FBEvent) {
        if (!isEnabled) {
            return
        }

        mEventsToSync.add(event)
        if (mEventsToSync.size > 50) {
            sync()
        }
    }

    protected fun sync() {
        if (!isEnabled) {
            return
        }

        for (event in mEventsToSync) {
            doSyncEvent(event)
        }
        mEventsToSync.clear()
    }

    protected open fun doSyncEvent(event: FBEvent) {
        var localId: Long? = mFutureLocalIds[event.eventId()] ?: mPastLocalIds[event.eventId()]
        try {
            if (localId == null) {
                event.create(mContext)
                mSyncStats.added += 1
            } else {
                event.update(mContext, localId)
                mSyncStats.modified += 1
            }
        } catch (e: Exception) {
            when (e) {
                is android.os.RemoteException,
                is android.database.sqlite.SQLiteException -> mContext.logger.error(TAG, "doSyncEvent: $e")
                else -> {
                    mContext.logger.error(TAG, "doSyncEvent: unhandled $e")
                    throw e
                }
            }
        }
        mFutureLocalIds.remove(event.eventId())
    }

    open fun finalizeSync() {
        if (!isEnabled) {
            return
        }

        sync()
        // Only delete from future events, we want to keep past events at any cost
        val log = mContext.logger
        for (localId in mFutureLocalIds.values) {
            try {
                FBEvent.remove(mContext, localId)
                mSyncStats.removed += 1
            } catch (e: Exception) {
                when (e) {
                    is android.os.RemoteException,
                    is android.database.sqlite.SQLiteException -> log.error(TAG, "finalizeSync: $e")
                    else -> {
                        log.error(TAG,"finalizeSync: unhandled $e")
                        throw e
                    }
                }
            }
        }
        mFutureLocalIds.clear()
        mPastLocalIds.clear()

        log.info(TAG, "Sync stats for ${name()}")
        log.info(TAG, "    Events added: ${mSyncStats.added}")
        log.info(TAG, "    Events modified: ${mSyncStats.modified}")
        log.info(TAG, "    Events removed: ${mSyncStats.removed}")
    }

    companion object {
        private const val TAG = "FBCalendar"
    }
}
