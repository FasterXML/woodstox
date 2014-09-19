package wstxtest.stream;

import java.io.*;

import javax.xml.stream.*;

/**
 * This set on unit tests checks that woodstox-specific invariants
 * regarding automatic input encoding detection are maintained. Some
 * of these might be required by stax specification too, but it is not
 * quite certain, thus tests are included in woodstox-specific packages.
 */
public class TestEncodingDetection
    extends BaseStreamTest
{
    final static String ENC_EBCDIC_IN_PREFIX = "cp";

    final static String ENC_EBCDIC_OUT_PREFIX = "IBM";

    public void testUtf8()
        throws IOException, XMLStreamException
    {
        /* Default is, in absence of any other indications, UTF-8...
         * let's check the shortest legal doc:
         */
        String XML = "<a/>";
        byte[] b = XML.getBytes("UTF-8");
        XMLStreamReader sr = getReader(b);
        assertTokenType(START_DOCUMENT, sr.getEventType());
        assertNull(sr.getCharacterEncodingScheme());
        assertEquals("UTF-8", sr.getEncoding());
        // let's iterate just for fun though
        assertTokenType(START_ELEMENT, sr.next());
        sr.close();
    }

    public void testUtf16()
        throws XMLStreamException
    {
        // Should be able to figure out encoding...
        String XML = ".<?xml version='1.0'?><root/>";

        /* Let's first check a somewhat common case; figuring out UTF-16
         * encoded doc (which has to have BOM, thus); first, big-endian
         */
        StringBuffer sb = new StringBuffer(XML);
        sb.setCharAt(0, (char) 0xFEFF);

        byte[] b = getUtf16Bytes(sb.toString(), true);
        XMLStreamReader sr = getReader(b);
        assertTokenType(START_DOCUMENT, sr.getEventType());
        assertNull(sr.getCharacterEncodingScheme());
        assertEquals("UTF-16BE", sr.getEncoding());
        // let's iterate just for fun though
        assertTokenType(START_ELEMENT, sr.next());
        sr.close();

        // and then little-endian
        b = getUtf16Bytes(sb.toString(), false);
        sr = getReader(b);
        assertTokenType(START_DOCUMENT, sr.getEventType());
        assertNull(sr.getCharacterEncodingScheme());
        assertEquals("UTF-16LE", sr.getEncoding());
        assertTokenType(START_ELEMENT, sr.next());
        sr.close();
    }

    /**
     * Testing for EBCDIC is tricky, mostly due to possible
     * complexity of the things to support, as well as lack
     * of sample documents and difficulty in reading ones
     * that exist (it not being 7-bit ascii compatible).
     * But let's try a straight-forward (naive?) test
     * to verify that what is supposed to work does.
     */
    public void testEBCDIC()
        throws IOException, XMLStreamException
    {
        final String[] subtypes = new String[] {
            "037", "277", "278", "280", "284", "285", "297",
            "420", "424", "500", "870", "871", "918",
        };
 
        for (int i = 0; i < subtypes.length; ++i) {
            String actEnc = ENC_EBCDIC_IN_PREFIX + subtypes[i];
            String xml = "<?xml version='1.0' encoding='"+actEnc+"' ?>"
                +"<root attr='123'>rock &amp; roll!<!-- comment --></root>";
            byte[] bytes = xml.getBytes(actEnc);
            XMLStreamReader sr = getReader(bytes);
            
            assertTokenType(START_DOCUMENT, sr.getEventType());
            
            // Declared encoding should match 100%
            assertEquals(actEnc, sr.getCharacterEncodingScheme());
            
            // Found encoding, though, can be changed
            String expEnc = ENC_EBCDIC_OUT_PREFIX + subtypes[i];
            assertEquals(expEnc, sr.getEncoding());
            
            assertTokenType(START_ELEMENT, sr.next());
            assertEquals("root", sr.getLocalName());
            assertTokenType(CHARACTERS, sr.next());
            assertEquals("rock & roll!", getAndVerifyText(sr));
            assertTokenType(COMMENT, sr.next());
            assertEquals(" comment ", getAndVerifyText(sr));
            assertTokenType(END_ELEMENT, sr.next());
            assertEquals("root", sr.getLocalName());
            sr.close();
        }
    }


    /*
    /////////////////////////////////////////
    // Non-test methods
    /////////////////////////////////////////
     */

    private byte[] getUtf16Bytes(String input, boolean bigEndian)
    {
        int len = input.length();
        byte[] b = new byte[len+len];
        int offset = bigEndian ? 1 : 0; // offset for LSB
        for (int i = 0; i < len; ++i) {
            int c = input.charAt(i);
            // BOM is 2-byte, others 1 byte...
            b[i+i+offset] = (byte) (c & 0xFF);
            b[i+i+(1 - offset)] = (byte) (c >> 8);
        }
        return b;
    }

    private XMLStreamReader getReader(byte[] b)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        return f.createXMLStreamReader(new ByteArrayInputStream(b));
    }
}
