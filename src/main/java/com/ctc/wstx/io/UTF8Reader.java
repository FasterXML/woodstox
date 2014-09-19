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
 * Optimized Reader that reads UTF-8 encoded content from an input stream.
 * In addition to doing (hopefully) optimal conversion, it can also take
 * array of "pre-read" (leftover) bytes; this is necessary when preliminary
 * stream/reader is trying to figure out XML encoding.
 */
public final class UTF8Reader
    extends BaseReader
{
    boolean mXml11 = false;

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

    public UTF8Reader(ReaderConfig cfg, InputStream in, byte[] buf, int ptr, int len,
		      boolean recycleBuffer)
    {
        super(cfg, in, buf, ptr, len, recycleBuffer);
    }

    @Override
    public void setXmlCompliancy(int xmlVersion) {
        mXml11 = (xmlVersion == XmlConsts.XML_V_11);
    }

    /*
    ////////////////////////////////////////
    // Public API
    ////////////////////////////////////////
    */

    @SuppressWarnings("cast")
    @Override
    public int read(char[] cbuf, int start, int len) throws IOException
    {
        // Let's first ensure there's enough room...
        if (start < 0 || (start+len) > cbuf.length) {
            reportBounds(cbuf, start, len);
        }
        // Already EOF?
        if (mByteBuffer == null) {
            return -1;
        }
        if (len < 1) { // dummy call?
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
            /* To prevent unnecessary blocking (esp. with network streams),
             * we'll only require decoding of a single char
             */
            int left = (mByteBufferEnd - mBytePtr);

            /* So; only need to load more if we can't provide at least
             * one more character. We need not do thorough check here,
             * but let's check the common cases here: either completely
             * empty buffer (left == 0), or one with less than max. byte
             * count for a single char, and starting of a multi-byte
             * encoding (this leaves possibility of a 2/3-byte char
             * that is still fully accessible... but that can be checked
             * by the load method)
             */

            if (left < 4) {
                // Need to load more?
                if (left < 1 || mByteBuffer[mBytePtr] < 0) {
                    if (!loadMore(left)) { // (legal) EOF?
                        return -1;
                    }
                }
            }
        }

        /* This may look silly, but using a local var is indeed faster
         * (if and when HotSpot properly gets things running) than
         * member variable...
         */
        byte[] buf = mByteBuffer;
        int inPtr = mBytePtr;
        int inBufLen = mByteBufferEnd;

        main_loop:
        while (outPtr < len) {
            // At this point we have at least one byte available
            int c = (int) buf[inPtr++];

            /* Let's first do the quickie loop for common case; 7-bit
             * ascii:
             */
            if (c >= 0) { // ascii? can probably loop, then
                if (c == 0x7F && mXml11) { // DEL illegal in xml1.1
                    int bytePos = mByteCount + inPtr - 1;
                    int charPos = mCharCount + (outPtr-start);
                    reportInvalidXml11(c, bytePos, charPos);
                }
                cbuf[outPtr++] = (char) c; // ok since MSB is never on

                /* Ok, how many such chars could we safely process
                 * without overruns? (will combine 2 in-loop comparisons
                 * into just one)
                 */
                int outMax = (len - outPtr); // max output
                int inMax = (inBufLen - inPtr); // max input
                int inEnd = inPtr + ((inMax < outMax) ? inMax : outMax);

                ascii_loop:
                while (true) {
                    if (inPtr >= inEnd) {
                        break main_loop;
                    }
                    c = ((int) buf[inPtr++]) & 0xFF;
                    if (c >= 0x7F) { // DEL, or multi-byte
                        break ascii_loop;
                    }
                    cbuf[outPtr++] = (char) c;
                }
                if (c == 0x7F) { 
                    if (mXml11) { // DEL illegal in xml1.1
                        int bytePos = mByteCount + inPtr - 1;
                        int charPos = mCharCount + (outPtr-start);
                        reportInvalidXml11(c, bytePos, charPos);
                    } // but not in xml 1.0
                    cbuf[outPtr++] = (char) c;
                    if(inPtr >= inEnd){
                    	break main_loop;
                    }
                    continue main_loop;
                }
            }

            int needed;

            // Ok; if we end here, we got multi-byte combination
            if ((c & 0xE0) == 0xC0) { // 2 bytes (0x0080 - 0x07FF)
                c = (c & 0x1F);
                needed = 1;
            } else if ((c & 0xF0) == 0xE0) { // 3 bytes (0x0800 - 0xFFFF)
                c = (c & 0x0F);
                needed = 2;
            } else if ((c & 0xF8) == 0xF0) {
                // 4 bytes; double-char BS, with surrogates and all...
                c = (c & 0x0F);
                needed = 3;
            } else {
                reportInvalidInitial(c & 0xFF, outPtr-start);
                // never gets here...
                needed = 1;
            }
            /* Do we have enough bytes? If not, let's just push back the
             * byte and leave, since we have already gotten at least one
             * char decoded. This way we will only block (with read from
             * input stream) when absolutely necessary.
             */
            if ((inBufLen - inPtr) < needed) {
                --inPtr;
                break main_loop;
            }

            int d = (int) buf[inPtr++];
            if ((d & 0xC0) != 0x080) {
                reportInvalidOther(d & 0xFF, outPtr-start);
            }
            c = (c << 6) | (d & 0x3F);

            if (needed > 1) { // needed == 1 means 2 bytes total
                d = buf[inPtr++]; // 3rd byte
                if ((d & 0xC0) != 0x080) {
                    reportInvalidOther(d & 0xFF, outPtr-start);
                }
                c = (c << 6) | (d & 0x3F);
                if (needed > 2) { // 4 bytes? (need surrogates)
                    d = buf[inPtr++];
                    if ((d & 0xC0) != 0x080) {
                        reportInvalidOther(d & 0xFF, outPtr-start);
                    }
                    c = (c << 6) | (d & 0x3F);
                    if (c > XmlConsts.MAX_UNICODE_CHAR) {
                        reportInvalid(c, outPtr-start,
                                      "(above "+Integer.toHexString(XmlConsts.MAX_UNICODE_CHAR)+") ");
                    }
                    /* Ugh. Need to mess with surrogates. Ok; let's inline them
                     * there, then, if there's room: if only room for one,
                     * need to save the surrogate for the rainy day...
                     */
                    c -= 0x10000; // to normalize it starting with 0x0
                    cbuf[outPtr++] = (char) (0xD800 + (c >> 10));
                    // hmmh. can this ever be 0? (not legal, at least?)
                    c = (0xDC00 | (c & 0x03FF));

                    // Room for second part?
                    if (outPtr >= len) { // nope
                        mSurrogate = (char) c;
                        break main_loop;
                    }
                    // sure, let's fall back to normal processing:
                } else {
                    /* Otherwise, need to check that 3-byte chars are
                     * legal ones (should not expand to surrogates;
                     * 0xFFFE and 0xFFFF are illegal)
                     */
                    if (c >= 0xD800) {
                        // But first, let's check max chars:
                        if (c < 0xE000) {
                            reportInvalid(c, outPtr-start, "(a surrogate character) ");
                        } else if (c >= 0xFFFE) {
                            reportInvalid(c, outPtr-start, "");
                        }
                    } else if (mXml11 && c == 0x2028) { // LSEP?
                        /* 10-May-2006, TSa: Since LSEP is "non-associative",
                         *    it needs additional handling. One way to do
                         *    this is to convert preceding \r to \n. This
                         *    should be implemented better when integrating
                         *    decoder and tokenizer.
                         */
                        if (outPtr > start && cbuf[outPtr-1] == '\r') {
                            cbuf[outPtr-1] = '\n';
                        }
                        c = CONVERT_LSEP_TO;
                    }
                }
            } else { // (needed == 1)
                if (mXml11) { // high-order ctrl char detection...
                    if (c <= 0x9F) {
                        if (c == 0x85) { // NEL, let's convert?
                            c = CONVERT_NEL_TO;
                        } else if (c >= 0x7F) { // DEL, ctrl chars
                            int bytePos = mByteCount + inPtr - 1;
                            int charPos = mCharCount + (outPtr-start);
                            reportInvalidXml11(c, bytePos, charPos);
                        }
                    }
                }
            }
            cbuf[outPtr++] = (char) c;
            if (inPtr >= inBufLen) {
                break main_loop;
            }
        }

        mBytePtr = inPtr;
        len = outPtr - start;
        mCharCount += len;
        return len;
    }

    /*
    ////////////////////////////////////////
    // Internal methods
    ////////////////////////////////////////
    */

    private void reportInvalidInitial(int mask, int offset)
        throws IOException
    {
        // input (byte) ptr has been advanced by one, by now:
        int bytePos = mByteCount + mBytePtr - 1;
        int charPos = mCharCount + offset + 1;

        throw new CharConversionException("Invalid UTF-8 start byte 0x"
                                          +Integer.toHexString(mask)
                                          +" (at char #"+charPos+", byte #"+bytePos+")");
    }

    private void reportInvalidOther(int mask, int offset)
        throws IOException
    {
        int bytePos = mByteCount + mBytePtr - 1;
        int charPos = mCharCount + offset;

        throw new CharConversionException("Invalid UTF-8 middle byte 0x"
                                          +Integer.toHexString(mask)
                                          +" (at char #"+charPos+", byte #"+bytePos+")");
    }

    private void reportUnexpectedEOF(int gotBytes, int needed)
        throws IOException
    {
        int bytePos = mByteCount + gotBytes;
        int charPos = mCharCount;

        throw new CharConversionException("Unexpected EOF in the middle of a multi-byte char: got "
                                          +gotBytes+", needed "+needed
                                          +", at char #"+charPos+", byte #"+bytePos+")");
    }

    private void reportInvalid(int value, int offset, String msg)
        throws IOException
    { 
        int bytePos = mByteCount + mBytePtr - 1;
        int charPos = mCharCount + offset;

        throw new CharConversionException("Invalid UTF-8 character 0x"
                                          +Integer.toHexString(value)+msg
                                          +" at char #"+charPos+", byte #"+bytePos+")");
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

        /* We now have at least one byte... and that allows us to
         * calculate exactly how many bytes we need!
         */
        @SuppressWarnings("cast")
        int c = (int) mByteBuffer[mBytePtr];
        if (c >= 0) { // single byte (ascii) char... cool, can return
            return true;
        }

        // Ok, a multi-byte char, let's check how many bytes we'll need:
        int needed;
        if ((c & 0xE0) == 0xC0) { // 2 bytes (0x0080 - 0x07FF)
            needed = 2;
        } else if ((c & 0xF0) == 0xE0) { // 3 bytes (0x0800 - 0xFFFF)
            needed = 3;
        } else if ((c & 0xF8) == 0xF0) {
            // 4 bytes; double-char BS, with surrogates and all...
            needed = 4;
        } else {
            reportInvalidInitial(c & 0xFF, 0);
            // never gets here... but compiler whines without this:
            needed = 1;
        }

        /* And then we'll just need to load up to that many bytes;
         * if an EOF is hit, that'll be an error. But we need not do
         * actual decoding here, just load enough bytes.
         */
        while ((mBytePtr + needed) > mByteBufferEnd) {
            int count = readBytesAt(mByteBufferEnd);
            if (count < 1) {
                if (count < 0) { // -1, EOF... no good!
                    freeBuffers();
                    reportUnexpectedEOF(mByteBufferEnd, needed);
                }
                // 0 count is no good; let's err out
                reportStrangeStream();
            }
        }
        return true;
    }
}

