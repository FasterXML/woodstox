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

package com.ctc.wstx.dtd;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.ent.EntityDecl;
import com.ctc.wstx.io.WstxInputData;
import com.ctc.wstx.io.WstxInputSource;
import com.ctc.wstx.sr.StreamScanner;

/**
 * Minimal DTD reader implementation that only knows how to skip
 * internal DTD subsets.
 */
public class MinimalDTDReader
    extends StreamScanner
{
    /*
    //////////////////////////////////////////////////
    // Configuration
    //////////////////////////////////////////////////
     */

    /**
     * True, when reading external subset, false when reading internal
     * subset.
     */
    final boolean mIsExternal;

    /*
    //////////////////////////////////////////////////
    // Life-cycle
    //////////////////////////////////////////////////
     */

    /**
     * Constructor used for reading/skipping internal subset.
     */
    private MinimalDTDReader(WstxInputSource input, ReaderConfig cfg)
    {
        this(input, cfg, false);
    }

    /**
     * Common initialization part of int/ext subset constructors.
     */
    protected MinimalDTDReader(WstxInputSource input, ReaderConfig cfg,
                               boolean isExt)
    {
        super(input, cfg, cfg.getDtdResolver());
        mIsExternal = isExt;
        /* And let's force expansion (matters mostly/only for undefined
         * entities)
         */
        mCfgReplaceEntities = true;
    }

    /**
     * Method that just skims
     * through structure of internal subset, but without doing any sort
     * of validation, or parsing of contents. Method may still throw an
     * exception, if skipping causes EOF or there's an I/O problem.
     *
     * @param srcData Link back to the input buffer shared with the owning
     *    stream reader.
     */
    public static void skipInternalSubset(WstxInputData srcData, WstxInputSource input,
                                          ReaderConfig cfg)
        throws XMLStreamException
    {
        MinimalDTDReader r = new MinimalDTDReader(input, cfg);
        // Need to read from same source as the master (owning stream reader)
        r.copyBufferStateFrom(srcData);
        try {
            r.skipInternalSubset();
        } finally {
            /* And then need to restore changes back to srcData (line nrs etc);
             * effectively means that we'll stop reading internal DTD subset,
             * if so.
             */
            srcData.copyBufferStateFrom(r);
        }
    }

    /*
    //////////////////////////////////////////////////
    // Abstract methods from StreamScanner
    //////////////////////////////////////////////////
     */

    /**
     * What DTD reader returns doesn't really matter, so let's just return
     * perceived start location (different from what stream readers actually
     * do)
     */
    @Override
    public final Location getLocation() {
        return getStartLocation();
    }

    @Override
    protected EntityDecl findEntity(String id, Object arg) {
        throwIllegalCall();
        return null; // never gets here but javac needs it
    }

    /**
     * This is a VC, not WFC, nothing to do when skipping through
     * DTD in non-supporting mode.
     */
    @Override
    protected void handleUndeclaredEntity(String id)
        throws XMLStreamException
    {
        // nothing to do...
    }

    /**
     * Since improper entity/PE nesting is VC, not WFC, let's not
     * react to this failure at all when only skipping the DTD subset.
     */
    @Override
    protected void handleIncompleteEntityProblem(WstxInputSource closing)
        throws XMLStreamException
    {
        // nothing to do...
    }

    protected char handleExpandedSurrogate(char first, char second)
    {
        // should we throw an exception?
        return first;
    }

    /*
    //////////////////////////////////////////////////
    // Internal API
    //////////////////////////////////////////////////
     */

    /**
     * Method that may need to be called by attribute default value
     * validation code, during parsing....
     *<p>
     * 03-Dec-2004, TSa: This is not particularly elegant: should be
     * able to pass the information some other way. But for now it
     * works and is necessary.
     */
    public EntityDecl findEntity(String entName) {
        return null;
    }

    /*
    //////////////////////////////////////////////////
    // Main-level skipping method(s)
    //////////////////////////////////////////////////
     */

    /**
     * Method that will skip through internal DTD subset, without doing
     * any parsing, except for trying to match end of subset properly.
     */
    protected void skipInternalSubset()
        throws XMLStreamException
    {
        while (true) {
            int i = getNextAfterWS();
            if (i < 0) {
                // Error for internal subset
                throwUnexpectedEOF(SUFFIX_IN_DTD_INTERNAL);
            }
            if (i == '%') { // parameter entity
                skipPE();
                continue;
            }
            if (i == '<') {
                /* Let's determine type here, and call appropriate skip
                 * methods.
                 */
                char c = getNextSkippingPEs();
                if (c == '?') { // xml decl?
                    /* Not sure if PIs are really allowed in DTDs, but let's
                     * just allow them until proven otherwise. XML declaration
                     * is legal in the beginning, anyhow
                     */
                    skipPI();
                } else if (c == '!') { // ignore/include, comment, declaration?
                    c = getNextSkippingPEs();
                    if (c == '[') {
                        /* Shouldn't really get these, as they are not allowed
                         * in the internal subset? So let's just leave it
                         * as is, and see what happens. :-)
                         */
                        ;
                    } else if (c == '-') { // plain comment
                        skipComment();
                    } else if (c >= 'A' && c <= 'Z') {
                        skipDeclaration(c);
                    } else {
                        /* Hmmh, let's not care too much; but we need to try
                         * to match the closing gt-char nonetheless?
                         */
                        skipDeclaration(c);
                    }
                } else {
                    /* Shouldn't fail (since we are to completely ignore
                     * subset); let's just push it back and continue.
                     */
                    --mInputPtr;
                }
                continue;
            }

            if (i == ']') {
                // Int. subset has no conditional sections, has to be the end...
                /* 18-Jul-2004, TSa: Let's just make sure it happened
                 *   in the main input source, not at external entity...
                 */
                if (mInput != mRootInput) {
                        throwParseError("Encountered int. subset end marker ']]>' in an expanded entity; has to be at main level.");
                }
                // End of internal subset
                break;
            }
            throwUnexpectedChar(i, SUFFIX_IN_DTD_INTERNAL+"; expected a '<' to start a directive, or \"]>\" to end internal subset.");
        }
    }

    /*
    //////////////////////////////////////////////////
    // Internal methods, input access:
    //////////////////////////////////////////////////
     */

    protected char dtdNextFromCurr()
        throws XMLStreamException
    {
        return (mInputPtr < mInputEnd) ?
            mInputBuffer[mInputPtr++] : getNextCharFromCurrent(getErrorMsg());
    }

    protected char dtdNextChar()
        throws XMLStreamException
    {
        return (mInputPtr < mInputEnd) ?
            mInputBuffer[mInputPtr++] : getNextChar(getErrorMsg());
    }

    protected char getNextSkippingPEs()
        throws XMLStreamException
    {
        while (true) {
            char c = (mInputPtr < mInputEnd) ?
                mInputBuffer[mInputPtr++] : getNextChar(getErrorMsg());
            if (c != '%') {
                return c;
            }
            skipPE();
        }
    }

    /*
    //////////////////////////////////////////////////
    // Internal methods, skipping:
    //////////////////////////////////////////////////
     */

    private void skipPE()
        throws XMLStreamException
    {
        skipDTDName();
        /* Should now get semicolon... let's try to find and skip it; but
         * if none found, let's not throw an exception -- we are just skipping
         * internal subset here.
         */
        char c = (mInputPtr < mInputEnd) ?
            mInputBuffer[mInputPtr++] : dtdNextFromCurr();
        if (c != ';') {
            --mInputPtr;
        }
    }

    protected void skipComment()
        throws XMLStreamException
    {
        skipCommentContent();
        // Now, we may be getting end mark; first need second marker char:.
        char c = (mInputPtr < mInputEnd)
            ? mInputBuffer[mInputPtr++] : dtdNextFromCurr();
        if (c != '>') {
            throwParseError("String '--' not allowed in comment (missing '>'?)");
        }
    }

    protected void skipCommentContent()
        throws XMLStreamException
    {
        while (true) {
            char c = (mInputPtr < mInputEnd) ?
                mInputBuffer[mInputPtr++] : dtdNextFromCurr();
            if (c == '-') {
                c = (mInputPtr < mInputEnd) ?
                    mInputBuffer[mInputPtr++] : dtdNextFromCurr();
                if (c == '-') {
                    return;
                }
            } else if (c == '\n' || c == '\r') {
                skipCRLF(c);
            }
        }
    }

    protected void skipPI()
        throws XMLStreamException
    {
        while (true) {
            char c = (mInputPtr < mInputEnd)
                ? mInputBuffer[mInputPtr++] : dtdNextFromCurr();
            if (c == '?') {
                do {
                    c = (mInputPtr < mInputEnd)
                        ? mInputBuffer[mInputPtr++] : dtdNextFromCurr();
                } while (c == '?');
                if (c == '>') {
                    break;
                }
            }
            if (c == '\n' || c == '\r') {
                skipCRLF(c);
            }
        }
    }

    private void skipDeclaration(char c)
        throws XMLStreamException
    {
        while (c != '>') {
            c = (mInputPtr < mInputEnd)
                ? mInputBuffer[mInputPtr++] : dtdNextFromCurr();
            if (c == '\n' || c == '\r') {
                skipCRLF(c);
                /* No need for specific handling for PE refs; they just have
                 * identifier that'll get properly skipped.
                 */
                /* 17-Jul-2004, TSa: But we do need to properly handle literals;
                 *   it is possible to add '>' char in entity expansion values.
                 */
            } else if (c == '\'' || c == '"') {
                skipLiteral(c);
            }
        }
    }

    private void skipLiteral(char quoteChar)
        throws XMLStreamException
    {
        while (true) {
            char c = (mInputPtr < mInputEnd)
                ? mInputBuffer[mInputPtr++] : dtdNextFromCurr();
            if (c == '\n' || c == '\r') {
                skipCRLF(c);
            } else if (c == quoteChar) {
                break;
            }
            /* No need for specific handling for PE refs, should be ignored
             * just ok (plus they need to properly nested in any case)
             */
        }
    }

    private void skipDTDName()
        throws XMLStreamException
    {
        /*int len =*/ skipFullName(getNextChar(getErrorMsg()));
        /* Should we give an error about missing name? For now,
         * let's just exit.
         */
    }

    /*
    //////////////////////////////////////////////////
    // Internal methods, error handling:
    //////////////////////////////////////////////////
     */

    protected String getErrorMsg() {
        return mIsExternal ? SUFFIX_IN_DTD_EXTERNAL : SUFFIX_IN_DTD_INTERNAL;
    }


    protected void throwIllegalCall()
        throws Error
    {
        throw new IllegalStateException("Internal error: this method should never be called");
    }
}

