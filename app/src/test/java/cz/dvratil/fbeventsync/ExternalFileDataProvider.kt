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

import com.tngtech.java.junit.dataprovider.DataProvider

import org.junit.runners.model.FrameworkMethod
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.NodeList

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.util.LinkedList

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

object ExternalFileDataProvider {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
    annotation class ExternalFile(val fileName: String)

    @DataProvider(format = "%m:%p[0]")
    @Throws(org.xml.sax.SAXException::class, javax.xml.parsers.ParserConfigurationException::class, java.io.IOException::class)
    fun load(testMethod: FrameworkMethod): Array<Any> {
        val testDataFile = testMethod.getAnnotation<ExternalFile>(ExternalFile::class.java).fileName()
        var stream: InputStream? = null
        // FIXME: I cannot be bothered to figure out what our relative path to root is and
        // it's different when I run it in Android Studio and manually via gradle from terminal
        try {
            stream = FileInputStream("src/test/res/values/" + testDataFile)
        } catch (e: java.io.FileNotFoundException) {
            stream = FileInputStream("app/src/test/res/values/" + testDataFile)
        }

        val testCases = LinkedList<Array<Any>>()
        val factory = DocumentBuilderFactory.newInstance()
        factory.isValidating = false
        factory.isIgnoringElementContentWhitespace = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(stream, "UTF-8")
        val xmlTestCases = doc.getElementsByTagName("testcase")
        for (i in 0 until xmlTestCases.length) {
            val xmlTestCase = xmlTestCases.item(i)
            val xmlInput = xmlTestCase.firstChild.nextSibling
            val xmlOutput = xmlTestCase.lastChild.previousSibling

            testCases.add(arrayOf(xmlTestCase.attributes.getNamedItem("name").nodeValue, xmlInput.textContent.trim { it <= ' ' }, xmlOutput.textContent.trim { it <= ' ' }))
        }

        return testCases.toTypedArray<Array<Any>>()
    }
}
