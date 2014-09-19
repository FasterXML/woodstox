package org.codehaus.stax.test.stream;

import java.io.*;

import javax.xml.stream.*;

/**
 * Unit test suite that ensures that the 'segmented' text accessors
 * (multi-argument getTextCharacters) works as expected, with various
 * combinations of access lengths, and orderings.
 *
 * @author Tatu Saloranta
 */
public class TestGetSegmentedText
    extends BaseStreamTest
{
    static String sXmlInput = null;
    static String sExpResult = null;

    public void testCoalescingAutoEntity()
        throws Exception
    {
        doTest(false, true, true); // non-ns
        doTest(true, true, true); // ns-aware
    }

    public void testNonCoalescingAutoEntity()
        throws Exception
    {
        doTest(false, false, true); // non-ns
        doTest(true, false, true); // ns-aware
    }

    public void testCoalescingNonAutoEntity()
        throws Exception
    {
        doTest(false, true, false); // non-ns
        doTest(true, true, false); // ns-aware
    }

    public void testNonCoalescingNonAutoEntity()
        throws Exception
    {
        doTest(false, false, false); // non-ns
        doTest(true, false, false); // ns-aware
    }

    public void testSegmentedGetCharacters()
        throws XMLStreamException
    {
        final String TEXT = "Let's just add some content in here ('') to fill some of the parser buffers, to test multi-argument getTextCharacters() method";
        final String XML = "<!--comment--><root><?proc instr?>"+TEXT+"</root>";

		XMLInputFactory f = getFactory(true, false, true);
		XMLStreamReader sr = constructStreamReader(f, XML);

        // May or may not get the prolog comment
        int type = sr.next();
        if (type == COMMENT) {
            type = sr.next();
        }
        assertTokenType(START_ELEMENT, type);
        assertTokenType(PROCESSING_INSTRUCTION, sr.next());
        type = sr.next();
        assertTokenType(CHARACTERS, type);

        /* Ok... let's just access all the text, by one char reads, from
         * possibly multiple events:
         */
        StringBuffer sb = new StringBuffer();
          while (type == CHARACTERS) {
            char[] buf = new char[5];
            int offset = 0;
            int count;

            while (true) { // let's use 2 different size of requests...
                int start, len;

                if ((offset & 1) == 0) {
                    start = 2;
                    len = 1;
                } else {
                    start = 0;
                    len = buf.length;
                }
                count = sr.getTextCharacters(offset, buf, start, len);
                if (count > 0) {
                    sb.append(buf, start, count);
                    offset += count;
                }
                if (count < len) {
                    break;
                }
            }

            type = sr.next();
        }

        assertEquals(TEXT, sb.toString());
        assertTokenType(END_ELEMENT, type);
    }

    /*
    ////////////////////////////////////////
    // Private methods, common test code
    ////////////////////////////////////////
     */

    private void doTest(boolean ns, boolean coalescing, boolean autoEntity)
        throws Exception
    {
        // This is bit hacky, but speeds up testing...
        if (sXmlInput == null) {
            initData();
        }

        // And let's also check using different buffer sizes:
        for (int sz = 0; sz < 3; ++sz) {
            // Let's test different input methods too:
            for (int j = 0; j < 3; ++j) {
		
                XMLInputFactory f = getFactory(ns, coalescing, autoEntity);
                XMLStreamReader sr;
		
                switch (j) {
                case 0: // simple StringReader:
                    sr = constructStreamReader(f, sXmlInput);
                    break;
                case 1: // via InputStream and auto-detection
                    /* It shouldn't really contain anything outside ISO-Latin;
                     * however, detection may be tricky.. so let's just
                     * test with UTF-8, for now?
                     */
                    {
                        ByteArrayInputStream bin = new ByteArrayInputStream
                            (sXmlInput.getBytes("UTF-8"));
                        sr = f.createXMLStreamReader(bin);
                    }
                    break;
                case 2: // explicit UTF-8 stream
                    {
                        ByteArrayInputStream bin = new ByteArrayInputStream
                            (sXmlInput.getBytes("UTF-8"));
                        Reader br = new InputStreamReader(bin, "UTF-8");
                        sr = f.createXMLStreamReader(br);
                    }
                    break;
                default: throw new Error("Internal error");
                }
		
                char[] cbuf;

                if (sz == 0) {
                    cbuf = new char[23];
                } else if (sz == 1) {
                    cbuf = new char[384];
                } else {
                    cbuf = new char[4005];
                }

                assertTokenType(START_ELEMENT, sr.next());
                int segCount = 0;
                int totalLen = sExpResult.length();
                StringBuffer totalBuf = new StringBuffer(totalLen);

                /* Ok; for each segment let's test separately first,
                 * and then combine all the results together as well
                 */
                while (sr.next() == CHARACTERS) {
                    // Where are we within the whole String?
                    int segOffset = totalBuf.length();

                    ++segCount;
                    // Should not get multiple when coalescing...
                    if (coalescing && segCount > 1) {
                        fail("Didn't expect multiple CHARACTERS segments when coalescing: first segment contained "+segOffset+" chars from the whole expected "+totalLen+" chars");
                    }
                    StringBuffer sb = new StringBuffer();
                    int count;
                    int offset = 0;
                    int readCount = 0;

                    while ((count = sr.getTextCharacters(offset, cbuf, 0, cbuf.length)) > 0) {
                        ++readCount;
                        sb.append(cbuf, 0, count);
                        offset += count;
                    }
                    int expLen = sr.getTextLength();

                    // Sanity check #1: should get matching totals
                    assertEquals
                        ("Expected segment #"+segOffset+" (one-based; read with "+readCount+" reads) to have length of "
                         +expLen+"; reported to have gotten just "+offset+" chars",
                         expLen, offset);

                    // Sanity check #2: and string buf should have it too
                    assertEquals
                        ("Expected segment #"+segOffset+" (one-based; read with "+readCount+" reads) to get "
                         +expLen+" chars; StringBuffer only has "+sb.length(),
                         expLen, sb.length());
		    
                    totalBuf.append(sb);
                }
                assertTokenType(END_ELEMENT, sr.getEventType());
        
                // Ok; all gotten, does it match?
                assertEquals("Expected total of "+totalLen+" chars, got "+totalBuf.length(),
                             sExpResult.length(), totalBuf.length());

                // Lengths are ok,  but how about content?
                if (!sExpResult.equals(totalBuf.toString())) {
                    // TODO: indicate where they differ?
                    String str1 = sExpResult;
                    String str2 = totalBuf.toString();
                    int len = str1.length();
                    int i = 0;
                    char c1 = 'x', c2 = 'x';

                    for (; i < len; ++i) {
                        c1 = str1.charAt(i);
                        c2 = str2.charAt(i);
                        if (c1 != c2) {
                            break;
                        }
                    }
                    fail("Expected Strings to equal; differed at character #"+i+" (length "+len+" was correct); expected '"+c1+"' ("+((int) c1)+"), got '"+c2+"' ("+((int) c2)+")");
                    
                    sr.close();
                }
            }
        }
    }

    /*
    ////////////////////////////////////////
    // Private methods, other
    ////////////////////////////////////////
     */

    private XMLInputFactory getFactory(boolean nsAware,
                                       boolean coalescing, boolean autoEntity)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setNamespaceAware(f, nsAware);
        setCoalescing(f, coalescing);
        setReplaceEntities(f, autoEntity);

        setSupportDTD(f, true);
        setValidating(f, false);
        return f;
    }

    private void initData()
        throws XMLStreamException
    {
        StringBuffer sb = new StringBuffer("<?xml version='1.0'?>");
        sb.append("<root>");
        
        /* Let's create a ~64kchar text segment for testing, first; and one
         * including stuff like linefeeds and (pre-defined) entities.
         */
        while (sb.length() < 65000) {
            sb.append("abcd efgh\r\nijkl &amp; mnop &lt; &gt; qrst\n uvwx\r yz &#65;");
        }
        
        sb.append("</root>");
        final String XML = sb.toString();
        
        /* But more than that, let's also see what we should get
         * as a result...
         */
        XMLInputFactory f = getFactory(true, false, true);
        XMLStreamReader sr = constructStreamReader(f, XML);
        assertTokenType(START_ELEMENT, sr.next());
        StringBuffer sb2 = new StringBuffer(XML.length());
        while (sr.next() == CHARACTERS) {
            sb2.append(sr.getText());
        }
        
        sXmlInput = XML;
        sExpResult = sb2.toString();
    }
}
