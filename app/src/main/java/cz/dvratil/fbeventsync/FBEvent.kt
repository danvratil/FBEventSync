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
import java.util.regex.Matcher
import java.util.regex.Pattern

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
            val eventId = uri.lastPathSegment!!.toLong()
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

        private const val TAG = "FBEvent"

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
                date = format.parse("$dt +0000")
            }
            return date.time
        }

        val weekDays = Calendar.getInstance(Locale.US).getDisplayNames(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.US).keys.joinToString("|")
        val longMonths = Calendar.getInstance(Locale.US).getDisplayNames(Calendar.MONTH, Calendar.LONG, Locale.US).keys.joinToString("|")
        private val shortMonths = Calendar.getInstance(Locale.US).getDisplayNames(Calendar.MONTH, Calendar.SHORT, Locale.US).keys.joinToString("|")

        private val re_singleDaySoon: Pattern = Pattern.compile("(Today|Tomorrow) at ([0-9]{1,2})(:([0-9]{1,2}))? (AM|PM)( – ([0-9]{1,2})(:([0-9]{1,2}))? (AM|PM))?")
        private val re_singleDayNoYear: Pattern = Pattern.compile("($weekDays) at ([0-9]{1,2})(:([0-9]{1,2}))? (AM|PM)( – ([0-9]{1,2})(:([0-9]{1,2}))? (AM|PM))?")
        private val re_singleDayWithYear: Pattern = Pattern.compile("($weekDays), ($longMonths) ([0-9]{1,2}), ([0-9]{4}) at ([0-9]{1,2})(:([0-9]{1,2}))? (AM|PM)( – ([0-9]{1,2})(:([0-9]{1,2}))? (AM|PM))?")
        private val re_multiDayNoYear: Pattern = Pattern.compile("($shortMonths) ([0-9]{1,2}) at ([0-9]{1,2})(:([0-9]{1,2}))? (AM|PM)")
        private val re_multiDayAllDay: Pattern = Pattern.compile("($shortMonths) ([0-9]{1,2})(, ([0-9]{4}))? – ($shortMonths) ([0-9]{1,2})(, ([0-9]{4}))?")
        private val re_singleDayAllDay: Pattern = Pattern.compile("(Today|Tomorrow|$shortMonths)( ([0-9]{1,2}))?(, ([0-9]{4}))?")
        private val re_multiDayWithYear: Pattern = Pattern.compile("($shortMonths) ([0-9]{1,2}), ([0-9]{4}) at ([0-9]{1,2})(:([0-9]{1,2}))? (AM|PM)")

        data class FancyDateResult(val dtStart: Long, val dtEnd: Long, val allDay: Boolean = false)

        private fun parseSingleDayNoYear(match: Matcher, timezone: TimeZone): FancyDateResult {
            val day = match.group(1)
            val startHour = match.group(2)
            val startMinute = match.group(4)
            val startAmPm = match.group(5)
            val endHour = match.group(7)
            val endMinute = match.group(9)
            val endAmPm = match.group(10)

            var cal = Calendar.getInstance(timezone)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            when(day) {
                "Today" -> {} // cal is already today
                "Tomorrow" -> cal.add(Calendar.DATE, 1) // add 1 day
                else -> while (cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.US) != day) {
                            cal.add(Calendar.DATE, 1)
                        }
            }

            cal.set(Calendar.HOUR, startHour.toInt())
            cal.set(Calendar.MINUTE, startMinute?.toInt() ?: 0)
            cal.set(Calendar.AM_PM, if (startAmPm == "AM") Calendar.AM else Calendar.PM)
            val dtStart = cal.time.time

            val dtEnd = if (endHour == null) {
                cal.add(Calendar.HOUR, 3)
                cal.time.time
            } else {
                cal.set(Calendar.HOUR, endHour.toInt())
                cal.set(Calendar.MINUTE, endMinute?.toInt() ?: 0)
                cal.set(Calendar.AM_PM, if (endAmPm == "AM") Calendar.AM else Calendar.PM)
                cal.time.time
            }

            return FancyDateResult(dtStart, dtEnd)
        }

        private fun parseSingleDayWithYear(match: Matcher, timezone: TimeZone): FancyDateResult {
            val month = match.group(2)
            val day = match.group(3)
            val year = match.group(4)
            val startHour = match.group(5)
            val startMinute = match.group(7) ?: "00"
            val startAmPm = match.group(8)
            val endHour = match.group(10)
            val endMinute = match.group(12)
            val endAmPm = match.group(13)

            var date = SimpleDateFormat("MMMMM dd, yyyy hh:mm a", Locale.US).parse("$month $day, $year $startHour:$startMinute $startAmPm")
            val dtStart = date.time

            var cal = Calendar.getInstance(timezone)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.time = date
            val dtEnd = if (endHour == null) {
                cal.add(Calendar.HOUR, 3)
                cal.time.time
            } else {
                cal.set(Calendar.HOUR, endHour.toInt())
                cal.set(Calendar.MINUTE, endMinute?.toInt() ?: 0)
                cal.set(Calendar.AM_PM, if (endAmPm == "AM") Calendar.AM else Calendar.PM)
                cal.time.time
            }

            return FancyDateResult(dtStart, dtEnd)
        }

        private fun parseMultiDayNoYear(match: Matcher, timezone: TimeZone): FancyDateResult {
            fun parseGroup(match: Matcher, timezone: TimeZone): Long {
                val month = match.group(1)
                val day = match.group(2)
                val hour = match.group(3)
                val minute = match.group(5) ?: "00"
                val amPm = match.group(6)
                val year = Calendar.getInstance(timezone).get(Calendar.YEAR)

                var date = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.US).parse("$month $day, $year $hour:$minute $amPm")
                return date.time
            }

            val dtStart = parseGroup(match, timezone)
            if (!match.find()) {
                throw IllegalArgumentException()
            }
            val dtEnd = parseGroup(match, timezone)

            return FancyDateResult(dtStart, dtEnd)
        }

        private fun parseMultiDayAllDay(match: Matcher, timezone: TimeZone): FancyDateResult {
            val startMonth = match.group(1)
            val startDay = match.group(2)
            val startYear = match.group(4) ?: Calendar.getInstance(timezone).get(Calendar.YEAR)
            val endMonth = match.group(5)
            val endDay = match.group(6)
            val endYear = match.group(8) ?: Calendar.getInstance(timezone).get(Calendar.YEAR)

            var format = SimpleDateFormat("MMM dd, yyyy")
            format.timeZone = TimeZone.getTimeZone("UTC")
            val dtStart = format.parse("$startMonth $startDay, $startYear")
            val dtEnd = format.parse("$endMonth $endDay, $endYear")

            return FancyDateResult(dtStart.time, dtEnd.time, true)
        }

        private fun parseSingleDayAllDay(match: Matcher, timezone: TimeZone): FancyDateResult {
            val month = match.group(1)
            val day = match.group(3)
            val year = match.group(5) ?: Calendar.getInstance(timezone).get(Calendar.YEAR)

            if (month == "Today" || month == "Tomorrow") {
                val today = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                today.set(Calendar.HOUR, 0)
                today.set(Calendar.MINUTE, 0)
                today.set(Calendar.SECOND, 0)
                today.set(Calendar.MILLISECOND, 0)
                if (month == "Tomorrow") {
                    today.add(Calendar.DATE, 1)
                }
                return FancyDateResult(today.timeInMillis, today.timeInMillis, true)
            }

            val format = SimpleDateFormat("MMM dd, yyyy")
            format.timeZone = TimeZone.getTimeZone("UTC")
            val dt = format.parse("$month $day, $year").time

            return FancyDateResult(dt, dt, true)
        }

        private fun parseMultiDayWithYear(match: Matcher, timezone: TimeZone): FancyDateResult {
            fun parseGroup(match: Matcher, timezone: TimeZone): Long {
                val month = match.group(1)
                val day = match.group(2)
                val year = match.group(3)
                val hour = match.group(4)
                val minute = match.group(6) ?: "00"
                val amPm = match.group(7)

                var date = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.US).parse("$month $day, $year $hour:$minute $amPm")
                return date.time
            }

            val dtStart = parseGroup(match, timezone)
            if (!match.find()) {
                throw IllegalArgumentException()
            }
            val dtEnd = parseGroup(match, timezone)

            return FancyDateResult(dtStart, dtEnd)
        }


        fun parseFancyDate(dt_: String, timezone: TimeZone = TimeZone.getDefault(), context: SyncContext? = null): FancyDateResult {
            val dt = dt_.trim()

            var match = re_singleDaySoon.matcher(dt)
            if (match.matches()) {
                return parseSingleDayNoYear(match, timezone)
            }
            match = re_singleDayNoYear.matcher(dt)
            if (match.matches()) {
                return parseSingleDayNoYear(match, timezone)
            }
            match = re_singleDayWithYear.matcher(dt)
            if (match.matches()) {
                return parseSingleDayWithYear(match, timezone)
            }
            match = re_multiDayNoYear.matcher(dt)
            if (match.find()) {
                return parseMultiDayNoYear(match, timezone)
            }
            match = re_multiDayWithYear.matcher(dt)
            if (match.find()) {
                return parseMultiDayWithYear(match, timezone)
            }
            match = re_multiDayAllDay.matcher(dt)
            if (match.matches()) {
                return parseMultiDayAllDay(match, timezone)
            }
            match = re_singleDayAllDay.matcher(dt)
            if (match.matches()) {
                return parseSingleDayAllDay(match, timezone)
            }

            context?.logger?.error(TAG, "Unknown datetime format: '$dt'.")
            throw IllegalArgumentException()
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
            val (dtStart, dtEnd, allDay) = parseFancyDate(date, TimeZone.getDefault(), context)
            values.put(CalendarContract.Events.DTSTART, dtStart)
            values.put(CalendarContract.Events.DTEND, dtEnd)
            values.put(CalendarContract.Events.ALL_DAY, allDay)
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
                        else -> context.logger.warning(TAG, "Unknown RSVP status '${prop.value}'")
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
