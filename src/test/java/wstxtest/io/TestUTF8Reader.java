package wstxtest.io;

import java.io.*;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.io.UTF8Reader;

/**
 * Unit test created to verify fix to
 * <a href="http://jira.codehaus.org/browse/WSTX-143">WSTX-143</a>
 * and
 * <a href="https://github.com/FasterXML/woodstox/pull/291/">woodstox#291</a>.
 *
 * @author Matt Gormley
 */
public class TestUTF8Reader extends wstxtest.BaseJUnit4Test
{
    @SuppressWarnings("resource")
    @Test
    public void testDelAtBufferBoundary() throws Exception
    {
        final int BYTE_BUFFER_SIZE = 4;
        final int CHAR_BUFFER_SIZE = 1 + BYTE_BUFFER_SIZE;
        final int INPUT_SIZE = 4 * BYTE_BUFFER_SIZE; // could be of arbitrary size
        final byte CHAR_FILLER = 32; // doesn't even matter, just need an ascii char
        final byte CHAR_DEL = 127;

        // Create input that will cause the array index out of bounds exception
        byte[] inputBytes = new byte[INPUT_SIZE];
        Arrays.fill(inputBytes, CHAR_FILLER);
        inputBytes[BYTE_BUFFER_SIZE - 1] = CHAR_DEL;
        InputStream in = new ByteArrayInputStream(inputBytes);

        // Create the UTF8Reader
        ReaderConfig cfg = ReaderConfig.createFullDefaults();
        byte[] byteBuffer = new byte[BYTE_BUFFER_SIZE];
        UTF8Reader reader = new UTF8Reader(cfg,in, byteBuffer, 0, 0, false);

        // Run the reader on the input
        char[] charBuffer = new char[CHAR_BUFFER_SIZE];
        reader.read(charBuffer, 0, charBuffer.length);
    }

    @Test
    public void testOverlongEncodingsRejected() throws Exception
    {
        // Overlong forms decode to a codepoint below the minimum for their
        // byte length; these must be rejected as malformed (RFC 3629)
        assertRejected(new byte[]{(byte)0xC0,(byte)0xBC}); // overlong '<'
        assertRejected(new byte[]{(byte)0xC0,(byte)0x80}); // overlong NUL
        assertRejected(new byte[]{(byte)0xE0,(byte)0x80,(byte)0xAF}); // overlong '/'
        assertRejected(new byte[]{(byte)0xF0,(byte)0x80,(byte)0x81,(byte)0x81}); // overlong 4-byte

        // Shortest (valid) forms for the same boundaries must still decode
        assertEquals("<", decode(new byte[]{(byte)0x3C}));
        assertEquals("é", decode(new byte[]{(byte)0xC3,(byte)0xA9}));
        assertEquals("€", decode(new byte[]{(byte)0xE2,(byte)0x82,(byte)0xAC}));
        assertEquals(new String(Character.toChars(0x1F600)),
                decode(new byte[]{(byte)0xF0,(byte)0x9F,(byte)0x98,(byte)0x80}));
    }

    private static void assertRejected(byte[] input) throws Exception
    {
        try {
            decode(input);
            fail("Expected CharConversionException for overlong UTF-8 sequence");
        } catch (CharConversionException e) {
            // expected
        }
    }

    @SuppressWarnings("resource")
    private static String decode(byte[] input) throws Exception
    {
        ReaderConfig cfg = ReaderConfig.createFullDefaults();
        UTF8Reader reader = new UTF8Reader(cfg, new ByteArrayInputStream(input),
                new byte[16], 0, 0, false);
        char[] cbuf = new char[16];
        int count = reader.read(cbuf, 0, cbuf.length);
        return new String(cbuf, 0, count);
    }
}
