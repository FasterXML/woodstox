package com.ctc.wstx.cfg;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamConstants;

/**
 * "Static" class that contains error message constants. Note that the
 * error message constants are NOT made final; reason is that doing so
 * would make compiler inline them in other classes. Doing so would increase
 * class size (although not mem usage -- Strings do get interned), with
 * minimal performance impact.
 */
public class ErrorConsts
    implements XMLStreamConstants
{
    // // // Types of warnings we issue via XMLReporter

    public static String WT_ENT_DECL = "entity declaration";
    public static String WT_ELEM_DECL = "element declaration";
    public static String WT_ATTR_DECL = "attribute declaration";
    public static String WT_XML_DECL = "xml declaration";
    public static String WT_DT_DECL = "doctype declaration";
    public static String WT_NS_DECL = "namespace declaration";

    /**
     * This is the generic type for warnings based on XMLValidationProblem
     * objects.
     */
    public static String WT_VALIDATION = "schema validation";

    // // And then warning strings

    public static String W_UNDEFINED_ELEM = "Undefined element \"{0}\"; referred to by attribute(s)";
    public static String W_MIXED_ENCODINGS = "Inconsistent text encoding; declared as \"{0}\" in xml declaration, application had passed \"{1}\"";
    public static String W_MISSING_DTD = "Missing DOCTYPE declaration in validating mode; can not validate elements or attributes";
    public static String W_DTD_DUP_ATTR = "Attribute \"{0}\" (for element <{1}>) declared multiple times";
    public static String W_DTD_ATTR_REDECL = "Attribute \"{0}\" already declared for element <{1}>; ignoring re-declaration";

    // // // Generic errors:

    public static String ERR_INTERNAL = "Internal error";
    public static String ERR_NULL_ARG = "Illegal to pass null as argument";
    public static String ERR_UNKNOWN_FEATURE = "Unrecognized feature \"{0}\"";

    // // // Wrong reader state:

    public static String ERR_STATE_NOT_STELEM = "Current event not START_ELEMENT";
    public static String ERR_STATE_NOT_ELEM = "Current event not START_ELEMENT or END_ELEMENT";
    public static String ERR_STATE_NOT_PI = "Current event not PROCESSING_INSTRUCTION";
    public static String ERR_STATE_NOT_ELEM_OR_TEXT = "Current event ({0}) not START_ELEMENT, END_ELEMENT, CHARACTERS or CDATA";

    // // // XML declaration related problems

    public static String ERR_XML_10_VS_11 = "XML 1.0 document can not refer to XML 1.1 parsed external entities";

    // // // Structural problems, prolog/epilog:

    public static String ERR_DTD_IN_EPILOG = "Can not have DOCTYPE declaration in epilog";
    public static String ERR_DTD_DUP = "Duplicate DOCTYPE declaration";
    public static String ERR_CDATA_IN_EPILOG = " (CDATA not allowed in prolog/epilog)";

    // // // Illegal input:

    public static String ERR_HYPHENS_IN_COMMENT = "String '--' not allowed in comment (missing '>'?)";
    public static String ERR_BRACKET_IN_TEXT = "String ']]>' not allowed in textual content, except as the end marker of CDATA section";

    // // // Generic parsing errors:

    public static String ERR_UNEXP_KEYWORD = "Unexpected keyword \"{0}\"; expected \"{1}\"";

    public static String ERR_WF_PI_MISSING_TARGET = "Missing processing instruction target";
    public static String ERR_WF_PI_XML_TARGET = "Illegal processing instruction target (\"{0}\"); 'xml' (case insensitive) is reserved by the specs.";
    public static String ERR_WF_PI_XML_MISSING_SPACE = "excepted either space or \"?>\" after PI target";

    // // // Entity problems:

    public static String ERR_WF_ENTITY_EXT_DECLARED = "Entity \"{0}\" declared externally, but referenced from a document declared 'standalone=\"yes\"'";

    public static String ERR_WF_GE_UNDECLARED = "Undeclared general entity \"{0}\"";

    public static String ERR_WF_GE_UNDECLARED_SA = "Undeclared general entity \"{0}\" (document in stand-alone mode; perhaps declared externally?)";

    // // // Namespace problems:

    public static String ERR_NS_UNDECLARED = "Undeclared namespace prefix \"{0}\"";
    public static String ERR_NS_UNDECLARED_FOR_ATTR = "Undeclared namespace prefix \"{0}\" (for attribute \"{1}\")";

    public static String ERR_NS_REDECL_XML = "Trying to redeclare prefix 'xml' from its default URI '"
                                               +XMLConstants.XML_NS_URI
                                               +"' to \"{0}\"";

    public static String ERR_NS_REDECL_XMLNS = "Trying to declare prefix 'xmlns' (illegal as per NS 1.1 #4)";

    public static String ERR_NS_REDECL_XML_URI = "Trying to bind URI '"
        +XMLConstants.XML_NS_URI+" to prefix \"{0}\" (can only bind to 'xml')";

    public static String ERR_NS_REDECL_XMLNS_URI = "Trying to bind URI '"
        +XMLConstants.XMLNS_ATTRIBUTE_NS_URI+" to prefix \"{0}\" (can not be explicitly bound)";

    public static String ERR_NS_EMPTY = 
"Non-default namespace can not map to empty URI (as per Namespace 1.0 # 2) in XML 1.0 documents";


    // // // DTD-specific:

    public static String ERR_DTD_MAINLEVEL_KEYWORD = "; expected a keyword (ATTLIST, ELEMENT, ENTITY, NOTATION), comment, or conditional section";

    public static String ERR_DTD_ATTR_TYPE = "; expected one of type (CDATA, ID, IDREF, IDREFS, ENTITY, ENTITIES NOTATION, NMTOKEN or NMTOKENS)";

    public static String ERR_DTD_DEFAULT_TYPE = "; expected #REQUIRED, #IMPLIED or #FIXED";

    public static String ERR_DTD_ELEM_REDEFD =
        "Trying to redefine element \"{0}\" (originally defined at {1})";
    public static String ERR_DTD_NOTATION_REDEFD =
        "Trying to redefine notation \"{0}\" (originally defined at {1})";

    public static String ERR_DTD_UNDECLARED_ENTITY =
        "Undeclared {0} entity \"{1}\"";

    public static String ERR_DTD_XML_SPACE = "Attribute xml:space has to be defined of type enumerated, and have 1 or 2 values, 'default' and/or 'preserve'";

    public static String ERR_DTD_XML_ID = "Attribute xml:id has to have attribute type of ID, as per Xml:id specification";


    // // // DTD-validation:

    public static String ERR_VLD_UNKNOWN_ELEM = "Undefined element <{0}> encountered";

    public static String ERR_VLD_EMPTY = "Element <{0}> has EMPTY content specification; can not contain {1}";
    public static String ERR_VLD_NON_MIXED = "Element <{0}> has non-mixed content specification; can not contain non-white space text, or any CDATA sections";
    public static String ERR_VLD_ANY = "Element <{0}> has ANY content specification; can not contain {1}";
    public static String ERR_VLD_UNKNOWN_ATTR = "Element <{0}> has no attribute \"{1}\"";
    public static String ERR_VLD_WRONG_ROOT = "Unexpected root element <{0}>; expected <{0}> as per DOCTYPE declaration";

    // // // Output problems:

    public static String WERR_PROLOG_CDATA =
        "Trying to output a CDATA block outside main element tree (in prolog or epilog)";
    public static String WERR_PROLOG_NONWS_TEXT =
        "Trying to output non-whitespace characters outside main element tree (in prolog or epilog)";
    public static String WERR_PROLOG_SECOND_ROOT =
        "Trying to output second root, <{0}>";

    public static String WERR_CDATA_CONTENT =
        "Illegal input: CDATA block has embedded ']]>' in it (index {0})";
    public static String WERR_COMMENT_CONTENT = 
        "Illegal input: comment content has embedded '--' in it (index {0})";

    public static String WERR_ATTR_NO_ELEM =
        "Trying to write an attribute when there is no open start element.";

    public static String WERR_NAME_EMPTY = "Illegal to pass empty name";

    public static String WERR_NAME_ILLEGAL_FIRST_CHAR = "Illegal first name character {0}";
    public static String WERR_NAME_ILLEGAL_CHAR = "Illegal name character {0}";

    /*
    ////////////////////////////////////////////////////
    // Utility methods
    ////////////////////////////////////////////////////
     */

    public static String tokenTypeDesc(int type)
    {
        switch (type) {
        case START_ELEMENT:
            return "START_ELEMENT";
        case END_ELEMENT:
            return "END_ELEMENT";
        case START_DOCUMENT:
            return "START_DOCUMENT";
        case END_DOCUMENT:
            return "END_DOCUMENT";

        case CHARACTERS:
            return "CHARACTERS";
        case CDATA:
            return "CDATA";
        case SPACE:
            return "SPACE";

        case COMMENT:
            return "COMMENT";
        case PROCESSING_INSTRUCTION:
            return "PROCESSING_INSTRUCTION";
        case DTD:
            return "DTD";
        case ENTITY_REFERENCE:
            return "ENTITY_REFERENCE";
        }
        return "["+type+"]";
    }
}
