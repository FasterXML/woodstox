package org.codehaus.stax.test.stream;

import javax.xml.stream.*;

/**
 * Unit test suite that tests handling of XML comments (except for the
 * linefeed normalization which is tested elsewhere); mostly just what
 * properties is the stream reader returning when pointing to a comment.
 *
 * @author Tatu Saloranta
 */
public class TestCommentRead
    extends BaseStreamTest
{
    public void testValidComments()
        throws XMLStreamException
    {
        String XML = "<!-- <comment> --><root />  ";
        streamThrough(getReader(XML, true));
        streamThrough(getReader(XML, false));
        XML = "  <root>  </root>  <!-- hee&haw - - -->";
        streamThrough(getReader(XML, true));
        streamThrough(getReader(XML, false));
    }

    /**
     * Method that checks properties of COMMENT
     * returned by the stream reader are correct according to StAX specs.
     */
    public void testCommentProperties()
        throws XMLStreamException
    {
        /* Neither ns-awareness nor dtd-support should make any difference,
         * but let's double check them...
         */
        doTestProperties(true, true);
        doTestProperties(true, false);
        doTestProperties(false, true);
        doTestProperties(false, false);
    }

    public void testInvalidComment()
        throws XMLStreamException
    {
        String XML = "<!-- Can not have '--' in here! -->  <root />";
        String XML2 = "<root><!  -- no spaces either--></root>";
        String XML3 = "<root><!- - no spaces either--></root>";

        for (int i = 0; i < 1; ++i) {
            boolean ns = (i & 1) != 0;

            streamThroughFailing(getReader(XML, ns),
                                 "invalid comment content (embedded \"--\")");
            streamThroughFailing(getReader(XML2, ns),
                                 "malformed comment (extra space)");
            streamThroughFailing(getReader(XML3, ns),
                                 "malformed comment (extra space)");
        }
    }

    public void testUnfinishedComment()
        throws XMLStreamException
    {
        String XML = "<root><!-- Comment that won't end </root>";

        for (int i = 0; i < 1; ++i) {
            boolean ns = (i & 1) != 0;

            streamThroughFailing(getReader(XML, ns),
                                 "invalid comment (unfinished)");
        }
    }

    /**
     * This unit test checks that the parser does not allow split comments;
     * comments that start from within an entity expansion, but have
     * no matching close marker until in the context that had the entity
     * reference.
     *<p>
     * Note: if entity expansion does not work, we will have to skip the
     * test...
     */
    public void testRunawayComment()
        throws XMLStreamException
    {
        String XML = "<!DOCTYPE root [\n"
            + "<!ENTITY comm '<!-- start'>\n"
            +"]>"
            + "<root>&comm;   --></root>";

        XMLStreamReader sr = getReader(XML, true);
        try {
            // May get an exception when parsing entity declaration... ?
            // (since it contains partial token)
            assertTokenType(DTD, sr.next());
            assertTokenType(START_ELEMENT, sr.next());
            int type = sr.next();
            if (type != COMMENT) {
                reportNADueToEntityExpansion("testRunawayComment", type);
                return;
            }
            type = sr.next();
            fail("Expected an exception for split/runaway comment (instead got event "+tokenTypeDesc(type)+")");
        } catch (XMLStreamException sex) {
            // good
        } catch (RuntimeException rex) {
            // some impls. throw lazy exceptions, too...
        }
    }

    public void testLongComments()
        throws XMLStreamException
    {
        final String COMMENT1 =
            "Some longish comment to see if the input buffer size restrictions might apply here: the reference\nimplementation had problems beyond 256 characters\n"
            +" so let's add at least that much, and preferably quite a bit more\n"
            +"too... Blah blah yadda yadda: also, unquoted '&' and '<' are kosher here"
            +"\nwithout any specific problems or issues."
            +" Is this long enough now? :-)"
            ;

        String XML = "<?xml version='1.0'?>"
+"<!--"+COMMENT1+"-->"
+"<?xml-stylesheet href='xmlconformance.xsl' type='text/xsl'?>"
+"<!DOCTYPE root [\n"
+" <!-- comments in DTD --> <?proc instr too?>\n"
+"]><root><!--"+COMMENT1+"--></root>"
            ;
        XMLStreamReader sr = getReader(XML, true);
        assertTokenType(COMMENT, sr.next());
        assertEquals(COMMENT1, getAndVerifyText(sr));
        assertTokenType(PROCESSING_INSTRUCTION, sr.next());
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertTokenType(COMMENT, sr.next());
        assertEquals(COMMENT1, getAndVerifyText(sr));
        assertTokenType(END_ELEMENT, sr.next());
    }

    /*
    ////////////////////////////////////////
    // Private methods, shared test code
    ////////////////////////////////////////
     */

    private void doTestProperties(boolean nsAware, boolean dtd)
        throws XMLStreamException
    {
        XMLStreamReader sr = getReader("<!--comment & <content>--><root/>",
                                       nsAware);
        assertEquals(COMMENT, sr.next());

        // Type info
        assertEquals(false, sr.isStartElement());
        assertEquals(false, sr.isEndElement());
        assertEquals(false, sr.isCharacters());
        assertEquals(false, sr.isWhiteSpace());

        // indirect type info
        assertEquals(false, sr.hasName());
        assertEquals(true, sr.hasText());

        assertNotNull(sr.getLocation());
        if (nsAware) {
            assertNotNull(sr.getNamespaceContext());
        }

        // And then let's check methods that should throw specific exception
        for (int i = 0; i < 8; ++i) {
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
                }
                fail("Expected IllegalStateException, when calling "
                     +method+"() for COMMENT");
            } catch (IllegalStateException iae) {
                ; // good
            }
        }

        String content = getAndVerifyText(sr);
        assertEquals("comment & <content>", content);
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
        setCoalescing(f, false); // shouldn't really matter
        setNamespaceAware(f, nsAware);
        setValidating(f, false);
        return constructStreamReader(f, contents);
    }
}
