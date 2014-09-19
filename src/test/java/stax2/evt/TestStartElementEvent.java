package stax2.evt;

import java.io.*;
import java.util.*;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.*;

import stax2.BaseStax2Test;

/**
 * Set of unit tests that checks that {@link {@link StartElement}
 * implementation works as expected.
 */
public class TestStartElementEvent
    extends BaseStax2Test
{
    /**
     * This test was inspired by Woodstox bug [WSTX-188]...
     */
    public void testStartEventAttrs()
        throws XMLStreamException
    {
        final String DOC = "<a>"
            +"<b a=\"aaa\" b=\"bbb\" c=\"ccc\" problem=\"problem\">some content</b>"
            +"<b a=\"aaa\" b=\"bbb\" c=\"ccc\" d=\"ddd\" problem=\"problem\">some content</b>"
            +"</a>";
        XMLInputFactory f = getNewInputFactory();
        XMLEventReader er = f.createXMLEventReader(new StringReader(DOC));

        ArrayList<StartElement> elemEvents = new ArrayList<StartElement>();

        assertTokenType(START_DOCUMENT, er.nextEvent());
        XMLEvent evt = er.nextEvent();
        assertTokenType(START_ELEMENT, evt);
        elemEvents.add(evt.asStartElement());
        evt = er.nextEvent();
        assertTokenType(START_ELEMENT, evt);
        elemEvents.add(evt.asStartElement());

        assertTokenType(CHARACTERS, er.nextEvent());
        assertTokenType(END_ELEMENT, er.nextEvent());
        evt = er.nextEvent();
        assertTokenType(START_ELEMENT, evt);
        elemEvents.add(evt.asStartElement());

        assertTokenType(CHARACTERS, er.nextEvent());
        assertTokenType(END_ELEMENT, er.nextEvent());
        assertTokenType(END_ELEMENT, er.nextEvent());
        er.close();

        /* Ok, got 3 start elements, and accessing the SECOND one triggers
         * the problem
         */
        _verifyAttrCount(elemEvents.get(1), 4, true);
    }

    /*
    /////////////////////////////////////////////////
    // Helper methods
    /////////////////////////////////////////////////
     */

    private void _verifyAttrCount(StartElement start, int expCount, boolean hasProb)
    {
        // First things first: do we have the 'problem' attribute?
        Attribute probAttr = start.getAttributeByName(new QName("problem"));
        if (hasProb) {
            assertNotNull(probAttr);
        } else {
            assertNull(probAttr);
        }

        Iterator<?> it = start.getAttributes();
        int count = 0;
        Map<QName,String> attrs = new HashMap<QName,String>();

        // First, collect the attributes
        while (it.hasNext()) {
            ++count;
            Attribute attr = (Attribute) it.next();
            attrs.put(attr.getName(), attr.getValue());
        }

        assertEquals(expCount, attrs.size());

        // Then verify we can access them ok
        //for (Map.Entry<QName,String> en : attrs) {

        Iterator<Map.Entry<QName,String>> it2 = attrs.entrySet().iterator();
        while (it2.hasNext()) {
            Map.Entry<QName,String> en = it2.next();
            QName key = en.getKey();
            String value = en.getValue();

            // should find it via StartElement too
            Attribute attr = start.getAttributeByName(key);
            assertNotNull(attr);
            assertEquals(value, attr.getValue());

            // .. how about a bogus name?
            assertNull(start.getAttributeByName(new QName("bogus+"+key.getLocalPart())));
        }
    }
}
