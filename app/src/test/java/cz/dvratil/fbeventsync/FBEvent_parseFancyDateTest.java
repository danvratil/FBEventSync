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

import java.util.TimeZone;

@RunWith(DataProviderRunner.class)
public class FBEvent_parseFancyDateTest {

    @Test
    @UseDataProvider(value = "load", location = ExternalFileDataProvider.class)
    @ExternalFileDataProvider.ExternalFile(fileName = "fbeventtest_fancydate.xml")
    public void test(String name, String input, String expectedOutput) {
        String[] expected = expectedOutput.split(",");
        Assert.assertEquals(2, expected.length);
        FBEvent.Companion.FancyDateResult result = FBEvent.Companion.parseFancyDate(input.trim(), TimeZone.getTimeZone("Europe/Prague"), null);

        Assert.assertEquals(Long.parseLong(expected[0]), result.getDtStart());
        Assert.assertEquals(Long.parseLong(expected[1]), result.getDtEnd());
    }
}

