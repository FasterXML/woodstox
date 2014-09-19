package org.codehaus.stax.test.stream;

import java.io.*;

import javax.xml.stream.*;

/**
 * Unit test suite that tests handling of the xml declaration.
 */
public class TestXmlDecl
    extends BaseStreamTest
{
    final String VALID_XML1
        = "<?xml version='1.0'?><root />";

    final String VALID_XML_UTF8
        = "<?xml version='1.0' encoding='UTF-8' ?><root />";

    /**
     * Method that verifies properties that should be active when
     * START_DOCUMENT is the current event (ie before iterating), ie.
     * right after xml declaration has been read
     */
    public void testProperties()
        throws XMLStreamException
    {
        doTestProperties(false);
        doTestProperties(true);
    }

    public void testValidDecl() 
        throws XMLStreamException, IOException
    {
        doTestValid(false);
        doTestValid(true);
    }

    public void testValidStandaloneDecls() 
        throws XMLStreamException, IOException
    {
        String XML = "<?xml version='1.0' standalone='yes' ?><root />";
        XMLStreamReader sr = getReader(XML, true);

        assertEquals("1.0", sr.getVersion());
        assertTrue("XMLStreamReader.standalonSet() should be true", sr.standaloneSet());
        assertTrue("XMLStreamReader.isStandalone() should be true", sr.isStandalone());


        XML = "<?xml version='1.0' standalone='no' ?><root />";
        sr = getReader(XML, true);

        assertEquals("1.0", sr.getVersion());
        assertTrue("XMLStreamReader.standalonSet() should be true", sr.standaloneSet());
        assertFalse("XMLStreamReader.isStandalone() should be false", sr.isStandalone());

        // And then all of it:

        XML = "<?xml version='1.0' encoding='US-ASCII' standalone='yes' ?><root />";
        sr = getReader(XML, true);

        assertEquals("1.0", sr.getVersion());
        assertTrue("XMLStreamReader.standalonSet() should be true", sr.standaloneSet());
        assertTrue("XMLStreamReader.isStandalone() should be true", sr.isStandalone());
        assertEquals("US-ASCII", sr.getCharacterEncodingScheme());
    }

    public void testInvalidDecl() 
        throws XMLStreamException
    {
        doTestInvalid(false);
        doTestInvalid(true);
    }

    /*
    ////////////////////////////////////////
    // Private methods, shared test code
    ////////////////////////////////////////
     */

    private void doTestProperties(boolean nsAware)
        throws XMLStreamException
    {
        XMLStreamReader sr = getReader(VALID_XML1, nsAware);
        assertEquals(START_DOCUMENT, sr.getEventType());
        // Type info
        assertEquals(false, sr.isStartElement());
        assertEquals(false, sr.isEndElement());
        assertEquals(false, sr.isCharacters());
        assertEquals(false, sr.isWhiteSpace());

        // indirect type info
        assertEquals(false, sr.hasName());
        assertEquals(false, sr.hasText());

        /* Now... how about location and namespace context? Are they really
         * guaranteed to exist at this point? Since API doesn't indicate
         * otherwise, let's assume this is the case, for now.
         */
        assertNotNull(sr.getLocation());
        if (nsAware) {
            assertNotNull(sr.getNamespaceContext());
        }

        // And then let's check methods that should throw specific exception
        for (int i = 0; i < 8; ++i) {
            String method = "";

            try {
                Object result = null;
                switch (i) {
                case 0:
                    method = "getName";
                    result = sr.getName();
                    break;
                case 1:
                    method = "getPrefix";
                    result = sr.getPrefix();
                    break;
                case 2:
                    method = "getLocalName";
                    result = sr.getLocalName();
                    break;
                case 3:
                    method = "getNamespaceURI";
                    result = sr.getNamespaceURI();
                    break;
                case 4:
                    method = "getNamespaceCount";
                    result = new Integer(sr.getNamespaceCount());
                    break;
                case 5:
                    method = "getAttributeCount";
                    result = new Integer(sr.getAttributeCount());
                    break;
                case 6:
                    method = "getPITarget";
                    result = sr.getPITarget();
                    break;
                case 7:
                    method = "getPIData";
                    result = sr.getPIData();
                    break;
                }
                fail("Expected IllegalStateException, when calling "
                     +method+"() for XML declaration (START_DOCUMENT)");
            } catch (IllegalStateException iae) {
                ; // good
            }
        }
    }

    private void doTestValid(boolean nsAware)
        throws XMLStreamException, IOException
    {
        XMLStreamReader sr = getReader(VALID_XML1, nsAware);

        /* First, let's ensure that version is ok, and whether
         * stand-alone pseudo-attr was set:
         */
        assertEquals("1.0", sr.getVersion());
        assertFalse(sr.standaloneSet());

        // Then, encoding passed via factory method:
        sr = getUTF8StreamReader(VALID_XML1, nsAware);
        assertEquals("UTF-8", sr.getEncoding());

        // Then, automatic detection of encoding:
        sr = getReader(VALID_XML_UTF8, nsAware);
        assertEquals("1.0", sr.getVersion());
        assertEquals("UTF-8", sr.getCharacterEncodingScheme());
    }

    private void doTestInvalid(boolean nsAware)
        throws XMLStreamException
    {
        String XML = "<?xml ?><root />";
        streamThroughFailing(getFactory(nsAware), XML,
                             "invalid XML declaration (missing version)");

        XML = "<?xml version='1.0\" ?><root />";
        streamThroughFailing(getFactory(nsAware), XML,
                             "invalid XML declaration (mismatch of quotes)");
    }

    /*
    ////////////////////////////////////////
    // Private methods, other
    ////////////////////////////////////////
     */

    private XMLInputFactory getFactory(boolean nsAware)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setCoalescing(f, false); // shouldn't matter
        setNamespaceAware(f, nsAware);
        setValidating(f, false);
        return f;
    }

    private XMLStreamReader getReader(String contents, boolean nsAware)
        throws XMLStreamException
    {
        return constructStreamReader(getFactory(nsAware), contents);
    }

    private XMLStreamReader getUTF8StreamReader(String contents, boolean nsAware)
        throws XMLStreamException, UnsupportedEncodingException
    {
        XMLInputFactory f = getInputFactory();
        setCoalescing(f, false); // shouldn't matter
        setNamespaceAware(f, nsAware);
        setValidating(f, false);
        InputStream in = new ByteArrayInputStream(contents.getBytes("UTF-8"));
        return f.createXMLStreamReader(in, "UTF-8");
    }
}
