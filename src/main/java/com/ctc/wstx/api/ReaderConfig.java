package com.ctc.wstx.api;

import java.lang.ref.SoftReference;
import java.net.URL;
import java.util.*;

import javax.xml.stream.*;

import org.codehaus.stax2.XMLInputFactory2; // for property consts
import org.codehaus.stax2.XMLStreamProperties; // for property consts
import org.codehaus.stax2.validation.DTDValidationSchema;

import com.ctc.wstx.cfg.InputConfigFlags;
import com.ctc.wstx.dtd.DTDEventListener;
import com.ctc.wstx.ent.IntEntity;
import com.ctc.wstx.ent.EntityDecl;
import com.ctc.wstx.io.BufferRecycler;
import com.ctc.wstx.util.ArgUtil;
import com.ctc.wstx.util.DataUtil;
import com.ctc.wstx.util.SymbolTable;

/**
 * Simple configuration container class; passed by reader factory to reader
 * instance created.
 *<p>
 * In addition to its main task as a configuration container, this class
 * also acts as a wrapper around simple buffer recycling functionality.
 * The reason is that while conceptually this is a separate concern,
 * there are enough commonalities with the life-cycle of this object to
 * make this a very convenience place to add that functionality...
 * (that is: conceptually this is not right, but from pragmatic viewpoint
 * it just makes sense)
 */
public final class ReaderConfig
    extends CommonConfig
    implements InputConfigFlags
{
    // Default limit values
    
    public final static int DEFAULT_MAX_ATTRIBUTES_PER_ELEMENT = 1000;
    public final static int DEFAULT_MAX_ATTRIBUTE_LENGTH = 65536 * 8;

    public final static int DEFAULT_MAX_ELEMENT_DEPTH = 1000;

    public final static int DEFAULT_MAX_ENTITY_DEPTH = 500;
    public final static int DEFAULT_MAX_ENTITY_COUNT = 100 * 1000;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Constants for reader properties:
    ///////////////////////////////////////////////////////////////////////
    */

    // // First, standard StAX properties:

    // Simple flags:
    final static int PROP_COALESCE_TEXT = 1;
    final static int PROP_NAMESPACE_AWARE = 2;
    final static int PROP_REPLACE_ENTITY_REFS = 3;
    final static int PROP_SUPPORT_EXTERNAL_ENTITIES = 4;
    final static int PROP_VALIDATE_AGAINST_DTD = 5;
    final static int PROP_SUPPORT_DTD = 6;

    // Object type properties
    public final static int PROP_EVENT_ALLOCATOR = 7;
    final static int PROP_WARNING_REPORTER = 8;
    final static int PROP_XML_RESOLVER = 9;

    // // Then StAX2 standard properties:

    // Simple flags:
    final static int PROP_INTERN_NS_URIS = 20;
    final static int PROP_INTERN_NAMES = 21;
    final static int PROP_REPORT_CDATA = 22;
    final static int PROP_REPORT_PROLOG_WS = 23;
    final static int PROP_PRESERVE_LOCATION = 24;
    final static int PROP_AUTO_CLOSE_INPUT = 25;

    // Enum / Object type properties:
    final static int PROP_SUPPORT_XMLID = 26; // shared with WriterConfig
    final static int PROP_DTD_OVERRIDE = 27;

    // // // Constants for additional Wstx properties:

    // Simple flags:

    /**
     * Note: this entry was deprecated for 4.0 versions up until
     * and including 4.0.7; was brought back for 4.0.8 (and will
     * be retained for 4.1)
     */
    final static int PROP_NORMALIZE_LFS = 40;

    /* This entry was deprecated for 3.2 and removed in 4.0
     * version. There are no plans to bring it back.
     */
    //final static int PROP_NORMALIZE_ATTR_VALUES = 41;

    final static int PROP_CACHE_DTDS = 42;
    final static int PROP_CACHE_DTDS_BY_PUBLIC_ID = 43;
    final static int PROP_LAZY_PARSING = 44;
    final static int PROP_SUPPORT_DTDPP = 45;
    final static int PROP_TREAT_CHAR_REFS_AS_ENTS = 46;
    
    /**
     * @since 5.2
     */
    final static int PROP_ALLOW_XML11_ESCAPED_CHARS_IN_XML10 = 47;

    // Object type properties:

    final static int PROP_INPUT_BUFFER_LENGTH = 50;
    //final static int PROP_TEXT_BUFFER_LENGTH = 51;
    final static int PROP_MIN_TEXT_SEGMENT = 52;
    final static int PROP_CUSTOM_INTERNAL_ENTITIES = 53;
    final static int PROP_DTD_RESOLVER = 54;
    final static int PROP_ENTITY_RESOLVER = 55;
    final static int PROP_UNDECLARED_ENTITY_RESOLVER = 56;
    final static int PROP_BASE_URL = 57;
    final static int PROP_INPUT_PARSING_MODE = 58;

    // Size limitation to prevent various DOS attacks
    final static int PROP_MAX_ATTRIBUTES_PER_ELEMENT = 60;
    final static int PROP_MAX_CHILDREN_PER_ELEMENT = 61;
    final static int PROP_MAX_ELEMENT_COUNT = 62;
    final static int PROP_MAX_ELEMENT_DEPTH = 63;
    final static int PROP_MAX_CHARACTERS = 64;
    final static int PROP_MAX_ATTRIBUTE_SIZE = 65;
    final static int PROP_MAX_TEXT_LENGTH = 66;
    final static int PROP_MAX_ENTITY_COUNT = 67;
    final static int PROP_MAX_ENTITY_DEPTH = 68;
    
    /*
    ////////////////////////////////////////////////
    // Limits for numeric properties
    ////////////////////////////////////////////////
    */

    /**
     * Need to set a minimum size, since there are some limitations to
     * smallest consequtive block that can be used.
     */
    final static int MIN_INPUT_BUFFER_LENGTH = 8; // 16 bytes

    /**
     * Let's allow caching of just a dozen DTDs... shouldn't really
     * matter, how many DTDs does one really use?
     */
    final static int DTD_CACHE_SIZE_J2SE = 12;

    final static int DTD_CACHE_SIZE_J2ME = 5;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Default values for custom properties:
    ///////////////////////////////////////////////////////////////////////
    */

    /**
     * By default, let's require minimum of 64 chars to be delivered
     * as shortest partial (piece of) text (CDATA, text) segment;
     * same for both J2ME subset and full readers. Prevents tiniest
     * runts from getting passed
     */
    final static int DEFAULT_SHORTEST_TEXT_SEGMENT = 64;

    /**
     * Default config flags are converted from individual settings,
     * to conform to StAX 1.0 specifications.
     */
    final static int DEFAULT_FLAGS_FULL =
        0
        // First, default settings StAX specs dictate:

        | CFG_NAMESPACE_AWARE
        // Coalescing to be disabled
        //| CFG_COALESCE_TEXT
        | CFG_REPLACE_ENTITY_REFS
        | CFG_SUPPORT_EXTERNAL_ENTITIES
        | CFG_SUPPORT_DTD

        // and then custom setting defaults:

        // and namespace URI interning
        | CFG_INTERN_NAMES
        | CFG_INTERN_NS_URIS

        // we will also accurately report CDATA, by default
        | CFG_REPORT_CDATA

        /* 20-Jan-2006, TSa: As per discussions on stax-builders list
         *   (and input from xml experts), 4.0 will revert to "do not
         *   report SPACE events outside root element by default"
         *   settings. Conceptually this is what xml specification
         *   implies should be done: there is no content outside of
         *   the element tree, including any ignorable content, just
         *   processing instructions and comments.
         */
        //| CFG_REPORT_PROLOG_WS

        /* but enable DTD caching (if they are handled):
         * (... maybe J2ME subset shouldn't do it?)
         */
        | CFG_CACHE_DTDS
        /* 29-Mar-2006, TSa: But note, no caching by public-id, due
         *   to problems with cases where public-id/system-id were
         *   inconsistently used, leading to problems.
         */

        /* by default, let's also allow lazy parsing, since it tends
         * to improve performance
         */
        | CFG_LAZY_PARSING

        /* and also make Event objects preserve location info...
         * can be turned off for maximum performance
         */
        | CFG_PRESERVE_LOCATION

        // As per Stax 1.0 specs, we can not enable this by default:
        //| CFG_AUTO_CLOSE_INPUT);

        /* Also, let's enable dtd++ support (shouldn't hurt with non-dtd++
         * dtds)
         */

        | CFG_SUPPORT_DTDPP
        
        /*
         * Set this as a default, as this is required in xml;
         */
        | CFG_NORMALIZE_LFS

        /* Regarding Xml:id, let's enabled typing by default, but not
         * uniqueness validity checks: latter will be taken care of
         * by DTD validation if enabled, otherwise needs to be explicitly
         * enabled
         */
        | CFG_XMLID_TYPING
        // | CFG_XMLID_UNIQ_CHECKS
        ;

    /**
     * For now defaults for J2ME flags can be identical to 'full' set;
     * differences are in buffer sizes.
     */
    final static int DEFAULT_FLAGS_J2ME = DEFAULT_FLAGS_FULL;

    // // //

    /**
     * Map to use for converting from String property ids to ints
     * described above; useful to allow use of switch later on.
     */
    final static HashMap<String,Integer> sProperties = new HashMap<String,Integer>(64); // we have about 40 entries
    static {
        // Standard ones; support for features
        sProperties.put(XMLInputFactory.IS_COALESCING, PROP_COALESCE_TEXT);
        sProperties.put(XMLInputFactory.IS_NAMESPACE_AWARE,
                        PROP_NAMESPACE_AWARE);
        sProperties.put(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES,
                    PROP_REPLACE_ENTITY_REFS);
        sProperties.put(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES,
                    PROP_SUPPORT_EXTERNAL_ENTITIES);
        sProperties.put(XMLInputFactory.IS_VALIDATING,
                        PROP_VALIDATE_AGAINST_DTD);
        sProperties.put(XMLInputFactory.SUPPORT_DTD,
                        PROP_SUPPORT_DTD);

        // Standard ones; pluggable components
        sProperties.put(XMLInputFactory.ALLOCATOR,
                        PROP_EVENT_ALLOCATOR);
        sProperties.put(XMLInputFactory.REPORTER,
                        PROP_WARNING_REPORTER);
        sProperties.put(XMLInputFactory.RESOLVER,
                        PROP_XML_RESOLVER);

        // StAX2-introduced flags:
        sProperties.put(XMLInputFactory2.P_INTERN_NAMES,
                        PROP_INTERN_NAMES);
        sProperties.put(XMLInputFactory2.P_INTERN_NS_URIS,
                        PROP_INTERN_NS_URIS);
        sProperties.put(XMLInputFactory2.P_REPORT_CDATA,
                        PROP_REPORT_CDATA);
        sProperties.put(XMLInputFactory2.P_REPORT_PROLOG_WHITESPACE,
                        PROP_REPORT_PROLOG_WS);
        sProperties.put(XMLInputFactory2.P_PRESERVE_LOCATION,
                        PROP_PRESERVE_LOCATION);
        sProperties.put(XMLInputFactory2.P_AUTO_CLOSE_INPUT,
                        PROP_AUTO_CLOSE_INPUT);
        sProperties.put(XMLInputFactory2.XSP_SUPPORT_XMLID,
                        PROP_SUPPORT_XMLID);
        sProperties.put(XMLInputFactory2.P_DTD_OVERRIDE,
                        PROP_DTD_OVERRIDE);

        // Non-standard ones, flags:

        sProperties.put(WstxInputProperties.P_CACHE_DTDS, PROP_CACHE_DTDS);
        sProperties.put(WstxInputProperties.P_CACHE_DTDS_BY_PUBLIC_ID,
                        PROP_CACHE_DTDS_BY_PUBLIC_ID);
        sProperties.put(XMLInputFactory2.P_LAZY_PARSING, PROP_LAZY_PARSING);
        /*
        sProperties.put(WstxInputProperties.P_SUPPORT_DTDPP,
                        PROP_SUPPORT_DTDPP));
                        */
        sProperties.put(WstxInputProperties.P_TREAT_CHAR_REFS_AS_ENTS,
                PROP_TREAT_CHAR_REFS_AS_ENTS);
        sProperties.put(WstxInputProperties.P_ALLOW_XML11_ESCAPED_CHARS_IN_XML10,
                PROP_ALLOW_XML11_ESCAPED_CHARS_IN_XML10);
        sProperties.put(WstxInputProperties.P_NORMALIZE_LFS, PROP_NORMALIZE_LFS);

        // Non-standard ones, non-flags:

        sProperties.put(WstxInputProperties.P_INPUT_BUFFER_LENGTH,
                        PROP_INPUT_BUFFER_LENGTH);
        sProperties.put(WstxInputProperties.P_MIN_TEXT_SEGMENT,
                        PROP_MIN_TEXT_SEGMENT);
        sProperties.put(WstxInputProperties.P_MAX_ATTRIBUTES_PER_ELEMENT,
                        PROP_MAX_ATTRIBUTES_PER_ELEMENT);
        sProperties.put(WstxInputProperties.P_MAX_ATTRIBUTE_SIZE,
                        PROP_MAX_ATTRIBUTE_SIZE);
        sProperties.put(WstxInputProperties.P_MAX_CHILDREN_PER_ELEMENT,
                        PROP_MAX_CHILDREN_PER_ELEMENT);
        sProperties.put(WstxInputProperties.P_MAX_TEXT_LENGTH,
                        PROP_MAX_TEXT_LENGTH);
        sProperties.put(WstxInputProperties.P_MAX_ELEMENT_COUNT,
                        PROP_MAX_ELEMENT_COUNT);
        sProperties.put(WstxInputProperties.P_MAX_ELEMENT_DEPTH,
                        PROP_MAX_ELEMENT_DEPTH);
         sProperties.put(WstxInputProperties.P_MAX_ENTITY_DEPTH,
                 PROP_MAX_ENTITY_DEPTH);
         sProperties.put(WstxInputProperties.P_MAX_ENTITY_COUNT,
                 PROP_MAX_ENTITY_COUNT);
        sProperties.put(WstxInputProperties.P_MAX_CHARACTERS, PROP_MAX_CHARACTERS);
        sProperties.put(WstxInputProperties.P_CUSTOM_INTERNAL_ENTITIES,
                Integer.valueOf(PROP_CUSTOM_INTERNAL_ENTITIES));
        sProperties.put(WstxInputProperties.P_DTD_RESOLVER,
                        PROP_DTD_RESOLVER);
        sProperties.put(WstxInputProperties.P_ENTITY_RESOLVER,
                        PROP_ENTITY_RESOLVER);
        sProperties.put(WstxInputProperties.P_UNDECLARED_ENTITY_RESOLVER,
                        PROP_UNDECLARED_ENTITY_RESOLVER);
        sProperties.put(WstxInputProperties.P_BASE_URL,
                        PROP_BASE_URL);
        sProperties.put(WstxInputProperties.P_INPUT_PARSING_MODE,
                        PROP_INPUT_PARSING_MODE);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Current config state:
    ///////////////////////////////////////////////////////////////////////
     */

    final protected boolean mIsJ2MESubset;

    final protected SymbolTable mSymbols;

    /**
     * Bitset that contains state of on/off properties; initialized
     * to defaults, but can be set/cleared.
     */
    protected int mConfigFlags;

    /**
     * Bitset that indicates explicit changes to {@link #mConfigFlags}
     * through calls; empty bit means that the corresponding property
     * has its default value, set bit that an explicit call has been
     * made.
     */
    protected int mConfigFlagMods;

    /**
     * 13-Nov-2008, tatus: Need to be able to keep track of whether
     *    name-interning has been explicitly enabled/disable or not
     *    (not if it's whatever defaults we have)
     */
    final static int PROP_INTERN_NAMES_EXPLICIT = 26;
    final static int PROP_INTERN_NS_URIS_EXPLICIT = 27;

    protected int mInputBufferLen;
    protected int mMinTextSegmentLen;
    protected int mMaxAttributesPerElement = DEFAULT_MAX_ATTRIBUTES_PER_ELEMENT;
    protected int mMaxAttributeSize = DEFAULT_MAX_ATTRIBUTE_LENGTH;
    protected int mMaxChildrenPerElement = Integer.MAX_VALUE;
    protected int mMaxElementDepth = DEFAULT_MAX_ELEMENT_DEPTH;
    protected long mMaxElementCount = Long.MAX_VALUE; // unlimited
    protected long mMaxCharacters = Long.MAX_VALUE; // unlimited
    protected int mMaxTextLength = Integer.MAX_VALUE; // unlimited

    protected int mMaxEntityDepth = DEFAULT_MAX_ENTITY_DEPTH;
    protected long mMaxEntityCount = DEFAULT_MAX_ENTITY_COUNT;
    
    /**
     * Base URL to use as the resolution context for relative entity
     * references
     */
    protected URL mBaseURL;

    /**
     * Parsing mode can be changed from the default xml compliant
     * behavior to one of alternate modes (fragment processing,
     * multiple document processing).
     */
    protected WstxInputProperties.ParsingMode mParsingMode =
        WstxInputProperties.PARSING_MODE_DOCUMENT;

    /**
     * This boolean flag is set if the input document requires
     * xml 1.1 (or above) compliant processing: default is xml 1.0
     * compliant. Note that unlike most other properties, this
     * does not come from configuration settings, but from processed
     * document itself.
     */
    protected boolean mXml11 = false;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Common configuration objects
    ///////////////////////////////////////////////////////////////////////
     */

    XMLReporter mReporter;

    XMLResolver mDtdResolver = null;
    XMLResolver mEntityResolver = null;

    /*
    ///////////////////////////////////////////////////////////////////////
    // More special(ized) configuration objects
    ///////////////////////////////////////////////////////////////////////
     */

    //Map mCustomEntities;
    //XMLResolver mUndeclaredEntityResolver;
    //DTDEventListener mDTDEventListener;

    Object[] mSpecialProperties = null;

    private final static int SPEC_PROC_COUNT = 4;

    private final static int SP_IX_CUSTOM_ENTITIES = 0;
    private final static int SP_IX_UNDECL_ENT_RESOLVER = 1;
    private final static int SP_IX_DTD_EVENT_LISTENER = 2;
    private final static int SP_IX_DTD_OVERRIDE = 3;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Buffer recycling:
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * This <code>ThreadLocal</code> contains a {@link SoftRerefence}
     * to a {@link BufferRecycler} used to provide a low-cost
     * buffer recycling between Reader instances.
     */
    final static ThreadLocal<SoftReference<BufferRecycler>> mRecyclerRef = new ThreadLocal<SoftReference<BufferRecycler>>();

    /**
     * This is the actually container of the recyclable buffers. It
     * is obtained via ThreadLocal/SoftReference combination, if one
     * exists, when Config instance is created. If one does not
     * exist, it will created first time a buffer is returned.
     */
    BufferRecycler mCurrRecycler = null;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////////////////////////////////////
     */

    private ReaderConfig(ReaderConfig base,
            boolean j2meSubset, SymbolTable symbols,
            int configFlags, int configFlagMods,
            int inputBufLen,
            int minTextSegmentLen)
    {
        super(base);
        mIsJ2MESubset = j2meSubset;
        mSymbols = symbols;

        mConfigFlags = configFlags;
        mConfigFlagMods = configFlagMods;

        mInputBufferLen = inputBufLen;
        mMinTextSegmentLen = minTextSegmentLen;
        if (base != null) {
            mMaxAttributesPerElement = base.mMaxAttributesPerElement;
            mMaxAttributeSize = base.mMaxAttributeSize;
            mMaxChildrenPerElement = base.mMaxChildrenPerElement;
            mMaxElementCount = base.mMaxElementCount;
            mMaxElementDepth = base.mMaxElementDepth;
            mMaxCharacters = base.mMaxCharacters;
            mMaxTextLength = base.mMaxTextLength;
            mMaxEntityDepth = base.mMaxEntityDepth;
            mMaxEntityCount = base.mMaxEntityCount;
        }

        /* Ok, let's then see if we can find a buffer recycler. Since they
         * are lazily constructed, and since GC may just flush them out
         * on its whims, it's possible we might not find one. That's ok;
         * we can reconstruct one if and when we are to return one or more
         * buffers.
         */
        SoftReference<BufferRecycler> ref = mRecyclerRef.get();
        if (ref != null) {
            mCurrRecycler = ref.get();
        }
    }

    public static ReaderConfig createJ2MEDefaults()
    {
        /* For J2ME we'll use slightly smaller buffer sizes by
         * default, on assumption lower memory usage is desireable:
         */
        ReaderConfig rc = new ReaderConfig(null,
                true, null, DEFAULT_FLAGS_J2ME, 0,
                // 4k input buffer (2000 chars):
                2000,
                DEFAULT_SHORTEST_TEXT_SEGMENT);
        return rc;
    }

    public static ReaderConfig createFullDefaults()
    {
        /* For full version, can use bit larger buffers to achieve better
         * overall performance.
         */
        ReaderConfig rc = new ReaderConfig(null,
                false, null, DEFAULT_FLAGS_FULL, 0,
                // 8k input buffer (4000 chars):
                4000,
                DEFAULT_SHORTEST_TEXT_SEGMENT);
        return rc;
    }

    public ReaderConfig createNonShared(SymbolTable sym)
    {
        // should we throw an exception?
        //if (sym == null) { }
        ReaderConfig rc = new ReaderConfig(this,
                mIsJ2MESubset, sym,
                mConfigFlags, mConfigFlagMods,
                mInputBufferLen,
                mMinTextSegmentLen);
        rc.mReporter = mReporter;
        rc.mDtdResolver = mDtdResolver;
        rc.mEntityResolver = mEntityResolver;
        rc.mBaseURL = mBaseURL;
        rc.mParsingMode = mParsingMode;
        rc.mMaxAttributesPerElement = mMaxAttributesPerElement;
        rc.mMaxAttributeSize = mMaxAttributeSize;
        rc.mMaxChildrenPerElement = mMaxChildrenPerElement;
        rc.mMaxElementCount = mMaxElementCount;
        rc.mMaxCharacters = mMaxCharacters;
        rc.mMaxTextLength = mMaxTextLength;
        rc.mMaxElementDepth = mMaxElementDepth;
        rc.mMaxEntityDepth = mMaxEntityDepth;
        rc.mMaxEntityCount = mMaxEntityCount;
        if (mSpecialProperties != null) {
            int len = mSpecialProperties.length;
            Object[] specProps = new Object[len];
            System.arraycopy(mSpecialProperties, 0, specProps, 0, len);
            rc.mSpecialProperties = specProps;
        }
        return rc;
    }

    /**
     * Unlike name suggests there is also some limited state information
     * associated with the config object. If these objects are reused,
     * that state needs to be reset between reuses, to avoid carrying
     * over incorrect state.
     */
    public void resetState()
    {
        // Current, only xml 1.0 vs 1.1 state is stored here:
        mXml11 = false;
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Implementation of abstract methods
    ///////////////////////////////////////////////////////////////////////
     */

    @Override
    protected int findPropertyId(String propName)
    {
        Integer I = sProperties.get(propName);
        return (I == null) ? -1 : I.intValue();
    }
 
    /*
    ///////////////////////////////////////////////////////////////////////
    // Public API, accessors
    ///////////////////////////////////////////////////////////////////////
     */

    // // // Accessors for immutable configuration:

    public SymbolTable getSymbols() { return mSymbols; }

    /**
     * In future this property could/should be made configurable?
     */

    public int getDtdCacheSize() {
        return mIsJ2MESubset ? DTD_CACHE_SIZE_J2ME : DTD_CACHE_SIZE_J2SE;
    }

    // // // "Raw" accessors for on/off properties:

    public int getConfigFlags() { return mConfigFlags; }

    // // // Standard StAX on/off property accessors

    public boolean willCoalesceText() {
        return _hasConfigFlag(CFG_COALESCE_TEXT);
    }

    public boolean willSupportNamespaces() {
        return _hasConfigFlag(CFG_NAMESPACE_AWARE);
    }

    public boolean willReplaceEntityRefs() {
        return _hasConfigFlag(CFG_REPLACE_ENTITY_REFS);
    }

    public boolean willSupportExternalEntities() {
        return _hasConfigFlag(CFG_SUPPORT_EXTERNAL_ENTITIES);
    }

    public boolean willSupportDTDs() {
        return _hasConfigFlag(CFG_SUPPORT_DTD);
    }

    public boolean willValidateWithDTD() {
        return _hasConfigFlag(CFG_VALIDATE_AGAINST_DTD);
    }

    // // // Stax2 on/off property accessors

    public boolean willReportCData() {
        return _hasConfigFlag(CFG_REPORT_CDATA);
    }

    public boolean willParseLazily() {
        return _hasConfigFlag(CFG_LAZY_PARSING);
    }

    public boolean willInternNames() {
        return _hasConfigFlag(CFG_INTERN_NAMES);
    }

    public boolean willInternNsURIs() {
        return _hasConfigFlag(CFG_INTERN_NS_URIS);
    }

    public boolean willPreserveLocation() {
        return _hasConfigFlag(CFG_PRESERVE_LOCATION);
    }

    public boolean willAutoCloseInput() {
        return _hasConfigFlag(CFG_AUTO_CLOSE_INPUT);
    }

    // // // Woodstox on/off property accessors

    public boolean willReportPrologWhitespace() {
        return _hasConfigFlag(CFG_REPORT_PROLOG_WS);
    }

    public boolean willCacheDTDs() {
        return _hasConfigFlag(CFG_CACHE_DTDS);
    }

    public boolean willCacheDTDsByPublicId() {
        return _hasConfigFlag(CFG_CACHE_DTDS_BY_PUBLIC_ID);
    }

    public boolean willDoXmlIdTyping() {
        return _hasConfigFlag(CFG_XMLID_TYPING);
    }

    public boolean willDoXmlIdUniqChecks() {
        return _hasConfigFlag(CFG_XMLID_UNIQ_CHECKS);
    }

    public boolean willSupportDTDPP() {
        return _hasConfigFlag(CFG_SUPPORT_DTDPP);
    }
    
    public boolean willNormalizeLFs() {
        return _hasConfigFlag(CFG_NORMALIZE_LFS);
    }
    
    public boolean willTreatCharRefsAsEnts() {
        return _hasConfigFlag(CFG_TREAT_CHAR_REFS_AS_ENTS);
    }

    public boolean willAllowXml11EscapedCharsInXml10() {
        return _hasConfigFlag(CFG_ALLOW_XML11_ESCAPED_CHARS_IN_XML10);
    }

    public int getInputBufferLength() { return mInputBufferLen; }

    public int getShortestReportedTextSegment() { return mMinTextSegmentLen; }
    
    public int getMaxAttributesPerElement() { return mMaxAttributesPerElement; }
    public int getMaxAttributeSize() { return mMaxAttributeSize; }
    public int getMaxChildrenPerElement() { return mMaxChildrenPerElement; }

    public int getMaxElementDepth() { return mMaxElementDepth; }
    public long getMaxElementCount() { return mMaxElementCount; }

    public int getMaxEntityDepth() { return mMaxEntityDepth; }
    public long getMaxEntityCount() { return mMaxEntityCount; }

    public long getMaxCharacters() { return mMaxCharacters; }
    public long getMaxTextLength() { return mMaxTextLength; }

    public Map<String,EntityDecl> getCustomInternalEntities()
    {
        @SuppressWarnings("unchecked")
        Map<String,EntityDecl> custEnt = (Map<String,EntityDecl>) _getSpecialProperty(SP_IX_CUSTOM_ENTITIES);
        if (custEnt == null) {
            return Collections.emptyMap();
        }
        // Better be defensive and just return a copy...
        int len = custEnt.size();
        HashMap<String,EntityDecl> m = new HashMap<String,EntityDecl>(len + (len >> 2), 0.81f);
        for (Map.Entry<String,EntityDecl> me : custEnt.entrySet()) {
            m.put(me.getKey(), me.getValue());
        }
        return m;
    }

    public EntityDecl findCustomInternalEntity(String id)
    {
        @SuppressWarnings("unchecked")
        Map<String,EntityDecl> custEnt = (Map<String,EntityDecl>) _getSpecialProperty(SP_IX_CUSTOM_ENTITIES);
        if (custEnt == null) {
            return null;
        }
        return custEnt.get(id);
    }

    public XMLReporter getXMLReporter() { return mReporter; }

    public XMLResolver getXMLResolver() { return mEntityResolver; }

    public XMLResolver getDtdResolver() { return mDtdResolver; }
    public XMLResolver getEntityResolver() { return mEntityResolver; }
    public XMLResolver getUndeclaredEntityResolver() {
        return (XMLResolver) _getSpecialProperty(SP_IX_UNDECL_ENT_RESOLVER);
    }

    public URL getBaseURL() { return mBaseURL; }

    public WstxInputProperties.ParsingMode getInputParsingMode() {
        return mParsingMode;
    }

    public boolean inputParsingModeDocuments() {
        return mParsingMode == WstxInputProperties.PARSING_MODE_DOCUMENTS;
    }

    public boolean inputParsingModeFragment() {
        return mParsingMode == WstxInputProperties.PARSING_MODE_FRAGMENT;
    }

    /**
     * @return True if the input well-formedness and validation checks
     *    should be done according to xml 1.1 specification; false if
     *    xml 1.0 specification.
     */
    public boolean isXml11() {
        return mXml11;
    }

    public DTDEventListener getDTDEventListener() {
        return (DTDEventListener) _getSpecialProperty(SP_IX_DTD_EVENT_LISTENER);
    }

    public DTDValidationSchema getDTDOverride() {
        return (DTDValidationSchema) _getSpecialProperty(SP_IX_DTD_OVERRIDE);
    }

    /**
     * Special accessor to use to verify whether name interning has
     * explicitly been enabled; true if call was been made to set
     * it to true; false otherwise (default, or set to false)
     */
    public boolean hasInternNamesBeenEnabled() {
        return _hasExplicitConfigFlag(CFG_INTERN_NAMES);
    }

    public boolean hasInternNsURIsBeenEnabled() {
        return _hasExplicitConfigFlag(CFG_INTERN_NS_URIS);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Simple mutators
    ///////////////////////////////////////////////////////////////////////
     */

    public void setConfigFlag(int flag) {
        mConfigFlags |= flag;
        mConfigFlagMods |= flag;
    }

    public void clearConfigFlag(int flag) {
        mConfigFlags &= ~flag;
        mConfigFlagMods |= flag;
    }

    // // // Mutators for standard StAX properties

    public void doCoalesceText(boolean state) {
        setConfigFlag(CFG_COALESCE_TEXT, state);
    }

    public void doSupportNamespaces(boolean state) {
        setConfigFlag(CFG_NAMESPACE_AWARE, state);
    }

    public void doReplaceEntityRefs(boolean state) {
        setConfigFlag(CFG_REPLACE_ENTITY_REFS, state);
    }

    public void doSupportExternalEntities(boolean state) {
        setConfigFlag(CFG_SUPPORT_EXTERNAL_ENTITIES, state);
    }

    public void doSupportDTDs(boolean state) {
        setConfigFlag(CFG_SUPPORT_DTD, state);
    }

    public void doValidateWithDTD(boolean state) {
        setConfigFlag(CFG_VALIDATE_AGAINST_DTD, state);
    }

    // // // Mutators for Woodstox-specific properties

    public void doInternNames(boolean state) {
        setConfigFlag(CFG_INTERN_NAMES, state);
    }

    public void doInternNsURIs(boolean state) {
        setConfigFlag(CFG_INTERN_NS_URIS, state);
    }

    public void doReportPrologWhitespace(boolean state) {
        setConfigFlag(CFG_REPORT_PROLOG_WS, state);
    }

    public void doReportCData(boolean state) {
        setConfigFlag(CFG_REPORT_CDATA, state);
    }

    public void doCacheDTDs(boolean state) {
        setConfigFlag(CFG_CACHE_DTDS, state);
    }

    public void doCacheDTDsByPublicId(boolean state) {
        setConfigFlag(CFG_CACHE_DTDS_BY_PUBLIC_ID, state);
    }

    public void doParseLazily(boolean state) {
        setConfigFlag(CFG_LAZY_PARSING, state);
    }

    public void doXmlIdTyping(boolean state) {
        setConfigFlag(CFG_XMLID_TYPING, state);
    }

    public void doXmlIdUniqChecks(boolean state) {
        setConfigFlag(CFG_XMLID_UNIQ_CHECKS, state);
    }

    public void doPreserveLocation(boolean state) {
        setConfigFlag(CFG_PRESERVE_LOCATION, state);
    }

    public void doAutoCloseInput(boolean state) {
        setConfigFlag(CFG_AUTO_CLOSE_INPUT, state);
    }

    public void doSupportDTDPP(boolean state) {
        setConfigFlag(CFG_SUPPORT_DTDPP, state);
    }
    
    public void doTreatCharRefsAsEnts(final boolean state) {
        setConfigFlag(CFG_TREAT_CHAR_REFS_AS_ENTS, state);
    }

    public void doAllowXml11EscapedCharsInXml10(final boolean state) {
        setConfigFlag(CFG_ALLOW_XML11_ESCAPED_CHARS_IN_XML10, state);
    }

    public void doNormalizeLFs(final boolean state) {
        setConfigFlag(CFG_NORMALIZE_LFS, state);
    }

    public void setInputBufferLength(int value)
    {
        /* Let's enforce minimum here; necessary to allow longest
         * consequtive text span to be available (xml decl, etc)
         */
        if (value < MIN_INPUT_BUFFER_LENGTH) {
            value = MIN_INPUT_BUFFER_LENGTH;
        }
        mInputBufferLen = value;
    }

    public void setShortestReportedTextSegment(int value) {
        mMinTextSegmentLen = value;
    }
    public void setMaxAttributesPerElement(int value) {
        mMaxAttributesPerElement = value;
    }
    public void setMaxAttributeSize(int value) {
        mMaxAttributeSize = value;
    }
    public void setMaxChildrenPerElement(int value) {
        mMaxChildrenPerElement = value;
    }
    public void setMaxElementDepth(int value) {
        mMaxElementDepth = value;
    }
    public void setMaxElementCount(long value) {
        mMaxElementCount = value;
    }
    public void setMaxCharacters(long value) {
        mMaxCharacters = value;
    }
    public void setMaxTextLength(int value) {
        mMaxTextLength = value;
    }
    public void setMaxEntityDepth(int value) {
        mMaxEntityDepth = value;
    }
    public void setMaxEntityCount(long value) {
        mMaxEntityCount = value;
    }

    public void setCustomInternalEntities(Map<String,?> m)
    {
        Map<String,EntityDecl> entMap;
        if (m == null || m.size() < 1) {
            entMap = Collections.emptyMap();
        } else {
            int len = m.size();
            entMap = new HashMap<String,EntityDecl>(len + (len >> 1), 0.75f);
            for (Map.Entry<String,?> me : m.entrySet()) {
                Object val = me.getValue();
                char[] ch;
                if (val == null) {
                    ch = DataUtil.getEmptyCharArray();
                } else if (val instanceof char[]) {
                    ch = (char[]) val;
                } else {
                    // Probably String, but let's just ensure that
                    String str = val.toString();
                    ch = str.toCharArray();
                }
                String name = me.getKey();
                entMap.put(name, IntEntity.create(name, ch));
            }
        }
        _setSpecialProperty(SP_IX_CUSTOM_ENTITIES, entMap);
    }

    public void setXMLReporter(XMLReporter r) {
        mReporter = r;
    }

    /**
     * Note: for better granularity, you should call {@link #setEntityResolver}
     * and {@link #setDtdResolver} instead.
     */
    public void setXMLResolver(XMLResolver r) {
        mEntityResolver = r;
        mDtdResolver = r;
    }

    public void setDtdResolver(XMLResolver r) {
        mDtdResolver = r;
    }

    public void setEntityResolver(XMLResolver r) {
        mEntityResolver = r;
    }

    public void setUndeclaredEntityResolver(XMLResolver r) {
        _setSpecialProperty(SP_IX_UNDECL_ENT_RESOLVER, r);
    }

    public void setBaseURL(URL baseURL) { mBaseURL = baseURL; }

    public void setInputParsingMode(WstxInputProperties.ParsingMode mode) {
        mParsingMode = mode;
    }

    /**
     * Method called to enable or disable 1.1 compliant processing; if
     * disabled, defaults to xml 1.0 compliant processing.
     */
    public void enableXml11(boolean state) {
        mXml11 = state;
    }

    public void setDTDEventListener(DTDEventListener l) {
        _setSpecialProperty(SP_IX_DTD_EVENT_LISTENER, l);
    }

    public void setDTDOverride(DTDValidationSchema schema) {
        _setSpecialProperty(SP_IX_DTD_OVERRIDE, schema);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Profile mutators:
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Method to call to make Reader created conform as closely to XML
     * standard as possible, doing all checks and transformations mandated
     * (linefeed conversions, attr value normalizations).
     * See {@link XMLInputFactory2#configureForXmlConformance} for
     * required settings for standard StAX/StAX properties.
     *<p>
     * In addition to the standard settings, following Woodstox-specific
     * settings are also done:
     *<ul>
     * <b>None</b>.
     *</ul>
     *<p>
     * Notes: Does NOT change 'performance' settings (buffer sizes,
     * DTD caching, coalescing, interning, accurate location info).
     */
    public void configureForXmlConformance()
    {
        // // StAX 1.0 settings
        doSupportNamespaces(true);
        doSupportDTDs(true);
        doSupportExternalEntities(true);
        doReplaceEntityRefs(true);

        // // Stax2 additional settings

        // Better enable full xml:id checks:
        doXmlIdTyping(true);
        doXmlIdUniqChecks(true);

        // Woodstox-specific ones:
    }

    /**
     * Method to call to make Reader created be as "convenient" to use
     * as possible; ie try to avoid having to deal with some of things
     * like segmented text chunks. This may incur some slight performance
     * penalties, but should not affect XML conformance.
     * See {@link XMLInputFactory2#configureForConvenience} for
     * required settings for standard StAX/StAX properties.
     *<p>
     * In addition to the standard settings, following Woodstox-specific
     * settings are also done:
     *<ul>
     *  <li>Disable <code>XMLStreamFactory2.P_LAZY_PARSING</code> (to allow for synchronous
     *    error notification by forcing full XML events to be completely
     *    parsed when reader's <code>next() is called)
     * </li>
     *</ul>
     */
    public void configureForConvenience()
    {
        // StAX (1.0) settings:
        doCoalesceText(true);
        doReplaceEntityRefs(true);

        // StAX2: 
        doReportCData(false);
        doReportPrologWhitespace(false);
        /* Also, knowing exact locations is nice esp. for error
         * reporting purposes
         */
        doPreserveLocation(true);

        // Woodstox-specific:

        /* Also, we can force errors to be reported in timely manner:
         * (once again, at potential expense of performance)
         */
        doParseLazily(false);
    }

    /**
     * Method to call to make the Reader created be as fast as possible reading
     * documents, especially for long-running processes where caching is
     * likely to help.
     *
     * See {@link XMLInputFactory2#configureForSpeed} for
     * required settings for standard StAX/StAX properties.
     *<p>
     * In addition to the standard settings, following Woodstox-specific
     * settings are also done:
     *<ul>
     * <li>Enable <code>P_CACHE_DTDS</code>.
     *  </li>
     * <li>Enable <code>XMLStremaFactory2.P_LAZY_PARSING</code> (can improve performance
     *   especially when skipping text segments)
     *  </li>
     * <li>Disable Xml:id uniqueness checks (and leave typing as is)
     *  </li>
     * <li>Set lowish value for <code>P_MIN_TEXT_SEGMENT</code>, to allow
     *   reader to optimize segment length it uses (and possibly avoids
     *   one copy operation in the process)
     *  </li>
     * <li>Increase <code>P_INPUT_BUFFER_LENGTH</code> a bit from default,
     *   to allow for longer consequtive read operations; also reduces cases
     *   where partial text segments are on input buffer boundaries.
     *  </li>
     *</ul>
     */
    public void configureForSpeed()
    {
        // StAX (1.0):
        doCoalesceText(false);

        // StAX2:
        doPreserveLocation(false);
        doReportPrologWhitespace(false);
        //doInternNames(true); // this is a NOP
        doInternNsURIs(true);
        doXmlIdUniqChecks(false);

        // Woodstox-specific:
        doCacheDTDs(true);
        doParseLazily(true);

        /* If we let Reader decide sizes of text segments, it should be
         * able to optimize it better, thus low min value. This value
         * is only used in cases where text is at buffer boundary, or
         * where entity prevents using consequtive chars from input buffer:
         */
        setShortestReportedTextSegment(16);
        setInputBufferLength(8000); // 16k input buffer
    }

    /**
     * Method to call to minimize the memory usage of the stream/event reader;
     * both regarding Objects created, and the temporary memory usage during
     * parsing.
     * This generally incurs some performance penalties, due to using
     * smaller input buffers.
     *<p>
     * See {@link XMLInputFactory2#configureForLowMemUsage} for
     * required settings for standard StAX/StAX properties.
     *<p>
     * In addition to the standard settings, following Woodstox-specific
     * settings are also done:
     *<ul>
     * <li>Disable <code>P_CACHE_DTDS</code>
     *  </li>
     * <li>Enable <code>P_PARSE_LAZILY</code>
     *  </li>
     * <li>Resets <code>P_MIN_TEXT_SEGMENT</code> to the (somewhat low)
     *   default value.
     *  <li>
     * <li>Reduces <code>P_INPUT_BUFFER_LENGTH</code> a bit from the default
     *  <li>
     *</ul>
     */
    public void configureForLowMemUsage()
    {
        // StAX (1.0)
        doCoalesceText(false);

        // StAX2:

        doPreserveLocation(false); // can reduce temporary mem usage

        // Woodstox-specific:
        doCacheDTDs(false);
        doParseLazily(true); // can reduce temporary mem usage
        doXmlIdUniqChecks(false); // enabling would increase mem usage
        setShortestReportedTextSegment(ReaderConfig.DEFAULT_SHORTEST_TEXT_SEGMENT);
        setInputBufferLength(512); // 1k input buffer
        // Text buffer need not be huge, as we do not coalesce
    }
    
    /**
     * Method to call to make Reader try to preserve as much of input
     * formatting as possible, so that round-tripping would be as lossless
     * as possible.
     *<p>
     * See {@link XMLInputFactory2#configureForLowMemUsage} for
     * required settings for standard StAX/StAX properties.
     *<p>
     * In addition to the standard settings, following Woodstox-specific
     * settings are also done:
     *<ul>
     * <li>Increases <code>P_MIN_TEXT_SEGMENT</code> to the maximum value so
     *    that all original text segment chunks are reported without
     *    segmentation (but without coalescing with adjacent CDATA segments)
     *  <li>
     *  <li>Sets <code>P_TREAT_CHAR_REFS_AS_ENTS</code> to true, so the all the 
     *   original character references are reported with their position, 
     *   original text, and the replacement text.
     *   </li>
     *</ul>
     */
    public void configureForRoundTripping()
    {
        // StAX (1.0)
        doCoalesceText(false);
        doReplaceEntityRefs(false);
        
        // StAX2:
        doReportCData(true);
        doReportPrologWhitespace(true);
        
        // Woodstox specific settings
        doTreatCharRefsAsEnts(true);
        doNormalizeLFs(false);

        // effectively prevents from reporting partial segments:
        setShortestReportedTextSegment(Integer.MAX_VALUE);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Buffer recycling:
    ///////////////////////////////////////////////////////////////////////
     */

    public char[] allocSmallCBuffer(int minSize)
    {
        if (mCurrRecycler != null) {
            char[] result = mCurrRecycler.getSmallCBuffer(minSize);
            if (result != null) {
                return result;
            }
        }
        // Nope; no recycler, or it has no suitable buffers, let's create:
        return new char[minSize];
    }

    public void freeSmallCBuffer(char[] buffer)
    {
        // Need to create (and assign) the buffer?
        if (mCurrRecycler == null) {
            mCurrRecycler = createRecycler();
        }
        mCurrRecycler.returnSmallCBuffer(buffer);
    }

    public char[] allocMediumCBuffer(int minSize)
    {
        if (mCurrRecycler != null) {
            char[] result = mCurrRecycler.getMediumCBuffer(minSize);
            if (result != null) {
                return result;
            }
        }
        return new char[minSize];
    }

    public void freeMediumCBuffer(char[] buffer)
    {
        if (mCurrRecycler == null) {
            mCurrRecycler = createRecycler();
        }
        mCurrRecycler.returnMediumCBuffer(buffer);
    }

    public char[] allocFullCBuffer(int minSize)
    {
        if (mCurrRecycler != null) {
            char[] result = mCurrRecycler.getFullCBuffer(minSize);
            if (result != null) {
                return result;
            }
        }
        return new char[minSize];
    }

    public void freeFullCBuffer(char[] buffer)
    {
        // Need to create (and assign) the buffer?
        if (mCurrRecycler == null) {
            mCurrRecycler = createRecycler();
        }
        mCurrRecycler.returnFullCBuffer(buffer);
    }

    public byte[] allocFullBBuffer(int minSize)
    {
        if (mCurrRecycler != null) {
            byte[] result = mCurrRecycler.getFullBBuffer(minSize);
            if (result != null) {
                return result;
            }
        }
        return new byte[minSize];
    }

    public void freeFullBBuffer(byte[] buffer)
    {
        // Need to create (and assign) the buffer?
        if (mCurrRecycler == null) {
            mCurrRecycler = createRecycler();
        }
        mCurrRecycler.returnFullBBuffer(buffer);
    }

    private BufferRecycler createRecycler()
    {
        BufferRecycler recycler = new BufferRecycler();
        // No way to reuse/reset SoftReference, have to create new always:
        mRecyclerRef.set(new SoftReference<BufferRecycler>(recycler));
        return recycler;
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Internal methods:
    ///////////////////////////////////////////////////////////////////////
     */

    private void setConfigFlag(int flag, boolean state)
    {
        if (state) {
            mConfigFlags |= flag;
        } else {
            mConfigFlags &= ~flag;
        }
        mConfigFlagMods |= flag;
    }

    @Override
    public Object getProperty(int id)
    {
        switch (id) {
            // First, standard Stax 1.0 properties:

        case PROP_COALESCE_TEXT:
            return willCoalesceText() ? Boolean.TRUE : Boolean.FALSE;
        case PROP_NAMESPACE_AWARE:
            return willSupportNamespaces() ? Boolean.TRUE : Boolean.FALSE;
        case PROP_REPLACE_ENTITY_REFS:
            return willReplaceEntityRefs() ? Boolean.TRUE : Boolean.FALSE;
        case PROP_SUPPORT_EXTERNAL_ENTITIES:
            return willSupportExternalEntities() ? Boolean.TRUE : Boolean.FALSE;

        case PROP_VALIDATE_AGAINST_DTD:
            return willValidateWithDTD() ? Boolean.TRUE : Boolean.FALSE;
        case PROP_SUPPORT_DTD:
            return willSupportDTDs() ? Boolean.TRUE : Boolean.FALSE;
        case PROP_WARNING_REPORTER:
            return getXMLReporter();
        case PROP_XML_RESOLVER:
            return getXMLResolver();
        case PROP_EVENT_ALLOCATOR:
            /* 25-Mar-2006, TSa: Not really supported here, so let's
             *   return null
             */
            return null;

        // Then Stax2 properties:

        case PROP_REPORT_PROLOG_WS:
            return willReportPrologWhitespace() ? Boolean.TRUE : Boolean.FALSE;
        case PROP_REPORT_CDATA:
            return willReportCData() ? Boolean.TRUE : Boolean.FALSE;

        case PROP_INTERN_NAMES:
            return willInternNames() ? Boolean.TRUE : Boolean.FALSE;
        case PROP_INTERN_NS_URIS:
            return willInternNsURIs() ? Boolean.TRUE : Boolean.FALSE;

        case PROP_PRESERVE_LOCATION:
            return willPreserveLocation() ? Boolean.TRUE : Boolean.FALSE;
        case PROP_AUTO_CLOSE_INPUT:
            return willAutoCloseInput() ? Boolean.TRUE : Boolean.FALSE;

        case PROP_DTD_OVERRIDE:
            return getDTDOverride();

        // // // Then Woodstox custom properties:

            // first, flags:
        case PROP_CACHE_DTDS:
            return willCacheDTDs() ? Boolean.TRUE : Boolean.FALSE;
        case PROP_CACHE_DTDS_BY_PUBLIC_ID:
            return willCacheDTDsByPublicId() ? Boolean.TRUE : Boolean.FALSE;
        case PROP_LAZY_PARSING:
            return willParseLazily() ? Boolean.TRUE : Boolean.FALSE;
        case PROP_SUPPORT_XMLID:
            {
                if (!_hasConfigFlag(CFG_XMLID_TYPING)) {
                    return XMLStreamProperties.XSP_V_XMLID_NONE;
                }
                return _hasConfigFlag(CFG_XMLID_UNIQ_CHECKS) ?
                    XMLStreamProperties.XSP_V_XMLID_FULL :
                    XMLStreamProperties.XSP_V_XMLID_TYPING;
            }
            
        case PROP_TREAT_CHAR_REFS_AS_ENTS:
            return willTreatCharRefsAsEnts() ? Boolean.TRUE : Boolean.FALSE;
        case PROP_ALLOW_XML11_ESCAPED_CHARS_IN_XML10:
            return willAllowXml11EscapedCharsInXml10() ? Boolean.TRUE : Boolean.FALSE;
        case PROP_NORMALIZE_LFS:
            return willNormalizeLFs() ? Boolean.TRUE : Boolean.FALSE;

            // then object values:
        case PROP_INPUT_BUFFER_LENGTH:
            return getInputBufferLength();
        case PROP_MAX_ATTRIBUTES_PER_ELEMENT:
            return getMaxAttributesPerElement();
        case PROP_MAX_ATTRIBUTE_SIZE:
            return getMaxAttributeSize();
        case PROP_MAX_CHILDREN_PER_ELEMENT:
            return getMaxChildrenPerElement();
        case PROP_MAX_ELEMENT_DEPTH:
            return getMaxElementDepth();
        case PROP_MAX_ELEMENT_COUNT:
            return getMaxElementCount();
        case PROP_MAX_CHARACTERS:
            return getMaxCharacters();
        case PROP_MAX_TEXT_LENGTH:
            return getMaxTextLength();
        case PROP_MAX_ENTITY_DEPTH:
            return getMaxEntityDepth();
        case PROP_MAX_ENTITY_COUNT:
            return getMaxEntityCount();

        case PROP_MIN_TEXT_SEGMENT:
            return getShortestReportedTextSegment();
        case PROP_CUSTOM_INTERNAL_ENTITIES:
            return getCustomInternalEntities();
        case PROP_DTD_RESOLVER:
            return getDtdResolver();
        case PROP_ENTITY_RESOLVER:
            return getEntityResolver();
        case PROP_UNDECLARED_ENTITY_RESOLVER:
            return getUndeclaredEntityResolver();
        case PROP_BASE_URL:
            return getBaseURL();
        case PROP_INPUT_PARSING_MODE:
            return getInputParsingMode();

        default: // sanity check, should never happen
            throw new IllegalStateException("Internal error: no handler for property with internal id "+id+".");
        }
    }

    @Override
    public boolean setProperty(String propName, int id, Object value)
    {
        switch (id) {
            // First, standard (Stax 1.0) properties:

        case PROP_COALESCE_TEXT:
            doCoalesceText(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_NAMESPACE_AWARE:
            doSupportNamespaces(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_REPLACE_ENTITY_REFS:
            doReplaceEntityRefs(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_SUPPORT_EXTERNAL_ENTITIES:
            doSupportExternalEntities(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_SUPPORT_DTD:
            doSupportDTDs(ArgUtil.convertToBoolean(propName, value));
            break;
            
            // // // Then ones that can be dispatched:

        case PROP_VALIDATE_AGAINST_DTD:
            doValidateWithDTD(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_WARNING_REPORTER:
            setXMLReporter((XMLReporter) value);
            break;

        case PROP_XML_RESOLVER:
            setXMLResolver((XMLResolver) value);
            break;

        case PROP_EVENT_ALLOCATOR:
            /* 25-Mar-2006, TSa: Not really supported here, so let's
             *   return false to let caller deal with it
             */
            return false;

        // // // Then Stax2 properties, flags:

        case PROP_INTERN_NS_URIS:
            doInternNsURIs(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_INTERN_NAMES:
            doInternNames(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_REPORT_CDATA:
            doReportCData(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_REPORT_PROLOG_WS:
            doReportPrologWhitespace(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_PRESERVE_LOCATION:
            doPreserveLocation(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_AUTO_CLOSE_INPUT:
            doAutoCloseInput(ArgUtil.convertToBoolean(propName, value));
            break;

        // // // Then Stax2 properties, enum/object types:

        case PROP_SUPPORT_XMLID:
            {
                boolean typing, uniq;

                if (XMLStreamProperties.XSP_V_XMLID_NONE.equals(value)) {
                    typing = uniq = false;
                } else if (XMLStreamProperties.XSP_V_XMLID_TYPING.equals(value)) {
                    typing = true;
                    uniq = false;
                } else if (XMLStreamProperties.XSP_V_XMLID_FULL.equals(value)) {
                    typing = uniq = true;
                } else {
                    throw new IllegalArgumentException
                        ("Illegal argument ('"+value+"') to set property "
+XMLStreamProperties.XSP_SUPPORT_XMLID+" to: has to be one of '"
+XMLStreamProperties.XSP_V_XMLID_NONE+"', '"+XMLStreamProperties.XSP_V_XMLID_TYPING+"' or '"+XMLStreamProperties.XSP_V_XMLID_FULL+"'"
                         );
                }
                setConfigFlag(CFG_XMLID_TYPING, typing);
                setConfigFlag(CFG_XMLID_UNIQ_CHECKS, uniq);
            }
            break;

        case PROP_DTD_OVERRIDE:
            setDTDOverride((DTDValidationSchema) value);
            break;

        // // // And then Woodstox specific, flags

        case PROP_CACHE_DTDS:
            doCacheDTDs(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_CACHE_DTDS_BY_PUBLIC_ID:
            doCacheDTDsByPublicId(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_LAZY_PARSING:
            doParseLazily(ArgUtil.convertToBoolean(propName, value));
            break;
            
        case PROP_TREAT_CHAR_REFS_AS_ENTS:
            doTreatCharRefsAsEnts(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_ALLOW_XML11_ESCAPED_CHARS_IN_XML10:
            doAllowXml11EscapedCharsInXml10(ArgUtil.convertToBoolean(propName, value));
            break;

        case PROP_NORMALIZE_LFS:
            doNormalizeLFs(ArgUtil.convertToBoolean(propName, value));
            break;
            
        // // // And then Woodstox specific, enum/object:

        case PROP_INPUT_BUFFER_LENGTH:
            setInputBufferLength(ArgUtil.convertToInt(propName, value, 1));
            break;
            
        case PROP_MAX_ATTRIBUTES_PER_ELEMENT:
            setMaxAttributesPerElement(ArgUtil.convertToInt(propName, value, 1));
            break;
        case PROP_MAX_ATTRIBUTE_SIZE:
            setMaxAttributeSize(ArgUtil.convertToInt(propName, value, 1));
            break;
        case PROP_MAX_CHILDREN_PER_ELEMENT:
            setMaxChildrenPerElement(ArgUtil.convertToInt(propName, value, 1));
            break;
        case PROP_MAX_ELEMENT_DEPTH:
            setMaxElementDepth(ArgUtil.convertToInt(propName, value, 1));
            break;
        case PROP_MAX_ELEMENT_COUNT:
            setMaxElementCount(ArgUtil.convertToLong(propName, value, 1));
            break;
        case PROP_MAX_CHARACTERS:
            setMaxCharacters(ArgUtil.convertToLong(propName, value, 1));
            break;
        case PROP_MAX_TEXT_LENGTH:
            setMaxTextLength(ArgUtil.convertToInt(propName, value, 1));
            break;
        case PROP_MAX_ENTITY_DEPTH:
            setMaxEntityDepth(ArgUtil.convertToInt(propName, value, 1));
            break;
        case PROP_MAX_ENTITY_COUNT:
            setMaxEntityCount(ArgUtil.convertToLong(propName, value, 1));
            break;
            
        case PROP_MIN_TEXT_SEGMENT:
            setShortestReportedTextSegment(ArgUtil.convertToInt(propName, value, 1));
            break;

        case PROP_CUSTOM_INTERNAL_ENTITIES:
        	{
        		@SuppressWarnings("unchecked")
        		Map<String,?> arg = (Map<String,?>) value;
        		setCustomInternalEntities(arg);
        	}
            break;

        case PROP_DTD_RESOLVER:
            setDtdResolver((XMLResolver) value);
            break;

        case PROP_ENTITY_RESOLVER:
            setEntityResolver((XMLResolver) value);
            break;

        case PROP_UNDECLARED_ENTITY_RESOLVER:
            setUndeclaredEntityResolver((XMLResolver) value);
            break;

        case PROP_BASE_URL:
            /* 17-Nov-2008, TSa: Let's make it bit more versatile; if it's not
             *   a URL per se, let's assume it is something that we can convert
             *   to URL
             */
            {
                URL u;
                if (value == null) {
                    u = null;
                } else if (value instanceof URL) {
                    u = (URL) value;
                } else {
                    try {
                        u = new URL(value.toString());
                    } catch (Exception ioe) { // MalformedURLException actually...
                        throw new IllegalArgumentException(ioe.getMessage(), ioe);
                    }
                }
                setBaseURL(u);
            }
            break;

        case PROP_INPUT_PARSING_MODE:
            setInputParsingMode((WstxInputProperties.ParsingMode) value);
            break;

        default: // sanity check, should never happen
            throw new IllegalStateException("Internal error: no handler for property with internal id "+id+".");
        }

        return true;
    }

    protected boolean _hasConfigFlag(int flag) {
        return (mConfigFlags & flag) != 0;
    }

    /**
     * Method similar to {@link #_hasConfigFlag}, but that will only
     * return true if in addition to being set, flag has been explicitly
     * modified (i.e. setProperty has been called to modify it)
     */
    protected boolean _hasExplicitConfigFlag(int flag) {
        return _hasConfigFlag(flag) && (mConfigFlagMods & flag) != 0;
    }

    private final Object _getSpecialProperty(int ix)
    {
        if (mSpecialProperties == null) {
            return null;
        }
        return mSpecialProperties[ix];
    }

    private final void _setSpecialProperty(int ix, Object value)
    {
        if (mSpecialProperties == null) {
            mSpecialProperties = new Object[SPEC_PROC_COUNT];
        }
        mSpecialProperties[ix] = value;
    }
}
