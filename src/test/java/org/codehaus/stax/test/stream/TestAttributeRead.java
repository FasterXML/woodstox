package org.codehaus.stax.test.stream;

import javax.xml.namespace.QName;
import javax.xml.stream.*;

/**
 * Unit test suite that tests handling of the XML attributes, both
 * in namespace aware and non-namespace modes, including ensuring
 * that values are properly normalized with regards to white space.
 * There are tests for both well-formed and non-wellformed cases.
 *
 * @author Tatu Saloranta
 */
public class TestAttributeRead
    extends BaseStreamTest
{
    final String VALID_XML1
        = "<root a='r&amp;b' a:b=\"&quot;\" xmlns:a='url' />";

    final String VALID_XML2
        = "<root a:b=\"&quot;\" xmlns:a='url' />";

    final String VALID_XML3
        = "<root><e1 a='123' /><e2 /></root>";

    public void testValidNsAttrsByName()
        throws XMLStreamException
    {
        XMLStreamReader sr = getReader(VALID_XML1, true);
        assertEquals(START_ELEMENT, sr.next());
        assertEquals(1, sr.getNamespaceCount());
        assertEquals(2, sr.getAttributeCount());

        assertEquals("r&b", sr.getAttributeValue(null, "a"));
        assertEquals("\"", sr.getAttributeValue("url", "b"));

        // Shoulnd't allow using prefix instead of URI
        String val = sr.getAttributeValue("a", "b");
        assertNull("Should get null, not '"+val+"'", val);
        val = sr.getAttributeValue("", "b");
        assertNull("Should get null, not '"+val+"'", val);

        assertEquals(END_ELEMENT, sr.next());
        assertEquals(END_DOCUMENT, sr.next());
    }

    /**
     * Additional unit test that verifies that empty namespace URI
     * can be used as expected
     */
    public void testValidNsAttrsByName2()
        throws XMLStreamException
    {
        XMLStreamReader sr = getReader("<root attr='1' />", true);
        assertEquals(START_ELEMENT, sr.next());
        assertEquals(0, sr.getNamespaceCount());
        assertEquals(1, sr.getAttributeCount());

        /* Both null and "" should work as well
         * (note: specs are bit confusing and contradictory wrt
         * how null is to be handled, but in this case all interpretations
         * would produce same result)
         */
        assertEquals("1", sr.getAttributeValue(null, "attr"));
        assertEquals("1", sr.getAttributeValue("", "attr"));

        assertNull(sr.getAttributeValue(null, "b"));
        assertNull(sr.getAttributeValue("", "b"));

        assertEquals(END_ELEMENT, sr.next());
        assertEquals(END_DOCUMENT, sr.next());
    }

    // [woodstox-core#53]: oddly enough, `null` for namespace should, as per Javadocs,
    // IGNORE any namespace information from matches.
    public void testValidNsAttrsIgnoreNamespace()
        throws XMLStreamException
    {
        XMLStreamReader sr = getReader("<root xmlns:ns='foo:bar' ns:a='1' b='2' />", true);
        assertEquals(START_ELEMENT, sr.next());
        assertEquals(1, sr.getNamespaceCount());
        assertEquals(2, sr.getAttributeCount());

        assertEquals("1", sr.getAttributeValue("foo:bar", "a"));
        assertEquals("1", sr.getAttributeValue(null, "a"));
        assertNull(sr.getAttributeValue("bar:foo", "a"));

        assertEquals("2", sr.getAttributeValue("", "b"));
        assertEquals("2", sr.getAttributeValue(null, "b"));
        assertNull(sr.getAttributeValue("foo:bar", "b"));

        assertEquals(END_ELEMENT, sr.next());
        assertEquals(END_DOCUMENT, sr.next());
    }

    public void testValidNsAttrsByIndex()
        throws XMLStreamException
    {
        XMLStreamReader sr = getReader(VALID_XML1, true);
        assertEquals(START_ELEMENT, sr.next());
        assertEquals(1, sr.getNamespaceCount());
        assertEquals(2, sr.getAttributeCount());

        /* Note: we can not assume on stream reader returning attributes
         * in document order... most will do that, but it's not a
         * strict StAX requirement.
         */

        String ln1 = sr.getAttributeLocalName(0);

        int index1 = 0;
        int index2 = 1;

        if (ln1.equals("a")) {
            ;
        } else if (ln1.equals("b")) {
            index1 = 1;
            index2 = 0;
        } else {
            fail("Unexpected local name for attribute #0; expected either 'a' or 'b'; got '"+ln1+"'.");
        }
        
        assertEquals("a", sr.getAttributeLocalName(index1));
        assertEquals("b", sr.getAttributeLocalName(index2));
        
        assertEquals("r&b", sr.getAttributeValue(index1));
        assertEquals("\"", sr.getAttributeValue(index2));

        assertNoAttrPrefix(sr.getAttributePrefix(index1));
        assertEquals("a", sr.getAttributePrefix(index2));
        assertNoAttrNamespace(sr.getAttributeNamespace(index1));
        assertEquals("url", sr.getAttributeNamespace(index2));
    }

    public void testValidNsAttrs2()
        throws XMLStreamException
    {
        XMLStreamReader sr = getReader(VALID_XML3, true);
        assertEquals(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());

        assertEquals(START_ELEMENT, sr.next());
        assertEquals("e1", sr.getLocalName());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("a", sr.getAttributeLocalName(0));
        assertEquals("123", sr.getAttributeValue(0));
        assertEquals("123", sr.getAttributeValue(null, "a"));
        assertEquals(END_ELEMENT, sr.next());
        assertEquals("e1", sr.getLocalName());

        assertEquals(START_ELEMENT, sr.next());
        assertEquals("e2", sr.getLocalName());
        assertEquals(0, sr.getAttributeCount());
        String val = sr.getAttributeValue(null, "a");
        if (val != null) {
            fail("Should not find a value for attribute 'a', found '"+val+"'");
        }

        assertEquals(END_ELEMENT, sr.next());
        assertEquals("e2", sr.getLocalName());

        assertEquals(END_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        sr.close();
    }

    public void testValidNsAttrNsInfo()
        throws XMLStreamException
    {
        XMLStreamReader sr = getReader
            ("<root a='xyz' xmlns:b='http://foo'><leaf b:attr='1' /></root>",
             true);

        assertEquals(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertEquals(1, sr.getNamespaceCount());
        assertEquals(1, sr.getAttributeCount());
        assertNoAttrPrefix(sr.getAttributePrefix(0));
        assertNoAttrNamespace(sr.getAttributeNamespace(0));
        assertEquals("xyz", sr.getAttributeValue(0));

        assertEquals(START_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());
        assertEquals(0, sr.getNamespaceCount());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("b", sr.getAttributePrefix(0));
        assertEquals("http://foo", sr.getAttributeNamespace(0));
        assertEquals("1", sr.getAttributeValue(0));

        assertEquals(END_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());
        assertEquals(END_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());

        assertEquals(END_DOCUMENT, sr.next());
    }

    public void testValidNonNsAttrs()
        throws XMLStreamException
    {
        XMLStreamReader sr = getReader(VALID_XML1, false);

        // Does the impl support non-ns mode?
        if (sr == null) { // nope!
            return;
        }

        assertEquals(START_ELEMENT, sr.next());
        assertEquals(0, sr.getNamespaceCount());
        assertEquals(3, sr.getAttributeCount());

        assertEquals("r&b", sr.getAttributeValue(null, "a"));
        assertEquals("\"", sr.getAttributeValue(null, "a:b"));

        assertEquals(END_ELEMENT, sr.next());
        assertEquals(END_DOCUMENT, sr.next());
    }

    public void testValidNonNsAttrsByIndex()
        throws XMLStreamException
    {
        // = "<root a:b=\"&quot;\" xmlns:a='url' />";
        XMLStreamReader sr = getReader(VALID_XML2, false);

        // Does the impl support non-ns mode?
        if (sr == null) { // nope!
            return;
        }

        assertEquals(START_ELEMENT, sr.next());
        assertEquals(0, sr.getNamespaceCount());
        assertEquals(2, sr.getAttributeCount());

        /* Note: we can not assume on stream reader returning attributes
         * in document order... most will do that, but it's not a
         * strict StAX requirement.
         */

        String ln1 = sr.getAttributeLocalName(0);

        int index1 = 0;
        int index2 = 1;

        if (ln1.equals("a:b")) {
            ;
        } else if (ln1.equals("xmlns:a")) {
            index1 = 1;
            index2 = 0;
        } else {
            fail("Unexpected local name for attribute #0; expected either 'a:b' or 'xmlns:a'; got '"+ln1+"'.");
        }
        
        assertEquals("a:b", sr.getAttributeLocalName(index1));
        assertEquals("xmlns:a", sr.getAttributeLocalName(index2));
        
        assertEquals("\"", sr.getAttributeValue(index1));
        assertEquals("url", sr.getAttributeValue(index2));
        assertNoAttrPrefix(sr.getAttributePrefix(index1));
        assertNoAttrPrefix(sr.getAttributePrefix(index2));

        assertNoAttrNamespace(sr.getAttributeNamespace(index1));
        assertNoAttrNamespace(sr.getAttributeNamespace(index2));
    }

    public void testInvalidAttrNames()
        throws XMLStreamException
    {
        // First NS-aware, then non-NS:
        XMLStreamReader sr = getReader("<tree .attr='value' />", true);
        if (sr != null) {
            streamThroughFailing(sr, "invalid attribute name; can not start with '.'");
        }
        sr = getReader("<tree .attr='value' />", false);
        if (sr != null) {
            streamThroughFailing(sr, "invalid attribute name; can not start with '.'");
        }
        sr = getReader("<tree attr?='value' />", false);
        if (sr != null) {
            streamThroughFailing(sr, "invalid attribute name can not contain '?'");
        }
        sr = getReader("<tree attr?='value' />", true);
        if (sr != null) {
            streamThroughFailing(sr, "invalid attribute name can not contain '?'");
        }
    }

    public void testInvalidAttrValue()
        throws XMLStreamException
    {
        for (int i = 0; i < 2; ++i) {
            boolean ns = (i > 0);
            // Invalid, '<' not allowed in attribute value
            String XML = "<root a='<' />";

            XMLStreamReader sr = getReader(XML, ns);
            // Does the impl support non-ns mode?
            if (sr == null) { // nope! shouldn't test...
                continue;
            }
            streamThroughFailing(sr, "unquoted '<' in attribute value");
            
            XML = "<root a />";
            streamThroughFailing(getReader(XML, ns),
                                 "missing value for attribute");
        }
    }

    /**
     * This tests that spaces are actually needed between attributes...
     */
    public void testInvalidAttrSpaces()
        throws XMLStreamException
    {
        for (int i = 0; i < 2; ++i) {
            boolean ns = (i > 0);
            String XML = "<root a='b'b='a' />";
            XMLStreamReader sr = getReader(XML, ns);
            // Does the impl support non-ns mode?
            if (sr == null) { // nope! shouldn't test...
                continue;
            }
            streamThroughFailing(sr, "missing space between attributes");
            XML = "<root a=\"b\"b=\"a\" />";
            streamThroughFailing(getReader(XML, ns),
                                 "missing space between attributes");
        }
    }

    public void testInvalidNsAttrDup()
        throws XMLStreamException
    {
        // Invalid; straight duplicate attrs:
        String XML = "<root xmlns:a='xxx' a:attr='1' a:attr='2' />";
        streamThroughFailing(getReader(XML, true), "duplicate attributes");
        // Invalid; sneakier duplicate attrs:
        XML = "<root xmlns:a='xxx' xmlns:b='xxx' a:attr='1' b:attr='2' />";
        streamThroughFailing(getReader(XML, true),
                             "duplicate attributes (same URI, different prefix)");

        /* Also, let's add some more, in case parser just checks for adjacent
         * ones, or has special cases for small number of attrs...
         */
        XML = "<a xmlns:a='abc' a:a='1' a:b='2' a:c='3' a:d='1' a:e='' a:b='2' />";
        streamThroughFailing(getReader(XML, true), "duplicate attributes");

        XML = "<a xmlns:a='abc' xmlns:b='abc' a:a='1' a:b='2' a:c='3' a:d='1' a:e='' b:c='2' />";
        streamThroughFailing(getReader(XML, true),
                             "duplicate attributes (same URI, different prefix)");
    }
    
    public void testInvalidNonNsAttrDup()
        throws XMLStreamException
    {
        // Invalid; duplicate attrs even without namespaces
        String XML = "<root xmlns:a='xxx' a:attr='1' a:attr='2' />";
        XMLStreamReader sr = getReader(XML, false);
        if (sr != null) {
            streamThroughFailing(sr, "duplicate attributes");
        }
        
        // Valid when namespaces not enabled:
        XML = "<root xmlns:a='xxx' xmlns:b='xxx' a:attr='1' b:attr='2' />";
        try {
            sr = getReader(XML, false);
            // Does the impl support non-ns mode?
            if (sr == null) { // nope! shouldn't test...
                return;
            }
            streamThrough(sr);
        } catch (Exception e) {
            fail("Didn't expect an exception when namespace support not enabled: "+e);
        }
    }

    /**
     * This test verifies that handling of multiple attributes should
     * work as expected
     */
    public void testManyAttrsNs()
        throws Exception
    {
        doTestManyAttrs(true);
    }

    public void testManyAttrsNonNs()
        throws Exception
    {
        doTestManyAttrs(false);
    }

    /**
     * Unit test that verifies that by-index attribute accessors
     * will throw proper exception when using invalid index to
     * access data (value, name, uri/prefix).
     */
    public void testInvalidAccessByIndex()
        throws Exception
    {
        String XML = "<root attr1='1' attr2='2'><leaf attr3='3' /></root>";

        XMLStreamReader sr = getReader(XML, true);
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(2, sr.getAttributeCount());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(1, sr.getAttributeCount());

        try {
            String str = sr.getAttributeValue(1);
            fail("Expected IllegalArgumentException for sr.getAttributeValue() with illegal index, no exception, value = ["+str+"]");
        } catch (IllegalArgumentException iae) {
            ; // good
        } catch (Exception e) {
            // almost ok
            fail("Expected IllegalArgumentException for sr.getAttributeValue() with illegal index, got: "+e);
        }

        try {
            QName n = sr.getAttributeName(1);
            fail("Expected IllegalArgumentException for sr.getAttributeName() with illegal index, no exception, name = ["+n+"]");
        } catch (IllegalArgumentException iae) {
            ; // good
        } catch (Exception e) {
            // almost ok
            fail("Expected IllegalArgumentException for sr.getAttributeName() with illegal index, got: "+e);
        }

        try {
            String n = sr.getAttributeLocalName(1);
            fail("Expected IllegalArgumentException for sr.getAttributeLocalName() with illegal index, no exception, got = ["+n+"]");
        } catch (IllegalArgumentException iae) {
            ; // good
        } catch (Exception e) {
            // almost ok
            fail("Expected IllegalArgumentException for sr.getAttributeLocalName() with illegal index, got: "+e);
        }

        try {
            String n = sr.getAttributeNamespace(1);
            fail("Expected IllegalArgumentException for sr.getAttributeNamespace() with illegal index, no exception, got = ["+n+"]");
        } catch (IllegalArgumentException iae) {
            ; // good
        } catch (Exception e) {
            // almost ok
            fail("Expected IllegalArgumentException for sr.getAttributeNamespace() with illegal index, got: "+e);
        }

        try {
            String n = sr.getAttributePrefix(1);
            fail("Expected IllegalArgumentException for sr.getAttributePrefix() with illegal index, no exception, got = ["+n+"]");
        } catch (IllegalArgumentException iae) {
            ; // good
        } catch (Exception e) {
            // almost ok
            fail("Expected IllegalArgumentException for sr.getAttributePrefix() with illegal index, got: "+e);
        }

        sr.close();
    }

    /*
    ////////////////////////////////////////
    // Private methods, other
    ////////////////////////////////////////
     */

    final static String[] ATTR11_NAMES = new String[] {
        "method", "activeShell", "source", "data",
        "widget", "length", "start", "styledTextNewValue",
        "replacedText", "styledTextFunction", "raw"
    };
    final static String[] ATTR11_VALUES = new String[] {
        "a", "x", "y", "z",
        "a", "1", "2", "t",
        "", "f", "b"
    };

    private String get11AttrDoc()
    {
        StringBuffer sb = new StringBuffer();
        sb.append("<root");
        for (int i = 0; i < ATTR11_NAMES.length; ++i) {
            sb.append(' ');
            sb.append(ATTR11_NAMES[i]);
            sb.append('=');
            sb.append(((i & 1) == 0) ? '"' : '\'');
            // Assuming no quoting needed
            sb.append(ATTR11_VALUES[i]);
            sb.append(((i & 1) == 0) ? '"' : '\'');
        }
        sb.append(" />");
        return sb.toString();
    }

    private void doTestManyAttrs(boolean ns)
        throws XMLStreamException
    {
        XMLStreamReader sr = getReader(get11AttrDoc(), ns);
        // If non-ns mode not available, that's ok; we'll skip the test
        if (sr == null) {
            return;
        }
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(11, sr.getAttributeCount());
        /* Let's verify we can find them all
         */
        for (int i = ATTR11_NAMES.length; --i >= 0; ) {
            String name = ATTR11_NAMES[i];
            String value = ATTR11_VALUES[i];
            // First, via string constant:
            assertEquals(value, sr.getAttributeValue(null, name));
            // Then via new String (non-interned)
            assertEquals(value, sr.getAttributeValue(null, ""+name));

            // Then that non-existing ones are not found:
            String start = name.substring(0, 1);
            assertNull(value, sr.getAttributeValue(null, name+start));
            assertNull(value, sr.getAttributeValue(null, start+name));
        }
        assertTokenType(END_ELEMENT, sr.next());
    }

    /**
     * @return Stream reader constructed if initialization succeeded (all
     *   setting supported by the impl); null if some settings (namespace
     *   awareness) not supported.
     */
    private XMLStreamReader getReader(String contents, boolean nsAware)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        if (!setNamespaceAware(f, nsAware)) {
            return null;
        }

        setCoalescing(f, true); // shouldn't matter
        setValidating(f, false);
        return constructStreamReader(f, contents);
    }
}
