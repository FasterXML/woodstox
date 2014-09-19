package org.codehaus.stax.test.stream;

import javax.xml.stream.*;

/**
 * Unit test suite that tests that events from prolog and epilog are
 * correctly reported (and normalized if need be) by the stream reader.
 */
public class TestEpilog
    extends BaseStreamTest
{
    public void testValidEpilog()
        throws XMLStreamException
    {
        String XML = "<!-- test comment -->     <root attr='whatever' />  <?some processing instruction?>   <!-- another comment! -->   ";

        XMLStreamReader sr = getReader(XML, true);
        assertTokenType(COMMENT, sr.next());
        assertEquals(" test comment ", getAndVerifyText(sr));

        // May or may not get white space in epilog...
        int type;
        while ((type = sr.next()) == SPACE) {
            ;
        }
        assertTokenType(START_ELEMENT, type);
        assertTokenType(END_ELEMENT, sr.next());

        while ((type = sr.next()) == SPACE) {
            ;
        }
        assertTokenType(PROCESSING_INSTRUCTION, type);
        assertEquals("some", sr.getPITarget());
        // Not sure if the white space between target and data is included...
        assertEquals("processing instruction", sr.getPIData().trim());

        while ((type = sr.next()) == SPACE) {
            ;
        }

        assertTokenType(COMMENT, type);
        assertEquals(" another comment! ", getAndVerifyText(sr));

        while ((type = sr.next()) == SPACE) {
            ;
        }
        assertTokenType(END_DOCUMENT, type);
    }

    public void testInvalidEpilog()
        throws XMLStreamException
    {
        /* Once again, ns/non-ns shouldn't matter... but you
         * never know
         */
        doTestInvalid(false);
        doTestInvalid(true);
    }

    /*
    ////////////////////////////////////////
    // Private methods, shared test code
    ////////////////////////////////////////
     */

    private void doTestInvalid(boolean nsAware)
        throws XMLStreamException
    {
        // Text before the root element:
        String XML = "  yeehaw! <root />";
        try {
            streamThrough(getReader(XML, nsAware));
            fail("Expected an exception for text in prolog");
        } catch (Exception e) {
            ; // good
        }

        // Text after the root element:
        XML = " <root /> foobar";
        try {
            streamThrough(getReader(XML, nsAware));
            fail("Expected an exception for text in epilog");
        } catch (Exception e) {
            ; // good
        }

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
        // Let's coalesce, makes it easier to skip white space
        setCoalescing(f, true);
        setNamespaceAware(f, nsAware);
        setValidating(f, false);
        return constructStreamReader(f, contents);
    }
}
