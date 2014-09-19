package org.codehaus.stax.test.evt;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.*;

/**
 * Class that tests basic dealing of the StartDocument event (skipping,
 * accessing)
 *
 * @author Tatu Saloranta
 */
public class TestStartDocument
    extends BaseEventTest
{
    /**
     * First, let's see what happens when there's no decl
     */
    public void testTrivialValid()
        throws XMLStreamException
    {
        String XML = "<root />";
        for (int i = 0; i < 4; ++i) {
            boolean ns = (i & 1) != 0;
            boolean coal = (i & 2) != 0;
            XMLEventReader er = getReader(XML, ns, coal);

            // First, explicit access without peek
            XMLEvent evt = er.nextEvent();
            assertTrue(evt.isStartDocument());
            StartDocument devt = (StartDocument) evt;
            assertFalse(devt.encodingSet());
            assertFalse("Stand-alone should not be assumed true if not included in xml declaration", devt.standaloneSet());
            // Version is to default to "1.0", as per stax 1.0 javadocs
            assertEquals("1.0", devt.getVersion());
            er.close();
        }

        // And then let's check with peeking:
        for (int i = 0; i < 4; ++i) {
            boolean ns = (i & 1) != 0;
            boolean coal = (i & 2) != 0;
            XMLEventReader er = getReader(XML, ns, coal);
            XMLEvent evt = er.peek();
            assertTrue(evt.isStartDocument());
            StartDocument devt = (StartDocument) evt;
            assertFalse(devt.encodingSet());
            assertFalse(devt.standaloneSet());
            // Version is to default to "1.0", as per stax 1.0 javadocs
            assertEquals("1.0", devt.getVersion());
            er.close();

            // And then let's see that we can still access it normally too
            evt = er.peek();
            assertTrue(evt.isStartDocument());
            // and cast just for fun (to check it really is startdoc event)
            devt = (StartDocument) evt;
        }
    }

    public void testNormalValid()
        throws XMLStreamException
    {
        String XML = "<?xml version='1.0' encoding='UTF-8' standalone='yes'?>"
            +"<root />";
        for (int i = 0; i < 4; ++i) {
            boolean ns = (i & 1) != 0;
            boolean coal = (i & 2) != 0;
            XMLEventReader er = getReader(XML, ns, coal);

            // First, explicit access without peek
            XMLEvent evt = er.nextEvent();
            assertTrue(evt.isStartDocument());
            StartDocument devt = (StartDocument) evt;
            assertEquals("1.0", devt.getVersion());
            assertTrue(devt.encodingSet());
            assertEquals("UTF-8", devt.getCharacterEncodingScheme());
            assertTrue(devt.standaloneSet());
            assertTrue(devt.isStandalone());
            er.close();
        }

        // And then let's check with peeking:
        for (int i = 0; i < 4; ++i) {
            boolean ns = (i & 1) != 0;
            boolean coal = (i & 2) != 0;
            XMLEventReader er = getReader(XML, ns, coal);

            XMLEvent evt = er.peek();
            assertTrue(evt.isStartDocument());
            StartDocument devt = (StartDocument) evt;
            assertEquals("1.0", devt.getVersion());
            assertTrue(devt.encodingSet());
            assertEquals("UTF-8", devt.getCharacterEncodingScheme());
            assertTrue(devt.standaloneSet());
            assertTrue(devt.isStandalone());
            er.close();

            // And then let's see that we can still access it normally too
            evt = er.peek();
            assertTrue(evt.isStartDocument());
            // and cast just for fun (to check it really is startdoc event)
            devt = (StartDocument) evt;
        }
    }

    /**
     * Let's also quickly verify that we can skip xml declaration (actual
     * or virtual) to the root element -- should work ok.
     */
    public void testNextEvent()
        throws XMLStreamException
    {
        String XML = "<root />";
        for (int i = 0; i < 4; ++i) {
            boolean ns = (i & 1) != 0;
            boolean coal = (i & 2) != 0;
            XMLEventReader er = getReader(XML, ns, coal);

            XMLEvent evt = er.nextTag();
            assertNotNull(evt);
            assertTrue(evt.isStartElement());
            StartElement elem = evt.asStartElement();
            QName n = elem.getName();
            assertNotNull(n);
            assertEquals("root", n.getLocalPart());
        }

        XML = "<?xml version='1.0' encoding='UTF-8' standalone='no'?>"
            +"<root />";
        for (int i = 0; i < 4; ++i) {
            boolean ns = (i & 1) != 0;
            boolean coal = (i & 2) != 0;
            XMLEventReader er = getReader(XML, ns, coal);
            XMLEvent evt = er.peek();

            assertTrue(evt.isStartDocument());

            // Fine, and then should get the start elem anyways
            evt = er.nextTag();
            assertNotNull(evt);
            assertTrue(evt.isStartElement());
            StartElement elem = evt.asStartElement();
            QName n = elem.getName();
            assertNotNull(n);
            assertEquals("root", n.getLocalPart());
        }
    }

    /*
    /////////////////////////////////////////////////
    // Internal methods:
    /////////////////////////////////////////////////
     */

    private XMLEventReader getReader(String contents, boolean nsAware,
                                     boolean coalesce)
        throws XMLStreamException
    {
        return getReader(contents, nsAware, coalesce, true);
    }

    private XMLEventReader getReader(String contents, boolean nsAware,
                                     boolean coalesce, boolean expandEnt)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setNamespaceAware(f, nsAware);
        setCoalescing(f, coalesce);
        setSupportDTD(f, true);
        setValidating(f, false);
        setReplaceEntities(f, expandEnt);
        return constructEventReader(f, contents);
    }
}
