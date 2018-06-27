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
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import cz.dvratil.fbeventsync.Logger
import java.sql.SQLException

class StoreHelper(context: Context, dbName: String)
    : SQLiteOpenHelper(context, dbName, null, STORE_VERSION) {


    companion object {
        private const val TAG = "PREFS"
        private const val STORE_VERSION = 1

        const val INT_TABLE = "prefs_int"
        const val STRING_TABLE = "prefs_string"
        const val FLOAT_TABLE = "prefs_float"
    }

    private val mLogger = Logger.getInstance(context)


    override fun onCreate(db: SQLiteDatabase) {

        try {
            db.execSQL(
                    "CREATE TABLE $STRING_TABLE (" +
                            "key TEXT NOT NULL UNIQUE PRIMARY KEY, " +
                            "value TEXT)"
            )

            db.execSQL(
                    "CREATE TABLE $INT_TABLE (" +
                            "key TEXT NOT NULL UNIQUE PRIMARY KEY, " +
                            "value INTEGER)"
            )

            db.execSQL(
                    "CREATE TABLE $FLOAT_TABLE (" +
                            "key TEXT NOT NULL UNIQUE PRIMARY KEY, " +
                            "value REAL)"
            )
        } catch (e: SQLException) {
            mLogger.error(TAG, "SQLException when creating new schema: $e")
        }

    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

        // Nothing to do yet

    }

}