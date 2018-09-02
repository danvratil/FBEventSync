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

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class InspectableFBCalendar extends FBCalendar {

    public InspectableFBCalendar(SyncContext context, FBCalendar.CalendarType type) {
        super(context, type);
    }

    public boolean createLocalCalendarCalled = false;

    @Override
    public long createLocalCalendar() throws android.os.RemoteException {
        createLocalCalendarCalled = true;
        return super.createLocalCalendar();
    }

    public boolean updateLocalCalendarCalled = false;

    @Override
    public void updateLocalCalendar() throws android.os.RemoteException {
        updateLocalCalendarCalled = true;
        super.updateLocalCalendar();
    }

    @Override
    public long findLocalCalendar() throws android.os.RemoteException {
        return super.findLocalCalendar();
    }

    public boolean deleteLocalCalendarCalled = false;
    @Override
    public void deleteLocalCalendar() throws android.os.RemoteException {
        deleteLocalCalendarCalled = true;
        super.deleteLocalCalendar();
    }


    @Override
    @NotNull
    public HashMap<String, Long> fetchLocalPastEvents() throws android.os.RemoteException {
        return super.fetchLocalPastEvents();
    }

    @Override
    @NotNull
    public HashMap<String, Long> fetchLocalFutureEvents() throws android.os.RemoteException {
        return super.fetchLocalFutureEvents();
    }
}
