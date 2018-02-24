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
import android.util.Log

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Logger private constructor() {

    private var mContext: Context? = null
    private var mLogFile: File? = null
    private var mLogWriter: FileWriter? = null

    private var mDateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val mMinLogCatLvl = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.NO_LOG

    private enum class LogLevel constructor(private var `val`: Int, private var lvlChar: String) {
        DEBUG(Log.DEBUG, "D"),
        INFO(Log.INFO, "I"),
        WARNING(Log.WARN, "W"),
        ERROR(Log.ERROR, "E"),

        NO_LOG(1000, "N");

        fun toInt(): Int  = `val`
        override fun toString() = lvlChar
    }

    fun clearLogs() {
        synchronized(this) {
            val writer = mLogWriter ?: return
            try {
                writer.close()

                val outChan = FileOutputStream(mLogFile, true).channel
                outChan.truncate(0)
                outChan.close()

                mLogWriter = FileWriter(mLogFile, true)
            } catch (e: IOException) {
                Log.e(TAG, "clearLogs: $e")
            }
        }
    }

    private fun truncateLogs() {
        synchronized(this) {
            val writer = mLogWriter ?: return
            val context = mContext ?: return
            var logFile = mLogFile ?: return

            try {
                writer.close()

                val tmpFile = File(context.filesDir, LOG_FILE + ".old")
                if (tmpFile.exists()) {
                    tmpFile.delete()
                }
                logFile.renameTo(tmpFile)
                logFile = File(context.filesDir, LOG_FILE)
                val inChan = FileInputStream(tmpFile).channel
                val outChan = FileOutputStream(logFile, false).channel
                inChan.transferTo(Math.max(0, tmpFile.length() - TRIM_LOG_SIZE), TRIM_LOG_SIZE.toLong(), outChan)
                inChan.close()
                tmpFile.delete()
                outChan.close()

                mLogFile = logFile
                mLogWriter = FileWriter(logFile, true)
            } catch (e: IOException) {
                Log.e(TAG, "truncateLogs: $e")
            }
        }
    }

    private fun init(context: Context) {
        synchronized(this) {
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

            val logFile = File(context.filesDir, LOG_FILE)
            try {
                mLogWriter = FileWriter(logFile, true)
                mLogFile = logFile
            } catch (e: IOException) {
                Log.e(TAG, "Failed to open log: $e")
                mLogWriter = null
                mLogFile = null
            }

            mContext = context
        }
    }

    fun debug(tag: String, msg: String) {
        doLog(LogLevel.DEBUG, tag, msg)
    }

    fun info(tag: String, msg: String) {
        doLog(LogLevel.INFO, tag, msg)
    }

    fun warning(tag: String, msg: String) {
        doLog(LogLevel.WARNING, tag, msg)
    }

    fun error(tag: String, msg: String) {
        doLog(LogLevel.ERROR, tag, msg)
    }

    private fun doLog(level: LogLevel, tag: String, msg: String) {
        val logMsg = "${mDateFormat.format(Date())} $level/$tag: $msg\n"
        synchronized(this) {
            val writer = mLogWriter ?: return
            val logFile = mLogFile ?: return
            try {
                writer.write(logMsg)
                writer.flush()
            } catch (e: IOException) {
                Log.e(TAG, "doLog: $e")
            }

            if (logFile.length() >= MAX_LOG_SIZE) {
                truncateLogs()
            }

            if (level.toInt() >= mMinLogCatLvl.toInt()) {
                Log.println(level.toInt(), tag, msg)
            }
        }
    }

    companion object {

        private const val TAG = "LOG"

        const val LOG_FILE = "logs/sync.log"

        private const val MAX_LOG_SIZE = 256 * 1024 // 256KB
        private const val TRIM_LOG_SIZE = 128 * 1024 // 128KB

        private var sInstance: WeakReference<Logger>? = null

        @Synchronized
        fun getInstance(context: Context): Logger {
            var logger: Logger? = sInstance?.get()
            if (logger == null) {
                logger = Logger()
                sInstance = WeakReference(logger)
                logger.init(context)
            }

            return logger
        }
    }
}
