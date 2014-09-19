package com.ctc.wstx.cfg;

/**
 * Simple constant container interface, shared by input and output
 * sides.
 */
public interface XmlConsts
{
    // // // Constants for XML declaration

    public final static String XML_DECL_KW_ENCODING = "encoding";
    public final static String XML_DECL_KW_VERSION = "version";
    public final static String XML_DECL_KW_STANDALONE = "standalone";

    public final static String XML_V_10_STR = "1.0";
    public final static String XML_V_11_STR = "1.1";

    /**
     * This constants refers to cases where the version has not been
     * declared explicitly; and needs to be considered to be 1.0.
     */
    public final static int XML_V_UNKNOWN = 0x0000;

    public final static int XML_V_10 = 0x0100;
    public final static int XML_V_11 = 0x0110;

    public final static String XML_SA_YES = "yes";
    public final static String XML_SA_NO = "no";

    // // // Stax specs mandates some settings: but since exact
    // // // definitions have been re-interpreted a few times,
    // // // let's isolate them in a single place

    /* 13-Mar-2008, TSa: As per latest reading of Stax specs,
     *   all of these are expected to be "", not null.
     */

    public final static String ELEM_NO_NS_URI = "";

    public final static String ATTR_NO_NS_URI = "";

    public final static String ELEM_NO_PREFIX = "";

    public final static String ATTR_NO_PREFIX = "";

    /**
     * Top-most namespace URI assigned for root element, if not specifically
     * defined (default namespace unbound).
     *<p>
     * As per Stax specs, related clarifying discussion on
     * the mailing list, and especially JDK 1.6 definitions
     * in {@link javax.xml.XMLConstants} constants, <b>empty String</b>
     * should be used instead of null.
     */
    public final static String DEFAULT_NAMESPACE_URI = ELEM_NO_NS_URI;

    // // // Well, these are not strictly xml constants, but for
    // // // now can live here

    /**
     * This constant defines the highest Unicode character allowed
     * in XML content.
     */
    final static int MAX_UNICODE_CHAR = 0x10FFFF;

}
