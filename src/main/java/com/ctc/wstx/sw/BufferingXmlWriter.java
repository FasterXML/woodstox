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
import java.util.Arrays;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.ri.typed.AsciiValueEncoder;
import org.codehaus.stax2.validation.XMLValidator;

import com.ctc.wstx.api.WriterConfig;
import com.ctc.wstx.io.CharsetNames;
import com.ctc.wstx.io.CompletelyCloseable;

/**
 * Concrete implementation of {@link XmlWriter} that will dispatch writes
 * to another writer (of type {@link java.io.Writer}, and will NOT handle
 * encoding. It will, however, do basic buffering such that the underlying
 * Writer need (and thus, should) not do buffering.
 *<p>
 * One design goal for this class is to avoid unnecessary buffering: since
 * there will be another Writer doing the actual encoding, amount of
 * buffering needed should still be limited. To this end, a threshold is
 * used to define what's the threshold of writes that we do want to
 * coalesce, ie. buffer. Writes bigger than this should in general proceed
 * without buffering.
 */
public final class BufferingXmlWriter
    extends XmlWriter
    implements XMLStreamConstants
{
    /**
     * Let's use a typical default to have a compromise between large
     * enough chunks to output, and minimizing memory overhead.
     * Compared to encoding writers, buffer size can be bit smaller
     * since there's one more level of processing (at encoding), which
     * may use bigger buffering.
     */
    final static int DEFAULT_BUFFER_SIZE = 1000;

    /**
     * Choosing threshold for 'small size' is a compromise between
     * excessive buffering (high small size), and too many fragmented
     * calls to the underlying writer (low small size). Let's just
     * use about 1/4 of the full buffer size.
     */
    final static int DEFAULT_SMALL_SIZE = 256;

    /**
     * Highest valued character that may need to be encoded (minus charset
     * encoding requirements) when writing attribute values.
     */
    protected final static int HIGHEST_ENCODABLE_ATTR_CHAR = '<';

    /**
     * Highest valued character that may need to be encoded (minus charset
     * encoding requirements) when writing attribute values.
     */
    protected final static int HIGHEST_ENCODABLE_TEXT_CHAR = '>';

    protected final static int[] QUOTABLE_TEXT_CHARS;
    static {
        int[] q = new int[4096];
        Arrays.fill(q, 0, 32, 1);
        Arrays.fill(q, 127, 160, 1);
        q['\t'] = 0;
        q['\n'] = 0;
        q['<'] = 1;
        q['>'] = 1;
        q['&'] = 1;
        QUOTABLE_TEXT_CHARS = q;
    }
    
    /*
    ////////////////////////////////////////////////
    // Output state, buffering
    ////////////////////////////////////////////////
     */

    /**
     * Actual Writer to use for outputting buffered data as appropriate.
     */
    protected final Writer mOut;

    protected char[] mOutputBuffer;

    /**
     * This is the threshold used to check what is considered a "small"
     * write; small writes will be buffered until resulting size will
     * be above the threshold.
     */
    protected final int mSmallWriteSize;

    protected int mOutputPtr;

    protected int mOutputBufLen;

    /**
     * Actual physical stream that the writer is using, if known.
     * Not used for actual output, only needed so that calling
     * application may (try to) figure out the original
     * source.
     */
    protected final OutputStream mUnderlyingStream;

    /*
    ////////////////////////////////////////////////
    // Encoding/escaping configuration
    ////////////////////////////////////////////////
     */

    /**
     * First Unicode character (one with lowest value) after (and including)
     * which character entities have to be used. For
     */
    private final int mEncHighChar;

    /**
     * Character that is considered to be the enclosing quote character;
     * for XML either single or double quote.
     */
    final char mEncQuoteChar;

    /**
     * Entity String to use for escaping the quote character.
     */
    final String mEncQuoteEntity;

    /*
    ////////////////////////////////////////////////
    // Life-cycle
    ////////////////////////////////////////////////
     */

    /**
     * @param outs Underlying OutputStream that the writer
     *    (<code>out</code>) is using, if known. Needed to support
     *    (optional) access to the underlying stream
     */
    public BufferingXmlWriter(Writer out, WriterConfig cfg, String enc,
                              boolean autoclose,
                              OutputStream outs, int bitsize)
        throws IOException
    {
        super(cfg, enc, autoclose);
        mOut = out;
        mOutputBuffer = cfg.allocFullCBuffer(DEFAULT_BUFFER_SIZE);
        mOutputBufLen = mOutputBuffer.length;
        mSmallWriteSize = DEFAULT_SMALL_SIZE;
        mOutputPtr = 0;

        mUnderlyingStream = outs;

        // Let's use double-quotes, as usual; alternative is apostrophe
        mEncQuoteChar = '"';
        mEncQuoteEntity = "&quot;";
        /* Note: let's actually exclude couple of illegal chars for
         * unicode-based encoders. But we do not have to worry about
         * surrogates quite here, fortunately.
         */
        if (bitsize < 1) {
            bitsize = guessEncodingBitSize(enc);
        }
        mEncHighChar = ((bitsize < 16) ? (1 << bitsize) : 0xFFFE);
    }

    @Override
    protected int getOutputPtr() {
        return mOutputPtr;
    }

    /*
    ////////////////////////////////////////////////
    // Raw access to underlying output objects
    ////////////////////////////////////////////////
     */

    @Override
    final protected OutputStream getOutputStream() {
        return mUnderlyingStream;
    }

    @Override
    final protected Writer getWriter() {
        return mOut;
    }

    /*
    ////////////////////////////////////////////////
    // Low-level (pass-through) methods
    ////////////////////////////////////////////////
     */

    @Override
    public void close(boolean forceRealClose) throws IOException
    {
        flush();
        mTextWriter = null;
        mAttrValueWriter = null;

        // Buffers to free?
        char[] buf = mOutputBuffer;
        if (buf != null) {
            mOutputBuffer = null;
            mConfig.freeFullCBuffer(buf);
        }
        // Plus may need to close the actual writer
        if (forceRealClose || mAutoCloseOutput) {
            /* 14-Nov-2008, TSa: To resolve [WSTX-163], need to have a way
             *   to force UTF8Writer to close the underlying stream...
             */
            if (mOut instanceof CompletelyCloseable) {
                ((CompletelyCloseable)mOut).closeCompletely();
            } else {
                mOut.close();
            }
        }
    }

    @Override
    public final void flush() throws IOException
    {
        flushBuffer();
        mOut.flush();
    }

    @Override
    public void writeRaw(char[] cbuf, int offset, int len) throws IOException
    {
        if (mOut == null) {
            return;
        }

        // First; is the new request small or not? If yes, needs to be buffered
        if (len < mSmallWriteSize) { // yup
            // Does it fit in with current buffer? If not, need to flush first
            if ((mOutputPtr + len) > mOutputBufLen) {
                flushBuffer();
            }
            System.arraycopy(cbuf, offset, mOutputBuffer, mOutputPtr, len);
            mOutputPtr += len;
            return;
        }

        // Ok, not a small request. But buffer may have existing content?
        int ptr = mOutputPtr;
        if (ptr > 0) {
            // If it's a small chunk, need to fill enough before flushing
            if (ptr < mSmallWriteSize) {
                /* Also, if we are to copy any stuff, let's make sure
                 * that we either copy it all in one chunk, or copy
                 * enough for non-small chunk, flush, and output remaining
                 * non-small chink (former possible if chunk we were requested
                 * to output is only slightly over 'small' size)
                 */
                int needed = (mSmallWriteSize - ptr);

                // Just need minimal copy:
                System.arraycopy(cbuf, offset, mOutputBuffer, ptr, needed);
                mOutputPtr = ptr + needed;
                len -= needed;
                offset += needed;
            }
            flushBuffer();
        }

        // And then we'll just write whatever we have left:
        mOut.write(cbuf, offset, len);
    }

    /**
     * Method called to output typed values (int, long, double, float etc)
     * that are known not to contain any escapable characters, or anything
     * else beyond 7-bit ascii range.
     */
    @Override
    public final void writeRawAscii(char[] cbuf, int offset, int len)
        throws IOException
    {
        // Can't optimize any further with buffering writer, so:
        writeRaw(cbuf, offset, len);
    }

    @Override
    public void writeRaw(String str) throws IOException
    {
        if (mOut == null) {
            return;
        }
        final int len = str.length();

        // First; is the new request small or not? If yes, needs to be buffered
        if (len < mSmallWriteSize) { // yup
            // Does it fit in with current buffer? If not, need to flush first
            if ((mOutputPtr + len) >= mOutputBufLen) {
                flushBuffer();
            }
            str.getChars(0, len, mOutputBuffer, mOutputPtr);
            mOutputPtr += len;
            return;
        }
        // Otherwise, let's just call the main method
        writeRaw(str, 0, len);
    }

    @Override
    public void writeRaw(String str, int offset, int len) throws IOException
    {
        if (mOut == null) {
            return;
        }

        // First; is the new request small or not? If yes, needs to be buffered
        if (len < mSmallWriteSize) { // yup
            // Does it fit in with current buffer? If not, need to flush first
            if ((mOutputPtr + len) >= mOutputBufLen) {
                flushBuffer();
            }
            str.getChars(offset, offset+len, mOutputBuffer, mOutputPtr);
            mOutputPtr += len;
            return;
        }

        // Ok, not a small request. But buffer may have existing content?
        int ptr = mOutputPtr;
        if (ptr > 0) {
            // If it's a small chunk, need to fill enough before flushing
            if (ptr < mSmallWriteSize) {
                /* Also, if we are to copy any stuff, let's make sure
                 * that we either copy it all in one chunk, or copy
                 * enough for non-small chunk, flush, and output remaining
                 * non-small chunk (former possible if chunk we were requested
                 * to output is only slightly over 'small' size)
                 */
                int needed = (mSmallWriteSize - ptr);

                // Just need minimal copy:
                str.getChars(offset, offset+needed, mOutputBuffer, ptr);
                mOutputPtr = ptr + needed;
                len -= needed;
                offset += needed;
            }
            flushBuffer();
        }

        // And then we'll just write whatever we have left:
        mOut.write(str, offset, len);
    }

    /*
    ////////////////////////////////////////////////
    // "Trusted" low-level output methods
    ////////////////////////////////////////////////
     */

    @Override
    public final void writeCDataStart() throws IOException {
        fastWriteRaw("<![CDATA[");
    }

    @Override
    public final void writeCDataEnd() throws IOException {
        fastWriteRaw("]]>");
    }

    @Override
    public final void writeCommentStart() throws IOException {
        fastWriteRaw("<!--");
    }

    @Override
    public final void writeCommentEnd() throws IOException {
        fastWriteRaw("-->");
    }

    @Override
    public final void writePIStart(String target, boolean addSpace) throws IOException
    {
        fastWriteRaw('<', '?');
        fastWriteRaw(target);
        if (addSpace) {
            fastWriteRaw(' ');
        }
    }

    @Override
    public final void writePIEnd() throws IOException {
        fastWriteRaw('?', '>');
    }

    /*
    ////////////////////////////////////////////////
    // Higher-level output methods, text output
    ////////////////////////////////////////////////
     */

    @Override
    public int writeCData(String data) throws IOException
    {
        if (mCheckContent) {
            int ix = verifyCDataContent(data);
            if (ix >= 0) {
                if (!mFixContent) { // Can we fix it?
                    return ix;
                }
                // Yes we can! (...Bob the Builder...)
                writeSegmentedCData(data, ix);
                return -1;
            }
        }
        fastWriteRaw("<![CDATA[");
        writeRaw(data, 0, data.length());
        fastWriteRaw("]]>");
        return -1;
    }

    @Override
    public int writeCData(char[] cbuf, int offset, int len) throws IOException
    {
        if (mCheckContent) {
            int ix = verifyCDataContent(cbuf, offset, len);
            if (ix >= 0) {
                if (!mFixContent) { // Can we fix it?
                    return ix;
                }
                // Yes we can! (...Bob the Builder...)
                writeSegmentedCData(cbuf, offset, len, ix);
                return -1;
            }
        }
        fastWriteRaw("<![CDATA[");
        writeRaw(cbuf, offset, len);
        fastWriteRaw("]]>");
        return -1;
    }

    @Override
    public void writeCharacters(String text) throws IOException
    {
        if (mOut == null) {
            return;
        }
        if (mTextWriter != null) { // custom escaping?
            mTextWriter.write(text);
            return;
        }
        int inPtr = 0;
        final int len = text.length();

        // nope, default:
        final int[] QC = QUOTABLE_TEXT_CHARS;
        final int highChar = mEncHighChar;
        final int MAXQC = Math.min(QC.length, highChar);
        
        main_loop:
        while (true) {
            String ent = null;

            inner_loop:
            while (true) {
                if (inPtr >= len) {
                    break main_loop;
                }
                char c = text.charAt(inPtr++);

                if (c < MAXQC) {
                    if (QC[c] != 0) {
                        if (c < 0x0020) {
                            if (c != ' ' && c != '\n' && c != '\t') { // fine as is
                                if (c == '\r') {
                                    if (mEscapeCR) {
                                        break inner_loop;
                                    }
                                } else {
                                    if (!mXml11 || c == 0) {
                                        c = handleInvalidChar(c); // throws an error usually
                                        ent = String.valueOf((char) c);
                                    } else {
                                        break inner_loop; // need quoting
                                    }
                                }
                            }
                        } else if (c == '<') {
                            ent = "&lt;";
                            break inner_loop;
                        } else if (c == '&') {
                            ent = "&amp;";
                            break inner_loop;
                        } else if (c == '>') {
                            // Let's be conservative; and if there's any
                            // change it might be part of "]]>" quote it
                            if (inPtr < 2 || text.charAt(inPtr-2) == ']') {
                                ent = "&gt;";
                                break inner_loop;
                            }
                        } else if (c >= 0x7F) {
                            break;
                        }
                    }
                } else if (c >= highChar) {
                    break inner_loop;
                }
                if (mOutputPtr >= mOutputBufLen) {
                    flushBuffer();
                }
                mOutputBuffer[mOutputPtr++] = c;
            }
            if (ent != null) {
                writeRaw(ent);
            } else {
                writeAsEntity(text.charAt(inPtr-1));
            }
        }
    }
    
    @Override
    public void writeCharacters(char[] cbuf, int offset, int len) throws IOException
    {
        if (mOut == null) {
            return;
        }
        if (mTextWriter != null) { // custom escaping?
            mTextWriter.write(cbuf, offset, len);
            return;
        }
        // nope, default:
        final int[] QC = QUOTABLE_TEXT_CHARS;
        final int highChar = mEncHighChar;
        final int MAXQC = Math.min(QC.length, highChar);
        len += offset;
        do {
            int c = 0;
            int start = offset;
            String ent = null;
            
            for (; offset < len; ++offset) {
                c = cbuf[offset];
                
                if (c < MAXQC) {
                    if (QC[c] != 0) {
                        // Ok, possibly needs quoting... further checks needed
                        if (c == '<') {
                            ent = "&lt;";
                            break;
                        } else if (c == '&') {
                            ent = "&amp;";
                            break;
                        } else if (c == '>') {
                            /* Let's be conservative; and if there's any
                             * change it might be part of "]]>" quote it
                             */
                            if ((offset == start) || cbuf[offset-1] == ']') {
                                ent = "&gt;";
                                break;
                            }
                        } else if (c < 0x0020) {
                            if (c == '\n' || c == '\t') { // fine as is
                                ;
                            } else if (c == '\r') {
                                if (mEscapeCR) {
                                    break;
                                }
                            } else {
                                if (!mXml11 || c == 0) {
                                    c = handleInvalidChar(c);
                                    // Hmmh. This is very inefficient, but...
                                    ent = String.valueOf((char) c);
                                }
                                break; // need quoting
                            }
                        } else if (c >= 0x7F) {
                            break;
                        }
                    }
                } else if (c >= highChar) {
                    break;
                }
                // otherwise fine
            }
            int outLen = offset - start;
            if (outLen > 0) {
                writeRaw(cbuf, start, outLen);
            } 
            if (ent != null) {
                writeRaw(ent);
                ent = null;
            } else if (offset < len) {
                writeAsEntity(c);
            }
        } while (++offset < len);
    }    

    /**
     * Method that will try to output the content as specified. If
     * the content passed in has embedded "--" in it, it will either
     * add an intervening space between consequtive hyphens (if content
     * fixing is enabled), or return the offset of the first hyphen in
     * multi-hyphen sequence.
     */
    @Override
    public int writeComment(String data) throws IOException
    {
        if (mCheckContent) {
            int ix = verifyCommentContent(data);
            if (ix >= 0) {
                if (!mFixContent) { // Can we fix it?
                    return ix;
                }
                // Yes we can! (...Bob the Builder...)
                writeSegmentedComment(data, ix);
                return -1;
            }
        }
        fastWriteRaw("<!--");
        writeRaw(data);
        fastWriteRaw("-->");
        return -1;
    }

    @Override
    public void writeDTD(String data) throws IOException
    {
        writeRaw(data);
    }

    @Override
    public void writeDTD(String rootName, String systemId, String publicId,
                         String internalSubset)
        throws IOException, XMLStreamException
    {
        fastWriteRaw("<!DOCTYPE ");
        if (mCheckNames) {
            /* 20-Apr-2005, TSa: Can only really verify that it has at most
             *    one colon in ns-aware mode (and not even that in non-ns
             *    mode)... so let's just ignore colon count, and check
             *    that other chars are valid at least
             */
            verifyNameValidity(rootName, false);
        }
        fastWriteRaw(rootName);
        if (systemId != null) {
            if (publicId != null) {
                fastWriteRaw(" PUBLIC \"");
                fastWriteRaw(publicId);
                fastWriteRaw("\" \"");
            } else {
                fastWriteRaw(" SYSTEM \"");
            }
            fastWriteRaw(systemId);
            fastWriteRaw('"');
        }
        // Hmmh. Should we output empty internal subset?
        if (internalSubset != null && internalSubset.length() > 0) {
            fastWriteRaw(' ', '[');
            fastWriteRaw(internalSubset);
            fastWriteRaw(']');
        }
        fastWriteRaw('>');
    }

    @Override
    public void writeEntityReference(String name)
        throws IOException, XMLStreamException
    {
        if (mCheckNames) {
            verifyNameValidity(name, mNsAware);
        }
        fastWriteRaw('&');
        fastWriteRaw(name);
        fastWriteRaw(';');
    }    

    @Override
    public void writeXmlDeclaration(String version, String encoding, String standalone)
        throws IOException
    {
        final char chQuote = (mUseDoubleQuotesInXmlDecl ? '"' : '\'');

        fastWriteRaw("<?xml version=");
        fastWriteRaw(chQuote);
        fastWriteRaw(version);
        fastWriteRaw(chQuote);

        if (encoding != null && encoding.length() > 0) {
            fastWriteRaw(" encoding=");
            fastWriteRaw(chQuote);
            fastWriteRaw(encoding);
            fastWriteRaw(chQuote);
        }
        if (standalone != null) {
            fastWriteRaw(" standalone=");
            fastWriteRaw(chQuote);
            fastWriteRaw(standalone);
            fastWriteRaw(chQuote);
        }
        fastWriteRaw('?', '>');
    }

    @Override
    public int writePI(String target, String data)
        throws IOException, XMLStreamException
    {
        if (mCheckNames) {
            // As per namespace specs, can not have colon(s)
            verifyNameValidity(target, mNsAware);
        }
        fastWriteRaw('<', '?');
        fastWriteRaw(target);
        if (data != null && data.length() > 0) {
            if (mCheckContent) {
                int ix = data.indexOf('?');
                if (ix >= 0) {
                    ix = data.indexOf("?>", ix);
                    if (ix >= 0) {
                        return ix;
                    }
                }
            }
            fastWriteRaw(' ');
            // Data may be longer, let's call regular writeRaw method
            writeRaw(data);
        }
        fastWriteRaw('?', '>');
        return -1;
    }    

    /*
    ////////////////////////////////////////////////////
    // Write methods, elements
    ////////////////////////////////////////////////////
     */

    @Override
    public void writeStartTagStart(String localName)
        throws IOException, XMLStreamException
    {
        if (mCheckNames) {
            verifyNameValidity(localName, mNsAware);
        }

        int ptr = mOutputPtr;
        int extra = (mOutputBufLen - ptr) - (1 + localName.length());
        if (extra < 0) { // split on boundary, slower
            fastWriteRaw('<');
            fastWriteRaw(localName);
        } else {
            char[] buf = mOutputBuffer;
            buf[ptr++] = '<';
            int len = localName.length();
            localName.getChars(0, len, buf, ptr);
            mOutputPtr = ptr+len;
        }
    }    

    @Override
    public void writeStartTagStart(String prefix, String localName)
        throws IOException, XMLStreamException
    {
        if (prefix == null || prefix.length() == 0) { // shouldn't happen
            writeStartTagStart(localName);
            return;
        }

        if (mCheckNames) {
            verifyNameValidity(prefix, mNsAware);
            verifyNameValidity(localName, mNsAware);
        }

        int ptr = mOutputPtr;
        int len = prefix.length();
        int extra = (mOutputBufLen - ptr) - (2 + localName.length() + len);
        if (extra < 0) { // across buffer boundary, slow case
            fastWriteRaw('<');
            fastWriteRaw(prefix);
            fastWriteRaw(':');
            fastWriteRaw(localName);
        } else { // fast case, all inlined
            char[] buf = mOutputBuffer;
            buf[ptr++] = '<';
            prefix.getChars(0, len, buf, ptr);
            ptr += len;
            buf[ptr++] = ':';
            len = localName.length();
            localName.getChars(0, len, buf, ptr);
            mOutputPtr = ptr+len;
        }
    }    

    @Override
    public void writeStartTagEnd() throws IOException {
        fastWriteRaw('>');
    }    

    @Override
    public void writeStartTagEmptyEnd() throws IOException
    {
        int ptr = mOutputPtr;
        if ((ptr + 3) >= mOutputBufLen) {
            if (mOut == null) {
                return;
            }
            flushBuffer();
            ptr = mOutputPtr;
        }
        char[] buf = mOutputBuffer;
        if (mAddSpaceAfterEmptyElem) {
            buf[ptr++] = ' ';
        }
        buf[ptr++] = '/';
        buf[ptr++] = '>';
        mOutputPtr = ptr;
    }    

    @Override
    public void writeEndTag(String localName) throws IOException
    {
        int ptr = mOutputPtr;
        int extra = (mOutputBufLen - ptr) - (3 + localName.length());
        if (extra < 0) {
            fastWriteRaw('<', '/');
            fastWriteRaw(localName);
            fastWriteRaw('>');
        } else {
            char[] buf = mOutputBuffer;
            buf[ptr++] = '<';
            buf[ptr++] = '/';
            int len = localName.length();
            localName.getChars(0, len, buf, ptr);
            ptr += len;
            buf[ptr++] = '>';
            mOutputPtr = ptr;
        }
    }    

    @Override
    public void writeEndTag(String prefix, String localName) throws IOException
    {
        if (prefix == null || prefix.length() == 0) {
            writeEndTag(localName);
            return;
        }
        int ptr = mOutputPtr;
        int len = prefix.length();
        int extra = (mOutputBufLen - ptr) - (4 + localName.length() + len);
        if (extra < 0) {
            fastWriteRaw('<', '/');
            /* At this point, it is assumed caller knows that end tag
             * matches with start tag, and that it (by extension) has been
             * validated if and as necessary
             */
            fastWriteRaw(prefix);
            fastWriteRaw(':');
            fastWriteRaw(localName);
            fastWriteRaw('>');
        } else {
            char[] buf = mOutputBuffer;
            buf[ptr++] = '<';
            buf[ptr++] = '/';
            prefix.getChars(0, len, buf, ptr);
            ptr += len;
            buf[ptr++] = ':';
            len = localName.length();
            localName.getChars(0, len, buf, ptr);
            ptr += len;
            buf[ptr++] = '>';
            mOutputPtr = ptr;
        }
    }    

    /*
    ////////////////////////////////////////////////////
    // Write methods, attributes/ns
    ////////////////////////////////////////////////////
     */

    @Override
    public void writeAttribute(String localName, String value)
        throws IOException, XMLStreamException
    {
        if (mOut == null) {
            return;
        }
        if (mCheckNames) {
            verifyNameValidity(localName, mNsAware);
        }
        int len = localName.length();
        if (((mOutputBufLen - mOutputPtr) - (3 + len)) < 0) {
            fastWriteRaw(' ');
            fastWriteRaw(localName);
            fastWriteRaw('=', '"');
        } else {
            int ptr = mOutputPtr;
            char[] buf = mOutputBuffer;
            buf[ptr++] = ' ';
            localName.getChars(0, len, buf, ptr);
            ptr += len;
            buf[ptr++] = '=';
            buf[ptr++] = '"';
            mOutputPtr = ptr;
        }

        len = (value == null) ? 0 : value.length();
        if (len > 0) {
            if (mAttrValueWriter != null) { // custom escaping?
                mAttrValueWriter.write(value, 0, len);
            } else { // nope, default
                writeAttrValue(value, len);
            }
        }
        fastWriteRaw('"');
    }

    @Override
    public void writeAttribute(String localName, char[] value, int offset, int vlen)
        throws IOException, XMLStreamException
    {
        if (mOut == null) {
            return;
        }
        if (mCheckNames) {
            verifyNameValidity(localName, mNsAware);
        }
        int len = localName.length();
        if (((mOutputBufLen - mOutputPtr) - (3 + len)) < 0) {
            fastWriteRaw(' ');
            fastWriteRaw(localName);
            fastWriteRaw('=', '"');
        } else {
            int ptr = mOutputPtr;
            char[] buf = mOutputBuffer;
            buf[ptr++] = ' ';
            localName.getChars(0, len, buf, ptr);
            ptr += len;
            buf[ptr++] = '=';
            buf[ptr++] = '"';
            mOutputPtr = ptr;
        }

        if (vlen > 0) {
            if (mAttrValueWriter != null) { // custom escaping?
                mAttrValueWriter.write(value, offset, vlen);
            } else { // nope, default
                writeAttrValue(value, offset, vlen);
            }
        }
        fastWriteRaw('"');
    }

    @Override
    public void writeAttribute(String prefix, String localName, String value)
        throws IOException, XMLStreamException
    {
        if (mOut == null) {
            return;
        }
        if (mCheckNames) {
            verifyNameValidity(prefix, mNsAware);
            verifyNameValidity(localName, mNsAware);
        }
        int len = prefix.length();
        if (((mOutputBufLen - mOutputPtr) - (4 + localName.length() + len)) < 0) {
            fastWriteRaw(' ');
            if (len > 0) {
                fastWriteRaw(prefix);
                fastWriteRaw(':');
            }
            fastWriteRaw(localName);
            fastWriteRaw('=', '"');
        } else {
            int ptr = mOutputPtr;
            char[] buf = mOutputBuffer;
            buf[ptr++] = ' ';
            prefix.getChars(0, len, buf, ptr);
            ptr += len;
            buf[ptr++] = ':';
            len = localName.length();
            localName.getChars(0, len, buf, ptr);
            ptr += len;
            buf[ptr++] = '=';
            buf[ptr++] = '"';
            mOutputPtr = ptr;
        }

        len = (value == null) ? 0 : value.length();
        if (len > 0) {
            if (mAttrValueWriter != null) { // custom escaping?
                mAttrValueWriter.write(value, 0, len);
            } else { // nope, default
                writeAttrValue(value, len);
            }
        }
        fastWriteRaw('"');
    }

    @Override
    public void writeAttribute(String prefix, String localName, char[] value, int offset, int vlen)
        throws IOException, XMLStreamException
    {
        if (mOut == null) {
            return;
        }
        if (mCheckNames) {
            verifyNameValidity(prefix, mNsAware);
            verifyNameValidity(localName, mNsAware);
        }
        int len = prefix.length();
        if (((mOutputBufLen - mOutputPtr) - (4 + localName.length() + len)) < 0) {
            fastWriteRaw(' ');
            if (len > 0) {
                fastWriteRaw(prefix);
                fastWriteRaw(':');
            }
            fastWriteRaw(localName);
            fastWriteRaw('=', '"');
        } else {
            int ptr = mOutputPtr;
            char[] buf = mOutputBuffer;
            buf[ptr++] = ' ';
            prefix.getChars(0, len, buf, ptr);
            ptr += len;
            buf[ptr++] = ':';
            len = localName.length();
            localName.getChars(0, len, buf, ptr);
            ptr += len;
            buf[ptr++] = '=';
            buf[ptr++] = '"';
            mOutputPtr = ptr;
        }
        if (vlen > 0) {
            if (mAttrValueWriter != null) { // custom escaping?
                mAttrValueWriter.write(value, offset, vlen);
            } else { // nope, default
                writeAttrValue(value, offset, vlen);
            }
        }
        fastWriteRaw('"');
    }

    private final void writeAttrValue(String value, int len)
        throws IOException
    {
        int inPtr = 0;
        final char qchar = mEncQuoteChar;
        int highChar = mEncHighChar;
        
        main_loop:
        while (true) { // main_loop
            String ent = null;
            
            inner_loop:
            while (true) {
                if (inPtr >= len) {
                    break main_loop;
                }
                char c = value.charAt(inPtr++);
                if (c <= HIGHEST_ENCODABLE_ATTR_CHAR) { // special char?
                    if (c < 0x0020) { // tab, cr/lf need encoding too
                        if (c == '\r') {
                            if (mEscapeCR) {
                                break inner_loop; // quoting
                            }
                        } else if (c != '\n' && c != '\t'
                            && (!mXml11 || c == 0)) {
                            c = handleInvalidChar(c);
                        } else {
                            break inner_loop; // need quoting
                        }
                    } else if (c == qchar) {
                        ent = mEncQuoteEntity;
                        break inner_loop;
                    } else if (c == '<') {
                        ent = "&lt;";
                        break inner_loop;
                    } else if (c == '&') {
                        ent = "&amp;";
                        break inner_loop;
                    }
                } else if (c >= highChar) { // out of range, have to escape
                    break inner_loop;
                }
                if (mOutputPtr >= mOutputBufLen) {
                    flushBuffer();
                }
                mOutputBuffer[mOutputPtr++] = c;
            }
            if (ent != null) {
                writeRaw(ent);
            } else {
                writeAsEntity(value.charAt(inPtr-1));
            }
        }
    }

    private final void writeAttrValue(char[] value, int offset, int len)
        throws IOException
    {
        len += offset;
        final char qchar = mEncQuoteChar;
        int highChar = mEncHighChar;
        
        main_loop:
        while (true) { // main_loop
            String ent = null;
            
            inner_loop:
            while (true) {
                if (offset >= len) {
                    break main_loop;
                }
                char c = value[offset++];
                if (c <= HIGHEST_ENCODABLE_ATTR_CHAR) { // special char?
                    if (c < 0x0020) { // tab, cr/lf need encoding too
                        if (c == '\r') {
                            if (mEscapeCR) {
                                break inner_loop; // quoting
                            }
                        } else if (c != '\n' && c != '\t'
                            && (!mXml11 || c == 0)) {
                            c = handleInvalidChar(c);
                        } else {
                            break inner_loop; // need quoting
                        }
                    } else if (c == qchar) {
                        ent = mEncQuoteEntity;
                        break inner_loop;
                    } else if (c == '<') {
                        ent = "&lt;";
                        break inner_loop;
                    } else if (c == '&') {
                        ent = "&amp;";
                        break inner_loop;
                    }
                } else if (c >= highChar) { // out of range, have to escape
                    break inner_loop;
                }
                if (mOutputPtr >= mOutputBufLen) {
                    flushBuffer();
                }
                mOutputBuffer[mOutputPtr++] = c;
            }
            if (ent != null) {
                writeRaw(ent);
            } else {
                writeAsEntity(value[offset-1]);
            }
        }
    }

    /*
    ////////////////////////////////////////////////
    // Methods used by Typed Access API
    ////////////////////////////////////////////////
     */

    @Override
    public final void writeTypedElement(AsciiValueEncoder enc)
        throws IOException
    {
        if (mOut == null) {
            return;
        }

        int free = mOutputBufLen - mOutputPtr;
        if (enc.bufferNeedsFlush(free)) {
            flush();
        }
        while (true) {
            mOutputPtr = enc.encodeMore(mOutputBuffer, mOutputPtr, mOutputBufLen);
            // If no flushing needed, indicates that all data was encoded
            if (enc.isCompleted()) {
                break;
            }
            flush();
        }
    }

    @Override
    public final void writeTypedElement(AsciiValueEncoder enc,
            XMLValidator validator, char[] copyBuffer)
        throws IOException, XMLStreamException
    {
        if (mOut == null) {
            return;
        }
        int free = mOutputBufLen - mOutputPtr;
        if (enc.bufferNeedsFlush(free)) {
            flush();
        }
        int start = mOutputPtr;
        while (true) {
            mOutputPtr = enc.encodeMore(mOutputBuffer, mOutputPtr, mOutputBufLen);
            // False -> can't be sure it's the whole remaining text
            validator.validateText(mOutputBuffer, start, mOutputPtr, false);
            if (enc.isCompleted()) {
                break;
            }
            flush();
            start = mOutputPtr;
        }
    }

    @Override
    public void writeTypedAttribute(String localName, AsciiValueEncoder enc)
        throws IOException, XMLStreamException
    {
        if (mOut == null) {
            return;
        }
        if (mCheckNames) {
            verifyNameValidity(localName, mNsAware);
        }
        int len = localName.length();
        if ((mOutputPtr + 3 + len) > mOutputBufLen) {
            fastWriteRaw(' ');
            fastWriteRaw(localName);
            fastWriteRaw('=', '"');
        } else {
            int ptr = mOutputPtr;
            char[] buf = mOutputBuffer;
            buf[ptr++] = ' ';
            localName.getChars(0, len, buf, ptr);
            ptr += len;
            buf[ptr++] = '=';
            buf[ptr++] = '"';
            mOutputPtr = ptr;
        }

        int free = mOutputBufLen - mOutputPtr;
        if (enc.bufferNeedsFlush(free)) {
            flush();
        }
        while (true) {
            mOutputPtr = enc.encodeMore(mOutputBuffer, mOutputPtr, mOutputBufLen);
            if (enc.isCompleted()) {
                break;
            }
            flush();
        }
        fastWriteRaw('"');
    }

    @Override
    public void writeTypedAttribute(String prefix, String localName,
                                    AsciiValueEncoder enc)
        throws IOException, XMLStreamException
    {
        if (mOut == null) {
            return;
        }
        if (mCheckNames) {
            verifyNameValidity(prefix, mNsAware);
            verifyNameValidity(localName, mNsAware);
        }
        int plen = prefix.length();
        int llen = localName.length();

        if ((mOutputPtr + 4 + plen + llen) > mOutputBufLen) {
            writePrefixedName(prefix, localName);
            fastWriteRaw('=', '"');
        } else {
            int ptr = mOutputPtr;
            char[] buf = mOutputBuffer;
            buf[ptr++] = ' ';
            if (plen > 0) {
                prefix.getChars(0, plen, buf, ptr);
                ptr += plen;
                buf[ptr++] = ':';

            }
            localName.getChars(0, llen, buf, ptr);
            ptr += llen;
            buf[ptr++] = '=';
            buf[ptr++] = '"';
            mOutputPtr = ptr;
        }

        int free = mOutputBufLen - mOutputPtr;
        if (enc.bufferNeedsFlush(free)) {
            flush();
        }
        while (true) {
            mOutputPtr = enc.encodeMore(mOutputBuffer, mOutputPtr, mOutputBufLen);
            if (enc.isCompleted()) {
                break;
            }
            flush();
        }

        fastWriteRaw('"');
    }

    @Override
    public void writeTypedAttribute(String prefix, String localName, String nsURI,
            AsciiValueEncoder enc,
            XMLValidator validator, char[] copyBuffer)
        throws IOException, XMLStreamException
    {
        if (mOut == null) {
            return;
        }
        if (prefix == null) {
            prefix = "";
        }
        if (nsURI == null) {
            nsURI = "";
        }
        int plen = prefix.length();
        if (mCheckNames) {
            if (plen > 0) {
                verifyNameValidity(prefix, mNsAware);
            }
            verifyNameValidity(localName, mNsAware);
        }
        if (((mOutputBufLen - mOutputPtr) - (4 + localName.length() + plen)) < 0) {
            writePrefixedName(prefix, localName);
            fastWriteRaw('=', '"');
        } else {
            int ptr = mOutputPtr;
            char[] buf = mOutputBuffer;
            buf[ptr++] = ' ';
            if (plen > 0) {
                prefix.getChars(0, plen, buf, ptr);
                ptr += plen;
                buf[ptr++] = ':';

            }
            int llen = localName.length();
            localName.getChars(0, llen, buf, ptr);
            ptr += llen;
            buf[ptr++] = '=';
            buf[ptr++] = '"';
            mOutputPtr = ptr;
        }

        /* Tricky here is this: attributes to validate can not be
         * split (validators expect complete values). So, if value
         * won't fit as is, may need to aggregate using StringBuilder
         */
        int free = mOutputBufLen - mOutputPtr;
        if (enc.bufferNeedsFlush(free)) {
            flush();
        }
        int start = mOutputPtr;

        // First, let's see if one call is enough
        mOutputPtr = enc.encodeMore(mOutputBuffer, mOutputPtr, mOutputBufLen);
        if (enc.isCompleted()) { // yup
            validator.validateAttribute(localName, nsURI, prefix, mOutputBuffer, start, mOutputPtr);
            return;
        }

        // If not, must combine first
        StringBuilder sb = new StringBuilder(mOutputBuffer.length << 1);
        sb.append(mOutputBuffer, start, mOutputPtr-start);
        while (true) {
            flush();
            start = mOutputPtr;
            mOutputPtr = enc.encodeMore(mOutputBuffer, mOutputPtr, mOutputBufLen);
            sb.append(mOutputBuffer, start, mOutputPtr-start);
            // All done?
            if (enc.isCompleted()) {
                break;
            }
        }
        fastWriteRaw('"');

        // Then validate
        String valueStr = sb.toString();
        validator.validateAttribute(localName, nsURI, prefix, valueStr);
    }

    protected final void writePrefixedName(String prefix, String localName)
        throws IOException
    {
        fastWriteRaw(' ');
        if (prefix.length() > 0) {
            fastWriteRaw(prefix);
            fastWriteRaw(':');
        }
        fastWriteRaw(localName);
    }

    /*
    ////////////////////////////////////////////////////
    // Internal methods, buffering
    ////////////////////////////////////////////////////
     */

    private final void flushBuffer()
        throws IOException
    {
        if (mOutputPtr > 0 && mOutputBuffer != null) {
            int ptr = mOutputPtr;
            // Need to update location info, to keep it in sync
            mLocPastChars += ptr;
            mLocRowStartOffset -= ptr;
            mOutputPtr = 0;
            mOut.write(mOutputBuffer, 0, ptr);
        }
    }

    private final void fastWriteRaw(char c)
        throws IOException
    {
        if (mOutputPtr >= mOutputBufLen) {
            if (mOut == null) {
                return;
            }
            flushBuffer();
        }
        mOutputBuffer[mOutputPtr++] = c;
    }

    private final void fastWriteRaw(char c1, char c2)
        throws IOException
    {
        if ((mOutputPtr + 1) >= mOutputBufLen) {
            if (mOut == null) {
                return;
            }
            flushBuffer();
        }
        mOutputBuffer[mOutputPtr++] = c1;
        mOutputBuffer[mOutputPtr++] = c2;
    }

    private final void fastWriteRaw(String str)
        throws IOException
    {
        int len = str.length();
        int ptr = mOutputPtr;
        if ((ptr + len) >= mOutputBufLen) {
            if (mOut == null) {
                return;
            }
            /* It's even possible that String is longer than the buffer (not
             * likely, possible). If so, let's just call the full
             * method:
             */
            if (len > mOutputBufLen) {
                writeRaw(str);
                return;
            }
            flushBuffer();
            ptr = mOutputPtr;
        }
        str.getChars(0, len, mOutputBuffer, ptr);
        mOutputPtr = ptr+len;
    }

    /*
    ////////////////////////////////////////////////////
    // Internal methods, content verification/fixing
    ////////////////////////////////////////////////////
     */

    /**
     * @return Index at which a problem was found, if any; -1 if there's
     *   no problem.
     */
    protected int verifyCDataContent(String content)
    {
        if (content != null && content.length() >= 3) {
            int ix = content.indexOf(']');
            if (ix >= 0) {
                return content.indexOf("]]>", ix);
            }
        }
        return -1;
    }

    protected int verifyCDataContent(char[] c, int start, int end)
    {
        if (c != null) {
            start += 2;
            /* Let's do simple optimization for search...
             * (simple bayer-moore - like algorithm) 
             */
            while (start < end) {
                char ch = c[start];
                if (ch == ']') {
                    ++start; // let's just move by one in this case
                    continue;
                }
                if (ch == '>') { // match?
                    if (c[start-1] == ']' 
                        && c[start-2] == ']') {
                        return start-2;
                    }
                }
                start += 2;
            }
        }
        return -1;
    }
    
    protected int verifyCommentContent(String content)
    {
        int ix = content.indexOf('-');
        if (ix >= 0) {
            /* actually, it's illegal to just end with '-' too, since 
             * that would cause invalid end marker '--->'
             */
            if (ix < (content.length() - 1)) {
                ix = content.indexOf("--", ix);
            }
        }
        return ix;
    }

    protected void writeSegmentedCData(String content, int index)
        throws IOException
    {
        /* It's actually fairly easy, just split "]]>" into 2 pieces;
         * for each ']]>'; first one containing "]]", second one ">"
         * (as long as necessary)
         */
        int start = 0;
        while (index >= 0) {
            fastWriteRaw("<![CDATA[");
            writeRaw(content, start, (index+2) - start);
            fastWriteRaw("]]>");
            start = index+2;
            index = content.indexOf("]]>", start);
        }
        // Ok, then the last segment
        fastWriteRaw("<![CDATA[");
        writeRaw(content, start, content.length()-start);
        fastWriteRaw("]]>");
    }

    protected void writeSegmentedCData(char[] c, int start, int len, int index)
        throws IOException
    {
        int end = start + len;
        while (index >= 0) {
            fastWriteRaw("<![CDATA[");
            writeRaw(c, start, (index+2) - start);
            fastWriteRaw("]]>");
            start = index+2;
            index = verifyCDataContent(c, start, end);
        }
        // Ok, then the last segment
        fastWriteRaw("<![CDATA[");
        writeRaw(c, start, end-start);
        fastWriteRaw("]]>");
    }

    protected void writeSegmentedComment(String content, int index)
        throws IOException
    {
        int len = content.length();
        // First the special case (last char is hyphen):
        if (index == (len-1)) {
            fastWriteRaw("<!--");
            writeRaw(content);
            // we just need to inject one space in there
            fastWriteRaw(" -->");
            return;
        }
        
        /* Fixing comments is more difficult than that of CDATA segments';
         * this because CDATA can still contain embedded ']]'s, but
         * comment neither allows '--' nor ending with '-->'; which means
         * that it's impossible to just split segments. Instead we'll do
         * something more intrusive, and embed single spaces between all
         * '--' character pairs... it's intrusive, but comments are not
         * supposed to contain any data, so that should be fine (plus
         * at least result is valid, unlike contents as is)
         */
        fastWriteRaw("<!--");
        int start = 0;
        while (index >= 0) {
            // first, content prior to '--' and the first hyphen
            writeRaw(content, start, (index+1) - start);
            // and an obligatory trailing space to split double-hyphen
            fastWriteRaw(' ');
            // still need to handle rest of consequtive double'-'s if any
            start = index+1;
            index = content.indexOf("--", start);
        }
        // Ok, then the last segment
        writeRaw(content, start, len-start);
        // ends with a hyphen? that needs to be fixed, too
        if (content.charAt(len-1) == '-') {
            fastWriteRaw(' ');
        }
        fastWriteRaw("-->");
    }

    /**
     * Method used to figure out which part of the Unicode char set the
     * encoding can natively support. Values returned are 7, 8 and 16,
     * to indicate (respectively) "ascii", "ISO-Latin" and "native Unicode".
     * These just best guesses, but should work ok for the most common
     * encodings.
     */
    public static int guessEncodingBitSize(String enc)
    {
        if (enc == null || enc.length() == 0) { // let's assume default is UTF-8...
            return 16;
        }

        // Let's see if we can find a normalized name, first:
        enc = CharsetNames.normalize(enc);

        // Ok, first, do we have known ones; starting with most common:
        if (enc == CharsetNames.CS_UTF8) {
            return 16; // meaning up to 2^16 can be represented natively
        } else if (enc == CharsetNames.CS_ISO_LATIN1) {
            return 8;
        } else if (enc == CharsetNames.CS_US_ASCII) {
            return 7;
        } else if (enc == CharsetNames.CS_UTF16
                   || enc == CharsetNames.CS_UTF16BE
                   || enc == CharsetNames.CS_UTF16LE
                   || enc == CharsetNames.CS_UTF32BE
                   || enc == CharsetNames.CS_UTF32LE) {
            return 16;
        }

        /* Above and beyond well-recognized names, it might still be
         * good to have more heuristics for as-of-yet unhandled cases...
         * But, it's probably easier to only assume 8-bit clean (could
         * even make it just 7, let's see how this works out)
         */
        return 8;
    }

    protected final void writeAsEntity(int c)
        throws IOException
    {
        char[] buf = mOutputBuffer;
        int ptr = mOutputPtr;
        if ((ptr + 10) >= buf.length) { // &#x [up to 6 hex digits] ;
            flushBuffer();
            ptr = mOutputPtr;
        }
        buf[ptr++] = '&';

        // Can use more optimal notation for 8-bit ascii stuff:
        if (c < 256) {
            /* Also; although not really mandatory, let's also
             * use pre-defined entities where possible.
             */
            if (c == '&') {
                buf[ptr++] = 'a';
                buf[ptr++] = 'm';
                buf[ptr++] = 'p';
            } else if (c == '<') {
                buf[ptr++] = 'l';
                buf[ptr++] = 't';
            } else if (c == '>') {
                buf[ptr++] = 'g';
                buf[ptr++] = 't';
            } else if (c == '\'') {
                buf[ptr++] = 'a';
                buf[ptr++] = 'p';
                buf[ptr++] = 'o';
                buf[ptr++] = 's';
            } else if (c == '"') {
                buf[ptr++] = 'q';
                buf[ptr++] = 'u';
                buf[ptr++] = 'o';
                buf[ptr++] = 't';
            } else {
                buf[ptr++] = '#';;
                buf[ptr++] = 'x';;
                // Can use shortest quoting for tab, cr, lf:
                if (c >= 16) {
                    int digit = (c >> 4);
                    buf[ptr++] = (char) ((digit < 10) ? ('0' + digit) : (('a' - 10) + digit));
                    c &= 0xF;
                }
                buf[ptr++] = (char) ((c < 10) ? ('0' + c) : (('a' - 10) + c));
            }
        } else {
            buf[ptr++] = '#';
            buf[ptr++] = 'x';

            // Ok, let's write the shortest possible sequence then:
            int shift = 20;
            int origPtr = ptr;

            do {
                int digit = (c >> shift) & 0xF;
                if (digit > 0 || (ptr != origPtr)) {
                    buf[ptr++] = (char) ((digit < 10) ? ('0' + digit) : (('a' - 10) + digit));
                }
                shift -= 4;
            } while (shift > 0);
            c &= 0xF;
            buf[ptr++] = (char) ((c < 10) ? ('0' + c) : (('a' - 10) + c));
        }
        buf[ptr++] = ';';
        mOutputPtr = ptr;
    }
}
