package org.codehaus.stax.test.vstream;

import javax.xml.stream.*;

/**
 * Simple unit test suite that checks for set of well-formedness problems
 * with DTDs
 *
 * @author Tatu Saloranta
 */
public class TestInvalidDTD
    extends BaseVStreamTest
{
    public void testInvalidDirectives()
        throws XMLStreamException
    {
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEM root EMPTY>\n"
            +"]>\n<root />";
        streamThroughFailing(getValidatingReader(XML), "invalid directive '<!ELEM ...>'");

        XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTRLIST root attr CDATA #IMPLIED>\n"
            +"]>\n<root />";
        streamThroughFailing(getValidatingReader(XML), "invalid directive '<!ATRLIST ...>'");
    }

    public void testInvalidGE()
        throws XMLStreamException
    {
        // Need space between name, content
        String XML = "<!DOCTYPE root [\n"
            +"<!ENTITY ge'value'>\n"
            +"]>\n<root />";
        streamThroughFailing(getValidatingReader(XML),
                             "missing space between general entity name and value");
    }

    public void testInvalidPE()
        throws XMLStreamException
    {
        // Need space between name, content
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ENTITY % pe'value'>\n"
            +"]>\n<root />";
        streamThroughFailing(getValidatingReader(XML), "missing space between parameter entity name and value");

        // As well as before and after percent sign
        XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ENTITY %pe 'value'>\n"
            +"]>\n<root />";
        streamThroughFailing(getValidatingReader(XML), "missing space between parameter entity percent sign and name");
        XML = "<!DOCTYPE root [\n"
            +"<!ENTITY% e ''>\n"
            +"<!ELEMENT root EMPTY>\n"
            +"]><root />";
        streamThroughFailing(getValidatingReader(XML), "missing space between ENTITY and parameter entity percent sign");

        // and finally, no NDATA allowed for PEs
        XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!NOTATION notation SYSTEM 'url:notation'>\n"
            +"<!ENTITY % pe 'value' SYSTEM 'url:foo' NDATA notation>\n"
            +"]>\n<root />";
        streamThroughFailing(getValidatingReader(XML), "PEs can not be unparsed external (ie. have NDATA reference)");
    }

    public void testInvalidComment()
        throws XMLStreamException
    {
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!-- Can not have '--' in here! (unlike in SGML) -->\n"
            +"]><root />";
        streamThroughFailing(getValidatingReader(XML), "invalid directive '<!ELEM ...>'");
    }

    public void testInvalidPI()
        throws XMLStreamException
    {
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<?xml version='1.0'?>\n"
            +"]><root />";
        streamThroughFailing(getValidatingReader(XML), "invalid processing instruction in DTD; can not have target 'xml'");
    }

    /**
     * CDATA directive not allowed in DTD subsets.
     */
    public void testInvalidCData()
        throws XMLStreamException
    {
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<![CDATA[ hah! ]]>\n"
            +"]><root />";
        streamThroughFailing(getValidatingReader(XML), "invalid CDATA directive in int. DTD subset");
    }
}
