package stax2.dtd;

import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.*;

import stax2.BaseStax2Test;

/**
 * Test class that checks whether namespace declarations gained via
 * attribute defaulting work.
 */
public class TestDefaultAttrs
    extends BaseStax2Test
{
    public void testValidNsFromDefaultAttrs()
        throws XMLStreamException
    {
        final String XML =
            "<!DOCTYPE node [\n"
            +"<!ELEMENT node ANY>\n"
            +"<!ATTLIST node attr1 CDATA #IMPLIED>\n"
            +"<!ATTLIST node attr2 CDATA #FIXED 'abc'>\n"
            +"<!ATTLIST node attr3 CDATA #FIXED '123'>\n"
            +"]><node attr1='xyz' attr3='123' />"
            ;

        XMLStreamReader2 sr = getReader(XML, true);
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("node", sr.getLocalName());
        assertEquals(3, sr.getAttributeCount());
        assertEquals("xyz", sr.getAttributeValue("", "attr1"));
        assertEquals("xyz", sr.getAttributeValue(null, "attr1"));
        assertEquals("123", sr.getAttributeValue("", "attr3"));
        assertEquals("123", sr.getAttributeValue(null, "attr3"));
        assertEquals("abc", sr.getAttributeValue("", "attr2"));
        assertEquals("abc", sr.getAttributeValue(null, "attr2"));

        // and non existing...
        assertNull(sr.getAttributeValue("http://foo", "attr1"));
    }

    /*
    ////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////
     */

    private XMLStreamReader2 getReader(String contents, boolean nsAware)
        throws XMLStreamException
    {
        XMLInputFactory2 f = getInputFactory();
        setNamespaceAware(f, nsAware);
        setSupportDTD(f, true);
        setValidating(f, true);
        return constructStreamReader(f, contents);
    }
}
