package org.codehaus.stax.test.evt;

import javax.xml.stream.*;
import javax.xml.stream.events.*;

/**
 * Simple unit test suite that tries to if filtered stream readers are
 * constructed and can be used.
 *<p>
 * One thing to note, though, is that the StAX specs do not tell much
 * anything about expected ways that the implementation is to deal with
 * problems resulting from filtering END_DOCUMENT event and so forth.
 *
 * @author Tatu Saloranta
 */
public class TestFilteredReader
    extends BaseEventTest
{
    /**
     * Simplest possible test: let's only check that we can actually
     * construct an instance with dummy filter that accepts everything,
     * and that we can traverse through all the events as usual.
     */
    public void testCreation()
        throws XMLStreamException
    {
        XMLEventReader er = createFilteredReader(new MyFilter(), "<root>text</root>", true);

        assertTokenType(START_DOCUMENT, er.nextEvent().getEventType());
        XMLEvent evt = er.nextEvent();
        assertTokenType(START_ELEMENT, evt.getEventType());
        assertNotNull(evt.asStartElement().getName());
        assertTokenType(CHARACTERS, er.nextEvent().getEventType());
        assertTokenType(END_ELEMENT, er.nextEvent().getEventType());
        assertTokenType(END_DOCUMENT, er.nextEvent().getEventType());
    }

    /*
    ////////////////////////////////////////
    // Non-test methods
    ////////////////////////////////////////
     */
    
    private XMLEventReader createFilteredReader(EventFilter filter, String content,
                                                boolean nsAware)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setNamespaceAware(f, nsAware);
        XMLEventReader base = constructEventReader(f, content);
        return f.createFilteredReader(base, filter);
    }

    final static class MyFilter
        implements EventFilter
    {
        @Override
        public boolean accept(XMLEvent event) {
            return true;
        }
    }
}

