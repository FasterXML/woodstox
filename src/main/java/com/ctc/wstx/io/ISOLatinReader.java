/* Woodstox XML processor
 *
 * Copyright (c) 2004- Tatu Saloranta, tatu.saloranta@iki.fi
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
 * Optimized Reader that reads ISO-Latin (aka ISO-8859-1) content from an
 * input stream.
 * In addition to doing (hopefully) optimal conversion, it can also take
 * array of "pre-read" (leftover) bytes; this is necessary when preliminary
 * stream/reader is trying to figure out XML encoding.
 */
public final class ISOLatinReader
    extends BaseReader
{
    boolean mXml11 = false;

    /**
     * Total read byte (and char) count; used for error reporting purposes
     */
    int mByteCount = 0;

    /*
    ////////////////////////////////////////
    // Life-cycle
    ////////////////////////////////////////
    */

    public ISOLatinReader(ReaderConfig cfg, InputStream in, byte[] buf, int ptr, int len,
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
        // Let's then ensure there's enough room...
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

        // Need to load more data?
        int avail = mByteBufferEnd - mBytePtr;
        if (avail <= 0) {
            mByteCount += mByteBufferEnd;
            // Let's always (try to) read full buffers
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

        /* K, have at least one byte == char, good enough; requiring more
         * could block the calling thread too early
         */

        if (len > avail) {
            len = avail;
        }
        int i = mBytePtr;
        int last = i + len;

        if (mXml11) {
            for (; i < last; ) {
                char c = (char) (mByteBuffer[i++] & 0xFF);
                if (c >= CHAR_DEL) {
                    if (c <= 0x9F) {
                        if (c == 0x85) { // NEL, let's convert?
                            c = CONVERT_NEL_TO;
                        } else if (c >= 0x7F) { // DEL, ctrl chars
                            int pos = mByteCount + i;
                            reportInvalidXml11(c, pos, pos);
                        }
                    }
                }
                cbuf[start++] = c;

            }
        } else {
            for (; i < last; ) {
                cbuf[start++] = (char) (mByteBuffer[i++] & 0xFF);
            }
        }

        mBytePtr = last;
        return len;
    }
}

