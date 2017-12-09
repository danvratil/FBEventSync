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

import com.tngtech.java.junit.dataprovider.DataProvider;

import org.junit.runners.model.FrameworkMethod;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.LinkedList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class ExternalFileDataProvider {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface ExternalFile {
        String fileName();
    }

    @DataProvider(format = "%m:%p[0]")
    public static Object[] load(FrameworkMethod testMethod)
        throws org.xml.sax.SAXException,
               javax.xml.parsers.ParserConfigurationException,
               java.io.IOException
    {
        String testDataFile = testMethod.getAnnotation(ExternalFile.class).fileName();
        InputStream stream = null;
        // FIXME: I cannot be bothered to figure out what our relative path to root is and
        // it's different when I run it in Android Studio and manually via gradle from terminal
        try {
            stream = new FileInputStream("src/test/res/values/" + testDataFile);
        } catch (java.io.FileNotFoundException e) {
            stream = new FileInputStream("app/src/test/res/values/" + testDataFile);
        }

        LinkedList<Object[]> testCases = new LinkedList<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setIgnoringElementContentWhitespace(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(stream, "UTF-8");
        NodeList xmlTestCases = doc.getElementsByTagName("testcase");
        for (int i = 0; i < xmlTestCases.getLength(); i++) {
            Node xmlTestCase = xmlTestCases.item(i);
            Node xmlInput = xmlTestCase.getFirstChild().getNextSibling();
            Node xmlOutput = xmlTestCase.getLastChild().getPreviousSibling();

            testCases.add(new Object[]{
                    xmlTestCase.getAttributes().getNamedItem("name").getNodeValue(),
                    xmlInput.getTextContent().trim(),
                    xmlOutput.getTextContent().trim()
            });
        }

        Object[][] v = testCases.toArray(new Object[testCases.size()][3]);
        return v;
    }
}
