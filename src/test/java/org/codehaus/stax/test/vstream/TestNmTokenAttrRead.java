package org.codehaus.stax.test.vstream;

import javax.xml.stream.*;

/**
 * Unit test suite that tests handling of attributes that are declared
 * by DTD to be of type NMTOKEN or NMTOKENS; such information is only
 * guranteed to be available in validation mode.
 */
public class TestNmTokenAttrRead
    extends BaseVStreamTest
{
    /*
    ///////////////////////////////////////
    // Test cases
    ///////////////////////////////////////
     */

    /**
     * Test case that verifies behaviour of valid NMTOKEN/NMTOKENS
     * attribute declarations.
     */
    public void testValidNmTokenAttrDecl()
        throws XMLStreamException
    {
        // Following should be ok
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root name NMTOKEN #IMPLIED\n"
            +"    names NMTOKENS #IMPLIED>\n"
            +"]>\n<root />";
        streamThrough(getValidatingReader(XML));
    }

    /**
     * Test case that verifies behaviour of invalid NMTOKEN/NMTOKENS
     * attribute declarations.
     */
    public void testInvalidNmTokenAttrDecl()
        throws XMLStreamException
    {
        // ??? Are there any such cases?
    }

    public void testValidNmTokenAttrUse()
        throws XMLStreamException
    {
        String XML = "<!DOCTYPE elem [\n"
            +"<!ELEMENT elem (elem*)>\n"
            +"<!ATTLIST elem name NMTOKEN #IMPLIED>\n"
            +"<!ATTLIST elem names NMTOKENS #IMPLIED>\n"
            +"]>\n<elem name='some-Name'> <elem names='a few names1' /> </elem>";
        streamThrough(getValidatingReader(XML));
    }

    public void testInvalidNmTokenAttrUse()
        throws XMLStreamException
    {
        // Error: invalid NMTOKEN, ? not valid
        String XML = "<!DOCTYPE elem [\n"
            +"<!ELEMENT elem (elem*)>\n"
            +"<!ATTLIST elem name NMTOKEN #IMPLIED>\n"
            +"]>\n<elem name='?foo'/>";
        streamThroughFailing(getValidatingReader(XML),
                             "invalid char ('?') in NMTOKEN");

        // Error: invalid NMTOKENS, / not valid
        XML = "<!DOCTYPE elem [\n"
            +"<!ELEMENT elem (elem*)>\n"
            +"<!ATTLIST elem name NMTOKEN #IMPLIED>\n"
            +"]>\n<elem name='foo foo/bar'/>";
        streamThroughFailing(getValidatingReader(XML),
                             "invalid char ('/') in NMTOKENS");
    }

    /**
     * Unit test that verifies that values of attributes of type NMTOKEN and
     * NMTOKENS will get properly normalized.
     */
    public void testNmTokenAttrNormalization()
        throws XMLStreamException
    {
        String XML = "<!DOCTYPE elem [\n"
            +"<!ELEMENT elem (elem*, elem2?, elem3?)>\n"
            +"<!ATTLIST elem name NMTOKEN #IMPLIED>\n"
            +"<!ATTLIST elem names NMTOKENS #IMPLIED>\n"
            +"<!ELEMENT elem2 EMPTY>\n"
            +"<!ATTLIST elem2 name NMTOKEN 'somename  '>\n"
            +"<!ELEMENT elem3 EMPTY>\n"
            +"<!ATTLIST elem3 names NMTOKENS 'name1\tname2   name3  '>\n"
            +"]>"
            +"<elem name='nmToken  '>"
            +"<elem name='  name' />"
            +"<elem names='first_name  \tsecond last' />"
            +"<elem2 /><elem3 />"
            +"</elem>";
            ;
        XMLStreamReader sr = getValidatingReader(XML);
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("elem", sr.getLocalName());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("nmToken", sr.getAttributeValue(0));
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("name", sr.getAttributeValue(0));
        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("first_name second last", sr.getAttributeValue(0));
        assertTokenType(END_ELEMENT, sr.next());

        // then the defaults
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("elem2", sr.getLocalName());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("name", sr.getAttributeLocalName(0));
        assertEquals("somename", sr.getAttributeValue(0));
        assertTokenType(END_ELEMENT, sr.next());

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("elem3", sr.getLocalName());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("names", sr.getAttributeLocalName(0));
        assertEquals("name1 name2 name3", sr.getAttributeValue(0));
        assertTokenType(END_ELEMENT, sr.next());

        assertTokenType(END_ELEMENT, sr.next());
    }

    /*
    ////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////
     */
}
