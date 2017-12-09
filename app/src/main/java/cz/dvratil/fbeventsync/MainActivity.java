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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Intent;
import android.os.Build;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView view = findViewById(R.id.versionLabel);
        view.setText(String.format(getString(R.string.version_label), BuildConfig.VERSION_NAME));

        checkAccounts();
    }

    public void onAddAccountClicked(View view) {
        Intent intent = new Intent(Settings.ACTION_ADD_ACCOUNT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            intent.putExtra(Settings.EXTRA_ACCOUNT_TYPES, new String[]{getString(R.string.account_type)});
        }
        startActivity(intent);
    }

    public void onConfigureCalendarsClicked(View view) {
        openSettings(SettingsActivity.CONFIGURE_CALENDARS);
    }

    public void onConfigureSyncClicked(View view) {
        openSettings(SettingsActivity.CONFIGURE_SYNC_ACTION);
    }

    public void onConfigureMiscClicked(View view) {
        openSettings(SettingsActivity.CONFIGURE_MISC_ACTION);
    }

    private void openSettings(String action) {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.setAction(action);
        startActivity(intent);
    }

    public void onShowLogsClicked(View view) {
        Intent intent = new Intent(this, LogViewActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkAccounts();
    }

    private void checkAccounts() {
        Account accounts[] = AccountManager.get(this).getAccountsByType(getString(R.string.account_type));
        if (accounts.length == 0) {
            findViewById(R.id.calendar_prefs_btn).setVisibility(View.GONE);
            findViewById(R.id.sync_prefs_btn).setVisibility(View.GONE);
            findViewById(R.id.misc_prefs_btn).setVisibility(View.GONE);
            findViewById(R.id.add_account_btn).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.calendar_prefs_btn).setVisibility(View.VISIBLE);
            findViewById(R.id.sync_prefs_btn).setVisibility(View.VISIBLE);
            findViewById(R.id.misc_prefs_btn).setVisibility(View.VISIBLE);
            findViewById(R.id.add_account_btn).setVisibility(View.GONE);
        }
    }

}
