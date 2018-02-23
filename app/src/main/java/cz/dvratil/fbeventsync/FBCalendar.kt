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

import android.accounts.Account
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract

import java.util.ArrayList
import java.util.Calendar
import java.util.HashMap
import java.util.HashSet
import java.util.Locale

open class FBCalendar protected constructor(context: SyncContext, type: CalendarType) {

    protected var mType: CalendarType? = null
    protected var mContext: SyncContext? = null
    protected var mEventsToSync: MutableList<FBEvent>? = null
    protected var mPastLocalIds: HashMap<String /* FBID */, Long>/* local ID */? = null
    protected var mFutureLocalIds: HashMap<String /* FBID */, Long>/* local ID */? = null
    protected var mLocalCalendarId: Long? = -1L
    var isEnabled = false
        protected set
    protected var mSyncStats = SyncStats()

    private val calendarColor: Int
        get() {
            when (mType) {
                FBCalendar.CalendarType.TYPE_ATTENDING -> return mContext!!.preferences!!.attendingCalendarColor()
                FBCalendar.CalendarType.TYPE_MAYBE -> return mContext!!.preferences!!.maybeAttendingCalendarColor()
                FBCalendar.CalendarType.TYPE_NOT_REPLIED -> return mContext!!.preferences!!.notRespondedCalendarColor()
                FBCalendar.CalendarType.TYPE_DECLINED -> return mContext!!.preferences!!.declinedCalendarColor()
                FBCalendar.CalendarType.TYPE_BIRTHDAY -> return mContext!!.preferences!!.birthdayCalendarColor()
                else -> return -1
            }
        }

    val reminderIntervals: Set<Int>
        get() {
            val reminders: Set<String>
            when (mType) {
                FBCalendar.CalendarType.TYPE_NOT_REPLIED -> reminders = mContext!!.preferences!!.notRespondedCalendarReminders()
                FBCalendar.CalendarType.TYPE_DECLINED -> reminders = mContext!!.preferences!!.declinedCalendarReminders()
                FBCalendar.CalendarType.TYPE_MAYBE -> reminders = mContext!!.preferences!!.maybeAttendingCalendarReminders()
                FBCalendar.CalendarType.TYPE_ATTENDING -> reminders = mContext!!.preferences!!.attendingCalendarReminders()
                FBCalendar.CalendarType.TYPE_BIRTHDAY -> reminders = mContext!!.preferences!!.birthdayCalendarReminders()
                else -> return null
            }
            val rv = HashSet<Int>()
            for (reminder in reminders) {
                try {
                    rv.add(Integer.parseInt(reminder))
                } catch (e: java.lang.NumberFormatException) {
                    mContext!!.logger!!.error(TAG, "NumberFormatException when loading reminders. Value was '%s': %s", reminder, e.message)
                }

            }
            return rv
        }

    enum class CalendarType private constructor(private val id: String) {
        TYPE_ATTENDING("fb_attending_calendar"),
        TYPE_MAYBE("fb_tentative_calendar"),
        TYPE_DECLINED("fb_declined_calendar"),
        TYPE_NOT_REPLIED("fb_not_responded"),
        TYPE_BIRTHDAY("fb_birthday_calendar");

        fun id(): String {
            return id
        }
    }

    protected inner class SyncStats {
        internal var added = 0
        internal var removed = 0
        internal var modified = 0
    }

    class Set : HashMap<CalendarType, FBCalendar>() {
        fun initialize(ctx: SyncContext) {
            put(CalendarType.TYPE_ATTENDING, FBCalendar(ctx, CalendarType.TYPE_ATTENDING))
            put(CalendarType.TYPE_MAYBE, FBCalendar(ctx, CalendarType.TYPE_MAYBE))
            put(CalendarType.TYPE_DECLINED, FBCalendar(ctx, CalendarType.TYPE_DECLINED))
            put(CalendarType.TYPE_NOT_REPLIED, FBCalendar(ctx, CalendarType.TYPE_NOT_REPLIED))
            put(CalendarType.TYPE_BIRTHDAY, FBBirthdayCalendar(ctx))
        }

        internal fun getCalendarForEvent(event: FBEvent): FBCalendar {
            return get(event.rsvp)
        }

        fun release() {
            for (calendar in values) {
                calendar.finalizeSync()
            }
            clear()
        }
    }

    @Throws(android.os.RemoteException::class, android.database.sqlite.SQLiteException::class, NumberFormatException::class)
    private fun createLocalCalendar(): Long {
        val account = mContext!!.account
        val values = ContentValues()
        values.put(CalendarContract.Calendars.ACCOUNT_NAME, account!!.name)
        values.put(CalendarContract.Calendars.ACCOUNT_TYPE, mContext!!.context!!.getString(R.string.account_type))
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
                String.format(Locale.US, "%d,%d", CalendarContract.Reminders.METHOD_DEFAULT,
                        CalendarContract.Reminders.METHOD_ALERT))
        values.put(CalendarContract.Calendars.ALLOWED_AVAILABILITY,
                String.format(Locale.US, "%d,%d,%d", CalendarContract.Events.AVAILABILITY_BUSY,
                        CalendarContract.Events.AVAILABILITY_FREE,
                        CalendarContract.Events.AVAILABILITY_TENTATIVE))
        values.put(CalendarContract.Calendars.ALLOWED_ATTENDEE_TYPES,
                CalendarContract.Attendees.TYPE_NONE.toString())
        // +2 allows for up to 2 custom reminders set by the user
        values.put(CalendarContract.Calendars.MAX_REMINDERS, reminderIntervals.size + 2)

        val calUri = mContext!!.contentProviderClient!!.insert(
                mContext!!.contentUri(CalendarContract.Calendars.CONTENT_URI), values)
        return if (calUri != null) {
            java.lang.Long.parseLong(calUri.lastPathSegment)
        } else {
            -1
        }
    }

    @Throws(android.os.RemoteException::class, android.database.sqlite.SQLiteException::class)
    private fun updateLocalCalendar() {
        val values = ContentValues()
        values.put(CalendarContract.Calendars.CALENDAR_COLOR, calendarColor)
        mContext!!.contentProviderClient!!.update(
                mContext!!.contentUri(CalendarContract.Calendars.CONTENT_URI),
                values,
                String.format("(%s = ?)", CalendarContract.Calendars._ID),
                arrayOf(mLocalCalendarId!!.toString()))
    }

    @Throws(android.os.RemoteException::class, android.database.sqlite.SQLiteException::class)
    private fun findLocalCalendar(): Long {
        val cur = mContext!!.contentProviderClient!!.query(
                mContext!!.contentUri(CalendarContract.Calendars.CONTENT_URI),
                arrayOf(CalendarContract.Calendars._ID),
                String.format("((%s = ?) AND (%s = ?) AND (%s = ?) AND (%s = ?))",
                        CalendarContract.Calendars.ACCOUNT_NAME,
                        CalendarContract.Calendars.ACCOUNT_TYPE,
                        CalendarContract.Calendars.OWNER_ACCOUNT,
                        CalendarContract.Calendars.NAME),
                arrayOf(mContext!!.account!!.name, mContext!!.context!!.getString(R.string.account_type), mContext!!.account!!.name, mType!!.id()), null)
        var result: Long = -1
        if (cur != null) {
            if (cur.moveToNext()) {
                result = cur.getLong(0)
            }
            cur.close()
        }
        return result
    }

    @Throws(android.os.RemoteException::class, android.database.sqlite.SQLiteException::class)
    fun deleteLocalCalendar() {
        mContext!!.contentProviderClient!!.delete(
                mContext!!.contentUri(CalendarContract.Calendars.CONTENT_URI),
                String.format("(%s = ?)", CalendarContract.Calendars._ID),
                arrayOf(mLocalCalendarId!!.toString()))
    }

    @Throws(android.os.RemoteException::class, android.database.sqlite.SQLiteException::class)
    private fun fetchLocalPastEvents(): HashMap<String /* FBID */, Long>/* local ID */ {
        return fetchLocalEvents(
                String.format("((%s = ?) AND (%s < ?))", CalendarContract.Events.CALENDAR_ID, CalendarContract.Events.DTSTART),
                arrayOf(mLocalCalendarId!!.toString(), java.lang.Long.valueOf(Calendar.getInstance().timeInMillis)!!.toString()))
    }

    @Throws(android.os.RemoteException::class, android.database.sqlite.SQLiteException::class)
    private fun fetchLocalFutureEvents(): HashMap<String /* FBID */, Long>/* local ID */ {
        return fetchLocalEvents(
                String.format("((%s = ?) AND (%s >= ?))", CalendarContract.Events.CALENDAR_ID, CalendarContract.Events.DTSTART),
                arrayOf(mLocalCalendarId!!.toString(), java.lang.Long.valueOf(Calendar.getInstance().timeInMillis)!!.toString()))
    }

    @Throws(android.os.RemoteException::class, android.database.sqlite.SQLiteException::class)
    private fun fetchLocalEvents(
            selectorQuery: String, selectorValues: Array<String>): HashMap<String /* FBID */, Long>/* local ID */ {
        val localIds = HashMap<String, Long>()
        val cur = mContext!!.contentProviderClient!!.query(
                mContext!!.contentUri(CalendarContract.Events.CONTENT_URI),
                arrayOf(CalendarContract.Events.UID_2445, CalendarContract.Events._ID),
                selectorQuery, selectorValues, null)
        if (cur != null) {
            while (cur.moveToNext()) {
                localIds[cur.getString(0)] = cur.getLong(1)
            }
            cur.close()
        }
        return localIds
    }


    init {
        mType = type
        mContext = context
        mEventsToSync = ArrayList()

        when (mType) {
            FBCalendar.CalendarType.TYPE_ATTENDING -> isEnabled = mContext!!.preferences!!.attendingCalendarEnabled()
            FBCalendar.CalendarType.TYPE_MAYBE -> isEnabled = mContext!!.preferences!!.maybeAttendingCalendarEnabled()
            FBCalendar.CalendarType.TYPE_DECLINED -> isEnabled = mContext!!.preferences!!.declinedCalendarEnabled()
            FBCalendar.CalendarType.TYPE_NOT_REPLIED -> isEnabled = mContext!!.preferences!!.notRespondedCalendarEnabled()
            FBCalendar.CalendarType.TYPE_BIRTHDAY -> isEnabled = mContext!!.preferences!!.birthdayCalendarEnabled()
        }

        try {
            mLocalCalendarId = findLocalCalendar()
            if (mLocalCalendarId < 0) {
                if (isEnabled) {
                    mLocalCalendarId = createLocalCalendar()
                    mPastLocalIds = HashMap()
                    mFutureLocalIds = HashMap()
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
        } catch (e: android.os.RemoteException) {
            mContext!!.logger!!.error(TAG, "Remote exception on creation: %s", e.message)
        } catch (e: android.database.sqlite.SQLiteException) {
            mContext!!.logger!!.error(TAG, "SQL exception on creation: %s", e.message)
        } catch (e: NumberFormatException) {
            mContext!!.logger!!.error(TAG, "Number exception on creation: %s", e.message)
        }

    }

    fun id(): String {
        return mType!!.id()
    }

    fun localId(): Long {
        return mLocalCalendarId!!
    }

    fun type(): CalendarType? {
        return mType
    }

    fun name(): String {
        when (mType) {
            FBCalendar.CalendarType.TYPE_ATTENDING -> return mContext!!.context!!.getString(R.string.calendar_attending_title)
            FBCalendar.CalendarType.TYPE_MAYBE -> return mContext!!.context!!.getString(R.string.calendar_tentative_title)
            FBCalendar.CalendarType.TYPE_DECLINED -> return mContext!!.context!!.getString(R.string.calendar_declined_title)
            FBCalendar.CalendarType.TYPE_NOT_REPLIED -> return mContext!!.context!!.getString(R.string.calendar_not_responded_title)
            FBCalendar.CalendarType.TYPE_BIRTHDAY -> return mContext!!.context!!.getString(R.string.calendar_birthday_title)
        }
        return null
    }

    fun availability(): Int {
        when (mType) {
            FBCalendar.CalendarType.TYPE_NOT_REPLIED, FBCalendar.CalendarType.TYPE_DECLINED, FBCalendar.CalendarType.TYPE_BIRTHDAY -> return CalendarContract.Events.AVAILABILITY_FREE
            FBCalendar.CalendarType.TYPE_MAYBE -> return CalendarContract.Events.AVAILABILITY_TENTATIVE
            FBCalendar.CalendarType.TYPE_ATTENDING -> return CalendarContract.Events.AVAILABILITY_BUSY
        }
        return CalendarContract.Events.AVAILABILITY_BUSY
    }

    fun syncEvent(event: FBEvent) {
        if (!isEnabled) {
            return
        }

        mEventsToSync!!.add(event)
        if (mEventsToSync!!.size > 50) {
            sync()
        }
    }

    protected fun sync() {
        if (!isEnabled) {
            return
        }

        for (event in mEventsToSync!!) {
            doSyncEvent(event)
        }
        mEventsToSync!!.clear()
    }

    protected open fun doSyncEvent(event: FBEvent) {
        var localId: Long? = mFutureLocalIds!![event.eventId()]
        if (localId == null) {
            localId = mPastLocalIds!![event.eventId()]
        }
        try {
            if (localId == null) {
                event.create(mContext)
                mSyncStats.added += 1
            } else {
                event.update(mContext, localId.toLong())
                mSyncStats.modified += 1
            }
        } catch (e: android.os.RemoteException) {
            mContext!!.logger!!.error(TAG, "Remote exception during FBCalendar sync: %s", e.message)
            // continue with remaining events
        } catch (e: android.database.sqlite.SQLiteException) {
            mContext!!.logger!!.error(TAG, "SQL exception during FBCalendar sync: %s", e.message)
            // continue with remaining events
        }

        mFutureLocalIds!!.remove(event.eventId())
    }

    open fun finalizeSync() {
        if (!isEnabled) {
            return
        }

        sync()
        // Only delete from future events, we want to keep past events at any cost
        val log = mContext!!.logger
        if (mFutureLocalIds == null) {
            log!!.error(TAG, "FinalizeSync passed for %s calendar '%s' with null futureLocalIDs!",
                    if (isEnabled) "enabled" else "disabled", mType!!.id())
            return
        }

        for (localId in mFutureLocalIds!!.values) {
            try {
                FBEvent.remove(mContext, localId)
                mSyncStats.removed += 1
            } catch (e: android.os.RemoteException) {
                log!!.error(TAG, "Remote exception during FBCalendar finalizeSync: %s", e.message)
                // continue with remaining events
            } catch (e: android.database.sqlite.SQLiteException) {
                log!!.error(TAG, "SQL exception during FBCalendar fynalize sync: %s", e.message)
                // continue with remaining events
            }

        }
        mFutureLocalIds!!.clear()
        mPastLocalIds!!.clear()

        log!!.info(TAG, "Sync stats for %s", name())
        log.info(TAG, "    Events added: %d", mSyncStats.added)
        log.info(TAG, "    Events modified: %d", mSyncStats.modified)
        log.info(TAG, "    Events removed: %d", mSyncStats.removed)
    }

    companion object {

        private val TAG = "FBCalendar"
    }
}
