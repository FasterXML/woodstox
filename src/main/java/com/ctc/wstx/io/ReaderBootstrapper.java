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
import java.text.MessageFormat;

import javax.xml.stream.Location;
import javax.xml.stream.XMLReporter;
import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.validation.XMLValidationProblem;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.cfg.ErrorConsts;
import com.ctc.wstx.cfg.ParsingErrorMsgs;
import com.ctc.wstx.exc.*;
import com.ctc.wstx.util.StringUtil;

/**
 * Input bootstrap class used when input comes from a Reader; in this case,
 * encoding is already known, and thus encoding from XML declaration (if
 * any) is only double-checked, not really used.
 *<p>
 * Note: since the actual Reader to use after bootstrapping is pre-constructed,
 * the local input buffer can (and should) be quite small.
 */
public final class ReaderBootstrapper
    extends InputBootstrapper
{
    final static char CHAR_BOM_MARKER = (char) 0xFEFF;

    /*
    ////////////////////////////////////////
    // Configuration
    ////////////////////////////////////////
    */

    /**
     * Underlying Reader to use for reading content.
     */
    final Reader mIn;

    /**
     * Encoding identifier processing application passed in; if not null,
     * will be compared to actual xml declaration based encoding (if
     * declaration found)
     */
    final String mInputEncoding;

    /*
    ///////////////////////////////////////////////////////////////
    // Input buffering
    ///////////////////////////////////////////////////////////////
    */

    private char[] mCharBuffer;

    private int mInputPtr;

    private int mInputEnd;

    /*
    ////////////////////////////////////////
    // Life-cycle
    ////////////////////////////////////////
    */

    private ReaderBootstrapper(String pubId, SystemId sysId, Reader r, String appEncoding)
    {
        super(pubId, sysId);
        mIn = r;
        if (appEncoding == null) { // may still be able to figure it out
            if (r instanceof InputStreamReader) {
                appEncoding = ((InputStreamReader) r).getEncoding();
            }
        }
        mInputEncoding = appEncoding;
    }

    /*
    ////////////////////////////////////////
    // Public API
    ////////////////////////////////////////
    */

    /**
     * @param r Eventual reader that will be reading actual content, after
     *   bootstrapping finishes
     * @param appEncoding Encoding that application declared; may be null.
     *   If not null, will be compared to actual declaration found; and
     *   incompatibility reported as a potential (but not necessarily fatal)
     *   problem.
     */
    public static ReaderBootstrapper getInstance(String pubId, SystemId sysId,
    		Reader r, String appEncoding)
    {
        return new ReaderBootstrapper(pubId, sysId, r, appEncoding);
    }

    /**
     * Method called to do actual bootstrapping.
     *
     * @return Actual reader to use for reading xml content
     */
    @Override
    public Reader bootstrapInput(ReaderConfig cfg, boolean mainDoc, int xmlVersion)
        throws IOException, XMLStreamException
    {
        /* First order of business: allocate input buffer. Not done during
         * construction for simplicity; that way config object need not be
         * passed before actual bootstrap method is called
         */
        /* Let's make sure buffer is at least 6 chars (to know '<?xml '
         * prefix), and preferably big enough to contain the whole declaration,
         *  but not too long to waste space -- it won't be reused
         * by the real input reader.
         */
        mCharBuffer = (cfg == null) ? new char[128] : cfg.allocSmallCBuffer(128); // 128 chars should be enough

        initialLoad(7);

        /* Only need 6 for signature ("<?xml\s"), but there may be a leading
         * BOM in there... and a valid xml declaration has to be longer
         * than 7 chars anyway (although, granted, shortest valid xml docl
         * is just 4 chars... "<a/>")
         */
        if (mInputEnd >= 7) {
            char c = mCharBuffer[mInputPtr];
            
            // BOM to skip?
            if (c == CHAR_BOM_MARKER) {
                c = mCharBuffer[++mInputPtr];
            }
            if (c == '<') {
                if (mCharBuffer[mInputPtr+1] == '?'
                    && mCharBuffer[mInputPtr+2] == 'x'
                    && mCharBuffer[mInputPtr+3] == 'm'
                    && mCharBuffer[mInputPtr+4] == 'l'
                    && mCharBuffer[mInputPtr+5] <= CHAR_SPACE) {
                    // Yup, got the declaration ok!
                    mInputPtr += 6; // skip declaration
                    readXmlDecl(mainDoc, xmlVersion);
                    
                    if (mFoundEncoding != null && mInputEncoding != null) {
                        verifyXmlEncoding(cfg);
                    }
                }
            } else {
                /* We may also get something that would be invalid xml
                 * ("garbage" char; neither '<' nor space). If so, and
                 * it's one of "well-known" cases, we can not only throw
                 * an exception but also indicate a clue as to what is likely
                 * to be wrong.
                 */
                /* Specifically, UTF-8 read via, say, ISO-8859-1 reader, can
                 * "leak" marker (0xEF, 0xBB, 0xBF). While we could just eat
                 * it, there's bound to be other problems cropping up, so let's
                 * inform about the problem right away.
                 */
                if (c == 0xEF) {
                    throw new WstxIOException("Unexpected first character (char code 0xEF), not valid in xml document: could be mangled UTF-8 BOM marker. Make sure that the Reader uses correct encoding or pass an InputStream instead");
                }
            }
        }
 
        /* Ok, now; do we have unused chars we have read that need to
         * be merged in?
         */
        if (mInputPtr < mInputEnd) {
            return new MergedReader(cfg, mIn, mCharBuffer, mInputPtr, mInputEnd);
        }

        return mIn;
    }

    @Override
    public String getInputEncoding() {
        return mInputEncoding;
    }

    @Override
    public int getInputTotal() {
        return mInputProcessed + mInputPtr;
    }

    @Override
    public int getInputColumn() {
        return (mInputPtr - mInputRowStart);
    }

    /*
    ////////////////////////////////////////
    // Internal methods, parsing
    ////////////////////////////////////////
    */

    protected void verifyXmlEncoding(ReaderConfig cfg)
        throws XMLStreamException
    {
        String inputEnc = mInputEncoding;

        // Close enough?
        if (StringUtil.equalEncodings(inputEnc, mFoundEncoding)) {
            return;
        }

        /* Ok, maybe the difference is just with endianness indicator?
         * (UTF-16BE vs. UTF-16)?
         */
        // !!! TBI

        XMLReporter rep = cfg.getXMLReporter();
        if (rep != null) {
            Location loc = getLocation();
            String msg = MessageFormat.format(ErrorConsts.W_MIXED_ENCODINGS,
                                              new Object[] { mFoundEncoding,
                                                             inputEnc });
            String type = ErrorConsts.WT_XML_DECL;
            /* 30-May-2008, tatus: Should wrap all the info as XMValidationProblem
             *    since that's Woodstox' contract wrt. relatedInformation field.
             */
            XMLValidationProblem prob = new XMLValidationProblem(loc, msg, XMLValidationProblem.SEVERITY_WARNING, type);
            rep.report(msg, type, prob, loc);
        }
    }

    /*
    /////////////////////////////////////////////////////
    // Internal methods, loading input data
    /////////////////////////////////////////////////////
    */

    protected boolean initialLoad(int minimum)
        throws IOException
    {
        mInputPtr = 0;
        mInputEnd = 0;

        while (mInputEnd < minimum) {
            int count = mIn.read(mCharBuffer, mInputEnd,
                                 mCharBuffer.length - mInputEnd);
            if (count < 1) {
                return false;
            }
            mInputEnd += count;
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
        mInputProcessed += mInputEnd;
        mInputRowStart -= mInputEnd;

        mInputPtr = 0;
        mInputEnd = mIn.read(mCharBuffer, 0, mCharBuffer.length);
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
        --mInputPtr;
    }

    @Override
    protected int getNext()
        throws IOException, WstxException
    {
        return (mInputPtr < mInputEnd) ?
            mCharBuffer[mInputPtr++] : nextChar();
    }

    @Override
    protected int getNextAfterWs(boolean reqWs)
        throws IOException, WstxException
    {
        int count = 0;

        while (true) {
            char c = (mInputPtr < mInputEnd) ?
                mCharBuffer[mInputPtr++] : nextChar();

            if (c > CHAR_SPACE) {
                if (reqWs && count == 0) {
                    reportUnexpectedChar(c, ERR_XMLDECL_EXP_SPACE);
                }
                return c;
            }
            if (c == CHAR_CR || c == CHAR_LF) {
                skipCRLF(c);
            } else if (c == CHAR_NULL) {
                reportNull();
            }
            ++count;
        }
    }

    /**
     * @return First character that does not match expected, if any;
     *    CHAR_NULL if match succeeded
     */
    @Override
    protected int checkKeyword(String exp)
        throws IOException, WstxException
    {
        int len = exp.length();
        
        for (int ptr = 1; ptr < len; ++ptr) {
            char c = (mInputPtr < mInputEnd) ?
                mCharBuffer[mInputPtr++] : nextChar();
            
            if (c != exp.charAt(ptr)) {
                return c;
            }
            if (c == CHAR_NULL) {
                reportNull();
            }
        }

        return CHAR_NULL;
    }

    @Override
    protected int readQuotedValue(char[] kw, int quoteChar)
        throws IOException, WstxException
    {
        int i = 0;
        int len = kw.length;

        while (true) {
            char c = (mInputPtr < mInputEnd) ?
                mCharBuffer[mInputPtr++] : nextChar();
            if (c == CHAR_CR || c == CHAR_LF) {
                skipCRLF(c);
            } else if (c == CHAR_NULL) {
                reportNull();
            }
            if (c == quoteChar) {
                return (i < len) ? i : -1;
            }
	    // Let's just truncate longer values, but match quote
	    if (i < len) {
		kw[i++] = c;
	    }
	}
    }

    @Override
    protected Location getLocation()
    {
        return new WstxInputLocation(null, mPublicId, mSystemId,
        		mInputProcessed + mInputPtr - 1,
        		mInputRow, mInputPtr - mInputRowStart);
    }

    /*
    /////////////////////////////////////////////////////
    // Internal methods, single-byte access methods
    /////////////////////////////////////////////////////
    */

    protected char nextChar()
        throws IOException, WstxException
    {
        if (mInputPtr >= mInputEnd) {
            loadMore();
        }
        return mCharBuffer[mInputPtr++];
    }

    protected void skipCRLF(char lf)
        throws IOException, WstxException
    {
        if (lf == CHAR_CR) {
            char c = (mInputPtr < mInputEnd) ?
                mCharBuffer[mInputPtr++] : nextChar();
            if (c != BYTE_LF) {
                --mInputPtr; // pushback if not 2-char/byte lf
            }
        }
        ++mInputRow;
        mInputRowStart = mInputPtr;
    }
}
