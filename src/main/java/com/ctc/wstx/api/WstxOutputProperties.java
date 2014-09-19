package com.ctc.wstx.api;

/**
 * Class that contains constant for property names used to configure
 * cursor and event writers produced by Wstx implementation of
 * {@link javax.xml.stream.XMLOutputFactory}.
 *<p>
 */
public final class WstxOutputProperties
{
    /**
     * Default xml version number output, if none was specified by
     * application. Version 1.0 is used 
     * to try to maximize compatibility (some older parsers
     * may barf on 1.1 and later...)
     */
    public final static String DEFAULT_XML_VERSION = "1.0";

    /**
     * If no encoding is passed, we should just default to what xml
     * in general expects (and can determine), UTF-8.
     *<p>
     * Note: you can check out bug entry [WSTX-18] for more details
     */
    public final static String DEFAULT_OUTPUT_ENCODING = "UTF-8";

    // // // Output options, simple on/off settings:

    /**
     * Whether writer should use double quotes in the XML declaration.
     * The default is to use single quotes.
     *
     * @since 4.2.2
     */
    public final static String P_USE_DOUBLE_QUOTES_IN_XML_DECL = "com.ctc.wstx.useDoubleQuotesInXmlDecl";

    /**
     * Whether writer should just automatically convert all calls that
     * would normally produce CDATA to produce (quoted) text.
     */
    public final static String P_OUTPUT_CDATA_AS_TEXT = "com.ctc.wstx.outputCDataAsText";

    /**
     * Whether writer should copy attributes that were initially expanded
     * using default settings ("implicit" attributes) or not.
     */
    public final static String P_COPY_DEFAULT_ATTRS = "com.ctc.wstx.copyDefaultAttrs";

    /**
     * Whether writer is to add a single white space before closing "/>"
     * of the empty element or not. It is sometimes useful to add to
     * increase compatibility with HTML browsers, or to increase
     * readability.
     *<p>
     * The default value is 'false', up to Woodstox 4.x.
     *<p>
     * <b>NOTE</b>: JavaDocs for versions 4.0.0 - 4.0.7 incorrectly state that
     * default is 'true': this is NOT the case.
     *<p>
     * Note: added to resolve Jira entry 
     * <a href="http://jira.codehaus.org/browse/WSTX-125">WSTX-125</a>.
     */
    public final static String P_ADD_SPACE_AFTER_EMPTY_ELEM = "com.ctc.wstx.addSpaceAfterEmptyElem";

    /**
     * Whether stream writer is to automatically add end elements that are
     * needed to properly close the output tree, when the stream is closed
     * (either explicitly by a call to <code>close</code> or
     * <code>closeCompletely</code>, or implicitly by a call
     * to <code>writeEndDocument</code>.
     *<p>
     * The default value is 'true' as of Woodstox 4.x.
     * Prior to 4.0, this feature was always enabled and there was no
     * way to disable it)
     *
     * @since 3.2.8
     */
    public final static String P_AUTOMATIC_END_ELEMENTS = "com.ctc.wstx.automaticEndElements";

    // // // Validation options:

    /**
     * Whether output classes should do basic verification that the output
     * structure is well-formed (start and end elements match); that
     * there is one and only one root, and that there is no textual content
     * in prolog/epilog. If false, won't do any checking regarding structure.
     */
    public final static String P_OUTPUT_VALIDATE_STRUCTURE = "com.ctc.wstx.outputValidateStructure";

    /**
     * Whether output classes should do basic verification that the textual
     * content output as part of nodes should be checked for validity,
     * if there's a possibility of invalid content. Nodes that include
     * such constraints are: comment/'--', cdata/']]>',
     * proc. instr/'?>'.
     */
    public final static String P_OUTPUT_VALIDATE_CONTENT = "com.ctc.wstx.outputValidateContent";

    /**
     * Whether output classes should check uniqueness of attribute names,
     * to prevent accidental output of duplicate attributes.
     */
    public final static String P_OUTPUT_VALIDATE_ATTR = "com.ctc.wstx.outputValidateAttr";

    /**
     * Whether output classes should check validity of names, ie that they
     * only contain legal XML identifier characters.
     */
    public final static String P_OUTPUT_VALIDATE_NAMES = "com.ctc.wstx.outputValidateNames";

    /**
     * Property that further modifies handling of invalid content so
     * that if {@link #P_OUTPUT_VALIDATE_CONTENT} is enabled, instead of
     * reporting an error, writer will try to fix the problem.
     * Invalid content in this context refers  to comment
     * content with "--", CDATA with "]]>" and proc. instr data with "?>".
     * This can
     * be done for some content (CDATA, possibly comment), by splitting
     * content into separate
     * segments; but not for others (proc. instr, since that might
     * change the semantics in unintended ways).
     */
    public final static String P_OUTPUT_FIX_CONTENT = "com.ctc.wstx.outputFixContent";

    /**
     * Property that determines whether Carriage Return (\r) characters are
     * to be escaped when output or not. If enabled, all instances of
     * of character \r are escaped using a character entity (where possible,
     * that is, within CHARACTERS events, and attribute values). Otherwise
     * they are output as is. The main reason to enable this property is
     * to ensure that carriage returns are preserved as is through parsing,
     * since otherwise they will be converted to canonical xml linefeeds
     * (\n), when occuring along or as part of \r\n pair.
     */
    public final static String P_OUTPUT_ESCAPE_CR = "com.ctc.wstx.outputEscapeCr";

    /**
     * Property that defines a {@link InvalidCharHandler} used to determine
     * what to do with a Java character that app tries to output but which
     * is not a valid xml character. Alternatives are converting it to
     * another character or throw an exception: default implementations
     * exist for both behaviors.
     */
    public final static String P_OUTPUT_INVALID_CHAR_HANDLER = "com.ctc.wstx.outputInvalidCharHandler";
    
    /**
     * Property that defines an {@link EmptyElementHandler} used to determine
     * if the end tag for an empty element should be written or not.
     * 
     * If specified {@link org.codehaus.stax2.XMLOutputFactory2#P_AUTOMATIC_EMPTY_ELEMENTS} is ignored.
     */
    public final static String P_OUTPUT_EMPTY_ELEMENT_HANDLER = "com.ctc.wstx.outputEmptyElementHandler";

    // // // Per-instance access to underlying output objects

    /**
     * Property that can be used to find out the underlying
     * {@link java.io.OutputStream} that an
     * {@link javax.xml.stream.XMLStreamWriter} instance is using,
     * if known (not known if constructed with a {@link java.io.Writer},
     * or other non-stream destination). Null is returned, if not
     * known.
     *<p>
     * Note: in general it is dangerous to operate on returned stream
     * (if any), due to buffering stream writer can do. As such, caller
     * has to take care to know what he is doing, including properly
     * flushing output.
     */
    public final static String P_OUTPUT_UNDERLYING_STREAM = "com.ctc.wstx.outputUnderlyingStream";

    /**
     * Property that can be used to find out the underlying
     * {@link java.io.Writer} that an
     * {@link javax.xml.stream.XMLStreamWriter} instance is using,
     * if known (may not be known if constructed with a {@link java.io.OutputStream},
     * or other non-Writer destination). Null is returned, if not
     * known. Note that the Writer may be an internal wrapper over
     * an output stream.
     *<p>
     * Note: in general it is dangerous to operate on returned Writer
     * (if any), due to buffering stream writer can do. As such, caller
     * has to take care to know what he is doing, including properly
     * flushing output.
     */
    public final static String P_OUTPUT_UNDERLYING_WRITER = "com.ctc.wstx.outputUnderlyingWriter";
}
