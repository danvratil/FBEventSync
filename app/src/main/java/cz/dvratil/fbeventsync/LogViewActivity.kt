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

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.v4.content.FileProvider
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

import java.io.BufferedReader
import java.io.File
import java.io.FileReader

class LogViewActivity : AppCompatActivity() {

    private lateinit var mTextView: TextView
    private lateinit var mScrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_view)
        setSupportActionBar(findViewById<View>(R.id.logview_toolbar) as Toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mTextView = findViewById<View>(R.id.log_text_view) as TextView
        mScrollView = findViewById<View>(R.id.log_scroll_view) as ScrollView
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_logview, menu)
        return true
    }

    override fun onResume() {
        super.onResume()

        loadLogFile()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_clear_log -> {
                Logger.getInstance(this).clearLogs()
                loadLogFile()
                return true
            }

            R.id.action_send_to_develop -> {
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "message/rfc822"
                intent.putExtra(Intent.EXTRA_EMAIL, arrayOf("me@dvratil.cz"))
                intent.putExtra(Intent.EXTRA_SUBJECT, "FBEventSync logs")

                intent.putExtra(
                        Intent.EXTRA_TEXT,
                        "${getString(R.string.log_email_template)}\n\n\n" +
                                "App ID: ${BuildConfig.APPLICATION_ID}\n" +
                                "App version: ${BuildConfig.VERSION_CODE} (${BuildConfig.VERSION_NAME})\n" +
                                "App build: ${BuildConfig.BUILD_TYPE}\n" +
                                "OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")

                val logFile = File(filesDir, Logger.LOG_FILE)
                if (!logFile.exists() || !logFile.canRead()) {
                    Toast.makeText(this, R.string.log_error_sending_log_toast, Toast.LENGTH_SHORT).show()
                    return false
                }

                val contentUri = FileProvider.getUriForFile(
                        this, getString(R.string.fileprovider_authority), logFile)
                intent.putExtra(Intent.EXTRA_STREAM, contentUri)
                startActivity(Intent.createChooser(intent, resources.getString(R.string.log_send_email_action)))
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun loadLogFile() {
        val file = File(filesDir, Logger.LOG_FILE)
        val builder = StringBuilder()
        try {
            val buffer = BufferedReader(FileReader(file))
            try {
                var line = buffer.readLine()
                while (line != null) {
                    builder.append(line)
                    builder.append('\n')
                    line = buffer.readLine()
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadLogFile read: $e")
            }

        } catch (e: Exception) {
            Log.e(TAG, "loadLogFile open: $e")
        }

        mTextView.text = builder.toString()
        mScrollView.post { mScrollView.fullScroll(View.FOCUS_DOWN) }
    }

    companion object {
        private const val TAG = "LOGVIEW"
    }
}
