package org.codehaus.stax.test.stream;

import java.io.*;

import javax.xml.stream.*;

/**
 * Unit test suite that tests handling of text encoding, as specified
 * by XML declaration and/or specific byte-order markers.
 */
public class TestEncodingRead
    extends BaseStreamTest
{
    final String UTF_1 = String.valueOf((char) 0x41); // 'A'
    final String UTF_2 = String.valueOf((char) 0xA0); // nbsp
    final String UTF_3 = String.valueOf((char) 0xB61); // some char that needs 3-byte encoding

    final String UTF_CONTENT = ""
        +UTF_1 + UTF_2 + UTF_3
        +UTF_1 + UTF_1 + UTF_2 + UTF_2 + UTF_3 + UTF_3
        +UTF_3 + UTF_3 + UTF_2 + UTF_2 + UTF_1 + UTF_1
        +UTF_1 + UTF_3 + UTF_2
        +UTF_2 + UTF_1 + UTF_3
        +UTF_2 + UTF_3 + UTF_1
        +UTF_3 + UTF_1 + UTF_2
        +UTF_3 + UTF_2 + UTF_1
        ;

    final static byte[] BE_BOM = new byte[] { (byte) 0xFE, (byte) 0xFF };
    final static byte[] LE_BOM = new byte[] { (byte) 0xFF, (byte) 0xFE };
    final static byte[] UTF8_BOM = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

    /**
     * Test to check that UTF-8 stream with no leading BOM is succesfully
     * handled by parser.
     */
    public void testUTF8()
        throws Exception
    {
        doTestEncoding("UTF-8", "UTF-8", null);
        doTestEncoding("UTF-8", null, null);
    }

    /**
     * Test to check that UTF-8 stream with leading BOM is succesfully
     * handled by parser.
     */
    public void testUTF8WithBOM()
        throws Exception
    {
        doTestEncoding("UTF-8", "UTF-8", UTF8_BOM);
        doTestEncoding("UTF-8", null, UTF8_BOM);
    }

    public void testUTF8Surrogates()
        throws XMLStreamException, IOException
    {
        String XML = "<?xml version='1.0' encoding='UTF-8'?><root>XXXX</root>";
        int ix = XML.indexOf('X');
        byte[] src = XML.getBytes("UTF-8");

        // A somewhat random high-order Unicode char:
        src[ix] = (byte)0xF1;
        src[ix+1] = (byte)0x90;
        src[ix+2] = (byte)0x88;
        src[ix+3] = (byte)0x88;

        InputStream in = new ByteArrayInputStream(src);
        XMLInputFactory f = getInputFactory();
        XMLStreamReader sr = f.createXMLStreamReader(in);

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertTokenType(CHARACTERS, sr.next());
        String str = getAndVerifyText(sr);
        // Should result in a surrogate pair...
        assertEquals(2, str.length());
        assertEquals((char) 0xd900, str.charAt(0));
        assertEquals((char) 0xde08, str.charAt(1));
        assertTokenType(END_ELEMENT, sr.next());
    }

    public void testUTF16BEWithBOM()
        throws XMLStreamException,
               UnsupportedEncodingException
    {
        doTestEncoding("UTF-16BE", "UTF-16", BE_BOM);
        doTestEncoding("UTF-16BE", null, BE_BOM);

        doTestEncoding2(true);
    }

    public void testUTF16LEWithBOM()
        throws XMLStreamException,
               UnsupportedEncodingException
    {
        doTestEncoding("UTF-16LE", "UTF-16", LE_BOM);
        doTestEncoding("UTF-16LE", null, LE_BOM);

        doTestEncoding2(false);
    }

    /*
    ////////////////////////////////////////
    // Private methods, shared test code
    ////////////////////////////////////////
     */

    /**
     * @param javaEnc Name of encoding as understood by JDK; used to
     *    instantiate JDK encoder/decoder to use for test
     * @param xmlEnc Name of encoding as included in xml declaration;
     *   null to indicate nothing should be added
     * @param bom Pre-defined bom bytes to prepend to input, if any.
     */
    public void doTestEncoding(String javaEnc, String xmlEnc,
                               byte[] bom)
        throws XMLStreamException,
               UnsupportedEncodingException
    {
        String XML = "<?xml version='1.0'";
        
        if (xmlEnc != null) {
            XML +=  " encoding='"+xmlEnc+"'";
        }
        XML += "?><root>"+UTF_CONTENT+"</root>";
        
        byte[] b = XML.getBytes(javaEnc);
        if (bom != null) {
            byte[] orig = b;
            b = new byte[b.length + bom.length];
            System.arraycopy(bom, 0, b, 0, bom.length);
            System.arraycopy(orig, 0, b, bom.length, orig.length);
        }
        
        XMLStreamReader sr = getReader(b);
        
        if (xmlEnc != null) {
            assertEquals(xmlEnc, sr.getCharacterEncodingScheme());
        } else {
            /* otherwise... should we get some info? Preferably yes;
             * (getEncoding() should return auto-detected encoding)
             * but this is not strictly mandated by the specs?
             */
        }
        
        assertEquals(START_ELEMENT, sr.next());
        assertEquals(CHARACTERS, sr.next());
        
        assertEquals(UTF_CONTENT, getAllText(sr));

        assertEquals(END_ELEMENT, sr.getEventType());
        assertEquals(END_DOCUMENT, sr.next());
    }

    private void doTestEncoding2(boolean bigEndian)
        throws XMLStreamException
    {

        /* 20-Jan-2006, TSa: Ok, let's try another variation that may
         *    causes problem; UTF-16 is vague, and if using JDK provided
         *    readers, parser has to indicate endianness.
         */
        final String XML = "<?xml version='1.0' encoding='UTF-16'?>\n"
            +"<!--comment--><root>text</root>";
        int len = XML.length();
        byte[] b = new byte[2 + len + len];

        if (bigEndian) {
            b[0] = (byte) 0xFE;
            b[1] = (byte) 0xFF;
        } else {
            b[0] = (byte) 0xFF;
            b[1] = (byte) 0xFE;
        }

        int offset = bigEndian ? 3 : 2;
        for (int i = 0; i < len; ++i) {
            b[offset + i + i] = (byte) XML.charAt(i);
        }
        XMLStreamReader sr = getReader(b);
        // may get white space...
        int type = sr.next();
        if (type == SPACE) {
            type = sr.next();
        }
        assertTokenType(COMMENT, type);
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());
        assertEquals("text", getAndVerifyText(sr));
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();
    }

    /*
    ////////////////////////////////////////
    // Private methods, other
    ////////////////////////////////////////
     */

    private XMLStreamReader getReader(byte[] contents)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setValidating(f, false);
        return constructStreamReader(f, contents);
    }
}
