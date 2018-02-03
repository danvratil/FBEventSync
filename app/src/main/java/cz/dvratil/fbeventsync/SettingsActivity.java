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

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import com.kizitonwose.colorpreference.ColorPreference;
import com.larswerkman.lobsterpicker.LobsterPicker;
import com.larswerkman.lobsterpicker.sliders.LobsterShadeSlider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SettingsActivity extends PreferenceActivity {

    private boolean mShouldForceSync = false;
    private boolean mShouldRescheduleSync = false;

    public static String CONFIGURE_CALENDARS = "cz.dvratil.fbeventsync.Settings.CONFIGURE_CALENDARS";
    public static String CONFIGURE_SYNC_ACTION = "cz.dvratil.fbeventsync.Settings.CONFIGURE_SYNC";
    public static String CONFIGURE_MISC_ACTION = "cz.dvratil.fbeventsync.Settings.CONFIGURE_MISC";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        PreferencesMigrator.migrate(this);

        PreferenceFragment fragment = null;
        String action = getIntent().getAction();
        int fragmentTitleId = 0;
        if (action == CONFIGURE_CALENDARS) {
            fragment = new CalendarPreferenceFragment();
            fragmentTitleId = R.string.pref_calendar_settings_title;
        } else if (action == CONFIGURE_SYNC_ACTION) {
            fragment = new SyncPreferenceFragment();
            fragmentTitleId = R.string.pref_sync_settings_title;
        } else if (action == CONFIGURE_MISC_ACTION) {
            fragment = new MiscPreferenceFragment();
            fragmentTitleId = R.string.pref_misc_settings_title;
        }

        // API 21
        //String preferencesName = PreferenceManager.getDefaultSharedPreferencesName(this);
        SharedPreferences prefs = getSharedPreferences(getString(R.string.cz_dvratil_fbeventsync_preferences), Context.MODE_MULTI_PROCESS);
        prefs.registerOnSharedPreferenceChangeListener(
                new SharedPreferences.OnSharedPreferenceChangeListener() {
                    public void onSharedPreferenceChanged(
                            SharedPreferences prefs, String key) {
                        if (key.equals(getString(R.string.pref_sync_frequency))) {
                            mShouldRescheduleSync = true;
                        }
                        mShouldForceSync = true;
                    }
                });

        getFragmentManager().beginTransaction().replace(R.id.settings_content, fragment).commit();
        Toolbar toolbar = findViewById(R.id.settings_toolbar);
        toolbar.setTitle(fragmentTitleId);
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        Toolbar toolbar = findViewById(R.id.settings_toolbar);
        toolbar.setNavigationOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        finish();
                    }
                }
        );
    }

    private void maybeSync() {
        if (mShouldRescheduleSync) {
            CalendarSyncAdapter.updateSync(this);
            mShouldRescheduleSync = false;
        }
        if (mShouldForceSync) {
            CalendarSyncAdapter.requestSync(this);
            mShouldForceSync = false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        maybeSync();
    }

    @Override
    protected void onStop() {
        super.onStop();
        maybeSync();
    }

    public static class CalendarPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.calendar_preferences);

            installColorDialogHandler(R.string.pref_calendar_attending_color);
            installColorDialogHandler(R.string.pref_calendar_tentative_color);
            installColorDialogHandler(R.string.pref_calendar_declined_color);
            installColorDialogHandler(R.string.pref_calendar_not_responded_color);
            installColorDialogHandler(R.string.pref_calendar_birthday_color);
        }

        private void installColorDialogHandler(int keyId) {
            final String key = getString(keyId);
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
            @SuppressLint("InflateParams")
            View colorView = inflater.inflate(R.layout.color_dialog, null);

            int color = PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .getInt(key, getResources().getColor(R.color.colorFBBlue));
            final LobsterPicker lobsterPicker = colorView.findViewById(R.id.colordialog_lobsterpicker);
            LobsterShadeSlider shadeSlider = colorView.findViewById(R.id.colordialog_shadeslider);

            lobsterPicker.addDecorator(shadeSlider);
            lobsterPicker.setColorHistoryEnabled(true);
            lobsterPicker.setHistory(color);
            lobsterPicker.setColor(color);

            new AlertDialog.Builder(getActivity())
                    .setView(colorView)
                    .setTitle(getString(R.string.color_dlg_title))
                    .setPositiveButton(getString(R.string.color_dlg_save_btn_title), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            ((ColorPreference) preference).setValue(lobsterPicker.getColor());
                        }
                    })
                    .setNegativeButton(getString(R.string.color_dlg_close_btn_title), null)
                    .show();
        }


    }

    public static class SyncPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.sync_preferences);
            ListPreference pref = (ListPreference) findPreference(getString(R.string.pref_language));
            XmlResourceParser parser = getResources().getXml(R.xml.fb_languages);
            List<CharSequence> entryValues = new ArrayList<>();
            entryValues.add(getString(R.string.pref_language_default_value));
            List<CharSequence> entries = new ArrayList<>();
            entries.add(getString(R.string.pref_language_default_entry));
            try {
                int ev = parser.getEventType();
                while (ev != XmlResourceParser.END_DOCUMENT) {
                    if (ev == XmlResourceParser.START_TAG && parser.getName().equals("language")) {
                        String code = parser.getAttributeValue(null,"code");
                        String lang = code.substring(0, 2);
                        Locale locale = new Locale(lang, code.substring(3, 5));
                        String name;
                        if (locale.getDisplayLanguage().equals(lang)) {
                            name = String.format(Locale.getDefault(), "%s (%s)",
                                    parser.getAttributeValue(null, "name"),
                                    locale.getDisplayCountry());
                        } else {
                            name = locale.getDisplayName();
                        }
                        entries.add(name);
                        entryValues.add(code);
                    }
                    ev = parser.next();
                }
            } catch (org.xmlpull.v1.XmlPullParserException e) {
                e.printStackTrace();
                Log.e("PREFS","Language XML parsing exception: " + e.getMessage());
            } catch (java.io.IOException e) {
                e.printStackTrace();
                Log.e("PREFS", "Language XML IO exception:" + e.getMessage());
            }
            pref.setEntries(entries.toArray(new CharSequence[0]));
            pref.setEntryValues(entryValues.toArray(new CharSequence[0]));
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
