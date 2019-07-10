package cz.dvratil.fbeventsync

import android.provider.CalendarContract
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

class FBBirthdayEvent: FBEvent() {

    companion object {
        private fun parseFancyBirthdayDate(dt: String): Date {
            return if (dt.count { it == ',' } == 2) {
                SimpleDateFormat("EEEEE, MMMMM d, yyyy", Locale.US).parse(dt)
            } else { // handle fancy dates like "Tomorrow, July 11"
                val ds = dt.split(", ")
                val year = Calendar.getInstance().get(Calendar.YEAR)
                SimpleDateFormat("MMMMM d yyyy", Locale.US).parse("${ds[1]} $year")
            }
        }

        fun parse(event: Element, context: SyncContext): FBEvent? {
            var fbEvent = FBBirthdayEvent()
            var values = fbEvent.values

            var link = event.select("a")?.first() ?: return null
            var uri = link.attr("href")
            var name = link.child(0)?.text() ?: return null
            var date = parseFancyBirthdayDate(link.child(2)?.text() ?: return null)

            values.put(CalendarContract.Events.UID_2445, uri.substring(1))
            values.put(CalendarContract.Events.TITLE, context.context.resources.getString(R.string.birthday_event_title, name))
            values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)

            if (context.preferences.fbLink()) {
                values.put(CalendarContract.Events.DESCRIPTION, "https://www.facebook.com$uri")
            } else {
                values.put(CalendarContract.Events.DESCRIPTION, String())
            }

            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            @Suppress("DEPRECATION")
            calendar.set(calendar.get(Calendar.YEAR) - 1, date.month, date.date, 0, 0, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            values.put(CalendarContract.Events.DTSTART, calendar.timeInMillis)
            // Those are identical for all birthdays, so we hardcode them
            values.put(CalendarContract.Events.ALL_DAY, 1)
            values.put(CalendarContract.Events.RRULE, "FREQ=YEARLY")
            values.put(CalendarContract.Events.DURATION, "P1D")

            fbEvent.rsvp = FBCalendar.CalendarType.TYPE_BIRTHDAY
            values.put(CalendarContract.Events.CUSTOM_APP_URI, "fb://user?id=${uri.substring(1)}")

            return fbEvent
        }
    }
}