package org.codehaus.stax.test.stream;

import java.io.*;

/**
 * Test stream used to test whether Reader using this stream would
 * 'accidentally' cause blocking. Used by {@link TestStreaming}
 * unit test suite.
 */
class BlockingStream
    extends FilterInputStream
{
    public boolean mBlocked = false;
    
    // dummy ctor to keep JUnit happy
    public BlockingStream() { super(null); }
    
    public BlockingStream(InputStream is)
    {
        super(is);
    }
    
    public boolean hasBlocked() {
        return mBlocked;
    }

    @Override
    public int read() throws IOException
    {
        int r = super.read();
        if (r < 0) {
            mBlocked = true;
        }
        return r;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException
    {
        int r = super.read(b, off, len);
        if (r < 0) {
            mBlocked = true;
        }
        return r;
    }
}
