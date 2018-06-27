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

package cz.dvratil.fbeventsync;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import android.support.test.InstrumentationRegistry;
import org.junit.Assert;


import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import cz.dvratil.fbeventsync.preferences.Preferences;
import cz.dvratil.fbeventsync.preferences.PreferencesProvider;


@RunWith(AndroidJUnit4.class)
@SmallTest
public class PreferencesProviderAndroidUnitTest {

    private static final String INT_KEY = "int_key";
    private static final String LONG_KEY = "long_key";
    private static final String BOOL_KEY = "bool_key";
    private static final String STRING_KEY = "string_key";
    private static final String STRINGSET_KEY = "stringset_key";
    private static final String FLOAT_KEY = "float_key";

    private static final long DEFAULT_LONG = Long.MAX_VALUE - 1;
    private static final long TEST_LONG = Long.MAX_VALUE - 42;

    private static final String DEFAULT_STRING = "Nothing here, move on!";
    private static final String TEST_STRING = "Hello World!";

    private static final Set<String> DEFAULT_SET = new HashSet<>(Arrays.asList("String 1", "String 2"));
    private static final Set<String> TEST_SET = new HashSet<>(Arrays.asList("String,with,commas", "Two,,commas", "Comma,", "Next"));

    @BeforeClass
    public static void setUp() {
        PreferencesProvider.Companion.setDbName("preferencesprovider_test");
    }

    @Test
    public void preferencesProvider_intTest() {
        Preferences pref = new Preferences(InstrumentationRegistry.getContext());

        // Default value
        Assert.assertEquals(100, pref.getInt(INT_KEY, 100));

        // Write value
        pref.putInt(INT_KEY, 42);
        Assert.assertEquals(42, pref.getInt(INT_KEY, 100));

        // Reset value
        pref.removeInt(INT_KEY);
        Assert.assertEquals(100, pref.getInt(INT_KEY, 100));
    }

    @Test
    public void preferencesProvider_longTest() {
        Preferences pref = new Preferences(InstrumentationRegistry.getContext());

        // Default value
        Assert.assertEquals(DEFAULT_LONG, pref.getLong(LONG_KEY, DEFAULT_LONG));

        // Write value
        pref.putLong(LONG_KEY, TEST_LONG);
        Assert.assertEquals(TEST_LONG, pref.getLong(LONG_KEY, DEFAULT_LONG));

        // Reset value
        pref.removeLong(LONG_KEY);
        Assert.assertEquals(DEFAULT_LONG, pref.getLong(LONG_KEY, DEFAULT_LONG));
    }

    @Test
    public void preferencesProvider_booleanTest() {
        Preferences pref = new Preferences(InstrumentationRegistry.getContext());

        // Default value
        Assert.assertEquals(true, pref.getBoolean(BOOL_KEY, true));

        // Write value
        pref.putBoolean(BOOL_KEY, false);
        Assert.assertEquals(false, pref.getBoolean(BOOL_KEY, true));

        // Reset value
        pref.removeBoolean(BOOL_KEY);
        Assert.assertEquals(true, pref.getBoolean(BOOL_KEY, true));
    }

    @Test
    public void preferencesProvider_stringTest() {
        Preferences pref = new Preferences(InstrumentationRegistry.getContext());

        // Default value
        Assert.assertEquals(DEFAULT_STRING, pref.getString(STRING_KEY, DEFAULT_STRING));

        // Write value
        pref.putString(STRING_KEY, TEST_STRING);
        Assert.assertEquals(TEST_STRING, pref.getString(STRING_KEY, DEFAULT_STRING));

        // Reset value
        pref.removeString(STRING_KEY);
        Assert.assertEquals(DEFAULT_STRING, pref.getString(STRING_KEY, DEFAULT_STRING));
    }

    @Test
    public void preferencesProvider_stringSetTest() {
        Preferences pref = new Preferences(InstrumentationRegistry.getContext());

        // Default value
        Assert.assertEquals(DEFAULT_SET, pref.getStringSet(STRINGSET_KEY, DEFAULT_SET));

        // Write value
        pref.putStringSet(STRINGSET_KEY, TEST_SET);
        Assert.assertEquals(TEST_SET, pref.getStringSet(STRINGSET_KEY, DEFAULT_SET));

        // Reset value
        pref.removeStringSet(STRINGSET_KEY);
        Assert.assertEquals(DEFAULT_SET, pref.getStringSet(STRINGSET_KEY, DEFAULT_SET));
    }

    @Test
    public void preferencesProvider_floatTest() {
        Preferences pref = new Preferences(InstrumentationRegistry.getContext());

        // Default value
        Assert.assertEquals(3.14f, pref.getFloat(FLOAT_KEY, 3.14f), 0);

        // Write value
        pref.putFloat(FLOAT_KEY, 42.13567f);
        Assert.assertEquals(42.13567f, pref.getFloat(FLOAT_KEY, 3.14f), 0);

        // Reset value
        pref.removeFloat(FLOAT_KEY);
        Assert.assertEquals(3.14f, pref.getFloat(FLOAT_KEY, 3.14f), 0);
    }
}
