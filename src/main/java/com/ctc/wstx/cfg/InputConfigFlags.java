package com.ctc.wstx.cfg;

/**
 * Constant interface that contains configuration flag used by parser
 * and parser factory, as well as some other input constants.
 */
public interface InputConfigFlags
{
    /*
    //////////////////////////////////////////////////////
    // Flags for standard StAX features:
    //////////////////////////////////////////////////////
     */

    // // // Namespace handling:

    /**
     * If true, parser will handle namespaces according to XML specs; if
     * false, will only pass them as part of element/attribute name value
     * information.
     */
    final static int CFG_NAMESPACE_AWARE =  0x0001;


    // // // Text normalization


    /// Flag that indicates iterator should coalesce all text segments.
    final static int CFG_COALESCE_TEXT  =   0x0002;


    // // // Entity handling

    /**
     * Flag that enables automatic replacement of internal entities
     */
    final static int CFG_REPLACE_ENTITY_REFS = 0x0004;

    /**
     * Flag that enables support for expanding external entities. Woodstox
     * pretty much ignores the setting, since effectively it is irrelevant,
     * as {@link #CFG_REPLACE_ENTITY_REFS} and {@link #CFG_SUPPORT_DTD}
     * both need to be enabled for external entities to be supported.
     */
    final static int CFG_SUPPORT_EXTERNAL_ENTITIES = 0x0008;

    // // // DTD handling

    /**
     * Whether DTD handling is enabled or disabled; disabling means both
     * internal and external subsets will just be skipped unprocessed.
     */
    final static int CFG_SUPPORT_DTD = 0x0010;

    /**
     * Not yet (fully) supported; added as the placeholder
     */
    final static int CFG_VALIDATE_AGAINST_DTD = 0x0020;

    // // Note: can add 2 more 'standard' flags here... 

    /*
    //////////////////////////////////////////////////////
    // Flags for StAX2 features
    //////////////////////////////////////////////////////
     */

    /**
     * If true, parser will report (ignorable) white space events in prolog
     * and epilog; if false, it will silently ignore them.
     */
    final static int CFG_REPORT_PROLOG_WS = 0x0100;

    // // // Type conversions:


    /**
     * If true, parser will accurately report CDATA event as such (unless
     * coalescing); otherwise will always report them as CHARACTERS
     * independent of coalescing settings.
     */
    final static int CFG_REPORT_CDATA = 0x0200;

    // // // String interning:

    /**
     * If true, will guarantee that all names (attribute/element local names
     * have been intern()ed. If false, this is not guaranteed although
     * implementation may still choose to do it.
     */
    final static int CFG_INTERN_NAMES = 0x0400;

    /**
     * It true, will call intern() on all namespace URIs parsed; otherwise
     * will just use 'regular' Strings created from parsed contents. Interning
     * makes namespace-based access faster, but has initial overhead of
     * intern() call.
     */
    final static int CFG_INTERN_NS_URIS = 0x0800;

    // // // Lazy/incomplete parsing

    /**
     * Property that determines whether Event objects created will
     * contain (accurate) {@link javax.xml.stream.Location} information or not. If not,
     * Location may be null or a fixed location (beginning of main
     * XML file).
     *<p>
     * Note, however, that the underlying parser will still keep track
     * of location information for error reporting purposes; it's only
     * Event objects that are affected.
     */
    final static int CFG_PRESERVE_LOCATION = 0x1000;

    // // // Input source handling

    /**
     * Property that enables/disables stream reader to close the underlying
     * input source, either when it is asked to (.close() is called), or
     * when it doesn't need it any more (reaching EOF, hitting an
     * unrecoverable exception).
     * As per Stax 1.0 specification, automatic closing is NOT enabled by
     * default; except if the caller has no access to the target (i.e.
     * when factory created it)
     */
    final static int CFG_AUTO_CLOSE_INPUT = 0x2000;

    /*
    //////////////////////////////////////////////////////
    // Flags for Woodstox-specific features
    //////////////////////////////////////////////////////
     */

    // // // Content normalization

    // 20-Jan-2007, TSa: These properties removed from 4.0, deprecated:
    final static int CFG_NORMALIZE_LFS  =   0x4000;
    //final static int CFG_NORMALIZE_ATTR_VALUES = 0x8000;

    // // // Caching

    /**
     * If true, input factory is allowed cache parsed external DTD subsets,
     * potentially speeding up things for which DTDs are needed for: entity
     * substitution, attribute defaulting, and of course DTD-based validation.
     */
    final static int CFG_CACHE_DTDS = 0x00010000;

    /**
     * If true, key used for matching DTD subsets can be the public id,
     * if false, only system id can be used.
     */
    final static int CFG_CACHE_DTDS_BY_PUBLIC_ID = 0x00020000;

    // // // Lazy/incomplete parsing

    /**
     * If true, input factory can defer parsing of nodes until data is
     * actually needed; if false, it has to read all the data in right
     * away when next type is requested. Setting it to true is good for
     * performance, in the cases where some of the nodes (like comments,
     * processing instructions, or whole subtrees) are ignored. Otherwise
     * setting will not make much of a difference. Downside is that error
     * reporting is also done 'lazily'; not right away when getting the next
     * even type but when either accessing data, or skipping it.
     */
    final static int CFG_LAZY_PARSING = 0x00040000;

    // // // Validation support

    // DTD++ support

    /**
     * If true, DTD-parser will recognize DTD++ features, and the validator
     * will also use any such information found from DTD when DTD validation
     * is enabled.
     */
    final static int CFG_SUPPORT_DTDPP = 0x00080000;

    // Automatic W3C Schema support?
    //final static int CFG_AUTOMATIC_W3C_SCHEMA = 0x00100000;

    // // // Xml:id support

    /**
     * If true, xml:id attribute type assignment and matching checks will
     * be done as per Xml:id specification. Needs to be enabled for xml:id
     * uniqueness checks to function properly
     */
    final static int CFG_XMLID_TYPING = 0x00200000;

    /**
     * If true, xml:id attribute uniqueness constraints are enforced, even
     * if not validating against DTD otherwise.
     */
    final static int CFG_XMLID_UNIQ_CHECKS = 0x00400000;
    
    /**
     * If true, the XML parser will treat character references as entities. 
     * 
     */
    final static int CFG_TREAT_CHAR_REFS_AS_ENTS = 0x00800000;
}

