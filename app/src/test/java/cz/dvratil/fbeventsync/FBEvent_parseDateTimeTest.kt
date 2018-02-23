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

package cz.dvratil.fbeventsync

import com.tngtech.java.junit.dataprovider.DataProviderRunner
import com.tngtech.java.junit.dataprovider.UseDataProvider

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(DataProviderRunner::class)
class FBEvent_parseDateTimeTest {

    @Test
    @UseDataProvider(value = "load", location = arrayOf(ExternalFileDataProvider::class))
    @ExternalFileDataProvider.ExternalFile(fileName = "fbeventtest_datetime.xml")
    @Throws(Exception::class)
    fun test(name: String, input: String, expectedOutput: String) {
        Assert.assertEquals(java.lang.Long.parseLong(expectedOutput), FBEvent.parseDateTime(input))
    }
}

