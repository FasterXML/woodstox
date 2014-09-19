package org.codehaus.stax.test.stream;

import javax.xml.stream.*;

/**
 * Unit tests related to handling of attributes that depend on DTD subsets.
 *
 * @author Tatu Saloranta
 */
public class TestAttributeDTD
    extends BaseStreamTest
{

    /**
     * Test to make sure that quotes can be used in attribute values
     * via entity expansion
     */
    final String VALID_ATTRS_WITH_QUOTES
        = "<!DOCTYPE tree [\n"
        + "<!ENTITY val1 '\"quoted\"'>\n"
        + "<!ENTITY val2 \"'quoted too'\"> ]>\n"
        + "<tree attr='&val1;' attr2=\"&val1;\" "
        +" attr3='&val2;' attr4=\"&val2;\" />";

    public void testQuotesViaEntities()
        throws XMLStreamException
    {
        XMLInputFactory ifact = getNewInputFactory();
        setNamespaceAware(ifact, false); // shouldn't matter
        // These are needed to get entities read and expanded:
        setSupportDTD(ifact, true); 
        setReplaceEntities(ifact, true); 

        XMLStreamReader sr = constructStreamReader(ifact, VALID_ATTRS_WITH_QUOTES);
        // Shouldn't get exceptions...

        try {
            streamThrough(sr);
        } catch (XMLStreamException ex) {
            fail("Failed to parse attributes with quotes expanded from entities: "+ex);
        }
    }
}
