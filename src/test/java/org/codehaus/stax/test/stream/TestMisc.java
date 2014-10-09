package org.codehaus.stax.test.stream;

import java.util.Random;

import javax.xml.stream.*;

/**
 * Unit test suite that tests miscellaneous stream reader methods, such
 * as require(), getElementText() and nextTag()
 * @author Tatu Saloranta
 */
public class TestMisc
    extends BaseStreamTest
{
    public void testRequire()
        throws XMLStreamException
    {
        String XML =
            "<root>"
            +"<!-- comment --><?proc instr?>"
            +"<tag>Text</tag>"
            +"<tag><![CDATA[xx]]></tag>"
            +"<a:empty xmlns:a='http://foo' />"
            +"</root>"
            ;

        XMLStreamReader sr = getReader(XML, true, true);

        sr.require(START_DOCUMENT, null, null);

        assertTokenType(START_ELEMENT, sr.next());

        assertTokenType(COMMENT, sr.next());
        sr.require(COMMENT, null, null);
        try { // should get an exception due to comments not having names
            sr.require(COMMENT, null, "tag");
            fail("Should have thrown an exception when checking local name of a COMMENT");
        } catch (XMLStreamException e) {
            ; // good
        } catch (IllegalStateException ise) {
            ; // likewise this is ok, as getName() can throw it
        }

        assertTokenType(PROCESSING_INSTRUCTION, sr.next());
        sr.require(PROCESSING_INSTRUCTION, null, null);

        assertTokenType(START_ELEMENT, sr.next());
        sr.require(START_ELEMENT, null, "tag");

        String nsURI = sr.getNamespaceURI();
        try {
            sr.require(START_ELEMENT, "", "tag");
        } catch (XMLStreamException e) {
            fail("Did not expect problems with <tag> match (current ns URI as reported by stream reader = '"+nsURI+"'), got: "+e.getMessage());
        }

        try { // should get an exception due to incorrect ns URI
            sr.require(START_ELEMENT, "http://foo", "tag");
            fail("Should have thrown an exception for incorrect NS URI");
        } catch (XMLStreamException e) {
            ; // good
        }

        assertTokenType(CHARACTERS, sr.next());
        sr.require(CHARACTERS, null, null);

        assertTokenType(END_ELEMENT, sr.next());
        sr.require(END_ELEMENT, null, "tag");
        sr.require(END_ELEMENT, "", "tag");

        assertTokenType(START_ELEMENT, sr.next());

        /* Will get CHARACTERS instead of CDATA, because it's a
         * coalescing reader...
         */
        assertTokenType(CHARACTERS, sr.next());
        sr.require(CHARACTERS, null, null);

        assertTokenType(END_ELEMENT, sr.next());

        assertTokenType(START_ELEMENT, sr.next()); // empty
        sr.require(START_ELEMENT, "http://foo", "empty");
        assertTokenType(END_ELEMENT, sr.next());
        sr.require(END_ELEMENT, "http://foo", "empty");
        sr.require(END_ELEMENT, "http://foo", null);
        sr.require(END_ELEMENT, null, "empty");

        assertTokenType(END_ELEMENT, sr.next());
        sr.require(END_ELEMENT, "", "root");

        assertTokenType(END_DOCUMENT, sr.next());
        sr.require(END_DOCUMENT, null, null);
    }

    public void testGetElementText()
        throws XMLStreamException
    {
        _testGetElementText(false);
        _testGetElementText(true);
    }

    private void _testGetElementText(boolean textual)
        throws XMLStreamException
    {
        String XML = "<root>"
            +"<tag>Got some <!-- comment --> text &apos;n stuff!</tag>"
            +"<tag><?proc instr?>more <![CDATA[stuff]]> </tag>"
            +"<tag>a<?pi?>b<!--comment-->c<!--c-2--><?pi -3?>de</tag>"
            +"</root>"
            ;
        // Special: let's verify using both utf-8 and text-based readers
        XMLInputFactory f = getInputFactory();
        setNamespaceAware(f, true);

        XMLStreamReader sr = textual ? constructCharStreamReader(f, XML)
            : constructUtf8StreamReader(f, XML);

        // First 2 valid cases:
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("Got some  text 'n stuff!", sr.getElementText());
        assertTokenType(END_ELEMENT, sr.getEventType());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("more stuff ", sr.getElementText());
        assertTokenType(END_ELEMENT, sr.getEventType());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("abcde", sr.getElementText());
        assertTokenType(END_ELEMENT, sr.getEventType());

        assertTokenType(END_ELEMENT, sr.next());
        sr.close();
    }

    public void testGetElementTextInvalid()
        throws XMLStreamException
    {
        String XML = "<root>Invalid: <tag><empty /> (no kids allowed!)</tag></root>";
        XMLStreamReader sr = getReader(XML, true, false);
        assertTokenType(START_ELEMENT, sr.next());

        // And then should get proper exception
        try {
            @SuppressWarnings("unused")
            String str = sr.getElementText();
            fail("Expected an exception for nested start element");
        } catch (XMLStreamException ex) {
            ; // ok! (would be nice to check error message but...)
        }
    }

    /**
     * Alternative test that tries to verify that handling of long
     * and non-contiguous text segment also works acceptably.
     */
    public void testGetElementTextLong()
        throws XMLStreamException
    {
        /* Let's do multiple passes, starting with shorter Strings;
         * easier to troubleshoot with shorter, better coverage with
         * longer
         */

        int[] lengths = new int[] { 50, 190, 2400, 9300, 42000 };

        for (int i = 0; i < lengths.length; ++i) {
            int len = lengths[i];
            StringBuffer input = new StringBuffer(len * 3 / 2);
            StringBuffer output = new StringBuffer(len + 100);

            Random r = new Random(i);

            while (output.length() < len) {
                String str = null;
                switch (Math.abs(r.nextInt()) % 7) {
                case 0:
                    {
                        int nr = r.nextInt();
                        // note: linefeed normalization
                        input.append(" ").append(nr).append("\r\n");
                        output.append(" ").append(nr).append("\n");
                    }
                    break;
                case 1:
                    input.append("&amp;");
                    output.append("&");
                    break;
                case 2:
                    input.append("<?processing instr \n.... ?>");
                    break;
                case 3:
                    input.append("<!-- comment\r nr ").append(r.nextInt()).append(" -->");
                    break;
                case 4:
                    str = "abc "+((char) ('A' + i))+" xyz";
                    break;
                case 5:
                    str = ">>> ";
                    break;
                case 6:
                    str = "; ?? (\u00A0, \u1123)";
                    break;        
                }
                if (str != null) {
                    if ((r.nextInt() & 0x3) == 1) {
                        input.append("<![CDATA[").append(str).append("]]>");
                    } else {
                        input.append(str);
                    }
                    output.append(str);
                }
            }

            final String XML = "<root>"+input.toString()+"</root>";
            XMLStreamReader sr = getReader(XML, true, false);
            
            assertTokenType(START_ELEMENT, sr.next());
            String act = sr.getElementText();
            String exp = output.toString();
            assertEquals(exp, act);
            sr.close();
        }
    }

    /**
     * Additional simple test that verifies that element text for
     * an empty element is returned as empty String.
     */
    public void testGetElementTextEmpty()
        throws XMLStreamException
    {
        // First simple case

        String XML = "<root />";
        XMLStreamReader sr = getReader(XML, true, false);

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("", sr.getElementText());
        // and then invalid
        assertTokenType(END_ELEMENT, sr.getEventType());
        assertEquals("root", sr.getLocalName());
        assertTokenType(END_DOCUMENT, sr.next());
        sr.close();

        // Then similar but longer
        XML = "<root><a></a><b /></root>";
        sr = getReader(XML, true, false);

        assertTokenType(START_ELEMENT, sr.next()); // root

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("a", sr.getLocalName());
        assertEquals("", sr.getElementText());
        // and then invalid
        assertTokenType(END_ELEMENT, sr.getEventType());

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("b", sr.getLocalName());
        assertEquals("", sr.getElementText());
        // and then invalid
        assertTokenType(END_ELEMENT, sr.getEventType());

        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertTokenType(END_DOCUMENT, sr.next());
    }

    public void testNextTag()
        throws XMLStreamException
    {
        String XML =
            "<root>   "
            +"<tag>   </tag>"
            +"<tag><leaf />   </tag>"
            +"<tag>text</tag>"
            ;
        XMLStreamReader sr = getReader(XML, true, false);
        // First valid cases:
        assertTokenType(START_ELEMENT, sr.nextTag());
        assertEquals("root", sr.getLocalName());
        assertTokenType(START_ELEMENT, sr.nextTag());
        assertTokenType(END_ELEMENT, sr.nextTag());
        assertEquals("tag", sr.getLocalName());

        assertTokenType(START_ELEMENT, sr.nextTag());
        assertEquals("tag", sr.getLocalName());

        assertTokenType(START_ELEMENT, sr.nextTag());
        assertEquals("leaf", sr.getLocalName());

        assertTokenType(END_ELEMENT, sr.nextTag());
        assertEquals("leaf", sr.getLocalName());

        assertTokenType(END_ELEMENT, sr.nextTag());
        assertEquals("tag", sr.getLocalName());

        // And then invalid:
        assertTokenType(START_ELEMENT, sr.nextTag());
        assertEquals("tag", sr.getLocalName());
        try {
            @SuppressWarnings("unused")
            int type = sr.nextTag();
            fail("Expected an exception for non-whitespace text");
        } catch (XMLStreamException ex) {
            ; // ok!
        }
    }

    public void testNextTagWithCommentsAndPIs()
        throws XMLStreamException
    {
        String XML =
            "<root>   <!-- comment -->   <leaf />\n"
            +"<leaf><?target data.. ?></leaf>\n"
            +"<leaf><!-- comment --><!-- comment --> <!--comment--></leaf>\n"
            +"<leaf><?target data.. ?><!--comment  --><?target?></leaf>\n"
            +"</root>"
            ;
        XMLStreamReader sr = getReader(XML, true, false);

        assertTokenType(START_ELEMENT, sr.nextTag());
        assertEquals("root", sr.getLocalName());

        // First, an empty 'leaf' element
        assertTokenType(START_ELEMENT, sr.nextTag());
        assertEquals("leaf", sr.getLocalName());
        assertTokenType(END_ELEMENT, sr.nextTag());
        assertEquals("leaf", sr.getLocalName());

        // Then one with a single PI in it
        assertTokenType(START_ELEMENT, sr.nextTag());
        assertEquals("leaf", sr.getLocalName());
        assertTokenType(END_ELEMENT, sr.nextTag());
        assertEquals("leaf", sr.getLocalName());

        // Then one with multiple comments
        assertTokenType(START_ELEMENT, sr.nextTag());
        assertEquals("leaf", sr.getLocalName());
        assertTokenType(END_ELEMENT, sr.nextTag());
        assertEquals("leaf", sr.getLocalName());

        // Then one with a mix:
        assertTokenType(START_ELEMENT, sr.nextTag());
        assertEquals("leaf", sr.getLocalName());
        assertTokenType(END_ELEMENT, sr.nextTag());
        assertEquals("leaf", sr.getLocalName());

        // And then the closing root tag
        assertTokenType(END_ELEMENT, sr.nextTag());
        assertEquals("root", sr.getLocalName());
    }

    /**
     * Test that checks that stream reader's behavior at the end of
     * input is compliant. Specifically, an exception should be thrown
     * if one tries to access events beyond END_DOCUMENT.
     */
    public void testEndOfStream()
        throws XMLStreamException
    {
        String XML = "<root>x</root>";
        XMLStreamReader sr = getReader(XML, true, true);

        assertTokenType(START_DOCUMENT, sr.getEventType());
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());
        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(END_DOCUMENT, sr.next());
        assertFalse(sr.hasNext());

        try {
            int type = sr.next();
            fail("Expected NoSuchElementException when trying to access events after END_DOCUMENT returned (but received event "+tokenTypeDesc(type)+")");
        } catch (java.util.NoSuchElementException ne) {
            // good
        } catch (XMLStreamException e) { // wrong exception
            fail("Expected NoSuchElementException; received (type "+e.getClass()+"): "+e);
        }
    }

    /**
     * Simple test case to verify an edge case with isWhiteSpace().
     */
    public void testIsWhiteSpace()
        throws XMLStreamException
    {
        // First, simplest possible
        XMLStreamReader sr = getReader("<root>&#65;?</root>", true, true);
        assertTokenType(START_DOCUMENT, sr.getEventType());
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());
        if (sr.isWhiteSpace()) {
            fail("XMLStreamReader.isWhiteSpace() should return false, text = '"+sr.getText()+"'");
        }
        sr.close();

        // Then just bit more complex
        sr = getReader("<root>\n<x>&#65;?</x></root>", true, true);
        assertTokenType(START_DOCUMENT, sr.getEventType());
        assertTokenType(START_ELEMENT, sr.nextTag());
        assertTokenType(START_ELEMENT, sr.nextTag());
        assertTokenType(CHARACTERS, sr.next());
        if (sr.isWhiteSpace()) {
            fail("XMLStreamReader.isWhiteSpace() should return false, text = '"+sr.getText()+"'");
        }
        sr.close();
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
        return constructStreamReader(f, contents);
    }
}
