package org.codehaus.stax.test.stream;

import java.util.List;

import javax.xml.stream.*;
import javax.xml.stream.events.EntityDeclaration;
import javax.xml.stream.events.NotationDeclaration;

/**
 * Unit test suite that tests handling of the DOCTYPE declaration event
 * (XMLStreamConstants.DTD).
 * Note that these tests are all related to the non-validating DTD-aware
 * mode: actual validation tests are in separate package. Some of the
 * tests are complementary to the validating mode, however, often testing
 * explicitly that validity violations are not to be reported as exceptions
 * in non-validating mode.
 *
 * @author Tatu Saloranta
 */
public class TestDoctypeDecl
    extends BaseStreamTest
{
    /**
     * Method that verifies properties that should be active when
     * DTD is the current event.
     */
    public void testProperties()
        throws XMLStreamException
    {
        doTestProperties(false);
        doTestProperties(true);
    }

    public void testMinimalValidDecl() 
        throws XMLStreamException
    {
        doTestMinimalValid(false);
        doTestMinimalValid(true);
    }

    public void testSimpleValidDecl() 
        throws XMLStreamException
    {
        doTestSimpleValid(false);
        doTestSimpleValid(true);
    }

    public void testTypicalValid()
        throws XMLStreamException
    {
        doTestTypicalValid(false);
        doTestTypicalValid(true);
    }

    public void testSimpleInvalidDecl() 
        throws XMLStreamException
    {
        doTestSimpleInvalid(false);
        doTestSimpleInvalid(true);
    }

    final static String UNPARSED_ENTITY_XML =
        "<!DOCTYPE root [\n"
        +"<!NOTATION mynot PUBLIC 'some-notation'>\n"
        +"<!ENTITY unp SYSTEM 'http://foo' NDATA mynot>\n"
        +"]><root />";


    public void testSimpleEntity()
        throws XMLStreamException
    {
        XMLStreamReader sr = getReader(UNPARSED_ENTITY_XML, true);
        assertTokenType(DTD, sr.next());
        List<?> l = (List<?>) sr.getProperty("javax.xml.stream.entities");
        assertNotNull(l);
        assertEquals(1, l.size());
        EntityDeclaration ed = (EntityDeclaration) l.get(0);
        assertEquals("unp", ed.getName());
        assertEquals("mynot", ed.getNotationName());

        sr.close();
    }

    public void testSimpleNotation()
        throws XMLStreamException
    {
        XMLStreamReader sr = getReader(UNPARSED_ENTITY_XML, true);
        assertTokenType(DTD, sr.next());
        List<?> l = (List<?>) sr.getProperty("javax.xml.stream.notations");
        assertNotNull(l);
        assertEquals(1, l.size());
        NotationDeclaration nd = (NotationDeclaration) l.get(0);
        assertEquals("mynot", nd.getName());
    }

    /**
     * This test specifically checks to see that xml validation is
     * only to be done when enabled; not just because DTD-awareness
     * is enabled.
     */
    public void testNonVldWithEmptyContentModel()
        throws XMLStreamException
    {
        final boolean NS = true;
        final String XML1 = 
            "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ELEMENT leaf ANY>\n"
            +"]>"
            +"<root>";
        final String XML2 = "</root>";

        // First, let's verify regular text is ok
        XMLStreamReader sr = getReader(XML1 + "some text" + XML2, NS);
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());
        streamThrough(sr);
        sr.close();

        // Then that char/general entities are
        sr = getReader(XML1 + "&amp;" + XML2, NS);
        assertTokenType(DTD, sr.next());
        streamThrough(sr);
        sr.close();

        /* And then that a (declared) element is fine too
         * (undeclared should be, too, but there's separate test
         * elsewhere for that)
         */
        sr = getReader(XML1 + "<leaf></leaf>" + XML2, NS);
        assertTokenType(DTD, sr.next());
        streamThrough(sr);
        sr.close();
    }

    public void testNonVldWithNonMixedContentModel()
        throws XMLStreamException
    {
        final boolean NS = true;
        final String XML1 = 
            "<!DOCTYPE root [\n"
            +"<!ELEMENT root (leaf+)>\n"
            +"<!ELEMENT leaf ANY>\n"
            +"]>"
            +"<root>";
        final String XML2 = "</root>";

        XMLStreamReader sr = getReader(XML1 + "some text<leaf />" + XML2, NS);
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());
        streamThrough(sr);
        sr.close();

        // Then that entities are ok too
        sr = getReader(XML1 + "&amp; xxx<leaf></leaf>" + XML2, NS);
        assertTokenType(DTD, sr.next());
        streamThrough(sr);
        sr.close();
    }

    /*
    ////////////////////////////////////////
    // Private methods, shared test code
    ////////////////////////////////////////
     */

    private void doTestProperties(boolean nsAware)
        throws XMLStreamException
    {
        final String PROP_TEST = 
            "<!DOCTYPE root [ <!-- comment --> ]><root />";

        XMLStreamReader sr = getReader(PROP_TEST, nsAware);
        assertEquals(DTD, sr.next());
        // Type info
        assertEquals(false, sr.isStartElement());
        assertEquals(false, sr.isEndElement());
        assertEquals(false, sr.isCharacters());
        assertEquals(false, sr.isWhiteSpace());

        // indirect type info
        assertEquals(false, sr.hasName());
        assertEquals(true, sr.hasText());

        // And then let's check methods that should throw specific exception
        for (int i = 0; i <= 10; ++i) {
            String method = "";

            try {
                @SuppressWarnings("unused")
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
                case 8:
                    method = "getTextCharacters";
                    result = sr.getTextCharacters();
                    break;
                case 9:
                    method = "getTextStart";
                    result = new Integer(sr.getTextStart());
                    break;
                case 10:
                    method = "getTextLength";
                    result = new Integer(sr.getTextLength());
                    break;
                }
                fail("Expected IllegalStateException, when calling "
                     +method+"() for DTD");
            } catch (IllegalStateException iae) {
                ; // good
            }
        }

        /* Here let's only check it's not null or empty, not exact contents
         * (there are other tests for checking contents)
         */
        String str = sr.getText();
        if (str == null || str.trim().length() == 0) {
            fail("Internal subset not available; StreamReader.getText() returned an empty String (after trim())");
        }
    }

    private void doTestMinimalValid(boolean nsAware)
        throws XMLStreamException
    {
        final String VALID_TEST = "<!DOCTYPE root><root />";

        XMLStreamReader sr = getReader(VALID_TEST, nsAware);

        assertEquals(DTD, sr.next());
        assertEquals(START_ELEMENT, sr.next());
    }

    private void doTestSimpleValid(boolean nsAware)
        throws XMLStreamException
    {
        final String INT_SUBSET = "<!-- comment -->";
        final String VALID_TEST = 
            "<!DOCTYPE root [ "+INT_SUBSET+" ]><root />";

        XMLStreamReader sr = getReader(VALID_TEST, nsAware);
        assertTokenType(DTD, sr.next());

        /* Now... exactly what should be returned is not quite clear.
         * Let's assume that leading/trailing white space may be truncated,
         * but that otherwise we'll get stuff back, with linefeeds
         * normalized?
         */
        String str = getAndVerifyText(sr).trim();

        /* 16-Aug-2004, TSa: Hmmh. Specs are bit confusing; in some places
         *   claiming it should be only the internal subset, in others that
         *   it should be the full DOCTYPE declaration production...
         */
        assertEquals(INT_SUBSET, str);

        sr.close();

        /* 05-Apr-2006, TSa: Following is actually invalid, but
         *   well-formed. And as such, it should not throw an exception
         *   in non-validating mode (but should in validating mode).
         */
        final String VALID_TEST2 = "<!DOCTYPE root><fubar />";
        sr = getReader(VALID_TEST2, nsAware);
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("fubar", sr.getLocalName());
        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(END_DOCUMENT, sr.next());


    }

    private void doTestTypicalValid(boolean nsAware)
        throws XMLStreamException
    {
        final String VALID_TEST = "<!DOCTYPE root [\n"
	    +"<!ELEMENT root ANY>\n"
	    +"<!ATTLIST root attr CDATA #IMPLIED>\n"
	    +"]>\n"
	    +"<root />";

        XMLStreamReader sr = getReader(VALID_TEST, nsAware);

        assertTokenType(DTD, sr.next());
        // May or may not get SPACE... if we get it, will skip
        int type;
        while ((type = sr.next()) == SPACE) {
            ;
        }
        assertTokenType(START_ELEMENT, type);
    }

    private void doTestSimpleInvalid(boolean nsAware)
        throws XMLStreamException
    {
        final String INVALID1 = "<!DOCTYPE>  <root />";
        streamThroughFailing(getReader(INVALID1, nsAware), 
                             "invalid DOCTYPE declaration (missing root element)");

        final String INVALID3 = "<!DOCTYPE root SYSTEM  ><root />";
        streamThroughFailing(getReader(INVALID3, nsAware),
                             "invalid DOCTYPE declaration (missing SYSTEM identifier for DTD)");

        final String INVALID4 = "<!DOCTYPE root PUBLIC  ><root />";
        streamThroughFailing(getReader(INVALID4, nsAware),
                             "invalid DOCTYPE declaration (missing PUBLIC identifier for DTD)");

        final String INVALID5 = "<!DOCTYPE & ><root />";
        streamThroughFailing(getReader(INVALID5, nsAware),
                             "invalid DOCTYPE declaration (unexpected ampersand character)");
    }

    /*
    ////////////////////////////////////////
    // Private methods, other
    ////////////////////////////////////////
     */

    private XMLStreamReader getReader(String contents, boolean nsAware)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setCoalescing(f, false); // shouldn't matter
        setNamespaceAware(f, nsAware);
        setValidating(f, false);
        setSupportDTD(f, true);
        return constructStreamReader(f, contents);
    }
}
