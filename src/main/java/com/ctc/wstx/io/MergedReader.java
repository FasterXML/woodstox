package com.ctc.wstx.io;

import java.io.*;

import com.ctc.wstx.api.ReaderConfig;

/**
 * Simple {@link Reader} implementation that is used to "unwind" some
 * data previously read from a Reader; so that as long as some of
 * that data remains, it's returned; but as long as it's read, we'll
 * just use data from the underlying original Reader.
 * This is similar to {@link java.io.PushbackReader}, but with this class
 * there's only one implicit pushback, when instance is constructed; not
 * general pushback buffer and methods to use it.
 */
public final class MergedReader
    extends Reader
{
    final ReaderConfig mConfig;

    final Reader mIn;

    /**
     * This buffer contains the partially read remains left over after
     * bootstrapper has consumed xml declaration (if one found).
     * It is generally recycled and can be returned after having been
     * read.
     */
    char[] mData;

    int mPtr;

    final int mEnd;

    public MergedReader(ReaderConfig cfg, Reader in, char[] buf, int start, int end)
    {
        mConfig = cfg;
        mIn = in;
        mData = buf;
        mPtr = start;
        mEnd = end;
        // sanity check: should not pass empty buffer
        if (buf != null && start >= end) {
            throw new IllegalArgumentException("Trying to construct MergedReader with empty contents (start "+start+", end "+end+")");
        }
    }

    @Override
    public void close() throws IOException
    {
        freeMergedBuffer();
        mIn.close();
    }

    @Override
    public void mark(int readlimit) throws IOException
    {
        if (mData == null) {
            mIn.mark(readlimit);
        }
    }

    @Override
    public boolean markSupported() {
        /* Only supports marks past the initial rewindable section...
         */
        return (mData == null) && mIn.markSupported();
    }
    
    @Override
    public int read() throws IOException
    {
        if (mData != null) {
            int c = mData[mPtr++] & 0xFF;
            if (mPtr >= mEnd) {
                freeMergedBuffer();
            }
            return c;
        }
        return mIn.read();
    }
    
    @Override
    public int read(char[] cbuf) throws IOException {
        return read(cbuf, 0, cbuf.length);
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException
    {
        if (mData != null) {
            int avail = mEnd - mPtr;
            if (len > avail) {
                len = avail;
            }
            System.arraycopy(mData, mPtr, cbuf, off, len);
            mPtr += len;
            if (mPtr >= mEnd) {
                freeMergedBuffer();
            }
            return len;
        }

        return mIn.read(cbuf, off, len);
    }

    @Override
    public boolean ready() throws IOException
    {
        return (mData != null) || mIn.ready();
    }

    @Override
    public void reset() throws IOException
    {
        if (mData == null) {
            mIn.reset();
        }
    }

    @Override
    public long skip(long n) throws IOException
    {
        long count = 0L;

        if (mData != null) {
            int amount = mEnd - mPtr;

            if (amount > n) { // all in pushed back segment?
                mPtr += (int) n;
                return amount;
            }
            freeMergedBuffer();
            count += amount;
            n -= amount;
        }

        if (n > 0) {
            count += mIn.skip(n);
        }
        return count;
    }

    private void freeMergedBuffer()
    {
        if (mData != null) {
            char[] data = mData;
            mData = null;
            if (mConfig != null) {
                mConfig.freeSmallCBuffer(data);
            }
        }
    }
}
