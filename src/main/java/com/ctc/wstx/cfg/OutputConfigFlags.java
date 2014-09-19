package com.ctc.wstx.cfg;

/**
 * Constant interface that contains configuration flag used by output
 * classes internally, for presenting on/off configuration options.
 */
public interface OutputConfigFlags
{
    /**
     * Flag that indicates whether writer is namespace-aware or not; if not,
     * only local part is ever used.
     */
    final static int CFG_ENABLE_NS    =        0x0001;

    /// Flag that indicates that output class should auto-generate namespace prefixes as necessary.
    final static int CFG_AUTOMATIC_NS =        0x0002;

    /// Flag that indicates we can output 'automatic' empty elements.
    final static int CFG_AUTOMATIC_EMPTY_ELEMENTS =  0x0004;

    /**
     * Whether writer should just automatically convert all calls that
     * would normally produce CDATA to produce (quoted) text.
     */
    final static int CFG_OUTPUT_CDATA_AS_TEXT = 0x0008;

    /**
     * Flag that indicates whether attributes expanded from default attribute
     * values should be copied to output, when using copy methods.
     */
    final static int CFG_COPY_DEFAULT_ATTRS =  0x0010;

    /**
     * Flag that indicates whether CR (\r, ascii 13) characters occuring
     * in text (CHARACTERS) and attribute values should be escaped using
     * character entities or not. Escaping is needed to enable seamless
     * round-tripping (preserving CR characters).
     */
    final static int CFG_ESCAPE_CR =  0x0020;

    /**
     * Flag that indicates
     * whether writer is to add a single white space before closing "/>"
     * of the empty element or not. It is sometimes useful to add to
     * increase compatibility with HTML browsers, or to increase
     * readability.
     */
    final static int CFG_ADD_SPACE_AFTER_EMPTY_ELEM =  0x0040;

    /**
     * Flag that indicates we can output 'automatic' empty elements;
     * end elements needed to close the logical output tree when
     * stream writer is closed (by closing it explicitly, or by writing
     * end-document event)
     *
     * @since 3.2.8
     */
    final static int CFG_AUTOMATIC_END_ELEMENTS =  0x0080;

    /// Flag that indicates we should check validity of output XML structure.
    final static int CFG_VALIDATE_STRUCTURE =  0x0100;

    /**
     * Flag that indicates we should check validity of textual content of
     * nodes that have constraints.
     *<p>
     * Specifically: comments can not have '--', CDATA sections can not
     * have ']]>' and processing instruction can not have '?&lt;' character
     * combinations in content passed in.
     */
    final static int CFG_VALIDATE_CONTENT =    0x0200;

    /**
     * Flag that indicates we should check validity of names (element and
     * attribute names and prefixes; processing instruction names), that they
     * contain only legal identifier characters.
     */
    final static int CFG_VALIDATE_NAMES = 0x0400;

    /**
     * Flag that indicates we should check uniqueness of attribute names,
     * to prevent accidental output of duplicate attributes.
     */
    final static int CFG_VALIDATE_ATTR = 0x0800;

    /**
     * Flag that will enable writer that checks for validity of content
     * to try to fix the problem, by splitting output segments as
     * necessary. If disabled, validation will throw an exception; and
     * without validation no problem is noticed by writer (but instead
     * invalid output is created).
     */
    final static int CFG_FIX_CONTENT =   0x1000;

    /**
     * Property that enables/disables stream write to close the underlying
     * output target, either when it is asked to (.close() is called), or
     * when it doesn't need it any more (reaching EOF, hitting an
     * unrecoverable exception).
     * As per Stax 1.0 specification, automatic closing is NOT enabled by
     * default; except if the caller has no access to the target (i.e.
     * when factory created it)
     */
    final static int CFG_AUTO_CLOSE_OUTPUT = 0x2000;

    /**
     * Property that indicates if singe quotes or double quotes should be
     * used in the XML declaration.
     * The default is to use single quotes.
     */
    final static int CFG_USE_DOUBLE_QUOTES_IN_XML_DECL = 0x4000;
}
