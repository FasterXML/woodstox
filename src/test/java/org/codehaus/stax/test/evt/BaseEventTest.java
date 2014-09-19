package org.codehaus.stax.test.evt;

import java.io.*;
import java.util.Iterator;

import javax.xml.stream.*;
import javax.xml.stream.events.*;

import org.codehaus.stax.test.BaseStaxTest;

/**
 * Base class for all StaxTest unit tests that test Event API
 * functionality.
 *
 * @author Tatu Saloranta
 */
public abstract class BaseEventTest
    extends BaseStaxTest
{
    protected BaseEventTest() { super(); }

    /*
    ///////////////////////////////////////////////////
    // Utility methods
    ///////////////////////////////////////////////////
     */

    protected XMLEventFactory getEventFactory()
        throws FactoryConfigurationError
    {
            return XMLEventFactory.newInstance();
    }

    protected static XMLEventReader constructEventReader(XMLInputFactory f, String content)
        throws XMLStreamException
    {
        return f.createXMLEventReader(new StringReader(content));
    }

    protected static XMLEventReader constructEventReaderForFile(XMLInputFactory f, String filename)
        throws IOException, XMLStreamException
    {
        File inf = new File(filename);
        XMLEventReader er = f.createXMLEventReader(inf.toURL().toString(),
                                                   new FileReader(inf));
        return er;
    }

    /**
     * Method that will iterate through contents of an XML document
     * using specified event reader; will also access some of data
     * to make sure reader reads most of lazy-loadable data.
     * Method is usually called to try to get an exception for invalid
     * content.
     *
     * @return Dummy value calculated on contents; used to make sure
     *   no dead code is eliminated
     */
    protected int streamThrough(XMLEventReader er)
        throws XMLStreamException
    {
        int result = 0;

        while (er.hasNext()) {
            XMLEvent evt = er.nextEvent();
            int type = evt.getEventType();
            result += type;
            if (evt.isCharacters()) {
                result += evt.asCharacters().getData().hashCode();
            }
        }

        return result;
    }

    protected int calcAttrCount(StartElement elem)
        throws XMLStreamException
    {
        int count = 0;
        Iterator it = elem.getAttributes();
        if (it != null) {
            while (it.hasNext()) {
                Attribute attr = (Attribute) it.next();
                ++count;
            }
        }
        return count;
    }

    public static void checkEventIsMethods(int type, XMLEvent evt)
    {
        int actualType = evt.getEventType();
        if (actualType != type) {
            /* Minor deviation; should Characters objects that are constructed
             * for CDATA and SPACE return true type or CHARACTERS?
             */
            if (type == CHARACTERS &&
                (actualType == SPACE || actualType == CDATA)) {
                // for now let's let this pass...
            } else {
                assertTokenType(type, actualType); // this'll fail and output descs for types
            }
        }

        /* Hmmh. Whether Namespace object should return true or false
         * is an open question. So let's accept both
         */
        if (type == NAMESPACE) {
            /* for now let's just ask for it (to make sure it won't throw
             * exceptions), but not verify the value
             */
            boolean isAttr = evt.isAttribute();
        } else {
            assertEquals((type == ATTRIBUTE), evt.isAttribute());
        }

        assertEquals((type == CHARACTERS), evt.isCharacters());
        assertEquals((type == START_DOCUMENT), evt.isStartDocument());
        assertEquals((type == END_DOCUMENT), evt.isEndDocument());
        assertEquals((type == START_ELEMENT), evt.isStartElement());
        assertEquals((type == END_ELEMENT), evt.isEndElement());
        assertEquals((type == ENTITY_REFERENCE), evt.isEntityReference());
        assertEquals((type == NAMESPACE), evt.isNamespace());
        assertEquals((type == PROCESSING_INSTRUCTION), evt.isProcessingInstruction());
    }

    /**
     * Simple test utility method that just calls output method, to verify
     * it does not throw anything nasty, and does output something.
     * Not enough to verify actual working, but should exercise code path
     * to check for fatal problems.
     */
    public void testEventWritability(XMLEvent evt)
        throws XMLStreamException
    {
        StringWriter sw = new StringWriter();
        evt.writeAsEncodedUnicode(sw);

        // Some events do not (have to) output anything:
        switch (evt.getEventType()) {
        case END_DOCUMENT: // nothing to output, usually
            return;
        }

        assertTrue(sw.toString().length() > 0);
    }
}
