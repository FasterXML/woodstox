package wstxtest.io;

import java.io.*;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.io.UTF32Reader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class TestUTF32Reader extends wstxtest.BaseJUnit4Test
{
    @SuppressWarnings("resource")
    private UTF32Reader reader(byte[] input, boolean bigEndian) {
        ReaderConfig cfg = ReaderConfig.createFullDefaults();
        return new UTF32Reader(cfg, new ByteArrayInputStream(input),
                new byte[16], 0, 0, false, bigEndian);
    }

    // Code points above U+10FFFF must be rejected, not truncated to 16 bits.
    // The high byte's top bit sign-extends the int negative, which previously
    // slipped past the range checks and decoded e.g. 80 00 00 3C to '<'.
    @Test
    public void testOutOfRangeBigEndian() throws IOException {
        try {
            int n = reader(new byte[]{(byte)0x80, 0x00, 0x00, 0x3C}, true)
                    .read(new char[8], 0, 8);
            fail("Expected CharConversionException, got "+n+" char(s)");
        } catch (CharConversionException expected) { }
    }

    @Test
    public void testOutOfRangeLittleEndian() throws IOException {
        try {
            int n = reader(new byte[]{0x3C, 0x00, 0x00, (byte)0x80}, false)
                    .read(new char[8], 0, 8);
            fail("Expected CharConversionException, got "+n+" char(s)");
        } catch (CharConversionException expected) { }
    }

    // Legal astral character (U+10000) still decodes to a surrogate pair.
    @Test
    public void testValidAstral() throws IOException {
        char[] cbuf = new char[8];
        int n = reader(new byte[]{0x00, 0x01, 0x00, 0x00}, true).read(cbuf, 0, 8);
        assertEquals(2, n);
        assertEquals('\uD800', cbuf[0]);
        assertEquals('\uDC00', cbuf[1]);
    }
}
