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

// This is a workaround for Android being obnoxious and requiring Context to access our own resources
// (seriously, wtf?)
// Source: https://www.linkedin.com/pulse/android-dev-tips-how-get-static-application-context-kropachov/

object AppHolder {

    private val s_app : android.app.Application = try {
        Class.forName("android.app.ActivityThread").getDeclaredMethod("currentApplication").invoke(null) as android.app.Application
    } catch (e : Throwable) {
        throw AssertionError(e)
    }

    fun getContext() : Context? = s_app.applicationContext
}