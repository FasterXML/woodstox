package org.codehaus.stax.test.stream;

import javax.xml.namespace.*;
import javax.xml.stream.*;

/**
 * Unit test suite that tests handling of XML elements, both in namespace
 * aware and non-namespace modes.
 *
 * @author Tatu Saloranta
 */
public class TestElements
    extends BaseStreamTest
{
    /**
     * Method that checks properties of START_ELEMENT and END_ELEMENT
     * returned by the stream reader are correct according to StAX specs.
     */
    public void testNsProperties()
        throws XMLStreamException
    {
        testProperties(true, "testNsProperties");
    }

    public void testNonNsProperties()
        throws XMLStreamException
    {
        testProperties(false, "testNonNsProperties");
    }

    /**
     * Does test for simple element structure in namespace aware mode
     */
    public void testValidNsElems()
        throws XMLStreamException
    {
        testValid(true, "testValidNsElems");
    }

    public void testValidNonNsElems()
        throws XMLStreamException
    {
        testValid(false, "testValidNonNsElems");
    }

    public void testInvalidNsElems()
        throws XMLStreamException
    {
        testInvalid(true, "testInvalidNsElems");
    }

    public void testInvalidNonNsElems()
        throws XMLStreamException
    {
        testInvalid(false, "testInvalidNonNsElems");
    }

    public void testEmptyDocument()
        throws XMLStreamException
    {
        String EMPTY_XML = "   ";

        // Empty documents are not valid (missing root element)

        streamThroughFailing(getElemReader(EMPTY_XML, true),
                             "empty document (not valid, missing root element)");

        XMLStreamReader sr = getElemReader(EMPTY_XML, false);
        if (sr != null) { // only if non-ns-aware mode supported
            streamThroughFailing(sr, 
                                 "empty document (not valid, missing root element)");
        }
    }

    public void testNoRootDocument()
        throws XMLStreamException
    {
        String NOROOT_XML = "<?xml version='1.0' ?>\n"
            +"   <!-- comment...-->   <?target !?>";

        // Documents without root are not valid
        streamThroughFailing(getElemReader(NOROOT_XML, true),
                             "document without root element");
        
        XMLStreamReader sr = getElemReader(NOROOT_XML, false);
        if (sr != null) { // only if non-ns-aware mode supported
            streamThroughFailing(sr, "document without root element");
        }
    }
    
    public void testInvalidEmptyElem()
        throws XMLStreamException
    {
        String XML = "<root>   <elem / ></root>";
        String MSG = "malformed empty element (space between '/' and '>')";

        streamThroughFailing(getElemReader(XML, true), MSG);

        XMLStreamReader sr = getElemReader(XML, false);
        if (sr != null) { // only if non-ns-aware mode supported
            streamThroughFailing(sr, MSG);
        }
    }

    /*
    ///////////////////////////////////////////////////////////
    // Private methods, shared test code
    ///////////////////////////////////////////////////////////
     */

    private void testProperties(boolean nsAware, String method)
        throws XMLStreamException
    {
        XMLStreamReader sr = getElemReader("<root />", nsAware);
        if (sr == null) {
            reportNADueToNS(method);
            return;
        }
        
        assertEquals(START_ELEMENT, sr.next());
        testStartOrEnd(nsAware, sr, true);

        assertEquals(END_ELEMENT, sr.next());
        testStartOrEnd(nsAware, sr, false);
    }

    private void testStartOrEnd(boolean nsAware, XMLStreamReader sr,
                                boolean isStart)
        throws XMLStreamException
    {
        int evtType = isStart ? START_ELEMENT : END_ELEMENT;
        assertEquals(evtType, sr.getEventType());
        String eventStr = tokenTypeDesc(evtType);

        // simple type info
        assertEquals(isStart, sr.isStartElement());
        assertEquals(!isStart, sr.isEndElement());
        assertEquals(false, sr.isCharacters());
        assertEquals(false, sr.isWhiteSpace());

        // indirect type info
        assertEquals(true, sr.hasName());
        assertEquals(false, sr.hasText());

        assertNotNull(sr.getLocation());
        QName n = sr.getName();
        assertNotNull(n);
        assertEquals("root", n.getLocalPart());
        /* 07-Sep-2007, TSa: The current thinking within Stax community
         *   is that empty String is the right answer for all unbound
         *   prefixes and namespace URIs, unless explicitly defined
         *   that null is to be used for individual methods.
         */
        assertEquals("", n.getPrefix());
        assertNoNsURI(sr);

        if (isStart) {
            assertEquals(0, sr.getAttributeCount());
        } else {
            try {
                int count = sr.getAttributeCount();
                fail("Expected an IllegalStateException when trying to call getAttributeCount() for "+eventStr);
            } catch (IllegalStateException e) {
                // good
            }
        }
        assertEquals(0, sr.getNamespaceCount());
        if (nsAware) {
            /* but how about if namespaces are not supported? Can/should
             * it return null?
             */
            assertNotNull(sr.getNamespaceContext());
        }

        for (int i = 0; i < 4; ++i) {
            String method = "";

            try {
                Object result = null;
                switch (i) {
                case 0:
                    method = "getText";
                    result = sr.getText();
                    break;
                case 1:
                    method = "getTextCharacters";
                    result = sr.getTextCharacters();
                    break;
                case 2:
                    method = "getPITarget";
                    result = sr.getPITarget();
                    break;
                case 3:
                    method = "getPIData";
                    result = sr.getPIData();
                    break;
                }
                fail("Expected IllegalStateException, when calling "+method+"() for "+eventStr);
            } catch (IllegalStateException iae) {
                ; // good
            }
        }
    }

    private void testValid(boolean nsAware, String method)
        throws XMLStreamException
    {
        final String NS_URL1 = "http://www.stax.org";
        final String NS_PREFIX1 = "prefix1";
        
        final String NS_URL2 = "urn://mydummyid";
        final String NS_PREFIX2 = "prefix2";

        final String VALID_CONTENT
            = "<root><"+NS_PREFIX1+":elem xmlns:"+NS_PREFIX1
            +"='"+NS_URL1+"' "+NS_PREFIX1+":attr='value'>Text"
            +"</"+NS_PREFIX1+":elem>"
            +"<elem2 xmlns='"+NS_URL2+"' attr='value' /></root>";

        /* First of all, let's check that it can be completely
         * parsed:
         */
        XMLStreamReader sr = getElemReader(VALID_CONTENT, nsAware);
        if (sr == null) {
            reportNADueToNS(method);
            return;
        }
        
        streamThrough(sr);

        // And then let's do it step by step
        sr = getElemReader(VALID_CONTENT, nsAware);

        // First, need to get <root>
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertNoPrefix(sr);
        assertNoNsURI(sr);

        // Let's also check QName seems valid:
        QName name = sr.getName();
        assertNotNull("Shouldn't get null QName for any start element", name);
        assertEquals(name, new QName("root"));
        assertNoNsURI(sr);
        assertEquals(0, sr.getAttributeCount());
        assertEquals(0, sr.getNamespaceCount());

        // And then <elem ...>
        assertEquals(START_ELEMENT, sr.next());
        if (nsAware) {
            assertEquals("elem", sr.getLocalName());
            assertEquals(NS_PREFIX1, sr.getPrefix());
            assertEquals(NS_URL1, sr.getNamespaceURI());
        } else {
            assertEquals(NS_PREFIX1+":elem", sr.getLocalName());
            assertNoPrefix(sr);
            assertNoNsURI(sr);
        }

        int expNs = nsAware ? 1 : 0;
        int expAttr = nsAware ? 1 : 2;

        /* Let's just check counts, not values; attribute test can
         * do thorough tests for values and access
         */
        assertEquals(expAttr, sr.getAttributeCount());
        assertEquals(expNs, sr.getNamespaceCount());

        assertEquals(CHARACTERS, sr.next());
        assertEquals("Text", getAndVerifyText(sr));

        assertEquals(END_ELEMENT, sr.next());
        if (nsAware) {
            assertEquals("elem", sr.getLocalName());
            assertEquals(NS_PREFIX1, sr.getPrefix());
            assertEquals(NS_URL1, sr.getNamespaceURI());
        } else {
            assertEquals(NS_PREFIX1+":elem", sr.getLocalName());
            assertNoPrefix(sr);
            assertNoNsURI(sr);
        }
        assertEquals(expNs, sr.getNamespaceCount());

        assertEquals(START_ELEMENT, sr.next());
        assertEquals("elem2", sr.getLocalName());

        assertNoPrefix(sr);
        if (nsAware) {
            assertEquals(NS_URL2, sr.getNamespaceURI());
        } else {
            assertNoNsURI(sr);
        }
        assertEquals(expAttr, sr.getAttributeCount());
        assertEquals(expNs, sr.getNamespaceCount());

        assertEquals(END_ELEMENT, sr.next());
        assertEquals("elem2", sr.getLocalName());

        assertNoPrefix(sr);
        if (nsAware) {
            assertEquals(NS_URL2, sr.getNamespaceURI());
        } else {
            assertNoNsURI(sr);
        }
        assertEquals(expNs, sr.getNamespaceCount());

        assertEquals(END_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertNoNsURI(sr);
        assertNoPrefix(sr);
        assertEquals(0, sr.getNamespaceCount());
    }

    /**
     * Simple tests to check for incorrect nesting
     */
    private void testInvalid(boolean nsAware, String method)
        throws XMLStreamException
    {
        // Wrong end element:
        String XML = "<root>  text </notroot>";

        XMLStreamReader sr = getElemReader(XML, nsAware);
        if (sr == null) {
            reportNADueToNS(method);
            return;
        }
        
        streamThroughFailing(sr, "incorrect nesting (wrong end element name)");

        // For namespace mode, has to be same prefix (not just same URI)
        if (nsAware) {
            XML = "<a:root xmlns:a='myurl' xmlns:b='myurl'>  text </b:root>";
            sr = getElemReader(XML, nsAware);
            streamThroughFailing(sr, "incorrect nesting (namespace prefix in close element not the same as in start element)");
        }

        // Missing end element:
        XML = "<root><branch>  text </branch>";
        streamThroughFailing(getElemReader(XML, nsAware),
                             "incorrect nesting (missing end element)");

        // More than one root:
        XML = "<root /><anotherRoot />";
        streamThroughFailing(getElemReader(XML, nsAware),
                             "more than one root element");
    }

    /*
    ///////////////////////////////////////////////////////////
    // Private methods, other
    ///////////////////////////////////////////////////////////
     */

    private XMLStreamReader getElemReader(String contents, boolean nsAware)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        if (!setNamespaceAware(f, nsAware)) {
            return null;
        }
        setCoalescing(f, true);
        setValidating(f, false);
        return constructStreamReader(f, contents);
    }
}
