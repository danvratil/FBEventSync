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

import android.support.annotation.Nullable;
import android.support.v7.preference.PreferenceDataStore;

import java.util.HashMap;
import java.util.Set;

public class PreferenceMemoryDataStore extends PreferenceDataStore {

    private HashMap<String, Boolean> mBoolMap = new HashMap<>();
    private HashMap<String, Integer> mIntMap = new HashMap<>();
    private HashMap<String, Float> mFloatMap = new HashMap<>();
    private HashMap<String, String> mStringMap = new HashMap<>();
    private HashMap<String, Set<String>> mStringSetMap = new HashMap<>();

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        return mBoolMap.getOrDefault(key, defValue);
    }

    @Override
    public void putBoolean(String key, boolean value) {
        mBoolMap.put(key, value);
    }

    @Override
    public int getInt(String key, int defValue) {
        return mIntMap.getOrDefault(key, defValue);
    }

    @Override
    public void putInt(String key, int value) {
        mIntMap.put(key, value);
    }

    @Override
    public float getFloat(String key, float defValue) {
        return mFloatMap.getOrDefault(key, defValue);
    }

    @Override
    public void putFloat(String key, float value) {
        mFloatMap.put(key, value);
    }

    @Nullable
    @Override
    public String getString(String key, @Nullable String defValue) {
        return mStringMap.getOrDefault(key, defValue);
    }

    @Override
    public void putString(String key, @Nullable String value) {
        mStringMap.put(key, value);
    }

    @Nullable
    @Override
    public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
        return mStringSetMap.getOrDefault(key, defValues);
    }

    @Override
    public void putStringSet(String key, @Nullable Set<String> values) {
        mStringSetMap.put(key, values);
    }
}
