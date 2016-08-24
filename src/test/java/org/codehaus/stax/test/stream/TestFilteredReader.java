package org.codehaus.stax.test.stream;

import javax.xml.stream.*;

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
    extends BaseStreamTest
{
    /**
     * Simplest possible test: let's only check that we can actually
     * construct an instance with dummy filter that accepts everything,
     * and that we can traverse through all the events as usual.
     */
    public void testCreation()
        throws XMLStreamException
    {
        XMLStreamReader sr = createFilteredReader(new MyFilter(), "<root>text</root>", true);

        assertTokenType(START_DOCUMENT, sr.getEventType());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertEquals(0, sr.getAttributeCount());
        assertNotNull(sr.getName());
        assertTokenType(CHARACTERS, sr.next());
        assertEquals("text", getAndVerifyText(sr));
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertTokenType(END_DOCUMENT, sr.next());
    }

    public void testStartElemFilter1()
        throws XMLStreamException
    {
        XMLStreamReader sr = createFilteredReader
            (getElementFilter("elem"),
             "<elem>text</elem>", true);
        // Should skip the START_DOCUMENT due to filter
        assertTokenType(START_ELEMENT, sr.getEventType());
    }

    public void testStartElemFilter2()
        throws XMLStreamException
    {
        XMLStreamReader sr = createFilteredReader
            (getElementFilter("elem"),
             "<root>...<elem>text</elem></root>", true);
        // Should skip START_DOCUMENT, START_ELEMENT and CHARACTERS
        assertTokenType(START_ELEMENT, sr.getEventType());
        assertEquals("elem", sr.getLocalName());
    }

    /*
    ////////////////////////////////////////
    // Non-test methods
    ////////////////////////////////////////
     */

    private StreamFilter getElementFilter(final String localName)
    {
        return new StreamFilter() {
            @Override
            public boolean accept(XMLStreamReader r) {
                    return r.getEventType() == XMLStreamConstants.START_ELEMENT &&
                        r.getLocalName().equals(localName);
                }
            };
    }
    
    private XMLStreamReader createFilteredReader(StreamFilter filter, String content,
                                                 boolean nsAware)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setNamespaceAware(f, nsAware);
        XMLStreamReader base = constructStreamReader(f, content);
        return f.createFilteredReader(base, filter);
    }

    final static class MyFilter
        implements StreamFilter
    {
        @Override
        public boolean accept(XMLStreamReader reader) {
            return true;
        }
    }
}
