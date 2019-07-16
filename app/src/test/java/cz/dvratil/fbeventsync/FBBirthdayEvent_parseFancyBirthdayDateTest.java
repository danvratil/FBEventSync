/*
    Copyright (C) 2019  Daniel Vrátil <me@dvratil.cz>

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

import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

@RunWith(DataProviderRunner.class)
public class FBBirthdayEvent_parseFancyBirthdayDateTest {

    private Calendar today(String tzid) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(tzid));
        cal.set(Calendar.HOUR, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(Calendar.AM_PM, Calendar.AM);
        return cal;
    }

    @Test
    @UseDataProvider(value = "load", location = ExternalFileDataProvider.class)
    @ExternalFileDataProvider.ExternalFile(fileName = "fbbirthdayeventtest_fancydate.xml")
    public void test(String name, String input, String expectedOutput) {
        long expected = Long.parseLong(expectedOutput);

        if (input.startsWith("Today")) {
            Calendar now = today("UTC");
            expected = now.getTimeInMillis();
        } else if (input.startsWith("Tomorrow")) {
            Calendar now = today("UTC");
            now.add(Calendar.DATE, 1);
            expected = now.getTimeInMillis();
        }

        Date result = FBBirthdayEvent.Companion.parseFancyBirthdayDate(input.trim(), null);

        Assert.assertEquals(expected, result.getTime());
    }
}

