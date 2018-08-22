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
import java.text.MessageFormat;

import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.io.EscapingWriterFactory;
import org.codehaus.stax2.ri.typed.AsciiValueEncoder;
import org.codehaus.stax2.validation.XMLValidator;

import com.ctc.wstx.api.InvalidCharHandler;
import com.ctc.wstx.api.WriterConfig;
import com.ctc.wstx.api.WstxOutputProperties;
import com.ctc.wstx.cfg.ErrorConsts;
import com.ctc.wstx.cfg.OutputConfigFlags;
import com.ctc.wstx.exc.WstxIOException;
import com.ctc.wstx.io.WstxInputData;

/**
 * This is the base class for actual physical xml outputters. These
 * instances will only handle actual writing (possibly including
 * encoding) of the serialized textual xml, and will in general
 * not verify content being output. The exception are the
 * character-by-character checks that are most efficiently done
 * at encoding level (such as character escaping, and checks for
 * illegal character combinations), which are handled at this
 * level.
 *<p>
 * Note that implementations can have different operating modes:
 * specifically, when dealing with illegal content (such as "--"
 * in a comment, "?>" in processing instruction, or "]]>" within
 * CDATA section), implementations can do one of 3 things:
 * <ul>
 *  <li>Fix the problem, by splitting the section (which can be done
 *    for CDATA sections, and to some degree, comments)
 *   </li>
 *  <li>Stop outputting, and return an index to the illegal piece
 *    of data (if there is no easy way to fix the problem: for
 *    example, for processing instruction)
 *   </li>
 *  <li>Just output content even though it will not result in
 *    well-formed output. This should only be done if the calling
 *    application has specifically requested verifications to be
 *    disabled.
 *   </li>
 *  </ul>
 */
public abstract class XmlWriter
{
    protected final static int SURR1_FIRST = 0xD800;
    protected final static int SURR1_LAST = 0xDBFF;
    protected final static int SURR2_FIRST = 0xDC00;
    protected final static int SURR2_LAST = 0xDFFF;

    protected final static char DEFAULT_QUOTE_CHAR = '"';

    protected final WriterConfig mConfig;
    protected final String mEncoding;

    // // // Operating mode: base class needs to know whether
    // // // namespaces are support (for entity/PI target validation)

    protected final boolean mNsAware;

    protected final boolean mCheckStructure;
    protected final boolean mCheckContent;
    protected final boolean mCheckNames;
    protected final boolean mFixContent;

    /**
     * Whether to escape CR (\r) character.
     */
    final boolean mEscapeCR;

    /**
     * Whether to add a space after empty element (before closing "/>")
     * or not.
     */
    final boolean mAddSpaceAfterEmptyElem;

    /**
     * Whether to use double quotes in XML declaration or not.
     */
    final boolean mUseDoubleQuotesInXmlDecl;

    /**
     * Flag that defines whether close() on this writer should call
     * close on the underlying output object (stream, writer)
     */
    protected final boolean mAutoCloseOutput;

    /**
     * Optional escaping writer used for escaping characters like '&lt;'
     * '&amp;' and '&gt;' in textual content.
     * Constructed if calling code has
     * installed a special escaping writer factory for text content.
     * Null if the default escaper is to be used.
     */
    protected Writer mTextWriter;

    /**
     * Optional escaping writer used for escaping characters like '&quot;'
     * '&amp;' and '&lt;' in attribute values.
     * Constructed if calling code has
     * installed a special escaping writer factory for text content.
     * Null if the default escaper is to be used.
     */
    protected Writer mAttrValueWriter;

    /**
     * Indicates whether output is to be compliant; if false, is to be
     * xml 1.0 compliant, if true, xml 1.1 compliant.
     */
    protected boolean mXml11 = false;

    /**
     * Lazy-constructed wrapper object, which will route all calls to
     * Writer API, to matching <code>writeRaw</code> methods of this
     * XmlWriter instance.
     */
    protected XmlWriterWrapper mRawWrapper = null;

    /**
     * Lazy-constructed wrapper object, which will route all calls to
     * Writer API, to matching <code>writeCharacters</code> methods of this
     * XmlWriter instance.
     */
    protected XmlWriterWrapper mTextWrapper = null;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Output location info
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Number of characters output prior to currently buffered output
     */
    protected int mLocPastChars = 0;

    protected int mLocRowNr = 1;

    /**
     * Offset of the first character on this line. May be negative, if
     * the offset was in a buffer that has been flushed out.
     */
    protected int mLocRowStartOffset = 0;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////////////////////////////////////
     */

    protected XmlWriter(WriterConfig cfg, String encoding, boolean autoclose)
        throws IOException
    {
        mConfig = cfg;
        mEncoding = encoding;
        mAutoCloseOutput = autoclose;
        int flags = cfg.getConfigFlags();
        mNsAware = (flags & OutputConfigFlags.CFG_ENABLE_NS) != 0;
        mCheckStructure = (flags & OutputConfigFlags.CFG_VALIDATE_STRUCTURE) != 0;
        mCheckContent = (flags & OutputConfigFlags.CFG_VALIDATE_CONTENT) != 0;
        mCheckNames = (flags & OutputConfigFlags.CFG_VALIDATE_NAMES) != 0;
        mFixContent = (flags & OutputConfigFlags.CFG_FIX_CONTENT) != 0;
        mEscapeCR = (flags & OutputConfigFlags.CFG_ESCAPE_CR) != 0;
        mAddSpaceAfterEmptyElem = (flags & OutputConfigFlags.CFG_ADD_SPACE_AFTER_EMPTY_ELEM) != 0;
        mUseDoubleQuotesInXmlDecl = (flags & OutputConfigFlags.CFG_USE_DOUBLE_QUOTES_IN_XML_DECL) != 0;

        // Has caller requested any custom text or attr value escaping?

        EscapingWriterFactory f = mConfig.getTextEscaperFactory();
        if (f == null) {
            mTextWriter = null;
        } else {
            String enc = (mEncoding == null || mEncoding.length() == 0) ?
                WstxOutputProperties.DEFAULT_OUTPUT_ENCODING : mEncoding;
            mTextWriter = f.createEscapingWriterFor(wrapAsRawWriter(), enc);
        }

        f = mConfig.getAttrValueEscaperFactory();
        if (f == null) {
            mAttrValueWriter = null;
        } else {
            String enc = (mEncoding == null || mEncoding.length() == 0) ?
                WstxOutputProperties.DEFAULT_OUTPUT_ENCODING : mEncoding;
            mAttrValueWriter = f.createEscapingWriterFor(wrapAsRawWriter(), enc);
        }
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Extra configuration
    ///////////////////////////////////////////////////////////////////////
     */

    public void enableXml11() {
        mXml11 = true;
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Access to underlying physical output destinations
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * @return Underlying OutputStream used for physical output,
     *   if the writer was constructed using one
     */
    protected abstract OutputStream getOutputStream();

    /**
     * @return Underlying Writer used for physical output,
     *   if the writer was constructed with one, or one was
     *   created to be used with an OutputStream.
     */
    protected abstract Writer getWriter();

    /*
    ///////////////////////////////////////////////////////////////////////
    // Basic methods for communicating with underlying stream or writer
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Method called to flush the buffer(s), and close the output
     * sink (stream or writer) if enabled (auto-closing) or
     * forced.
     */
    public abstract void close(boolean forceRealClose) throws IOException;

    public abstract void flush()
        throws IOException;

    public abstract void writeRaw(String str, int offset, int len)
        throws IOException;

    public void writeRaw(String str)
        throws IOException
    {
        writeRaw(str, 0, str.length());
    }

    public abstract void writeRaw(char[] cbuf, int offset, int len)
        throws IOException;

    /**
     * Like {@link #writeRaw}, but caller guarantees that the contents
     * additionally are known to be in 7-bit ascii range.
     */
    public abstract void writeRawAscii(char[] cbuf, int offset, int len)
        throws IOException;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Raw, non-verifying write methods; used when directly copying trusted content
    ///////////////////////////////////////////////////////////////////////
     */

    public abstract void writeCDataStart()
        throws IOException;

    public abstract void writeCDataEnd()
        throws IOException;

    public abstract void writeCommentStart()
        throws IOException;

    public abstract void writeCommentEnd()
        throws IOException;

    public abstract void writePIStart(String target, boolean addSpace)
        throws IOException;

    public abstract void writePIEnd()
        throws IOException;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Write methods, textual:
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * @param data Contents of the CDATA section to write out

     * @return offset of the (first) illegal content segment ("]]>") in
     *   passed content and not in repairing mode; or -1 if none or is
     *   repairing
     */
    public abstract int writeCData(String data)
        throws IOException, XMLStreamException;

    public abstract int writeCData(char[] cbuf, int offset, int len)
        throws IOException, XMLStreamException;

    public abstract void writeCharacters(String data)
        throws IOException;

    public abstract void writeCharacters(char[] cbuf, int offset, int len)
        throws IOException;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Write methods, non-textual, non-elem/attr:
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Method that will try to output the content as specified. If
     * the content passed in has embedded "--" in it, it will either
     * add an intervening space between consequtive hyphens (if content
     * fixing is enabled), or return the offset of the first hyphen in
     * multi-hyphen sequence.
     */
    public abstract int writeComment(String data)
        throws IOException, XMLStreamException;

    /**
     * Older "legacy" output method for outputting DOCTYPE declaration.
     * Assumes that the passed-in String contains a complete DOCTYPE
     * declaration properly quoted.
     */
    public abstract void writeDTD(String data)
        throws IOException, XMLStreamException;

    public abstract void writeDTD(String rootName,
            String systemId, String publicId, String internalSubset)
        throws IOException, XMLStreamException;

    public abstract void writeEntityReference(String name)
        throws IOException, XMLStreamException;

    public abstract int writePI(String target, String data)
        throws IOException, XMLStreamException;

    public abstract void writeXmlDeclaration(String version, String enc, String standalone)
        throws IOException;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Write methods, elements
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     *<p>
     * Note: can throw XMLStreamException, if name checking is enabled,
     * and name is invalid (name check has to be in this writer, not
     * caller, since it depends not only on xml limitations, but also
     * on encoding limitations)
     */
    public abstract void writeStartTagStart(String localName)
        throws IOException, XMLStreamException;

    /**
     *<p>
     * Note: can throw XMLStreamException, if name checking is enabled,
     * and name is invalid (name check has to be in this writer, not
     * caller, since it depends not only on xml limitations, but also
     * on encoding limitations)
     */
    public abstract void writeStartTagStart(String prefix, String localName)
        throws IOException, XMLStreamException;

    public abstract void writeStartTagEnd()
        throws IOException;

    public abstract void writeStartTagEmptyEnd()
        throws IOException;

    public abstract void writeEndTag(String localName)
        throws IOException;

    public abstract void writeEndTag(String prefix, String localName)
        throws IOException;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Write methods, attributes/ns
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     *<p>
     * Note: can throw XMLStreamException, if name checking is enabled,
     * and name is invalid (name check has to be in this writer, not
     * caller, since it depends not only on xml limitations, but also
     * on encoding limitations)
     */
    public abstract void writeAttribute(String localName, String value)
        throws IOException, XMLStreamException;

    public abstract void writeAttribute(String localName, char[] value, int offset, int len)
        throws IOException, XMLStreamException;

    /**
     *<p>
     * Note: can throw XMLStreamException, if name checking is enabled,
     * and name is invalid (name check has to be in this writer, not
     * caller, since it depends not only on xml limitations, but also
     * on encoding limitations)
     */
    public abstract void writeAttribute(String prefix, String localName, String value)
        throws IOException, XMLStreamException;

    public abstract void writeAttribute(String prefix, String localName, char[] value, int offset, int len)
        throws IOException, XMLStreamException;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Write methods, Typed Access API support
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Like {@link #writeRaw}, but caller guarantees that the contents
     * additionally are known to be in 7-bit ascii range, and also
     * passes an encoder object that will encode values only when
     * being handed a buffer to append to.
     *
     * @param enc Encoder that will produce content
     */
    public abstract void writeTypedElement(AsciiValueEncoder enc)
        throws IOException;

    /**
     * Like {@link #writeRaw}, but caller guarantees that the contents
     * additionally are known to be in 7-bit ascii range, and also
     * passes an encoder object that will encode values only when
     * being handed a buffer to append to.
     *
     * @param enc Encoder that will produce content
     * @param validator Validator to use for validating serialized textual
     *   content (can not be null)
     * @param copyBuffer Temporary buffer that writer can use for temporary
     *   copies as necessary
     */
    public abstract void writeTypedElement(AsciiValueEncoder enc,
            XMLValidator validator, char[] copyBuffer)
        throws IOException, XMLStreamException;

    /**
     * Method similar to {@link #writeAttribute(String,String,char[],int,int)}
     * but where is known not to require escaping.
     * No validation needs to be performed.
     */
    public abstract void writeTypedAttribute(String localName, AsciiValueEncoder enc)
        throws IOException, XMLStreamException;

    /**
     * Method similar to {@link #writeAttribute(String,String,char[],int,int)}
     * but where is known not to require escaping.
     * No validation needs to be performed.
     */
    public abstract void writeTypedAttribute(String prefix, String localName, AsciiValueEncoder enc)
        throws IOException, XMLStreamException;

    /**
     * Method similar to {@link #writeAttribute(String,String,char[],int,int)}
     * but where is known not to require escaping.
     * Validation of the attribute value must be done by calling given
     * validator appropriately.
     */
    public abstract void writeTypedAttribute(String prefix, String localName, String nsURI,
            AsciiValueEncoder enc,
            XMLValidator validator, char[] copyBuffer)
        throws IOException, XMLStreamException;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Location information
    ///////////////////////////////////////////////////////////////////////
     */

    protected abstract int getOutputPtr();

    public int getRow() {
        return mLocRowNr;
    }

    public int getColumn() {
        return (getOutputPtr() - mLocRowStartOffset) + 1;
    }

    public int getAbsOffset() {
        return mLocPastChars +getOutputPtr();
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Wrapper methods, semi-public
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Method that can be called to get a wrapper instance that
     * can be used to essentially call the <code>writeRaw</code>
     * method.
     */
    public final Writer wrapAsRawWriter()
    {
        if (mRawWrapper == null) {
            mRawWrapper = XmlWriterWrapper.wrapWriteRaw(this);
        }
        return mRawWrapper;
    }

    public final Writer wrapAsTextWriter()
    {
        if (mTextWrapper == null) {
            mTextWrapper = XmlWriterWrapper.wrapWriteCharacters(this);
        }
        return mTextWrapper;
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Helper methods for sub-classes
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Method called to verify that the name is a legal XML name.
     */
    public final void verifyNameValidity(String name, boolean checkNs)
        throws XMLStreamException
    {
        /* No empty names... caller must have dealt with optional arguments
         * prior to calling this method
         */
        if (name == null || name.length() == 0) {
            reportNwfName(ErrorConsts.WERR_NAME_EMPTY);
        }
        int illegalIx = WstxInputData.findIllegalNameChar(name, checkNs, mXml11);
        if (illegalIx >= 0) {
            if (illegalIx == 0) {
                reportNwfName(ErrorConsts.WERR_NAME_ILLEGAL_FIRST_CHAR,
                              WstxInputData.getCharDesc(name.charAt(0)));
            }
            reportNwfName(ErrorConsts.WERR_NAME_ILLEGAL_CHAR,
                          WstxInputData.getCharDesc(name.charAt(illegalIx)));
        }
    }

    /**
     * This is the method called when an output method call violates
     * name well-formedness checks
     * and {@link WstxOutputProperties#P_OUTPUT_VALIDATE_NAMES} is
     * is enabled.
     */
    protected void reportNwfName(String msg)
        throws XMLStreamException
    {
        throwOutputError(msg);
    }

    protected void reportNwfName(String msg, Object arg)
        throws XMLStreamException
    {
        throwOutputError(msg, arg);
    }

    protected void reportNwfContent(String msg)
        throws XMLStreamException
    {
        throwOutputError(msg);
    }

    protected void throwOutputError(String msg)
        throws XMLStreamException
    {
        // First, let's flush any output we may have, to make debugging easier
        try {
            flush();
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }

        throw new XMLStreamException(msg);
    }

    protected void throwOutputError(String format, Object arg)
        throws XMLStreamException
    {
        String msg = MessageFormat.format(format, new Object[] { arg });
        throwOutputError(msg);
    }

    /**
     * Method called to handle invalid character in textual content requested
     * to be output. Content may be part of textual events (CHARACTER, CDATA),
     * attribute value, COMMENT content or PROCESSING_INSTRUCTION data.
     * The default behavior is to just throw an exception, but this can
     * be configured via property {@link WstxOutputProperties#P_OUTPUT_INVALID_CHAR_HANDLER}.
     */
    protected char handleInvalidChar(int c)
        throws IOException
    {
        // First, let's flush any output we may have, to make debugging easier
        flush();
        InvalidCharHandler h = mConfig.getInvalidCharHandler();
        if (h == null) {
            h = InvalidCharHandler.FailingHandler.getInstance();
        }
        return h.convertInvalidChar(c);
    }
}
