package org.codehaus.stax.test.evt;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.*;

import java.io.*;

/**
 * This test tries to verify that events can be copied from event reader
 * to event writer, and result in well-formed output
 *
 * @author Tatu Saloranta
 */
public class TestEventCopy
    extends BaseEventTest
{
	public void testCopy()
        throws XMLStreamException
    {
        final String XML =
            "<root>\n"
            +" <branch>\n"
            +"   <leaf attr='123' />"
            +" </branch>\n"
            +" <leaf attr='\"a\"' />"
            +"</root>"
            ;
		XMLEventReader er = getEventReader(XML, true, true);
        StringWriter strw = new StringWriter();
        XMLOutputFactory f = getOutputFactory();
        XMLEventWriter ew = f.createXMLEventWriter(strw);

		while (er.hasNext()) {
            ew.add(er.nextEvent());
		}
        ew.close();

        // And let's then just verify it's well-formed still
        String results = strw.toString();
        er = getEventReader(results, true, true);
        XMLEvent evt;

        // Plus that events are the way they ought to be
        assertNotNull((evt = er.nextEvent()));
        assertTrue(evt.isStartDocument());

        evt = er.nextEvent();
        assertEquals(new QName("root"), evt.asStartElement().getName());

        evt = er.nextEvent();
        assertTrue(evt.isCharacters());
        assertTrue(evt.asCharacters().isWhiteSpace());

        evt = er.nextEvent();
        assertTrue(evt.isStartElement());
        assertEquals(new QName("branch"), evt.asStartElement().getName());

        evt = er.nextEvent();
        assertTrue(evt.isCharacters());
        assertTrue(evt.asCharacters().isWhiteSpace());

        evt = er.nextEvent();
        assertTrue(evt.isStartElement());
        assertEquals(new QName("leaf"), evt.asStartElement().getName());
        evt = er.nextEvent();
        assertTrue(evt.isEndElement());
        assertEquals(new QName("leaf"), evt.asEndElement().getName());

        evt = er.nextEvent();
        assertTrue(evt.isCharacters());
        assertTrue(evt.asCharacters().isWhiteSpace());

        evt = er.nextEvent();
        assertTrue(evt.isEndElement());
        assertEquals(new QName("branch"), evt.asEndElement().getName());

        evt = er.nextEvent();
        assertTrue(evt.isCharacters());
        assertTrue(evt.asCharacters().isWhiteSpace());

        evt = er.nextEvent();
        assertTrue(evt.isStartElement());
        assertEquals(new QName("leaf"), evt.asStartElement().getName());
        evt = er.nextEvent();
        assertTrue(evt.isEndElement());
        assertEquals(new QName("leaf"), evt.asEndElement().getName());

        evt = er.nextEvent();
        assertTrue(evt.isEndElement());
        assertEquals(new QName("root"), evt.asEndElement().getName());
	}

	public void testCopyWithCData()
        throws XMLStreamException
    {
        final String XML =
            "<root><![CDATA[a&b]]></root>";

		XMLEventReader er = getEventReader(XML, true, false);
        StringWriter strw = new StringWriter();
        XMLOutputFactory f = getOutputFactory();
        XMLEventWriter ew = f.createXMLEventWriter(strw);

		while (er.hasNext()) {
            ew.add(er.nextEvent());
		}
        ew.close();

        // And then test what it looks like
        String results = strw.toString();
        er = getEventReader(results, true, false);
        XMLEvent evt;

        // Plus that events are the way they ought to be
        assertNotNull((evt = er.nextEvent()));
        assertTrue(evt.isStartDocument());

        evt = er.nextEvent();
        assertTrue(evt.isStartElement());
        assertEquals(new QName("root"), evt.asStartElement().getName());

        evt = er.nextEvent();
        assertTrue(evt.isCharacters());
        assertTrue("Expected CDATA block to generate a Characters event for which isCData() returns true", evt.asCharacters().isCData());
        assertEquals("a&b", evt.asCharacters().getData());

        evt = er.nextEvent();
        assertTrue(evt.isEndElement());
        assertEquals(new QName("root"), evt.asEndElement().getName());

    }

    private XMLEventReader getEventReader(String contents, boolean nsAware, boolean coal)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setNamespaceAware(f, nsAware);
        setCoalescing(f, coal);
        setSupportDTD(f, true);
        setValidating(f, false);
        return constructEventReader(f, contents);
    }
}
