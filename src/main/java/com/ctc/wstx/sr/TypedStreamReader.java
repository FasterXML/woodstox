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

package com.ctc.wstx.sr;

import java.math.BigDecimal;
import java.math.BigInteger;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.typed.Base64Variant;
import org.codehaus.stax2.typed.Base64Variants;
import org.codehaus.stax2.typed.TypedArrayDecoder;
import org.codehaus.stax2.typed.TypedValueDecoder;
import org.codehaus.stax2.typed.TypedXMLStreamException;
import org.codehaus.stax2.ri.Stax2Util;
import org.codehaus.stax2.ri.typed.ValueDecoderFactory;
import org.codehaus.stax2.ri.typed.CharArrayBase64Decoder;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.cfg.ErrorConsts;
import com.ctc.wstx.io.BranchingReaderSource;
import com.ctc.wstx.io.InputBootstrapper;
import com.ctc.wstx.io.WstxInputData;

/**
 * Complete implementation of {@link org.codehaus.stax2.XMLStreamReader2},
 * including  Typed Access API (Stax2 v3.0) implementation.
 * Only functionality  missing is DTD validation, which is provided by a
 * specialized sub-class.
 */
public class TypedStreamReader
    extends BasicStreamReader
{
    /**
     * Mask of event types that are legal (starting) states
     * to call Typed Access API from.
     * 
     */
    final protected static int MASK_TYPED_ACCESS_ARRAY =
        (1 << START_ELEMENT)
        | (1 << END_ELEMENT) // for convenience
        | (1 << CHARACTERS) | (1 << CDATA) | (1 << SPACE)
    // Not ok for PI or COMMENT? Let's assume so
        ;

    final protected static int MASK_TYPED_ACCESS_BINARY =
        (1 << START_ELEMENT) //  note: END_ELEMENT handled separately
        | (1 << CHARACTERS) | (1 << CDATA) | (1 << SPACE)
        ;

    /**
     * Minimum length of text chunks to parse before base64 decoding.
     * Will try to limit it to fit within regular result buffers.
     */
    final static int MIN_BINARY_CHUNK = 2000;

    /**
     * Factory used for constructing decoders we need for typed access
     */
    protected ValueDecoderFactory _decoderFactory;

    /**
     * Lazily-constructed decoder object for decoding base64 encoded
     * element binary content.
     */
    protected CharArrayBase64Decoder _base64Decoder = null;

    /*
    ////////////////////////////////////////////////////
    // Instance construction
    ////////////////////////////////////////////////////
     */

    protected TypedStreamReader(InputBootstrapper bs,
                                BranchingReaderSource input, ReaderCreator owner,
                                ReaderConfig cfg, InputElementStack elemStack,
                                boolean forER)
        throws XMLStreamException
    {
        super(bs, input, owner, cfg, elemStack, forER);
    }

    /**
     * Factory method for constructing readers.
     *
     * @param owner "Owner" of this reader, factory that created the reader;
     *   needed for returning updated symbol table information after parsing.
     * @param input Input source used to read the XML document.
     * @param cfg Object that contains reader configuration info.
     */
    public static TypedStreamReader createStreamReader
        (BranchingReaderSource input, ReaderCreator owner, ReaderConfig cfg,
         InputBootstrapper bs, boolean forER)
        throws XMLStreamException
    {

        TypedStreamReader sr = new TypedStreamReader
            (bs, input, owner, cfg, createElementStack(cfg), forER);
        return sr;
    }


    /*
    ////////////////////////////////////////////////////////
    // TypedXMLStreamReader2 implementation, scalar elements
    ////////////////////////////////////////////////////////
     */

    @Override
    public boolean getElementAsBoolean() throws XMLStreamException
    {
        ValueDecoderFactory.BooleanDecoder dec = _decoderFactory().getBooleanDecoder();
        getElementAs(dec);
        return dec.getValue();
    }

    @Override
    public int getElementAsInt() throws XMLStreamException
    {
        ValueDecoderFactory.IntDecoder dec = _decoderFactory().getIntDecoder();
        getElementAs(dec);
        return dec.getValue();
    }

    @Override
    public long getElementAsLong() throws XMLStreamException
    {
        ValueDecoderFactory.LongDecoder dec = _decoderFactory().getLongDecoder();
        getElementAs(dec);
        return dec.getValue();
    }

    @Override
    public float getElementAsFloat() throws XMLStreamException
    {
        ValueDecoderFactory.FloatDecoder dec = _decoderFactory().getFloatDecoder();
        getElementAs(dec);
        return dec.getValue();
    }

    @Override
    public double getElementAsDouble() throws XMLStreamException
    {
        ValueDecoderFactory.DoubleDecoder dec = _decoderFactory().getDoubleDecoder();
        getElementAs(dec);
        return dec.getValue();
    }

    @Override
    public BigInteger getElementAsInteger() throws XMLStreamException
    {
        ValueDecoderFactory.IntegerDecoder dec = _decoderFactory().getIntegerDecoder();
        getElementAs(dec);
        return dec.getValue();
    }

    @Override
    public BigDecimal getElementAsDecimal() throws XMLStreamException
    {
        ValueDecoderFactory.DecimalDecoder dec = _decoderFactory().getDecimalDecoder();
        getElementAs(dec);
        return dec.getValue();
    }

    @Override
    public QName getElementAsQName() throws XMLStreamException
    {
        ValueDecoderFactory.QNameDecoder dec = _decoderFactory().getQNameDecoder(getNamespaceContext());
        getElementAs(dec);
        return _verifyQName(dec.getValue());
    }

    @Override
    public final byte[] getElementAsBinary() throws XMLStreamException
    {
        return getElementAsBinary(Base64Variants.getDefaultVariant());
    }

    @Override
    public byte[] getElementAsBinary(Base64Variant v) throws XMLStreamException
    {
        // note: code here is similar to Base64DecoderBase.aggregateAll(), see comments there
        Stax2Util.ByteAggregator aggr = _base64Decoder().getByteAggregator();
        byte[] buffer = aggr.startAggregation();
        while (true) {
            int offset = 0;
            int len = buffer.length;

            do {
                int readCount = readElementAsBinary(buffer, offset, len, v);
                if (readCount < 1) { // all done!
                    return aggr.aggregateAll(buffer, offset);
                }
                offset += readCount;
                len -= readCount;
            } while (len > 0);
            buffer = aggr.addFullBlock(buffer);
        }
    }

    @Override
    public void getElementAs(TypedValueDecoder tvd) throws XMLStreamException
    {
        if (mCurrToken != START_ELEMENT) {
            throwParseError(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        /* Ok, now: with START_ELEMENT we know that it's not partially
         * processed; that we are in-tree (not prolog or epilog).
         * The only possible complication would be:
         */
        if (mStEmptyElem) {
            /* And if so, we'll then get 'virtual' close tag; things
             * are simple as location info was set when dealing with
             * empty start element; and likewise, validation (if any)
             * has been taken care of
             */
            mStEmptyElem = false;
            mCurrToken = END_ELEMENT;
            _handleEmptyValue(tvd);
            return;
        }
        // First need to find a textual event
        while (true) {
            int type = next();
            if (type == END_ELEMENT) {
                _handleEmptyValue(tvd);
                return;
            }
            if (type == COMMENT || type == PROCESSING_INSTRUCTION) {
                continue;
            }
            if (((1 << type) & MASK_GET_ELEMENT_TEXT) == 0) {
                throwParseError("Expected a text token, got "+tokenTypeDesc(type)+".");
            }
            break;
        }
        if (mTokenState < TOKEN_FULL_SINGLE) {
            readCoalescedText(mCurrToken, false);
        }
        /* Ok: then a quick check; if it looks like we are directly
         * followed by the end tag, we need not construct String
         * quite yet.
         */
        if ((mInputPtr + 1) < mInputEnd &&
            mInputBuffer[mInputPtr] == '<' && mInputBuffer[mInputPtr+1] == '/') {
            // Note: next() has validated text, no need for more validation
            mInputPtr += 2;
            mCurrToken = END_ELEMENT;
            /* Can by-pass next(), nextFromTree(), in this case.
             * However, must do decoding first, and only then call
             * readEndElem(), since this latter call may invalidate
             * underlying input buffer (when end tag is at buffer
             * boundary)
             */
            try { // buffer now has all the data
                mTextBuffer.decode(tvd);
            } catch (IllegalArgumentException iae) {
                throw _constructTypeException(iae, mTextBuffer.contentsAsString());
            }
            readEndElem();
            return;
        }

        // Otherwise, we'll need to do slower processing
        int extra = 1 + (mTextBuffer.size() >> 1); // let's add 50% space
        StringBuilder sb = mTextBuffer.contentsAsStringBuilder(extra);
        int type;
        
        while ((type = next()) != END_ELEMENT) {
            if (((1 << type) & MASK_GET_ELEMENT_TEXT) != 0) {
                if (mTokenState < TOKEN_FULL_SINGLE) {
                    readCoalescedText(type, false);
                }
                mTextBuffer.contentsToStringBuilder(sb);
                continue;
            }
            if (type != COMMENT && type != PROCESSING_INSTRUCTION) {
                throwParseError("Expected a text token, got "+tokenTypeDesc(type)+".");
            }
        }
        // Note: calls next() have validated text, no need for more validation
        String str = sb.toString();
        String tstr = Stax2Util.trimSpaces(str);
        if (tstr == null) {
            _handleEmptyValue(tvd);
        } else {
            try {
                tvd.decode(tstr);
            } catch (IllegalArgumentException iae) {
                throw _constructTypeException(iae, str);
            }
        }
    }

    /*
    ////////////////////////////////////////////////////////
    // TypedXMLStreamReader2 implementation, array elements
    ////////////////////////////////////////////////////////
     */

    @Override
    public int readElementAsIntArray(int[] value, int from, int length) throws XMLStreamException
    {
        return readElementAsArray(_decoderFactory().getIntArrayDecoder(value, from, length));
    }

    @Override
    public int readElementAsLongArray(long[] value, int from, int length) throws XMLStreamException
    {
        return readElementAsArray(_decoderFactory().getLongArrayDecoder(value, from, length));
    }

    @Override
    public int readElementAsFloatArray(float[] value, int from, int length) throws XMLStreamException
    {
        return readElementAsArray(_decoderFactory().getFloatArrayDecoder(value, from, length));
    }

    @Override
    public int readElementAsDoubleArray(double[] value, int from, int length) throws XMLStreamException
    {
        return readElementAsArray(_decoderFactory().getDoubleArrayDecoder(value, from, length));
    }

    /**
     * Method called to parse array of primitives.
     *<p>
     * !!! 05-Sep-2008, tatu: Current implementation is not optimal
     *   either performance-wise, or from getting accurate Location
     *   for decoding problems. But it works otherwise, and we need
     *   to get Woodstox 4.0 out by the end of the year... so it'll
     *   do, for now.
     *
     * @return Number of elements decoded (if any were decoded), or
     *   -1 to indicate that no more values can be decoded.
     */
    @Override
    public final int readElementAsArray(TypedArrayDecoder dec)
        throws XMLStreamException
    {
        int type = mCurrToken;
        // First things first: must be acceptable start state:
        if (((1 << type) & MASK_TYPED_ACCESS_ARRAY) == 0) {
            throwNotTextualOrElem(type);
        }

        // Are we just starting (START_ELEMENT)?
        if (type == START_ELEMENT) {
            // Empty? Not common, but can short-cut handling if occurs
            if (mStEmptyElem) {
                mStEmptyElem = false;
                mCurrToken = END_ELEMENT;
                return -1;
            }
            // Otherwise let's just find the first text segment
            while (true) {
                type = next();
                if (type == END_ELEMENT) {
                    // Simple... no textul content
                    return -1;
                }
                if (type == COMMENT || type == PROCESSING_INSTRUCTION) {
                    continue;
                }
                if (type == CHARACTERS || type == CDATA) {
                    break;
                }
                // otherwise just not legal (how about SPACE, unexpanded entities?)
                throw _constructUnexpectedInTyped(type);
            }
        }

        int count = 0;
        while (type != END_ELEMENT) {
            /* Ok then: we will have a valid textual type. Just need to
             * ensure current segment is completed. Plus, for current impl,
             * also need to coalesce to prevent artificial CDATA/text
             * boundary from splitting tokens
             */
            if (type == CHARACTERS || type == CDATA || type == SPACE) {
                if (mTokenState < TOKEN_FULL_SINGLE) {
                    readCoalescedText(type, false);
                }
            } else if (type == COMMENT || type == PROCESSING_INSTRUCTION) {
                type = next();
                continue;
            } else {
                throw _constructUnexpectedInTyped(type);
            }
            count += mTextBuffer.decodeElements(dec, this);
            if (!dec.hasRoom()) {
                break;
            }
            type = next();
        }

        // If nothing was found, needs to be indicated via -1, not 0
        return (count > 0) ? count : -1;
    }

    /*
    ////////////////////////////////////////////////////////
    // TypedXMLStreamReader2 implementation, binary data
    ////////////////////////////////////////////////////////
     */

    @Override
    public final int readElementAsBinary(byte[] resultBuffer, int offset, int maxLength)
        throws XMLStreamException
    {
        return readElementAsBinary(resultBuffer, offset, maxLength, Base64Variants.getDefaultVariant());
    }

    @Override
    public int readElementAsBinary(byte[] resultBuffer, int offset, int maxLength, Base64Variant v)
        throws XMLStreamException
    {
        if (resultBuffer == null) {
            throw new IllegalArgumentException("resultBuffer is null");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Illegal offset ("+offset+"), must be [0, "+resultBuffer.length+"[");
        }
        if (maxLength < 1 || (offset + maxLength) > resultBuffer.length) {
            if (maxLength == 0) { // special case, allowed, but won't do anything
                return 0;
            }
            throw new IllegalArgumentException("Illegal maxLength ("+maxLength+"), has to be positive number, and offset+maxLength can not exceed"+resultBuffer.length);
        }

        final CharArrayBase64Decoder dec = _base64Decoder();
        int type = mCurrToken;
        // First things first: must be acceptable start state:
        if (((1 << type) & MASK_TYPED_ACCESS_BINARY) == 0) {
            if (type == END_ELEMENT) {
                // Minor complication: may have unflushed stuff (non-padded versions)
                if (!dec.hasData()) {
                    return -1;
                }
            } else {
                throwNotTextualOrElem(type);
            }
        } else if (type == START_ELEMENT) { // just starting (START_ELEMENT)?
            if (mStEmptyElem) { // empty element? simple...
                mStEmptyElem = false;
                mCurrToken = END_ELEMENT;
                return -1;
            }
            // Otherwise let's just find the first text segment
            while (true) {
                type = next();
                if (type == END_ELEMENT) {
                    // Simple... no textual content
                    return -1;
                }
                if (type == COMMENT || type == PROCESSING_INSTRUCTION) {
                    continue;
                }
                /* 12-Dec-2009, tatu: Important: in coalescing mode we may
                 *   have incomplete segment that needs to be completed
                 */
                if (mTokenState < mStTextThreshold) {
                    finishToken(false);
                }
                _initBinaryChunks(v, dec, type, true);
                break;
            }
        }

        int totalCount = 0;

        main_loop:
        while (true) {
            // Ok, decode:
            int count;
            try {
                count = dec.decode(resultBuffer, offset, maxLength);
            } catch (IllegalArgumentException iae) {
                // !!! 26-Sep-2008, tatus: should try to figure out which char (etc) triggered problem to pass with typed exception
                throw _constructTypeException(iae.getMessage(), "");
            }
            offset += count;
            totalCount += count;
            maxLength -= count;

            /* And if we filled the buffer we are done. Or, an edge
             * case: reached END_ELEMENT (for non-padded variant)
             */
            if (maxLength < 1 || mCurrToken == END_ELEMENT) {
                break;
            }
            // Otherwise need to advance to the next event
            while (true) {
                type = next();
                if (type == COMMENT || type == PROCESSING_INSTRUCTION
                    || type == SPACE) { // space is ignorable too
                    continue;
                }
                if (type == END_ELEMENT) {
                    /* Just need to verify we don't have partial stuff
                     * (missing one to three characters of a full quartet
                     * that encodes 1 - 3 bytes). Also: non-padding
                     * variants can be in incomplete state, from which
                     * data may need to be flushed...
                     */
                    int left = dec.endOfContent();
                    if (left < 0) { // incomplete, error
                        throw _constructTypeException("Incomplete base64 triplet at the end of decoded content", "");
                    } else if (left > 0) { // 1 or 2 more bytes of data, loop some more
                        continue main_loop;
                    }
                    // Otherwise, no more data, we are done
                    break main_loop;
                }
                /* 12-Dec-2009, tatu: Important: in coalescing mode we may
                 *   have incomplete segment that needs to be completed
                 */
                if (mTokenState < mStTextThreshold) {
                    finishToken(false);
                }
                _initBinaryChunks(v, dec, type, false);
                break;
            }
        }

        // If nothing was found, needs to be indicated via -1, not 0
        return (totalCount > 0) ? totalCount : -1;
    }

    private final void _initBinaryChunks(Base64Variant v, CharArrayBase64Decoder dec, int type, boolean isFirst)
        throws XMLStreamException
    {
        if (type == CHARACTERS) {
            if (mTokenState < mStTextThreshold) {
                mTokenState = readTextSecondary(MIN_BINARY_CHUNK, false)
                    ? TOKEN_FULL_SINGLE : TOKEN_PARTIAL_SINGLE;
            }
        } else if (type == CDATA) {
            if (mTokenState < mStTextThreshold) {
                mTokenState = readCDataSecondary(MIN_BINARY_CHUNK)
                    ? TOKEN_FULL_SINGLE : TOKEN_PARTIAL_SINGLE;
            }
        } else {
            throw _constructUnexpectedInTyped(type);
        }
        mTextBuffer.initBinaryChunks(v, dec, isFirst);
    }

    /*
    ///////////////////////////////////////////////////////////
    // TypedXMLStreamReader2 implementation, scalar attributes
    ///////////////////////////////////////////////////////////
     */

    @Override
    public int getAttributeIndex(String namespaceURI, String localName)
    {
        // Note: cut'n pasted from "getAttributeInfo()"
        if (mCurrToken != START_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        return mElementStack.findAttributeIndex(namespaceURI, localName);
    }

    @Override
    public boolean getAttributeAsBoolean(int index) throws XMLStreamException
    {
        ValueDecoderFactory.BooleanDecoder dec = _decoderFactory().getBooleanDecoder();
        getAttributeAs(index, dec);
        return dec.getValue();
    }

    @Override
    public int getAttributeAsInt(int index) throws XMLStreamException
    {
        ValueDecoderFactory.IntDecoder dec = _decoderFactory().getIntDecoder();
        getAttributeAs(index, dec);
        return dec.getValue();
    }

    @Override
    public long getAttributeAsLong(int index) throws XMLStreamException
    {
        ValueDecoderFactory.LongDecoder dec = _decoderFactory().getLongDecoder();
        getAttributeAs(index, dec);
        return dec.getValue();
    }

    @Override
    public float getAttributeAsFloat(int index) throws XMLStreamException
    {
        ValueDecoderFactory.FloatDecoder dec = _decoderFactory().getFloatDecoder();
        getAttributeAs(index, dec);
        return dec.getValue();
    }

    @Override
    public double getAttributeAsDouble(int index) throws XMLStreamException
    {
        ValueDecoderFactory.DoubleDecoder dec = _decoderFactory().getDoubleDecoder();
        getAttributeAs(index, dec);
        return dec.getValue();
    }

    @Override
    public BigInteger getAttributeAsInteger(int index) throws XMLStreamException
    {
        ValueDecoderFactory.IntegerDecoder dec = _decoderFactory().getIntegerDecoder();
        getAttributeAs(index, dec);
        return dec.getValue();
    }

    @Override
    public BigDecimal getAttributeAsDecimal(int index) throws XMLStreamException
    {
        ValueDecoderFactory.DecimalDecoder dec = _decoderFactory().getDecimalDecoder();
        getAttributeAs(index, dec);
        return dec.getValue();
    }

    @Override
    public QName getAttributeAsQName(int index) throws XMLStreamException
    {
        ValueDecoderFactory.QNameDecoder dec = _decoderFactory().getQNameDecoder(getNamespaceContext());
        getAttributeAs(index, dec);
        return _verifyQName(dec.getValue());
    }

    @Override
    public void getAttributeAs(int index, TypedValueDecoder tvd) throws XMLStreamException
    {
        if (mCurrToken != START_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        try {
            mAttrCollector.decodeValue(index, tvd);
        } catch (IllegalArgumentException iae) {
            throw _constructTypeException(iae, mAttrCollector.getValue(index));
        }
    }

    @Override
    public int[] getAttributeAsIntArray(int index) throws XMLStreamException
    {
        ValueDecoderFactory.IntArrayDecoder dec = _decoderFactory().getIntArrayDecoder();
        getAttributeAsArray(index, dec);
        return dec.getValues();
    }

    @Override
    public long[] getAttributeAsLongArray(int index) throws XMLStreamException
    {
        ValueDecoderFactory.LongArrayDecoder dec = _decoderFactory().getLongArrayDecoder();
        getAttributeAsArray(index, dec);
        return dec.getValues();
    }

    @Override
    public float[] getAttributeAsFloatArray(int index) throws XMLStreamException
    {
        ValueDecoderFactory.FloatArrayDecoder dec = _decoderFactory().getFloatArrayDecoder();
        getAttributeAsArray(index, dec);
        return dec.getValues();
    }

    @Override
    public double[] getAttributeAsDoubleArray(int index) throws XMLStreamException
    {
        ValueDecoderFactory.DoubleArrayDecoder dec = _decoderFactory().getDoubleArrayDecoder();
        getAttributeAsArray(index, dec);
        return dec.getValues();
    }

    /**
     * Method that allows reading contents of an attribute as an array
     * of whitespace-separate tokens, decoded using specified decoder.
     *
     * @return Number of tokens decoded, 0 if none found
     */
    @Override
    public int getAttributeAsArray(int index, TypedArrayDecoder tad) throws XMLStreamException
    {
        if (mCurrToken != START_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        return mAttrCollector.decodeValues(index, tad, this);
    }

    @Override
    public byte[] getAttributeAsBinary(int index) throws XMLStreamException
    {
        return getAttributeAsBinary(index, Base64Variants.getDefaultVariant());
    }

    @Override
    public byte[] getAttributeAsBinary(int index, Base64Variant v) throws XMLStreamException {
        return mAttrCollector.decodeBinary(index, v, _base64Decoder(), this);
    }

    /*
    /////////////////////////////////////////////////////
    // Internal helper methods
    /////////////////////////////////////////////////////
     */

    /**
     * Method called to verify validity of the parsed QName element
     * or attribute value. At this point binding of a prefixed name
     * (if qname has a prefix) has been verified, and thereby prefix
     * also must be valid (since there must have been a preceding
     * declaration). But local name might still not be a legal
     * well-formed xml name, so let's verify that.
     */
    protected QName _verifyQName(QName n)
        throws TypedXMLStreamException
    {
        String ln = n.getLocalPart();
        int ix = WstxInputData.findIllegalNameChar(ln, mCfgNsEnabled, mXml11);
        if (ix >= 0) {
            String prefix = n.getPrefix();
            String pname = (prefix != null && prefix.length() > 0) ?
                (prefix + ":" +ln) : ln;
            throw _constructTypeException("Invalid local name \""+ln+"\" (character at #"+ix+" is invalid)", pname);
        }
        return n;
    }

    protected ValueDecoderFactory _decoderFactory()
    {
        if (_decoderFactory == null) {
            _decoderFactory = new ValueDecoderFactory();
        }
        return _decoderFactory;
    }

    protected CharArrayBase64Decoder _base64Decoder()
    {
        if (_base64Decoder == null) {
            _base64Decoder = new CharArrayBase64Decoder();
        }
        return _base64Decoder;
    }

    /**
     * Method called to handle value that has empty String
     * as representation. This will usually either lead to an
     * exception, or parsing to the default value for the
     * type in question (null for nullable types and so on).
     */
    private void _handleEmptyValue(TypedValueDecoder dec)
        throws XMLStreamException
    {
        try { // default action is to throw an exception
            dec.handleEmptyValue();
        } catch (IllegalArgumentException iae) {
            throw _constructTypeException(iae, "");
        }
    }

    /**
     * Method called to wrap or convert given conversion-fail exception
     * into a full {@link TypedXMLStreamException},
     *
     * @param iae Problem as reported by converter
     * @param lexicalValue Lexical value (element content, attribute value)
     *    that could not be converted succesfully.
     */
    protected TypedXMLStreamException _constructTypeException(IllegalArgumentException iae, String lexicalValue)
    {
        return new TypedXMLStreamException(lexicalValue, iae.getMessage(), getStartLocation(), iae);
    }
}

