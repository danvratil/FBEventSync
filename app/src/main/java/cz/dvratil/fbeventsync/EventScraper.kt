/*
    Copyright (C) 2019 Daniel Vrátil <me@dvratil.cz>

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

import android.provider.CalendarContract
import org.jsoup.Connection
import org.jsoup.Jsoup
import java.net.URL
import java.util.*

class EventScraper {

    class CookiesExpiredException: Exception() {}

    fun fetchInvites(context: SyncContext, cookies: String): List<FBEvent> {
        return doFetchEvents("invites", FBCalendar.CalendarType.TYPE_NOT_REPLIED, context, cookies)
    }

    fun fetchDeclined(context: SyncContext, cookies: String): List<FBEvent> {
        return doFetchEvents("declined", FBCalendar.CalendarType.TYPE_DECLINED, context, cookies)
    }

    fun fetchBirthdays(context: SyncContext, cookies: String): List<FBEvent> {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        var events: MutableList<FBEvent> = mutableListOf()
        for (month in 1..12) {
            var conn = prepareConnection("https://mbasic.facebook.com/events/birthdays?cursor=$year-%02d-01&locale=en_US".format(month), cookies)
            val document = conn.get()
            context.logger.debug("SCRAPER", "Fetched ${conn.request().url()}")
            checkRequestUrlIsValid(conn.request().url())
            val eventElements = document.select("div[role='article'] ul>li")

            val newEvents = eventElements.mapNotNull { FBBirthdayEvent.parse(it, context) }
            context.logger.debug("SCRAPER", "Scraped ${newEvents.size} birthdays")
            events.addAll(newEvents)
        }

        return events
    }

    fun fetchEvents(skipEvents: List<String>, context: SyncContext, cookies: String): List<FBEvent> {
        return doFetchEvents("calendar", null, context, cookies).filter {
            !skipEvents.contains(it.values.get(CalendarContract.Events.UID_2445))
        }
    }

    private fun checkRequestUrlIsValid(url: URL) {
        if (!url.path.startsWith("/events")) {
            throw CookiesExpiredException()
        }
    }


    private fun prepareConnection(url: String, cookies: String): Connection {
        var conn = Jsoup.connect(url)
        cookies.split("; ").forEach {
            val (name, value) = it.split('=', limit = 2)
            conn.cookie(name, value)
        }
        return conn
    }

    private fun doFetchEvents(resource: String, rsvp: FBCalendar.CalendarType?, context: SyncContext, cookies: String): List<FBEvent> {
        var conn = prepareConnection("https://mbasic.facebook.com/events/$resource?locale=en_US", cookies)
        var events: MutableList<FBEvent> = mutableListOf()
        while (true) {
            val document = conn.get()
            context.logger.debug("SCRAPER", "Fetched ${conn.request().url()}")
            checkRequestUrlIsValid(conn.request().url())
            val eventElements = document.select("div[role='article']")
            if (eventElements?.first()?.text() == "Currently No Events") {
                return events
            }

            val newEvents = eventElements.mapNotNull { FBEvent.parse(it, context, rsvp) }
            context.logger.debug("SCRAPER", "Scraped ${newEvents.size} events")
            events.addAll(newEvents)

            var moreLink = document.selectFirst("#event_list_seemore>a")?.attr("href")
                    ?: return events
            conn = prepareConnection("https://mbasic.facebook.com$moreLink&locale=en_US", cookies)
        }
    }

}