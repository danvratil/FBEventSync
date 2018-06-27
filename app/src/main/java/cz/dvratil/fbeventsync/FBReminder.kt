/*
    Copyright (C) 2018  Daniel Vrátil <me@dvratil.cz>

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

import java.text.DateFormat
import java.util.*
import kotlin.math.abs


class FBReminder(var minutesOffset: Int = -1, var allDay: Boolean = false) : Comparable<FBReminder> {


    constructor(daysBefore: Int, hours: Int, minutes: Int, allDay: Boolean)
            : this((daysBefore * 24 * 60) - (hours * 60) - minutes, allDay)

    companion object {
        fun fromString(serialized: String): FBReminder {
            val split = serialized.split(',')
            try {
                var minutes = if (split.isNotEmpty()) split[0].toInt() else -1
                var allDay = if (split.size >= 2) split[1].toBoolean() else false
                if (minutes > -1) {
                    return FBReminder(minutes, allDay)
                }
            } catch (e: NumberFormatException) {
                // fall-through
            }
            return FBReminder()
        }
    }

    override fun equals(other: Any?): Boolean
        = other is FBReminder
            && minutesOffset == other.minutesOffset
            && allDay == other.allDay

    override fun compareTo(other: FBReminder): Int = other.minutesOffset - minutesOffset

    fun isvalid() = minutesOffset > -1
    fun serialize() = if (allDay) "$minutesOffset,$allDay" else "$minutesOffset"

    override fun toString(): String {
        val res = AppHolder.getContext()?.resources ?: return ""
        if (allDay) {
            val daysBefore = minutesOffset / (24 * 60) + 1
            val hours =  abs((minutesOffset - (daysBefore * (24 * 60))) / 60)
            var minutes = abs((minutesOffset - (daysBefore * (24 * 60))) % 60)
            val formattedTime = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(0, 0, 0, hours, minutes, 0))
            return res.getQuantityString(R.plurals.pref_reminder_allday_description, daysBefore, daysBefore, formattedTime)
        } else {
            return when (minutesOffset) {
                res.getString(R.string.pref_reminder_0minutes_value).toInt() -> res.getString(R.string.pref_reminder_0minutes_entry)
                res.getString(R.string.pref_reminder_5minutes_value).toInt() -> res.getString(R.string.pref_reminder_5minutes_entry)
                res.getString(R.string.pref_reminder_10minutes_value).toInt() -> res.getString(R.string.pref_reminder_10minutes_entry)
                res.getString(R.string.pref_reminder_15minutes_value).toInt() -> res.getString(R.string.pref_reminder_15minutes_entry)
                res.getString(R.string.pref_reminder_20minutes_value).toInt() -> res.getString(R.string.pref_reminder_20minutes_entry)
                res.getString(R.string.pref_reminder_30minutes_value).toInt() -> res.getString(R.string.pref_reminder_30minutes_entry)
                res.getString(R.string.pref_reminder_1hour_value).toInt() -> res.getString(R.string.pref_reminder_1hour_entry)
                res.getString(R.string.pref_reminder_2hours_value).toInt() -> res.getString(R.string.pref_reminder_2hours_entry)
                res.getString(R.string.pref_reminder_12hours_value).toInt() -> res.getString(R.string.pref_reminder_12hours_entry)
                res.getString(R.string.pref_reminder_24hours_value).toInt() -> res.getString(R.string.pref_reminder_24hours_entry)
                res.getString(R.string.pref_reminder_36hours_value).toInt() -> res.getString(R.string.pref_reminder_36hours_entry)
                res.getString(R.string.pref_reminder_2days_value).toInt() -> res.getString(R.string.pref_reminder_2days_entry)
                res.getString(R.string.pref_reminder_1week_value).toInt() -> res.getString(R.string.pref_reminder_1week_entry)
                else -> res.getQuantityString(R.plurals.pref_reminder_anyminutes, minutesOffset, minutesOffset)
            }
        }
    }

}