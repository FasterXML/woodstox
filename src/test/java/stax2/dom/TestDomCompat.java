package stax2.dom;

import java.io.*;

import javax.xml.parsers.*;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.transform.dom.DOMSource;

import org.xml.sax.InputSource;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;

import org.codehaus.stax2.*;

import stax2.BaseStax2Test;

/**
 * Unit test suite that checks that input-side DOM-compatibility
 * features (DOMSource as input) are implemented as expected.
 *<p>
 * This test is part of stax2test suite because a reference implementation
 * of DOM-wrapping/adapting reader is included, and hence it is
 * reasonable to expect that Stax2 implementations would implement
 * this part of DOM interoperability support.
 */
public class TestDomCompat
    extends BaseStax2Test
{
    public void testSimpleDomInput() throws Exception
    {
        final String XML =
            "<?xml version='1.0' ?><!--prolog-->"
            +"<ns:root xmlns:ns='http://foo' attr='value'>"
            +"<leaf ns:attr='value2' />"
            +"<?proc instr?><!--comment-->"
            +"\nAnd some text"
            +"</ns:root><?pi-in epilog?>"
            ;

        XMLStreamReader sr = createDomBasedReader(XML, true);

        assertTokenType(COMMENT, sr.next());
        assertEquals("prolog", getAndVerifyText(sr));

        // 10-Sep-2010, tatu: Verify [WSTX-246]
        assertNotNull(sr.getLocation());
        
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertEquals("ns", sr.getPrefix());
        assertEquals("http://foo", sr.getNamespaceURI());
        QName n = sr.getName();
        assertNotNull(n);
        assertEquals("root", n.getLocalPart());
        
        assertEquals(1, sr.getAttributeCount());
        assertEquals("attr", sr.getAttributeLocalName(0));
        assertEquals("", sr.getAttributePrefix(0));
        assertEquals("", sr.getAttributeNamespace(0));
        n = sr.getAttributeName(0);
        assertNotNull(n);
        assertEquals("attr", n.getLocalPart());
        assertEquals("value", sr.getAttributeValue(0));

        assertEquals(1, sr.getNamespaceCount());
        assertEquals("ns", sr.getNamespacePrefix(0));
        assertEquals("http://foo", sr.getNamespaceURI(0));

        NamespaceContext nsCtxt = sr.getNamespaceContext();
        assertNotNull(nsCtxt);
        /* 28-Apr-2006, TSa: Alas, namespace access is only fully
         *   implemented in DOM Level 3 (JDK 1.5+)... thus, can't check:
         */
        /*
        assertEquals("ns", nsCtxt.getPrefix("http://foo"));
        assertEquals("http://foo", nsCtxt.getNamespaceURI("ns"));
        assertNull(nsCtxt.getPrefix("http://whatever"));
        assertNull(nsCtxt.getNamespaceURI("nsX"));
        */

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());
        assertEquals("", sr.getPrefix());
        assertEquals("", sr.getNamespaceURI());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("attr", sr.getAttributeLocalName(0));
        assertEquals("ns", sr.getAttributePrefix(0));
        assertEquals("http://foo", sr.getAttributeNamespace(0));
        assertEquals(0, sr.getNamespaceCount());

        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());
        assertEquals("", sr.getPrefix());
        assertEquals("", sr.getNamespaceURI());
        assertEquals(0, sr.getNamespaceCount());

        assertTokenType(PROCESSING_INSTRUCTION, sr.next());
        assertEquals("proc", sr.getPITarget());
        assertEquals("instr", sr.getPIData());

        assertTokenType(COMMENT, sr.next());
        assertEquals("comment", getAndVerifyText(sr));

        assertTokenType(CHARACTERS, sr.next());
        // yeah yeah, could be split...
        assertEquals("\nAnd some text", getAndVerifyText(sr));

        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertEquals("ns", sr.getPrefix());
        assertEquals("http://foo", sr.getNamespaceURI());

        assertEquals(1, sr.getNamespaceCount());
        assertEquals("ns", sr.getNamespacePrefix(0));
        assertEquals("http://foo", sr.getNamespaceURI(0));

        assertTokenType(PROCESSING_INSTRUCTION, sr.next());
        assertEquals("pi-in", sr.getPITarget());
        assertEquals("epilog", sr.getPIData());

        assertTokenType(END_DOCUMENT, sr.next());

        assertFalse(sr.hasNext());
        sr.close();
    }

    /**
     * Test added to verify that [WSTX-134] is fixed properly
     */
    public void testDomWhitespace()
        throws Exception
    {
        final String XML =
            "<?xml version='1.0' ?><root>  \n<leaf>\t</leaf>  x </root>";
        XMLStreamReader sr = createDomBasedReader(XML, true);

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertTokenType(CHARACTERS, sr.next());
        assertTrue(sr.isWhiteSpace());
        assertEquals("  \n", getAndVerifyText(sr));
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());
        assertTokenType(CHARACTERS, sr.next());
        assertTrue(sr.isWhiteSpace());
        assertEquals("\t", getAndVerifyText(sr));
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());
        assertTokenType(CHARACTERS, sr.next());
        assertFalse(sr.isWhiteSpace());
        assertEquals("  x ", getAndVerifyText(sr));
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertTokenType(END_DOCUMENT, sr.next());
        sr.close();
    }

    /**
     * Test to verify that [WSTX-145] is properly fixed
     */
    public void testDomCoalescingText()
        throws Exception
    {
        final String XML =
            "<root>Some <![CDATA[content]]> in cdata</root>";

        Document doc = parseDomDoc(XML, true);
        XMLInputFactory2 ifact = getInputFactory();
        setCoalescing(ifact, true);
        XMLStreamReader sr = ifact.createXMLStreamReader(new DOMSource(doc));
        assertTokenType(START_DOCUMENT, sr.getEventType());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertTokenType(CHARACTERS, sr.next());
        assertEquals("Some content in cdata", getAndVerifyText(sr));

        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(END_DOCUMENT, sr.next());
    }

    public void testDomCoalescingType()
        throws Exception
    {
        final String XML =
            "<root><![CDATA[...]]></root>";

        Document doc = parseDomDoc(XML, true);
        XMLInputFactory2 ifact = getInputFactory();
        setCoalescing(ifact, true);
        XMLStreamReader sr = ifact.createXMLStreamReader(new DOMSource(doc));
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        // Should always be of type CHARACTERS, even if underlying event is CDATA
        assertTokenType(CHARACTERS, sr.next());
        assertEquals("...", getAndVerifyText(sr));

        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(END_DOCUMENT, sr.next());
    }

    /**
     * First test regarding [WSTX-162], let's check that we can
     * actually enable/disable interning on reader instances
     * (independent of whether settings take effect or not)
     */
    public void testDomInternProperties()
        throws Exception
    {
        Document doc = parseDomDoc("<root />", true);
        XMLInputFactory2 ifact = getInputFactory();
        XMLStreamReader2 sr = (XMLStreamReader2) ifact.createXMLStreamReader(new DOMSource(doc));

        boolean okSet = sr.setProperty(XMLInputFactory2.P_INTERN_NAMES, Boolean.TRUE);
        assertTrue(okSet);
        assertEquals(Boolean.TRUE, sr.getProperty(XMLInputFactory2.P_INTERN_NAMES));
        okSet = sr.setProperty(XMLInputFactory2.P_INTERN_NAMES, Boolean.FALSE);
        assertTrue(okSet);
        assertEquals(Boolean.FALSE, sr.getProperty(XMLInputFactory2.P_INTERN_NAMES));

        okSet = sr.setProperty(XMLInputFactory2.P_INTERN_NS_URIS, Boolean.TRUE);
        assertTrue(okSet);
        assertEquals(Boolean.TRUE, sr.getProperty(XMLInputFactory2.P_INTERN_NS_URIS));
        okSet = sr.setProperty(XMLInputFactory2.P_INTERN_NS_URIS, Boolean.FALSE);
        assertTrue(okSet);
        assertEquals(Boolean.FALSE, sr.getProperty(XMLInputFactory2.P_INTERN_NS_URIS));
    }

    /**
     * Test for checking that [WSTX-162] has been addressed,
     * regarding names.
     */
    public void testDomInternNames()
        throws Exception
    {
        final String ELEM = "root";
        final String PREFIX = "ns";
        final String ATTR = "attr";
        final String URI = "http://foo";
        final String XML = "<"+PREFIX+":"+ELEM+" attr='1' xmlns:"+PREFIX+"='"+URI+"' />";
        Document doc = parseDomDoc(XML, true);
        XMLInputFactory2 ifact = getInputFactory();

        /* Ok, so: let's first ensure that local names ARE intern()ed
         * when we request them to be:
         */
        ifact.setProperty(XMLInputFactory2.P_INTERN_NAMES, Boolean.TRUE);
        XMLStreamReader sr = ifact.createXMLStreamReader(new DOMSource(doc));
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(ELEM, sr.getLocalName());
        assertSame(ELEM, sr.getLocalName());

        assertEquals(ATTR, sr.getAttributeLocalName(0));
        assertSame(ATTR, sr.getAttributeLocalName(0));

        assertEquals(PREFIX, sr.getPrefix());
        assertSame(PREFIX, sr.getPrefix());
        sr.close();

        /* And then also that the impl does honor disabling of
         * the feature: while optional, ref. impl. makes this
         * easy so there's no excuse not to.
         */
        ifact.setProperty(XMLInputFactory2.P_INTERN_NAMES, Boolean.FALSE);
        sr = ifact.createXMLStreamReader(new DOMSource(doc));
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(ELEM, sr.getLocalName());
        // Xerces won't force intern() of element names
        assertNotSame(ELEM, sr.getLocalName());

        // But does intern attribute names
        /*
        assertEquals(ATTR, sr.getAttributeLocalName(0));
        assertNotSame(ATTR, sr.getAttributeLocalName(0));
        */

        assertEquals(PREFIX, sr.getPrefix());
        assertNotSame(PREFIX, sr.getPrefix());
        sr.close();
    }

    /**
     * Test for checking that [WSTX-162] has been addressed,
     * regarding names.
     */
    public void testDomInternNsURIs()
        throws Exception
    {
        final String ELEM = "root";
        final String URI = "http://foo";
        final String XML = "<"+ELEM+" xmlns='"+URI+"' />";
        Document doc = parseDomDoc(XML, true);
        XMLInputFactory2 ifact = getInputFactory();

        /* Ok, so: let's first ensure that URIs are intern()ed
         * when we request them to be:
         */
        ifact.setProperty(XMLInputFactory2.P_INTERN_NS_URIS, Boolean.TRUE);
        XMLStreamReader sr = ifact.createXMLStreamReader(new DOMSource(doc));
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(ELEM, sr.getLocalName());
        assertEquals(URI, sr.getNamespaceURI());
        assertSame(URI, sr.getNamespaceURI());
        sr.close();

        /* Beyond this we can't say much: it all depends on whether
         * the backing DOM impl uses intern() or not.
         */
        ifact.setProperty(XMLInputFactory2.P_INTERN_NS_URIS, Boolean.FALSE);
        sr = ifact.createXMLStreamReader(new DOMSource(doc));
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(ELEM, sr.getLocalName());
        assertEquals(URI, sr.getNamespaceURI());

        // Ok, looks like Xerces does intern namespace URIs? Weird...
        /*
        assertNotSame(URI, sr.getNamespaceURI());
        */

        sr.close();
    }

    // [WSTX-244]
    public void testGetElementText() throws Exception
    {
        final String XML =
            "<root>Some<![CDATA[ ]]>text</root>";

        XMLInputFactory2 ifact = getInputFactory();
        XMLStreamReader sr;

        // First, non-coalescing:
        setCoalescing(ifact, false);
        sr = ifact.createXMLStreamReader(new DOMSource(parseDomDoc(XML, true)));
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("Some text", sr.getElementText());
        assertTokenType(END_ELEMENT, sr.getEventType());
        assertTokenType(END_DOCUMENT, sr.next());
        sr.close();

        // then coalescing
        setCoalescing(ifact, true);
        sr = ifact.createXMLStreamReader(new DOMSource(parseDomDoc(XML, true)));
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("Some text", sr.getElementText());
        assertTokenType(END_ELEMENT, sr.getEventType());
        assertTokenType(END_DOCUMENT, sr.next());
        sr.close();
    }

    // [WSTX-259]
    public void testEmptyFragment() throws Exception
    {
        DocumentFragment fragment = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument().createDocumentFragment();
    
        XMLStreamReader sr = XMLInputFactory.newInstance().createXMLStreamReader(new DOMSource(fragment));
        assertTokenType(START_DOCUMENT, sr.getEventType());
        assertTokenType(END_DOCUMENT, sr.next());
        assertFalse(sr.hasNext());
    }
    
    /*
    ///////////////////////////////////////////////////////////
    // Helper methods
    ///////////////////////////////////////////////////////////
    */

    private XMLStreamReader2 createDomBasedReader(String content, boolean nsAware)
        throws Exception
    {
        XMLInputFactory2 ifact = getInputFactory();
        XMLStreamReader2 sr = (XMLStreamReader2) ifact.createXMLStreamReader(new DOMSource(parseDomDoc(content, nsAware)));
        // Let's also check it's properly initialized
        assertTokenType(START_DOCUMENT, sr.getEventType());
        return sr;
    }

    private Document parseDomDoc(String content, boolean nsAware)
        throws Exception
    {
        // First, need to parse using JAXP DOM:
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(nsAware);
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(new InputSource(new StringReader(content)));
    }
}
