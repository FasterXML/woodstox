package stax2.evt;

import java.io.StringWriter;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.*;

import org.codehaus.stax2.*;
import org.codehaus.stax2.evt.*;

import stax2.BaseStax2Test;

/**
 * Set of unit tests that checks that {@linkAttributeInfo}
 * implementation works as expected.
 */
public class TestDTDEvent
    extends BaseStax2Test
{
    public void testDTDCreation()
        throws XMLStreamException
    {
        final String COMMENT_STR = "<!-- xxx -->";

        XMLOutputFactory f = getOutputFactory();
        StringWriter strw = new StringWriter();
        XMLEventWriter w = f.createXMLEventWriter(strw);

        XMLEventFactory2 evtf = getEventFactory();

        w.add(evtf.createStartDocument());
        w.add(evtf.createDTD("root", "sysid", "pubid", COMMENT_STR));
        w.add(evtf.createStartElement("", "", "root"));
        w.add(evtf.createEndElement("", "", "root"));
        w.add(evtf.createEndDocument());

        w.close();

        String xmlContent = strw.toString();
        XMLInputFactory f2 = getInputFactory();

        /* Hmmh. This is bit problematic: we don't want to read an
         * external subset... yet it'd be good to test that system
         * and public ids are properly parsed. For now, let's see if
         * this works:
         */
        setSupportDTD(f2, false);
        XMLEventReader2 er = constructEventReader(f2, xmlContent);

        assertTokenType(START_DOCUMENT, er.nextEvent().getEventType());
        XMLEvent evt = er.nextEvent();
        assertTokenType(DTD, evt.getEventType());

        // Check that all properties are correct:
        DTD2 dtd = (DTD2) evt;
        assertEquals("root", dtd.getRootName());
        assertEquals("pubid", dtd.getPublicId());
        assertEquals("sysid", dtd.getSystemId());
        String intss = dtd.getInternalSubset().trim();
        assertEquals(COMMENT_STR, intss);

        evt = er.nextEvent();
        assertTokenType(START_ELEMENT, evt.getEventType());
        StartElement start = evt.asStartElement();
        QName name = start.getName();
        assertEquals("root", name.getLocalPart());

        // just to test hasNextEvent()...
        assertTrue(er.hasNextEvent());

        assertTokenType(END_ELEMENT, er.nextEvent().getEventType());
        assertTokenType(END_DOCUMENT, er.nextEvent().getEventType());
    }
}
