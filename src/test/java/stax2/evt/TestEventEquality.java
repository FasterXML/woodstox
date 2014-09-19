package stax2.evt;

import javax.xml.stream.*;
import javax.xml.stream.events.XMLEvent;

import stax2.BaseStax2Test;

/**
 * As of Stax2 v3 (~= Woodstox 4.0), XMLEvent instances are expected
 * to implement simple equality comparison. Stax2 reference implementation
 * implements this for all event types. This test suite verifies that
 * equality checks work for simple cases.
 */
public class TestEventEquality
    extends BaseStax2Test
{
    public void testSimple()
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setNamespaceAware(f, true);
        String XML =
            "<root>Text...<?proc\ninstr?>"
            +"<ns:leaf xmlns:ns='http://url'><!--  comment--></ns:leaf>\r\n"
            +"<leaf2 _attr1='123' attr2='this&amp;that' />\n"
            +"</root>"
            ;

        XMLEventReader er1 = constructEventReader(f, XML);
        XMLEventReader er2 = constructEventReader(f, XML);

        while (er1.hasNext()) {
            XMLEvent e1 = er1.nextEvent();
            XMLEvent e2 = er2.nextEvent();

            if (!e1.equals(e2) || !e2.equals(e1)) {
                fail("Event 1 (type "+e1.getEventType()+") differs from Event2 (type "+e2.getEventType()+"), location "+e1.getLocation());
            }
            if (e1.hashCode() != e2.hashCode()) {
                fail("Hash codes differ for events (type: "+e2.getEventType()+"), location "+e1.getLocation());
            }
        }
    }
}
