package stax2.evt;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.stax2.*;
import org.codehaus.stax2.evt.*;

import stax2.BaseStax2Test;

/**
 * Set of unit tests that checks that new Stax2 features work (generically)
 * for event instances.
 */
public class TestEventTypes
    extends BaseStax2Test
{
    /**
     * This unit test does some crude checking to ensure that the usual
     * events can be output to the specified writer. Events are here
     * constructed using event factory.
     */
    public void testEventObjectOutput()
        throws XMLStreamException
    {
        XMLEventFactory2 evtf = getEventFactory();
        XMLOutputFactory2 f = getOutputFactory();
        StringWriter strw = new StringWriter();
        XMLStreamWriter2 sw = f.createXMLStreamWriter(strw, "UTF-8");

        XMLEvent2 evt = (XMLEvent2) evtf.createStartDocument();
        evt.writeUsing(sw);

        // Let's output root element with no attrs
        ((XMLEvent2) evtf.createStartElement("", "", "root")).writeUsing(sw);
        ((XMLEvent2) evtf.createEndElement("", "", "root")).writeUsing(sw);

        ((XMLEvent2) evtf.createEndDocument()).writeUsing(sw);

        sw.close();

        // Ok, parsing:
        XMLInputFactory f2 = getInputFactory();
        XMLStreamReader sr = f2.createXMLStreamReader(new StringReader(strw.toString()));
        assertTokenType(START_DOCUMENT, sr.getEventType());
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(END_DOCUMENT, sr.next());
        sr.close();
    }
}
