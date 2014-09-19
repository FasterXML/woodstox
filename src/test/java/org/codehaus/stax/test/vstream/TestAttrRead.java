package org.codehaus.stax.test.vstream;

import javax.xml.stream.*;

/**
 * Unit test suite that tests basic handling of attributes; aspects
 * that do not depend on actual concrete type.
 */
public class TestAttrRead
    extends BaseVStreamTest
{
    /*
    ///////////////////////////////////////////////////////////
    // Attribute declaration tests:
    ///////////////////////////////////////////////////////////
     */

    /**
     * Simple tests for generic valid attribute declarations; using
     * some constructs that can be warned about, but that are not
     * erroneous.
     */
    public void testValidAttrDecl()
        throws XMLStreamException
    {
        /* First; declaring attributes for non-declared elements is
         * not an error
         */
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST element attr CDATA #IMPLIED>\n"
            +"]>\n<root />";
        streamThrough(getValidatingReader(XML, true));

        /* Then, declaring same attribute more than once is not an
         * error; first one is binding (note: should test that this
         * indeed happens, via attribute property inspection?)
         */
        XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root attr CDATA #IMPLIED>\n"
            +"<!ATTLIST root attr CDATA #IMPLIED>\n"
            +"<!ATTLIST root attr CDATA #IMPLIED>\n"
            +"]>\n<root />";
        streamThrough(getValidatingReader(XML, true));
    }

    /**
     * Unit test that verifies that the attribute type declaration information
     * is properly parsed and accessible via stream reader.
     */
    public void testAttributeTypes()
        throws XMLStreamException
    {
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root cdata CDATA #IMPLIED>\n"
            +"<!ATTLIST root id ID #IMPLIED>\n"
            +"<!ATTLIST root nmtoken NMTOKEN #IMPLIED>\n"
            +"<!ATTLIST root nmtokens NMTOKENS #IMPLIED>\n"
            +"]>"
            +"<root"
            +" cdata='content'"
            +" id='node1'"
            +" nmtoken='token'"
            +" nmtokens='token1 token2'"
            +"/>";
        /* Could/should extend to cover all types... but this should be
         * enough to at least determined reader does pass through the
         * type info (instead of always returning CDATA)
         */
        XMLStreamReader sr = getValidatingReader(XML, true);

        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());

        assertEquals(4, sr.getAttributeCount());
        for (int i = 0; i < 4; ++i) {
            String ln = sr.getAttributeLocalName(i);
            String type = sr.getAttributeType(i);
            String expType = ln.toUpperCase();
            assertNotNull("Attribute type should never be null; CDATA should be returned if information not known/available");
            assertEquals("Incorrect attribute type for attribute '"+ln+"'",
                         expType, type);
        }
        assertTokenType(END_ELEMENT, sr.next());
    }

    public void testValidRequiredAttr()
        throws XMLStreamException
    {
        // this should be valid:
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root attr CDATA #REQUIRED>\n"
            +"]><root attr='value' />";
        XMLStreamReader sr = getValidatingReader(XML, true);
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());

        assertEquals(1, sr.getAttributeCount());
        assertEquals("attr", sr.getAttributeLocalName(0));
        assertEquals("value", sr.getAttributeValue(0));
    }

    public void testInvalidRequiredAttr()
        throws XMLStreamException
    {
        // Invalid as it's missing the required attribute
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root attr CDATA #REQUIRED>\n"
            +"]>\n<root />";
        streamThroughFailing(getValidatingReader(XML, true),
                             "Missing required attribute value");
    }

    public void testOkFixedAttr()
        throws XMLStreamException
    {
        // Ok to omit altogether
        String XML = "<!DOCTYPE elem [\n"
            +"<!ELEMENT elem EMPTY>\n"
            +"<!ATTLIST elem attr CDATA #FIXED 'fixed'>\n"
            +"]><elem/>";
        // But if so, should get the default value
        XMLStreamReader sr = getValidatingReader(XML, true);
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("Should have 1 attribute; 'elem' had #FIXED default value", 1, sr.getAttributeCount());
        assertEquals("attr", sr.getAttributeLocalName(0));
        assertEquals("fixed", sr.getAttributeValue(0));
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();

        // Or to use fixed value
        XML = "<!DOCTYPE elem [\n"
            +"<!ELEMENT elem EMPTY>\n"
            +"<!ATTLIST elem attr CDATA #FIXED 'fixed'>\n"
            +"]><elem attr='fixed'/>";
        sr = getValidatingReader(XML, true);
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("attr", sr.getAttributeLocalName(0));
        assertEquals("fixed", sr.getAttributeValue(0));
        assertTokenType(END_ELEMENT, sr.next());
    }

    public void testInvalidFixedAttr()
        throws XMLStreamException
    {
        // Not ok to have any other value, either completely different
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root attr CDATA #FIXED 'fixed'>\n"
            +"]>\n<root attr='wrong'/>";
        streamThroughFailing(getValidatingReader(XML),
                             "fixed attribute value not matching declaration");

        // Or one with extra white space (CDATA won't get fully normalized)
        XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root attr CDATA #FIXED 'fixed'>\n"
            +"]>\n<root attr=' fixed '/>";
        streamThroughFailing(getValidatingReader(XML),
                             "fixed attribute value not matching declaration");
    }

    /**
     * Unit test that verifies that the default attribute values are properly
     * used on validating mode.
     */
    public void testDefaultAttr()
        throws XMLStreamException
    {
        // Let's verify we get default value
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root attr CDATA 'default'>\n"
            +"]><root/>";
        XMLStreamReader sr = getValidatingReader(XML, true);
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());

        assertEquals(1, sr.getAttributeCount());
        assertEquals("attr", sr.getAttributeLocalName(0));
        assertEquals("default", sr.getAttributeValue(0));
    }

    /**
     * Test for proper handling for multiple attribute declarations for
     * a single attribute. This is legal, although discouraged (ie. parser
     * can issue a non-fatal warning): but if used, the first definition
     * should stick. Let's test for both default values and types.
     */
    public void testMultipleDeclForSingleAttr()
        throws XMLStreamException
    {
        // Let's verify we get the right default value
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root attr CDATA 'val1'>\n"
            +"<!ATTLIST root attr CDATA 'val2'>\n"
            +"]><root/>";
        XMLStreamReader sr = getValidatingReader(XML, true);
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("attr", sr.getAttributeLocalName(0));
        assertEquals("val1", sr.getAttributeValue(0));

        // And then let's test that the type is correct as well
        XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root attr NMTOKEN #IMPLIED>\n"
            +"<!ATTLIST root attr CDATA #IMPLIED>\n"
            +"]><root attr='valX'/>";
        sr = getValidatingReader(XML, true);
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("attr", sr.getAttributeLocalName(0));
        assertEquals("valX", sr.getAttributeValue(0));
        assertEquals("NMTOKEN", sr.getAttributeType(0));
    }

    /*
    ////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////
     */
}
