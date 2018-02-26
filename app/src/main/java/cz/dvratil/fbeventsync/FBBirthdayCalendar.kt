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

class FBBirthdayCalendar(context: SyncContext) : FBCalendar(context, FBCalendar.CalendarType.TYPE_BIRTHDAY) {

    private var mSyncedEvents =  HashSet<String>()

    override fun doSyncEvent(event: FBEvent) {
        super.doSyncEvent(event)
        mSyncedEvents.add(event.eventId())
    }

    override fun finalizeSync() {
        if (!isEnabled) {
            return
        }

        sync()

        // In birthday calendar all event are in past (they are recurrent). By removing all events
        // we synced from mPastLocalIds we are left with events that we did not receive in this sync
        // run - such events represent birthdays of friends who have been unfriended since the last
        // sync and we want those removed.
        mSyncedEvents.forEach { mPastLocalIds.remove(it) }
        for (eventId in mPastLocalIds.values) {
            try {
                FBEvent.remove(mContext, eventId)
                mSyncStats.removed += 1
            } catch (e: Exception) {
                when (e) {
                    is android.os.RemoteException,
                    is android.database.sqlite.SQLiteException -> mContext.logger.error(TAG, "FBCalendar.finalizeSync: $e")
                    else -> {
                        mContext.logger.error(TAG, "FBCalendar.finalizeSync: unhandled $e")
                        throw e
                    }
                }
            }
        }

        super.finalizeSync()
    }

    companion object {
        private const val TAG = "FBBirthdayCalendar"
    }
}
