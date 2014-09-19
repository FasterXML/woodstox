/* Woodstox XML processor
 *
 * Copyright (c) 2004 Tatu Saloranta, tatu.saloranta@iki.fi
 *
 * Licensed under the License specified in file LICENSE, included with
 * the source code.
 * You may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ctc.wstx.io;

import java.io.*;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.cfg.XmlConsts;

/**
 * Since JDK does not come with UTF-32/UCS-4, let's implement a simple
 * decoder to use.
 */
public final class UTF32Reader
    extends BaseReader
{
    final boolean mBigEndian;

    boolean mXml11;

    /**
     * Although input is fine with full Unicode set, Java still uses
     * 16-bit chars, so we may have to split high-order chars into
     * surrogate pairs.
     */
    char mSurrogate = NULL_CHAR;

    /**
     * Total read character count; used for error reporting purposes
     */
    int mCharCount = 0;

    /**
     * Total read byte count; used for error reporting purposes
     */
    int mByteCount = 0;

    /*
    ////////////////////////////////////////
    // Life-cycle
    ////////////////////////////////////////
    */

    public UTF32Reader(ReaderConfig cfg, InputStream in, byte[] buf, int ptr, int len,
		       boolean recycleBuffer,
                       boolean isBigEndian)
    {
        super(cfg, in, buf, ptr, len, recycleBuffer);
        mBigEndian = isBigEndian;
    }

    public void setXmlCompliancy(int xmlVersion)
    {
        mXml11 = (xmlVersion == XmlConsts.XML_V_11);
    }

    /*
    ////////////////////////////////////////
    // Public API
    ////////////////////////////////////////
    */

    public int read(char[] cbuf, int start, int len)
        throws IOException
    {
        // Let's first ensure there's enough room...
        if (start < 0 || (start+len) > cbuf.length) {
            reportBounds(cbuf, start, len);
        }
        // Already EOF?
        if (mByteBuffer == null) {
            return -1;
        }
        if (len < 1) {
            return 0;
        }

        len += start;
        int outPtr = start;

        // Ok, first; do we have a surrogate from last round?
        if (mSurrogate != NULL_CHAR) {
            cbuf[outPtr++] = mSurrogate;
            mSurrogate = NULL_CHAR;
            // No need to load more, already got one char
        } else {
            /* Note: we'll try to avoid blocking as much as possible. As a
             * result, we only need to get 4 bytes for a full char.
             */
            int left = (mByteBufferEnd - mBytePtr);
            if (left < 4) {
                if (!loadMore(left)) { // (legal) EOF?
                    return -1;
                }
            }
        }

        byte[] buf = mByteBuffer;

        main_loop:
        while (outPtr < len) {
            int ptr = mBytePtr;
            int ch;

            if (mBigEndian) {
                ch = (buf[ptr] << 24) | ((buf[ptr+1] & 0xFF) << 16)
                    | ((buf[ptr+2] & 0xFF) << 8) | (buf[ptr+3] & 0xFF);
            } else {
                ch = (buf[ptr] & 0xFF) | ((buf[ptr+1] & 0xFF) << 8)
                    | ((buf[ptr+2] & 0xFF) << 16) | (buf[ptr+3] << 24);
            }
            mBytePtr += 4;

            // Does it need to be split to surrogates?
            // (also, we can and need to verify illegal chars)
            if (ch >= 0x7F) {
                if (ch <= 0x9F) {
                    if (mXml11) { // high-order ctrl char detection...
                        if (ch != 0x85) {
                            reportInvalid(ch, outPtr-start, "(can only be included via entity in xml 1.1)");
                        }
                        ch = CONVERT_NEL_TO;
                    }
                } else if (ch >= 0xD800) {
                    // Illegal?
                    if (ch > XmlConsts.MAX_UNICODE_CHAR) {
                        reportInvalid(ch, outPtr-start,
                                      "(above "+Integer.toHexString(XmlConsts.MAX_UNICODE_CHAR)+") ");
                    }
                    if (ch > 0xFFFF) { // need to split into surrogates?
                        ch -= 0x10000; // to normalize it starting with 0x0
                        cbuf[outPtr++] = (char) (0xD800 + (ch >> 10));
                        // hmmh. can this ever be 0? (not legal, at least?)
                        ch = (0xDC00 | (ch & 0x03FF));
                        // Room for second part?
                        if (outPtr >= len) { // nope
                            mSurrogate = (char) ch;
                            break main_loop;
                        }
                    } else { // in 16-bit range... just need validity checks
                        if (ch < 0xE000) {
                            reportInvalid(ch, outPtr-start, "(a surrogate char) ");
                        } else if (ch >= 0xFFFE) {
                            reportInvalid(ch, outPtr-start, "");
                        }
                    }
                } else if (ch == 0x2028 && mXml11) { // LSEP
                    ch = CONVERT_LSEP_TO;
                }
            }
            cbuf[outPtr++] = (char) ch;
            if (mBytePtr >= mByteBufferEnd) {
                break main_loop;
            }
        }

        len = outPtr - start;
        mCharCount += len;
        return len;
    }

    /*
    ////////////////////////////////////////
    // Internal methods
    ////////////////////////////////////////
    */

    private void reportUnexpectedEOF(int gotBytes, int needed)
        throws IOException
    {
        int bytePos = mByteCount + gotBytes;
        int charPos = mCharCount;

        throw new CharConversionException("Unexpected EOF in the middle of a 4-byte UTF-32 char: got "
                                          +gotBytes+", needed "+needed
                                          +", at char #"+charPos+", byte #"+bytePos+")");
    }

    private void reportInvalid(int value, int offset, String msg)
        throws IOException
    {
        int bytePos = mByteCount + mBytePtr - 1;
        int charPos = mCharCount + offset;

        throw new CharConversionException("Invalid UTF-32 character 0x"
                                          +Integer.toHexString(value)
                                          +msg+" at char #"+charPos+", byte #"+bytePos+")");
    }

    /**
     * @param available Number of "unused" bytes in the input buffer
     *
     * @return True, if enough bytes were read to allow decoding of at least
     *   one full character; false if EOF was encountered instead.
     */
    private boolean loadMore(int available)
        throws IOException
    {
        mByteCount += (mByteBufferEnd - available);

        // Bytes that need to be moved to the beginning of buffer?
        if (available > 0) {
	    /* 11-Nov-2008, TSa: can only move if we own the buffer; otherwise
	     *   we are stuck with the data.
	     */
            if (mBytePtr > 0 && canModifyBuffer()) {
                for (int i = 0; i < available; ++i) {
                    mByteBuffer[i] = mByteBuffer[mBytePtr+i];
                }
                mBytePtr = 0;
		mByteBufferEnd = available;
            }
        } else {
            /* Ok; here we can actually reasonably expect an EOF,
             * so let's do a separate read right away:
             */
            int count = readBytes();
            if (count < 1) {
                if (count < 0) { // -1
                    freeBuffers(); // to help GC?
                    return false;
                }
                // 0 count is no good; let's err out
                reportStrangeStream();
            }
        }

        /* Need at least 4 bytes; if we don't get that many, it's an
         * error.
         */
        while (mByteBufferEnd < 4) {
            int count = readBytesAt(mByteBufferEnd);
            if (count < 1) {
                if (count < 0) { // -1, EOF... no good!
                    freeBuffers(); // to help GC?
                    reportUnexpectedEOF(mByteBufferEnd, 4);
                }
                // 0 count is no good; let's err out
                reportStrangeStream();
            }
        }
        return true;
    }
}

