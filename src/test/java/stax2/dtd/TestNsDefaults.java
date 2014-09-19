package stax2.dtd;

import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.*;

import stax2.BaseStax2Test;

/**
 * Test class that checks whether namespace declarations gained via
 * attribute defaulting work.
 */
public class TestNsDefaults
    extends BaseStax2Test
{
    public void testValidNsFromDefaultAttrs()
        throws XMLStreamException
    {
        final String XML =
            "<!DOCTYPE node [\n"
            +"<!ELEMENT node ANY>\n"
            +"<!ATTLIST node xmlns:ns CDATA 'http://default'>\n"
            +"]><node xmlns:ns='http://expl' ns:attr='123'>"
            +"<node attr='456' /></node>"
            ;

        XMLStreamReader2 sr = getReader(XML, true);
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("node", sr.getLocalName());
        assertElemNotInNamespace(sr);
        assertNoElemPrefix(sr);
        assertEquals(1, sr.getAttributeCount());
        assertEquals(1, sr.getNamespaceCount());
        
        assertEquals("ns", sr.getNamespacePrefix(0));
        assertEquals("http://expl", sr.getNamespaceURI(0));
        
        assertEquals("attr", sr.getAttributeLocalName(0));
        assertEquals("ns", sr.getAttributePrefix(0));
        assertEquals("http://expl", sr.getAttributeNamespace(0));
        assertEquals("123", sr.getAttributeValue(0));
        
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("node", sr.getLocalName());
        assertElemNotInNamespace(sr);
        assertNoElemPrefix(sr);

        assertEquals(1, sr.getAttributeCount());
        assertEquals(1, sr.getNamespaceCount());
        
        assertEquals("ns", sr.getNamespacePrefix(0));
        assertEquals("http://default", sr.getNamespaceURI(0));
        
        assertEquals("attr", sr.getAttributeLocalName(0));
        assertNoAttrPrefix(sr.getAttributePrefix(0));
        assertNoAttrNamespace(sr.getAttributeNamespace(0));
        assertEquals("456", sr.getAttributeValue(0));
        
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("node", sr.getLocalName());
        
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("node", sr.getLocalName());
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
        setCoalescing(f, false);
        setSupportDTD(f, true);
        setValidating(f, false);
        return constructStreamReader(f, contents);
    }
}
