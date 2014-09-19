package stax2.stream;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.*;

import org.codehaus.stax2.*;

/**
 * Unit test suite that tests additional StAX2 namespace information
 * accessors.
 */
public class TestNamespaces
    extends stax2.BaseStax2Test
{
    public void testNonTransientNsCtxt()
        throws XMLStreamException
    {
        String XML =
            "<root xmlns:a='myurl' attr1='value' a:attr1='' xmlns:b='urlforb'>"
            +"<a:branch xmlns='someurl' xmlns:a='whatever' attr='value' b:foo='1' xmlns:c='yetanotherurl'>"
            +"<leaf xmlns='url' />"
            +"</a:branch>"
            +"</root>";
        XMLStreamReader2 sr = getNsReader(XML, true);

        assertEquals(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        NamespaceContext curr = sr.getNamespaceContext();
        assertNotNull(curr);
        checkValidityOfNs1(curr);
        NamespaceContext nc1 = sr.getNonTransientNamespaceContext();
        assertNotNull(nc1);
        checkValidityOfNs1(nc1);

        assertEquals(START_ELEMENT, sr.next());
        assertEquals("branch", sr.getLocalName());
        curr = sr.getNamespaceContext();
        // ok, this should have different settings:
        assertNull(curr.getPrefix("nosuchurl"));
        assertNull(curr.getNamespaceURI("xyz"));
        // bindings from parent:
        assertEquals("a", curr.getPrefix("whatever"));
        assertEquals("whatever", curr.getNamespaceURI("a"));
        assertEquals("b", curr.getPrefix("urlforb"));
        assertEquals("urlforb", curr.getNamespaceURI("b"));
        // and new ones:
        assertEquals("", curr.getPrefix("someurl"));
        assertEquals("c", curr.getPrefix("yetanotherurl"));
        assertEquals("someurl", curr.getNamespaceURI(""));
        assertEquals("yetanotherurl", curr.getNamespaceURI("c"));

        // but nc1 should be non-transient...
        checkValidityOfNs1(nc1);

        assertEquals(START_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());
        // should be non-transient...
        checkValidityOfNs1(nc1);

        assertEquals(END_ELEMENT, sr.next());
        assertEquals(END_ELEMENT, sr.next());
        assertEquals(END_ELEMENT, sr.next());

        // and nc1 should persist still
        checkValidityOfNs1(nc1);
    }

    /*
    //////////////////////////////////////////////////////
    // Internal methods
    //////////////////////////////////////////////////////
     */

    private void checkValidityOfNs1(NamespaceContext nc)
        throws XMLStreamException
    {
        // Ok, we have just 2 bindings here.
        // First, let's check some non-existing bindings
        assertNull(nc.getPrefix("someurl"));
        assertNull(nc.getPrefix("whatever"));
        assertNull(nc.getNamespaceURI("c"));
        // default can be empty or null
        String defNs = nc.getNamespaceURI("");
        if (defNs != null && defNs.length() > 0) {
            fail("Expected default namespace to be null or empty, was '"+defNs+"'");
        }
        // And then the ones that do exist
        assertEquals("a", nc.getPrefix("myurl"));
        assertEquals("myurl", nc.getNamespaceURI("a"));
        assertEquals("b", nc.getPrefix("urlforb"));
        assertEquals("urlforb", nc.getNamespaceURI("b"));
    }

    private XMLStreamReader2 getNsReader(String contents, boolean coalesce)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setNamespaceAware(f, true);
        setCoalescing(f, coalesce);
        setValidating(f, false);
        return constructStreamReader(f, contents);
    }
}
