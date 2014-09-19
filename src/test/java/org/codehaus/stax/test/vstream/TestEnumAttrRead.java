package org.codehaus.stax.test.vstream;

import javax.xml.stream.*;

/**
 * Unit test suite that tests handling of attributes that are declared
 * by DTD to be of type NOTATION.
 */
public class TestEnumAttrRead
    extends BaseVStreamTest
{
    public void testValidAttrDecl()
        throws XMLStreamException
    {
        // Ok, just a simple declaration...
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root attr (enum) #IMPLIED\n"
            +" attr2 (enum|enum2) #IMPLIED>\n"
            +"]>\n<root />";
        streamThrough(getValidatingReader(XML, true));
    }


    public void testValidAttrDecl2()
        throws XMLStreamException
    {
        /* Following should be ok, only problematic if DTD parser is
         * either trying to match SGML comments, or otherwise unhappy
         * about hyphen starting an NMTOKEN.
         */
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root attr (- | on | -- | off-white) #IMPLIED>\n"
            +"]>\n<root />";
        streamThrough(getReader(XML));
    }

    public void testInvalidAttrDecl()
        throws XMLStreamException
    {
        // Duplicates are not allowed
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root attr (enum | enum2 | enum) #IMPLIED>\n"
            +"]>\n<root />";
        streamThroughFailing(getValidatingReader(XML, true),
                             "duplicate enumeration in attribute declaration");
    }

    public void testValidAttrUse()
        throws XMLStreamException
    {
        // Ok, just a simple declaration...
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root attr (  enum) #IMPLIED\n"
            +" attr2 (enum | enum2  ) #IMPLIED>\n"
            +"]><root attr2='enum2' />";

        XMLStreamReader sr = getValidatingReader(XML, true);
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());

        assertEquals(1, sr.getAttributeCount());
        assertEquals("attr2", sr.getAttributeLocalName(0));
        assertEquals("enum2", sr.getAttributeValue(0));
    }

    /**
     * Unit test that verifies that values of attributes of type ID 
     * will get properly normalized.
     */
    public void testEnumAttrNormalization()
        throws XMLStreamException
    {
        String XML = "<!DOCTYPE elem [\n"
            +"<!ELEMENT elem (elem*)>\n"
            +"<!ATTLIST elem enumAttr (enum | enum2|last  ) #IMPLIED>\n"
            +"]>"
            +"<elem enumAttr='enum2  '>"
            +"<elem enumAttr='   enum' />"
            +"<elem enumAttr='\tlast' />"
            +"</elem>";
            ;
        XMLStreamReader sr = getValidatingReader(XML);
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("enum2", sr.getAttributeValue(0));
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("enum", sr.getAttributeValue(0));
        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("last", sr.getAttributeValue(0));
        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(END_ELEMENT, sr.next());
    }

    /*
    ////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////
     */

    private XMLStreamReader getReader(String contents)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setCoalescing(f, false); // shouldn't really matter
        //setNamespaceAware(f, nsAware);
        setSupportDTD(f, true);
        // Let's make sure DTD is really parsed?
        setValidating(f, true);
        return constructStreamReader(f, contents);
    }
}
