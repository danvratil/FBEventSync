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

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.view.MenuItem


class AllDayReminderPreferenceActivity : AppCompatActivity()
{
    class AllDayReminderAdapter(context: AllDayReminderPreferenceActivity, layoutId: Int, textViewId: Int)
        : ArrayAdapter<FBReminder>(context, layoutId, textViewId) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = super.getView(position, convertView, parent)
            view.findViewById<Button>(R.id.allday_reminder_remove_button).setOnClickListener {
                val activity = context as AllDayReminderPreferenceActivity
                var reminders = activity.getReminders().toMutableList()
                reminders.remove(getItem(position))
                activity.setReminders(reminders)
            }
            return view
        }
    }


    private lateinit var m_listView: ListView
    private lateinit var m_viewAdapter: AllDayReminderAdapter
    private lateinit var m_calendarType: FBCalendar.CalendarType

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.pref_allday_reminder_layout)
        setSupportActionBar(findViewById<View>(R.id.pref_allday_reminder_toolbar) as Toolbar)

        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        m_calendarType = when (intent.extras.getString("calendarType")) {
            getString(R.string.pref_calendar_attending_allday_reminders) -> FBCalendar.CalendarType.TYPE_ATTENDING
            getString(R.string.pref_calendar_tentative_allday_reminders) -> FBCalendar.CalendarType.TYPE_MAYBE
            getString(R.string.pref_calendar_not_responded_allday_reminders) -> FBCalendar.CalendarType.TYPE_NOT_REPLIED
            getString(R.string.pref_calendar_declined_allday_reminders) -> FBCalendar.CalendarType.TYPE_DECLINED
            getString(R.string.pref_calendar_birthday_allday_reminders) -> FBCalendar.CalendarType.TYPE_BIRTHDAY
            else -> throw Exception("Invalid calendar type in intent!")
        }

        m_viewAdapter = AllDayReminderAdapter(this, R.layout.allday_reminder_item_layout, R.id.allday_reminder_item_text)
        m_listView = findViewById<ListView>(R.id.pref_allday_reminders_listView).apply {
            adapter = m_viewAdapter
        }

        findViewById<FloatingActionButton>(R.id.pref_allday_reminders_fab).setOnClickListener {
            val view = layoutInflater.inflate(R.layout.allday_reminder_dialog, null)
            val dayPicker = view.findViewById<NumberPicker>(R.id.allday_reminder_day_picker).apply {
                minValue = 1 // FIXME: Investigate if we can have a reminder on the day of the event
                value = 1
                maxValue = 30
            }
            val timePicker = view.findViewById<TimePicker>(R.id.allday_reminder_time_picker).apply {
                // TODO: Preselects current time by default, maybe we could round up/down to nearest half-hour?
                setIs24HourView(DateFormat.is24HourFormat(context))
            }
            AlertDialog.Builder(this)
                    .setView(view)
                    .setPositiveButton(R.string.btn_save) { dialog, _ ->
                        val currentReminders = getReminders().toMutableList()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            currentReminders.add(FBReminder(dayPicker.value, timePicker.hour, timePicker.minute, true))
                        } else {
                            currentReminders.add(FBReminder(dayPicker.value, timePicker.currentHour, timePicker.currentMinute, true))
                        }
                        setReminders(currentReminders)

                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.btn_cancel) { dialog, _ ->
                        dialog.cancel()
                    }
                    .create()
                    .show()
        }
        m_viewAdapter.addAll(getReminders())
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == android.R.id.home) {
            // Workaround an activity stack related crash when navigating back using the
            // toolbar home button
            finish()
            return true
        } else {
            return super.onOptionsItemSelected(item)
        }
    }

    private fun getReminders(): List<FBReminder> {
        var prefs = Preferences(this)
        return when (m_calendarType) {
            FBCalendar.CalendarType.TYPE_ATTENDING -> prefs.attendingCalendarAllDayReminders()
            FBCalendar.CalendarType.TYPE_MAYBE -> prefs.maybeAttendingCalendarAllDayReminders()
            FBCalendar.CalendarType.TYPE_DECLINED -> prefs.declinedCalendarAllDayReminders()
            FBCalendar.CalendarType.TYPE_NOT_REPLIED -> prefs.notRespondedCalendarAllDayReminders()
            FBCalendar.CalendarType.TYPE_BIRTHDAY -> prefs.birthdayCalendarAllDayReminders()
        }
    }

    private fun setReminders(reminders: List<FBReminder>) {
        var prefs = Preferences(this)
        when (m_calendarType) {
            FBCalendar.CalendarType.TYPE_ATTENDING -> prefs.setAttendingCalendarAllDayReminders(reminders)
            FBCalendar.CalendarType.TYPE_MAYBE -> prefs.setMaybeAttendingCalendarAllDayReminders(reminders)
            FBCalendar.CalendarType.TYPE_DECLINED -> prefs.setDeclinedCalendarAllDayReminders(reminders)
            FBCalendar.CalendarType.TYPE_NOT_REPLIED -> prefs.setNotRespondedCalendarAllDayReminders(reminders)
            FBCalendar.CalendarType.TYPE_BIRTHDAY -> prefs.setBirthdayCalendarAllDayReminders(reminders)
        }
        m_viewAdapter.clear()
        m_viewAdapter.addAll(reminders)
    }
}