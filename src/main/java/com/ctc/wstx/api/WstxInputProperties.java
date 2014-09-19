package com.ctc.wstx.api;

import javax.xml.stream.XMLResolver;

import org.codehaus.stax2.XMLInputFactory2;

/**
 * Class that contains constant for property names used to configure
 * cursor and event readers produced by Wstx implementation of
 * {@link javax.xml.stream.XMLInputFactory}.
 *<p>
 * TODO:
 *
 * - CHECK_CHAR_VALIDITY (separate for white spaces?)
 * - CATALOG_RESOLVER? (or at least, ENABLE_CATALOGS)
 */
public final class WstxInputProperties
{
    /**
     * Constants used when no DTD handling is done, and we do not know the
     * 'real' type of an attribute. Seems like CDATA is the safe choice.
     */
    public final static String UNKNOWN_ATTR_TYPE = "CDATA";

    /*
    ///////////////////////////////////////////////////////
    // Simple on/off settings:
    ///////////////////////////////////////////////////////
     */

    // // // Normalization:

    /**
     * Feature that controls whether linefeeds are normalized into
     * canonical linefeed as mandated by xml specification.
     *<p>
     * Note that disabling this property (from its default enabled
     * state) will result in non-conforming XML processing. It may
     * be useful for use cases where changes to input content should
     * be minimized.
     *<p>
     * Note: this property was initially removed from Woodstox 4.0,
     * but was reintroduced in 4.0.8 due to user request.
     */
    public final static String P_NORMALIZE_LFS = "com.ctc.wstx.normalizeLFs";

    //public final static String P_NORMALIZE_ATTR_VALUES = "com.ctc.wstx.normalizeAttrValues";

    // // // XML character validation:

    /**
     * Whether readers will verify that characters in text content are fully
     * valid XML characters (not just Unicode). If true, will check
     * that they are valid (including white space); if false, will not
     * check.
     *<p>
     * Turning this option off may improve parsing performance; leaving
     * it on guarantees compatibility with XML 1.0 specs regarding character
     * validity rules.
     */
    public final static String P_VALIDATE_TEXT_CHARS = "com.ctc.wstx.validateTextChars";


    // // // Caching:

    /**
     * Whether readers will try to cache parsed external DTD subsets or not.
     */

    public final static String P_CACHE_DTDS = "com.ctc.wstx.cacheDTDs";

    /**
     * Whether reader is to cache DTDs (when caching enabled) based on public id
     * or not: if not, system id will be primarily used. Although theoretically
     * public IDs should be unique, and should be good caching keys, sometimes
     * broken documents use 'wrong' public IDs, and such by default caching keys
     * are based on system id only.
     */
    public final static String P_CACHE_DTDS_BY_PUBLIC_ID = "com.ctc.wstx.cacheDTDsByPublicId";


    // // // Enabling/disabling lazy/incomplete parsing

    /**
     * Whether stream readers are allowed to do lazy parsing, meaning
     * to parse minimal part of the event when
     * {@link javax.xml.stream.XMLStreamReader#next} is called, and only parse the rest
     * as needed (or skip remainder of no extra information is needed).
     * Alternative to lazy parsing is called "eager parsing", and is
     * what most xml parsers use by default.
     *<p>
     * Enabling lazy parsing can improve performance for tasks where
     * number of textual events are skipped. The downside is that
     * not all well-formedness problems are reported when
     * {@link javax.xml.stream.XMLStreamReader#next} is called, but only when the
     * rest of event are read or skipped.
     *<p>
     * Default value for Woodstox is such that lazy parsing is
     * enabled.
     *
     * @deprecated As of Woodstox 4.0 use
     *  {@link XMLInputFactory2#P_LAZY_PARSING} instead (from
     *  Stax2 extension API, v3.0)
     */
    @Deprecated
    public final static String P_LAZY_PARSING = XMLInputFactory2.P_LAZY_PARSING;

    // // // API behavior (for backwards compatibility)

    /**
     * This read-only property indicates whether null is returned for default name space prefix;
     * Boolean.TRUE indicates it does, Boolean.FALSE that it does not.
     *<p>
     * Default value for 4.1 is 'false'; this will most likely change for 5.0 since
     * Stax API actually specifies null to be used.
     * 
     * @since 4.1.2
     */
    public final static String P_RETURN_NULL_FOR_DEFAULT_NAMESPACE = "com.ctc.wstx.returnNullForDefaultNamespace";
    
    // // // Enabling/disabling support for dtd++

    /**
     * Whether the Reader will recognized DTD++ extensions when parsing
     * DTD subsets.
     *<p>
     * Note: not implemented by Woodstox.
     * 
     * @deprecated Never implement, let's phase this out (deprecated in 4.2)
     */
    public final static String P_SUPPORT_DTDPP = "com.ctc.wstx.supportDTDPP";
    
    
    /**
     * Whether the Reader will treat character references as entities while parsing 
     * XML documents. 
     */
    public static final String P_TREAT_CHAR_REFS_AS_ENTS = "com.ctc.wstx.treatCharRefsAsEnts";

    // // // Enabling alternate mode for parsing XML fragments instead
    // // // of full documents

    // Automatic W3C Schema support?
    /*
     * Whether W3C Schema hint attributes are recognized within document,
     * and used to locate Schema to use for validation.
     */
    //public final static String P_AUTOMATIC_W3C_SCHEMA = 0x00100000;

    /*
    ///////////////////////////////////////////////////////
    // More complex settings
    ///////////////////////////////////////////////////////
     */

    // // // Buffer sizes;

    /**
     * Size of input buffer (in chars), to use for reading XML content
     * from input stream/reader.
     */
    public final static String P_INPUT_BUFFER_LENGTH = "com.ctc.wstx.inputBufferLength";

    // // // Constraints on sizes of text segments parsed:


    /**
     * Property to specify shortest non-complete text segment (part of
     * CDATA section or text content) that parser is allowed to return,
     * if not required to coalesce text.
     */
    public final static String P_MIN_TEXT_SEGMENT = "com.ctc.wstx.minTextSegment";
    
    // // // Other size constraints (4.2+)

    /**
     * Maximum number of attributes allowed for single XML element.
     * @since 4.2
     */
    public final static String P_MAX_ATTRIBUTES_PER_ELEMENT = "com.ctc.wstx.maxAttributesPerElement";

    /**
     * Maximum length of of individual attribute values (in characters)
     * @since 4.2
     */
    public final static String P_MAX_ATTRIBUTE_SIZE = "com.ctc.wstx.maxAttributeSize";

    /**
     * Maximum number of child elements for any given element.
     * @since 4.2
     */
    public final static String P_MAX_CHILDREN_PER_ELEMENT = "com.ctc.wstx.maxChildrenPerElement";

    /**
     * Maximum number of all elements in a single document.
     * @since 4.2
     */
    public final static String P_MAX_ELEMENT_COUNT = "com.ctc.wstx.maxElementCount";

    /**
     * Maximum level of nesting of XML elements, starting with root element.
     * @since 4.2
     */
    public final static String P_MAX_ELEMENT_DEPTH = "com.ctc.wstx.maxElementDepth";

    /**
     * Maximum length of input document, in characters.
     * @since 4.2
     */
    public final static String P_MAX_CHARACTERS = "com.ctc.wstx.maxCharacters";

    /**
     * Maximum length of individual text (cdata) segments in input, in characters.
     * @since 4.2
     */
    public final static String P_MAX_TEXT_LENGTH = "com.ctc.wstx.maxTextLength";

    // and more size constraints (4.3+)

    /**
     * Maximum number of total (general parsed) entity expansions within input.
     * 
     * @since 4.3
     */
    public final static String P_MAX_ENTITY_COUNT = "com.ctc.wstx.maxEntityCount";

    /**
     * Maximum depth of nested (general parsed) entity expansions.
     * 
     * @since 4.3
     */
    public final static String P_MAX_ENTITY_DEPTH = "com.ctc.wstx.maxEntityDepth";

    // // // Entity handling

    /**
     * Property of type {@link java.util.Map}, that defines explicit set of
     * internal (generic) entities that will define of override any entities
     * defined in internal or external subsets; except for the 5 pre-defined
     * entities (lt, gt, amp, apos, quot). Can be used to explicitly define
     * entites that would normally come from a DTD.
     *<p>
     * @deprecated This feature may be removed from future versions of
     *   Woodstox, since the same functionality can be achieved by using
     *   custom entity resolvers.
     */
    @Deprecated
    public final static String P_CUSTOM_INTERNAL_ENTITIES = "com.ctc.wstx.customInternalEntities";

    /**
     * Property of type {@link XMLResolver}, that
     * will allow overriding of default DTD and external parameter entity
     * resolution.
     */
    public final static String P_DTD_RESOLVER = "com.ctc.wstx.dtdResolver";

    /**
     * Property of type {@link XMLResolver}, that
     * will allow overriding of default external general entity
     * resolution. Note that using this property overrides settings done
     * using {@link javax.xml.stream.XMLInputFactory#RESOLVER} (and vice versa).
     */
    public final static String P_ENTITY_RESOLVER = "com.ctc.wstx.entityResolver";
    
    /**
     * Property of type {@link XMLResolver}, that
     * will allow graceful handling of references to undeclared (general)
     * entities.
     */
    public final static String P_UNDECLARED_ENTITY_RESOLVER = "com.ctc.wstx.undeclaredEntityResolver";

    /**
     * Property of type {@link java.net.URL}, that will allow specifying
     * context URL to use when resolving relative references, for the
     * main-level entities (external DTD subset, references from the internal
     * DTD subset).
     */
    public final static String P_BASE_URL = "com.ctc.wstx.baseURL";

    // // // Alternate parsing modes

    /**
     * Three-valued property (one of
     * {@link #PARSING_MODE_DOCUMENT},
     * {@link #PARSING_MODE_FRAGMENT} or
     * {@link #PARSING_MODE_DOCUMENTS}; default being the document mode)
     * that can be used to handle "non-standard" XML content. The default
     * mode (<code>PARSING_MODE_DOCUMENT</code>) allows parsing of only
     * well-formed XML documents, but the other two modes allow more lenient
     * parsing. Fragment mode allows parsing of XML content that does not
     * have a single root element (can have zero or more), nor can have
     * XML or DOCTYPE declarations: this may be useful if parsing a subset
     * of a full XML document. Multi-document
     * (<code>PARSING_MODE_DOCUMENTS</code>) mode on the other hand allows
     * parsing of a stream that contains multiple consequtive well-formed
     * documents, with possibly multiple XML and DOCTYPE declarations.
     *<p>
     * The main difference from the API perspective is that in first two
     * modes, START_DOCUMENT and END_DOCUMENT are used as usual (as the first
     * and last events returned), whereas the multi-document mode can return
     * multiple pairs of these events: although it is still true that the
     * first event (one cursor points to when reader is instantiated or
     * returned by the event reader), there may be intervening pairs that
     * signal boundary between two adjacent enclosed documents.
     */
    public final static String P_INPUT_PARSING_MODE = "com.ctc.wstx.fragmentMode";

    // // // DTD defaulting, overriding

    /*
    ////////////////////////////////////////////////////////////////////
    // Helper classes, values enumerations
    ////////////////////////////////////////////////////////////////////
     */

    public final static ParsingMode PARSING_MODE_DOCUMENT = new ParsingMode();
    public final static ParsingMode PARSING_MODE_FRAGMENT = new ParsingMode();
    public final static ParsingMode PARSING_MODE_DOCUMENTS = new ParsingMode();

    /**
     * Inner class used for creating type-safe enumerations (prior to JDK 1.5).
     */
    public final static class ParsingMode
    {
        ParsingMode() { }
    }
}
