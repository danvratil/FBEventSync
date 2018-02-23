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

import android.content.Context
import android.content.SharedPreferences

object PreferencesMigrator {

    private const val PREFERENCES_VERSION = 1

    @Synchronized
    fun migrate(context: Context) {

        val prefs = context.getSharedPreferences(context.getString(R.string.cz_dvratil_fbeventsync_preferences), Context.MODE_MULTI_PROCESS)

        val version = prefs.getInt(context.getString(R.string.cfg_prefs_version), 0)
        // Nothing to do
        if (version == PREFERENCES_VERSION) {
            return
        }

        val editor = prefs.edit()

        if (version < 1) {
            updateToVersion1(prefs, editor, context)
        }

        editor.putInt(context.getString(R.string.cfg_prefs_version), PREFERENCES_VERSION)
        editor.apply()
    }


    // Version 1 introduced versioning and contains fix for default calendar color values
    private fun updateToVersion1(prefs: SharedPreferences, editor: SharedPreferences.Editor, context: Context) {
        val wrongColor = 3889560
        val correctColor = context.resources.getColor(R.color.colorFBBlue)

        arrayOf(R.string.pref_calendar_attending_color,
                R.string.pref_calendar_tentative_color,
                R.string.pref_calendar_declined_color,
                R.string.pref_calendar_not_responded_color,
                R.string.pref_calendar_birthday_color).forEach {
            if (prefs.getInt(context.getString(it), correctColor) == wrongColor) {
                editor.putInt(context.getString(it), correctColor)
            }
        }
    }
}
