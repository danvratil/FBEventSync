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

package cz.dvratil.fbeventsync.preferences

import android.content.Context
import android.support.v7.preference.PreferenceDataStore

class Preferences(private var mContext: Context): PreferenceDataStore() {

    override fun getString(key: String, defaultValue: String?) = PreferenceAccessor.getString(mContext, key, defaultValue)
    override fun putString(key: String, value: String?) = PreferenceAccessor.putString(mContext, key, value)
    fun removeString(key: String) = PreferenceAccessor.removeString(mContext, key)

    override fun getInt(key: String, defaultValue: Int) = PreferenceAccessor.getInteger(mContext, key, defaultValue)
    override fun putInt(key: String, value: Int) = PreferenceAccessor.putInteger(mContext, key, value)
    fun removeInt(key: String) = PreferenceAccessor.removeInteger(mContext, key)

    override fun getLong(key: String, defaultValue: Long) = PreferenceAccessor.getLong(mContext, key, defaultValue)
    override fun putLong(key: String, value: Long) = PreferenceAccessor.putLong(mContext, key, value)
    fun removeLong(key: String) = PreferenceAccessor.removeLong(mContext, key)

    override fun getBoolean(key: String, defaultValue: Boolean) = PreferenceAccessor.getBoolean(mContext, key, defaultValue)
    override fun putBoolean(key: String, value: Boolean) = PreferenceAccessor.putBoolean(mContext, key, value)
    fun removeBoolean(key: String) = PreferenceAccessor.removeBoolean(mContext, key)

    override fun getStringSet(key: String, defaultValue: Set<String>?) = PreferenceAccessor.getStringSet(mContext, key, defaultValue)
    override fun putStringSet(key: String, value: Set<String>?) = PreferenceAccessor.putStringSet(mContext, key, value)
    fun removeStringSet(key: String) = PreferenceAccessor.removeStringSet(mContext, key)

    override fun getFloat(key: String, defaultValue: Float) = PreferenceAccessor.getFloat(mContext, key, defaultValue)
    override fun putFloat(key: String, value: Float) = PreferenceAccessor.putFloat(mContext, key, value)
    fun removeFloat(key: String) = PreferenceAccessor.removeFloat(mContext, key)
}
