package wstxtest.stream;

import javax.xml.stream.*;

/**
 * This is a small test suite has some checks for features of xml 1.1
 * that are different from those of 1.0.
 */
public class TestXml11
    extends BaseStreamTest
{
    /**
     * This test checks that it is illegal to try to unbind a prefix;
     * only default namespace can be unbound.
     */
    public void testInvalidUnbinding()
        throws XMLStreamException
    {
        final String XML =
            "<?xml version='1.0'?>"
            +"<foo xmlns:a='http://example.org/namespace'>"
            +"<bar xmlns:a=''/></foo>"
            ;
        XMLStreamReader sr = getReader(XML);
        assertTokenType(START_ELEMENT, sr.next());
        // This should result in an exception:
        try {
            /*int type =*/ sr.next(); // usually fails here
            /*type =*/ sr.next(); // but if not, at least here (END_ELEMENT)
            fail("Expected a stream exception due to namespace unbind for xml 1.0 document");
        } catch (XMLStreamException sex) {
            ; //good
        }
    }

    /**
     * Test case adapted from XMLTest (based on
     * xmlconf/eduni/namespaces/1.1/004.xml)
     */
    public void testValidRebinding()
        throws XMLStreamException
    {
        final String XML =
            "<?xml version='1.1'?>"
+"<foo xmlns:a='http://ns1'>"
+"<bar xmlns:a=''>"
+"<foo xmlns:a='http://ns2' a:attr='1'/>"
+"</bar>"
+"</foo>"
            ;
        XMLStreamReader sr = getReader(XML);
        assertTokenType(START_ELEMENT, sr.next()); // foo
        assertEquals("foo", sr.getLocalName());

        assertTokenType(START_ELEMENT, sr.next()); // bar
        assertEquals("bar", sr.getLocalName());

        assertTokenType(START_ELEMENT, sr.next()); // foo (inner)
        assertEquals("foo", sr.getLocalName());

        assertTokenType(END_ELEMENT, sr.next()); // /foo (inner)
        assertEquals("foo", sr.getLocalName());
        assertTokenType(END_ELEMENT, sr.next()); // /bar
        assertEquals("bar", sr.getLocalName());
        assertTokenType(END_ELEMENT, sr.next()); // /foo
        assertEquals("foo", sr.getLocalName());
    }

    /**
     * Test case adapted from XMLTest (based on
     * xmlconf/eduni/namespaces/1.1/005.xml)
     */
    public void testInvalidUseOfUnbound()
        throws XMLStreamException
    {
        final String XML =
            "<?xml version='1.1'?>"
            +"<foo xmlns:a='http://example.org/namespace'>"
            +"<a:bar xmlns:a=''/></foo>"
            ;
        XMLStreamReader sr = getReader(XML);
        assertTokenType(START_ELEMENT, sr.next());
        // This should result in an exception:
        try {
            sr.next(); // usually fails here
            sr.next(); // but if not, at least here
            fail("Expected a stream exception due to a reference to an explicitly unbound prefix 'a'");
        } catch (XMLStreamException sex) {
            ; //good
        }
    }

    /*
    //////////////////////////////////////////////////
    // Helper methods
    //////////////////////////////////////////////////
     */

    private XMLStreamReader getReader(String contents)
        throws XMLStreamException
    {
        XMLInputFactory f = getWstxInputFactory();
        setNamespaceAware(f, true);
        setCoalescing(f, false);
        setValidating(f, false);
        setSupportDTD(f, false);
        return constructStreamReader(f, contents);
    }
}
