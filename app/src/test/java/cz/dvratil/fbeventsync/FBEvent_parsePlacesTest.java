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

import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DataProviderRunner.class)
public class FBEvent_parsePlacesTest {

    @Test
    @UseDataProvider(value = "load", location = ExternalFileDataProvider.class)
    @ExternalFileDataProvider.ExternalFile(fileName = "fbeventtest_places.xml")
    public void test(String name, String input, String expectedOutput) throws Exception {
        JSONObject place = new JSONObject(input);
        String output = FBEvent.parsePlace(place);
        Assert.assertEquals(expectedOutput, output);
    }
}