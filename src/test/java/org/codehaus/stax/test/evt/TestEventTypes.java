package org.codehaus.stax.test.evt;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.*;

/**
 * Class that contains simple tests for making sure that event types
 * produced have expected settings.
 *
 * @author Tatu Saloranta
 */
public class TestEventTypes
    extends BaseEventTest
{
    public void testTypes()
        throws XMLStreamException
    {
        doTestTypes(false); // non-namespace
        doTestTypes(true);  // namespace-aware
    }

    /*
    ////////////////////////////////////////
    // Private methods, tests
    ////////////////////////////////////////
     */

    private void doTestTypes(boolean useNs)
        throws XMLStreamException
    {
        String INPUT =
            "<?xml version='1.0'?>"
            +"<?proc instr?>"
            +"<!--comment - - contents-->"
            +"<!DOCTYPE root>"
            +"<root attr='value'>some text"
            +"</root>";

        XMLEventReader er = getReader(INPUT, useNs);

        // First, should get START_DOCUMENT:
        XMLEvent evt = er.nextEvent();
        assertEquals(START_DOCUMENT, evt.getEventType());
        assertTrue(evt.isStartDocument());
        {
            StartDocument doc = (StartDocument) evt;
            assertFalse(doc.encodingSet());
            /* Not sure how to test getCharacterEncodingScheme(); problem
             * is, XML parser are allowed to auto-detect things... but don't
             * have to.
             */
            // ... same is true about system id too

	    if (doc.standaloneSet()) {
		fail("Reporting stand-alone as set, even though xml declaration has no value (should assume 'false')");
	    }

            // I guess parser should NOT think it is stand-alone without decl?
            assertFalse("Should assume 'no' as stand-alone value if no extra information", doc.isStandalone());
        }

        // Then, proc. instr:
        evt = er.nextEvent();
        assertEquals(PROCESSING_INSTRUCTION, evt.getEventType());
        assertTrue(evt.isProcessingInstruction());
        {
            ProcessingInstruction pi = (ProcessingInstruction) evt;
            assertEquals("proc", pi.getTarget());
            // data may or may not contain the space between target and data...
            String data = pi.getData().trim();
            assertEquals("instr", data);
        }

        // Then COMMENT
        evt = er.nextEvent();
        assertEquals(COMMENT, evt.getEventType());
        {
            Comment c = (Comment) evt;
            assertEquals("comment - - contents", c.getText());
        }

        // Then DTD
        evt = er.nextEvent();
        assertEquals(DTD, evt.getEventType());
        {
            DTD dtd = (DTD) evt;
            checkEventIsMethods(DTD, dtd);
            testEventWritability(dtd);
            assertNotNull(dtd.getDocumentTypeDeclaration());
        }

        // Then START_ELEMENT
        evt = er.nextEvent();
        assertEquals(START_ELEMENT, evt.getEventType());
        assertTrue(evt.isStartElement());
        QName elemName;

        {
            StartElement elem = evt.asStartElement();
            elemName = elem.getName();
            assertEquals("root", elemName.getLocalPart());

            assertEquals(1, calcAttrCount(elem));

            Attribute noSuchAttr = elem.getAttributeByName(new QName("foobar"));
            assertNull("Should not have found attribute 'foobar' from the element", noSuchAttr);

            Attribute attr = elem.getAttributeByName(new QName("attr"));
            assertNotNull("Should have found attribute 'attr' from the element", attr);
            assertTrue(attr.isAttribute());
            assertEquals("value", attr.getValue());
            assertEquals("CDATA", attr.getDTDType());
            assertTrue(attr.isSpecified());
            QName an = attr.getName();
            assertEquals("attr", an.getLocalPart());
        }

        // Then CHARACTERS
        evt = er.nextEvent();
        assertEquals(CHARACTERS, evt.getEventType());
        assertTrue(evt.isCharacters());
        {
            Characters ch = evt.asCharacters();
            assertFalse(ch.isCData());
            assertFalse(ch.isIgnorableWhiteSpace());
            assertFalse(ch.isWhiteSpace());
            assertEquals("some text", ch.getData());
        }

        // Then END_ELEMENT
        evt = er.nextEvent();
        assertEquals(END_ELEMENT, evt.getEventType());
        assertTrue(evt.isEndElement());
        {
            EndElement elem = evt.asEndElement();
            QName endName = elem.getName();
            assertEquals("root", endName.getLocalPart());
            // Let's also verify it's equal to the start element name...
            assertEquals(elemName, endName);
        }

        // And finally END_DOCUMENT
        evt = er.nextEvent();
        assertEquals(END_DOCUMENT, evt.getEventType());
        assertTrue(evt.isEndDocument());
        // Nothing to test, but let's case to ensure it's of right type
        {
            @SuppressWarnings("unused")
            EndDocument doc = (EndDocument) evt;
        }
    }

    /*
    ////////////////////////////////////////
    // Private methods, other
    ////////////////////////////////////////
     */

    private XMLEventReader getReader(String contents, boolean nsAware)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setCoalescing(f, true);
        setNamespaceAware(f, nsAware);
        setValidating(f, false);
        setSupportDTD(f, true);
        return constructEventReader(f, contents);
    }

}

