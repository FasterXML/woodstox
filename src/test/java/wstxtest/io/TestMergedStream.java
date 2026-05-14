package wstxtest.io;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.ctc.wstx.io.MergedStream;

/**
 * Unit tests for {@link MergedStream}, the InputStream wrapper that serves
 * data from a "rewound" buffer first, then falls back to the underlying
 * stream once the buffer is exhausted.
 */
public class TestMergedStream extends wstxtest.BaseWstxTest
{
    // ---------- construction ----------

    public void testNullUnderlyingRejected()
    {
        try {
            new MergedStream(null, null, new byte[]{1, 2}, 0, 2);
            fail("Expected IllegalArgumentException for null underlying stream");
        } catch (IllegalArgumentException e) {
            verifyException(e, "InputStream");
        }
    }

    // ---------- single-byte read() ----------

    public void testReadDrainsBufferThenUnderlying() throws IOException
    {
        byte[] buf = "AB".getBytes("ISO-8859-1");
        InputStream underlying = new ByteArrayInputStream("CD".getBytes("ISO-8859-1"));
        try (MergedStream ms = new MergedStream(null, underlying, buf, 0, buf.length)) {
            assertEquals('A', ms.read());
            assertEquals('B', ms.read());
            // Buffer exhausted; should now read from underlying
            assertEquals('C', ms.read());
            assertEquals('D', ms.read());
            assertEquals(-1, ms.read());
        }
    }

    public void testReadHonorsStartOffset() throws IOException
    {
        byte[] buf = "XXAB".getBytes("ISO-8859-1");
        InputStream underlying = new ByteArrayInputStream(new byte[0]);
        // Start at offset 2 — first two bytes ignored
        try (MergedStream ms = new MergedStream(null, underlying, buf, 2, buf.length)) {
            assertEquals('A', ms.read());
            assertEquals('B', ms.read());
            assertEquals(-1, ms.read());
        }
    }

    // ---------- bulk read() ----------

    public void testReadIntoArrayFromBuffer() throws IOException
    {
        byte[] buf = "ABCDE".getBytes("ISO-8859-1");
        InputStream underlying = new ByteArrayInputStream(new byte[0]);
        try (MergedStream ms = new MergedStream(null, underlying, buf, 0, buf.length)) {
            byte[] out = new byte[10];
            int n = ms.read(out);
            // Bulk read is capped to remaining buffered bytes
            assertEquals(5, n);
            assertEquals("ABCDE", new String(out, 0, n, "ISO-8859-1"));
            // Subsequent read falls back to (empty) underlying
            assertEquals(-1, ms.read(out));
        }
    }

    public void testReadIntoArrayPartialThenUnderlying() throws IOException
    {
        byte[] buf = "ABCDE".getBytes("ISO-8859-1");
        InputStream underlying = new ByteArrayInputStream("FGH".getBytes("ISO-8859-1"));
        try (MergedStream ms = new MergedStream(null, underlying, buf, 0, buf.length)) {
            byte[] out = new byte[3];
            // First read pulls 3 bytes from the buffered segment
            assertEquals(3, ms.read(out, 0, 3));
            assertEquals("ABC", new String(out, 0, 3, "ISO-8859-1"));
            // Two left in buffer
            assertEquals(2, ms.read(out, 0, 3));
            assertEquals("DE", new String(out, 0, 2, "ISO-8859-1"));
            // Now from underlying
            int n = ms.read(out, 0, 3);
            assertEquals(3, n);
            assertEquals("FGH", new String(out, 0, n, "ISO-8859-1"));
        }
    }

    // ---------- available() ----------

    public void testAvailableReportsBufferedThenUnderlying() throws IOException
    {
        byte[] buf = "ABC".getBytes("ISO-8859-1");
        InputStream underlying = new ByteArrayInputStream("DE".getBytes("ISO-8859-1"));
        try (MergedStream ms = new MergedStream(null, underlying, buf, 0, buf.length)) {
            assertEquals(3, ms.available());
            ms.read(); // drain one
            assertEquals(2, ms.available());
            // Drain the rest of the buffer
            ms.read();
            ms.read();
            // Now reflects underlying stream's available()
            assertEquals(2, ms.available());
        }
    }

    // ---------- skip() ----------

    public void testSkipWithinBuffer() throws IOException
    {
        byte[] buf = "ABCDEFGH".getBytes("ISO-8859-1");
        InputStream underlying = new ByteArrayInputStream("xyz".getBytes("ISO-8859-1"));
        try (MergedStream ms = new MergedStream(null, underlying, buf, 0, buf.length)) {
            // Skip 3 within the buffered segment — return value == requested
            assertEquals(3L, ms.skip(3));
            assertEquals('D', ms.read());
        }
    }

    public void testSkipCrossesBufferIntoUnderlying() throws IOException
    {
        byte[] buf = "ABCD".getBytes("ISO-8859-1");
        InputStream underlying = new ByteArrayInputStream("EFGHIJ".getBytes("ISO-8859-1"));
        try (MergedStream ms = new MergedStream(null, underlying, buf, 0, buf.length)) {
            // Skip 6: 4 from buffer + 2 from underlying
            long n = ms.skip(6);
            assertEquals(6L, n);
            assertEquals('G', ms.read());
        }
    }

    public void testSkipPastBufferOnlyUnderlying() throws IOException
    {
        byte[] buf = "AB".getBytes("ISO-8859-1");
        InputStream underlying = new ByteArrayInputStream("CDEF".getBytes("ISO-8859-1"));
        try (MergedStream ms = new MergedStream(null, underlying, buf, 0, buf.length)) {
            // First fully drain the buffer
            ms.read();
            ms.read();
            // Now skip from underlying directly
            assertEquals(2L, ms.skip(2));
            assertEquals('E', ms.read());
        }
    }

    // ---------- mark / markSupported / reset ----------

    public void testMarkSupportedFalseWhileBuffered() throws IOException
    {
        byte[] buf = "AB".getBytes("ISO-8859-1");
        // ByteArrayInputStream.markSupported() returns true by default
        InputStream underlying = new ByteArrayInputStream(new byte[]{ 'C' });
        try (MergedStream ms = new MergedStream(null, underlying, buf, 0, buf.length)) {
            // While in buffered segment, marks are not supported
            assertFalse(ms.markSupported());
            ms.read();
            ms.read();
            // After buffer is drained, the underlying stream's capability is reported
            assertTrue(ms.markSupported());
        }
    }

    public void testMarkNoOpWhileBuffered() throws IOException
    {
        byte[] buf = "AB".getBytes("ISO-8859-1");
        // ByteArrayInputStream supports mark/reset, but it should NOT be called
        // while we're still in the buffered segment.
        InputStream underlying = new ByteArrayInputStream("CD".getBytes("ISO-8859-1"));
        try (MergedStream ms = new MergedStream(null, underlying, buf, 0, buf.length)) {
            ms.mark(10);   // should be no-op while buffered
            assertEquals('A', ms.read());
            // reset must also be a no-op while buffered (won't throw)
            ms.reset();
            // We continue from where we left off, NOT back to start
            assertEquals('B', ms.read());
        }
    }

    public void testMarkAndResetDelegatedAfterBufferDrained() throws IOException
    {
        byte[] buf = "AB".getBytes("ISO-8859-1");
        InputStream underlying = new ByteArrayInputStream("CDE".getBytes("ISO-8859-1"));
        try (MergedStream ms = new MergedStream(null, underlying, buf, 0, buf.length)) {
            // Drain buffered segment
            ms.read();
            ms.read();
            ms.mark(10);
            assertEquals('C', ms.read());
            assertEquals('D', ms.read());
            ms.reset();
            assertEquals('C', ms.read());
        }
    }

    // ---------- close() ----------

    public void testCloseClosesUnderlying() throws IOException
    {
        final boolean[] closed = { false };
        InputStream underlying = new ByteArrayInputStream(new byte[]{ 1 }) {
            @Override public void close() throws IOException {
                closed[0] = true;
                super.close();
            }
        };
        MergedStream ms = new MergedStream(null, underlying, new byte[]{ 0 }, 0, 1);
        ms.close();
        assertTrue("close() must propagate to underlying", closed[0]);
    }
}
