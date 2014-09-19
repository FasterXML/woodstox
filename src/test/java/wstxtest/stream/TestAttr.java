package wstxtest.stream;

import javax.xml.stream.*;

import com.ctc.wstx.stax.WstxInputFactory;

public class TestAttr
    extends BaseStreamTest
{
    final static String XML_11_ATTRS =
        "<tag method='a' activeShell='x' source='y' data='z' "
        +"widget='a' length='1' start='2' styledTextNewValue='t' "
        +"replacedText='' styledTextFunction='f' raw='b' />";

    /**
     * This test case was added after encountering a specific problem, which
     * only occurs when many attributes were spilled from main hash area....
     * and that's why exact attribute names do matter.
     */
    public void testManyAttrs()
        throws Exception
    {
        // First non-NS
        XMLStreamReader sr = getReader(XML_11_ATTRS, false);
        streamThrough(sr);
        // Then NS
        sr = getReader(XML_11_ATTRS, true);
        streamThrough(sr);
    }

    /**
     * Tests that the attributes are returned in the document order;
     * an invariant Woodstox honors (even though not mandated by StAX 1.0).
     */
    public void testAttributeOrder()
        throws XMLStreamException
    {
        String XML =  "<?xml version='1.0'?>"
        +"<!DOCTYPE root [\n"
        +"<!ELEMENT root EMPTY>\n"
        +"<!ATTLIST root realDef CDATA 'def'>\n"
        +"<!ATTLIST root attr2 CDATA #IMPLIED>\n"
        +"<!ATTLIST root attr3 CDATA #IMPLIED>\n"
        +"<!ATTLIST root attr4 CDATA #IMPLIED>\n"
        +"<!ATTLIST root defAttr CDATA 'deftoo'>\n"
        +"]>"
        +"<root attr4='4' attr3='3' defAttr='foobar' attr2='2' />"
            ;

        final String[] EXP = {
            "attr4", "4",
            "attr3", "3",
            "defAttr", "foobar",
            "attr2", "2",
            "realDef", "def",
        };

        for (int i = 0; i < 2; ++i) {
            boolean ns = (i > 0);
            // (May) need validating, to get default attribute values
            XMLStreamReader sr = getValidatingReader(XML, ns);

            assertTokenType(START_DOCUMENT, sr.getEventType());
            assertTokenType(DTD, sr.next());
            assertTokenType(START_ELEMENT, sr.next());

            assertEquals(5, sr.getAttributeCount());

            for (int ix = 0, len = sr.getAttributeCount(); ix < len; ++ix) {
                String lname = sr.getAttributeLocalName(ix);
                assertEquals("Attr #"+ix+" name", EXP[ix+ix], lname);
                assertEquals("Attr #"+ix+" value",
                             EXP[ix+ix+1], sr.getAttributeValue(ix));

                assertEquals("Attribute '"+lname+"' wrongly identified WRT isSpecified: ",
                             !lname.equals("realDef"),
                             sr.isAttributeSpecified(ix));
            }

            assertTokenType(END_ELEMENT, sr.next());
        }
    }

    final static String NESTED_XML =
        "<?xml version='1.0'?>"
        +"<!DOCTYPE root [\n"
        +"<!ELEMENT root (branch | leaf)+>\n"
        +"<!ELEMENT branch (#PCDATA | branch | leaf)*>\n"
        +"<!ELEMENT leaf (#PCDATA)>\n"
        +"<!ATTLIST root a CDATA 'rootValue'>\n"
        +"<!ATTLIST root a:b CDATA 'xyz'>\n"
        +"<!ATTLIST root foo CDATA #IMPLIED>\n"
        +"<!ATTLIST root xyz CDATA #IMPLIED>\n"
        +"<!ATTLIST branch a CDATA 'branchValue'>\n"
        +"<!ATTLIST branch a:b CDATA 'xyz'>\n"
        +"<!ATTLIST branch foo CDATA #IMPLIED>\n"
        +"<!ATTLIST branch xyz CDATA #IMPLIED>\n"
        +"<!ATTLIST branch c CDATA #IMPLIED>\n"
        +"<!ATTLIST branch f CDATA #IMPLIED>\n"
        +"<!ATTLIST leaf l CDATA 'leafValue'>\n"
        +"<!ATTLIST leaf a:b CDATA '123'>\n"
        +"<!ATTLIST leaf foo CDATA #IMPLIED>\n"
        +"<!ATTLIST leaf xyz CDATA #IMPLIED>\n"
        +"<!ATTLIST leaf a2 CDATA #IMPLIED>\n"
        +"<!ATTLIST leaf b7 CDATA #IMPLIED>\n"
        +"]>"
        +"<root xmlns:a='ns' xyz='123'>"
        +"<branch a:b='ab'>"
        +"<branch a='value' xyz='456' c='1' f=''>"
        +"<leaf />"
        +"</branch>"
        +"</branch>"
        +"</root>"
            ;

    /**
     * Test that verifies that the counts of attributes, values etc.
     * are consistent within nested elements, and in the presence/absence
     * of DTD default attributes. This is tested since attribute collectors
     * are highly specialized for performance, and small problems might
     * not manifest with simpler tests.
     *<p>
     * Note: one more implicit assumption tested: not only is the ordering
     * of explicit attributes fixed, but so is that of defaulted attributes.
     * Latter always come after explicit ones, and in the same order as
     * they were declared in DTD.
     */
    public void testNestedAttrsNs()
        throws Exception
    {
        XMLStreamReader sr = getValidatingReader(NESTED_XML, true);
        assertTokenType(DTD, sr.next());

        // root elem:
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(3, sr.getAttributeCount());
        assertEquals("123", sr.getAttributeValue(0)); // explicit
        assertEquals("rootValue", sr.getAttributeValue(1)); // default
        assertEquals("xyz", sr.getAttributeValue(2)); // default

        // 1st branch:
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(2, sr.getAttributeCount());
        assertEquals("a", sr.getAttributePrefix(0));
        assertEquals("b", sr.getAttributeLocalName(0));
        assertEquals("ns", sr.getAttributeNamespace(0));
        assertEquals("ab", sr.getAttributeValue(0)); // explicit
        assertEquals("branchValue", sr.getAttributeValue(1)); // default

        // and how about what should NOT be found?
        assertNull(sr.getAttributeValue(null, "xyz"));

        // 2nd branch:
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(5, sr.getAttributeCount());
        assertEquals("a", sr.getAttributeLocalName(0));
        assertEquals("value", sr.getAttributeValue(0)); // explicit
        assertEquals("xyz", sr.getAttributeLocalName(1));
        assertEquals("456", sr.getAttributeValue(1)); // explicit
        assertEquals("c", sr.getAttributeLocalName(2));
        assertEquals("1", sr.getAttributeValue(2)); // explicit
        assertEquals("f", sr.getAttributeLocalName(3));
        assertEquals("", sr.getAttributeValue(3)); // explicit
        assertEquals("a", sr.getAttributePrefix(4));
        assertEquals("ns", sr.getAttributeNamespace(4));
        assertEquals("b", sr.getAttributeLocalName(4));
        assertEquals("xyz", sr.getAttributeValue(4)); // default

        // 1st leaf:
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(2, sr.getAttributeCount());
        assertEquals("leafValue", sr.getAttributeValue(0)); // default
        assertEquals("123", sr.getAttributeValue(1)); // default

        // and how about what should not be found?
        assertNull(sr.getAttributeValue(null, "foo"));
        assertNull(sr.getAttributeValue(null, "a"));
        assertNull(sr.getAttributeValue(null, "c"));
        assertNull(sr.getAttributeValue(null, "f"));
        assertNull(sr.getAttributeValue(null, "xyz"));

        // close leaf
        assertTokenType(END_ELEMENT, sr.next());

        // close 2nd branch
        assertTokenType(END_ELEMENT, sr.next());

        // close 1st branch
        assertTokenType(END_ELEMENT, sr.next());

        // close root
        assertTokenType(END_ELEMENT, sr.next());

        assertTokenType(END_DOCUMENT, sr.next());
    }

    /**
     * This tests handling of ATTLIST declarations for "xmlns:xx" and "xmlns"
     * attributes (that is, namespace declarations). Some legacy DTDs
     * (most notably, XHTML dtd) do this, and Woodstox had some problems with
     * this concept...
     */
    public void testNsAttr()
        throws XMLStreamException
    {
        String XML = 
            "<!DOCTYPE root ["
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root attr CDATA #IMPLIED\n"
            +"   xmlns:ns CDATA 'some-weird-uri'\n"
            +"   xmlns:ns2 CDATA #IMPLIED\n"
            +">\n"
            +"]><root xmlns:ns='foobar' attr='123' />"
            ;
        XMLStreamReader sr = getReader(XML, true);

        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(1, sr.getNamespaceCount());
        assertEquals("ns", sr.getNamespacePrefix(0));
        assertEquals("foobar", sr.getNamespaceURI(0));
        /* This will be 2, if namespace attr declarations are not handled
         * properly...
         */
        assertEquals(1, sr.getAttributeCount());
        assertEquals("attr", sr.getAttributeLocalName(0));
        assertEquals("123", sr.getAttributeValue(0));
        sr.close();
    }

    /*
    //////////////////////////////////////////////////////
    // Internal methods
    //////////////////////////////////////////////////////
     */

    private XMLStreamReader getReader(String contents, boolean nsAware)
        throws XMLStreamException
    {
        WstxInputFactory f = getWstxInputFactory();
        f.getConfig().doValidateWithDTD(false);
        f.getConfig().doSupportNamespaces(nsAware);
        return constructStreamReader(f, contents);
    }

    private XMLStreamReader getValidatingReader(String contents, boolean nsAware)
        throws XMLStreamException
    {
        WstxInputFactory f = getWstxInputFactory();
        f.getConfig().doSupportNamespaces(nsAware);
        f.getConfig().doSupportDTDs(true);
        f.getConfig().doValidateWithDTD(true);
        return constructStreamReader(f, contents);
    }
}

