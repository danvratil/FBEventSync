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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;

public class SettingsActivity extends PreferenceActivity {

    private boolean mShouldForceSync = false;

    private static String CONFIGURE_REMINDERS_ACTION = "cz.dvratil.fbeventsync.Settings.CONFIGURE_REMINDERS";
    private static String CONFIGURE_SYNC_ACTION = "cz.dvratil.fbeventsync.Settings.CONFIGURE_SYNC";
    private static String CONFIGURE_MISC_ACTION = "cz.dvratil.fbeventsync.Settings.CONFIGURE_MISC";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceFragment fragment = null;
        String action = getIntent().getAction();
        if (action == CONFIGURE_REMINDERS_ACTION) {
            fragment = new ReminderPreferenceFragment();
        } else if (action == CONFIGURE_SYNC_ACTION) {
            fragment = new SyncPreferenceFragment();
        } else if (action == CONFIGURE_MISC_ACTION) {
            fragment = new MiscPreferenceFragment();
        }
        getFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(
                new SharedPreferences.OnSharedPreferenceChangeListener() {
                    public void onSharedPreferenceChanged(
                            SharedPreferences prefs, String key) {
                        if (key == "pref_attending_reminders"
                            || key == "pref_maybe_reminders"
                            || key == "pref_not_responded_reminders"
                            || key == "pref_declined_reminders"
                            || key == "pref_sync_frequency") {
                            mShouldForceSync = true;
                        }
                    }
                });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mShouldForceSync) {
            CalendarSyncAdapter.updateSync(this);
        }
    }

    public static class ReminderPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.reminder_prefrences);
        }
    }

    public static class SyncPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.sync_preferences);
        }
    }

    public static class MiscPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.misc_preferences);
        }
    }
}
