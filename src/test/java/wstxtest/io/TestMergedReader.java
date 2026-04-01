package wstxtest.io;

import java.io.*;

import junit.framework.TestCase;

import com.ctc.wstx.io.MergedReader;

/**
 * Unit tests for {@link MergedReader}, specifically verifying
 * that skip() returns the correct number of characters skipped.
 */
public class TestMergedReader extends TestCase
{
    // Regression test: skip() returned 'amount' (total available) instead
    // of 'n' (requested) when skip fits entirely in the buffered segment
    public void testSkipReturnValue() throws IOException
    {
        char[] buf = "ABCDEFGHIJ".toCharArray();
        StringReader underlying = new StringReader("xyz");
        MergedReader reader = new MergedReader(null, underlying, buf, 0, buf.length);

        // Skip 3, buffer has 10 available — should return 3, not 10
        long skipped = reader.skip(3);
        assertEquals("skip() should return the number actually skipped", 3L, skipped);

        // After skipping 3, reading should yield 'D' (the 4th char)
        assertEquals('D', (char) reader.read());

        reader.close();
    }

    public void testSkipExactlyAvailable() throws IOException
    {
        char[] buf = "ABCDE".toCharArray();
        StringReader underlying = new StringReader("xyz");
        MergedReader reader = new MergedReader(null, underlying, buf, 0, buf.length);

        // Skip exactly the buffered amount
        long skipped = reader.skip(5);
        assertEquals(5L, skipped);

        // Should now read from underlying reader
        assertEquals('x', (char) reader.read());

        reader.close();
    }

    public void testSkipBeyondBuffer() throws IOException
    {
        char[] buf = "AB".toCharArray();
        StringReader underlying = new StringReader("xyz");
        MergedReader reader = new MergedReader(null, underlying, buf, 0, buf.length);

        // Skip 4: 2 from buffer + 2 from underlying
        long skipped = reader.skip(4);
        assertEquals(4L, skipped);

        // Should now read 'z' from underlying
        assertEquals('z', (char) reader.read());

        reader.close();
    }
}
