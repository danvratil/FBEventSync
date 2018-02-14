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

import android.provider.CalendarContract;

import java.util.HashSet;

public class FBBirthdayCalendar extends FBCalendar {

    HashSet<String> mSyncedEvents = null;

    static private final String TAG = "FBBirthdayCalendar";

    protected FBBirthdayCalendar(SyncContext context) {
        super(context, CalendarType.TYPE_BIRTHDAY);

        mSyncedEvents = new HashSet<>();
    }

    @Override
    protected void doSyncEvent(FBEvent event) {
        super.doSyncEvent(event);
        mSyncedEvents.add(event.eventId());
    }

    @Override
    public void finalizeSync() {
        if (!mIsEnabled) {
            return;
        }

        sync();

        // In birthday calendar all event are in past (they are recurrent). By removing all events
        // we synced from mPastLocalIds we are left with events that we did not receive in this sync
        // run - such events represent birthdays of friends who have been unfriended since the last
        // sync and we want those removed.
        for (String syncedEvent : mSyncedEvents) {
            mPastLocalIds.remove(syncedEvent);
        }
        for (Long eventId : mPastLocalIds.values()) {
            try {
                FBEvent.remove(mContext, eventId);
                mSyncStats.removed += 1;
            } catch (android.os.RemoteException e) {
                mContext.getLogger().error(TAG,"Remote exception during FBCalendar finalizeSync: %s", e.getMessage());
                // continue with remaining events
            } catch (android.database.sqlite.SQLiteException e) {
                mContext.getLogger().error(TAG,"SQL exception during FBCalendar fynalize sync: %s", e.getMessage());
                // continue with remaining events
            }
        }

        super.finalizeSync();
    }
}
