package com.ctc.wstx.io;

import java.io.*;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.cfg.ParsingErrorMsgs;
import com.ctc.wstx.cfg.XmlConsts;
import com.ctc.wstx.exc.*;

/**
 * Input bootstrap class used with streams, when encoding is not known
 * (when encoding is specified by application, a reader is constructed,
 * and then reader-based bootstrapper is used).
 *<p>
 * Encoding used for an entity (including
 * main document entity) is determined using algorithms suggested in
 * XML 1.0#3 spec, appendix F
 */
public final class StreamBootstrapper
    extends InputBootstrapper
{
    /**
     * Let's size buffer at least big enough to contain the longest possible
     * prefix of a document needed to positively identify it starts with
     * the XML declaration. That means having (optional) BOM, and then first
     * 6 characters ("<?xml "), in whatever encoding. With 4-byte encodings
     * (UCS-4), that comes to 28 bytes. And for good measure, let's pad
     * that a bit as well....
     */
    final static int MIN_BUF_SIZE = 128;

    /*
    ////////////////////////////////////////
    // Configuration
    ////////////////////////////////////////
    */

    /**
     * Underlying InputStream to use for reading content. May be null
     * if the actual data source is not stream-based but a block source.
     */
    final InputStream mIn;

    /*
    ///////////////////////////////////////////////////////////////
    // Input buffering
    ///////////////////////////////////////////////////////////////
    */

    private byte[] mByteBuffer;

    /**
     * Whether byte buffer is recyclable or not
     */
    private final boolean mRecycleBuffer;

    private int mInputPtr;
    private int mInputEnd;
    /*
    ///////////////////////////////////////////////////////////////
    // Physical encoding properties found so far
    ///////////////////////////////////////////////////////////////
    */

    boolean mBigEndian = true;

    boolean mHadBOM = false;

    boolean mByteSizeFound = false;

    /**
     * For most encodings, number of physical characters needed for
     * decoding xml declaration characters (which for variable length
     * encodings like UTF-8 will be 1). Exception is EBCDIC, which
     * while a single-byte encoding, is denoted by -1 since it
     * needs an additional translation lookup.
     */
    int mBytesPerChar; // minimum, ie. 1 for UTF-8

    /**
     * Special case for 1-byte encodings: EBCDIC is problematic
     * as it's not 7-bit ascii compatible. We can deal with it,
     * still, but only with bit of extra state.
     */
    boolean mEBCDIC = false;

    String mInputEncoding = null;

    /**
     * For single-byte non-ascii-compatible encodings (ok ok, really
     * just EBCDIC), we'll have to use a lookup table.
     */
    int[] mSingleByteTranslation = null;

    /*
    ////////////////////////////////////////
    // Life-cycle
    ////////////////////////////////////////
    */

    private StreamBootstrapper(String pubId, SystemId sysId, InputStream in)
    {
        super(pubId, sysId);
        mIn = in;
        mInputPtr = mInputEnd = 0;
        mRecycleBuffer = true;
    }

    /**
     * @param start Pointer to the first valid byte in the buffer
     * @param end Pointer to the offset <b>after</b> last valid byte in the buffer
     */
    private StreamBootstrapper(String pubId, SystemId sysId, byte[] data, int start, int end)
    {
        super(pubId, sysId);
        mIn = null;
        mRecycleBuffer = false;
        mByteBuffer = data;
        mInputPtr = start;
        mInputEnd = end;
    }

    /*
    ////////////////////////////////////////
    // Public API
    ////////////////////////////////////////
    */

    /**
     * Factory method used when the underlying data provider is an 
     * actual stream.
     */
    public static StreamBootstrapper getInstance(String pubId, SystemId sysId, InputStream in)
    {
        return new StreamBootstrapper(pubId, sysId, in);
    }

    /**
     * Factory method used when the underlying data provider is a pre-allocated
     * block source, and no stream is used.
     * Additionally the buffer passed is not owned by the bootstrapper
     * or Reader that is created, so it is not to be recycled.
     */
    public static StreamBootstrapper getInstance(String pubId, SystemId sysId, byte[] data, int start, int end)
    {
        return new StreamBootstrapper(pubId, sysId, data, start, end);
    }

    @Override
    public Reader bootstrapInput(ReaderConfig cfg, boolean mainDoc, int xmlVersion)
        throws IOException, XMLStreamException
    {
        String normEnc = null;

        // First, let's get the buffers...
        int bufSize = cfg.getInputBufferLength();
        if (bufSize < MIN_BUF_SIZE) {
            bufSize = MIN_BUF_SIZE;
        }
	if (mByteBuffer == null) { // non-null if we were passed a buffer
	    mByteBuffer = cfg.allocFullBBuffer(bufSize);
	}

        resolveStreamEncoding();

        if (hasXmlDecl()) {
            // note: readXmlDecl will set mXml11Handling too
            readXmlDecl(mainDoc, xmlVersion);
            if (mFoundEncoding != null) {
                normEnc = verifyXmlEncoding(mFoundEncoding);
            }
        } else {
            /* We'll actually then just inherit whatever main doc had...
             * (or in case there was no parent, just copy the 'unknown')
             */
            mXml11Handling = (XmlConsts.XML_V_11 == xmlVersion);
        }

        // Now, have we figured out the encoding?

        if (normEnc == null) { // not via xml declaration
            /* 21-Sep-2007, TSa: As with any non-UTF-8 encoding, declaration
             * isn't optional any more. Besides, we need that information
             * anyway to know which variant it is.
             */
            if (mEBCDIC) {
                if (mFoundEncoding == null || mFoundEncoding.length() == 0) {
                    reportXmlProblem("Missing encoding declaration: underlying encoding looks like an EBCDIC variant, but no xml encoding declaration found");
                }
                // Hmmh. What should be the canonical name? Let's just use found encoding?
                normEnc = mFoundEncoding;
            } else if (mBytesPerChar == 2) { // UTF-16, BE/LE
                normEnc = mBigEndian ? CharsetNames.CS_UTF16BE : CharsetNames.CS_UTF16LE;
            } else if (mBytesPerChar == 4) { // UCS-4... ?
                /* 22-Mar-2005, TSa: JDK apparently has no way of dealing
                 *   with these encodings... not sure if and how it should
                 *   be dealt with, really. Name could be UCS-4xx... or
                 *   perhaps UTF-32xx
                 */
                normEnc = mBigEndian ? CharsetNames.CS_UTF32BE : CharsetNames.CS_UTF32LE;
            } else {
                // Ok, default has to be UTF-8, as per XML specs
                normEnc = CharsetNames.CS_UTF8;
            }
        }

        mInputEncoding = normEnc;

        /* And then the reader. Let's figure out if we can use our own fast
         * implementations first:
         */
        BaseReader r;

        // Normalized, can thus use straight equality checks now
        if (normEnc == CharsetNames.CS_UTF8) {
            r = new UTF8Reader(cfg, mIn, mByteBuffer, mInputPtr, mInputEnd, mRecycleBuffer);
        } else if (normEnc == CharsetNames.CS_ISO_LATIN1) {
            r = new ISOLatinReader(cfg, mIn, mByteBuffer, mInputPtr, mInputEnd, mRecycleBuffer);
        } else if (normEnc == CharsetNames.CS_US_ASCII) {
            r = new AsciiReader(cfg, mIn, mByteBuffer, mInputPtr, mInputEnd, mRecycleBuffer);
        } else if (normEnc.startsWith(CharsetNames.CS_UTF32)) {
            // let's augment with actual endianness info
            if (normEnc == CharsetNames.CS_UTF32) {
                mInputEncoding = mBigEndian ? CharsetNames.CS_UTF32BE : CharsetNames.CS_UTF32LE;
            }
            r = new UTF32Reader(cfg, mIn, mByteBuffer, mInputPtr, mInputEnd,
				mRecycleBuffer, mBigEndian);
        } else {
            // Nah, JDK needs to try it
            // Ok; first, do we need to merge stuff back?
            InputStream in = mIn;
            if (mInputPtr < mInputEnd) {
                in = new MergedStream(cfg, in, mByteBuffer, mInputPtr, mInputEnd);
            }
            /* 20-Jan-2006, TSa: Ok; although it is possible to declare
             *   stream as 'UTF-16', JDK may need help in figuring out
             *   the right order, so let's be explicit:
             */
            if (normEnc == CharsetNames.CS_UTF16) {
                mInputEncoding = normEnc = mBigEndian ? CharsetNames.CS_UTF16BE : CharsetNames.CS_UTF16LE;
            }
            try {
                return new InputStreamReader(in, normEnc);
            } catch (UnsupportedEncodingException usex) {
                throw new WstxIOException("Unsupported encoding: "+usex.getMessage());
            }
        }

        if (mXml11Handling) {
            r.setXmlCompliancy(XmlConsts.XML_V_11);
        }

        return r;
    }
    
    /**
     * Since this class only gets used when encoding is not explicitly
     * passed, need use the encoding that was auto-detected...
     */
    @Override
    public String getInputEncoding() {
        return mInputEncoding;
    }

    @Override
    public int getInputTotal() {
        int total = mInputProcessed + mInputPtr;
        if (mBytesPerChar > 1) {
            total /= mBytesPerChar;
        }
        return total;
    }

    @Override
    public int getInputColumn() {
        int col = mInputPtr - mInputRowStart;
        if (mBytesPerChar > 1) {
            col /= mBytesPerChar;
        }
        return col;
    }

    /*
    ////////////////////////////////////////
    // Internal methods, parsing
    ////////////////////////////////////////
    */

    /**
     * Method called to try to figure out physical encoding the underlying
     * input stream uses.
     */
    protected void resolveStreamEncoding()
        throws IOException, WstxException
    {
        // Let's first set defaults:
        mBytesPerChar = 0;
        mBigEndian = true;

        /* Ok; first just need 4 bytes for determining bytes-per-char from
         * BOM or first char(s) of likely xml declaration:
         */
        if (ensureLoaded(4)) {
            bomblock:
            do { // BOM/auto-detection block
                int quartet = (mByteBuffer[0] << 24)
                    | ((mByteBuffer[1] & 0xFF) << 16)
                    | ((mByteBuffer[2] & 0xFF) << 8)
                    | (mByteBuffer[3] & 0xFF);

                /* Handling of (usually) optional BOM (required for
                 * multi-byte formats); first 32-bit charsets:
                 */
                switch (quartet) {
                case 0x0000FEFF:
                    mBigEndian = true;
                    mInputPtr = mBytesPerChar = 4;
                    break bomblock;
                case 0xFFFE0000: // UCS-4, LE?
                    mInputPtr = mBytesPerChar = 4;
                    mBigEndian = false;
                    break bomblock;
                case 0x0000FFFE: // UCS-4, in-order...
                    reportWeirdUCS4("2143");
                    break bomblock;
                case 0x0FEFF0000: // UCS-4, in-order...
                    reportWeirdUCS4("3412");
                    break bomblock;
                }

                // Ok, if not, how about 16-bit encoding BOMs?
                int msw = quartet >>> 16;
                if (msw == 0xFEFF) { // UTF-16, BE
                    mInputPtr = mBytesPerChar = 2;
                    mBigEndian = true;
                    break;
                }
                if (msw == 0xFFFE) { // UTF-16, LE
                    mInputPtr = mBytesPerChar = 2;
                    mBigEndian = false;
                    break;
                }

                // And if not, then UTF-8 BOM?
                if ((quartet >>> 8) == 0xEFBBBF) { // UTF-8
                    mInputPtr = 3;
                    mBytesPerChar = 1;
                    mBigEndian = true; // doesn't really matter
                    break;
                }

                /* And if that wasn't succesful, how about auto-detection
                 * for '<?xm' (or subset for multi-byte encodings) marker?
                 */
                // Note: none of these consume bytes... so ptr remains at 0

                switch (quartet) {
                case 0x0000003c: // UCS-4, BE?
                    mBigEndian = true;
                    mBytesPerChar = 4;
                    break bomblock;
                case 0x3c000000: // UCS-4, LE?
                    mBytesPerChar = 4;
                    mBigEndian = false;
                    break bomblock;
                case 0x00003c00: // UCS-4, in-order...
                    reportWeirdUCS4("2143");
                    break bomblock;
                case 0x003c0000: // UCS-4, in-order...
                    reportWeirdUCS4("3412");
                    break bomblock;
                case 0x003c003f: // UTF-16, BE
                    mBytesPerChar = 2;
                    mBigEndian = true;
                    break bomblock;
                case 0x3c003f00: // UTF-16, LE
                    mBytesPerChar = 2;
                    mBigEndian = false;
                    break bomblock;
                case 0x3c3f786d: // UTF-8, Ascii, ISO-Latin
                    mBytesPerChar = 1;
                    mBigEndian = true; // doesn't really matter
                    break bomblock;

                case 0x4c6fa794:
                    mBytesPerChar = -1;
                    mEBCDIC = true;

                    /* For xml declaration handling we can basically
                     * use any of EBCDIC variants, since declaration
                     * must not contain control or punctuation characters
                     * that would differ
                     */
                    mSingleByteTranslation = EBCDICCodec.getCp037Mapping();
                    break bomblock;
                }
                
                /* Otherwise it's either single-byte doc without xml
                 * declaration, or corrupt input...
                 */
            } while (false); // BOM/auto-detection block
            
            mHadBOM = (mInputPtr > 0);

            // Let's update location markers to ignore BOM.
            mInputProcessed = -mInputPtr;
            mInputRowStart = mInputPtr;
        }

        /* Hmmh. If we haven't figured it out, let's just assume
         * UTF-8 as per XML specs:
         */
        mByteSizeFound = (mBytesPerChar != 0);
        if (!mByteSizeFound) {
            mBytesPerChar = 1;
            mBigEndian = true; // doesn't matter
        }
    }

    /**
     * @return Normalized encoding name
     */
    protected String verifyXmlEncoding(String enc)
        throws WstxException
    {
        enc = CharsetNames.normalize(enc);

        // Let's actually verify we got matching information:
        if (enc == CharsetNames.CS_UTF8) {
            verifyEncoding(enc, 1);
        } else if (enc == CharsetNames.CS_ISO_LATIN1) {
            verifyEncoding(enc, 1);
        } else if (enc == CharsetNames.CS_US_ASCII) {
            verifyEncoding(enc, 1);
        } else if (enc == CharsetNames.CS_UTF16) {
            // BOM is obligatory, to know the ordering
            /* 22-Mar-2005, TSa: Actually, since we don't have a
             *   custom decoder, so the underlying JDK Reader may
             *   have dealt with it transparently... so we can not
             *   really throw an exception here.
             */
            //if (!mHadBOM) {
            //reportMissingBOM(enc);
            //}
            verifyEncoding(enc, 2);
        } else if (enc == CharsetNames.CS_UTF16LE) {
            verifyEncoding(enc, 2, false);
        } else if (enc == CharsetNames.CS_UTF16BE) {
            verifyEncoding(enc, 2, true);

        } else if (enc == CharsetNames.CS_UTF32) {
            // Do we require a BOM here? we can live without it...
            //if (!mHadBOM) {
            //    reportMissingBOM(enc);
            //}
            verifyEncoding(enc, 4);
        } else if (enc == CharsetNames.CS_UTF32LE) {
            verifyEncoding(enc, 4, false);
        } else if (enc == CharsetNames.CS_UTF32BE) {
            verifyEncoding(enc, 4, true);
        }
        return enc;
    }

    /*
    /////////////////////////////////////////////////////
    // Internal methods, loading input data
    /////////////////////////////////////////////////////
    */

    protected boolean ensureLoaded(int minimum)
        throws IOException
    {
        /* Let's assume here buffer has enough room -- this will always
         * be true for the limited used this method gets
         */
        int gotten = (mInputEnd - mInputPtr);
        while (gotten < minimum) {
            int count = (mIn == null) ? -1 : mIn.read(mByteBuffer, mInputEnd, mByteBuffer.length - mInputEnd);
            if (count < 1) {
                return false;
            }
            mInputEnd += count;
            gotten += count;
        }
        return true;
    }

    protected void loadMore()
        throws IOException, WstxException
    {
        /* Need to make sure offsets are properly updated for error
         * reporting purposes, and do this now while previous amounts
         * are still known.
         */
        /* Note: at this point these are all in bytes, not chars (for multibyte
         * encodings)
         */
        mInputProcessed += mInputEnd;
        mInputRowStart -= mInputEnd;

        mInputPtr = 0;
        mInputEnd = (mIn == null) ? -1 : mIn.read(mByteBuffer, 0, mByteBuffer.length);
        if (mInputEnd < 1) {
            throw new WstxEOFException(ParsingErrorMsgs.SUFFIX_IN_XML_DECL,
                                       getLocation());
        }
    }

    /*
    /////////////////////////////////////////////////////
    // Implementations of abstract parsing methods
    /////////////////////////////////////////////////////
    */

    @Override
    protected void pushback() {
        if (mBytesPerChar < 0) {
            mInputPtr += mBytesPerChar;
        } else {
            mInputPtr -= mBytesPerChar;
        }
    }

    @Override
    protected int getNext()
        throws IOException, WstxException
    {
        if (mBytesPerChar != 1) {
            if (mBytesPerChar == -1) { // need to translate
                return nextTranslated();
            }
            return nextMultiByte();
        }
        byte b = (mInputPtr < mInputEnd) ?
            mByteBuffer[mInputPtr++] : nextByte();
        return (b & 0xFF);
    }

    @Override
    protected int getNextAfterWs(boolean reqWs)
        throws IOException, WstxException
    {
        int count;

        if (mBytesPerChar == 1) { // single byte
            count = skipSbWs();
        } else {
            if (mBytesPerChar == -1) { // translated
                count = skipTranslatedWs();
            } else { // multi byte
                count = skipMbWs();
            }
        }

        if (reqWs && count == 0) {
            reportUnexpectedChar(getNext(), ERR_XMLDECL_EXP_SPACE);
        }

        // inlined getNext()
        if (mBytesPerChar != 1) {
            if (mBytesPerChar == -1) { // translated
                return nextTranslated();
            }
            return nextMultiByte();
        }
        byte b = (mInputPtr < mInputEnd) ?
            mByteBuffer[mInputPtr++] : nextByte();
        return (b & 0xFF);
    }

    /**
     * @return First character that does not match expected, if any;
     *    CHAR_NULL if match succeeded
     */
    @Override
    protected int checkKeyword(String exp)
        throws IOException, WstxException
    {
        if (mBytesPerChar != 1) {
            if (mBytesPerChar == -1) {
                return checkTranslatedKeyword(exp);
            }
            return checkMbKeyword(exp);
        }
        return checkSbKeyword(exp);
    }

    @Override
    protected int readQuotedValue(char[] kw, int quoteChar)
        throws IOException, WstxException
    {
        int i = 0;
        int len = kw.length;
        boolean simple = (mBytesPerChar == 1);
        boolean mb = !simple && (mBytesPerChar > 1);

        while (i < len) {
            int c;

            if (simple) {
                byte b = (mInputPtr < mInputEnd) ?
                    mByteBuffer[mInputPtr++] : nextByte();
                if (b == BYTE_NULL) {
                    reportNull();
                }
                if (b == BYTE_CR || b == BYTE_LF) {
                    skipSbLF(b);
                    b = BYTE_LF;
                }
                c = (b & 0xFF);
            } else {
                if (mb) {
                    c = nextMultiByte();
                    if (c ==  CHAR_CR || c == CHAR_LF) {
                        skipMbLF(c);
                        c = CHAR_LF;
                    }
                } else {
                    c = nextTranslated();
                    if (c ==  CHAR_CR || c == CHAR_LF) {
                        skipTranslatedLF(c);
                        c = CHAR_LF;
                    }
                }
            }

            if (c == quoteChar) {
                return (i < len) ? i : -1;
            }

            if (i < len) {
                kw[i++] = (char) c;
            }
        }
        
        /* If we end up this far, we ran out of buffer space... let's let
         * caller figure that out, though
         */
        return -1;
    }

    protected boolean hasXmlDecl()
        throws IOException, WstxException
    {
        /* Separate handling for common and fast case; 1/variable byte
         * encodings that have ASCII subset:
         */
        if (mBytesPerChar == 1) {
            /* However... there has to be at least 6 bytes available; and if
             * so, can check the 'signature' easily:
             */
            if (ensureLoaded(6)) {
                if (mByteBuffer[mInputPtr] == '<'
                    && mByteBuffer[mInputPtr+1] == '?'
                    && mByteBuffer[mInputPtr+2] == 'x'
                    && mByteBuffer[mInputPtr+3] == 'm'
                    && mByteBuffer[mInputPtr+4] == 'l'
                    && ((mByteBuffer[mInputPtr+5] & 0xFF) <= CHAR_SPACE)) {

                    // Let's skip stuff so far:
                    mInputPtr += 6;
                    return true;
                }
            }
        } else if (mBytesPerChar == -1) { // translated (EBCDIC)
            if (ensureLoaded(6)) {
                int start = mInputPtr; // if we have to 'unread' chars
                if (nextTranslated() == '<'
                    && nextTranslated() == '?'
                    && nextTranslated() == 'x'
                    && nextTranslated() == 'm'
                    && nextTranslated() == 'l'
                    && nextTranslated() <= CHAR_SPACE) {
                    return true;
                }
                mInputPtr = start; // push data back
            }
        } else {
            // ... and then for slower fixed-multibyte encodings:

            // Is there enough data for checks?
            if (ensureLoaded (6 * mBytesPerChar)) {
                int start = mInputPtr; // if we have to 'unread' chars
                if (nextMultiByte() == '<'
                    && nextMultiByte() == '?'
                    && nextMultiByte() == 'x'
                    && nextMultiByte() == 'm'
                    && nextMultiByte() == 'l'
                    && nextMultiByte() <= CHAR_SPACE) {
                    return true;
                }
                mInputPtr = start; // push data back
            }
        }

        return false;
    }

    @Override
    protected Location getLocation()
    {
        /* Ok; for fixed-size multi-byte encodings, need to divide numbers
         * to get character locations. For variable-length encodings the
         * good thing is that xml declaration only uses shortest codepoints,
         * ie. char count == byte count.
         */
        int total = mInputProcessed + mInputPtr;
        int col = mInputPtr - mInputRowStart;

        if (mBytesPerChar > 1) {
            total /= mBytesPerChar;
            col /= mBytesPerChar;
        }

        return new WstxInputLocation(null, mPublicId, mSystemId,
                                     total - 1, // 0-based
                                     mInputRow, col);
    }

    /*
    /////////////////////////////////////////////////////
    // Internal methods, single-byte access methods
    /////////////////////////////////////////////////////
    */

    protected byte nextByte()
        throws IOException, WstxException
    {
        if (mInputPtr >= mInputEnd) {
            loadMore();
        }
        return mByteBuffer[mInputPtr++];
    }

    protected int skipSbWs()
        throws IOException, WstxException
    {
        int count = 0;

        while (true) {
            byte b = (mInputPtr < mInputEnd) ?
                mByteBuffer[mInputPtr++] : nextByte();

            if ((b & 0xFF) > CHAR_SPACE) {
                --mInputPtr;
                break;
            }
            if (b == BYTE_CR || b == BYTE_LF) {
                skipSbLF(b);
            } else if (b == BYTE_NULL) {
                reportNull();
            }
            ++count;
        }
        return count;
    }

    protected void skipSbLF(byte lfByte)
        throws IOException, WstxException
    {
        if (lfByte == BYTE_CR) {
            byte b = (mInputPtr < mInputEnd) ?
                mByteBuffer[mInputPtr++] : nextByte();
            if (b != BYTE_LF) {
                --mInputPtr; // pushback if not 2-char/byte lf
            }
        }
        ++mInputRow;
        mInputRowStart = mInputPtr;
    }

    /**
     * @return First character that does not match expected, if any;
     *    CHAR_NULL if match succeeded
     */
    protected int checkSbKeyword(String expected)
        throws IOException, WstxException
    {
        int len = expected.length();
        
        for (int ptr = 1; ptr < len; ++ptr) {
            byte b = (mInputPtr < mInputEnd) ?
                mByteBuffer[mInputPtr++] : nextByte();
            
            if (b == BYTE_NULL) {
                reportNull();
            }
            if ((b & 0xFF) != expected.charAt(ptr)) {
                return (b & 0xFF);
            }
        }

        return CHAR_NULL;
    }

    /*
    /////////////////////////////////////////////////////
    // Internal methods, multi-byte/translated access/checks
    /////////////////////////////////////////////////////
    */

    protected int nextMultiByte()
        throws IOException, WstxException
    {
        byte b = (mInputPtr < mInputEnd) ?
            mByteBuffer[mInputPtr++] : nextByte();
        byte b2 = (mInputPtr < mInputEnd) ?
            mByteBuffer[mInputPtr++] : nextByte();
        int c;

        if (mBytesPerChar == 2) {
            if (mBigEndian) {
                c = ((b & 0xFF) << 8) | (b2 & 0xFF);
            } else {
                c = (b & 0xFF) | ((b2 & 0xFF) << 8);
            }
        } else {
            // Has to be 4 bytes
            byte b3 = (mInputPtr < mInputEnd) ?
                mByteBuffer[mInputPtr++] : nextByte();
            byte b4 = (mInputPtr < mInputEnd) ?
                mByteBuffer[mInputPtr++] : nextByte();
            
            if (mBigEndian) {
                c = (b  << 24) | ((b2 & 0xFF) << 16)
                    | ((b3 & 0xFF) << 8) | (b4 & 0xFF);
            } else {
                c = (b4  << 24) | ((b3 & 0xFF) << 16)
                    | ((b2 & 0xFF) << 8) | (b & 0xFF);
            }
        }

        // Let's catch null chars early
        if (c == 0) {
            reportNull();
        }
        return c;
    }

    protected int nextTranslated()
        throws IOException, WstxException
    {
        byte b = (mInputPtr < mInputEnd) ?
            mByteBuffer[mInputPtr++] : nextByte();
        int ch = mSingleByteTranslation[b & 0xFF];
        if (ch < 0) { // special char... won't care for now
            ch = -ch;
        }
        return ch;
    }

    protected int skipMbWs()
        throws IOException, WstxException
    {
        int count = 0;

        while (true) {
            int c = nextMultiByte();

            if (c > CHAR_SPACE) {
                mInputPtr -= mBytesPerChar;
                break;
            }
            if (c == CHAR_CR || c == CHAR_LF) {
                skipMbLF(c);
            } else if (c == CHAR_NULL) {
                reportNull();
            }
            ++count;
        }
        return count;
    }

    protected int skipTranslatedWs()
        throws IOException, WstxException
    {
        int count = 0;

        while (true) {
            int c = nextTranslated();

            // Hmmh. Are we to accept NEL (0x85)?
            if (c > CHAR_SPACE && c != CHAR_NEL) {
                --mInputPtr;
                break;
            }
            if (c == CHAR_CR || c == CHAR_LF) {
                skipTranslatedLF(c);
            } else if (c == CHAR_NULL) {
                reportNull();
            }
            ++count;
        }
        return count;
    }

    protected void skipMbLF(int lf)
        throws IOException, WstxException
    {
        if (lf == CHAR_CR) {
            int c = nextMultiByte();
            if (c != CHAR_LF) {
                mInputPtr -= mBytesPerChar;
            }
        }
        ++mInputRow;
        mInputRowStart = mInputPtr;
    }

    protected void skipTranslatedLF(int lf)
        throws IOException, WstxException
    {
        if (lf == CHAR_CR) {
            int c = nextTranslated();
            if (c != CHAR_LF) {
                mInputPtr -= 1;
            }
        }
        ++mInputRow;
        mInputRowStart = mInputPtr;
    }

    /**
     * @return First character that does not match expected, if any;
     *    CHAR_NULL if match succeeded
     */
    protected int checkMbKeyword(String expected)
        throws IOException, WstxException
    {
        int len = expected.length();
        
        for (int ptr = 1; ptr < len; ++ptr) {
            int c = nextMultiByte();
            if (c == BYTE_NULL) {
                reportNull();
            }
            if (c != expected.charAt(ptr)) {
              return c;
            }
        }

        return CHAR_NULL;
    }

    protected int checkTranslatedKeyword(String expected)
        throws IOException, WstxException
    {
        int len = expected.length();
        
        for (int ptr = 1; ptr < len; ++ptr) {
            int c = nextTranslated();
            if (c == BYTE_NULL) {
                reportNull();
            }
            if (c != expected.charAt(ptr)) {
              return c;
            }
        }

        return CHAR_NULL;
    }

    /*
    ////////////////////////////////////////
    // Other private methods:
    ////////////////////////////////////////
    */

    private void verifyEncoding(String id, int bpc)
        throws WstxException
    {
        if (mByteSizeFound) {
            /* Let's verify that if we matched an encoding, it's the same
             * as what was declared...
             */
            if (bpc != mBytesPerChar) {
                // [WSTX-138]: Needs to detect EBCDIC discrepancy
                if (mEBCDIC) {
                    reportXmlProblem("Declared encoding '"+id+"' incompatible with auto-detected physical encoding (EBCDIC variant), can not decode input since actual code page not known");
                }
                reportXmlProblem("Declared encoding '"+id+"' uses "+bpc
                                 +" bytes per character; but physical encoding appeared to use "+mBytesPerChar+"; cannot decode");
            }
        }
    }

    private void verifyEncoding(String id, int bpc, boolean bigEndian)
        throws WstxException
    {
        if (mByteSizeFound) {
            verifyEncoding(id, bpc);

            if (bigEndian != mBigEndian) {
                String bigStr = bigEndian ? "big" : "little";
                reportXmlProblem
                    ("Declared encoding '"+id+"' has different endianness ("
                     +bigStr+" endian) than what physical ordering appeared to be; cannot decode");
            }
        }
    }

    private void reportWeirdUCS4(String type)
        throws IOException
    {
        throw new CharConversionException("Unsupported UCS-4 endianness ("+type+") detected");
    }

    /*
    private void reportMissingBOM(String enc)
        throws WstxException
    {
        throw new WstxException("Missing BOM for encoding '"+enc+"'; can not be omitted",
                                getLocation());
    }
    */
}
