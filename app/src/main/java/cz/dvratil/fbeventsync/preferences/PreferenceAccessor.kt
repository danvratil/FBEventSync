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

import android.content.ContentValues
import android.content.Context
import android.database.Cursor

object PreferenceAccessor {

    private inline fun <T>query(context: Context, key: String, defaultValue: T, type: PreferencesProvider.ValueType,
                         extractor:(cursor: Cursor, column: Int) -> T): T {
        val uri = PreferencesProvider.buildUri(key, type)
        val cursor = context.contentResolver.query(uri, arrayOf(PreferencesProvider.COLUMN_VALUE),
                "(${PreferencesProvider.COLUMN_KEY} = ?)", arrayOf(key), null)
        val result = when(cursor?.moveToFirst()) {
            true -> extractor(cursor, cursor.getColumnIndex(PreferencesProvider.COLUMN_VALUE))
            else -> defaultValue
        }
        cursor?.close()
        return result
    }

    private inline fun <reified T>update(context: Context, key: String, value: T, type: PreferencesProvider.ValueType) {
        val uri = PreferencesProvider.buildUri(key, type)
        val values = ContentValues().apply {
            put(PreferencesProvider.COLUMN_KEY, key)
            // Unlike C++ templates, Java generics are resolved at runtime, so we can't use it to
            // call the correct overload of put() based on T
            when (type) {
                PreferencesProvider.ValueType.BOOLEAN,
                PreferencesProvider.ValueType.INTEGER -> put(PreferencesProvider.COLUMN_VALUE, value as Int)
                PreferencesProvider.ValueType.LONG -> put(PreferencesProvider.COLUMN_VALUE, value as Long)
                PreferencesProvider.ValueType.STRINGSET,
                PreferencesProvider.ValueType.STRING -> put(PreferencesProvider.COLUMN_VALUE, value as String?)
                PreferencesProvider.ValueType.FLOAT -> put(PreferencesProvider.COLUMN_VALUE, value as Float)
            }
        }
        context.contentResolver.insert(uri, values)
    }

    private inline fun remove(context: Context, key: String, type: PreferencesProvider.ValueType) {
        var uri = PreferencesProvider.buildUri(key, type)
        context.contentResolver.delete(uri, "(${PreferencesProvider.COLUMN_KEY} = ?)", arrayOf(key))
    }


    fun getString(context: Context, key: String, defaultValue: String?): String?
            = query(context, key, defaultValue, PreferencesProvider.ValueType.STRING,
                { cursor, column -> cursor.getString(column) })

    fun putString(context: Context, key: String, value: String?)
            = update(context, key, value, PreferencesProvider.ValueType.STRING)

    fun removeString(context: Context, key: String)
            = remove(context, key, PreferencesProvider.ValueType.STRING)


    fun getStringSet(context: Context, key: String, defaultValue: Set<String>?)
            = query(context, key, defaultValue, PreferencesProvider.ValueType.STRINGSET,
                  { cursor, column -> Utils.stringToSet(cursor.getString(column)) })

    fun putStringSet(context: Context, key: String, value: Set<String>?)
            = update(context, key, Utils.setToString(value), PreferencesProvider.ValueType.STRINGSET)

    fun removeStringSet(context: Context, key: String)
            = remove(context, key, PreferencesProvider.ValueType.STRINGSET)


    fun getInteger(context: Context, key: String, defaultValue: Int)
            = query(context, key, defaultValue, PreferencesProvider.ValueType.INTEGER,
                    { cursor, column -> cursor.getInt(column) })

    fun putInteger(context: Context, key: String, value: Int)
            = update(context, key, value, PreferencesProvider.ValueType.INTEGER)

    fun removeInteger(context: Context, key: String)
            = remove(context, key, PreferencesProvider.ValueType.INTEGER)


    fun getLong(context: Context, key: String, defaultValue: Long)
            = query(context, key, defaultValue, PreferencesProvider.ValueType.LONG,
                  { cursor, column -> cursor.getLong(column) })

    fun putLong(context: Context, key: String, value: Long)
            = update(context, key, value, PreferencesProvider.ValueType.LONG)

    fun removeLong(context: Context, key: String)
            = remove(context, key, PreferencesProvider.ValueType.LONG)


    fun getBoolean(context: Context, key: String, defaultValue: Boolean)
            = query(context, key, defaultValue, PreferencesProvider.ValueType.BOOLEAN,
                  { cursor, column -> (cursor.getInt(column) == 1) })

    fun putBoolean(context: Context, key: String, value: Boolean)
            = update(context, key, if (value) 1 else 0, PreferencesProvider.ValueType.BOOLEAN)

    fun removeBoolean(context: Context, key: String)
            = remove(context, key, PreferencesProvider.ValueType.BOOLEAN)


    fun getFloat(context: Context, key: String, defaultValue: Float)
            = query(context, key, defaultValue, PreferencesProvider.ValueType.FLOAT,
                { cursor, column -> cursor.getFloat(column) })

    fun putFloat(context: Context, key: String, value: Float)
            = update(context, key, value, PreferencesProvider.ValueType.FLOAT)

    fun removeFloat(context: Context, key: String)
            = remove(context, key, PreferencesProvider.ValueType.FLOAT)
}