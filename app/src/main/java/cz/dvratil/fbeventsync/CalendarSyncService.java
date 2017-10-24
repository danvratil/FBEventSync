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

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class CalendarSyncService extends Service {

    private CalendarSyncAdapter mAdapter;
    private static final Object sAdapterLock  = new Object();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("SYNC", "Sync service created");
        synchronized (sAdapterLock) {
            if (mAdapter == null) {
                mAdapter = new CalendarSyncAdapter(getApplicationContext(), true);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d("SYNC", "Sync service binded");
        return mAdapter.getSyncAdapterBinder();
    }
}
