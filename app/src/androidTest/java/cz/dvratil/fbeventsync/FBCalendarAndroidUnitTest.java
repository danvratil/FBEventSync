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

import android.Manifest;
import android.accounts.Account;
import android.content.ContentResolver;
import android.content.SyncResult;
import android.content.pm.PackageManager;
import android.provider.CalendarContract;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.v4.content.ContextCompat;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;

import biweekly.component.VEvent;
import biweekly.property.Organizer;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class FBCalendarAndroidUnitTest {

    private static String TEST_ACCOUNT_NAME = "testAccount";

    @Rule
    public GrantPermissionRule mGrantPermission = GrantPermissionRule.grant(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR);

    private SyncContext prepareSyncContext() {
        ContentResolver contentResolver = InstrumentationRegistry.getContext().getContentResolver();
        Account mockAccount = new Account(TEST_ACCOUNT_NAME, "cz.dvratil.fbeventsync");
        Preferences preferences = new Preferences(InstrumentationRegistry.getTargetContext());
        preferences.setDataStore(new PreferenceMemoryDataStore());
        return new SyncContext(
                InstrumentationRegistry.getTargetContext(),
                mockAccount, "fakeAccessToken",
                contentResolver.acquireContentProviderClient(CalendarContract.CONTENT_URI),
                new SyncResult(),
                preferences,
                Logger.Companion.getInstance(InstrumentationRegistry.getContext()));
    }

    private FBEvent createEvent(SyncContext context, InspectableFBCalendar calendar,
                                String summary, String id, Calendar start, Calendar end) {
        VEvent vevent = new VEvent();
        vevent.setUid("e" + id + "@facebook.com");
        vevent.setSummary(summary);
        vevent.setDescription(summary);
        vevent.setOrganizer(new Organizer("Organizer", "noreply@facebook.com"));
        vevent.setLocation("Internet");
        vevent.setDateStart(start.getTime(), true);
        vevent.setDateEnd(end.getTime(), true);
        vevent.setExperimentalProperty("PARTSTAT", "ACCEPTED");

        FBEvent event = FBEvent.Companion.parse(vevent, context);
        event.setCalendar(calendar);
        try {
            event.create(context);
        } catch (Exception e) {
            Assert.fail("Unexpected exception thrown: " + e.toString());
        }
        return event;
    }

    private InspectableFBCalendar assertCreateLocalCalendar(SyncContext context) {
        InspectableFBCalendar calendar = new InspectableFBCalendar(context, FBCalendar.CalendarType.TYPE_ATTENDING);
        calendar.init();
        Assert.assertTrue(calendar.createLocalCalendarCalled);
        return calendar;
    }

    private void assertDeleteLocalCalendar(InspectableFBCalendar calendar) {
        try {
            calendar.deleteLocalCalendar();
        } catch (Exception e) {
            Assert.fail("Unexpected exception thrown: " + e.toString());
        }
        Assert.assertTrue(calendar.deleteLocalCalendarCalled);
    }

    @After
    public void cleanup() {
        ContentResolver contentResolver = InstrumentationRegistry.getContext().getContentResolver();
        contentResolver.delete(
                CalendarContract.Calendars.CONTENT_URI.buildUpon()
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, TEST_ACCOUNT_NAME)
                    .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, "cz.dvratil.fbeventsync")
                    .build(),
                null, null);
    }

    @Test
    public void FBCalendar_testPermissions() {
        Assert.assertEquals(ContextCompat.checkSelfPermission(InstrumentationRegistry.getContext(), Manifest.permission.READ_CALENDAR),
                            PackageManager.PERMISSION_GRANTED);
        Assert.assertEquals(ContextCompat.checkSelfPermission(InstrumentationRegistry.getContext(), Manifest.permission.WRITE_CALENDAR),
                            PackageManager.PERMISSION_GRANTED);
    }

    @Test
    public void FBCalendar_shouldCreateLocalCalendar() {
        SyncContext context = prepareSyncContext();
        Assert.assertNotNull(context);

        InspectableFBCalendar calendar = assertCreateLocalCalendar(context);
        Assert.assertNotNull(calendar);

        assertDeleteLocalCalendar(calendar);
    }

    @Test
    public void FBCalendar_testFetchLocalPastEvents() {
        SyncContext context = prepareSyncContext();
        Assert.assertNotNull(context);

        InspectableFBCalendar calendar = assertCreateLocalCalendar(context);
        Assert.assertNotNull(calendar);
        // First lets inject some test events into the calendar
        FBEvent simplePastEvent = createEvent(context, calendar, "Simple Past Event", "000001",
                new GregorianCalendar(2018, 1, 1, 14, 0, 0),
                new GregorianCalendar(2018, 1, 1, 18, 0 ,0));
        Assert.assertNotNull(simplePastEvent);

        FBEvent multiDayPastEvent = createEvent(context, calendar, "Multiday Past Event", "000002",
                new GregorianCalendar(2018, 1, 1, 8, 0, 0),
                new GregorianCalendar(2018, 1, 3, 20, 0, 0));
        Assert.assertNotNull(multiDayPastEvent);

        Calendar start = Calendar.getInstance();
        start.add(Calendar.HOUR_OF_DAY, -1);
        Calendar end = Calendar.getInstance();
        end.add(Calendar.HOUR_OF_DAY, 1);
        FBEvent simpleOngoingEvent = createEvent(context, calendar, "Simple Ongoing Event", "000003", start, end);
        Assert.assertNotNull(simpleOngoingEvent);

        start = Calendar.getInstance();
        start.add(Calendar.DAY_OF_MONTH, -1);
        end = Calendar.getInstance();
        end.add(Calendar.DAY_OF_MONTH, 1);
        FBEvent multiDayOngoingEvent = createEvent(context, calendar, "Multiday Ongoing Event", "000004", start, end);
        Assert.assertNotNull(multiDayOngoingEvent);

        // Java has the worst datetime API ever...
        start = Calendar.getInstance();
        start.add(Calendar.DAY_OF_WEEK, 1);
        start.set(Calendar.HOUR_OF_DAY, 8);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        end = (Calendar) start.clone();
        end.set(Calendar.HOUR_OF_DAY, 20);
        FBEvent simpleFutureEvent = createEvent(context, calendar, "Simple Future Event", "000005", start, end);
        Assert.assertNotNull(simpleFutureEvent);

        end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_WEEK, 3);
        FBEvent multiDayFutureEvent = createEvent(context, calendar, "Multiday Future Event", "00006", start, end);
        Assert.assertNotNull(multiDayFutureEvent);

        HashMap<String, Long> events = null;
        try {
            events = calendar.fetchLocalPastEvents();
        } catch (Exception e) {
            Assert.fail("Unexpected exception thrown: " + e.toString());
        }
        Assert.assertNotNull(events);
        Assert.assertFalse(events.isEmpty());

        Assert.assertTrue(events.containsKey(simplePastEvent.eventId()));
        Assert.assertTrue(events.containsKey(multiDayPastEvent.eventId()));
        Assert.assertFalse(events.containsKey(simpleOngoingEvent.eventId()));
        Assert.assertFalse(events.containsKey(multiDayOngoingEvent.eventId()));
        Assert.assertFalse(events.containsKey(simpleFutureEvent.eventId()));
        Assert.assertFalse(events.containsKey(multiDayFutureEvent.eventId()));

        assertDeleteLocalCalendar(calendar);
    }

    @Test
    public void FBCalendar_testFetchLocalFutureEvents() {
        SyncContext context = prepareSyncContext();
        Assert.assertNotNull(context);

        InspectableFBCalendar calendar = assertCreateLocalCalendar(context);
        Assert.assertNotNull(calendar);
        // First lets inject some test events into the calendar
        FBEvent simplePastEvent = createEvent(context, calendar, "Simple Past Event", "000001",
                new GregorianCalendar(2018, 1, 1, 14, 0, 0),
                new GregorianCalendar(2018, 1, 1, 18, 0 ,0));
        Assert.assertNotNull(simplePastEvent);

        FBEvent multiDayPastEvent = createEvent(context, calendar, "Multiday Past Event", "000002",
                new GregorianCalendar(2018, 1, 1, 8, 0, 0),
                new GregorianCalendar(2018, 1, 3, 20, 0, 0));
        Assert.assertNotNull(multiDayPastEvent);

        Calendar start = Calendar.getInstance();
        start.add(Calendar.HOUR_OF_DAY, -1);
        Calendar end = Calendar.getInstance();
        end.add(Calendar.HOUR_OF_DAY, 1);
        FBEvent simpleOngoingEvent = createEvent(context, calendar, "Simple Ongoing Event", "000003", start, end);
        Assert.assertNotNull(simpleOngoingEvent);

        start = Calendar.getInstance();
        start.add(Calendar.DAY_OF_MONTH, -1);
        end = Calendar.getInstance();
        end.add(Calendar.DAY_OF_MONTH, 1);
        FBEvent multiDayOngoingEvent = createEvent(context, calendar, "Multiday Ongoing Event", "000004", start, end);
        Assert.assertNotNull(multiDayOngoingEvent);

        // Java has the worst datetime API ever...
        start = Calendar.getInstance();
        start.add(Calendar.DAY_OF_WEEK, 1);
        start.set(Calendar.HOUR_OF_DAY, 8);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        end = (Calendar) start.clone();
        end.set(Calendar.HOUR_OF_DAY, 20);
        FBEvent simpleFutureEvent = createEvent(context, calendar, "Simple Future Event", "000005", start, end);
        Assert.assertNotNull(simpleFutureEvent);

        end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_WEEK, 3);
        FBEvent multiDayFutureEvent = createEvent(context, calendar, "Multiday Future Event", "00006", start, end);
        Assert.assertNotNull(multiDayFutureEvent);

        HashMap<String, Long> events = null;
        try {
            events = calendar.fetchLocalFutureEvents();
        } catch (Exception e) {
            Assert.fail("Unexpected exception thrown: " + e.toString());
        }
        Assert.assertNotNull(events);
        Assert.assertFalse(events.isEmpty());

        Assert.assertFalse(events.containsKey(simplePastEvent.eventId()));
        Assert.assertFalse(events.containsKey(multiDayPastEvent.eventId()));
        Assert.assertTrue(events.containsKey(simpleOngoingEvent.eventId()));
        Assert.assertTrue(events.containsKey(multiDayOngoingEvent.eventId()));
        Assert.assertTrue(events.containsKey(simpleFutureEvent.eventId()));
        Assert.assertTrue(events.containsKey(multiDayFutureEvent.eventId()));

        assertDeleteLocalCalendar(calendar);
    }
}
