package org.codehaus.stax.test.vstream;

import javax.xml.stream.*;

/**
 * Unit test suite that tests handling of DTD element declarations.
 */
public class TestDTDElemRead
    extends BaseVStreamTest
{
    /*
    ///////////////////////////////////////////////////////////
    // Element declaration tests:
    ///////////////////////////////////////////////////////////
     */

    public void testValidElementDecl()
        throws XMLStreamException
    {
        /* Following should be ok; it is not an error to refer to
         * undeclared elements... although it is to encounter such
         * undeclared elements in content.
         */
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root (node*)>\n"
            +"]>\n<root />";
        streamThrough(getVReader(XML));
    }

    public void testInvalidElementDecl()
        throws XMLStreamException
    {
        /* Then let's make sure that duplicate element declarations
         * are caught (as they are errors):
         */
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root (node*)>\n"
            +"<!ELEMENT node EMPTY>\n"
            +"<!ELEMENT root (node*)>\n"
            +"]>\n<root />";
        try {
            streamThrough(getVReader(XML));
            fail("Expected an exception for duplicate ELEMENT declaration.");
        } catch (XMLStreamException ex) { // good
        } catch (RuntimeException ex2) { // ok
        } catch (Throwable t) { // not so good
            fail("Expected an XMLStreamException or RuntimeException for duplicate ELEMENT declaration, not: "+t);
        }
    }

    /**
     * Let's ensure basic simple notation declarations are parsed
     * succesfully.
     */
    public void testValidNotationDecl()
        throws XMLStreamException
    {
        // Will need a simple content model, too, since we are validating...
        String XML = "<!DOCTYPE root [\n"
            +"<!NOTATION not1 SYSTEM 'url:dummy'>\n"
            +"<!NOTATION not2 PUBLIC 'public-id'>\n"
            +"<!ELEMENT root EMPTY>\n"
            +"]><root />";
        streamThrough(getVReader(XML));
    }

    /**
     * This unit test checks that there are no duplicate notation declarations
     */
    public void testInvalidDupNotationDecl()
        throws XMLStreamException
    {
        /* Then let's make sure that duplicate element declarations
         * are caught (as they are errors):
         */
        String XML = "<!DOCTYPE root [\n"
            +"<!NOTATION dup SYSTEM 'url:dummy'>\n"
            +"<!NOTATION dup PUBLIC 'public-id'>\n"
            +"<!ELEMENT root EMPTY>\n"
            +"]><root />";
        try {
            streamThrough(getVReader(XML));
            fail("Expected an exception for duplicate NOTATION declaration.");
        } catch (XMLStreamException ex) { // good
        } catch (RuntimeException ex2) { // ok
        } catch (Throwable t) { // not so good
            fail("Expected an XMLStreamException or RuntimeException for duplicate NOTATION declaration, not: "+t);
        }
    }

    /*
    ////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////
     */

    private XMLStreamReader getVReader(String contents)
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
