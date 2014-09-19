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

package com.ctc.wstx.sw;

import java.io.*;

import javax.xml.stream.XMLStreamException;

import com.ctc.wstx.api.WriterConfig;
import com.ctc.wstx.io.CharsetNames;

/**
 * Concrete implementation of {@link EncodingXmlWriter} used when output
 * is to be encoded using ISO-8859-1, aka ISO-Latin1 encoding.
 *<p>
 * Regarding surrogate pair handling: most of the checks are in the base
 * class, and here we only need to worry about <code>writeRaw</code>
 * methods.
 */
public final class ISOLatin1XmlWriter
    extends EncodingXmlWriter
{
    public ISOLatin1XmlWriter(OutputStream out, WriterConfig cfg, boolean autoclose)
        throws IOException
    {
        super(out, cfg, CharsetNames.CS_ISO_LATIN1, autoclose);
    }

    public void writeRaw(char[] cbuf, int offset, int len)
        throws IOException
    {
        if (mSurrogate != 0) {
            throwUnpairedSurrogate();
        }

        int ptr = mOutputPtr;
        while (len > 0) {
            int max = mOutputBuffer.length - ptr;
            if (max < 1) { // output buffer full?
                mOutputPtr = ptr;
                flushBuffer();
                ptr = 0;
                max = mOutputBuffer.length;
            }
            // How much can we output?
            if (max > len) {
                max = len;
            }
            if (mCheckContent) {
                for (int inEnd = offset + max; offset < inEnd; ++offset) {
                    int c = cbuf[offset];
                    if (c < 32) {
                        if (c == '\n') {
                            // !!! TBI: line nr
                        } else if (c == '\r') {
                            // !!! TBI: line nr (and skipping \n that may follow)
                        } else if (c != '\t') {
                            mOutputPtr = ptr;
                            c = handleInvalidChar(c);
                        }
                    } else if (c > 0x7E) {
                        if (c > 0xFF) {
                            mOutputPtr = ptr;
                            handleInvalidLatinChar(c);
                        } else if (mXml11) {
                            if (c < 0x9F && c != 0x85) {
                                mOutputPtr = ptr;
                                c = handleInvalidChar(c);
                            }
                        }
                    }
                    mOutputBuffer[ptr++] = (byte) c;
                }
            } else {
                for (int inEnd = offset + max; offset < inEnd; ++offset) {
                    mOutputBuffer[ptr++] = (byte) cbuf[offset];
                }
            }
            len -= max;
        }
        mOutputPtr = ptr;
    }

    public void writeRaw(String str, int offset, int len)
        throws IOException
    {
        if (mSurrogate != 0) {
            throwUnpairedSurrogate();
        }
        int ptr = mOutputPtr;
        while (len > 0) {
            int max = mOutputBuffer.length - ptr;
            if (max < 1) { // output buffer full?
                mOutputPtr = ptr;
                flushBuffer();
                ptr = 0;
                max = mOutputBuffer.length;
            }
            // How much can we output?
            if (max > len) {
                max = len;
            }
            if (mCheckContent) {
                for (int inEnd = offset + max; offset < inEnd; ++offset) {
                    int c = str.charAt(offset);
                    if (c < 32) {
                        if (c == '\n') {
                            // !!! TBI: line nr
                        } else if (c == '\r') {
                            // !!! TBI: line nr (and skipping \n that may follow)
                        } else if (c != '\t') {
                            mOutputPtr = ptr;
                            c = handleInvalidChar(c);
                        }
                    } else if (c > 0x7E) {
                        if (c > 0xFF) {
                            mOutputPtr = ptr;
                            handleInvalidLatinChar(c);
                        } else if (mXml11) {
                            if (c < 0x9F && c != 0x85) {
                                mOutputPtr = ptr;
                                c = handleInvalidChar(c);
                            }
                        }
                    }
                    mOutputBuffer[ptr++] = (byte) c;
                }
            } else {
                for (int inEnd = offset + max; offset < inEnd; ++offset) {
                    mOutputBuffer[ptr++] = (byte) str.charAt(offset);
                }
            }
            len -= max;
        }
        mOutputPtr = ptr;
    }

    protected void writeAttrValue(String data)
        throws IOException
    {
        int offset = 0;
        int len = data.length();
        int ptr = mOutputPtr;

        main_loop:
        while (len > 0) {
            int max = mOutputBuffer.length - ptr;
            if (max < 1) { // output buffer full?
                mOutputPtr = ptr;
                flushBuffer();
                ptr = 0;
                max = mOutputBuffer.length;
            }
            // Do we start with a surrogate?
            if (mSurrogate != 0) {
                int sec = data.charAt(offset++);
                sec = calcSurrogate(sec);
                mOutputPtr = ptr;
                ptr = writeAsEntity(sec);
                --len;
                continue main_loop;
            }
            // How much can we output?
            if (max > len) {
                max = len;
            }
            inner_loop:
            for (int inEnd = offset + max; offset < inEnd; ) {
                int c = data.charAt(offset++);
                if (c < 32) {
                    /* Need to quote all white space except for regular
                     * space chars, to preserve them (round-tripping)
                     */
                    if (c == '\r') {
                        if (!mEscapeCR) {
                            mOutputBuffer[ptr++] = (byte) c;
                            continue;
                        }
                    } else if (c != '\n' && c != '\t') {
                        if (mCheckContent) {
                            if (!mXml11 || c == 0) {
                                c = handleInvalidChar(c);
                                mOutputBuffer[ptr++] = (byte) c;
                                continue;
                            }
                        }
                    }
                    // fall-through to char entity output
                } else if (c < 0x7F) {
                    if (c != '<' && c != '&' && c != '"') {
                        mOutputBuffer[ptr++] = (byte) c;
                        continue;
                    }
                    // otherwise fall back on quoting
                } else if (c > 0x9F && c <= 0xFF) {
                    mOutputBuffer[ptr++] = (byte) c;
                    continue; // [WSTX-88]
                } else {
                    // Surrogate?
                    if (c >= SURR1_FIRST && c <= SURR2_LAST) {
                        mSurrogate = c;
                        // Last char needs special handling:
                        if (offset == inEnd) {
                            break inner_loop;
                        }
                        c = calcSurrogate(data.charAt(offset++));
                        // Let's fall down to entity output
                    }
                }
                /* Has to be escaped as char entity; as such, also need
                 * to re-calc max. continguous data we can output
                 */
                mOutputPtr = ptr;
                ptr = writeAsEntity(c);
                len = data.length() - offset;
                continue main_loop;
            }
            len -= max;
        }
        mOutputPtr = ptr;
    }

    protected void writeAttrValue(char[] data, int offset, int len)
        throws IOException
    {
        int ptr = mOutputPtr;

        main_loop:
        while (len > 0) {
            int max = mOutputBuffer.length - ptr;
            if (max < 1) { // output buffer full?
                mOutputPtr = ptr;
                flushBuffer();
                ptr = 0;
                max = mOutputBuffer.length;
            }
            // Do we start with a surrogate?
            if (mSurrogate != 0) {
                int sec = data[offset++];
                sec = calcSurrogate(sec);
                mOutputPtr = ptr;
                ptr = writeAsEntity(sec);
                --len;
                continue main_loop;
            }
            // How much can we output?
            if (max > len) {
                max = len;
            }
            inner_loop:
            for (int inEnd = offset + max; offset < inEnd; ) {
                int c = data[offset++];
                if (c < 32) {
                    /* Need to quote all white space except for regular
                     * space chars, to preserve them (round-tripping)
                     */
                    if (c == '\r') {
                        if (!mEscapeCR) {
                            mOutputBuffer[ptr++] = (byte) c;
                            continue;
                        }
                    } else if (c != '\n' && c != '\t') {
                        if (mCheckContent) {
                            if (!mXml11 || c == 0) {
                                c = handleInvalidChar(c);
                                mOutputBuffer[ptr++] = (byte) c;
                                continue;
                            }
                        }
                    }
                    // fall-through to char entity output
                } else if (c < 0x7F) {
                    if (c != '<' && c != '&' && c != '"') {
                        mOutputBuffer[ptr++] = (byte) c;
                        continue;
                    }
                    // otherwise fall back on quoting
                } else if (c > 0x9F && c <= 0xFF) {
                    mOutputBuffer[ptr++] = (byte) c;
                    continue; // [WSTX-88]
                } else {
                    // Surrogate?
                    if (c >= SURR1_FIRST && c <= SURR2_LAST) {
                        mSurrogate = c;
                        // Last char needs special handling:
                        if (offset == inEnd) {
                            break inner_loop;
                        }
                        c = calcSurrogate(data[offset++]);
                        // Let's fall down to entity output
                    }
                }
                /* Has to be escaped as char entity; as such, also need
                 * to re-calc max. contiguous data we can output
                 */
                mOutputPtr = ptr;
                ptr = writeAsEntity(c);
                max -= (inEnd - offset); // since we didn't loop completely
                break inner_loop;
            }
            len -= max;
        }
        mOutputPtr = ptr;
    }

    protected int writeCDataContent(String data)
        throws IOException
    {
        // Note: mSurrogate can not be non-zero at this point, no need to check

        int offset = 0;
        int len = data.length();
        if (!mCheckContent) {
            writeRaw(data, offset, len);
            return -1;
        }
        int ptr = mOutputPtr;

        main_loop:
        while (len > 0) {
            int max = mOutputBuffer.length - ptr;
            if (max < 1) { // output buffer full?
                mOutputPtr = ptr;
                flushBuffer();
                ptr = 0;
                max = mOutputBuffer.length;
            }
            // How much can we output?
            if (max > len) {
                max = len;
            }
            for (int inEnd = offset + max; offset < inEnd; ) {
                int c = data.charAt(offset++);
                if (c < 32) {
                    if (c == '\n') {
                        // !!! TBI: line nr
                    } else if (c == '\r') {
                        // !!! TBI: line nr (and skipping \n that may follow)
                    } else if (c != '\t') {
                        mOutputPtr = ptr;
                        c = handleInvalidChar(c);
                    }
                } else if (c > 0x7E) {
                    if (c > 0xFF) {
                        mOutputPtr = ptr;
                        handleInvalidLatinChar(c);
                    } else if (mXml11) {
                        if (c < 0x9F && c != 0x85) {
                            mOutputPtr = ptr;
                            c = handleInvalidChar(c);
                        }
                    }
                } else if (c == '>') { // embedded "]]>"?
                    if (offset > 2 && data.charAt(offset-2) == ']'
                        && data.charAt(offset-3) == ']') {
                        if (!mFixContent) {
                            return offset-3;
                        }
                        /* Relatively easy fix; just need to close this
                         * section, and open a new one...
                         */
                        mOutputPtr = ptr;
                        writeCDataEnd();
                        writeCDataStart();
                        writeAscii(BYTE_GT);
                        ptr = mOutputPtr;
                        /* No guarantees there's as much free room in the
                         * output buffer, thus, need to restart loop:
                         */
                        len = data.length() - offset;
                        continue main_loop;
                    }
                }
                mOutputBuffer[ptr++] = (byte) c;
            }
            len -= max;
        }
        mOutputPtr = ptr;
        return -1;
    }

    protected int writeCDataContent(char[] cbuf, int start, int len)
        throws IOException
    {
        // Note: mSurrogate can not be non-zero at this point, no need to check

        if (!mCheckContent) {
            writeRaw(cbuf, start, len);
            return -1;
        }

        int ptr = mOutputPtr;
        int offset = start;

        while (len > 0) {
            int max = mOutputBuffer.length - ptr;
            if (max < 1) { // output buffer full?
                mOutputPtr = ptr;
                flushBuffer();
                ptr = 0;
                max = mOutputBuffer.length;
            }
            // How much can we output?
            if (max > len) {
                max = len;
            }
            inner_loop:
            for (int inEnd = offset + max; offset < inEnd; ) {
                int c = cbuf[offset++];
                if (c < 32) {
                    if (c == '\n') {
                        // !!! TBI: line nr
                    } else if (c == '\r') {
                        // !!! TBI: line nr (and skipping \n that may follow)
                    } else if (c != '\t') {
                        mOutputPtr = ptr;
                        c = handleInvalidChar(c);
                    }
                } else if (c > 0x7E) {
                    if (c > 0xFF) {
                        mOutputPtr = ptr;
                        handleInvalidLatinChar(c);
                    } else if (mXml11) {
                        if (c < 0x9F && c != 0x85) {
                            mOutputPtr = ptr;
                            c = handleInvalidChar(c);
                        }
                    }
                } else if (c == '>') { // embedded "]]>"?
                    if (offset >= (start+3) && cbuf[offset-2] == ']'
                        && cbuf[offset-3] == ']') {
                        if (!mFixContent) {
                            return offset-3;
                        }
                        /* Relatively easy fix; just need to close this
                         * section, and open a new one...
                         */
                        mOutputPtr = ptr;
                        writeCDataEnd();
                        writeCDataStart();
                        writeAscii(BYTE_GT);
                        ptr = mOutputPtr;
                        /* No guarantees there's as much free room in the
                         * output buffer, thus, need to restart loop:
                         */
                        max -= (inEnd - offset);
                        break inner_loop;
                    }
                }
                mOutputBuffer[ptr++] = (byte) c;
            }
            len -= max;
        }
        mOutputPtr = ptr;
        return -1;
    }

    protected int writeCommentContent(String data)
        throws IOException
    {
        // Note: mSurrogate can not be non-zero at this point, no need to check

        int offset = 0;
        int len = data.length();
        if (!mCheckContent) {
            writeRaw(data, offset, len);
            return -1;
        }

        int ptr = mOutputPtr;

        while (len > 0) {
            int max = mOutputBuffer.length - ptr;
            if (max < 1) { // output buffer full?
                mOutputPtr = ptr;
                flushBuffer();
                ptr = 0;
                max = mOutputBuffer.length;
            }
            // How much can we output?
            if (max > len) {
                max = len;
            }
            inner_loop:
            for (int inEnd = offset + max; offset < inEnd; ) {
                int c = data.charAt(offset++);
                if (c < 32) {
                    if (c == '\n') {
                        // !!! TBI: line nr
                    } else if (c == '\r') {
                        // !!! TBI: line nr (and skipping \n that may follow)
                    } else if (c != '\t') {
                        mOutputPtr = ptr;
                        c = handleInvalidChar(c);
                    }
                } else if (c > 0x7E) {
                    if (c > 0xFF) {
                        mOutputPtr = ptr;
                        handleInvalidLatinChar(c);
                    } else if (mXml11) {
                        if (c < 0x9F && c != 0x85) {
                            mOutputPtr = ptr;
                            c = handleInvalidChar(c);
                        }
                    }
                } else if (c == '-') { // embedded "--"?
                    if (offset > 1 && data.charAt(offset-2) == '-') {
                        if (!mFixContent) {
                            return offset-2;
                        }
                        /* Quite easy to fix: just add an extra space
                         * in front. There will be room for that char;
                         * but may need to take that the following '-'
                         * also fits.
                         */
                        mOutputBuffer[ptr++] = ' ';
                        if (ptr >= mOutputBuffer.length) { // whops. need to flush
                            mOutputPtr = ptr;
                            flushBuffer();
                            ptr = 0;
                        }
                        mOutputBuffer[ptr++] = BYTE_HYPHEN;
                        /* Also, since we did output an extra char, better
                         * restart the loop (since max calculation is now
                         * off)
                         */
                        max -= (inEnd - offset);
                        break inner_loop;
                    }
                }
                mOutputBuffer[ptr++] = (byte) c;
            }
            len -= max;
        }
        mOutputPtr = ptr;
        return -1;
    }

    protected int writePIData(String data)
        throws IOException, XMLStreamException
    {
        // Note: mSurrogate can not be non-zero at this point, no need to check

        int offset = 0;
        int len = data.length();
        if (!mCheckContent) {
            writeRaw(data, offset, len);
            return -1;
        }

        int ptr = mOutputPtr;
        while (len > 0) {
            int max = mOutputBuffer.length - ptr;
            if (max < 1) { // output buffer full?
                mOutputPtr = ptr;
                flushBuffer();
                ptr = 0;
                max = mOutputBuffer.length;
            }
            // How much can we output?
            if (max > len) {
                max = len;
            }
            for (int inEnd = offset + max; offset < inEnd; ++offset) {
                int c = data.charAt(offset);
                if (c < 32) {
                    if (c == '\n') {
                        // !!! TBI: line nr
                    } else if (c == '\r') {
                        // !!! TBI: line nr (and skipping \n that may follow)
                    } else if (c != '\t') {
                        mOutputPtr = ptr;
                        c = handleInvalidChar(c);
                    }
                } else if (c > 0x7E) {
                    if (c > 0xFF) {
                        mOutputPtr = ptr;
                        handleInvalidLatinChar(c);
                    } else if (mXml11) {
                        if (c < 0x9F && c != 0x85) {
                            mOutputPtr = ptr;
                            c = handleInvalidChar(c);
                        }
                    }
                } else if (c == '>') { // enclosed end marker ("?>")?
                    if (offset > 0 && data.charAt(offset-1) == '?') {
                        return offset-2;
                    }
                }
                mOutputBuffer[ptr++] = (byte) c;
            }
            len -= max;
        }
        mOutputPtr = ptr;
        return -1;
    }

    protected void writeTextContent(String data)
        throws IOException
    {
        int offset = 0;
        int len = data.length();

        main_loop:
        while (len > 0) {
            int max = mOutputBuffer.length - mOutputPtr;
            if (max < 1) { // output buffer full?
                flushBuffer();
                max = mOutputBuffer.length;
            }
            // Do we start with a surrogate?
            if (mSurrogate != 0) {
                int sec = data.charAt(offset++);
                sec = calcSurrogate(sec);
                writeAsEntity(sec);
                --len;
                continue main_loop;
            }
            // How much can we output?
            if (max > len) {
                max = len;
            }
            inner_loop:
            for (int inEnd = offset + max; offset < inEnd; ) {
                int c = data.charAt(offset++);
                if (c < 32) {
                    if (c == '\n' || c == '\t') { // TODO: line count
                        mOutputBuffer[mOutputPtr++] = (byte) c;
                        continue;
                    } else if (c == '\r') {
                        if (!mEscapeCR) {
                            mOutputBuffer[mOutputPtr++] = (byte) c;
                            continue;
                        }
                    } else if (!mXml11 || c == 0) { // ok in xml1.1, as entity
                        if (mCheckContent) {
                            c = handleInvalidChar(c);
                            mOutputBuffer[mOutputPtr++] = (byte) c;
                            continue;
                        }
                        // otherwise... well, I guess we can just escape it
                    }
                    // \r, or xml1.1 + other whitespace, need to escape
                } else if (c < 0x7F) {
                    if (c != '<' && c != '&') {
                        if (c != '>' || (offset > 1 && data.charAt(offset-2) != ']')) {
                            mOutputBuffer[mOutputPtr++] = (byte) c;
                            continue;
                        }
                    }
                    // otherwise fall back on quoting
                } else if (c > 0x9F && c <= 0xFF) {
                    mOutputBuffer[mOutputPtr++] = (byte) c;
                    continue; // [WSTX-88]
                } else {
                    // Surrogate?
                    if (c >= SURR1_FIRST && c <= SURR2_LAST) {
                        mSurrogate = c;
                        // Last char needs special handling:
                        if (offset == inEnd) {
                            break inner_loop;
                        }
                        c = calcSurrogate(data.charAt(offset++));
                        // Let's fall down to entity output
                    }
                }
                /* Has to be escaped as char entity; as such, also need
                 * to re-calc max. continguous data we can output
                 */
                writeAsEntity(c);
                len = data.length() - offset;
                continue main_loop;
            }
            len -= max;
        }
    }

    protected void writeTextContent(char[] cbuf, int offset, int len)
        throws IOException
    {
        main_loop:
        while (len > 0) {
            int max = mOutputBuffer.length - mOutputPtr;
            if (max < 1) { // output buffer full?
                flushBuffer();
                max = mOutputBuffer.length;
            }
            // Do we start with a surrogate?
            if (mSurrogate != 0) {
                int sec = cbuf[offset++];
                sec = calcSurrogate(sec);
                writeAsEntity(sec);
                --len;
                continue main_loop;
            }
            // How much can we output?
            if (max > len) {
                max = len;
            }
            inner_loop:
            for (int inEnd = offset + max; offset < inEnd; ) {
                int c = cbuf[offset++];
                if (c < 32) {
                    if (c == '\n' || c == '\t') { // TODO: line count
                        mOutputBuffer[mOutputPtr++] = (byte) c;
                        continue;
                    } else if (c == '\r') {
                        if (!mEscapeCR) {
                            mOutputBuffer[mOutputPtr++] = (byte) c;
                            continue;
                        }
                    } else if (!mXml11 || c == 0) { // ok in xml1.1, as entity
                        if (mCheckContent) {
                            c = handleInvalidChar(c);
                            mOutputBuffer[mOutputPtr++] = (byte) c;
                            continue;
                        }
                        // otherwise... well, I guess we can just escape it
                    }
                    // \r, or xml1.1 + other whitespace, need to escape
                } else if (c < 0x7F) {
                    if (c !='<' && c != '&') {
                        /* Since we can be conservative, it doesn't matter
                         * if second check is not exact
                         */
                        if (c != '>' || (offset > 1 && cbuf[offset-2] != ']')) {
                            mOutputBuffer[mOutputPtr++] = (byte) c;
                            continue;
                        }
                    }
                    // otherwise fall back on quoting
                } else if (c > 0x9F && c <= 0xFF) {
                    mOutputBuffer[mOutputPtr++] = (byte) c;
                    continue; // [WSTX-88]
                } else {
                    // Surrogate?
                    if (c >= SURR1_FIRST && c <= SURR2_LAST) {
                        mSurrogate = c;
                        // Last char needs special handling:
                        if (offset == inEnd) {
                            break inner_loop;
                        }
                        c = calcSurrogate(cbuf[offset++]);
                        // Let's fall down to entity output
                    }
                }
                /* Has to be escaped as char entity; as such, also need
                 * to re-calc max. continguous data we can output
                 */
                writeAsEntity(c);
                max -= (inEnd - offset);
                break inner_loop;
            }
            len -= max;
        }
    }

    /*
    ////////////////////////////////////////////////////
    // Internal methods
    ////////////////////////////////////////////////////
     */

    protected void handleInvalidLatinChar(int c)
        throws IOException
    {
        // First, let's flush any output we may have, to make debugging easier
        flush();
        
        /* 17-May-2006, TSa: Would really be useful if we could throw
         *   XMLStreamExceptions; esp. to indicate actual output location.
         *   However, this causes problem with methods that call us and
         *   can only throw IOExceptions (when invoked via Writer proxy).
         *   Need to figure out how to resolve this.
         */
        throw new IOException("Invalid XML character (0x"+Integer.toHexString(c)+"); can only be output using character entity when using ISO-8859-1 encoding");
    }
}
