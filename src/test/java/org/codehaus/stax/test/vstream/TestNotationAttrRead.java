package org.codehaus.stax.test.vstream;

import javax.xml.stream.*;

/**
 * Unit test suite that tests handling of attributes that are declared
 * by DTD to be of type NOTATION.
 *
 * @author Tatu Saloranta
 */
public class TestNotationAttrRead
    extends BaseVStreamTest
{
    /*
    ///////////////////////////////////////
    // Test cases
    ///////////////////////////////////////
     */

    public void testValidAttrDecl()
        throws XMLStreamException
    {
        // Following should be ok; notations have been declared ok
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!NOTATION not1 PUBLIC 'some-public-id'>\n"
            +"<!NOTATION not2 PUBLIC 'other-public-id'>\n"
            +"<!ATTLIST root notation NOTATION (not1 | not2) #IMPLIED>"
            +"]>\n<root />";
        streamThrough(getValidatingReader(XML));

        // Likewise for default values
        XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!NOTATION not1 PUBLIC 'some-public-id'>\n"
            +"<!NOTATION not2 PUBLIC 'other-public-id'>\n"
            +"<!ATTLIST root notation NOTATION (not1 | not2) 'not1'>"
            +"]>\n<root />";
        streamThrough(getValidatingReader(XML));
    }

    /**
     * This unit test verifies that the ordering of ATTLIST declaration
     * and NOTATION(s) it refers to need not be done in a specific
     * order.
     */
    public void testValidUnorderedAttrDecl()
        throws XMLStreamException
    {
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root attr NOTATION (notation) #IMPLIED>"
            +"<!NOTATION notation PUBLIC 'some-public-id'>\n"
            +"]>\n<root />";
        try {
            streamThrough(getValidatingReader(XML));
       } catch (XMLStreamException e) {
            fail("Notation declaration order should not matter, but failed due to: "+e.getMessage());
        }

        // Likewise for default values
        XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root attr NOTATION (notation) 'notation'>"
            +"<!NOTATION notation PUBLIC 'some-public-id'>\n"
            +"]>\n<root />";
        try {
            streamThrough(getValidatingReader(XML));
       } catch (XMLStreamException e) {
            fail("Notation declaration order should not matter, but failed due to: "+e.getMessage());
        }
    }

    public void testInvalidAttrDecl()
        throws XMLStreamException
    {
        // First, let's check that undeclared notation throws an exception
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root notation NOTATION (not1) #IMPLIED>"
            +"]>\n<root />";

        XMLStreamReader sr = getValidatingReader(XML);
        streamThroughFailing(sr, "undeclared notation");

        // And then that only one attribute of type NOTATION is allowed per element
        XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!NOTATION not1 PUBLIC 'some-public-id'>\n"
            +"<!ATTLIST root notation NOTATION (not1) #IMPLIED"
            +"   notation2 NOTATION (not1) #IMPLIED>\n"
            +"]>\n<root />";

        sr = getValidatingReader(XML);
        streamThroughFailing(sr, "more than one notation attribute per element");

        // Also, notation ids can not be duplicates
        XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!NOTATION not1 PUBLIC 'some-public-id'>\n"
            +"<!NOTATION not2 PUBLIC 'some-other-public-id'>\n"
            +"<!ATTLIST root notation NOTATION (not1 | not2 | not1) #IMPLIED>\n"
            +"]>\n<root />";

        sr = getValidatingReader(XML);
        streamThroughFailing(sr, "duplicate notation values enumerated for attribute");
    }

    public void testValidAttrUse()
        throws XMLStreamException
    {
        // Following should be ok, everything defined as required...
        String XML = "<!DOCTYPE elem [\n"
            +"<!NOTATION notVal PUBLIC 'foobar'>\n"
            +"<!NOTATION notVal2 PUBLIC 'whatever'>\n"
            +"<!ELEMENT elem (elem*)>\n"
            +"<!ATTLIST elem not NOTATION (notVal | notVal2) #IMPLIED>\n"
            +"]>\n<elem not='notVal2' />";
        streamThrough(getReader(XML));
    }

    public void testInvalidAttrUse()
        throws XMLStreamException
    {
        // Shouldn't work, undefined notation...
        String XML = "<!DOCTYPE elem [\n"
            +"<!NOTATION notVal PUBLIC 'foobar'>\n"
            +"<!ELEMENT elem (elem*)>\n"
            +"<!ATTLIST elem not NOTATION (notVal) #IMPLIED>\n"
            +"]>\n<elem not='undefdNotValue' />";

        XMLStreamReader sr = getValidatingReader(XML);
        streamThroughFailing(sr, "reference to notation that is not enumerated");

        // and same using default values
        XML = "<!DOCTYPE elem [\n"
            +"<!NOTATION notVal PUBLIC 'foobar'>\n"
            +"<!ELEMENT elem (elem*)>\n"
            +"<!ATTLIST elem not NOTATION (notVal) 'not'>\n"
            +"]>\n<elem />";

        sr = getValidatingReader(XML);
        streamThroughFailing(sr, "reference to notation (via default value) that is not enumerated");
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
