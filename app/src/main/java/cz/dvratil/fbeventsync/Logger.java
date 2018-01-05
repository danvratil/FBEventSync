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

package cz.dvratil.fbeventsync;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Logger {

    private static String TAG = "LOG";

    public static String LOG_FILE = "logs/sync.log";

    private static int MAX_LOG_SIZE  = 512 * 1024; // 512KB
    private static int TRIM_LOG_SIZE = 256 * 1024; // 256KB

    private static WeakReference<Logger> sInstance = null;

    private Context mContext = null;
    private File mLogFile = null;
    private FileWriter mLogWriter = null;

    private SimpleDateFormat mDateFormat = null;

    private enum LogLevel {
        DEBUG(Log.DEBUG, "D"),
        INFO(Log.INFO, "I"),
        WARNING(Log.WARN, "W"),
        ERROR(Log.ERROR, "E"),

        NO_LOG(1000, "N");

        private int val = -1;
        private String lvlChar = null;

        LogLevel(int val, String lvlChar) {
            this.val = val;
            this.lvlChar = lvlChar;
        }

        public int toInt() {
            return val;
        }

        public String toString() {
            return lvlChar;
        }
    }
    private LogLevel mMinLogCatLvl = BuildConfig.DEBUG ? LogLevel.DEBUG : LogLevel.NO_LOG;

    protected Logger() {
        mDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    }

    public synchronized static Logger getInstance(Context context) {
        Logger l = (sInstance == null ? null : sInstance.get());
        if (l == null) {
            sInstance = new WeakReference<>(l = new Logger());
            l.init(context);
        }

        return l;
    }

    public void clearLogs() {
        if (mLogWriter == null) {
            return;
        }

        synchronized (mLogWriter) {
            try {
                mLogWriter.close();

                FileChannel outChan = new FileOutputStream(mLogFile, true).getChannel();
                outChan.truncate(0);
                outChan.close();

                mLogWriter = new FileWriter(mLogFile, true);
            } catch (IOException e) {
                Log.e(TAG,"IOException when truncating log file: " + e.getMessage());
            }
        }
    }

    private void truncateLogs() {
        if (mLogWriter == null) {
            return;
        }

        synchronized (mLogWriter) {
            try {
                mLogWriter.close();

                File tmpFile = new File(mContext.getFilesDir(), LOG_FILE + ".old");
                if (tmpFile.exists()) {
                    tmpFile.delete();
                }
                mLogFile.renameTo(tmpFile);
                mLogFile = new File(mContext.getFilesDir(), LOG_FILE);
                FileChannel inChan = new FileInputStream(tmpFile).getChannel();
                FileChannel outChan = new FileOutputStream(mLogFile, false).getChannel();
                inChan.transferTo(Math.max(0, tmpFile.length() - TRIM_LOG_SIZE), TRIM_LOG_SIZE, outChan);
                inChan.close();
                tmpFile.delete();
                outChan.close();

                mLogWriter = new FileWriter(mLogFile, true);
            } catch (IOException e) {
                Log.e(TAG,"IOException when shrinking log file:" + e.getMessage());
            }
        }
    }

    private void init(Context context) {
        if (mContext != null) {
            return;
        }

        File logDir = new File(context.getFilesDir(), "logs");
        if (!logDir.exists()) {
            if (!logDir.mkdir()) {
                Log.e(TAG,"Failed to create logs directory");
                return;
            }
        }
        mLogFile = new File(context.getFilesDir(), LOG_FILE);
        try {
            mLogWriter = new FileWriter(mLogFile, true);
        }  catch (IOException e) {
            Log.e(TAG, "Failed to open log: " + e.getMessage());
            mLogWriter = null;
            mLogFile = null;
        }
        mContext = context;
    }

    public void debug(String tag, String msg, Object ... args) {
        doLog(LogLevel.DEBUG, tag, msg, args);
    }
    public void info(String tag, String msg, Object ... args) {
        doLog(LogLevel.INFO, tag, msg, args);
    }
    public void warning(String tag, String msg, Object ... args) {
        doLog(LogLevel.WARNING, tag, msg, args);
    }
    public void error(String tag, String msg, Object ... args) {
        doLog(LogLevel.ERROR, tag, msg, args);
    }

    private void doLog(LogLevel level, String tag, String msg, Object ... args) {
        String formattedMsg = String.format(msg, args);
        String logMsg = String.format("%s %s/%s: %s\n", mDateFormat.format(new Date()), level.toString(),
                                      tag, formattedMsg);
        if (mLogWriter != null) {
            synchronized (mLogWriter) {
                try {
                    mLogWriter.write(logMsg);
                    mLogWriter.flush();
                } catch (IOException e) {
                    Log.e(TAG, "Log IOException: " + e.getMessage());
                }
            }

            if (mLogFile.length() >= MAX_LOG_SIZE) {
                truncateLogs();
            }
        }

        if (level.toInt() >= mMinLogCatLvl.toInt()) {
            Log.println(level.toInt(), tag, formattedMsg);
        }
    }
}
