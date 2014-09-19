package org.codehaus.stax.test.stream;

import javax.xml.stream.*;

/**
 * Unit test suite that tests linefeed normalization features of parsers.
 */
public class TestLinefeeds
    extends BaseStreamTest
{
    final String IN_SPACES1  = "  \r \n  \r\n   ";
    final String OUT_SPACES1 = "  \n \n  \n   ";

    final String IN_SPACES2  = "\r\r \n \r";
    final String OUT_SPACES2 = "\n\n \n \n";

    final String IN_SPACES3  = "  \r\n  \r\n \r\n";
    final String OUT_SPACES3 = "  \n  \n \n";

    final String IN_MIXED1  = "Something\nwonderful (?)\rhas...\r\r\n happened ";
    final String OUT_MIXED1 = "Something\nwonderful (?)\nhas...\n\n happened ";

    /**
     * Test that checks that if ignorable whitespace is reported from
     * epilog and/or prolog, it will be properly normalized.
     */
    public void testLfInEpilog()
        throws XMLStreamException
    {
        final String contents = IN_SPACES1+"<root />"+IN_SPACES2;

        for (int i = 0; i < 4; ++i) {
            XMLInputFactory f = getInputFactory();
            boolean coal = ((i & 1) == 0);
            boolean ns = ((i & 2) == 0);
            setCoalescing(f, coal);
            setNamespaceAware(f, ns);
            XMLStreamReader sr = constructStreamReader(f, contents);

            /* Since reporting (ignorable) white space is optional, have to
             * be careful...
             */
            int type = sr.next();
            if (type == SPACE) { // ok
                String str = getAndVerifyText(sr);
                while ((type = sr.next()) == SPACE) {
                    str += getAndVerifyText(sr);
                }
                assertEquals(printable(OUT_SPACES1), printable(str));
            }

            // Either way, needs to have the root element now
            assertEquals(START_ELEMENT, type);
            assertEquals("root", sr.getLocalName());
            assertNoPrefix(sr);

            // And then matching virtual close
            assertEquals(END_ELEMENT, sr.next());
            assertEquals("root", sr.getLocalName());
            assertNoPrefix(sr);

            // And then we may get more whitespace:
            type = sr.next();
            if (type == SPACE) { // ok
                String str = getAndVerifyText(sr);
                while ((type = sr.next()) == SPACE) {
                    str += getAndVerifyText(sr);
                }
                if (!str.equals(OUT_SPACES2)) {
                    String exp = printable(OUT_SPACES2);
                    String act = printable(str);
                    fail("Failed (coalesce: "+coal+", ns-aware: "+ns+"); expected '"+exp+"', got '"+act+"'.");
                }
            }

            assertEquals(END_DOCUMENT, type);
        }
    }

    public void testLfInCData()
        throws XMLStreamException
    {
        /* Split into separate calls, to make it easier to see which
         * combination failed (from stack trace)
         */
        doTestLfInCData(false, false);
        doTestLfInCData(false, true);
        doTestLfInCData(true, false);
        doTestLfInCData(true, true);
    }

    private void doTestLfInCData(boolean ns, boolean coalescing)
        throws XMLStreamException
    {
        final String contents = "<root><![CDATA["
            +IN_SPACES1+IN_SPACES2+"]]></root>";

        XMLInputFactory f = getInputFactory();
        setCoalescing(f, coalescing);
        setNamespaceAware(f, ns);
        XMLStreamReader sr = constructStreamReader(f, contents);
        
        // Then should get the root element:
        // Either way, needs to have the root element now
        assertEquals(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertNoPrefix(sr);
        
        /* Then we will get either CDATA or CHARACTERS type; let's
         * NOT do thorough check here -- that'll be up to specific
         * CDATA unit tests on a separate suite.
         */
        int type = sr.next();
        
        assertTrue("Expected either CDATA or CHARACTERS event, got "+type,
                   (type == CDATA || type == CHARACTERS));
        
        String str = getAndVerifyText(sr);
        /* If we are not coalescing, data can (in theory) be split
         * up...
         */
        if (coalescing) {
            type = sr.next();
        } else {
            while (true) {
                type = sr.next();
                if (type != CDATA && type != CHARACTERS) {
                    break;
                }
                str += getAndVerifyText(sr);
            }
        }
        
        String exp = OUT_SPACES1+OUT_SPACES2;
        if (!str.equals(exp)) {
            fail("Failed (coalesce: "+coalescing+", ns-aware: "+ns+"); expected '"
                 +printable(exp)+"', got '"+printable(str)+"'.");
        }
        
        // Plus, should get the close element too
        assertEquals(END_ELEMENT, type);
        assertEquals("root", sr.getLocalName());
        assertNoPrefix(sr);
        
        // And then the end doc
        assertEquals(END_DOCUMENT, sr.next());
    }

    public void testLfInProcInstr()
        throws XMLStreamException
    {
        /* Since exact handling of the white space between target and
         * data is not well-defined by the specs, let's just add markers
         * and trim such white space out...
         */
        final String contents = "<root><?target  ["
            +IN_SPACES1+IN_SPACES2+"]?>"
            +"<?target ["+IN_SPACES3+"]?></root>";

        /* There really shouldn't be any difference between coalescing/non
         * or namespace aware/non-ns modes, let's try out the combinations
         * just in case
         */
        for (int i = 0; i < 4; ++i) {
            XMLInputFactory f = getInputFactory();
            setCoalescing(f, ((i & 1) == 0));
            setNamespaceAware(f, ((i & 2) == 0));

            XMLStreamReader sr = constructStreamReader(f, contents);

            // Then should get the root element:
            // Either way, needs to have the root element now
            assertEquals(START_ELEMENT, sr.next());
            assertEquals("root", sr.getLocalName());
            assertNoPrefix(sr);

            assertEquals(PROCESSING_INSTRUCTION, sr.next());
            assertEquals("target", sr.getPITarget());

            // Ok, how about the contents:
            String data = sr.getPIData();
            String exp = "["+OUT_SPACES1+OUT_SPACES2+"]";

            assertEquals(printable(exp), printable(data));

            // And some more white space + lf handling:
            assertEquals(PROCESSING_INSTRUCTION, sr.next());
            assertEquals("target", sr.getPITarget());

            data = sr.getPIData();
            exp = "["+OUT_SPACES3+"]";
            assertEquals(printable(exp), printable(data));

            // Plus, should get the close element too
            assertEquals(END_ELEMENT, sr.next());
            assertEquals("root", sr.getLocalName());
            assertNoPrefix(sr);

            // And then the end doc
            assertEquals(END_DOCUMENT, sr.next());
        }
    }

    public void testLfInComment()
        throws XMLStreamException
    {
        final String contents = "<root>"
            +"<!--"+IN_SPACES1+"-->"
            +"<!--"+IN_SPACES2+"-->"
            +"<!--"+IN_SPACES3+"-->"
            +"<!--"+IN_MIXED1+"-->"
            +"</root>";

        /* There really shouldn't be any difference between coalescing/non
         * or namespace aware/non-ns modes, but let's try out the combinations
         * just in case (some implementations may internally have differing
         * handling)
         */
        for (int i = 0; i < 4; ++i) {
            XMLInputFactory f = getInputFactory();
            setCoalescing(f, ((i & 1) == 0));
            setNamespaceAware(f, ((i & 2) == 0));

            XMLStreamReader sr = constructStreamReader(f, contents);

            // Then should get the root element:
            // Either way, needs to have the root element now
            assertEquals(START_ELEMENT, sr.next());
            assertEquals("root", sr.getLocalName());
            assertNoPrefix(sr);

            assertEquals(COMMENT, sr.next());
            assertEquals(printable(OUT_SPACES1), printable(sr.getText()));
            assertEquals(COMMENT, sr.next());
            assertEquals(printable(OUT_SPACES2), printable(sr.getText()));
            assertEquals(COMMENT, sr.next());
            assertEquals(printable(OUT_SPACES3), printable(sr.getText()));
            assertEquals(COMMENT, sr.next());
            assertEquals(printable(OUT_MIXED1), printable(sr.getText()));

            // Plus, should get the close element too
            assertEquals(END_ELEMENT, sr.next());
            assertEquals("root", sr.getLocalName());
            assertNoPrefix(sr);

            // And then the end doc
            assertEquals(END_DOCUMENT, sr.next());
        }
    }

    public void testLfInText()
        throws XMLStreamException
    {
        final String contents = "<root>"+IN_SPACES1+IN_SPACES2+"</root>";

        for (int i = 0; i < 4; ++i) { // to test coalescing and non-coalescing
            XMLInputFactory f = getInputFactory();
            boolean coalescing = ((i & 1) == 0);
            setCoalescing(f, coalescing);
            setNamespaceAware(f, ((i & 2) == 0));
            XMLStreamReader sr = constructStreamReader(f, contents);

            assertEquals(START_ELEMENT, sr.next());
            assertEquals(CHARACTERS, sr.next());
            
            int type;
            String str = getAndVerifyText(sr);

            /* If we are not coalescing, data can be split
             * up... (but in practice would probably need longer input
             * text?)
             */
            if (coalescing) {
                type = sr.next();
            } else {
                while (true) {
                    type = sr.next();
                    if (type != CDATA && type != CHARACTERS) {
                        break;
                    }
                    str += getAndVerifyText(sr);
                }
            }

            assertEquals(printable(OUT_SPACES1+OUT_SPACES2),
                         printable(str));

            // Plus, should get the close element too
            assertEquals(END_ELEMENT, type);
            // And then the end doc
            assertEquals(END_DOCUMENT, sr.next());
        }
    }
}
