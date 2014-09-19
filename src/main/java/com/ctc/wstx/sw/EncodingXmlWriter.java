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

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.ri.typed.AsciiValueEncoder;
import org.codehaus.stax2.validation.XMLValidator;

import com.ctc.wstx.api.WriterConfig;
import com.ctc.wstx.cfg.XmlConsts;
//import com.ctc.wstx.io.CompletelyCloseable;

/**
 * Intermediate base class used when outputting to streams that use
 * an encoding that is compatible with 7-bit single-byte Ascii encoding.
 * That means it can be used for UTF-8, ISO-Latin1 and pure Ascii.
 *<p>
 * Implementation notes:
 *<p>
 * Parts of surrogate handling are implemented here in the base class:
 * storage for the first part of a split surrogate (only possible when
 * character content is output split in multiple calls) is within
 * base class. Also, simple checks for unmatched surrogate pairs are
 * in <code>writeAscii</code> method, since it is the most convenient
 * place to catch cases where a text segment ends with an unmatched
 * surrogate pair half.
 */
public abstract class EncodingXmlWriter
    extends XmlWriter
{
    /**
     * Let's use a typical default to have a compromise between large
     * enough chunks to output, and minimizing memory overhead.
     * 4k should be close enough to a physical page to work out
     * acceptably, without causing excessive (if temporary) memory usage.
     */
    final static int DEFAULT_BUFFER_SIZE = 4000;

    final static byte BYTE_SPACE = (byte) ' ';
    final static byte BYTE_COLON = (byte) ':';
    final static byte BYTE_SEMICOLON = (byte) ';';
    final static byte BYTE_LBRACKET = (byte) '[';
    final static byte BYTE_RBRACKET = (byte) ']';
    final static byte BYTE_QMARK = (byte) '?';
    final static byte BYTE_EQ = (byte) '=';
    final static byte BYTE_SLASH = (byte) '/';
    final static byte BYTE_HASH = (byte) '#';
    final static byte BYTE_HYPHEN = (byte) '-';

    final static byte BYTE_LT = (byte) '<';
    final static byte BYTE_GT = (byte) '>';
    final static byte BYTE_AMP = (byte) '&';
    final static byte BYTE_QUOT = (byte) '"';
    final static byte BYTE_APOS = (byte) '\'';

    final static byte BYTE_A = (byte) 'a';
    final static byte BYTE_G = (byte) 'g';
    final static byte BYTE_L = (byte) 'l';
    final static byte BYTE_M = (byte) 'm';
    final static byte BYTE_O = (byte) 'o';
    final static byte BYTE_P = (byte) 'p';
    final static byte BYTE_Q = (byte) 'q';
    final static byte BYTE_S = (byte) 's';
    final static byte BYTE_T = (byte) 't';
    final static byte BYTE_U = (byte) 'u';
    final static byte BYTE_X = (byte) 'x';

    /*
    ////////////////////////////////////////////////
    // Output state, buffering
    ////////////////////////////////////////////////
     */

    /**
     * Actual output stream to use for outputting encoded content as
     * bytes.
     */
    private final OutputStream mOut;

    protected byte[] mOutputBuffer;

    protected int mOutputPtr;

    /**
     * In case a split surrogate pair is output (which can only successfully
     * occur with either <code>writeRaw</code> or
     * <code>writeCharacters</code>), the first part is temporarily stored
     * within this member variable.
     */
    protected int mSurrogate = 0;

    /*
    ////////////////////////////////////////////////
    // 
    ////////////////////////////////////////////////
     */

    public EncodingXmlWriter(OutputStream out, WriterConfig cfg, String encoding,
                             boolean autoclose)
        throws IOException
    {
        super(cfg, encoding, autoclose);
        mOut = out;
        mOutputBuffer = cfg.allocFullBBuffer(DEFAULT_BUFFER_SIZE);
        mOutputPtr = 0;
    }

    /**
     * This method is needed by the super class, to calculate hard
     * byte/char offsets.
     */
    protected int getOutputPtr() {
        return mOutputPtr;
    }

    /*
    ////////////////////////////////////////////////
    // Partial API implementation
    ////////////////////////////////////////////////
     */

    final protected OutputStream getOutputStream()
    {
        return mOut;
    }

    final protected Writer getWriter()
    {
        // No writers are involved with these implementations...
        return null;
    }

    public void close(boolean forceRealClose)
        throws IOException
    {
        flush();

        // Buffers to free?
        byte[] buf = mOutputBuffer;
        if (buf != null) {
            mOutputBuffer = null;
            mConfig.freeFullBBuffer(buf);
        }
        // Plus may need to close the actual stream
        if (forceRealClose || mAutoCloseOutput) {
            /* 14-Nov-2008, TSa: Wrt [WSTX-163]; no need to
             *   check whether mOut implements CompletelyCloseable
             *   (unlike with BufferingXmlWriter)
             */
            mOut.close();
        }
    }

    public final void flush()
        throws IOException
    {
        flushBuffer();
        mOut.flush();
    }

    public abstract void writeRaw(char[] cbuf, int offset, int len)
        throws IOException;

    public abstract void writeRaw(String str, int offset, int len)
        throws IOException;

    /*
    //////////////////////////////////////////////////
    // "Trusted" low-level output methods (that do not
    // need to verify validity of input)
    //////////////////////////////////////////////////
     */

    public final void writeCDataStart()
        throws IOException
    { 
        writeAscii("<![CDATA[");
    }

    public final void writeCDataEnd()
        throws IOException
    {
        writeAscii("]]>");
    }

    public final void writeCommentStart()
        throws IOException
    {
        writeAscii("<!--");
    }

    public final void writeCommentEnd()
        throws IOException
    {
        writeAscii("-->");
    }

    public final void writePIStart(String target, boolean addSpace)
        throws IOException
    {
        writeAscii(BYTE_LT, BYTE_QMARK);
        writeRaw(target);
        if (addSpace) {
            writeAscii(BYTE_SPACE);
        }
    }

    public final void writePIEnd()
        throws IOException
    {
        writeAscii(BYTE_QMARK, BYTE_GT);
    }

    /*
    ////////////////////////////////////////////////
    // Higher-level output methods, text output
    ////////////////////////////////////////////////
     */

    public int writeCData(String data)
        throws IOException
    {
        writeAscii("<![CDATA[");
        int ix = writeCDataContent(data);
        if (ix >= 0) {
            return ix;
        }
        writeAscii("]]>");
        return -1;
    }

    public int writeCData(char[] cbuf, int offset, int len)
        throws IOException
    {
        writeAscii("<![CDATA[");
        int ix = writeCDataContent(cbuf, offset, len);
        if (ix >= 0) {
            return ix;
        }
        writeAscii("]]>");
        return -1;
    }    

    public final void writeCharacters(String data)
        throws IOException
    {
        // Note: may get second part of a surrogate
        if (mTextWriter != null) { // custom escaping?
            mTextWriter.write(data);
        } else { // nope, default:
            writeTextContent(data);
        }
    }

    public final void writeCharacters(char[] cbuf, int offset, int len)
        throws IOException
    {
        // Note: may get second part of a surrogate
        if (mTextWriter != null) { // custom escaping?
            mTextWriter.write(cbuf, offset, len);
        } else { // nope, default:
            writeTextContent(cbuf, offset, len);
        }
    }

    /**
     * Method that will try to output the content as specified. If
     * the content passed in has embedded "--" in it, it will either
     * add an intervening space between consequtive hyphens (if content
     * fixing is enabled), or return the offset of the first hyphen in
     * multi-hyphen sequence.
     */
    public int writeComment(String data)
        throws IOException
    {
        writeAscii("<!--");
        int ix = writeCommentContent(data);
        if (ix >= 0) { // unfixable '--'?
            return ix; 
        }
        writeAscii("-->");
        return -1;
    }    

    public void writeDTD(String data)
        throws IOException
    {
        if (mSurrogate != 0) {
            throwUnpairedSurrogate();
        }
        writeRaw(data, 0, data.length());
    }    

    public void writeDTD(String rootName, String systemId, String publicId,
                         String internalSubset)
        throws IOException, XMLStreamException
    {
        writeAscii("<!DOCTYPE ");
        writeAscii(rootName);
        if (systemId != null) {
            if (publicId != null) {
                writeAscii(" PUBLIC \"");
                writeRaw(publicId, 0, publicId.length());
                writeAscii("\" \"");
            } else {
                writeAscii(" SYSTEM \"");
            }
            writeRaw(systemId, 0, systemId.length());
            writeAscii(BYTE_QUOT);
        }

        // Hmmh. Should we output empty internal subset?
        if (internalSubset != null && internalSubset.length() > 0) {
            writeAscii(BYTE_SPACE, BYTE_LBRACKET);
            writeRaw(internalSubset, 0, internalSubset.length());
            writeAscii(BYTE_RBRACKET);
        }
        writeAscii(BYTE_GT);
    }

    public void writeEntityReference(String name)
        throws IOException, XMLStreamException
    {
        if (mSurrogate != 0) {
            throwUnpairedSurrogate();
        }
        writeAscii(BYTE_AMP);
        writeName(name);
        writeAscii(BYTE_SEMICOLON);
    }    

    public void writeXmlDeclaration(String version, String encoding, String standalone)
        throws IOException
    {
        final byte byQuote = (mUseDoubleQuotesInXmlDecl ? BYTE_QUOT : BYTE_APOS);

        writeAscii("<?xml version=");
        writeAscii(byQuote);
        writeAscii(version);
        writeAscii(byQuote);

        if (encoding != null && encoding.length() > 0) {
            writeAscii(" encoding=");
            writeAscii(byQuote);
            // Should be ascii, but let's play it safe:
            writeRaw(encoding, 0, encoding.length());
            writeAscii(byQuote);
        }
        if (standalone != null) {
            writeAscii(" standalone=");
            writeAscii(byQuote);
            writeAscii(standalone);
            writeAscii(byQuote);
        }
        writeAscii(BYTE_QMARK, BYTE_GT);
    }

    public int writePI(String target, String data)
        throws IOException, XMLStreamException
    {
        writeAscii(BYTE_LT, BYTE_QMARK);
        writeName(target);
        if (data != null && data.length() > 0) {
            writeAscii(BYTE_SPACE);
            int ix = writePIData(data);
            if (ix >= 0) { // embedded "?>"?
                return ix;
            }
        }
        writeAscii(BYTE_QMARK, BYTE_GT);
        return -1;
    }    

    /*
    ////////////////////////////////////////////////////
    // Write methods, elements
    ////////////////////////////////////////////////////
     */

    public void writeStartTagStart(String localName)
        throws IOException, XMLStreamException
    {
        writeAscii(BYTE_LT);
        writeName(localName);
    }    

    public void writeStartTagStart(String prefix, String localName)
        throws IOException, XMLStreamException
    {
        if (prefix == null || prefix.length() == 0) {
            writeStartTagStart(localName);
            return;
        }
        writeAscii(BYTE_LT);
        writeName(prefix);
        writeAscii(BYTE_COLON);
        writeName(localName);
    }    

    public void writeStartTagEnd()
        throws IOException
    {
        writeAscii(BYTE_GT);
    }    

    public void writeStartTagEmptyEnd()
        throws IOException
    {
        if (mAddSpaceAfterEmptyElem) {
            writeAscii(" />");
        } else {
            writeAscii(BYTE_SLASH, BYTE_GT);
        }
    }    

    public void writeEndTag(String localName)
        throws IOException
    {
        writeAscii(BYTE_LT, BYTE_SLASH);
        /* At this point, it is assumed caller knows that end tag
         * matches with start tag, and that it (by extension) has been
         * validated if and as necessary
         */
        writeNameUnchecked(localName);
        writeAscii(BYTE_GT);
    }    

    public void writeEndTag(String prefix, String localName)
        throws IOException
    {
        writeAscii(BYTE_LT, BYTE_SLASH);
        /* At this point, it is assumed caller knows that end tag
         * matches with start tag, and that it (by extension) has been
         * validated if and as necessary
         */
        if (prefix != null && prefix.length() > 0) {
            writeNameUnchecked(prefix);
            writeAscii(BYTE_COLON);
        }
        writeNameUnchecked(localName);
        writeAscii(BYTE_GT);
    }    

    /*
    ////////////////////////////////////////////////////
    // Write methods, attributes/ns
    ////////////////////////////////////////////////////
     */

    public void writeAttribute(String localName, String value)
        throws IOException, XMLStreamException
    {
        writeAscii(BYTE_SPACE);
        writeName(localName);
        writeAscii(BYTE_EQ, BYTE_QUOT);

        int len = value.length();
        if (len > 0) {
            if (mAttrValueWriter != null) { // custom escaping?
                mAttrValueWriter.write(value, 0, len);
            } else { // nope, default
                writeAttrValue(value);
            }
        }
        writeAscii(BYTE_QUOT);
    }    

    public void writeAttribute(String localName, char[] value, int offset, int len)
        throws IOException, XMLStreamException
    {
        writeAscii(BYTE_SPACE);
        writeName(localName);
        writeAscii(BYTE_EQ, BYTE_QUOT);

        if (len > 0) {
            if (mAttrValueWriter != null) { // custom escaping?
                mAttrValueWriter.write(value, offset, len);
            } else { // nope, default
                writeAttrValue(value, offset, len);
            }
        }
        writeAscii(BYTE_QUOT);
    }    

    public void writeAttribute(String prefix, String localName, String value)
        throws IOException, XMLStreamException
    {
        writeAscii(BYTE_SPACE);
        writeName(prefix);
        writeAscii(BYTE_COLON);
        writeName(localName);
        writeAscii(BYTE_EQ, BYTE_QUOT);

        int len = value.length();
        if (len > 0) {
            if (mAttrValueWriter != null) { // custom escaping?
                mAttrValueWriter.write(value, 0, len);
            } else { // nope, default
                writeAttrValue(value);
            }
        }
        writeAscii(BYTE_QUOT);
    }    

    public void writeAttribute(String prefix, String localName, char[] value, int offset, int len)
        throws IOException, XMLStreamException
    {
        writeAscii(BYTE_SPACE);
        writeName(prefix);
        writeAscii(BYTE_COLON);
        writeName(localName);
        writeAscii(BYTE_EQ, BYTE_QUOT);

        if (len > 0) {
            if (mAttrValueWriter != null) { // custom escaping?
                mAttrValueWriter.write(value, offset, len);
            } else { // nope, default
                writeAttrValue(value, offset, len);
            }
        }
        writeAscii(BYTE_QUOT);
    }

    /*
    ////////////////////////////////////////////////
    // Methods used by Typed Access API
    ////////////////////////////////////////////////
     */

    /**
     * Non-validating version of typed write method
     */
    public final void writeTypedElement(AsciiValueEncoder enc)
        throws IOException
    {
        if (mSurrogate != 0) {
            throwUnpairedSurrogate();
        }
        if (enc.bufferNeedsFlush(mOutputBuffer.length - mOutputPtr)) {
            flush();
        }
        while (true) {
            mOutputPtr = enc.encodeMore(mOutputBuffer, mOutputPtr, mOutputBuffer.length);
            // If no flushing needed, indicates that all data was encoded
            if (enc.isCompleted()) {
                break;
            }
            flush();
        }
    }

    /**
     * Validating version of typed write method
     */
    public final void writeTypedElement(AsciiValueEncoder enc,
                                        XMLValidator validator, char[] copyBuffer)
        throws IOException, XMLStreamException
    {
        if (mSurrogate != 0) {
            throwUnpairedSurrogate();
        }

        /* Ok, this gets trickier: can't use efficient direct-to-bytes
         * encoding since validator won't be able to use it. Instead
         * have to use temporary copy buffer.
         */
        final int copyBufferLen = copyBuffer.length;

        // Copy buffer should never be too small, no need to check up front
        do {
            int ptr = enc.encodeMore(copyBuffer, 0, copyBufferLen);

            // False -> can't be sure it's the whole remaining text
            validator.validateText(copyBuffer, 0, ptr, false);
            writeRawAscii(copyBuffer, 0, ptr);
        } while (!enc.isCompleted());
    }

    public void writeTypedAttribute(String localName, AsciiValueEncoder enc)
        throws IOException, XMLStreamException
    {
        writeAscii(BYTE_SPACE);
        writeName(localName);
        writeAscii(BYTE_EQ, BYTE_QUOT);

        if (enc.bufferNeedsFlush(mOutputBuffer.length - mOutputPtr)) {
            flush();
        }
        while (true) {
            mOutputPtr = enc.encodeMore(mOutputBuffer, mOutputPtr, mOutputBuffer.length);
            if (enc.isCompleted()) {
                break;
            }
            flush();
        }
        writeAscii(BYTE_QUOT);
    }

    public void writeTypedAttribute(String prefix, String localName,
                                    AsciiValueEncoder enc)
        throws IOException, XMLStreamException
    {
        writeAscii(BYTE_SPACE);
        writeName(prefix);
        writeAscii(BYTE_COLON);
        writeName(localName);
        writeAscii(BYTE_EQ, BYTE_QUOT);

        if (enc.bufferNeedsFlush(mOutputBuffer.length - mOutputPtr)) {
            flush();
        }
        while (true) {
            mOutputPtr = enc.encodeMore(mOutputBuffer, mOutputPtr, mOutputBuffer.length);
            if (enc.isCompleted()) {
                break;
            }
            flush();
        }
        writeAscii(BYTE_QUOT);
    }

    public void writeTypedAttribute(String prefix, String localName, String nsURI,
                                      AsciiValueEncoder enc,
                                      XMLValidator validator, char[] copyBuffer)
        throws IOException, XMLStreamException
    {
        boolean hasPrefix = (prefix != null && prefix.length() > 0);
        if (nsURI == null) {
            nsURI = "";
        }
        //validator.validateAttribute(localName, nsURI, (hasPrefix ? prefix: ""), buf, offset, len);

        writeAscii(BYTE_SPACE);
        if (hasPrefix) {
            writeName(prefix);
            writeAscii(BYTE_COLON);
        }
        writeName(localName);
        writeAscii(BYTE_EQ, BYTE_QUOT);

        /* Ok, this gets trickier: can't use efficient direct-to-bytes
         * encoding since validator won't be able to use it. Instead
         * have to use temporary copy buffer.
         * In addition, attributes to validate can not be
         * split (validators expect complete values). So, if value
         * won't fit as is, may need to aggregate using StringBuilder
         */
        final int copyBufferLen = copyBuffer.length;

        // First, let's see if one call is enough
        int last = enc.encodeMore(copyBuffer, 0, copyBufferLen);
        writeRawAscii(copyBuffer, 0, last);
        if (enc.isCompleted()) {
            validator.validateAttribute(localName, nsURI, prefix, copyBuffer, 0, last);
            return;
        }

        // If not, must combine first
        StringBuilder sb = new StringBuilder(copyBufferLen << 1);
        sb.append(copyBuffer, 0, last);
        do {
            last = enc.encodeMore(copyBuffer, 0, copyBufferLen);
            writeRawAscii(copyBuffer, 0, last);
            sb.append(copyBuffer, 0, last);
        } while (!enc.isCompleted());

        writeAscii(BYTE_QUOT);

        // Then validate
        String valueStr = sb.toString();
        validator.validateAttribute(localName, nsURI, prefix, valueStr);

        return;
    }

    /*
    ////////////////////////////////////////////////
    // Methods for sub-classes to use
    ////////////////////////////////////////////////
     */

    protected final void flushBuffer()
        throws IOException
    {
        if (mOutputPtr > 0 && mOutputBuffer != null) {
            int ptr = mOutputPtr;
            mOutputPtr = 0;
            mOut.write(mOutputBuffer, 0, ptr);
        }
    }

    protected final void writeAscii(byte b)
        throws IOException
    {
        if (mSurrogate != 0) {
            throwUnpairedSurrogate();
        }
        if (mOutputPtr >= mOutputBuffer.length) {
            flushBuffer();
        }
        mOutputBuffer[mOutputPtr++] = b;
    }

    protected final void writeAscii(byte b1, byte b2)
        throws IOException
    {
        if (mSurrogate != 0) {
            throwUnpairedSurrogate();
        }
        if ((mOutputPtr + 1) >= mOutputBuffer.length) {
            flushBuffer();
        }
        mOutputBuffer[mOutputPtr++] = b1;
        mOutputBuffer[mOutputPtr++] = b2;
    }

    protected final void writeAscii(String str)
        throws IOException
    {
        if (mSurrogate != 0) {
            throwUnpairedSurrogate();
        }

        int len = str.length();
        int ptr = mOutputPtr;
        byte[] buf = mOutputBuffer;
        if ((ptr + len) >= buf.length) {
            /* It's even possible that String is longer than the buffer (not
             * likely, possible). If so, let's just call the full
             * method:
             */
            if (len > buf.length) {
                writeRaw(str, 0, len);
                return;
            }
            flushBuffer();
            ptr = mOutputPtr;
        }
        mOutputPtr += len;
        for (int i = 0; i < len; ++i) {
            buf[ptr++] = (byte)str.charAt(i);
        }
    }

    public final void writeRawAscii(char[] buf, int offset, int len)
        throws IOException
    {
        if (mSurrogate != 0) {
            throwUnpairedSurrogate();
        }
        int ptr = mOutputPtr;
        byte[] dst = mOutputBuffer;
        if ((ptr + len) >= dst.length) {
            if (len > dst.length) {
                writeRaw(buf, offset, len);
                return;
            }
            flushBuffer();
            ptr = mOutputPtr;
        }
        mOutputPtr += len;
        for (int i = 0; i < len; ++i) {
            dst[ptr+i] = (byte)buf[offset+i];
        }
    }

    /**
     * Entity writing can be optimized quite nicely, since it only
     * needs to output ascii characters.
     *
     * @return New value of <code>mOutputPtr</code>
     */
    protected final int writeAsEntity(int c)
        throws IOException
    {
        byte[] buf = mOutputBuffer;
        int ptr = mOutputPtr;
        if ((ptr + 10) >= buf.length) { // &#x [up to 6 hex digits] ;
            flushBuffer();
            ptr = mOutputPtr;
        }
        buf[ptr++] = BYTE_AMP;

        // Can use more optimal notation for 8-bit ascii stuff:
        if (c < 256) {
            /* Also; although not really mandatory, let's also
             * use pre-defined entities where possible.
             */
            if (c == '&') {
                buf[ptr++] = BYTE_A;
                buf[ptr++] = BYTE_M;
                buf[ptr++] = BYTE_P;
            } else if (c == '<') {
                buf[ptr++] = BYTE_L;
                buf[ptr++] = BYTE_T;
            } else if (c == '>') {
                buf[ptr++] = BYTE_G;
                buf[ptr++] = BYTE_T;
            } else if (c == '\'') {
                buf[ptr++] = BYTE_A;
                buf[ptr++] = BYTE_P;
                buf[ptr++] = BYTE_O;
                buf[ptr++] = BYTE_S;
            } else if (c == '"') {
                buf[ptr++] = BYTE_Q;
                buf[ptr++] = BYTE_U;
                buf[ptr++] = BYTE_O;
                buf[ptr++] = BYTE_T;
            } else {
                buf[ptr++] = BYTE_HASH;
                buf[ptr++] = BYTE_X;
                // Can use shortest quoting for tab, cr, lf:
                if (c >= 16) {
                    int digit = (c >> 4);
                    buf[ptr++] = (byte) ((digit < 10) ? ('0' + digit) : (('a' - 10) + digit));
                    c &= 0xF;
                }
                buf[ptr++] = (byte) ((c < 10) ? ('0' + c) : (('a' - 10) + c));
            }
        } else {
            buf[ptr++] = BYTE_HASH;
            buf[ptr++] = BYTE_X;

            // Ok, let's write the shortest possible sequence then:
            int shift = 20;
            int origPtr = ptr;

            do {
                int digit = (c >> shift) & 0xF;
                if (digit > 0 || (ptr != origPtr)) {
                    buf[ptr++] = (byte) ((digit < 10) ? ('0' + digit) : (('a' - 10) + digit));
                }
                shift -= 4;
            } while (shift > 0);
            c &= 0xF;
            buf[ptr++] = (byte) ((c < 10) ? ('0' + c) : (('a' - 10) + c));
        }
        buf[ptr++] = BYTE_SEMICOLON;
        mOutputPtr = ptr;
        return ptr;
    }

    protected final void writeName(String name)
        throws IOException, XMLStreamException
    {
        if (mCheckNames) {
            verifyNameValidity(name, mNsAware);
        }
        // TODO: maybe we could reuse some previously encoded names?
        writeRaw(name, 0, name.length());
    }

    protected final void writeNameUnchecked(String name)
        throws IOException
    {
        writeRaw(name, 0, name.length());
    }

    protected final int calcSurrogate(int secondSurr)
        throws IOException
    {
        // First, let's verify first surrogate is valid:
        int firstSurr = mSurrogate;
        mSurrogate = 0;
        if (firstSurr < SURR1_FIRST || firstSurr > SURR1_LAST) {
            throwUnpairedSurrogate(firstSurr);
        }
        
        // Then that the second one is:
        if ((secondSurr < SURR2_FIRST) || (secondSurr > SURR2_LAST)) {
            throwUnpairedSurrogate(secondSurr);
        }
        int ch = 0x10000 + ((firstSurr - SURR1_FIRST) << 10) + (secondSurr - SURR2_FIRST);
        if (ch > XmlConsts.MAX_UNICODE_CHAR) {
            throw new IOException("Illegal surrogate character pair, resulting code 0x"+Integer.toHexString(ch)+" above legal XML character range");
        }
        return ch;
    }

    protected final void throwUnpairedSurrogate()
        throws IOException
    {
        int surr = mSurrogate;
        mSurrogate = 0;
        throwUnpairedSurrogate(surr);
    }

    protected final void throwUnpairedSurrogate(int code)
        throws IOException
{
        // Let's flush to make debugging easier
        flush();
        throw new IOException("Unpaired surrogate character (0x"+Integer.toHexString(code)+")");
}

    /*
    ////////////////////////////////////////////////
    // Abstract methods for sub-classes to define
    ////////////////////////////////////////////////
     */

    protected abstract void writeAttrValue(String data)
        throws IOException;

    protected abstract void writeAttrValue(char[] value, int offset, int len)
        throws IOException;

    protected abstract int writeCDataContent(String data)
        throws IOException;

    protected abstract int writeCDataContent(char[] cbuf, int start, int len)
        throws IOException;

    protected abstract int writeCommentContent(String data)
        throws IOException;

    protected abstract int writePIData(String data)
        throws IOException, XMLStreamException;

    protected abstract void writeTextContent(String data)
        throws IOException;

    protected abstract void writeTextContent(char[] cbuf, int start, int len)
        throws IOException;
}
