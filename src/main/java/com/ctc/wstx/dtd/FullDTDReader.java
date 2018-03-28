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

import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.text.MessageFormat;
import java.util.*;

import javax.xml.stream.Location;
import javax.xml.stream.XMLReporter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.NotationDeclaration;

import org.codehaus.stax2.validation.XMLValidationProblem;
import org.codehaus.stax2.validation.XMLValidator;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.cfg.ErrorConsts;
import com.ctc.wstx.cfg.XmlConsts;
import com.ctc.wstx.ent.*;
import com.ctc.wstx.evt.WNotationDeclaration;
import com.ctc.wstx.exc.WstxIOException;
import com.ctc.wstx.io.WstxInputData;
import com.ctc.wstx.io.WstxInputSource;
import com.ctc.wstx.util.*;

/**
 * Reader that reads in DTD information from internal or external subset.
 *<p>
 * There are 2 main modes for DTDReader, depending on whether it is parsing
 * internal or external subset. Parsing of internal subset is somewhat
 * simpler, since no dependency checking is needed. For external subset,
 * handling of parameter entities is bit more complicated, as care has to
 * be taken to distinguish between using PEs defined in int. subset, and
 * ones defined in ext. subset itself. This determines cachability of
 * external subsets.
 *<p>
 * Reader also implements simple stand-alone functionality for flattening
 * DTD files (expanding all references to their eventual textual form);
 * this is sometimes useful when optimizing modularized DTDs
 * (which are more maintainable) into single monolithic DTDs (which in
 * general can be more performant).
 *
 * @author Tatu Saloranta
 */
public class FullDTDReader
    extends MinimalDTDReader
{
    /**
     * Flag that can be changed to enable or disable interning of shared
     * names; shared names are used for enumerated values to reduce
     * memory usage.
     */
    final static boolean INTERN_SHARED_NAMES = false;

    // // // Entity expansion types:

    final static Boolean ENTITY_EXP_GE = Boolean.FALSE;

    final static Boolean ENTITY_EXP_PE = Boolean.TRUE;

    /*
    ///////////////////////////////////////////////////////////
    // Configuration
    ///////////////////////////////////////////////////////////
     */

    final int mConfigFlags;

    // Extracted wstx-specific settings:

    final boolean mCfgSupportDTDPP;

    /**
     * This flag indicates whether we should build a validating 'real'
     * validator (true, the usual case),
     * or a simpler pseudo-validator that can do all non-validation tasks
     * that are based on DTD info (entity expansion, notation references,
     * default attribute values). Latter is used in non-validating mode.
     *<p>
     */
    final boolean mCfgFullyValidating;

    /*
    ///////////////////////////////////////////////////////////
    // Entity handling, parameter entities (PEs)
    ///////////////////////////////////////////////////////////
     */

    /**
     * Set of parameter entities defined so far in the currently parsed
     * subset. Note: the first definition sticks, entities can not be
     * redefined.
     *<p>
     * Keys are entity name Strings; values are instances of EntityDecl
     */
    HashMap<String,EntityDecl> mParamEntities;

    /**
     * Set of parameter entities already defined for the subset being
     * parsed; namely, PEs defined in the internal subset passed when
     * parsing matching external subset. Null when parsing internal
     * subset.
     */
    final HashMap<String,EntityDecl> mPredefdPEs;

    /**
     * Set of parameter entities (ids) that have been referenced by this
     * DTD; only maintained for external subsets, and only as long as
     * no pre-defined PE has been referenced.
     */
    Set<String> mRefdPEs;

    /*
    ///////////////////////////////////////////////////////////
    // Entity handling, general entities (GEs)
    ///////////////////////////////////////////////////////////
     */

    /**
     * Set of generic entities defined so far in this subset.
     * As with parameter entities, the first definition sticks.
     *<p>
     * Keys are entity name Strings; values are instances of EntityDecl
     *<p>
     * Note: this Map only contains entities declared and defined in the
     * subset being parsed; no previously defined values are passed.
     */
    HashMap<String,EntityDecl> mGeneralEntities;

    /**
     * Set of general entities already defined for the subset being
     * parsed; namely, PEs defined in the internal subset passed when
     * parsing matching external subset. Null when parsing internal
     * subset. Such entities are only needed directly for one purpose;
     * to be expanded when reading attribute default value definitions.
     */
    final HashMap<String,EntityDecl> mPredefdGEs;

    /**
     * Set of general entities (ids) that have been referenced by this
     * DTD; only maintained for external subsets, and only as long as
     * no pre-defined GEs have been referenced.
     */
    Set<String> mRefdGEs;

    /*
    ///////////////////////////////////////////////////////////
    // Entity handling, both PEs and GEs
    ///////////////////////////////////////////////////////////
     */

    /**
     * Flag used to keep track of whether current (external) subset
     * has referenced at least one PE that was pre-defined.
     */
    boolean mUsesPredefdEntities = false;

    /*
    ///////////////////////////////////////////////////////////
    // Notation settings
    ///////////////////////////////////////////////////////////
     */

    /**
     * Set of notations defined so far. Since it's illegal to (try to)
     * redefine notations, there's no specific precedence.
     *<p>
     * Keys are entity name Strings; values are instances of
     * NotationDecl objects
     */
    HashMap<String,NotationDeclaration> mNotations;

    /**
     * Notations already parsed before current subset; that is,
     * notations from the internal subset if we are currently
     * parsing matching external subset.
     */
    final HashMap<String,NotationDeclaration> mPredefdNotations;

    /**
     * Flag used to keep track of whether current (external) subset
     * has referenced at least one notation that was defined in internal
     * subset. If so, can not cache the external subset
     */
    boolean mUsesPredefdNotations = false;

    /**
     * Finally, we need to keep track of Notation references that were
     * made prior to declaration. This is needed to ensure that all
     * references can be properly resolved.
     */
    HashMap<String,Location> mNotationForwardRefs;

    /*
    ///////////////////////////////////////////////////////////
    // Element specifications
    ///////////////////////////////////////////////////////////
     */

    /**
     * Map used to shared PrefixedName instances, to reduce memory usage
     * of (qualified) element and attribute names
     */
    HashMap<PrefixedName,PrefixedName> mSharedNames = null;

    /**
     * Contains definition of elements and matching content specifications.
     * Also contains temporary placeholders for elements that are indirectly
     * "created" by ATTLIST declarations that precede actual declaration
     * for the ELEMENT referred to.
     */
    LinkedHashMap<PrefixedName,DTDElement> mElements;

    /**
     * Map used for sharing legal enumeration values; used since oftentimes
     * same enumeration values are used with multiple attributes
     */
    HashMap<String,String> mSharedEnumValues = null;

    /*
    ///////////////////////////////////////////////////////////
    // Entity expansion state
    ///////////////////////////////////////////////////////////
     */

    /**
     * This is the attribute default value that is currently being parsed.
     * Needs to be a global member due to the way entity expansion failures
     * are reported: problems need to be attached to this object, even
     * thought the default value itself will not be passed through.
     */
    DefaultAttrValue mCurrAttrDefault = null;

    /**
     * Flag that indicates if the currently expanding (or last expanded)
     * entity is a Parameter Entity or General Entity.
     */
    boolean mExpandingPE = false;

    /**
     * Text buffer used for constructing expansion value of the internal
     * entities, and for default attribute values.
     * Lazily constructed when needed, reused.
     */
    TextBuffer mValueBuffer = null;

    /*
    ///////////////////////////////////////////////////////////
    // Reader state
    ///////////////////////////////////////////////////////////
     */

    /**
     * Nesting count for conditionally included sections; 0 means that
     * we are not inside such a section. Note that condition ignore is
     * handled separately.
     */
    int mIncludeCount = 0;

    /**
     * This flag is used to catch uses of PEs in the internal subset
     * within declarations (full declarations are ok, but not other types)
     */
    boolean mCheckForbiddenPEs = false;

    /**
     * Keyword of the declaration being currently parsed (if any). Can be
     * used for error reporting purposes.
     */
    String mCurrDeclaration;

    /*
    ///////////////////////////////////////////////////////////
    // DTD++ support information
    ///////////////////////////////////////////////////////////
     */

    /**
     * Flag that indicates if any DTD++ features have been encountered
     * (in DTD++-supporting mode).
     */
    boolean mAnyDTDppFeatures = false;

    /**
     * Currently active default namespace URI.
     */
    String mDefaultNsURI = "";

    /**
     * Prefix-to-NsURI mappings for this DTD, if any: lazily
     * constructed when needed
     */
    HashMap<String,String> mNamespaces = null;

    /*
    ///////////////////////////////////////////////////////////
    // Additional support for creating expanded output
    // of processed DTD.
    ///////////////////////////////////////////////////////////
     */

    DTDWriter mFlattenWriter = null;

    /*
    ///////////////////////////////////////////////////////////
    // Support for SAX API impl:
    ///////////////////////////////////////////////////////////
     */

    final DTDEventListener mEventListener;

    transient TextBuffer mTextBuffer = null;

    /*
    ///////////////////////////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////////////////////////
     */

    /**
     * Constructor used for reading/skipping internal subset.
     */
    private FullDTDReader(WstxInputSource input, ReaderConfig cfg,
                          boolean constructFully, int xmlVersion)
    {
        this(input, cfg, false, null, constructFully, xmlVersion);
    }

    /**
     * Constructor used for reading external subset.
     */
    private FullDTDReader(WstxInputSource input, ReaderConfig cfg, 
                          DTDSubset intSubset,
                          boolean constructFully, int xmlVersion)
    {
        this(input, cfg, true, intSubset, constructFully, xmlVersion);

        // Let's make sure line/col offsets are correct...
        input.initInputLocation(this, mCurrDepth, 0);
    }

    /**
     * Common initialization part of int/ext subset constructors.
     */
    private FullDTDReader(WstxInputSource input, ReaderConfig cfg,
                          boolean isExt, DTDSubset intSubset,
                          boolean constructFully, int xmlVersion)
    {
        super(input, cfg, isExt);
        /* What matters here is what the main xml doc had; that determines
         * xml conformance level to use.
         */
        mDocXmlVersion = xmlVersion;
        mXml11 = cfg.isXml11();
        int cfgFlags = cfg.getConfigFlags();
        mConfigFlags = cfgFlags;
        mCfgSupportDTDPP = (cfgFlags & CFG_SUPPORT_DTDPP) != 0;
        mCfgFullyValidating = constructFully;

        mUsesPredefdEntities = false;
        mParamEntities = null;
        mRefdPEs = null;
        mRefdGEs = null;
        mGeneralEntities = null;

        // Did we get any existing parameter entities?
        HashMap<String,EntityDecl> pes = (intSubset == null) ?
            null : intSubset.getParameterEntityMap();
        if (pes == null || pes.isEmpty()) {
            mPredefdPEs = null;
        } else {
            mPredefdPEs = pes;
        }

        // How about general entities (needed only for attr. def. values)
        HashMap<String,EntityDecl> ges = (intSubset == null) ?
            null : intSubset.getGeneralEntityMap();
        if (ges == null || ges.isEmpty()) {
            mPredefdGEs = null;
        } else {
            mPredefdGEs = ges;
        }

        // And finally, notations
        HashMap<String,NotationDeclaration> not = (intSubset == null) ?
            null : intSubset.getNotationMap();
        if (not == null || not.isEmpty()) {
            mPredefdNotations = null;
        } else {
            mPredefdNotations = not;
        }
        mEventListener = mConfig.getDTDEventListener();
    }

    /**
     * Method called to read in the internal subset definition.
     */
    public static DTDSubset readInternalSubset(WstxInputData srcData,
                                               WstxInputSource input,
                                               ReaderConfig cfg,
                                               boolean constructFully,
                                               int xmlVersion)
        throws XMLStreamException
    {
        FullDTDReader r = new FullDTDReader(input, cfg, constructFully, xmlVersion);
        // Need to read using the same low-level reader interface:
        r.copyBufferStateFrom(srcData);
        DTDSubset ss;

        try {
            ss = r.parseDTD();
        } finally {
            /* And then need to restore changes back to owner (line nrs etc);
             * effectively means that we'll stop reading external DTD subset,
             * if so.
             */
            srcData.copyBufferStateFrom(r);
        }
        return ss;
    }

    /**
     * Method called to read in the external subset definition.
     */
    public static DTDSubset readExternalSubset
        (WstxInputSource src, ReaderConfig cfg, DTDSubset intSubset, 
         boolean constructFully, int xmlVersion)
        throws XMLStreamException
    {
        FullDTDReader r = new FullDTDReader(src, cfg, intSubset, constructFully, xmlVersion);
        return r.parseDTD();
    }

    /**
     * Method that will parse, process and output contents of an external
     * DTD subset. It will do processing similar to
     * {@link #readExternalSubset}, but additionally will copy its processed
     * ("flattened") input to specified writer.
     *
     * @param src Input source used to read the main external subset
     * @param flattenWriter Writer to output processed DTD content to
     * @param inclComments If true, will pass comments to the writer; if false,
     *   will strip comments out
     * @param inclConditionals If true, will include conditional block markers,
     *   as well as intervening content; if false, will strip out both markers
     *   and ignorable sections.
     * @param inclPEs If true, will output parameter entity declarations; if
     *   false will parse and use them, but not output.
     */
    public static DTDSubset flattenExternalSubset(WstxInputSource src, Writer flattenWriter,
                                                  boolean inclComments, boolean inclConditionals,
                                                  boolean inclPEs)
        throws IOException, XMLStreamException
    {
        ReaderConfig cfg = ReaderConfig.createFullDefaults();
        // Need to create a non-shared copy to populate symbol table field
        cfg = cfg.createNonShared(new SymbolTable());

        /* Let's assume xml 1.0... can be taken as an arg later on, if we
         * truly care.
         */
        FullDTDReader r = new FullDTDReader(src, cfg, null, true, XmlConsts.XML_V_UNKNOWN);
        r.setFlattenWriter(flattenWriter, inclComments, inclConditionals,
                           inclPEs);
        DTDSubset ss = r.parseDTD();
        r.flushFlattenWriter();
        flattenWriter.flush();
        return ss;
    }

    private TextBuffer getTextBuffer()
    {
        if (mTextBuffer == null) {
            mTextBuffer = TextBuffer.createTemporaryBuffer();
            mTextBuffer.resetInitialized();
        } else {
            mTextBuffer.resetWithEmpty();
        }
        return mTextBuffer;
    }

    /*
    ///////////////////////////////////////////////////////////
    // Configuration
    ///////////////////////////////////////////////////////////
     */

    /**
     * Method that will set specified Writer as the 'flattening writer';
     * writer used to output flattened version of DTD read in. This is
     * similar to running a C-preprocessor on C-sources, except that
     * defining writer will not prevent normal parsing of DTD itself.
     */
    public void setFlattenWriter(Writer w, boolean inclComments,
                                 boolean inclConditionals, boolean inclPEs)
    {
        mFlattenWriter = new DTDWriter(w, inclComments, inclConditionals,
                                       inclPEs);
    }

    private void flushFlattenWriter() throws XMLStreamException {
        mFlattenWriter.flush(mInputBuffer, mInputPtr);
    }

    /*
    ///////////////////////////////////////////////////////////
    // Internal API
    ///////////////////////////////////////////////////////////
     */

    /**
     * Method that may need to be called by attribute default value
     * validation code, during parsing....
     *<p>
     * Note: see base class for some additional remarks about this
     * method.
     */
    @Override
    public EntityDecl findEntity(String entName)
    {
        if (mPredefdGEs != null) {
            EntityDecl decl = mPredefdGEs.get(entName);
            if (decl != null) {
                return decl;
            }
        }
        return mGeneralEntities.get(entName);
    }

    /*
    ///////////////////////////////////////////////////////////
    // Main-level parsing methods
    ///////////////////////////////////////////////////////////
     */

    protected DTDSubset parseDTD()
        throws XMLStreamException
    {
        while (true) {
            mCheckForbiddenPEs = false; // PEs are ok at this point
            int i = getNextAfterWS();
            if (i < 0) {
                if (mIsExternal) { // ok for external DTDs
                    break;
                }
                // Error for internal subset
                throwUnexpectedEOF(SUFFIX_IN_DTD_INTERNAL);
            }
            if (i == '%') { // parameter entity
                expandPE();
                continue;
            }

            /* First, let's keep track of start of the directive; needed for
             * entity and notation declaration events.
             */
            mTokenInputTotal = mCurrInputProcessed + mInputPtr;
            mTokenInputRow = mCurrInputRow;
            mTokenInputCol = mInputPtr - mCurrInputRowStart;

            if (i == '<') {
                // PEs not allowed within declarations, in the internal subset proper
                mCheckForbiddenPEs = !mIsExternal && (mInput == mRootInput);
                if (mFlattenWriter == null) {
                    parseDirective();
                } else {
                    parseDirectiveFlattened();
                }
                continue;
            }

            if (i == ']') {
                if (mIncludeCount == 0 && !mIsExternal) { // End of internal subset
                    break;
                }
                if (mIncludeCount > 0) { // active INCLUDE block(s) open?
                    boolean suppress = (mFlattenWriter != null) && !mFlattenWriter.includeConditionals();

                    if (suppress) {
                        mFlattenWriter.flush(mInputBuffer, mInputPtr-1);
                        mFlattenWriter.disableOutput();
                    }

                    try {
                        // ]]> needs to be a token, can not come from PE:
                        char c = dtdNextFromCurr();
                        if (c == ']') {
                            c = dtdNextFromCurr();
                            if (c == '>') {
                                // Ok, fine, conditional include section ended.
                                --mIncludeCount;
                                continue;
                            }
                        }
                        throwDTDUnexpectedChar(c, "; expected ']]>' to close conditional include section");
                    } finally {
                        if (suppress) {
                            mFlattenWriter.enableOutput(mInputPtr);
                        }
                    }
                }
                // otherwise will fall through, and give an error
            }

            if (mIsExternal) {
                throwDTDUnexpectedChar(i, "; expected a '<' to start a directive");
            }
            throwDTDUnexpectedChar(i, "; expected a '<' to start a directive, or \"]>\" to end internal subset");
        }

        /* 05-Feb-2006, TSa: Not allowed to have unclosed INCLUDE/IGNORE
         *    blocks...
         */
        if (mIncludeCount > 0) { // active INCLUDE block(s) open?
            String suffix = (mIncludeCount == 1) ? "an INCLUDE block" : (""+mIncludeCount+" INCLUDE blocks");
            throwUnexpectedEOF(getErrorMsg()+"; expected closing marker for "+suffix);
        }

        /* First check: have all notation references been resolved?
         * (related to [WSTX-121])
         */
        if (mNotationForwardRefs != null && mNotationForwardRefs.size() > 0) {
            _reportUndefinedNotationRefs();
        }

        // Ok; time to construct and return DTD data object.
        DTDSubset ss;

        // There are more settings for ext. subsets:
        if (mIsExternal) {
            /* External subsets are cachable if they did not refer to any
             * PEs or GEs defined in internal subset passed in (if any),
             * nor to any notations.
             * We don't care about PEs it defined itself, but need to pass
             * in Set of PEs it refers to, to check if cached copy can be
             * used with different int. subsets.
             * We need not worry about notations referred, since they are
             * not allowed to be re-defined.
             */
            boolean cachable = !mUsesPredefdEntities && !mUsesPredefdNotations;
            ss = DTDSubsetImpl.constructInstance(cachable,
                                                 mGeneralEntities, mRefdGEs,
                                                 null, mRefdPEs,
                                                 mNotations, mElements,
                                                 mCfgFullyValidating);
        } else {
            /* Internal subsets are not cachable (no unique way to refer
             * to unique internal subsets), and there can be no references
             * to pre-defined PEs, as none were passed.
             */
            ss = DTDSubsetImpl.constructInstance(false, mGeneralEntities, null,
                                                 mParamEntities, null,
                                                 mNotations, mElements,
                                                 mCfgFullyValidating);
        }
        return ss;
    }

    protected void parseDirective()
        throws XMLStreamException
    {
        /* Hmmh. Don't think PEs are allowed to contain starting
         * '!' (or '?')... and it has to come from the same
         * input source too (no splits)
         */
        char c = dtdNextFromCurr();
        if (c == '?') { // xml decl?
            readPI();
            return;
        }
        if (c != '!') { // nothing valid
            throwDTDUnexpectedChar(c, "; expected '!' to start a directive");
        }

        /* ignore/include, comment, or directive; we are still getting
         * token from same section though
         */
        c = dtdNextFromCurr();
        if (c == '-') { // plain comment
            c = dtdNextFromCurr();
            if (c != '-') {
                throwDTDUnexpectedChar(c, "; expected '-' for a comment");
            }
            if (mEventListener != null && mEventListener.dtdReportComments()) {
                readComment(mEventListener);
            } else {
                skipComment();
            }
        } else if (c == '[') {
            checkInclusion();
        } else if (c >= 'A' && c <= 'Z') {
            handleDeclaration(c);
        } else {
            throwDTDUnexpectedChar(c, ErrorConsts.ERR_DTD_MAINLEVEL_KEYWORD);
        }
    }

    /**
     * Method similar to {@link #parseDirective}, but one that takes care
     * to properly output dtd contents via {@link com.ctc.wstx.dtd.DTDWriter}
     * as necessary.
     * Separated to simplify both methods; otherwise would end up with
     * 'if (... flatten...) ... else ...' spaghetti code.
     */
    protected void parseDirectiveFlattened()
        throws XMLStreamException
    {
        /* First, need to flush any flattened output there may be, at
         * this point (except for opening lt char): and then need to
         * temporarily disable more output until we know the type and
         * whether it should be output or not:
         */
        mFlattenWriter.flush(mInputBuffer, mInputPtr-1);
        mFlattenWriter.disableOutput();

        /* Let's determine type here, and call appropriate skip/parse
         * methods.
         */
        char c = dtdNextFromCurr();
        if (c == '?') { // xml decl?
            mFlattenWriter.enableOutput(mInputPtr);
            mFlattenWriter.output("<?");
            readPI();
            //throwDTDUnexpectedChar(c, " expected '!' to start a directive");
            return;
        }
        if (c != '!') { // nothing valid
            throwDTDUnexpectedChar(c, ErrorConsts.ERR_DTD_MAINLEVEL_KEYWORD);
        }

        // ignore/include, comment, or directive

        c = dtdNextFromCurr();
        if (c == '-') { // plain comment
            c = dtdNextFromCurr();
            if (c != '-') {
                throwDTDUnexpectedChar(c, "; expected '-' for a comment");
            }
            boolean comm = mFlattenWriter.includeComments();
            if (comm) {
                mFlattenWriter.enableOutput(mInputPtr);
                mFlattenWriter.output("<!--");
            }

            try {
                skipComment();
            } finally {
                if (!comm) {
                    mFlattenWriter.enableOutput(mInputPtr);
                }
            }
        } else {
            if (c == '[') {
                boolean cond = mFlattenWriter.includeConditionals();
                if (cond) {
                    mFlattenWriter.enableOutput(mInputPtr);
                    mFlattenWriter.output("<![");
                }
                try {
                    checkInclusion();
                } finally {
                    if (!cond) {
                        mFlattenWriter.enableOutput(mInputPtr);
                    }
                }
            } else {
                /* 12-Jul-2004, TSa: Do we need to see if we have to suppress
                 *    a PE declaration?
                 */
                boolean filterPEs = (c == 'E') && !mFlattenWriter.includeParamEntities();
                if (filterPEs) {
                    handleSuppressedDeclaration();
                } else if (c >= 'A' && c <= 'Z') {
                    mFlattenWriter.enableOutput(mInputPtr);
                    mFlattenWriter.output("<!");
                    mFlattenWriter.output(c);
                    handleDeclaration(c);
                } else {
                    throwDTDUnexpectedChar(c, ErrorConsts.ERR_DTD_MAINLEVEL_KEYWORD);
                }
            }
        }
    }

    /*
    ///////////////////////////////////////////////////////////
    // Overridden input handling 
    ///////////////////////////////////////////////////////////
     */

    @Override
    protected void initInputSource(WstxInputSource newInput, boolean isExt, String entityId)
        throws XMLStreamException
    {
        if (mFlattenWriter != null) {
            // Anything to flush from previous buffer contents?
            mFlattenWriter.flush(mInputBuffer, mInputPtr);
            mFlattenWriter.disableOutput();
            try {
                /* Then let's let base class do the 'real' input source setup;
                 * this includes skipping of optional XML declaration that we
                 * do NOT want to output
                 */
                super.initInputSource(newInput, isExt, entityId);
            } finally {
                // This will effectively skip declaration
                mFlattenWriter.enableOutput(mInputPtr);
            }
        } else {
            super.initInputSource(newInput, isExt, entityId);
        }
    }

    /**
     * Need to override this method, to check couple of things: first,
     * that nested input sources are balanced, when expanding parameter
     * entities inside entity value definitions (as per XML specs), and
     * secondly, to handle (optional) flattening output.
     */
    @Override
    protected boolean loadMore() throws XMLStreamException
    {
        WstxInputSource input = mInput;

        // Any flattened not-yet-output input to flush?
        if (mFlattenWriter != null) {
            /* Note: can not trust mInputPtr; may not be correct. End of
             * input should be, though.
             */
            mFlattenWriter.flush(mInputBuffer, mInputEnd);
        }

        do {
            /* Need to make sure offsets are properly updated for error
             * reporting purposes, and do this now while previous amounts
             * are still known.
             */
            mCurrInputProcessed += mInputEnd;
            mCurrInputRowStart -= mInputEnd;
            try {
                int count = input.readInto(this);
                if (count > 0) {
                    if (mFlattenWriter != null) {
                        mFlattenWriter.setFlattenStart(mInputPtr);
                    }
                    return true;
                }
                input.close();
            } catch (IOException ioe) {
                throw constructFromIOE(ioe);
            }
            if (input == mRootInput) {
                return false;
            }
            WstxInputSource parent = input.getParent();
            if (parent == null) { // sanity check!
                throwNullParent(input);
            }
            /* 13-Feb-2006, TSa: Ok, do we violate a proper nesting constraints
             *   with this input block closure?
             */
            if (mCurrDepth != input.getScopeId()) {
                handleIncompleteEntityProblem(input);
            }

            mInput = input = parent;
            input.restoreContext(this);
            if (mFlattenWriter != null) {
                mFlattenWriter.setFlattenStart(mInputPtr);
            }
            mInputTopDepth = input.getScopeId();
            /* 21-Feb-2006, TSa: Since linefeed normalization needs to be
             *   suppressed for internal entity expansion, we may need to
             *   change the state...
             */
            if (!mNormalizeLFs) {
                mNormalizeLFs = !input.fromInternalEntity();
            }
            // Maybe there are leftovers from that input in buffer now?
        } while (mInputPtr >= mInputEnd);

        return true;
    }

    @Override
    protected boolean loadMoreFromCurrent() throws XMLStreamException
    {
        // Any flattened not-yet-output input to flush?
        if (mFlattenWriter != null) {
            mFlattenWriter.flush(mInputBuffer, mInputEnd);
        }

        // Need to update offsets properly
        mCurrInputProcessed += mInputEnd;
        mCurrInputRowStart -= mInputEnd;
        try {
            int count = mInput.readInto(this);
            if (count > 0) {
                if (mFlattenWriter != null) {
                    mFlattenWriter.setFlattenStart(mInputPtr);
                }
                return true;
            }
        } catch (IOException ie) {
            throwFromIOE(ie);
        }
        return false;
    }

    @Override
    protected boolean ensureInput(int minAmount) throws XMLStreamException
    {
        int currAmount = mInputEnd - mInputPtr;
        if (currAmount >= minAmount) {
            return true;
        }
        // Any flattened not-yet-output input to flush?
        if (mFlattenWriter != null) {
            mFlattenWriter.flush(mInputBuffer, mInputEnd);
        }
        try {
            if (mInput.readMore(this, minAmount)) {
                if (mFlattenWriter != null) {
                    //mFlattenWriter.setFlattenStart(mInputPtr);
                    mFlattenWriter.setFlattenStart(currAmount);
                }
                return true;
            }
        } catch (IOException ie) {
            throwFromIOE(ie);
        }
        return false;
    }

    /*
    ///////////////////////////////////////////////////////////
    // Internal methods, input access:
    ///////////////////////////////////////////////////////////
     */

    private void loadMoreScoped(WstxInputSource currScope,
                                String entityName, Location loc)
        throws XMLStreamException
    {
        boolean check = (mInput == currScope);
        loadMore(getErrorMsg());
        // Did we get out of the scope?
        if (check && (mInput != currScope)) {
            _reportWFCViolation("Unterminated entity value for entity '"
                                +entityName+"' (definition started at "
                                +loc+")");
        }
    }
        
    /**
     * @return Next character from the current input block, if any left;
     *    NULL if end of block (entity expansion)
     */
    private char dtdNextIfAvailable()
        throws XMLStreamException
    {
        char c;
        if (mInputPtr < mInputEnd) {
            c = mInputBuffer[mInputPtr++];
        } else {
            int i = peekNext();
            if (i < 0) {
                return CHAR_NULL;
            }
            ++mInputPtr;
            c = (char) i;
        }
        if (c == CHAR_NULL) {
            throwNullChar();
        }
        return c;
    }

    /**
     * Method that will get next character, and either return it as is (for
     * normal chars), or expand parameter entity that starts with next
     * character (which has to be '%').
     */
    private char getNextExpanded()
        throws XMLStreamException
    {
        while (true) {
            char c = (mInputPtr < mInputEnd) ?
                mInputBuffer[mInputPtr++] : getNextChar(getErrorMsg());
            if (c != '%') {
                return c;
            }
            expandPE();
        }
    }

    private char skipDtdWs(boolean handlePEs)
        throws XMLStreamException
    {
        while (true) {
            char c = (mInputPtr < mInputEnd)
                ? mInputBuffer[mInputPtr++] : getNextChar(getErrorMsg());
            if (c > CHAR_SPACE) {
                if (c == '%' && handlePEs) {
                    expandPE();
                    continue;
                }
                return c;
            }
            if (c == '\n' || c == '\r') {
                skipCRLF(c);
            } else if (c != CHAR_SPACE && c != '\t') {
                throwInvalidSpace(c);
            }
        }
    }

    /**
     * Note: Apparently a parameter entity expansion does also count
     * as white space (that is, PEs outside of quoted text are considered
     * to be separated by white spaces on both sides). Fortunately this
     * can be handled by 2 little hacks: both a start of a PE, and an
     * end of input block (== end of PE expansion) count as succesful
     * spaces.
     *
     * @return Character following the obligatory boundary (white space
     *   or PE start/end)
     */
    private char skipObligatoryDtdWs()
        throws XMLStreamException
    {
        /* Ok; since we need at least one space, or a PE, or end of input
         * block, let's do this unique check first...
         */
        int i = peekNext();
        char c;

        if (i == -1) { // just means 'local' EOF (since peek only checks current)
            c = getNextChar(getErrorMsg());
            // Non-space, non PE is ok, due to end-of-block...
            if (c > CHAR_SPACE && c != '%') {
                return c;
            }
        } else {
            c = mInputBuffer[mInputPtr++]; // was peek, need to read
            if (c > CHAR_SPACE && c != '%') {
                throwDTDUnexpectedChar(c, "; expected a separating white space");
            }
        }

        // Ok, got it, now can loop...
        while (true) {
            if (c == '%') {
                expandPE();
            } else if (c > CHAR_SPACE) {
                break;
            } else {
                if (c == '\n' || c == '\r') {
                    skipCRLF(c);
                } else if (c != CHAR_SPACE && c != '\t') {
                    throwInvalidSpace(c);
                }
            }
            /* Now we got one space (or end of input block) -- no need to
             * restrict get next on current block (in case PE ends); happens
             * with xmltest/valid/not-sa/003.xml, for eaxmple.
             */
            c = (mInputPtr < mInputEnd)
                ? mInputBuffer[mInputPtr++] : getNextChar(getErrorMsg());
        }
        return c;
    }

    /**
     * Method called to handle expansion of parameter entities. When called,
     * '%' character has been encountered as a reference indicator, and
     * now we should get parameter entity name.
     */
    private void expandPE()
        throws XMLStreamException
    {
        String id;
        char c;

        if (mCheckForbiddenPEs) {
            /* Ok; we hit a PE where we should not have (within the internal
             * dtd subset proper, within a declaration). This is a WF error.
             */
            throwForbiddenPE();
        }

        // 01-Jul-2004, TSa: When flattening, need to flush previous output
        if (mFlattenWriter != null) {
            // Flush up to but not including ampersand...
            mFlattenWriter.flush(mInputBuffer, mInputPtr-1);
            mFlattenWriter.disableOutput();
            c = (mInputPtr < mInputEnd) ?
                mInputBuffer[mInputPtr++] : dtdNextFromCurr();
            id = readDTDName(c);
            try {
                c = (mInputPtr < mInputEnd) ?
                    mInputBuffer[mInputPtr++] : dtdNextFromCurr();
            } finally {
                // will ignore name and colon (or whatever was parsed)
                mFlattenWriter.enableOutput(mInputPtr);
            }
        } else {
            c = (mInputPtr < mInputEnd) ?
                mInputBuffer[mInputPtr++] : dtdNextFromCurr();
            id = readDTDName(c);
            c = (mInputPtr < mInputEnd) ?
                mInputBuffer[mInputPtr++] : dtdNextFromCurr();
        }
        
        // Should now get semicolon...
        if (c != ';') {
            throwDTDUnexpectedChar(c, "; expected ';' to end parameter entity name");
        }
        mExpandingPE = true;
        expandEntity(id, true, ENTITY_EXP_PE);
    }

    /*
    ///////////////////////////////////////////////////////////
    // Internal methods, low-level parsing:
    ///////////////////////////////////////////////////////////
     */

    /**
     * Method called to verify whether input has specified keyword; if it
     * has, returns null and points to char after the keyword; if not,
     * returns whatever constitutes a keyword matched, for error
     * reporting purposes.
     */
    protected String checkDTDKeyword(String exp)
        throws XMLStreamException
    {
        int i = 0;
        int len = exp.length();
        char c = ' ';

        for (; i < len; ++i) {
            if (mInputPtr < mInputEnd) {
                c = mInputBuffer[mInputPtr++];
            } else {
                c = dtdNextIfAvailable();
                if (c == CHAR_NULL) { // end of block, fine
                    return exp.substring(0, i);
                }
            }
            if (c != exp.charAt(i)) {
                break;
            }
        }

        if (i == len) {
            // Got a match? Cool... except if identifier still continues...
            c = dtdNextIfAvailable();
            if (c == CHAR_NULL) { // EOB, fine
                return null;
            }
            if (!isNameChar(c)) {
                --mInputPtr; // to push it back
                return null;
            }
        }
        StringBuilder sb = new StringBuilder(exp.substring(0, i));
        sb.append(c);
        while (true) {
            c = dtdNextIfAvailable();
            if (c == CHAR_NULL) { // EOB, fine
                break;
            }
            if (!isNameChar(c) && c != ':') {
                --mInputPtr; // to push it back
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Method called usually to indicate an error condition; will read rest
     * of specified keyword (including characters that can be part of XML
     * identifiers), append that to passed prefix (which is optional), and
     * return resulting String.
     *
     * @param prefix Part of keyword already read in.
     */
    protected String readDTDKeyword(String prefix)
        throws XMLStreamException
    {
        StringBuilder sb = new StringBuilder(prefix);

        while (true) {
            char c;
            if (mInputPtr < mInputEnd) {
                c = mInputBuffer[mInputPtr++];
            } else {
                // Don't want to cross block boundary
                c = dtdNextIfAvailable();
                if (c == CHAR_NULL) {
                    break; // end-of-block
                }
            }
            if (!isNameChar(c) && c != ':') {
                --mInputPtr;
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * @return True, if input contains 'PUBLIC' keyword; false if it
     *   contains 'SYSTEM'; otherwise throws an exception.
     */
    private boolean checkPublicSystemKeyword(char c) 
        throws XMLStreamException
    {
        String errId;

        if (c == 'P') {
            errId = checkDTDKeyword("UBLIC");
            if (errId == null) {
                return true;
            }
            errId = "P" + errId;
        } else if (c == 'S') {
            errId = checkDTDKeyword("YSTEM");
            if (errId == null) {
                return false;
            }
            errId = "S" + errId;
        } else {
            if (!isNameStartChar(c)) {
                throwDTDUnexpectedChar(c, "; expected 'PUBLIC' or 'SYSTEM' keyword");
            }
            errId = readDTDKeyword(String.valueOf(c));
        }

        _reportWFCViolation("Unrecognized keyword '"+errId+"'; expected 'PUBLIC' or 'SYSTEM'");
        return false; // never gets here
    }

    private String readDTDName(char c)
        throws XMLStreamException
    {
        // Let's just check this before trying to parse the id...
        if (!isNameStartChar(c)) {
            throwDTDUnexpectedChar(c, "; expected an identifier");
        }
        return parseFullName(c);
    }

    private String readDTDLocalName(char c, boolean checkChar)
        throws XMLStreamException
    {
        /* Let's just check this first, to get better error msg
         * (parseLocalName() will double-check it too)
         */
        if (checkChar && !isNameStartChar(c)) {
            throwDTDUnexpectedChar(c, "; expected an identifier");
        }
        return parseLocalName(c);
    }

    /**
     * Similar to {@link #readDTDName}, except that the rules are bit looser,
     * ie. there are no additional restrictions for the first char
     */
    private String readDTDNmtoken(char c)
        throws XMLStreamException
    {
        char[] outBuf = getNameBuffer(64);
        int outLen = outBuf.length;
        int outPtr = 0;

        while (true) {
            /* Note: colon not included in name char array, since it has
             * special meaning WRT QNames, need to add into account here:
             */
            if (!isNameChar(c)  && c != ':') {
                // Need to get at least one char
                if (outPtr == 0) {
                    throwDTDUnexpectedChar(c, "; expected a NMTOKEN character to start a NMTOKEN");
                }
                --mInputPtr;
                break;
            }
            if (outPtr >= outLen) {
                outBuf = expandBy50Pct(outBuf);
                outLen = outBuf.length;
            }
            outBuf[outPtr++] = c;
            if (mInputPtr < mInputEnd) {
                c = mInputBuffer[mInputPtr++];
            } else {
                c = dtdNextIfAvailable();
                if (c == CHAR_NULL) { // end-of-block
                    break;
                }
            }
        }

        /* Nmtokens need not be canonicalized; they will be processed
         * as necessary later on:
         */
        return new String(outBuf, 0, outPtr);
    }

    /**
     * Method that will read an element or attribute name from DTD; depending
     * on namespace mode, it can have prefix as well.
     *<p>
     * Note: returned {@link PrefixedName} instances are canonicalized so that
     * all instances read during parsing of a single DTD subset so that
     * identity comparison can be used instead of calling <code>equals()</code>
     * method (but only within a single subset!). This also reduces memory
     * usage to some extent.
     */
    private PrefixedName readDTDQName(char firstChar)
        throws XMLStreamException
    {
        String prefix, localName;

        if (!mCfgNsEnabled) {
            prefix = null;
            localName = parseFullName(firstChar);
        } else {
            localName = parseLocalName(firstChar);
            /* Hmmh. This is tricky; should only read from the current
             * scope, but it is ok to hit end-of-block if it was a PE
             * expansion...
             */
            char c = dtdNextIfAvailable();
            if (c == CHAR_NULL) { // end-of-block
                // ok, that's it...
                prefix = null;
            } else {
                if (c == ':') { // Ok, got namespace and local name
                    prefix = localName;
                    c = dtdNextFromCurr();
                    localName = parseLocalName(c);
                } else {
                    prefix = null;
                    --mInputPtr;
                }
            }
        }

        return findSharedName(prefix, localName);
    }

    private char readArity()
        throws XMLStreamException
    {
        char c = (mInputPtr < mInputEnd) ?
            mInputBuffer[mInputPtr++] : getNextChar(getErrorMsg());
        if (c == '?' || c == '*' || c == '+') {
            return c;
        }
        // Hmmh, not recognized, let's put it back:
        --mInputPtr;

        // Default is 'just one'
        return ' ';
    }

    /**
     * Method that reads and pre-processes replacement text for an internal
     * entity (parameter or generic).
     */
    private char[] parseEntityValue(String id, Location loc, char quoteChar)
        throws XMLStreamException
    {
        /* 25-Jun-2004, TSa: Let's first mark current input source as the
         *   scope, so we can both make sure it ends in this input
         *   context (file), and that embedded single/double quotes
         *   in potentially expanded entities do not end the value
         *   definition (as per XML 1.0/3, 4.4.5)
         */
        WstxInputSource currScope = mInput;

        /* 18-Jul-2004, TSa: Also, let's see if parameter entities are
         *  allowed; they are only legal outside of main internal subset
         *  (ie. main XML input) file (or to be precise; they are legal
         *  in the int. subset only as complete declarations)
         */
        //boolean allowPEs = mIsExternal || (mInput != mRootInput);

        TextBuffer tb = mValueBuffer;
        if (tb == null) {
            tb = TextBuffer.createTemporaryBuffer();
        }
        tb.resetInitialized();

        char[] outBuf = tb.getCurrentSegment();
        int outPtr = tb.getCurrentSegmentSize();

        while (true) {
            if (mInputPtr >= mInputEnd) {
                loadMoreScoped(currScope, id, loc);
            }
            char c = mInputBuffer[mInputPtr++];

            // Let's get most normal chars 'skipped' first
            if (c >= CHAR_FIRST_PURE_TEXT) {
                ;
            } else if (c == quoteChar) {
                // Only end if we are in correct scope:
                if (mInput == currScope) {
                    break;
                }
            } else if (c == '&') { // char entity that needs to be replaced?
                /* 06-Sep-2004, TSa: We can NOT expand pre-defined entities, as
                 *   XML specs consider them 'real' (non-char) entities.
                 *   And expanding them would cause problems with entities
                 *   that have such entities.
                 */
                int d = resolveCharOnlyEntity(false);
                // Did we get a real char entity?
                if (d != 0) {
                    if (d <= 0xFFFF) {
                        c = (char) d;
                    } else {
                        // Need more room?
                        if (outPtr >= outBuf.length) {
                            outBuf = tb.finishCurrentSegment();
                            outPtr = 0;
                        }
                        d -= 0x10000;
                        outBuf[outPtr++] = (char) ((d >> 10)  + 0xD800);;
                        c = (char) ((d & 0x3FF)  + 0xDC00);
                    }
                } else {
                    /* 11-Feb-2006, TSa: Even so, must verify that the
                     *   entity reference is well-formed.
                     */
                    boolean first = true;
                    while (true) {
                        if (outPtr >= outBuf.length) { // need more room?
                            outBuf = tb.finishCurrentSegment();
                            outPtr = 0;
                        }
                        outBuf[outPtr++] = c; // starting with '&'
                        if (mInputPtr >= mInputEnd) {
                            loadMoreScoped(currScope, id, loc);
                        }
                        c = mInputBuffer[mInputPtr++];
                        if (c == ';') {
                            break;
                        }
                        if (first) {
                            first = false;
                            if (isNameStartChar(c)) {
                                continue;
                            }
                        } else {
                            if (isNameChar(c)) {
                                continue;
                            }
                        }
                        if (c == ':' && !mCfgNsEnabled) {
                            continue; // fine in non-ns mode
                        }
                        if (first) { // missing name
                            throwDTDUnexpectedChar(c, "; expected entity name after '&'");
                        }
                        throwDTDUnexpectedChar(c, "; expected semi-colon after entity name");
                    }
                    // we can just fall through to let semicolon be added
                }
                // Either '&' itself, or expanded char entity
            } else if (c == '%') { // param entity?
                expandPE();
                // Need to loop over, no char available yet
                continue;
            } else if (c < CHAR_SPACE) {
                if (c == '\n') {
                    markLF();
                } else if (c == '\r') {
                    if (skipCRLF(c)) {
                        if (!mNormalizeLFs) {
                            // Special handling, to output 2 chars at a time:
                            if (outPtr >= outBuf.length) { // need more room?
                                outBuf = tb.finishCurrentSegment();
                                outPtr = 0;
                            }
                            outBuf[outPtr++] = c;
                        }
                        c = '\n';
                    } else {
                        if (mNormalizeLFs) { // Mac LF
                            c = '\n';
                        }
                    }
                } else if (c != '\t') {
                    throwInvalidSpace(c);
                }
            }
            
            // Need more room?
            if (outPtr >= outBuf.length) {
                outBuf = tb.finishCurrentSegment();
                outPtr = 0;
            }
            // Ok, let's add char to output:
            outBuf[outPtr++] = c;
        }
        tb.setCurrentLength(outPtr);
        
        // Ok, now need the closing '>':
        char c = skipDtdWs(true);
        if (c != '>') {
            throwDTDUnexpectedChar(c, "; expected closing '>' after ENTITY declaration");
        }
        char[] result = tb.contentsAsArray();
        mValueBuffer = tb; // recycle, if needed later on

        return result;
    }

    /**
     * This method is similar to {@link #parseEntityValue} in some ways,
     * but has some notable differences, due to the way XML specs define
     * differences. Main differences are that parameter entities are not
     * allowed (or rather, recognized as entities), and that general
     * entities need to be verified, but NOT expanded right away.
     * Whether forward references are allowed or not is an open question
     * right now.
     */
    private void parseAttrDefaultValue(DefaultAttrValue defVal, char quoteChar, PrefixedName attrName,
                                       Location loc, boolean gotFixed)
        throws XMLStreamException
    {
        if (quoteChar != '"' && quoteChar != '\'') { // caller doesn't test it
            String msg = "; expected a single or double quote to enclose the default value";
            if (!gotFixed) {
                msg += ", or one of keywords (#REQUIRED, #IMPLIED, #FIXED)";
            }
            msg += " (for attribute '"+attrName+"')";
            throwDTDUnexpectedChar(quoteChar, msg);
        }

        /* Let's mark the current input source as the scope, so we can both
         * make sure it ends in this input context (DTD subset), and that
         * embedded single/double quotes in potentially expanded entities do
         * not end the value definition (as per XML 1.0/3, 4.4.5)
         */
        WstxInputSource currScope = mInput;

        TextBuffer tb = mValueBuffer;
        if (tb == null) {
            tb = TextBuffer.createTemporaryBuffer();
        }
        tb.resetInitialized();

        int outPtr = 0;
        char[] outBuf = tb.getCurrentSegment();
        int outLen = outBuf.length;

        /* One more note: this is mostly cut'n pasted from stream reader's
         * parseNormalizedAttrValue...
         */
        main_loop:

        while (true) {
            if (mInputPtr >= mInputEnd) {
                boolean check = (mInput == currScope);
                loadMore(getErrorMsg());
                // Did we get out of the scope?
                if (check && (mInput != currScope)) {
                    _reportWFCViolation("Unterminated attribute default value for attribute '"
                                    +attrName+"' (definition started at "
                                    +loc+")");
                }
            }
            char c = mInputBuffer[mInputPtr++];

            // Let's do a quick for most attribute content chars:
            if (c < CHAR_FIRST_PURE_TEXT) {
                if (c <= CHAR_SPACE) {
                    if (c == '\n') {
                        markLF();
                    } else if (c == '\r') {
                        c = getNextChar(SUFFIX_IN_DEF_ATTR_VALUE);
                        if (c != '\n') { // nope, not 2-char lf (Mac?)
                            --mInputPtr;
                            c = mNormalizeLFs ? '\n' : '\r';
                        } else {
                            // Fine if we are to normalize lfs
                            /* !!! 20-Jan-2007, TSa: Hmmh. Not sure if and
                             *  how to preserve: for now, let's assume there's
                             *  no need.
                             */
                            /*
                            if (!mNormalizeLFs) {
                                if (outPtr >= outLen) { // need more room?
                                    outBuf = tb.finishCurrentSegment();
                                    outPtr = 0;
                                    outLen = outBuf.length;
                                }
                                outBuf[outPtr++] = '\r';
                                // c is fine to continue
                            }
                            */
                        }
                        markLF();
                    } else if (c != CHAR_SPACE && c != '\t') {
                        throwInvalidSpace(c);
                    }
                    c = CHAR_SPACE;
                } else if (c == quoteChar) {
                    /* It is possible to get these via expanded entities;
                     * need to make sure this is the same input level as
                     * the one that had starting quote
                     */
                    if (mInput == currScope) {
                        break;
                    }
                } else if (c == '&') { // an entity of some sort...
                    /* Will need to expand char entities and pre-defd
                     * int. entities (amp, lt, apos, gt): first method
                     * is just more optimized than the second
                     */
                    int d;
                    if (inputInBuffer() >= 3) {
                        d = resolveSimpleEntity(true);
                    } else {
                        d = resolveCharOnlyEntity(true);
                    }
                    // Only get null if it's a 'real' general entity...
                    if (d == 0) {
                        c = getNextChar(SUFFIX_IN_ENTITY_REF);
                        String id = parseEntityName(c);
                        try {
                            mCurrAttrDefault = defVal;
                            mExpandingPE = false;
                            expandEntity(id, false, ENTITY_EXP_GE);
                        } finally {
                            mCurrAttrDefault = null;
                        }
                        // Ok, should have updated the input source by now
                        continue main_loop;
                    }
                    
                    if (c <= 0xFFFF) {
                        
                    } else{
                        if (d <= 0xFFFF) {
                            c = (char) d;
                        } else {
                            // Need more room?
                            if (outPtr >= outBuf.length) {
                                outBuf = tb.finishCurrentSegment();
                                outPtr = 0;
                            }
                            d -= 0x10000;
                            outBuf[outPtr++] = (char) ((d >> 10)  + 0xD800);;
                            c = (char) ((d & 0x3FF)  + 0xDC00);
                        }
                    }
                } else if (c == '<') {
                    throwDTDUnexpectedChar(c, SUFFIX_IN_DEF_ATTR_VALUE);
                }
            } // if (c < CHAR_FIRST_PURE_TEXT)
                
            // Ok, let's just add char in, whatever it was
            if (outPtr >= outLen) { // need more room?
                outBuf = tb.finishCurrentSegment();
                outPtr = 0;
                outLen = outBuf.length;
            }
            outBuf[outPtr++] = c;
        }

        tb.setCurrentLength(outPtr);
        defVal.setValue(tb.contentsAsString());
        mValueBuffer = tb;
    }

    /**
     * Method similar to {@link #skipPI}, but one that does basic
     * well-formedness checks.
     */
    protected void readPI()
        throws XMLStreamException
    {
        String target = parseFullName();
        if (target.length() == 0) {
            _reportWFCViolation(ErrorConsts.ERR_WF_PI_MISSING_TARGET);
        }
        if (target.equalsIgnoreCase("xml")) {
            _reportWFCViolation(ErrorConsts.ERR_WF_PI_XML_TARGET, target);
        }

        char c = dtdNextFromCurr();
        // Ok, need a space between target and data nonetheless
        if (!isSpaceChar(c)) { // except if it ends right away
            if (c != '?' || dtdNextFromCurr() != '>') {
                throwUnexpectedChar(c, ErrorConsts.ERR_WF_PI_XML_MISSING_SPACE);
            }
            if (mEventListener != null) {
                mEventListener.dtdProcessingInstruction(target, "");
            }
        } else if (mEventListener == null) {
            /* Otherwise, not that much to check since we don't care about
             * the contents.
             */
            while (true) {
                c = (mInputPtr < mInputEnd)
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
                if (c < CHAR_SPACE) {
                    if (c == '\n' || c == '\r') {
                        skipCRLF(c);
                    } else if (c != '\t') {
                        throwInvalidSpace(c);
                    }
                }
            }
        } else {
            // 24-Nov-2006, TSa: Actually, someone does care...
            // First, need to skip extra space (if any)
            while (c <= CHAR_SPACE) {
                if (c == '\n' || c == '\r') {
                    skipCRLF(c);
                } else if (c != '\t' && c != ' ') {
                    throwInvalidSpace(c);
                }
                c = (mInputPtr < mInputEnd)
                    ? mInputBuffer[mInputPtr++] : dtdNextFromCurr();
            }

            TextBuffer tb = getTextBuffer();
            char[] outBuf = tb.getCurrentSegment();
            int outPtr = 0;

            while (true) {
                if (c == '?') {
                    while (true) {
                        c = (mInputPtr < mInputEnd)
                            ? mInputBuffer[mInputPtr++] : dtdNextFromCurr();
                        if (c != '?') {
                            break;
                        }
                        if (outPtr >= outBuf.length) {
                            outBuf = tb.finishCurrentSegment();
                            outPtr = 0;
                        }
                        outBuf[outPtr++] = c;
                    }
                    if (c == '>') {
                        break;
                    }
                    // Need to push back char that follows '?', output '?'
                    --mInputPtr;
                    c = '?';
                } else if (c < CHAR_SPACE) {
                    if (c == '\n' || c == '\r') {
                        skipCRLF(c);
                        c = '\n';
                    } else if (c != '\t') {
                        throwInvalidSpace(c);
                    }
                }
                // Need more room?
                if (outPtr >= outBuf.length) {
                    outBuf = tb.finishCurrentSegment();
                    outPtr = 0;
                }
                // Ok, let's add char to output:
                outBuf[outPtr++] = c;
                c = (mInputPtr < mInputEnd)
                    ? mInputBuffer[mInputPtr++] : dtdNextFromCurr();
            }
            tb.setCurrentLength(outPtr);
            String data = tb.contentsAsString();
            mEventListener.dtdProcessingInstruction(target, data);
        }
    }

    /**
     * Method similar to {@link #skipComment}, but that has to collect
     * contents, to be reported for a SAX handler.
     */
    protected void readComment(DTDEventListener l)
        throws XMLStreamException
    {
        TextBuffer tb = getTextBuffer();
        char[] outBuf = tb.getCurrentSegment();
        int outPtr = 0;

        while (true) {
            char c = (mInputPtr < mInputEnd)
                ? mInputBuffer[mInputPtr++] : dtdNextFromCurr();
            if (c < CHAR_SPACE) {
                if (c == '\n' || c == '\r') {
                    skipCRLF(c);
                    c = '\n';
                } else if (c != '\t') {
                    throwInvalidSpace(c);
                }
            } else if (c == '-') {
                c = dtdNextFromCurr();
                if (c == '-') { // Ok, has to be end marker then:
                    // Either get '>' or error:
                    c = dtdNextFromCurr();
                    if (c != '>') {
                        throwParseError(ErrorConsts.ERR_HYPHENS_IN_COMMENT);
                    }
                    break;
                }
                c = '-';
                --mInputPtr; // need to push back the second char read
            }
            // Need more room?
            if (outPtr >= outBuf.length) {
                outBuf = tb.finishCurrentSegment();
                outPtr = 0;
            }
            // Ok, let's add char to output:
            outBuf[outPtr++] = c;
        }
        tb.setCurrentLength(outPtr);
        tb.fireDtdCommentEvent(l);
    }

    /*
    ///////////////////////////////////////////////////////////
    // Internal methods, conditional blocks:
    ///////////////////////////////////////////////////////////
     */

    private void checkInclusion()
        throws XMLStreamException
    {
        String keyword;

        // INCLUDE/IGNORE not allowed in internal subset...
        /* 18-Jul-2004, TSa: Except if it's in an expanded parsed external
         *   entity...
         */
        if (!mIsExternal && mInput == mRootInput) {
            _reportWFCViolation("Internal DTD subset can not use (INCLUDE/IGNORE) directives (except via external entities)");
        }

        char c = skipDtdWs(true);
        if (c != 'I') {
            // let's obtain the keyword for error reporting purposes:
            keyword = readDTDKeyword(String.valueOf(c));
        } else {
            c = dtdNextFromCurr();
            if (c == 'G') {
                keyword = checkDTDKeyword("NORE");
                if (keyword == null) {
                    handleIgnored();
                    return;
                }
                keyword = "IG"+keyword;
            } else if (c == 'N') {
                keyword = checkDTDKeyword("CLUDE");
                if (keyword == null) {
                    handleIncluded();
                    return;
                }
                keyword = "IN"+keyword;
            } else {
                --mInputPtr;
                keyword = readDTDKeyword("I");
            }
        }

        // If we get here, it was an error...
        _reportWFCViolation("Unrecognized directive '"+keyword+"'; expected either 'IGNORE' or 'INCLUDE'");
    }

    private void handleIncluded()
        throws XMLStreamException
    {
        char c = skipDtdWs(false);
        if (c != '[') {
            throwDTDUnexpectedChar(c, "; expected '[' to follow 'INCLUDE' directive");
        }
        ++mIncludeCount;
    }

    private void handleIgnored()
        throws XMLStreamException
    {
        char c = skipDtdWs(false);
        int count = 1; // Nesting of IGNORE/INCLUDE sections we have to match

        if (c != '[') {
            throwDTDUnexpectedChar(c, "; expected '[' to follow 'IGNORE' directive");
        }

        /* Ok; now, let's just skip until we get the closing ']]>'
         */
        String errorMsg = getErrorMsg();
        while (true) {
            c = (mInputPtr < mInputEnd)
                ? mInputBuffer[mInputPtr++] : getNextChar(errorMsg);
            if (c < CHAR_SPACE) {
                if (c == '\n' || c == '\r') {
                    skipCRLF(c);
                } else if (c != '\t') {
                    throwInvalidSpace(c);
                }
            } else if (c == ']') { // closing?
                if (getNextChar(errorMsg) == ']'
                    && getNextChar(errorMsg) == '>') {
                    if (--count < 1) { // done!
                        return;
                    }
                    // nested ignores, let's just continue
                } else {
                    --mInputPtr; // need to push one char back, may be '<'
                }
            } else if (c == '<') {
                if (getNextChar(errorMsg) == '!'
                    && getNextChar(errorMsg) == '[') {
                    // Further nesting, sweet
                    ++count;
                } else {
                    --mInputPtr; // need to push one char back, may be '<'
                }
            }
        }
    }

    /*
    ///////////////////////////////////////////////////////////
    // Internal methods, validation, exceptions
    ///////////////////////////////////////////////////////////
     */

    private void _reportUndefinedNotationRefs()
        throws XMLStreamException
    {
        int count = mNotationForwardRefs.size();

        String id = mNotationForwardRefs.keySet().iterator().next();
        String msg = ""+count+" referenced notation"+((count == 1) ? "":"s")+" undefined: first one '"+id+"'";
        _reportVCViolation(msg);
    }

    private void _reportBadDirective(String dir)
        throws XMLStreamException
    {
        String msg = "Unrecognized DTD directive '<!"+dir+" >'; expected ATTLIST, ELEMENT, ENTITY or NOTATION";
        if (mCfgSupportDTDPP) {
            msg += " (or, for DTD++, TARGETNS)";
        }
        _reportWFCViolation(msg);
    }

    private void _reportVCViolation(String msg)
        throws XMLStreamException
    {
        /* 01-Sep-2006, TSa: Not 100% sure what's the right way to do it --
         *   they are errors (non-fatal, but not warnings), but the way
         *   base class handles things, we probably better 'downgrade'
         *   them to warnings in non-validating mode.
         */
        if (mCfgFullyValidating) {
            reportValidationProblem(msg, XMLValidationProblem.SEVERITY_ERROR);
        } else {
            reportValidationProblem(msg, XMLValidationProblem.SEVERITY_WARNING);
        }
    }

    private void _reportWFCViolation(String msg)
        throws XMLStreamException
    {
        throwParseError(msg);
    }

    private void _reportWFCViolation(String format, Object arg)
        throws XMLStreamException
    {
        throwParseError(format, arg, null);
    }

    private void throwDTDElemError(String msg, Object elem)
        throws XMLStreamException
    {
        _reportWFCViolation(elemDesc(elem) + ": " + msg);
    }

    private void throwDTDAttrError(String msg, DTDElement elem, PrefixedName attrName)
        throws XMLStreamException
    {
        _reportWFCViolation(attrDesc(elem, attrName) + ": " + msg);
    }

    private void throwDTDUnexpectedChar(int i, String extraMsg)
        throws XMLStreamException
    {
        if (extraMsg == null) {
            throwUnexpectedChar(i, getErrorMsg());
        }
        throwUnexpectedChar(i, getErrorMsg()+extraMsg);
    }

    private void throwForbiddenPE()
        throws XMLStreamException
    {
        _reportWFCViolation("Can not have parameter entities in the internal subset, except for defining complete declarations (XML 1.0, #2.8, WFC 'PEs In Internal Subset')");
    }

    private String elemDesc(Object elem) {
        return "Element <"+elem+">)";
    }

    private String attrDesc(Object elem, PrefixedName attrName) {
        return "Attribute '"+attrName+"' (of element <"+elem+">)";
    }

    private String entityDesc(WstxInputSource input) {
        return "Entity &"+input.getEntityId()+";";
    }

    /*
    ///////////////////////////////////////////////////////////
    // Internal methods, main-level declaration parsing
    ///////////////////////////////////////////////////////////
     */

    /**
     *<p>
     * Note: c is known to be a letter (from 'A' to 'Z') at this poit.
     */
    private void handleDeclaration(char c)
        throws XMLStreamException
    {
        String keyw = null;
 
        /* We need to ensure that PEs do not span declaration boundaries
         * (similar to element nesting wrt. GE expansion for xml content).
         * This VC is defined in xml 1.0, section 2.8 as
         * "VC: Proper Declaration/PE Nesting"
         */
        /* We have binary depths within DTDs, for now: since the declaration
         * just started, we should now have 1 as the depth:
         */
        mCurrDepth = 1;

        try {
            do { // dummy loop, for break
                if (c == 'A') { // ATTLIST?
                    keyw = checkDTDKeyword("TTLIST");
                    if (keyw == null) {
                        mCurrDeclaration = "ATTLIST";
                        handleAttlistDecl();
                        break;
                    }
                    keyw = "A" + keyw;
                } else if (c == 'E') { // ENTITY, ELEMENT?
                    c = dtdNextFromCurr();
                    if (c == 'N') {
                        keyw = checkDTDKeyword("TITY");
                        if (keyw == null) {
                            mCurrDeclaration = "ENTITY";
                            handleEntityDecl(false);
                            break;
                        }
                        keyw = "EN" + keyw;
                    } else if (c == 'L') {
                        keyw = checkDTDKeyword("EMENT");
                        if (keyw == null) {
                            mCurrDeclaration = "ELEMENT";
                            handleElementDecl();
                            break;
                        }
                        keyw = "EL" + keyw;
                    } else {
                        keyw = readDTDKeyword("E"+c);
                    }
                } else if (c == 'N') { // NOTATION?
                    keyw = checkDTDKeyword("OTATION");
                    if (keyw == null) {
                        mCurrDeclaration = "NOTATION";
                        handleNotationDecl();
                        break;
                    }
                    keyw = "N" + keyw;
                } else if (c == 'T' && mCfgSupportDTDPP) { // (dtd++ only) TARGETNS?
                    keyw = checkDTDKeyword("ARGETNS");
                    if (keyw == null) {
                        mCurrDeclaration = "TARGETNS";
                        handleTargetNsDecl();
                        break;
                    }
                    keyw = "T" + keyw;
                } else {
                    keyw = readDTDKeyword(String.valueOf(c));
                }
                // If we got this far, we got a problem...
                _reportBadDirective(keyw);
            } while (false);
            /* Ok: now, the current input can not have been started
             * within the scope... so:
             */
            if (mInput.getScopeId() > 0) {
                handleGreedyEntityProblem(mInput);
            }

        } finally {
            // Either way, declaration has ended now...
            mCurrDepth = 0;
            mCurrDeclaration = null;
        }
    }

    /**
     * Specialized method that handles potentially suppressable entity
     * declaration. Specifically: at this point it is known that first
     * letter is 'E', that we are outputting flattened DTD info,
     * and that parameter entity declarations are to be suppressed.
     * Furthermore, flatten output is still being disabled, and needs
     * to be enabled by the method at some point.
     */
    private void handleSuppressedDeclaration()
        throws XMLStreamException
    {
        String keyw;
        char c = dtdNextFromCurr();

        if (c == 'N') {
            keyw = checkDTDKeyword("TITY");
            if (keyw == null) {
                handleEntityDecl(true);
                return;
            }
            keyw = "EN" + keyw;
            mFlattenWriter.enableOutput(mInputPtr); // error condition...
        } else {
            mFlattenWriter.enableOutput(mInputPtr);
            mFlattenWriter.output("<!E");
            mFlattenWriter.output(c);

            if (c == 'L') {
                keyw = checkDTDKeyword("EMENT");
                if (keyw == null) {
                    handleElementDecl();
                    return;
                }
                keyw = "EL" + keyw;
            } else {
                keyw = readDTDKeyword("E");
            }
        }
        _reportBadDirective(keyw);
    }

    /**
     * note: when this method is called, the keyword itself has
     * been succesfully parsed.
     */
    private void handleAttlistDecl()
        throws XMLStreamException
    {
        /* This method will handle PEs that contain the whole element
         * name. Since it's illegal to have partials, we can then proceed
         * to just use normal parsing...
         */
        char c = skipObligatoryDtdWs();
        final PrefixedName elemName = readDTDQName(c);

        /* Ok, event needs to know its exact starting point (opening '<'
         * char), let's get that info now (note: data has been preserved
         * earlier)
         */
        Location loc = getStartLocation();

        // Ok, where's our element?
        HashMap<PrefixedName,DTDElement> m = getElementMap();
        DTDElement elem = m.get(elemName);

        if (elem == null) { // ok, need a placeholder
            // Let's add ATTLIST location as the temporary location too
            elem = DTDElement.createPlaceholder(mConfig, loc, elemName);
            m.put(elemName, elem);
        }

        // Ok, need to loop to get all attribute defs:
        int index = 0;

        while (true) {
            /* White space is optional, if we get the closing '>' char;
             * otherwise it's obligatory.
             */
            c = getNextExpanded();
            if (isSpaceChar(c)) {
                // Let's push it back in case it's LF, to be handled properly
                --mInputPtr;
                c = skipDtdWs(true);

                /* 26-Jan-2006, TSa: actually there are edge cases where
                 *   we may get the attribute name right away (esp.
                 *   with PEs...); so let's defer possible error for
                 *   later on. Should not allow missing spaces between
                 *   attribute declarations... ?
                 */
                /*
            } else if (c != '>') {
                throwDTDUnexpectedChar(c, "; excepted either '>' closing ATTLIST declaration, or a white space character separating individual attribute declarations");
                */
            }
            if (c == '>') {
                break;
            }
            handleAttrDecl(elem, c, index, loc);
            ++index;
        }
    }

    private void handleElementDecl()
        throws XMLStreamException
    {
        char c = skipObligatoryDtdWs();
        final PrefixedName elemName = readDTDQName(c);

        /* Ok, event needs to know its exact starting point (opening '<'
         * char), let's get that info now (note: data has been preserved
         * earlier)
         */
        Location loc = getStartLocation();

        // Ok; name got, need some white space next
        c = skipObligatoryDtdWs();

        /* Then the content spec: either a special case (ANY, EMPTY), or
         * a parenthesis group for 'real' content spec
         */
        StructValidator val = null;
        int vldContent = XMLValidator.CONTENT_ALLOW_ANY_TEXT;

        if (c == '(') { // real content model
            c = skipDtdWs(true);
            if (c == '#') {
                val = readMixedSpec(elemName, mCfgFullyValidating);
                vldContent = XMLValidator.CONTENT_ALLOW_ANY_TEXT; // checked against DTD
            } else {
                --mInputPtr; // let's push it back...
                ContentSpec spec = readContentSpec(elemName, true, mCfgFullyValidating);
                val = spec.getSimpleValidator();
                if (val == null) {
                    val = new DFAValidator(DFAState.constructDFA(spec));
                }
                vldContent = XMLValidator.CONTENT_ALLOW_WS; // checked against DTD
            }
        } else if (isNameStartChar(c)) {
            do { // dummy loop to allow break:
                String keyw = null;
                if (c == 'A') {
                    keyw = checkDTDKeyword("NY");
                    if (keyw == null) {
                        vldContent = XMLValidator.CONTENT_ALLOW_ANY_TEXT; // no DTD checks
                        break;
                    }
                    keyw = "A"+keyw;
                } else if (c == 'E') {
                    keyw = checkDTDKeyword("MPTY");
                    if (keyw == null) {
                        val = EmptyValidator.getPcdataInstance();
                        vldContent = XMLValidator.CONTENT_ALLOW_NONE; // needed to prevent non-elements too
                        break;
                    }
                    keyw = "E"+keyw;
                } else {
                    --mInputPtr;
                    keyw = readDTDKeyword(String.valueOf(c));
                }
                _reportWFCViolation("Unrecognized DTD content spec keyword '"
                                +keyw+"' (for element <"+elemName+">); expected ANY or EMPTY");
             } while (false);
        } else {
            throwDTDUnexpectedChar(c, ": excepted '(' to start content specification for element <"+elemName+">");
        }

        // Ok, still need the trailing gt-char to close the declaration:
        c = skipDtdWs(true);
        if (c != '>') {
            throwDTDUnexpectedChar(c, "; expected '>' to finish the element declaration for <"+elemName+">");
        }

        LinkedHashMap<PrefixedName,DTDElement> m = getElementMap();
        DTDElement oldElem = m.get(elemName);
        // Ok to have it if it's not 'really' declared

        if (oldElem != null) {
            if (oldElem.isDefined()) { // oops, a problem!
                /* 03-Feb-2006, TSa: Hmmh. Apparently all other XML parsers
                 *    consider it's ok in non-validating mode. All right.
                 */
                if (mCfgFullyValidating) {
                    DTDSubsetImpl.throwElementException(oldElem, loc);
                } else {
                    // let's just ignore re-definition if not validating
                    return;
                }
            }

            /* 09-Sep-2004, TSa: Need to transfer existing attribute
             *   definitions, however...
             */
            oldElem = oldElem.define(loc, val, vldContent);
        } else {
            // Sweet, let's then add the definition:
            oldElem = DTDElement.createDefined(mConfig, loc, elemName, val, vldContent);
        }
        m.put(elemName, oldElem);
    }

    /**
     * This method is tricky to implement, since it can contain parameter
     * entities in multiple combinations... and yet declare one as well.
     *
     * @param suppressPEDecl If true, will need to take of enabling/disabling
     *   of flattened output.
     */
    private void handleEntityDecl(boolean suppressPEDecl)
        throws XMLStreamException
    {
        /* Hmmh. It seems that PE reference are actually accepted
         * even here... which makes distinguishing definition from
         * reference bit challenging.
         */
        char c = dtdNextFromCurr();
        boolean gotSeparator = false;
        boolean isParam = false;

        while (true) {
            if (c == '%') { // reference?
                // note: end-of-block acceptable, same as space
                char d = dtdNextIfAvailable();
                if (d == CHAR_NULL || isSpaceChar(d)) { // ok, PE declaration
                    isParam = true;
                    if (d == '\n' || c == '\r') {
                        skipCRLF(d);
                    }
                    break;
                }
                // Reference?
                if (!isNameStartChar(d)) {
                    throwDTDUnexpectedChar(d, "; expected a space (for PE declaration) or PE reference name");
                }
                --mInputPtr; // need to push the first char back, then
                gotSeparator = true;
                expandPE();
                // need the next char, from the new scope... or if it gets closed, this one
                c = dtdNextChar();
            } else if (!isSpaceChar(c)) { // non-PE entity?
                break;
            } else {
                gotSeparator = true;
                c = dtdNextFromCurr();
            }
        }

        if (!gotSeparator) {
            throwDTDUnexpectedChar(c, "; expected a space separating ENTITY keyword and entity name");
        }

        /* Ok; fair enough: now must have either '%', or a name start
         * character:
         */
        if (isParam) {
            /* PE definition: at this point we already know that there must
             * have been a space... just need to skip the rest, if any.
             * Also, can still get a PE to expand (name of a PE to define
             * from a PE reference)
             */
            c = skipDtdWs(true);
        }

        if (suppressPEDecl) { // only if mFlattenWriter != null
            if (!isParam) {
                mFlattenWriter.enableOutput(mInputPtr);
                mFlattenWriter.output("<!ENTITY ");
                mFlattenWriter.output(c);
            }
        }

        // Need a name char, then
        String id = readDTDName(c);

        /* Ok, event needs to know its exact starting point (opening '<'
         * char), let's get that info now (note: data has been preserved
         * earlier)
         */
        Location evtLoc = getStartLocation();
        EntityDecl ent;

        try {
            c = skipObligatoryDtdWs();
            if (c == '\'' || c == '"') { // internal entity
                /* Let's get the exact location of actual content, not the
                 * opening quote. To do that, need to 'peek' next char, then
                 * push it back:
                 */
                /*char foo =*/ dtdNextFromCurr();
                Location contentLoc = getLastCharLocation();
                --mInputPtr; // pushback
                char[] contents = parseEntityValue(id, contentLoc, c);
                try {
                	ent = new IntEntity(evtLoc, id, getSource(), contents, contentLoc);
                } catch (IOException e) {
                	throw new WstxIOException(e);
                }
            } else {
                if (!isNameStartChar(c)) {
                    throwDTDUnexpectedChar(c, "; expected either quoted value, or keyword 'PUBLIC' or 'SYSTEM'");
                }
                ent = handleExternalEntityDecl(mInput, isParam, id, c, evtLoc);
            }

            /* 05-Mar-2006, TSa: Need to know which entities came from the
             *    external subset; these can not be used if the xml document
             *    is declared as "standalone='yes'".
             */
            if (mIsExternal) {
                ent.markAsExternallyDeclared();
            }
        } finally {
            /* Ok; one way or the other, entity declaration contents have now
             * been read in.
             */
            if (suppressPEDecl && isParam) {
                mFlattenWriter.enableOutput(mInputPtr);
            }
        }

        // Ok, got it!
        HashMap<String,EntityDecl> m;
        if (isParam) {
            m = mParamEntities;
            if (m == null) {
                mParamEntities = m = new HashMap<String,EntityDecl>();
            }
        } else {
            m = mGeneralEntities;
            if (m == null) {
                /* Let's try to get insert-ordered Map, to be able to
                 * report redefinition problems when validating subset
                 * compatibility
                 */
                mGeneralEntities = m = new LinkedHashMap<String,EntityDecl>();
            }
        }

        // First definition sticks...
        Object old;
        if (m.size() > 0 && (old = m.get(id)) != null) {
            // Application may want to know about the problem...
            XMLReporter rep = mConfig.getXMLReporter();
            if (rep != null) {
                EntityDecl oldED = (EntityDecl) old;
                String str = " entity '"+id+"' defined more than once: first declaration at "
                    + oldED.getLocation();
                if (isParam) {
                    str = "Parameter" + str;
                } else {
                    str = "General" + str;
                }
                _reportWarning(rep, ErrorConsts.WT_ENT_DECL, str, evtLoc);
            }
        } else {
            m.put(id, ent);
        }

        // And finally, let's notify listener, if we have one...
        if (mEventListener != null) {
            if (ent.isParsed()) { // Parsed GE or PE
            } else { // unparsed GE
            	final URL src;
            	try {
            		src = mInput.getSource();
                } catch (IOException e) {
                	throw new WstxIOException(e);
                }
                mEventListener.dtdUnparsedEntityDecl(id, ent.getPublicId(), ent.getSystemId(), ent.getNotationName(), src);
            }
        }
    }

    /**
     * Method called to handle <!NOTATION ... > declaration.
     */
    private void handleNotationDecl()
        throws XMLStreamException
    {
        char c = skipObligatoryDtdWs();
        String id = readDTDName(c);

        c = skipObligatoryDtdWs();
        boolean isPublic = checkPublicSystemKeyword(c);

        String pubId, sysId;

        c = skipObligatoryDtdWs();

        // Ok, now we can parse the reference; first public id if needed:
        if (isPublic) {
            if (c != '"' && c != '\'') {
                throwDTDUnexpectedChar(c, "; expected a quote to start the public identifier");
            }
            pubId = parsePublicId(c, getErrorMsg());
            c = skipDtdWs(true);
        } else {
            pubId = null;
        }

        /* And then we may need the system id; one NOTATION oddity, if
         * there's public id, system one is optional.
         */
        if (c == '"' || c == '\'') {
            sysId = parseSystemId(c, mNormalizeLFs, getErrorMsg());
            c = skipDtdWs(true);
        } else {
            if (!isPublic) {
                throwDTDUnexpectedChar(c, "; expected a quote to start the system identifier");
            }
            sysId = null;
        }

        // And then we should get the closing '>'
        if (c != '>') {
            throwDTDUnexpectedChar(c, "; expected closing '>' after NOTATION declaration");
        }
        URL baseURL;
        try {
        	baseURL = mInput.getSource();
        } catch (IOException e) {
        	throw new WstxIOException(e);
        }

        // Any external listeners?
        if (mEventListener != null) {
            mEventListener.dtdNotationDecl(id, pubId, sysId, baseURL);
        }

        /* Ok, event needs to know its exact starting point (opening '<'
         * char), let's get that info now (note: data has been preserved
         * earlier)
         */
        Location evtLoc = getStartLocation();
        NotationDeclaration nd = new WNotationDeclaration(evtLoc, id, pubId, sysId, baseURL);

        // Any definitions from the internal subset?
        if (mPredefdNotations != null) {
            NotationDeclaration oldDecl = mPredefdNotations.get(id);
            if (oldDecl != null) { // oops, a problem!
                DTDSubsetImpl.throwNotationException(oldDecl, nd);
            }
        }

        HashMap<String,NotationDeclaration> m = mNotations;
        if (m == null) {
            /* Let's try to get insert-ordered Map, to be able to
             * report redefinition problems in proper order when validating
             * subset compatibility
             */
            mNotations = m = new LinkedHashMap<String,NotationDeclaration>();
        } else {
            NotationDeclaration oldDecl = m.get(id);
            if (oldDecl != null) { // oops, a problem!
                DTDSubsetImpl.throwNotationException(oldDecl, nd);
            }
        }
        // Does this resolve a dangling reference?
        if (mNotationForwardRefs != null) {
            mNotationForwardRefs.remove(id);
        }
        m.put(id, nd);
    }

    /**
     * Method called to handle <!TARGETNS ... > declaration (the only
     * new declaration type for DTD++)
     *<p>
     * Note: only valid for DTD++, in 'plain DTD' mode shouldn't get
     * called.
     */
    private void handleTargetNsDecl()
        throws XMLStreamException
    {
        mAnyDTDppFeatures = true;
        
        char c = skipObligatoryDtdWs();
        String name;
        
        // Explicit namespace name?
        if (isNameStartChar(c)) {
            name = readDTDLocalName(c, false);
            c = skipObligatoryDtdWs();
        } else { // no, default namespace (or error)
            name = null;
        }
        
        // Either way, should now get a quote:
        if (c != '"' && c != '\'') {
            if (c == '>') { // slightly more accurate error
                _reportWFCViolation("Missing namespace URI for TARGETNS directive");
            }
            throwDTDUnexpectedChar(c, "; expected a single or double quote to enclose the namespace URI");
        }
        
        /* !!! 07-Nov-2004, TSa: what's the exact value we should get
         *   here? Ns declarations can have any attr value...
         */
        String uri = parseSystemId(c, false, "in namespace URI");
        
        // Do we need to normalize the URI?
        if ((mConfigFlags & CFG_INTERN_NS_URIS) != 0) {
            uri = InternCache.getInstance().intern(uri);
        }
        
        // Ok, and then the closing '>':
        c = skipDtdWs(true);
        if (c != '>') {
            throwDTDUnexpectedChar(c, "; expected '>' to end TARGETNS directive");
        }
        
        if (name == null) { // default NS URI
            mDefaultNsURI = uri;
        } else {
            if (mNamespaces == null) {
                mNamespaces = new HashMap<String,String>();
            }
            mNamespaces.put(name, uri);
        }
    }

    /*
    ///////////////////////////////////////////////////////////
    // Internal methods, secondary decl parsing methods
    ///////////////////////////////////////////////////////////
     */

    /**
     * @param elem Element that contains this attribute
     * @param c First character of what should be the attribute name
     * @param index Sequential index number of this attribute as children
     *    of the element; used for creating bit masks later on.
     * @param loc Location of the element name in attribute list declaration
     */
    private void handleAttrDecl(DTDElement elem, char c, int index,
                                Location loc)
        throws XMLStreamException
    {
        // First attribute name
        PrefixedName attrName = readDTDQName(c);

        // then type:
        c = skipObligatoryDtdWs();

        int type = 0;
        WordResolver enumValues = null;
        
        if (c == '(') { // enumerated type
            enumValues = parseEnumerated(elem, attrName, false);
            type = DTDAttribute.TYPE_ENUMERATED;
        } else {
            String typeStr = readDTDName(c);
            
            dummy:
            do { // dummy loop
                switch (typeStr.charAt(0)) {
                case 'C': // CDATA
                    if (typeStr == "CDATA") {
                        type = DTDAttribute.TYPE_CDATA;
                        break dummy;
                    }
                    break;
                case 'I': // ID, IDREF, IDREFS
                    if (typeStr == "ID") {
                        type = DTDAttribute.TYPE_ID;
                        break dummy;
                    } else if (typeStr == "IDREF") {
                        type = DTDAttribute.TYPE_IDREF;
                        break dummy;
                    } else if (typeStr == "IDREFS") {
                        type = DTDAttribute.TYPE_IDREFS;
                        break dummy;
                    }
                    break;
                case 'E': // ENTITY, ENTITIES
                    if (typeStr == "ENTITY") {
                        type = DTDAttribute.TYPE_ENTITY;
                        break dummy;
                    } else if (typeStr == "ENTITIES") {
                        type = DTDAttribute.TYPE_ENTITIES;
                        break dummy;
                    }
                    break;
                case 'N': // NOTATION, NMTOKEN, NMTOKENS
                    if (typeStr == "NOTATION") {
                        type = DTDAttribute.TYPE_NOTATION;
                        /* Special case; is followed by a list of
                         * enumerated ids...
                         */
                        c = skipObligatoryDtdWs();
                        if (c != '(') {
                            throwDTDUnexpectedChar(c, "Excepted '(' to start the list of NOTATION ids");
                        }
                        enumValues = parseEnumerated(elem, attrName, true);
                        break dummy;
                    } else if (typeStr == "NMTOKEN") {
                        type = DTDAttribute.TYPE_NMTOKEN;
                        break dummy;
                    } else if (typeStr == "NMTOKENS") {
                        type = DTDAttribute.TYPE_NMTOKENS;
                        break dummy;
                    }
                    break;
                }

                // Problem:
                throwDTDAttrError("Unrecognized attribute type '"+typeStr+"'"
                                   +ErrorConsts.ERR_DTD_ATTR_TYPE,
                                   elem, attrName);
            } while (false);
        }
        DefaultAttrValue defVal;

        // Ok, and how about the default declaration?
        c = skipObligatoryDtdWs();
        if (c == '#') {
            String defTypeStr = readDTDName(getNextExpanded());
            if (defTypeStr == "REQUIRED") {
                defVal = DefaultAttrValue.constructRequired();
            } else if (defTypeStr == "IMPLIED") {
                defVal = DefaultAttrValue.constructImplied();
            } else if (defTypeStr == "FIXED") {
                defVal = DefaultAttrValue.constructFixed();
                c = skipObligatoryDtdWs();
                parseAttrDefaultValue(defVal, c, attrName, loc, true);
            } else {
                throwDTDAttrError("Unrecognized attribute default value directive #"+defTypeStr
                                   +ErrorConsts.ERR_DTD_DEFAULT_TYPE,
                                   elem, attrName);
                defVal = null; // never gets here...
            }
        } else {
            defVal = DefaultAttrValue.constructOptional();
            parseAttrDefaultValue(defVal, c, attrName, loc, false);
        }

        /* There are some checks that can/need to be done now, such as:
         *
         * - [#3.3.1/VC: ID Attribute default] def. value type can not
         *   be #FIXED
         */
        if (type == DTDAttribute.TYPE_ID && defVal.hasDefaultValue()) {
            // Just a VC, not WFC... so:
            if (mCfgFullyValidating) {
                throwDTDAttrError("has type ID; can not have a default (or #FIXED) value (XML 1.0/#3.3.1)",
                                  elem, attrName);
            }
        } else { // not an ID... shouldn't be xml:id, then
            if (mConfig.willDoXmlIdTyping()) {
                if (attrName.isXmlReservedAttr(mCfgNsEnabled, "id")) {
                    // 26-Sep-2006, TSa: For [WSTX-22], need to verify 'xml:id'
                    checkXmlIdAttr(type);
                }
            }
        }

        /* 01-Sep-2006, TSa: To address [WSTX-23], we should verify declaration
         *  of 'xml:space' attribute
         */
        if (attrName.isXmlReservedAttr(mCfgNsEnabled, "space")) {
            checkXmlSpaceAttr(type, enumValues);
        }

        DTDAttribute attr;

        /* 17-Feb-2006, TSa: Ok. So some (legacy?) DTDs do declare namespace
         *    declarations too... sometimes including default values.
         */
        if (mCfgNsEnabled && attrName.isaNsDeclaration()) { // only check in ns mode
            /* Ok: just declaring them is unnecessary, and can be safely
             * ignored. It's only the default values that matter (and yes,
             * let's not worry about #REQUIRED for now)
             */
            if (!defVal.hasDefaultValue()) {
                return;
            }
            // But defaulting... Hmmh.
            attr = elem.addNsDefault(this, attrName, type,
                                     defVal, mCfgFullyValidating);
        } else {
            attr = elem.addAttribute(this, attrName, type,
                                     defVal, enumValues,
                                     mCfgFullyValidating);
        }

        // getting null means this is a dup...
        if (attr == null) {
            // anyone interested in knowing about possible problem?
            XMLReporter rep = mConfig.getXMLReporter();
            if (rep != null) {
                String msg = MessageFormat.format(ErrorConsts.W_DTD_ATTR_REDECL, new Object[] { attrName, elem });
                _reportWarning(rep, ErrorConsts.WT_ATTR_DECL, msg, loc);
            }
        } else {
            if (defVal.hasDefaultValue()) {
                // always normalize
                attr.normalizeDefault();
                // but only validate in validating mode:
                if (mCfgFullyValidating) {
                    attr.validateDefault(this, true);
                }
            }
        }
    }

    /**
     * Parsing method that reads a list of one or more space-separated
     * tokens (nmtoken or name, depending on 'isNotation' argument)
     */
    private WordResolver parseEnumerated(DTDElement elem, PrefixedName attrName,
                                         boolean isNotation)
        throws XMLStreamException
    {
        /* Need to use tree set to be able to construct the data
         * structs we need later on...
         */
        TreeSet<String> set = new TreeSet<String>();

        char c = skipDtdWs(true);
        if (c == ')') { // just to give more meaningful error msgs
            throwDTDUnexpectedChar(c, " (empty list; missing identifier(s))?");
        }

        HashMap<String,String> sharedEnums;

        if (isNotation) {
            sharedEnums = null;
        } else {
            sharedEnums = mSharedEnumValues;
            if (sharedEnums == null && !isNotation) {
                mSharedEnumValues = sharedEnums = new HashMap<String,String>();
            }
        }

        String id = isNotation ? readNotationEntry(c, attrName, elem.getLocation())
            : readEnumEntry(c, sharedEnums);
        set.add(id);
        
        while (true) {
            c = skipDtdWs(true);
            if (c == ')') {
                break;
            }
            if (c != '|') {
                throwDTDUnexpectedChar(c, "; missing '|' separator?");
            }
            c = skipDtdWs(true);
            id = isNotation ? readNotationEntry(c, attrName, elem.getLocation())
                : readEnumEntry(c, sharedEnums);
            if (!set.add(id)) {
                /* 03-Feb-2006, TSa: Hmmh. Apparently all other XML parsers
                 *    consider it's ok in non-validating mode. All right.
                 */
                if (mCfgFullyValidating) {
                    throwDTDAttrError("Duplicate enumeration value '"+id+"'",
                                      elem, attrName);
                }
            }
        }

        // Ok, let's construct the minimal data struct, then:
        return WordResolver.constructInstance(set);
    }

    /**
     * Method called to read a notation reference entry; done both for
     * attributes of type NOTATION, and for external unparsed entities
     * that refer to a notation. In both cases, notation referenced
     * needs to have been defined earlier; but only if we are building
     * a fully validating DTD subset object (there is the alternative
     * of a minimal DTD in DTD-aware mode, which does no validation
     * but allows attribute defaulting and normalization, as well as
     * access to entity and notation declarations).
     * 
     * @param attrName Name of attribute in declaration that refers to this entity
     *
     * @param refLoc Starting location of the DTD component that contains
     *   the reference
     */
    private String readNotationEntry(char c, PrefixedName attrName, Location refLoc)
        throws XMLStreamException
    {
        String id = readDTDName(c);

        /* Need to check whether we have a reference to a "pre-defined"
         * notation: pre-defined here means that it was defined in the
         * internal subset (prior to this parsing which then would external
         * subset). This is needed to know if the subset can be cached or
         * not.
         */
        if (mPredefdNotations != null) {
            NotationDeclaration decl = mPredefdNotations.get(id);
            if (decl != null) {
                mUsesPredefdNotations = true;
                return decl.getName();
            }
        }

        NotationDeclaration decl = (mNotations == null) ? null :mNotations.get(id);
        if (decl == null) {
            // In validating mode, this may be a problem (otherwise not)
            if (mCfgFullyValidating) {
                if (mNotationForwardRefs == null) {
                    mNotationForwardRefs = new LinkedHashMap<String,Location>();
                }
                mNotationForwardRefs.put(id, refLoc);
            }
            return id;
        }
        return decl.getName();
    }

    private String readEnumEntry(char c, HashMap<String,String> sharedEnums)
        throws XMLStreamException
    {
        String id = readDTDNmtoken(c);

        /* Let's make sure it's shared for this DTD subset; saves memory
         * both for DTDs and resulting docs. Could also intern Strings?
         */
        String sid = sharedEnums.get(id);
        if (sid == null) {
            sid = id;
            if (INTERN_SHARED_NAMES) {
                /* 19-Nov-2004, TSa: Let's not use intern cache here...
                 *   shouldn't be performance critical (DTDs themselves
                 *   cached), and would add more entries to cache.
                 */
                sid = sid.intern();
            }
            sharedEnums.put(sid, sid);
        }
        return sid;
    }


    /**
     * Method called to parse what seems like a mixed content specification.
     *
     * @param construct If true, will build full object for validating content
     *   within mixed content model; if false, will just parse and discard
     *   information (done in non-validating DTD-supporting mode)
     */
    private StructValidator readMixedSpec(PrefixedName elemName, boolean construct)
        throws XMLStreamException
    {
        String keyw = checkDTDKeyword("PCDATA");
        if (keyw != null) {
            _reportWFCViolation("Unrecognized directive #"+keyw+"'; expected #PCDATA (or element name)");
        }

        HashMap<PrefixedName,ContentSpec> m = new LinkedHashMap<PrefixedName,ContentSpec>();
        while (true) {
            char c = skipDtdWs(true);
            if (c == ')') {
                break;
            }
            if (c == '|') {
                c = skipDtdWs(true);
            } else if (c == ',') {
                throwDTDUnexpectedChar(c, " (sequences not allowed within mixed content)");
            } else if (c == '(') {
                throwDTDUnexpectedChar(c, " (sub-content specs not allowed within mixed content)");
            } else {
                throwDTDUnexpectedChar(c, "; expected either '|' to separate elements, or ')' to close the list");
            }
            PrefixedName n = readDTDQName(c);
            Object old = m.put(n, TokenContentSpec.construct(' ', n));
            if (old != null) {
                /* 03-Feb-2006, TSa: Hmmh. Apparently all other XML parsers
                 *    consider it's ok in non-validating mode. All right.
                 */
                if (mCfgFullyValidating) {
                    throwDTDElemError("duplicate child element <"+n+"> in mixed content model",
                                      elemName);
                }
            }
        }

        /* One more check: can have a trailing asterisk; in fact, have
         * to have one if there were any elements.
         */
        char c = (mInputPtr < mInputEnd) ?
            mInputBuffer[mInputPtr++] : getNextChar(getErrorMsg());
        if (c != '*') {
            if (m.size() > 0) {
                _reportWFCViolation("Missing trailing '*' after a non-empty mixed content specification");
            }
            --mInputPtr; // need to push it back
        }
        if (!construct) { // no one cares?
            return null;
        }

        /* Without elements, it's considered "pure" PCDATA, which can use a
         * specific 'empty' validator:
         */
        if (m.isEmpty()) {
            return EmptyValidator.getPcdataInstance();
        }
        ContentSpec spec = ChoiceContentSpec.constructMixed(mCfgNsEnabled, m.values());
        StructValidator val = spec.getSimpleValidator();
        if (val == null) {
            DFAState dfa = DFAState.constructDFA(spec);
            val = new DFAValidator(dfa);
        }
        return val;
    }

    /**
	 * @param mainLevel Whether this is the main-level content specification or nested 
	 */
    private ContentSpec readContentSpec(PrefixedName elemName, boolean mainLevel,
                                        boolean construct)
        throws XMLStreamException
    {
        ArrayList<ContentSpec> subSpecs = new ArrayList<ContentSpec>();
        boolean isChoice = false; // default to sequence
        boolean choiceSet = false;

        while (true) {
            char c = skipDtdWs(true);
            if (c == ')') {
                // Need to have had at least one entry...
                if (subSpecs.isEmpty()) {
                    _reportWFCViolation("Empty content specification for '"+elemName+"' (need at least one entry)");
                }
                break;
            }
            if (c == '|' || c == ',') { // choice/seq indicator
                boolean newChoice = (c == '|');
                if (!choiceSet) {
                    isChoice = newChoice;
                    choiceSet = true;
                } else {
                    if (isChoice != newChoice) {
                        _reportWFCViolation("Can not mix content spec separators ('|' and ','); need to use parenthesis groups");
                    }
                }
                c = skipDtdWs(true);
            } else {
                // Need separator between subspecs...
                if (!subSpecs.isEmpty()) {
                    throwDTDUnexpectedChar(c, " (missing separator '|' or ','?)");
                }
            }
            if (c == '(') {
                ContentSpec cs = readContentSpec(elemName, false, construct);
                subSpecs.add(cs);
                continue;
            }

            // Just to get better error messages:
            if (c == '|' || c == ',') {
                throwDTDUnexpectedChar(c, " (missing element name?)");
            }
            PrefixedName thisName = readDTDQName(c);

            /* Now... it's also legal to directly tag arity marker to a
             * single element name, too...
             */
            char arity = readArity();
            ContentSpec cs = construct ?
                TokenContentSpec.construct(arity, thisName)
                : TokenContentSpec.getDummySpec();
            subSpecs.add(cs);
        }
        
        char arity = readArity();

        /* Not really interested in constructing anything? Let's just
         * return the dummy placeholder.
         */
        if (!construct) {
            return TokenContentSpec.getDummySpec();
        }

        // Just one entry? Can just return it as is, combining arities
        if (subSpecs.size() == 1) {
            ContentSpec cs = subSpecs.get(0);
            char otherArity = cs.getArity();
            if (arity != otherArity) {
                cs.setArity(combineArities(arity, otherArity));
            }
            return cs;
        }

        if (isChoice) {
            return ChoiceContentSpec.constructChoice(mCfgNsEnabled, arity, subSpecs);
        }
        return SeqContentSpec.construct(mCfgNsEnabled, arity, subSpecs);
    }

    private static char combineArities(char arity1, char arity2)
    {
        if (arity1 == arity2) {
            return arity1;
        }

        // no modifier doesn't matter:
        if (arity1 == ' ') {
            return arity2;
        }
        if (arity2 == ' ') {
            return arity1;
        }
        // Asterisk is most liberal, supercedes others:
        if (arity1 == '*' || arity2 == '*') {
            return '*';
        }

        /* Ok, can only have '+' and '?'; which combine to
         * '*'
         */
        return '*';
    }

    /**
     * Method that handles rest of external entity declaration, after
     * it's been figured out entity is not internal (does not continue
     * with a quote).
     * 
     * @param inputSource Input source for the start of the declaration.
     *   Needed for resolving relative system references, if any.
     * @param isParam True if this a parameter entity declaration; false
     *    if general entity declaration
     * @param evtLoc Location where entity declaration directive started;
     *   needed when construction event Objects for declarations.
     */
    private EntityDecl handleExternalEntityDecl(WstxInputSource inputSource,
                                                boolean isParam, String id,
                                                char c, Location evtLoc)
        throws XMLStreamException
    {
        boolean isPublic = checkPublicSystemKeyword(c);

        String pubId = null;

        // Ok, now we can parse the reference; first public id if needed:
        if (isPublic) {
            c = skipObligatoryDtdWs();
            if (c != '"' && c != '\'') {
                throwDTDUnexpectedChar(c, "; expected a quote to start the public identifier");
            }
            pubId = parsePublicId(c, getErrorMsg());
            /* 30-Sep-2005, TSa: SGML has public ids that miss the system
             *   id. Although not legal with XML DTDs, let's give bit more
             *   meaningful error in those cases...
             */
            c = getNextExpanded();
            if (c <= CHAR_SPACE) { // good
                c = skipDtdWs(true);
            } else { // not good...
                // Let's just push it back and generate normal error then:
                if (c != '>') { // this is handled below though
                    --mInputPtr;
                    c = skipObligatoryDtdWs();
                }
            }

            /* But here let's deal with one case that we are familiar with:
             * SGML does NOT require system id after public one...
             */
            if (c == '>') {
                _reportWFCViolation("Unexpected end of ENTITY declaration (expected a system id after public id): trying to use an SGML DTD instead of XML one?");
            }
        } else {
            // Just need some white space here
            c = skipObligatoryDtdWs();
        }
        if (c != '"' && c != '\'') {
            throwDTDUnexpectedChar(c, "; expected a quote to start the system identifier");
        }
        String sysId = parseSystemId(c, mNormalizeLFs, getErrorMsg());

        // Ok; how about notation?
        String notationId = null;

        /* Ok; PEs are simpler, as they always are parsed (see production
         *   #72 in xml 1.0 specs)
         */
        if (isParam) {
            c = skipDtdWs(true);
        } else {
            /* GEs can be unparsed, too, so it's bit more complicated;
             * if we get '>', don't need space; otherwise need separating
             * space (or PE boundary). Thus, need bit more code.
             */
            int i = peekNext();
            if (i == '>') { // good
                c = '>';
                ++mInputPtr;
            } else if (i < 0) { // local EOF, ok
                c = skipDtdWs(true);
            } else if (i == '%') {
                c = getNextExpanded();
            } else {
                ++mInputPtr;
                c = (char) i;
                if (!isSpaceChar(c)) {
                    throwDTDUnexpectedChar(c, "; expected a separating space or closing '>'");
                }
                c = skipDtdWs(true);
            }

            if (c != '>') {
                if (!isNameStartChar(c)) {
                    throwDTDUnexpectedChar(c, "; expected either NDATA keyword, or closing '>'");
                }
                String keyw = checkDTDKeyword("DATA");
                if (keyw != null) {
                    _reportWFCViolation("Unrecognized keyword '"+keyw+"'; expected NOTATION (or closing '>')");
                }
                c = skipObligatoryDtdWs();
                notationId = readNotationEntry(c, null, evtLoc);
                c = skipDtdWs(true);
            }
        }

        // Ok, better have '>' now:
        if (c != '>') {
            throwDTDUnexpectedChar(c, "; expected closing '>'");
        }

        URL ctxt;
        try {
        	ctxt = inputSource.getSource();
        } catch (IOException e) {
        	throw new WstxIOException(e);
        }
        if (notationId == null) { // parsed entity:
            return new ParsedExtEntity(evtLoc, id, ctxt, pubId, sysId);
        }
        return new UnparsedExtEntity(evtLoc, id, ctxt, pubId, sysId, notationId);
    }

    /*
    ///////////////////////////////////////////////////////////
    // Data struct access
    ///////////////////////////////////////////////////////////
     */

    private LinkedHashMap<PrefixedName,DTDElement> getElementMap() {
        LinkedHashMap<PrefixedName,DTDElement> m = mElements;
        if (m == null) {
            /* Let's try to get insert-ordered Map, to be able to
             * report redefinition problems in proper order when validating
             * subset compatibility
             */
            mElements = m = new LinkedHashMap<PrefixedName,DTDElement>();
        }
        return m;
    }

    final PrefixedName mAccessKey = new PrefixedName(null, null);

    /**
     * Method used to 'intern()' qualified names; main benefit is reduced
     * memory usage as the name objects are shared. May also slightly
     * speed up Map access, as more often identity comparisons catch
     * matches.
     *<p>
     * Note: it is assumed at this point that access is only from a single
     * thread, and non-recursive -- generally valid assumption as readers are
     * not shared. Restriction is needed since the method is not re-entrant:
     * it uses mAccessKey during the method call.
     */
    private PrefixedName findSharedName(String prefix, String localName)
    {
        HashMap<PrefixedName,PrefixedName> m = mSharedNames;

        if (mSharedNames == null) {
            mSharedNames = m = new HashMap<PrefixedName,PrefixedName>();
        } else {
            // Maybe we already have a shared instance... ?
            PrefixedName key = mAccessKey;
            key.reset(prefix, localName);
            key = m.get(key);
            if (key != null) { // gotcha
                return key;
            }
        }

        // Not found; let's create, cache and return it:
        PrefixedName result = new PrefixedName(prefix, localName);
        m.put(result, result);
        return result;
    }

    /*
    ///////////////////////////////////////////////////////////
    // Implementations of abstract methods from StreamScanner
    ///////////////////////////////////////////////////////////
     */

    /**
     * @param arg If Boolean.TRUE, we are expanding a general entity
     *   
     */
    @Override
    protected EntityDecl findEntity(String id, Object arg)
    {
        // Expand a Parameter Entity?
        if (arg == ENTITY_EXP_PE) { // for attribute default
            EntityDecl ed = (mPredefdPEs == null) ? null : mPredefdPEs.get(id);
            if (ed != null) { // Entity from int. subset...
                mUsesPredefdEntities = true;
                /* No need to further keep track of internal references,
                 * since this subset can not be cached, so let's just free
                 * up Map if it has been created
                 */
                mRefdPEs = null;
            } else if (mParamEntities != null) {
                ed = mParamEntities.get(id);
                if (ed != null) {
                    if (!mUsesPredefdEntities) {
                        // Let's also mark down the fact we referenced the entity:
                        Set<String> used = mRefdPEs;
                        if (used == null) {
                            mRefdPEs = used = new HashSet<String>();
                        }
                        used.add(id);
                    }
                }
            }
            return ed;
        }

        // Nope, General Entity (within attribute default value)?
        if (arg == ENTITY_EXP_GE) {
            /* This is only complicated for external subsets, since
             * they may 'inherit' entity definitions from preceding
             * internal subset...
             */
            EntityDecl ed = (mPredefdGEs == null) ? null : mPredefdGEs.get(id);
            if (ed != null) {
                mUsesPredefdEntities = true;
                /* No need to further keep track of references,
                 * as this means this subset is not cachable...
                 * so let's just free up Map if it has been created
                 */
                mRefdGEs = null;
            } else if (mGeneralEntities != null) {
                ed = mGeneralEntities.get(id);
                if (ed != null) {
                    // Ok, just need to mark reference, if we still care:
                    if (!mUsesPredefdEntities) {
                        // Let's also mark down the fact we referenced the entity:
                        if (mRefdGEs == null) {
                            mRefdGEs = new HashSet<String>();
                        }
                        mRefdGEs.add(id);
                    }
                }
            }
            return ed;
        }

        throw new IllegalStateException(ErrorConsts.ERR_INTERNAL);
    }

    /**
     * Undeclared parameter entity is a VC, not WFC...
     */
    @Override
    protected void handleUndeclaredEntity(String id)
        throws XMLStreamException
    {
        _reportVCViolation("Undeclared parameter entity '"+id+"'.");
        if (mCurrAttrDefault != null) {
            Location loc = getLastCharLocation();
            if (mExpandingPE) {
                mCurrAttrDefault.addUndeclaredPE(id, loc);
            } else {
                mCurrAttrDefault.addUndeclaredGE(id, loc);
            }
        }
        if (mEventListener != null) {
            // GEs only matter when expanding...
            if (mExpandingPE) {
                mEventListener.dtdSkippedEntity("%"+id);
            }
        }
    }

    /**
     * Handling of PE matching problems is actually intricate; one type
     * will be a WFC ("PE Between Declarations", which refers to PEs that
     * start from outside declarations), and another just a VC
     * ("Proper Declaration/PE Nesting", when PE is contained within
     * declaration)
     */
    @Override
    protected void handleIncompleteEntityProblem(WstxInputSource closing)
        throws XMLStreamException
    {
        // Did it start outside of declaration?
        if (closing.getScopeId() == 0) { // yup
            // and being WFC, need not be validating
            _reportWFCViolation(entityDesc(closing) + ": "
                          +"Incomplete PE: has to fully contain a declaration (as per xml 1.0.3, section 2.8, WFC 'PE Between Declarations')");
        } else {
            // whereas the other one is only sent in validating mode..
            if (mCfgFullyValidating) {
                _reportVCViolation(entityDesc(closing) + ": "
                              +"Incomplete PE: has to be fully contained in a declaration (as per xml 1.0.3, section 2.8, VC 'Proper Declaration/PE Nesting')");
            }
        }
    }

    protected void handleGreedyEntityProblem(WstxInputSource input)
        throws XMLStreamException
    {
        // Here it can only be of VC kind...
        if (mCfgFullyValidating) { // since it's a VC, not WFC
            _reportWFCViolation(entityDesc(input) + ": " + 
                              "Unbalanced PE: has to be fully contained in a declaration (as per xml 1.0.3, section 2.8, VC 'Proper Declaration/PE Nesting')");
        }
    }

    /*
    ///////////////////////////////////////////////////////////
    // Additional validity checking
    ///////////////////////////////////////////////////////////
     */

    protected void checkXmlSpaceAttr(int type, WordResolver enumValues)
        throws XMLStreamException
    {
        boolean ok = (type == DTDAttribute.TYPE_ENUMERATED);
        if (ok) {
            switch (enumValues.size()) {
            case 1:
                ok = (enumValues.find("preserve") != null)
                    || (enumValues.find("default") != null);
                break;
            case 2:
                ok = (enumValues.find("preserve") != null)
                    && (enumValues.find("default") != null);
                break;
            default:
                ok = false;
            }
        }
        
        if (!ok) {
            _reportVCViolation(ErrorConsts.ERR_DTD_XML_SPACE);
        }
    }

    protected void checkXmlIdAttr(int type)
        throws XMLStreamException
    {
        if (type != DTDAttribute.TYPE_ID) {
            _reportVCViolation(ErrorConsts.ERR_DTD_XML_ID);
        }
    }

    /*
    ///////////////////////////////////////////////////////////
    // Error handling
    ///////////////////////////////////////////////////////////
     */

    private void _reportWarning(XMLReporter rep, String probType, String msg,
                               Location loc)
        throws XMLStreamException
    {
        if (rep != null) {
            /* Note: since the problem occurs at DTD (schema) parsing,
             * not during validation, can not set reporter.
             */
            XMLValidationProblem prob = new XMLValidationProblem
                (loc, msg, XMLValidationProblem.SEVERITY_WARNING, probType);
            rep.report(msg, probType, prob, loc);
        }
    }
}
