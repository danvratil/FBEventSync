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

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri

class PreferencesProvider: ContentProvider() {

    enum class ValueType constructor(private val id: Int, private val typeName: String, private val table: String) {
        BOOLEAN(1, "boolean", StoreHelper.INT_TABLE),
        INTEGER(2, "integer", StoreHelper.INT_TABLE),
        LONG(3, "long", StoreHelper.INT_TABLE),
        STRING(4, "string", StoreHelper.STRING_TABLE),
        STRINGSET(5, "stringset", StoreHelper.STRING_TABLE),
        FLOAT(6, "float", StoreHelper.FLOAT_TABLE);

        fun id() = id
        fun typeName() = typeName
        fun table() = table
    }

    companion object {
        const val AUTHORITY = "cz.dvratil.fbeventsync.preferences.PreferencesProvider"
        const val COLUMN_KEY = "key"
        const val COLUMN_VALUE = "value"

        var dbName = "fbeventsync_prefs"

        private val sUriMatcher = UriMatcher(UriMatcher.NO_MATCH)

        init {
            sUriMatcher.addURI(AUTHORITY, "boolean/*", ValueType.BOOLEAN.id())
            sUriMatcher.addURI(AUTHORITY, "integer/*", ValueType.INTEGER.id())
            sUriMatcher.addURI(AUTHORITY, "long/*", ValueType.LONG.id())
            sUriMatcher.addURI(AUTHORITY, "string/*", ValueType.STRING.id())
            sUriMatcher.addURI(AUTHORITY, "stringset/*", ValueType.STRINGSET.id())
            sUriMatcher.addURI(AUTHORITY, "float/*", ValueType.FLOAT.id())
        }

        fun buildUri(key: String, type: ValueType) = Uri.parse("content://$AUTHORITY/${type.typeName()}/$key")

    }

    private lateinit var mStoreHelper: StoreHelper

    private fun matchTable(uri: Uri): String? {
        val matchIdx = sUriMatcher.match(uri)
        return ValueType.values().find { it.id() == matchIdx }?.table()
    }

    override fun onCreate(): Boolean {

        mStoreHelper = StoreHelper(context, dbName)

        return true
    }

    override fun insert(uri: Uri, values: ContentValues): Uri? {
        val db = mStoreHelper.writableDatabase
        val table = matchTable(uri) ?: return null
        db.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        return uri
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val db = mStoreHelper.readableDatabase
        val table = matchTable(uri) ?: return null
        return db.query(table, projection, selection, selectionArgs, null, null, null, null)
    }

    override fun update(uri: Uri, values: ContentValues, selection: String?, selectionArgs: Array<out String>?): Int {
        val db = mStoreHelper.writableDatabase
        val table = matchTable(uri) ?: return -1
        return db.update(table, values, selection, selectionArgs)
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val db = mStoreHelper.writableDatabase
        val table = matchTable(uri) ?: return -1
        return db.delete(table, selection, selectionArgs)
    }

    override fun getType(uri: Uri?): String? = null
}