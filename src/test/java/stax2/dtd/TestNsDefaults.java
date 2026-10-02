package stax2.dtd;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.*;

import stax2.BaseStax2Test;
import org.junit.jupiter.api.Test;

/**
 * Test class that checks whether namespace declarations gained via
 * attribute defaulting work.
 */
public class TestNsDefaults
    extends BaseStax2Test
{
    @Test
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

    /**
     * Reserved prefixes and reserved namespace URIs are constrained the same
     * way whether the declaration is explicit or comes from an attribute
     * default (Namespaces in XML 1.0/1.1 #3).
     */
    @Test
    public void testReservedPrefixFromDefaultAttrs()
        throws XMLStreamException
    {
        // "xmlns" can not be declared at all
        _verifyNsFailure("<!ATTLIST node xmlns:xmlns CDATA 'http://evil'>",
                "<node />", "declare prefix \"xmlns\"");
        // ... "xml" only to its own URI
        _verifyNsFailure("<!ATTLIST node xmlns:xml CDATA 'http://evil'>",
                "<node />", "redeclare prefix \"xml\"");
        // ... and an explicit, valid declaration must not let the default through
        _verifyNsFailure("<!ATTLIST node xmlns:xml CDATA 'http://evil'>",
                "<node xmlns:xml='"+XMLConstants.XML_NS_URI+"' />",
                "redeclare prefix \"xml\"");
    }

    @Test
    public void testReservedNsUriFromDefaultAttrs()
        throws XMLStreamException
    {
        final String XML_URI = XMLConstants.XML_NS_URI;
        final String XMLNS_URI = XMLConstants.XMLNS_ATTRIBUTE_NS_URI;

        _verifyNsFailure("<!ATTLIST node xmlns:ns CDATA '"+XML_URI+"'>",
                "<node />", "can only bind to");
        _verifyNsFailure("<!ATTLIST node xmlns:ns CDATA '"+XMLNS_URI+"'>",
                "<node />", "can not be explicitly bound");
        // same for the default namespace
        _verifyNsFailure("<!ATTLIST node xmlns CDATA '"+XML_URI+"'>",
                "<node />", "can only bind to");
        _verifyNsFailure("<!ATTLIST node xmlns CDATA '"+XMLNS_URI+"'>",
                "<node />", "can not be explicitly bound");
    }

    /**
     * Declaring "xml" to its own URI via an attribute default remains legal.
     */
    @Test
    public void testValidXmlPrefixFromDefaultAttrs()
        throws XMLStreamException
    {
        final String XML = "<!DOCTYPE node [\n"
            +"<!ELEMENT node ANY>\n"
            +"<!ATTLIST node xmlns:xml CDATA '"+XMLConstants.XML_NS_URI+"'>\n"
            +"]><node />";
        XMLStreamReader2 sr = getReader(XML, true);
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("node", sr.getLocalName());
        assertEquals(1, sr.getNamespaceCount());
        assertEquals("xml", sr.getNamespacePrefix(0));
        assertEquals(XMLConstants.XML_NS_URI, sr.getNamespaceURI(0));
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();
    }

    /*
    ////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////
     */

    private void _verifyNsFailure(String attlist, String docElem, String match)
        throws XMLStreamException
    {
        final String XML = "<!DOCTYPE node [\n<!ELEMENT node ANY>\n"
            +attlist+"\n]>"+docElem;
        XMLStreamReader2 sr = getReader(XML, true);
        try {
            assertTokenType(DTD, sr.next());
            assertTokenType(START_ELEMENT, sr.next());
            fail("Expected a failure for ["+attlist+"], instead got "
                    +sr.getNamespaceCount()+" namespace declaration(s), first one bound to \""
                    +sr.getNamespaceURI(0)+"\"");
        } catch (XMLStreamException e) {
            verifyException(e, match);
        } finally {
            sr.close();
        }
    }

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
