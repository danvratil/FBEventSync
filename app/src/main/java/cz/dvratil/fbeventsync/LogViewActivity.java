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

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.FileProvider;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;

public class LogViewActivity extends AppCompatActivity {

    TextView mTextView = null;
    ScrollView mScrollView = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_view);
        Toolbar toolbar = (Toolbar) findViewById(R.id.logview_toolbar);
        setSupportActionBar(toolbar);

        ActionBar ab = getSupportActionBar();
        ab.setDisplayHomeAsUpEnabled(true);

        mTextView = (TextView) findViewById(R.id.log_text_view);
        mScrollView = (ScrollView) findViewById(R.id.log_scroll_view);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_logview, menu);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        loadLogFile();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_clear_log:
                Logger.getInstance(this).clearLogs();
                loadLogFile();
                return true;

            case R.id.action_send_to_develop:
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("message/rfc822");
                intent.putExtra(Intent.EXTRA_EMAIL, new String[]{ "me@dvratil.cz" });
                intent.putExtra(Intent.EXTRA_SUBJECT, "FBEventSync logs");

                intent.putExtra(
                        Intent.EXTRA_TEXT,
                        String.format(
                                Locale.US,
                                "App ID: %s\n" +
                                        "App version: %d (%s)\n" +
                                        "App build: %s\n" +
                                        "OS: %s (API %d)\n",
                                BuildConfig.APPLICATION_ID,
                                BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME,
                                BuildConfig.BUILD_TYPE,
                                Build.VERSION.RELEASE, Build.VERSION.SDK_INT));

                File logFile = new File(getFilesDir(), Logger.LOG_FILE);
                if (!logFile.exists() || !logFile.canRead()) {
                    Toast.makeText(this, R.string.toast_error_sending_log, Toast.LENGTH_SHORT).show();
                    return false;
                }

                Uri contentUri = FileProvider.getUriForFile(
                        this, "cz.dvratil.fbeventsync.FileProvider", logFile);
                intent.putExtra(Intent.EXTRA_STREAM, contentUri);
                startActivity(Intent.createChooser(intent, getResources().getString(R.string.intent_send_email)));
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void loadLogFile() {
        File file = new File(getFilesDir(), Logger.LOG_FILE);
        StringBuilder builder = new StringBuilder();
        try {
            BufferedReader buffer = new BufferedReader(new FileReader(file));
            try {
                String line;
                while ((line = buffer.readLine()) != null) {
                    builder.append(line);
                    builder.append('\n');
                }
            } catch (Exception e) {
                Log.e("LOGVIEW", "Exception when reading log: " + e.getMessage());
            }
        } catch (Exception e) {
            Log.e("LOGVIEW", "Exception when opening log: " + e.getMessage());
        }

        mTextView.setText(builder.toString());
        mScrollView.post(new Runnable() {
            public void run()
            {
                mScrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }
}
