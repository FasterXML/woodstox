package com.ctc.wstx.util;

/**
 * Class similar to {@link StringBuilder}, except that it can be used to
 * construct multiple Strings, that will share same underlying character
 * buffer. This is generally useful for closely related value Strings,
 * such as attribute values of a single XML start element.
 */
public final class TextBuilder
{
    private final static int MIN_LEN = 60;
    private final static int MAX_LEN = 120;

    private char[] mBuffer;

    private int mBufferLen;

    private String mResultString;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Life-cycle:
    ///////////////////////////////////////////////////////////////////////
     */

    public TextBuilder(int initialSize)
    {
        int charSize = (initialSize << 4); // multiply by 16 (-> def. 192 chars)
        if (charSize < MIN_LEN) {
            charSize = MIN_LEN;
        } else if (charSize > MAX_LEN) {
            charSize = MAX_LEN;
        }
        mBuffer = new char[charSize];
    }

    /**
     * Method called before starting to (re)use the buffer, will discard
     * any existing content, and start collecting new set of values.
     */
    public void reset() {
        mBufferLen = 0;
        mResultString = null;
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Accessors
    ///////////////////////////////////////////////////////////////////////
     */

    public boolean isEmpty() {
        return mBufferLen == 0;
    }

    public String getAllValues()
    {
        if (mResultString == null) {
            mResultString = new String(mBuffer, 0, mBufferLen);
        }
        return mResultString;
    }

    /**
     * Method that gives access to underlying character buffer
     */
    public char[] getCharBuffer() {
        return mBuffer;
    }

    public int getCharSize() {
        return mBufferLen;
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Mutators
    ///////////////////////////////////////////////////////////////////////
     */

    public void append(char c) {
        if (mBuffer.length == mBufferLen) {
            resize(1);
        }
        mBuffer[mBufferLen++] = c;
    }

    public void append(char[] src, int start, int len) {
        if (len > (mBuffer.length - mBufferLen)) {
            resize(len);
        }
        System.arraycopy(src, start, mBuffer, mBufferLen, len);
        mBufferLen += len;
    }

    public void setBufferSize(int newSize) {
        mBufferLen = newSize;
    }

    public char[] bufferFull(int needSpaceFor) {
        mBufferLen = mBuffer.length;
        resize(needSpaceFor);
        return mBuffer;
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Debug support
    ///////////////////////////////////////////////////////////////////////
     */

    @Override
    public String toString() {
        return new String(mBuffer, 0, mBufferLen);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Internal methodss
    ///////////////////////////////////////////////////////////////////////
     */

    private void resize(int needSpaceFor) {
        char[] old = mBuffer;
        int oldLen = old.length;
        int addition = oldLen >> 1; // Grow by 50%
        needSpaceFor -= (oldLen - mBufferLen);
        if (addition < needSpaceFor) {
            addition = needSpaceFor;
        }
        mBuffer = new char[oldLen+addition];
        System.arraycopy(old, 0, mBuffer, 0, mBufferLen);
    }
}
