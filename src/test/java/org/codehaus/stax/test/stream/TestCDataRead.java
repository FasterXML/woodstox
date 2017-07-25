package org.codehaus.stax.test.stream;

import java.io.*;

import javax.xml.stream.*;

/**
 * Unit test suite that tests that the stream reader does in fact
 * coalesce adjacent text/CDATA segments when told to do so.
 */
public class TestCDataRead
    extends BaseStreamTest
{
    final static String CDATA1;
    final static String CDATA2;
    static {
        StringBuffer sb1 = new StringBuffer(8000);
        StringBuffer sb2 = new StringBuffer(8000);

        sb1.append("...");
        sb2.append("\n \n\n ");

        /* Let's add enough stuff to probably cause segmentation...
         */
        for (int i = 0; i < 200; ++i) {
            String txt = "Round #"+i+"; & that's fun: &x"+i+"; <> %xx; &ent  <<< %%% <![CDATA ]]  > ]> ";
            sb1.append(txt);
            sb1.append("  ");
            sb2.append("\n");
            sb2.append(txt);
        }

        CDATA1 = sb1.toString();
        CDATA2 = sb2.toString();
    }

    final static String CDATA3 = " ]] ";

    final static String EXP_CDATA = CDATA1 + CDATA2 + CDATA3;

    final static String VALID_XML =
        "<root>"
        +"<![CDATA["+CDATA1+"]]>"
        +"<![CDATA["+CDATA2+"]]>"
        +"<![CDATA[]]>"
        +"<![CDATA["+CDATA3+"]]>"
        +"</root>";

    /**
     * This test verifies that no character quoting need (or can) be
     * done within CDATA section.
     */
    public void testCDataSimple()
        throws XMLStreamException
    {
        String XML = "<doc><![CDATA[<&]>]]]></doc>";
        String EXP = "<&]>]";
        XMLStreamReader sr = getReader(XML, true);
        assertTokenType(START_ELEMENT, sr.next());
        // In coalescing mode, all CDATA are reported as CHARACTERS
        assertTokenType(CHARACTERS, sr.next());
        String act = getAndVerifyText(sr);
        assertEquals(EXP, act);
        assertTokenType(END_ELEMENT, sr.next());
    }

    public void testCDataCoalescing()
        throws XMLStreamException
    {
        XMLStreamReader sr = getReader(VALID_XML, true);
        assertTokenType(START_ELEMENT, sr.next());
        // In coalescing mode, all CDATA are reported as CHARACTERS
        assertTokenType(CHARACTERS, sr.next());
        String act = getAndVerifyText(sr);
        assertEquals(EXP_CDATA, act);
        assertTokenType(END_ELEMENT, sr.next());
    }

    public void testCDataNonCoalescing()
        throws XMLStreamException
    {
        XMLStreamReader sr = getReader(VALID_XML, false);
        assertTokenType(START_ELEMENT, sr.next());
        int type = sr.next();
        /* 07-Dec-2004, TSa: StAX specs actually allow returning
         *   CHARACTERS too...
         */
        if (type != CHARACTERS) {
            assertEquals("Unexpected token type ("
                         +tokenTypeDesc(type)
                         +") returned; expected CDATA or CHARACTERS",
                         CDATA, type);
        }

        StringBuffer sb = new StringBuffer(16000);
        do {
            sb.append(getAndVerifyText(sr));
            type = sr.next();
        } while (type == CDATA || type == CHARACTERS);
        assertEquals(EXP_CDATA, sb.toString());
        assertTokenType(END_ELEMENT, sr.getEventType());
    }

    public void testInvalidCData()
        throws XMLStreamException
    {
        String XML = "<root><![CDATA[   </root>";
        String MSG = "unfinished CDATA section";
        streamThroughFailing(getReader(XML, false), MSG);
        streamThroughFailing(getReader(XML, true), MSG);

        XML = "<root><![CDATA  [text]]>   </root>";
        MSG = "malformed CDATA section";
        streamThroughFailing(getReader(XML, false), MSG);
        streamThroughFailing(getReader(XML, true), MSG);

        XML = "<root><!  [ CDATA  [text]]>   </root>";
        streamThroughFailing(getReader(XML, false), MSG);
        streamThroughFailing(getReader(XML, true), MSG);

        XML = "<root><![CDATA[text   ]] >   </root>";
        streamThroughFailing(getReader(XML, false), MSG);
        streamThroughFailing(getReader(XML, true), MSG);
    }

    /**
     * This unit test verifies that nested CData sections cause
     * an error. It is related to another test, which just checks
     * that ]]> (with no quoting) is illegal, but parsers may deal
     * with them differently.
     *<p>
     * Note: this is directly based on XMLTest/SAXTest #735.
     */
    public void testInvalidNestedCData()
        throws XMLStreamException
    {
        String XML = "<doc>\n<![CDATA[\n"
            +"<![CDATA[XML doesn't allow CDATA sections to nest]]>\n"
            +"\n]]>\n</doc>";

        main_loop:
        for (int i = 0; i < 2; ++i) {
            boolean coal = (i > 0);
            XMLStreamReader sr = getReader(XML, coal);
            assertTokenType(START_ELEMENT, sr.next());
            // Ok, now should get an exception...
            StringBuffer sb = new StringBuffer();
            int type = -1;
            try {
                while (true) {
                    type = sr.next();
                    if (type != CDATA && type != CHARACTERS) {
                        break;
                    }
                    sb.append(getAndVerifyText(sr));
                }
            } catch (XMLStreamException sex) {
                // good
                continue;
            } catch (RuntimeException rex) {
                /* Hmmh. Some implementations may throw a runtime exception,
                 * if things are lazily parsed (for example, Woodstox).
                 * But let's allow this only if a nested exception is
                 * of proper type
                 */
                Throwable t = rex;
                while (t != null) {
                    if (t instanceof XMLStreamException) {
                        continue main_loop;
                    }
                    t = t.getCause();
                }
                fail("Expected an XMLStreamException for nested CDATA section (coalescing: "+coal+"); instead got exception ("+rex.getClass()+"): "+rex.getMessage());
            }
            fail("Expected an exception for nested CDATA section (coalescing: "+coal+"); instead got text \""+sb.toString()+"\" (next event "+tokenTypeDesc(type)+")");
        }
    }

    // [WSTX-294]: Incorrect coalescing in some cases
    public void testIssue294() throws Exception
    {
        XMLInputFactory f = getInputFactory();
        setCoalescing(f, true);

        InputStream in = getClass().getResource("issue294.xml").openStream();

        // Important: only occurs when we construct a Reader -- not with InputStream
        // (different offsets, perhaps?)
        XMLStreamReader sr = f.createXMLStreamReader(new InputStreamReader(in, "UTF-8"));

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("Envelope", sr.getLocalName());
        assertTokenType(CHARACTERS, sr.next()); // white space
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("Body", sr.getLocalName());
        assertTokenType(CHARACTERS, sr.next()); // white space
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("helloResponse", sr.getLocalName());
        assertTokenType(CHARACTERS, sr.next()); // white space
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("return", sr.getLocalName());

        assertTokenType(CHARACTERS, sr.next());

        String text = getAndVerifyText(sr);

        // Should start with "abcde"
        if (!text.startsWith("abcde")) {
            if (text.length() > 5) {
                text = text.substring(0, 5);
            }
            fail("Expected cdata in 'return' element to start with 'abcde': instead got: '"+text+"'");
        }
        
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("return", sr.getLocalName());
        assertTokenType(CHARACTERS, sr.next());
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("helloResponse", sr.getLocalName());
        assertTokenType(CHARACTERS, sr.next());
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("Body", sr.getLocalName());
        assertTokenType(CHARACTERS, sr.next());
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("Envelope", sr.getLocalName());

        sr.close();
        in.close();
    }

    // [woodstox-core#21]: CDATA contents truncated to buffer size (500 initially)
    public void testLongerCData2() throws Exception
    {
        String SRC_TEXT =
                "\r\n123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678\r\n"
                          + "<embededElement>Woodstox 4.0.5 does not like this embedded element.  However, if you take\r\n"
                          + "out one or more characters from the really long line (so that less than 500 characters come between\r\n"
                          + "'CDATA[' and the opening of the embeddedElement tag (including LF), then Woodstox will instead\r\n"
                          + "complain that the CDATA section wasn't ended.";
        String DST_TEXT = SRC_TEXT.replace("\r\n", "\n");
        String XML = "<?xml version='1.0' encoding='utf-8'?>\r\n"
                     + "<test><![CDATA[" + SRC_TEXT + "]]></test>";
        XMLInputFactory f = getInputFactory();
        // important: don't force coalescing, that'll convert CDATA to CHARACTERS
        f.setProperty(XMLInputFactory.IS_COALESCING, Boolean.FALSE);

        XMLStreamReader sr = f.createXMLStreamReader(new StringReader(XML));
        assertTokenType(START_DOCUMENT, sr.getEventType());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("test", sr.getLocalName());
        assertTokenType(CDATA, sr.next());
        // This should still work, although with linefeed replacements
        final String text = sr.getText();
        if (text.length() != DST_TEXT.length()) {
            fail("Length expected as "+DST_TEXT.length()+", was "+text.length());
        }
        if (!text.equals(DST_TEXT)) {
            fail("Length as expected ("+DST_TEXT.length()+"), contents differ:\n"+text);
        }
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();
    }

    // [woodstox-core#22]: and some CDATA contents truncation via different codepath
    public void testLongerCData3() throws Exception {
        String SRC_TEXT =
            "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678\r\n"
                + "<embededElement>Woodstox 4.0.5 does not like this embedded element.  However, if you take\r\n"
                + "out one or more characters from the really long line (so that less than 500 characters come between\r\n"
                + "'CDATA[' and the opening of the embeddedElement tag (including LF), then Woodstox will instead\r\n"
                + "complain that the CDATA section wasn't ended.";
        String DST_TEXT = SRC_TEXT.replace("\r\n", "\n");
        String XML = "<?xml version='1.0' encoding='utf-8'?>\r\n"
            + "<test><![CDATA[" + SRC_TEXT + "]]></test>";
        XMLInputFactory f = getInputFactory();
        // important: don't force coalescing, that'll convert CDATA to CHARACTERS
        f.setProperty(XMLInputFactory.IS_COALESCING, Boolean.FALSE);

        XMLStreamReader sr = f.createXMLStreamReader(new StringReader(XML));
        assertTokenType(START_DOCUMENT, sr.getEventType());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("test", sr.getLocalName());
        assertTokenType(CDATA, sr.next());
        // This should still work, although with linefeed replacements
        String text = sr.getText();
        if (text.length() != DST_TEXT.length()) {
            System.err.println("DEBUG: initial length = "+text.length());
            while (sr.next() == CDATA) {
                text += sr.getText();
                System.err.println("DEBUG: another CDATA, len now: "+text.length());
            }
//            fail("Length expected as " + DST_TEXT.length() + ", was " + text.length());
        }
        if (!text.equals(DST_TEXT)) {
            fail("Length as expected (" + DST_TEXT.length() + "), contents differ:\n" + text);
        }
//        assertTokenType(END_ELEMENT, sr.next());
        sr.close();
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Private methods, other
    ///////////////////////////////////////////////////////////////////////
     */

    private XMLStreamReader getReader(String contents, boolean coalescing)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setCoalescing(f, coalescing);
        setReplaceEntities(f, true);
        setValidating(f, false);
        return constructStreamReader(f, contents);
    }

}
