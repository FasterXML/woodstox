package stax2.typed;

import java.util.Random;
import javax.xml.stream.*;

import org.codehaus.stax2.*;
import org.codehaus.stax2.ri.typed.AsciiValueEncoder;
import org.codehaus.stax2.ri.typed.ValueEncoderFactory;
import org.codehaus.stax2.typed.*;

import stax2.BaseStax2Test;

/**
 * Base class that contains set of simple unit tests to verify implementation
 * of parts {@link TypedXMLStreamReader} that deal with base64 encoded
 * binary data.
 * Concrete sub-classes are used to test both native and wrapped Stax2
 * implementations.
 *
 * @author Tatu Saloranta
 */
public abstract class ReaderBinaryTestBase
    extends BaseStax2Test
{
    /**
     * For good testing let's try all alternative variants, in addition
     * to the default one (MIME)
     */
    final static Base64Variant[] sBase64Variants = new Base64Variant[] {
        Base64Variants.MIME,
        Base64Variants.PEM,
        Base64Variants.MODIFIED_FOR_URL
    };

    final static Base64Variant[] sPaddingVariants = new Base64Variant[] {
        Base64Variants.MIME,
        Base64Variants.PEM
    };

    final static Base64Variant[] sNonPaddingVariants = new Base64Variant[] {
        Base64Variants.MODIFIED_FOR_URL
    };

    // Let's test variable length arrays
    final static int[] LEN_ELEM = new int[] {
        1, 2, 3, 4, 7, 39, 116, 400, 900, 2890, 5003, 17045, 125000, 499999
    };
    final static int[] LEN_ATTR = new int[] {
        1, 2, 3, 5, 17, 59, 357, 1920, 9000, 63000, 257010
    };

    final static int[] LEN_ELEM_MULTIPLE = new int[] {
        4, 7, 16, 99, 458, 3000, 12888, 79003, 145000
    };

    final static int METHOD_SINGLE = 1;
    final static int METHOD_FULL = 2;
    final static int METHOD_2BYTES = 3;
    final static int METHOD_SEGMENTED = 4;
    final static int METHOD_FULL_CONVENIENT = 5;

    /**
     * Padding characters are only legal as last one or two characters
     * of 4-char units.
     */
    final static String[] INVALID_PADDING = new String[] {
        "AAAA====", "AAAAB===", "AA=A"
    };

    /**
     * White space is only allowed between 4-char units, not within.
     */
    final static String[] INVALID_WS = new String[] {
        "AAA A", "AAAA BBBB C CCC", "ABCD ABCD AB CD"
    };

    /**
     * And there are unlimited number of illegal characters within
     * base64 sections, too
     */
    final String[] INVALID_WEIRD_CHARS = new String[] {
        "AAA?", "AAAA@@@@", "ABCD\u00A0BCD"
    };

    /*
    ////////////////////////////////////////
    // Abstract methods
    ////////////////////////////////////////
     */

    protected abstract XMLStreamReader2 getReader(String contents)
        throws XMLStreamException;

    protected XMLStreamReader2 getElemReader(String contents)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getReader(contents);
        assertTokenType(START_ELEMENT, sr.next());
        return sr;
    }

    /*
    ////////////////////////////////////////
    // Test methods, elem, valid
    ////////////////////////////////////////
     */

    public void testBinaryElemByteByByte() throws XMLStreamException
    {
        _testBinaryElem(METHOD_SINGLE, false);
        _testBinaryElem(METHOD_SINGLE, true);
    }

    public void testBinaryElemFull() throws XMLStreamException
    {
        _testBinaryElem(METHOD_FULL, false);
        _testBinaryElem(METHOD_FULL, true);
    }

    public void testBinaryElem2Bytes() throws XMLStreamException
    {
        _testBinaryElem(METHOD_2BYTES, false);
        _testBinaryElem(METHOD_2BYTES, true);
    }

    public void testBinaryElemSegmented() throws XMLStreamException
    {
        _testBinaryElem(METHOD_SEGMENTED, false);
        _testBinaryElem(METHOD_SEGMENTED, true);
    }

    public void testBinaryElemFullConvenient() throws XMLStreamException
    {
        _testBinaryElem(METHOD_FULL_CONVENIENT, false);
        _testBinaryElem(METHOD_FULL_CONVENIENT, true);
    }

    /**
     * Unit test that verifies that decoding state is properly
     * reset even if not all data is read.
     * Access is done using supported method (i.e. starting with
     * 
     */
    public void testMultipleBinaryElems() throws XMLStreamException
    {
        /* Let's try couple of sizes here too, but only check partial
         * content; this to ensure content is properly cleared between
         * calls
         */
        final int REPS = 3;

        for (int bv = 0; bv < sBase64Variants.length; ++bv) {
            Base64Variant b64variant = sBase64Variants[bv];
            for (int x = 0; x < LEN_ELEM_MULTIPLE.length; ++x) {
                int size = LEN_ELEM_MULTIPLE[x];
                Random r = new Random(size+1);
                byte[][] dataTable = generateDataTable(r, size, REPS);
                String doc = buildMultiElemDoc(b64variant, dataTable);
                // First, get access to root elem
                XMLStreamReader2 sr = getElemReader(doc);
                
                // single-byte check should uncover problems
                for (int i = 0; i < REPS; ++i) {
                    assertTokenType(START_ELEMENT, sr.next());
                    _verifyElemData1(sr, b64variant, dataTable[i]);
                    // Should not have hit END_ELEMENT yet
                    if (sr.getEventType() == END_ELEMENT) {
                        fail("Should not have yet advanced to END_ELEMENT, when decoding not finished");
                    }
                    // but needs to if we advance; can see CHARACTERS in between tho
                    while (CHARACTERS == sr.next()) { }
                    assertTokenType(END_ELEMENT, sr.getEventType());
                }
                sr.close();
            }
        }
    }

    /**
     * Test that uses 'mixed' segments (CHARACTERS and CDATA), in
     * which base64 units (4 chars producing 3 bytes) can be split
     * between segments.
     *<p>
     * It is not clear if and how non-padding variants could
     * be mixed, so this test only covers padding variants
     * (it is likely that mixing would make sense whatsoever; but
     * at least additional spacing would have to be provided)
     */
    public void testBinaryMixedSegments() throws XMLStreamException
    {
        // We'll do just one long test
        Random r = new Random(123);
        final int SIZE = 128000;
        byte[] data = generateData(r, SIZE);
        char[] buffer = new char[100];

        /* 20-Nov-2008, tatus: Let's test all available base64
         *   variants too:
         */
        for (int bv = 0; bv < sPaddingVariants.length; ++bv) {
            Base64Variant b64variant = sPaddingVariants[bv];
            StringBuffer b64 = new StringBuffer(data.length * 2);
            
            /* Ok, first, let's first just generate long String of base64
             * data:
             */
            int ptr = 0;
            do {
                int chunkLen = 1 + (r.nextInt() & 0x7);
                AsciiValueEncoder enc = new ValueEncoderFactory().getEncoder(b64variant, data, ptr, chunkLen);
                ptr += chunkLen;
                int len = enc.encodeMore(buffer, 0, buffer.length);
                b64.append(buffer, 0, len);
            } while (b64.length() < SIZE);
            // And then create document, with split content
            
            final int byteLen = ptr;
            String refDoc = "<root>"+b64.toString()+"</root>";
            
            // But first: let's verify content is encoded correctly:
            {
                XMLStreamReader2 sr = getElemReader(refDoc);
                _verifyElemData(sr, b64variant, r, data, byteLen, METHOD_FULL);
                sr.close();
            }
            
            StringBuffer sb = new StringBuffer(b64.length() * 2);
            sb.append("<root>");
            
            ptr = 0;
            boolean cdata = false;
            
            while (ptr < b64.length()) {
                int segLen = 1 + (r.nextInt() & 0x7);
                if (cdata) {
                    sb.append("<![CDATA[");
                }
                segLen = Math.min(segLen, (b64.length() - ptr));
                for (int i = 0; i < segLen; ++i) {
                    sb.append(b64.charAt(ptr++));
                }
                if (cdata) {
                    sb.append("]]>");
                }
                cdata = !cdata;
            }
            sb.append("</root>");
            String actualDoc = sb.toString();
            
            XMLStreamReader2 sr = getElemReader(actualDoc);
            // should be enough to verify byte-by-byte?
            _verifyElemData(sr, b64variant, r, data, byteLen, METHOD_SINGLE);
            sr.close();
        }
    }
        
    private void _testBinaryElem(int readMethod, boolean addNoise)
        throws XMLStreamException
    {
        for (int bv = 0; bv < sBase64Variants.length; ++bv) {
            Base64Variant b64variant = sBase64Variants[bv];
            for (int x = 0; x < LEN_ELEM.length; ++x) {
                int size = LEN_ELEM[x];
                Random r = new Random(size);
                byte[] data = generateData(r, size);
                String doc = buildDoc(b64variant, r, data, addNoise);
                XMLStreamReader2 sr = getElemReader(doc);
                _verifyElemData(sr, b64variant, r, data, data.length, readMethod);
                sr.close();
            }
        }
    }
    
    private void _verifyElemData(XMLStreamReader2 sr, Base64Variant b64variant, Random r, byte[] data, int dataLen, int readMethod)
        throws XMLStreamException
    {
        switch (readMethod) {
        case METHOD_SINGLE: // minimal reads, single byte at a time
            {
                byte[] buffer = new byte[5];
                int ptr = 0;
                int count;
                
                while ((count = sr.readElementAsBinary(buffer, 2, 1, b64variant)) > 0) {
                    assertEquals(1, count);
                    if ((ptr+1) < dataLen) {
                        if (data[ptr] != buffer[2]) {
                            fail("(base64 variant "+b64variant+") Corrupt decode at #"+ptr+"/"+dataLen+", expected "+displayByte(data[ptr])+", got "+displayByte(buffer[2]));
                        }
                    }
                    ++ptr;
                }
                if (ptr != dataLen) {
                    fail("(base64 variant "+b64variant+") Expected to get "+dataLen+" bytes, got "+ptr);
                }
            }
            break;
        case METHOD_FULL: // full read
            {
                byte[] buffer = new byte[dataLen + 100];
                /* Let's assume reader will actually read it all:
                 * while not absolutely required, in practice it should
                 * happen. If this is not true, need to change unit
                 * test to reflect it.
                 */
                int count = sr.readElementAsBinary(buffer, 3, buffer.length-3, b64variant);
                assertEquals(dataLen, count);
                for (int i = 0; i < dataLen; ++i) {
                    if (buffer[3+i] != data[i]) {
                        fail("(base64 variant "+b64variant+") Corrupt decode at #"+i+", expected "+displayByte(data[i])+", got "+displayByte(buffer[3+i]));
                    }
                }
            }
            break;
            
        case METHOD_FULL_CONVENIENT: // full read
            {
                byte[] result = sr.getElementAsBinary(b64variant);
                assertEquals(dataLen, result.length);
                for (int i = 0; i < dataLen; ++i) {
                    if (result[i] != data[i]) {
                        fail("(base64 variant "+b64variant+") Corrupt decode at #"+i+", expected "+displayByte(data[i])+", got "+displayByte(result[i]));
                    }
                }
            }
            break;

        case METHOD_2BYTES: // 2 bytes at a time
        default: // misc sizes
            {
                boolean random = (readMethod != METHOD_2BYTES);
                
                byte[] buffer = new byte[200];
                int ptr = 0;
                
                while (true) {
                    int len = random ? (20 + (r.nextInt() & 127)) : 2;
                    int count = sr.readElementAsBinary(buffer, 0, len, b64variant);
                    if (count < 0) {
                        break;
                    }
                    if ((ptr + count) > dataLen) {
                        ptr += count;
                        break;
                    }
                    for (int i = 0; i < count; ++i) {
                        if (data[ptr+i] != buffer[i]) {
                            fail("(base64 variant "+b64variant+") Corrupt decode at #"+(ptr+i)+"/"+dataLen+" (read len: "+len+"; got "+count+"), expected "+displayByte(data[ptr+i])+", got "+displayByte(buffer[i]));
                        }
                    }
                    ptr += count;
                }
                
                if (ptr != dataLen) {
                    fail("(base64 variant "+b64variant+") Expected "+dataLen+" bytes, got "+ptr);
                }
            }
        }
        assertTokenType(END_ELEMENT, sr.getEventType());
    }

    private void _verifyElemData1(XMLStreamReader2 sr, Base64Variant b64variant, byte[] data)
        throws XMLStreamException
    {
        byte[] buffer = new byte[5];
        assertEquals(1, sr.readElementAsBinary(buffer, 1, 1, b64variant));
        assertEquals(data[0], buffer[1]);
    }
        
    /*
    ////////////////////////////////////////
    // Test methods, elem, invalid
    ////////////////////////////////////////
     */

    /**
     * Rules for padding are quite simple: you can use one or two padding
     * characters, which indicate 1 or 2 bytes instead full 3 for the
     * decode unit.
     */
    public void testInvalidElemPadding()
        throws XMLStreamException
    {
        // Let's try out couple of arbitrary broken ones...
        final byte[] resultBuffer = new byte[20];

        // Hmmh. Here we need to skip testing of non-padded variants...
        // (ideally would also test non-padding ones, but using different method)
        for (int bv = 0; bv < sPaddingVariants.length; ++bv) {
            Base64Variant b64variant = sPaddingVariants[bv];
            for (int i = 0; i < INVALID_PADDING.length; ++i) {
                String doc = "<root>"+INVALID_PADDING[i]+"</root>";
                XMLStreamReader2 sr = getElemReader(doc);
                try {
                    /*int count = */ sr.readElementAsBinary(resultBuffer, 0, resultBuffer.length, b64variant);
                    fail("Should have received an exception for invalid padding");
                } catch (TypedXMLStreamException ex) {
                    // any way to check that it's the excepted message? not right now
                }
                sr.close();
            }
        }
    }

    /**
     * Whitespace is allowed within base64, but only to separate 4 characters
     * base64 units. Ideally (and by the spec) they should be used every
     * 76 characters (== every 19 units), but it'd be hard to enforce this
     * as well as fail on much of existing supposedly base64 compliant
     * systems. So, we will just verify that white space can not be used
     * within 4 char units.
     */
    public void testInvalidWhitespace()
        throws XMLStreamException
    {
        // Let's try out couple of arbitrary broken ones...
        final byte[] resultBuffer = new byte[20];

        for (int bv = 0; bv < sBase64Variants.length; ++bv) {
            Base64Variant b64variant = sBase64Variants[bv];
            for (int i = 0; i < INVALID_WS.length; ++i) {
                String doc = "<root>"+INVALID_WS[i]+"</root>";
                XMLStreamReader2 sr = getElemReader(doc);
                try {
                    /*int count = */ sr.readElementAsBinary(resultBuffer, 0, resultBuffer.length, b64variant);
                    fail("Should have received an exception for white space used 'inside' 4-char base64 unit");
                } catch (TypedXMLStreamException ex) {
                    // any way to check that it's the excepted message? not right now
                }
                sr.close();
            }
        }
    }

    public void testInvalidWeirdChars()
        throws XMLStreamException
    {
        final byte[] resultBuffer = new byte[20];

        for (int bv = 0; bv < sBase64Variants.length; ++bv) {
            Base64Variant b64variant = sBase64Variants[bv];
            for (int i = 0; i < INVALID_WEIRD_CHARS.length; ++i) {
                String doc = "<root>"+INVALID_WEIRD_CHARS[i]+"</root>";
                XMLStreamReader2 sr = getElemReader(doc);
                try {
                    /*int count = */ sr.readElementAsBinary(resultBuffer, 0, resultBuffer.length, b64variant);
                    fail("Should have received an exception for invalid base64 character");
                } catch (TypedXMLStreamException ex) {
                    // any way to check that it's the excepted message? not right now
                }
                sr.close();
            }
        }
    }

    public void testIncompleteInvalidElem()
        throws XMLStreamException
    {
        // Let's just try with short partial segments, data used doesn't matter
        final byte[] data = new byte[6];
        final byte[] resultBuffer = new byte[20];
        // plus also skip non-padded variants, for now

        // So first we'll encode 1 to 6 bytes as base64
        for (int bv = 0; bv < sPaddingVariants.length; ++bv) {
            Base64Variant b64variant = sPaddingVariants[bv];
            for (int i = 1; i <= data.length; ++i) {
                AsciiValueEncoder enc = new ValueEncoderFactory().getEncoder(b64variant, data, 0, i);
                char[] cbuf = new char[20];
                int clen = enc.encodeMore(cbuf, 0, cbuf.length);
                
                // and use all byte last 1, 2 or 3 chars
                for (int j = 1; j <= 3; ++j) {
                    int testLen = clen-j;
                    StringBuffer sb = new StringBuffer();
                    sb.append("<root>");
                    sb.append(cbuf, 0, testLen);
                    sb.append("</root>");
                    
                    XMLStreamReader2 sr = getElemReader(sb.toString());
                    try {
                        /*int count = */ sr.readElementAsBinary(resultBuffer, 0, resultBuffer.length, b64variant);
                        fail("Should have received an exception for incomplete base64 unit");
                    } catch (TypedXMLStreamException ex) {
                        // any way to check that it's the excepted message? not right now
                    }
                    sr.close();
                }
            }
        }
    }

    /*
    ////////////////////////////////////////
    // Test methods, attr, valid
    ////////////////////////////////////////
     */

    /**
     * API to access attribute values is much simpler; hence fewer
     * things need testing
     */
    public void testBinaryAttrValid() throws XMLStreamException
    {
        final int REPS = 3;
        for (int j = 0; j < REPS; ++j) {
            for (int bv = 0; bv < sBase64Variants.length; ++bv) {
                Base64Variant b64variant = sBase64Variants[bv];
                for (int i = 0; i < LEN_ATTR.length; ++i) {
                    int size = LEN_ATTR[i];
                    byte[] data = generateData(new Random(size), size);
                    char[] buffer = new char[4 + (data.length * 3 / 2)];
                    AsciiValueEncoder enc = new ValueEncoderFactory().getEncoder(b64variant, data, 0, data.length);
                    int len = enc.encodeMore(buffer, 0, buffer.length);
                    StringBuilder sb = new StringBuilder(buffer.length + 32);
                    sb.append("<root attr='");
                    sb.append(buffer, 0, len);
                    sb.append("' />");
                    XMLStreamReader2 sr = getElemReader(sb.toString());
                    byte[] actData = null;
                    try {
                        actData = sr.getAttributeAsBinary(0, b64variant);
                    } catch (TypedXMLStreamException e) {
                        fail("Failed for variant "+b64variant+", input '"+e.getLexical()+"': "+e.getMessage());
                    }
 
                    assertNotNull(actData);
                    assertEquals(data.length, actData.length);
                    for (int x = 0; x < data.length; ++x) {
                        if (data[x] != actData[x]) {
                            fail("Corrupt decode at #"+x+"/"+data.length+", expected "+displayByte(data[x])+", got "+displayByte(actData[x]));
                        }
                    }
                }
            }
        }
    }

    /*
    ////////////////////////////////////////
    // Test methods, attr, invalid
    ////////////////////////////////////////
     */

    public void testInvalidAttrPadding()
        throws XMLStreamException
    {
        // Hmmh. Here we need to skip testing of non-padded variants...
        for (int bv = 0; bv < sPaddingVariants.length; ++bv) {
            Base64Variant b64variant = sPaddingVariants[bv];
            
            for (int i = 0; i < INVALID_PADDING.length; ++i) {
                String doc = "<root attr='"+INVALID_PADDING[i]+"' />";
                XMLStreamReader2 sr = getElemReader(doc);
                try {
                    /*byte[] data = */ sr.getAttributeAsBinary(0, b64variant);
                    fail("Should have received an exception for invalid padding");
                } catch (TypedXMLStreamException ex) {
                    // any way to check that it's the excepted message? not right now
                }
                sr.close();
            }
        }
    }

    public void testInvalidAttrWhitespace()
        throws XMLStreamException
    {
        for (int bv = 0; bv < sBase64Variants.length; ++bv) {
            Base64Variant b64variant = sBase64Variants[bv];
            for (int i = 0; i < INVALID_WS.length; ++i) {
                String doc = "<root x='"+INVALID_WS[i]+"' />";
                XMLStreamReader2 sr = getElemReader(doc);
                try {
                    /*byte[] data = */ sr.getAttributeAsBinary(0, b64variant);
                    fail("Should have received an exception for white space used 'inside' 4-char base64 unit");
                } catch (TypedXMLStreamException ex) {
                    // any way to check that it's the excepted message? not right now
                }
                sr.close();
            }
        }
    }

    public void testInvalidAttrWeirdChars()
        throws XMLStreamException
    {
        for (int bv = 0; bv < sBase64Variants.length; ++bv) {
            Base64Variant b64variant = sBase64Variants[bv];
            for (int i = 0; i < INVALID_WEIRD_CHARS.length; ++i) {
                String doc = "<root abc='"+INVALID_WEIRD_CHARS[i]+"'/>";
                XMLStreamReader2 sr = getElemReader(doc);
                try {
                    /*byte[] data = */ sr.getAttributeAsBinary(0, b64variant);
                    fail("Should have received an exception for invalid base64 character");
                } catch (TypedXMLStreamException ex) {
                    // any way to check that it's the excepted message? not right now
                }
                sr.close();
            }
        }
    }

    public void testInvalidAttrIncomplete()
        throws XMLStreamException
    {
        // Let's just try with short partial segments, data used doesn't matter
        final byte[] data = new byte[6];
        // plus also skip non-padded variants, for now

        for (int bv = 0; bv < sPaddingVariants.length; ++bv) {
            Base64Variant b64variant = sPaddingVariants[bv];

            // So first we'll encode 1 to 6 bytes as base64
            for (int i = 1; i <= data.length; ++i) {
                AsciiValueEncoder enc = new ValueEncoderFactory().getEncoder(b64variant, data, 0, i);
                char[] cbuf = new char[20];
                int clen = enc.encodeMore(cbuf, 0, cbuf.length);
                
                // and use all byte last 1, 2 or 3 chars
                for (int j = 1; j <= 3; ++j) {
                    int testLen = clen-j;
                    StringBuffer sb = new StringBuffer();
                    sb.append("<root attr='").append(cbuf, 0, testLen).append("'/>");
                    XMLStreamReader2 sr = getElemReader(sb.toString());
                    try {
                        /*byte[] data = */ sr.getAttributeAsBinary(0, b64variant);
                        fail("Should have received an exception for incomplete base64 unit");
                    } catch (TypedXMLStreamException ex) {
                        // any way to check that it's the excepted message? not right now
                    }
                    sr.close();
                }
            }
        }
    }

    /*
    ////////////////////////////////////////
    // Helper methods
    ////////////////////////////////////////
     */

    private byte[] generateData(Random r, int size)
    {
        byte[] result = new byte[size];
        r.nextBytes(result);
        return result;
    }

    private byte[][] generateDataTable(Random r, int size, int reps)
    {
        byte[][] table = new byte[reps][];
        for (int i = 0; i < reps; ++i) {
            table[i] = generateData(r, size);
        }
        return table;
    }

    private String buildDoc(Base64Variant b64variant, Random r, byte[] data, boolean addNoise)
    {
        // Let's use base64 codec from RI here:
        AsciiValueEncoder enc = new ValueEncoderFactory().getEncoder(b64variant, data, 0, data.length);

        StringBuffer sb = new StringBuffer(data.length * 2);
        sb.append("<root>");

        // Without noise it's quite easy, just need enough space:
        if (!addNoise) {
            // Base64 adds 33% overhead, but let's be generous
            char[] buffer = new char[4 + (data.length * 3 / 2)];
            int len = enc.encodeMore(buffer, 0, buffer.length);
            sb.append(buffer, 0, len);
        } else {
            // but with noise, need bit different approach
            char[] buffer = new char[300];

            while (!enc.isCompleted()) {
                int offset = r.nextInt() & 0xF;
                int len;
                int rn = r.nextInt() & 15;

                switch (rn) {
                case 1:
                case 2:
                case 3:
                case 4:
                    len = rn;
                    break;
                case 5:
                case 6:
                case 7:
                    len = 3 + (r.nextInt() & 15);
                    break;
                default:
                    len = 20 + (r.nextInt() & 127);
                    break;
                }
                int end = enc.encodeMore(buffer, offset, offset+len);

                // regular or CDATA?
                boolean cdata = r.nextBoolean() && r.nextBoolean();

                if (cdata) {
                    sb.append("<![CDATA[");
                } 
                sb.append(buffer, offset, end-offset);
                if (cdata) {
                    sb.append("]]>");
                } 

                // Let's add noise 25% of time
                if (r.nextBoolean() && r.nextBoolean()) {
                    sb.append("<!-- comment: "+len+" -->");
                } else {
                    sb.append("<?pi "+len+"?>");
                }
            }
        }
        sb.append("</root>");
        return sb.toString();
    }

    private String buildMultiElemDoc(Base64Variant b64variant, byte[][] dataTable)
    {
        StringBuffer sb = new StringBuffer(16 + dataTable.length * dataTable[0].length);
        sb.append("<root>");
        for (int i = 0; i < dataTable.length; ++i) {
            byte[] data = dataTable[i];
            char[] buffer = new char[4 + (data.length * 3 / 2)];
            AsciiValueEncoder enc = new ValueEncoderFactory().getEncoder(b64variant, data, 0, data.length);
            int len = enc.encodeMore(buffer, 0, buffer.length);
            sb.append("<a>");
            sb.append(buffer, 0, len);
            sb.append("</a>");
        }
        sb.append("</root>");
        return sb.toString();
    }

    final static String displayByte(byte b) {
        return "0x"+Integer.toHexString(b & 0xFF);
    }
}
