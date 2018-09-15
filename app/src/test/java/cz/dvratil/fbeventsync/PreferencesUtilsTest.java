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

import android.util.Log;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import cz.dvratil.fbeventsync.preferences.Utils;

@RunWith(Parameterized.class)
public class PreferencesUtilsTest {

    @Parameterized.Parameters(name = "{index}: \"{1}\"")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { new HashSet<String>() {{ add("Hello"); add("World"); }}, "Hello,World" },
                { new HashSet<String>() {{ add("Hello,"); add("World"); }}, "Hello,,,World" },
                //{ new HashSet<String>() {{ add("Hello,,"); add("World"); }}, "Hello,,,,,World" },
                { new HashSet<String>() {{ add("H,ello"); add("World"); }}, "H,,ello,World" },
                { new HashSet<String>(), "" }
        });

    }

    @Parameterized.Parameter
    public Set<String> fSet;

    @Parameterized.Parameter(1)
    public String fString;


    @Test
    public void test() {
        Assert.assertEquals(fString, Utils.INSTANCE.setToString(fSet));
        Assert.assertEquals(fSet, Utils.INSTANCE.stringToSet(fString));
    }
}
