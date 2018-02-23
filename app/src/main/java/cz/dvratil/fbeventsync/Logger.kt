/*
    Copyright (C) 2017  Daniel Vrátil <me@dvratil.cz>

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
import android.util.Log

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Logger protected constructor() {

    private var mContext: Context? = null
    private var mLogFile: File? = null
    private var mLogWriter: FileWriter? = null

    private var mDateFormat: SimpleDateFormat? = null
    private val mMinLogCatLvl = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.NO_LOG

    private enum class LogLevel private constructor(`val`: Int, lvlChar: String) {
        DEBUG(Log.DEBUG, "D"),
        INFO(Log.INFO, "I"),
        WARNING(Log.WARN, "W"),
        ERROR(Log.ERROR, "E"),

        NO_LOG(1000, "N");

        private val `val` = -1
        private val lvlChar: String? = null

        init {
            this.`val` = `val`
            this.lvlChar = lvlChar
        }

        fun toInt(): Int {
            return `val`
        }

        override fun toString(): String? {
            return lvlChar
        }
    }

    init {
        mDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    fun clearLogs() {
        if (mLogWriter == null) {
            return
        }

        synchronized(mLogWriter) {
            try {
                mLogWriter!!.close()

                val outChan = FileOutputStream(mLogFile!!, true).channel
                outChan.truncate(0)
                outChan.close()

                mLogWriter = FileWriter(mLogFile!!, true)
            } catch (e: IOException) {
                Log.e(TAG, "IOException when truncating log file: " + e.message)
            }

        }
    }

    private fun truncateLogs() {
        if (mLogWriter == null) {
            return
        }

        synchronized(mLogWriter) {
            try {
                mLogWriter!!.close()

                val tmpFile = File(mContext!!.filesDir, LOG_FILE + ".old")
                if (tmpFile.exists()) {
                    tmpFile.delete()
                }
                mLogFile!!.renameTo(tmpFile)
                mLogFile = File(mContext!!.filesDir, LOG_FILE)
                val inChan = FileInputStream(tmpFile).channel
                val outChan = FileOutputStream(mLogFile!!, false).channel
                inChan.transferTo(Math.max(0, tmpFile.length() - TRIM_LOG_SIZE), TRIM_LOG_SIZE.toLong(), outChan)
                inChan.close()
                tmpFile.delete()
                outChan.close()

                mLogWriter = FileWriter(mLogFile!!, true)
            } catch (e: IOException) {
                Log.e(TAG, "IOException when shrinking log file:" + e.message)
            }

        }
    }

    private fun init(context: Context) {
        if (mContext != null) {
            return
        }

        val logDir = File(context.filesDir, "logs")
        if (!logDir.exists()) {
            if (!logDir.mkdir()) {
                Log.e(TAG, "Failed to create logs directory")
                return
            }
        }
        mLogFile = File(context.filesDir, LOG_FILE)
        try {
            mLogWriter = FileWriter(mLogFile!!, true)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to open log: " + e.message)
            mLogWriter = null
            mLogFile = null
        }

        mContext = context
    }

    fun debug(tag: String, msg: String, vararg args: Any) {
        doLog(LogLevel.DEBUG, tag, msg, *args)
    }

    fun info(tag: String, msg: String, vararg args: Any) {
        doLog(LogLevel.INFO, tag, msg, *args)
    }

    fun warning(tag: String, msg: String, vararg args: Any) {
        doLog(LogLevel.WARNING, tag, msg, *args)
    }

    fun error(tag: String, msg: String, vararg args: Any) {
        doLog(LogLevel.ERROR, tag, msg, *args)
    }

    private fun doLog(level: LogLevel, tag: String, msg: String, vararg args: Any) {
        val formattedMsg = String.format(msg, *args)
        val logMsg = String.format("%s %s/%s: %s\n", mDateFormat!!.format(Date()), level.toString(),
                tag, formattedMsg)
        if (mLogWriter != null) {
            synchronized(mLogWriter) {
                try {
                    mLogWriter!!.write(logMsg)
                    mLogWriter!!.flush()
                } catch (e: IOException) {
                    Log.e(TAG, "Log IOException: " + e.message)
                }

            }

            if (mLogFile!!.length() >= MAX_LOG_SIZE) {
                truncateLogs()
            }
        }

        if (level.toInt() >= mMinLogCatLvl.toInt()) {
            Log.println(level.toInt(), tag, formattedMsg)
        }
    }

    companion object {

        private val TAG = "LOG"

        var LOG_FILE = "logs/sync.log"

        private val MAX_LOG_SIZE = 256 * 1024 // 256KB
        private val TRIM_LOG_SIZE = 128 * 1024 // 128KB

        private var sInstance: WeakReference<Logger>? = null

        @Synchronized
        fun getInstance(context: Context): Logger {
            var l: Logger? = if (sInstance == null) null else sInstance!!.get()
            if (l == null) {
                sInstance = WeakReference<Logger>(l = Logger())
                l!!.init(context)
            }

            return l
        }
    }
}
