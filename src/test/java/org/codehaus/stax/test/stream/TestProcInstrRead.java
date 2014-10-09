package org.codehaus.stax.test.stream;

import javax.xml.stream.*;

/**
 * Unit test suite that tests handling of XML processing instructions (except
 * for the linefeed normalization which is tested elsewhere); mostly just what
 * properties is the stream reader returning when pointing to a comment.
 */
public class TestProcInstrRead
    extends BaseStreamTest
{
    /**
     * Method that checks properties of PROCESSING_INSTRUCTION
     * returned by the stream reader are correct according to StAX specs.
     */
    public void testProcInstrProperties()
        throws XMLStreamException
    {
        /* Neither ns-awareness nor dtd-support should make any differnece,
         * but let's double check them...
         */
        doTestProperties(true, true);
        doTestProperties(true, false);
        doTestProperties(false, true);
        doTestProperties(false, false);
    }

    public void testSpaceHandling()
        throws XMLStreamException
    {
        String CONTENT_TEXT = "some   data ";
        String CONTENT = "   "+CONTENT_TEXT;
        String XML = "<?target   "+CONTENT+"?><root />";
        
        for (int i = 0; i < 3; ++i) {
            boolean ns = (i & 1) != 0;
            boolean dtd = (i & 2) != 0;
            XMLStreamReader sr = getReader(XML, ns, dtd);
            assertTokenType(PROCESSING_INSTRUCTION, sr.next());
            assertEquals("target", sr.getPITarget());
            
            String content = sr.getPIData();
            assertNotNull(content);
            // Is content exactly as expected?
            if (!content.equals(CONTENT_TEXT)) {
                // Nope... but would it be without white space?
                if (CONTENT_TEXT.trim().equals(content.trim())) {
                    fail("Proc. instr. white space handling not correct: expected data '"
                         +CONTENT_TEXT+"', got '"+content+"'");
                }
                // Nah, totally wrong:
                fail("Processing instruction data incorrect: expected '"
                     +CONTENT_TEXT+"', got '"+content+"'");
            }
        }
    }

    public void testInvalidProcInstr()
        throws XMLStreamException
    {
        String XML = "<?xMl Can not use that target!  ?><root />";
        String XML2 = "<?   ?>   <root />";
        String XML3 = "<root><?target data   ></root>";

        for (int i = 0; i < 3; ++i) {
            boolean ns = (i & 1) != 0;
            boolean dtd = (i & 2) != 0;

            streamThroughFailing(getReader(XML, ns, dtd),
                                 "invalid processing instruction target ('xml' [case-insensitive] not legal) [ns: "+ns+", dtd: "+dtd+"]");

            streamThroughFailing(getReader(XML2, ns, dtd),
                                 "invalid processing instruction; empty proc. instr (missing target)");

            streamThroughFailing(getReader(XML3, ns, dtd),
                                 "invalid processing instruction; ends with '?', not \"?>\"");
        }
    }

    public void testUnfinishedPI()
        throws XMLStreamException
    {
        String XML = "<root><!? target data     </root>";

        for (int i = 0; i < 3; ++i) {
            boolean ns = (i & 1) != 0;
            streamThroughFailing(getReader(XML, ns, true),
                                 "invalid proc. instr. (unfinished)");
        }
    }

    /**
     * This unit test checks that the parser does not allow split processing
     * instructions; ones that start from within an entity expansion, but do
     * not completely finish within entity expansion, but in the original
     * input source that referenced the entity.
     * Such markup is illegal according to XML specs.
     */
    public void testRunawayProcInstr()
        throws XMLStreamException
    {
        String XML = "<!DOCTYPE root [\n"
            + "<!ENTITY pi '<?target d'>\n"
            +"]>"
            + "<root>&pi;?></root>";

        XMLStreamReader sr = getReader(XML, true, true);

        try {
            // May get an exception when parsing entity declaration... ?
            // (since it contains partial token)
            assertTokenType(DTD, sr.next());
            assertTokenType(START_ELEMENT, sr.next());
            int type = sr.next();
            if (type != PROCESSING_INSTRUCTION) {
                reportNADueToEntityExpansion("testRunawayProcInstr", type);
                return;
            }
            type = sr.next();
            fail("Expected an exception for split/runaway processing instruction (instead got event "+tokenTypeDesc(type)+")");
        } catch (XMLStreamException sex) {
            // good
        } catch (RuntimeException rex) {
            // some impls. throw lazy exceptions, too...
        }
    }

    /**
     * Unit test based on a bug found in the Stax reference implementation.
     */
    public void testLongerProcInstr()
        throws XMLStreamException
    {
        String XML = "<?xml version='1.0'?>\n\n"
+"<!-- Richard Tobin's XML 1.0 2nd edition errata test suite. \n"
+"     Copyright Richard Tobin, HCRC July 2003.\n"
+"     May be freely redistributed provided copyright notice is retained.\n"
+"  -->\n\n"
+"<?xml-stylesheet href='xmlconformance.xsl' type='text/xsl'?>\n\n"
+"<!DOCTYPE TESTSUITE [\n"
+"    <!ENTITY eduni-errata2e SYSTEM 'errata2e.xml'>\n"
+"]>\n\n"
+"<TESTSUITE PROFILE=\"Richard Tobin's XML 1.0 2nd edition errata test suite 21 Jul 2003\">\n"
+"    &eduni-errata2e;\n"
            +"</TESTSUITE>\n";

        XMLStreamReader sr = getReader(XML, true, true);

        // May get an exception when parsing entity declaration... ?
        // (since it contains partial token)
        int type;

        while ((type = sr.next()) == SPACE) { }
        assertTokenType(COMMENT, type);
        while ((type = sr.next()) == SPACE) { }
        assertTokenType(PROCESSING_INSTRUCTION, type);
        assertEquals("xml-stylesheet", sr.getPITarget());
        while ((type = sr.next()) == SPACE) { }
        assertTokenType(DTD, type);
        while ((type = sr.next()) == SPACE) { }
        assertTokenType(START_ELEMENT, type);
    }

    /*
    ////////////////////////////////////////
    // Private methods, shared test code
    ////////////////////////////////////////
     */

    private void doTestProperties(boolean ns, boolean dtd)
        throws XMLStreamException
    {
        final String DATA = "data & more data (???) <>";
        XMLStreamReader sr = getReader("<?target "+DATA+" ?><root />", ns, dtd);

        assertEquals(PROCESSING_INSTRUCTION, sr.next());

        // Type info
        assertEquals(false, sr.isStartElement());
        assertEquals(false, sr.isEndElement());
        assertEquals(false, sr.isCharacters());
        assertEquals(false, sr.isWhiteSpace());

        // indirect type info
        assertFalse("Processing instructions have no names; XMLStreamReader.hasName() should return false", sr.hasName());
        assertEquals(false, sr.hasText());

        assertNotNull(sr.getLocation());
        if (ns) {
            assertNotNull(sr.getNamespaceContext());
        }

        // And then let's check methods that should throw specific exception
        for (int i = 0; i < 10; ++i) {
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
                    method = "getText";
                    result = sr.getText();
                    break;
                case 7:
                    method = "getTextCharacters";
                    result = sr.getTextCharacters();
                    break;
                case 8:
                    method = "getTextStart";
                    result = new Integer(sr.getTextStart());
                    break;
                case 9:
                    method = "getTextLength";
                    result = new Integer(sr.getTextLength());
                    break;
                }
                fail("Expected IllegalStateException, when calling "
                     +method+"() for PROCESSING_INSTRUCTION");
            } catch (IllegalStateException iae) {
                ; // good
            }
        }

        assertEquals("target", sr.getPITarget());

        /* Now; specs are bit vague WRT white space handling between target
         * and data; thus, let's just trim trailing/leading white space
         */
	/* 13-Nov-2004, TSa: Actually, handling is to get rid
	 *  of leading but not trailing white space, as per XML specs.
	 *  StAX API is not clear, but another test will verify proper
	 *  behaviour.
	 */
        assertEquals(DATA.trim(), sr.getPIData().trim());
    }

    /*
    ////////////////////////////////////////
    // Private methods, other
    ////////////////////////////////////////
     */

    private XMLStreamReader getReader(String contents, boolean nsAware,
                                      boolean supportDTD)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setCoalescing(f, false); // shouldn't really matter
        setNamespaceAware(f, nsAware);
        setSupportDTD(f, supportDTD);
        setValidating(f, false);
        return constructStreamReader(f, contents);
    }
}
