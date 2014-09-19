package org.codehaus.stax.test.stream;

import javax.xml.stream.*;

/**
 * Unit test suite that tests that the stream reader does in fact
 * coalesce adjacent text/CDATA segments when told to do so.
 */
public class TestTextCoalescing
    extends BaseStreamTest
{
    final static String VALID_XML = "<root>Text <![CDATA[cdata\n"
        +"in two lines]]><![CDATA[!]]>/that's all!</root>";

    public void testCoalescing()
        throws XMLStreamException
    {
        XMLStreamReader sr = getReader(VALID_XML, true, true);
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());

        String act = getAndVerifyText(sr);
        String exp = "Text cdata\nin two lines!/that's all!";

        if (!exp.equals(act)) {
            failStrings("Coalescing failed", exp, act);
        }

        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(END_DOCUMENT, sr.next());
    }

    /**
     * Test that ensures that even when just skipping (ie not accessing
     * any data), we'll still see just one event for the whole text
     */
    public void testCoalescingSkipping()
        throws XMLStreamException
    {
        XMLStreamReader sr = getReader(VALID_XML, true, true);
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());
        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(END_DOCUMENT, sr.next());
    }

    public void testNonCoalescing()
        throws XMLStreamException
    {
        // VALID_XML = "<root>Text <![CDATA[cdata\nin two lines]]><![CDATA[!]]>/that's all!</root>";

        XMLStreamReader sr = getReader(VALID_XML, true, false);
        assertTokenType(START_ELEMENT, sr.next());

        /* Can not assume that all events are returned in one call...
         * so let's play it safe:
         */
        sr.next();
        checkText(sr, CHARACTERS, "Text ");
        int count = checkText(sr, CDATA, "cdata\nin two lines!");
        if (count < 2) {
            // Can't easily check boundaries... well, could but...
            fail("Expected at least two CDATA events; parser coalesced them");
        }
        checkText(sr, CHARACTERS, "/that's all!");

        assertTokenType(END_ELEMENT, sr.getEventType());
        assertTokenType(END_DOCUMENT, sr.next());
    }

    public void testNonCoalescingSkipping()
        throws XMLStreamException
    {
        // VALID_XML = "<root>Text <![CDATA[cdata\nin two lines]]><![CDATA[!]]>/that's all!</root>";

        XMLStreamReader sr = getReader(VALID_XML, true, false);
        assertTokenType(START_ELEMENT, sr.next());

        assertTokenType(CHARACTERS, sr.next());

        /* ugh. Since implementations are allowed to return CHARACTERS,
         * instead of CDATA, we can only check that we get at least
         * 4 segments of any type...
         */
        // Now, we may get more than one CHARACTERS
        int count = 1;
        StringBuffer sb = new StringBuffer();
        sb.append('[');
        sb.append(sr.getText());
        sb.append(']');
        int type;

        while (true) {
            type = sr.next();
            if (type != CHARACTERS && type != CDATA) {
                break;
            }
            ++count;
            sb.append('[');
            sb.append(sr.getText());
            sb.append(']');
        }
        if (count < 4) {
            fail("Expected at least 4 separate segments (CDATA/CHARACTERS), in non-coalescing mode, got "+count+" (text: "+sb+")");
        }

        assertTokenType(END_ELEMENT, type);
        assertTokenType(END_DOCUMENT, sr.next());
    }

    public void testInvalidTextWithCDataEndMarker()
        throws XMLStreamException
    {
        String XML = "<root>   ]]>   </root>";

        streamThroughFailing(getReader(XML, true, true),
                             "text content that has ']]>' in it.");
    }

    /*
    ////////////////////////////////////////
    // Private methods, other
    ////////////////////////////////////////
     */

    private XMLStreamReader getReader(String contents, boolean nsAware,
                                      boolean coalescing)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setNamespaceAware(f, nsAware);
        setSupportDTD(f, true);
        setCoalescing(f, coalescing);
        setReplaceEntities(f, true);
        setValidating(f, false);
        // 13-Mar-2006, TSa: Let's try to get accurate CDATA reporting...
        setReportCData(f, true);
        return constructStreamReader(f, contents);
    }

    private int checkText(XMLStreamReader sr, int expType, String exp)
        throws XMLStreamException
    {
        assertTokenType(expType, sr.getEventType());
        //if (expType != sr.getEventType()) System.err.println("WARN: expected "+tokenTypeDesc(expType)+", got "+tokenTypeDesc(sr.getEventType()));
        StringBuffer sb = new StringBuffer(getAndVerifyText(sr));
        int count = 1;
        while ((sr.next()) == expType) {
            ++count;
            sb.append(getAndVerifyText(sr));
        }
        String act = sb.toString();
        if (!exp.equals(act)) {
            failStrings("Incorrect text contents", act, exp);
        }
        return count;
    }
}
