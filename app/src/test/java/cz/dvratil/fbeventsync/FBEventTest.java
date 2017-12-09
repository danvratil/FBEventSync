package cz.dvratil.fbeventsync;

import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DataProviderRunner.class)
public class FBEventTest {

    @Test
    @UseDataProvider(value = "load", location = ExternalFileDataProvider.class)
    @ExternalFileDataProvider.ExternalFile(fileName = "fbeventtest_places.xml")
    public void testPlaces(String name, String input, String expectedOutput) throws Exception {
        System.err.println(name + ": " + input);
        JSONObject place = new JSONObject(input);
        String output = FBEvent.parsePlace(place);
        Assert.assertEquals(expectedOutput, output);
    }

}