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
 * Optimized Reader that reads ascii content from an input stream.
 * In addition to doing (hopefully) optimal conversion, it can also take
 * array of "pre-read" (leftover) bytes; this is necessary when preliminary
 * stream/reader is trying to figure out XML encoding.
 */
public final class AsciiReader
    extends BaseReader
{
    boolean mXml11 = false;

   /**
     * Total read character count; used for error reporting purposes
     * (note: byte count is the same, due to fixed one-byte-per char mapping)
     */
    int mCharCount = 0;

    /*
    ////////////////////////////////////////
    // Life-cycle
    ////////////////////////////////////////
    */

    public AsciiReader(ReaderConfig cfg, InputStream in, byte[] buf, int ptr, int len,
		       boolean recycleBuffer)
    {
        super(cfg, in, buf, ptr, len, recycleBuffer);
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

        // Need to load more data?
        int avail = mByteBufferEnd - mBytePtr;
        if (avail <= 0) {
            mCharCount += mByteBufferEnd;
            // Let's always try to read full buffers, actually...
            int count = readBytes();
            if (count <= 0) {
                if (count == 0) {
                    reportStrangeStream();
                }
                /* Let's actually then free the buffer right away; shouldn't
                 * yet close the underlying stream though?
                 */
                freeBuffers(); // to help GC?
                return -1;
            }
            avail = count;
        }

        // K, have at least one byte == char, good enough:

        if (len > avail) {
            len = avail;
        }
        int i = mBytePtr;
        int last = i + len;

        for (; i < last; ) {
            char c = (char) mByteBuffer[i++];
            if (c >= CHAR_DEL) {
                if (c > CHAR_DEL) {
                    reportInvalidAscii(c);
                } else {
                    if (mXml11) {
                        int pos = mCharCount + mBytePtr;
                        reportInvalidXml11(c, pos, pos);
                    }
                }
            }
            cbuf[start++] = c;
        }

        mBytePtr = last;
        return len;
    }

    /*
    ////////////////////////////////////////
    // Internal methods
    ////////////////////////////////////////
    */

    private void reportInvalidAscii(char c)
        throws IOException
    {
        throw new CharConversionException("Invalid ascii byte; value above 7-bit ascii range ("+((int) c)+"; at pos #"+(mCharCount + mBytePtr)+")");
    }
}

