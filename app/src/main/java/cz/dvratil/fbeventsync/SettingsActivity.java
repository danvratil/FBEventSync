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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import com.kizitonwose.colorpreference.ColorPreference;
import com.larswerkman.lobsterpicker.LobsterPicker;
import com.larswerkman.lobsterpicker.sliders.LobsterShadeSlider;

public class SettingsActivity extends PreferenceActivity {

    private boolean mShouldForceSync = false;

    private static String CONFIGURE_CALENDARS = "cz.dvratil.fbeventsync.Settings.CONFIGURE_CALENDARS";
    private static String CONFIGURE_SYNC_ACTION = "cz.dvratil.fbeventsync.Settings.CONFIGURE_SYNC";
    private static String CONFIGURE_MISC_ACTION = "cz.dvratil.fbeventsync.Settings.CONFIGURE_MISC";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceFragment fragment = null;
        String action = getIntent().getAction();
        if (action == CONFIGURE_CALENDARS) {
            fragment = new CalendarPreferenceFragment();
        } else if (action == CONFIGURE_SYNC_ACTION) {
            fragment = new SyncPreferenceFragment();
        } else if (action == CONFIGURE_MISC_ACTION) {
            fragment = new MiscPreferenceFragment();
        }

        // API 21
        //String preferencesName = PreferenceManager.getDefaultSharedPreferencesName(this);
        SharedPreferences prefs = getSharedPreferences(getString(R.string.cz_dvratil_fbeventsync_preferences), Context.MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(
                new SharedPreferences.OnSharedPreferenceChangeListener() {
                    public void onSharedPreferenceChanged(
                            SharedPreferences prefs, String key) {
                        mShouldForceSync = true;
                    }
                });

        getFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mShouldForceSync) {
            CalendarSyncAdapter.updateSync(this);
        }
    }

    public static class CalendarPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.calendar_preferences);

            installColorDialogHandler("pref_attending_color");
            installColorDialogHandler("pref_maybe_color");
            installColorDialogHandler("pref_declined_color");
            installColorDialogHandler("pref_not_responded_color");
            installColorDialogHandler("pref_birthday_color");
        }

        private void installColorDialogHandler(final String key) {
            findPreference(key).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    showColorDialog(key, preference);
                    return true;
                }
            });
        }

        private void showColorDialog(String key, final Preference preference) {
            LayoutInflater inflater = getActivity().getLayoutInflater();
            View colorView = inflater.inflate(R.layout.color_dialog, null);

            int color = PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .getInt(key, Integer.parseInt(getString(R.string.pref_color_default)));
            final LobsterPicker lobsterPicker = (LobsterPicker) colorView.findViewById(R.id.colordialog_lobsterpicker);
            LobsterShadeSlider shadeSlider = (LobsterShadeSlider) colorView.findViewById(R.id.colordialog_shadeslider);

            lobsterPicker.addDecorator(shadeSlider);
            lobsterPicker.setColorHistoryEnabled(true);
            lobsterPicker.setHistory(color);
            lobsterPicker.setColor(color);

            new AlertDialog.Builder(getActivity())
                    .setView(colorView)
                    .setTitle(getString(R.string.choose_color))
                    .setPositiveButton("SAVE", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            ((ColorPreference) preference).setValue(lobsterPicker.getColor());
                        }
                    })
                    .setNegativeButton("CLOSE", null)
                    .show();
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
