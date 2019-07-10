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

import android.annotation.SuppressLint
import android.content.ContentValues
import android.provider.CalendarContract

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

import biweekly.component.VEvent
import org.jsoup.nodes.Element

open class FBEvent protected constructor() {

    var values = ContentValues()
    var rsvp: FBCalendar.CalendarType? = null
    private var mCalendar: FBCalendar? = null

    fun eventId(): String {
        return values.getAsString(CalendarContract.Events.UID_2445)
    }

    fun setCalendar(calendar: FBCalendar) {
        mCalendar = calendar
        values.put(CalendarContract.Events.CALENDAR_ID, calendar.localId())
        values.put(CalendarContract.Events.AVAILABILITY, calendar.availability())
    }

    fun isAllDay(): Boolean? {
        return values.getAsBoolean(CalendarContract.Events.ALL_DAY)
    }

    @Throws(android.os.RemoteException::class,
            android.database.sqlite.SQLiteException::class)
    fun create(context: SyncContext): Long {
        val uri = context.contentProviderClient.insert(
                context.contentUri(CalendarContract.Events.CONTENT_URI),
                values)
        if (uri != null && mCalendar != null) {
            val eventId = uri.lastPathSegment.toLong()
            val reminders = if (isAllDay() == true) mCalendar!!.allDayReminderIntervals else mCalendar!!.reminderIntervals
            if (reminders.isNotEmpty()) {
                createReminders(context, eventId, reminders)
            }

            context.syncResult.stats.numInserts++
            return eventId
        }

        return -1
    }

    @SuppressLint("UseSparseArrays")
    @Throws(android.os.RemoteException::class,
            android.database.sqlite.SQLiteException::class)
    private fun getLocalReminders(context: SyncContext, localEventId: Long): HashMap<FBReminder, Long /* reminder ID */> {
        val cur = context.contentProviderClient.query(
                context.contentUri(CalendarContract.Reminders.CONTENT_URI),
                arrayOf(CalendarContract.Reminders._ID, CalendarContract.Reminders.MINUTES),
                "(${CalendarContract.Reminders.EVENT_ID} = ?)",
                arrayOf(localEventId.toString()), null)
        val localReminders = HashMap<FBReminder, Long /* reminder ID */>()
        while (cur?.moveToNext() == true) {
            localReminders[FBReminder(cur.getInt(1), false)] = cur.getLong(0)
        }
        cur?.close()
        return localReminders
    }

    @Throws(android.os.RemoteException::class,
            android.database.sqlite.SQLiteException::class)
    private fun createReminders(context: SyncContext, localEventId: Long, reminders: List<FBReminder>) {
        val reminderValues = arrayListOf<ContentValues>()
        for (reminder in reminders) {
            val values = ContentValues()
            values.put(CalendarContract.Reminders.EVENT_ID, localEventId)
            values.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            values.put(CalendarContract.Reminders.MINUTES, reminder.minutesOffset)
            reminderValues.add(values)
        }

        context.contentProviderClient.bulkInsert(
                context.contentUri(CalendarContract.Reminders.CONTENT_URI),
                reminderValues.toTypedArray())
    }

    @Throws(android.os.RemoteException::class,
            android.database.sqlite.SQLiteException::class)
    private fun removeReminder(context: SyncContext, localReminderId: Long) {
        context.contentProviderClient.delete(
                CalendarContract.Reminders.CONTENT_URI,
                "(${CalendarContract.Reminders._ID} = ?)",
                arrayOf(localReminderId.toString()))
    }

    @Throws(android.os.RemoteException::class,
            android.database.sqlite.SQLiteException::class)
    fun update(context: SyncContext, localEventId: Long) {
        val values = ContentValues(this.values)
        values.remove(CalendarContract.Events._ID)
        values.remove(CalendarContract.Events.UID_2445)
        values.remove(CalendarContract.Events.CALENDAR_ID)

        context.contentProviderClient.update(
                context.contentUri(CalendarContract.Events.CONTENT_URI),
                values,
                "(${CalendarContract.Events._ID} = ?)",
                arrayOf(localEventId.toString()))

        val reminders = getLocalReminders(context, localEventId)
        val localReminderSet = reminders.keys
        val configuredReminders = if (isAllDay() == true) mCalendar!!.allDayReminderIntervals else mCalendar!!.reminderIntervals

        // Silly Java can't even subtract Sets...*sigh*
        val toAdd = HashSet<FBReminder>()
        toAdd.addAll(configuredReminders)
        toAdd.removeAll(localReminderSet)

        val toRemove = HashSet<FBReminder>()
        toRemove.addAll(localReminderSet)
        toRemove.removeAll(configuredReminders)

        if (!toAdd.isEmpty()) {
            createReminders(context, localEventId, toAdd.toList())
        }
        toRemove.forEach {
            val r: Long? = reminders[it]
            if (r != null) {
                removeReminder(context, r)
            }
        }
    }

    companion object {
        @Throws(java.text.ParseException::class)
        fun parseDateTime(dt: String): Long {
            val format: SimpleDateFormat
            val date: Date
            if (dt.length > 8) {
                format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
                date = format.parse(dt)
            } else {
                format = SimpleDateFormat("yyyyMMdd Z", Locale.US)
                // HACK: Force SimpleDateFormat to treat the date as UTC
                date = format.parse(dt + " +0000")
            }
            return date.time
        }

        data class FancyDateResult(val dtStart: Long, val dtEnd: Long)
        fun parseFancyDate(dt_: String, timezone: TimeZone = TimeZone.getDefault()): FancyDateResult {
            fun parseInner(dt: String, hourFormat: SimpleDateFormat, minuteFormat: SimpleDateFormat): Long {
                return (if (dt.contains(':')) minuteFormat.parse(dt) else hourFormat.parse(dt)).time
            }
            fun dateFormat(format: String, timezone: TimeZone): SimpleDateFormat {
                return SimpleDateFormat(format, Locale.US).apply {
                    timeZone = timezone
                }
            }

            // FIXME: This code is ugly!
            val dt = dt_.trim()
            val dtStart: Long
            val dtEnd: Long
            if (dt[3] == ' ') { // 3 letters of month at the beginning indicate multi-day event
                if (dt.contains(",")) { // If there's comma, then the string contains a year
                    val hourFormat = dateFormat("MMM d, yyyy 'at' h a", timezone)
                    val minuteFormat = dateFormat("MMM d, yyyy 'at' h:mm a", timezone)
                    val ds = dt.split(" – ")
                    dtStart = parseInner(ds[0], hourFormat, minuteFormat)
                    dtEnd = parseInner(ds[1], hourFormat, minuteFormat)
                } else { // no comma means no year information - implies current year
                    val hourFormat = dateFormat("yyyy MMM d 'at' h a", timezone)
                    val minuteFormat = dateFormat("yyyy MMM d 'at' h:mm a", timezone)
                    val ds = dt.split(" – ")
                    val currentYear = Calendar.getInstance(Locale.US).get(Calendar.YEAR)
                    dtStart = parseInner("$currentYear ${ds[0]}", hourFormat, minuteFormat)
                    dtEnd = parseInner("$currentYear ${ds[1]}", hourFormat, minuteFormat)
                }
            } else { // single day event
                val hourFormat = dateFormat("EEEEE, MMMMM d, yyyy 'at' h a", timezone)
                val minuteFormat = dateFormat("EEEEE, MMMMM d, yyyy 'at' h:mm a", timezone)
                if (dt.contains(" – ")) { // if the event has start and end
                    val ds = dt.split(" – ")
                    dtStart = parseInner(ds[0], hourFormat, minuteFormat)
                    val pos = ds[0].indexOf(" at ") // replace the start time by the end time
                    val endStr = ds[0].substring(0, pos + 4 /* len of " at " */) + ds[1]
                    dtEnd = parseInner(endStr, hourFormat, minuteFormat)
                } else {
                    dtStart = parseInner(dt, hourFormat, minuteFormat)
                    // for events without end time, the webcal uses default duration of 3 hours
                    dtEnd = dtStart + (3 * 60 * 60 * 1000) // 3 hours in milliseconds
                }
            }

            return FancyDateResult(dtStart, dtEnd)
        }

        fun parse(event: Element, context: SyncContext, rsvp: FBCalendar.CalendarType? = null): FBEvent? {
            val fbEvent = FBEvent()
            val values = fbEvent.values

            val uri = event.select("a")?.attr("href") ?: return null
            val start = uri.indexOf('/', 1)
            val end = uri.indexOf('?')
            val uid = uri.substring(start + 1, end)
            values.put(CalendarContract.Events.UID_2445, uid)

            val title = event.select("h4")?.text() ?: return null
            values.put(CalendarContract.Events.TITLE, title)

            val content = event.child(0) ?: return null
            val detail = content.child(1) ?: return null

            val date = detail.child(0)?.text() ?: return null
            val (dtStart, dtEnd) = parseFancyDate(date)
            values.put(CalendarContract.Events.DTSTART, dtStart)
            values.put(CalendarContract.Events.DTEND, dtEnd)
            values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)

            val location = detail.child(1)?.text() ?: return null
            values.put(CalendarContract.Events.EVENT_LOCATION, location)

            if (context.preferences.fbLink()) {
                values.put(CalendarContract.Events.DESCRIPTION, "https://www.facebook.com/$uid")
            }

            if (rsvp != null) {
                fbEvent.rsvp = rsvp
            } else {
                // This abuses an apparent bug(?) in Facebook when events with "Going" presence contain the
                // dot symbol, which is (only sometimes) followed by "Interested" link to toggle the presence.
                // Events with the "Interested" presence do not have the dot, so this is how we can
                // distinguish them.
                // Unfortunately this seems extremely fragile, but right now it is the only way if
                // we want to avoid scraping event details page.
                if (detail.child(2).select("div")?.text()?.contains("·") == true) {
                    fbEvent.rsvp = FBCalendar.CalendarType.TYPE_ATTENDING
                } else {
                    fbEvent.rsvp = FBCalendar.CalendarType.TYPE_MAYBE
                }
            }

            values.put(CalendarContract.Events.CUSTOM_APP_URI, "fb://event?id=$uid")

            return fbEvent
        }

        fun parse(vevent: VEvent, context: SyncContext): FBEvent {
            val fbEvent = FBEvent()
            val values = fbEvent.values

            var uid = vevent.uid.value
            val id = uid.substring(1, uid.indexOf("@"))
            var isBirthday = true
            if (uid.startsWith("e")) { // events
                uid = uid.substring(1, uid.indexOf("@"))
                isBirthday = false
            }

            values.put(CalendarContract.Events.UID_2445, uid)
            values.put(CalendarContract.Events.TITLE, vevent.summary.value)
            val organizer = vevent.organizer
            if (organizer != null) {
                values.put(CalendarContract.Events.ORGANIZER, organizer.commonName)
            }
            val location = vevent.location
            if (location != null) {
                values.put(CalendarContract.Events.EVENT_LOCATION, location.value)
            }
            values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)

            if (isBirthday) {
                if (context.preferences.fbLink()) {
                    values.put(CalendarContract.Events.DESCRIPTION, "https://www.facebook.com/$id")
                } else {
                    values.put(CalendarContract.Events.DESCRIPTION, String())
                }
                val date = vevent.dateStart.value
                val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                // HACK: Facebook only lists the "next" birthdays, which means birthdays disappear from
                // the listing the day after it they pass. Many users dislike that (and I understand why)
                // so we hack around by always setting the year as current year - 1 and setting yearly
                // recurrence so that they don't disappear from the calendar
                @Suppress("DEPRECATION")
                calendar.set(calendar.get(Calendar.YEAR) - 1, date.month, date.date, 0, 0, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                values.put(CalendarContract.Events.DTSTART, calendar.timeInMillis)
                // Those are identical for all birthdays, so we hardcode them
                values.put(CalendarContract.Events.ALL_DAY, 1)
                values.put(CalendarContract.Events.RRULE, "FREQ=YEARLY")
                values.put(CalendarContract.Events.DURATION, "P1D")

                fbEvent.rsvp = FBCalendar.CalendarType.TYPE_BIRTHDAY
                values.put(CalendarContract.Events.CUSTOM_APP_URI, "fb://user?id=$id")
            } else {
                val desc = vevent.description
                if (desc != null) {
                    var descStr = desc.value
                    if (!context.preferences.fbLink()) {
                        val pos = descStr.lastIndexOf('\n')
                        if (pos > -1) {
                            descStr = descStr.substring(0, pos)
                        }
                    }
                    values.put(CalendarContract.Events.DESCRIPTION, descStr)
                }

                val start = vevent.dateStart
                values.put(CalendarContract.Events.DTSTART, start.value.time)
                val end = vevent.dateEnd
                if (end != null) {
                    values.put(CalendarContract.Events.DTEND, end.value.time)
                }

                val prop = vevent.getExperimentalProperty("PARTSTAT")
                if (prop != null) {
                    when (prop.value) {
                        "ACCEPTED" -> fbEvent.rsvp = FBCalendar.CalendarType.TYPE_ATTENDING
                        "TENTATIVE" -> fbEvent.rsvp = FBCalendar.CalendarType.TYPE_MAYBE
                        "DECLINED" -> fbEvent.rsvp = FBCalendar.CalendarType.TYPE_DECLINED
                        "NEEDS-ACTION" -> fbEvent.rsvp = FBCalendar.CalendarType.TYPE_NOT_REPLIED
                        else -> context.logger.warning("SYNC.EVENT", "Unknown RSVP status '${prop.value}'")
                    }
                }

                values.put(CalendarContract.Events.CUSTOM_APP_URI, "fb://event?id=$id")
            }

            return fbEvent
        }

        @Throws(android.os.RemoteException::class,
                android.database.sqlite.SQLiteException::class)
        fun remove(context: SyncContext, localEventId: Long) {
            context.contentProviderClient.delete(
                    context.contentUri(CalendarContract.Events.CONTENT_URI),
                    "(${CalendarContract.Events._ID} = ?)",
                    arrayOf(localEventId.toString()))
        }
    }
}
