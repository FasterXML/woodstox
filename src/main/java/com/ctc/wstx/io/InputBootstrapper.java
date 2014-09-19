package com.ctc.wstx.io;

import java.io.*;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.cfg.ErrorConsts;
import com.ctc.wstx.cfg.XmlConsts;
import com.ctc.wstx.exc.*;

/**
 * Abstract base class that defines common API used with both stream and
 * reader-based input sources. Class is responsible for opening the physical
 * input source, figure out encoding (if necessary; only for streams), and
 * then handle (optional) XML declaration.
 */
public abstract class InputBootstrapper
{
    /*
    ////////////////////////////////////////////////////////////
    // Shared string consts
    ////////////////////////////////////////////////////////////
     */

    protected final static String ERR_XMLDECL_KW_VERSION = "; expected keyword '"+XmlConsts.XML_DECL_KW_VERSION+"'";
    protected final static String ERR_XMLDECL_KW_ENCODING = "; expected keyword '"+XmlConsts.XML_DECL_KW_ENCODING+"'";
    protected final static String ERR_XMLDECL_KW_STANDALONE = "; expected keyword '"+XmlConsts.XML_DECL_KW_STANDALONE+"'";

    protected final static String ERR_XMLDECL_END_MARKER = "; expected \"?>\" end marker";

    protected final static String ERR_XMLDECL_EXP_SPACE = "; expected a white space";
    protected final static String ERR_XMLDECL_EXP_EQ = "; expected '=' after ";
    protected final static String ERR_XMLDECL_EXP_ATTRVAL = "; expected a quote character enclosing value for ";

    /*
    ////////////////////////////////////////////////////////////
    // Other consts
    ////////////////////////////////////////////////////////////
     */

    public final static char CHAR_NULL = (char) 0;
    public final static char CHAR_SPACE = (char) 0x0020;

    public final static char CHAR_NEL = (char) 0x0085;

    public final static byte CHAR_CR = '\r';
    public final static byte CHAR_LF = '\n';

    public final static byte BYTE_NULL = (byte) 0;

    public final static byte BYTE_CR = (byte) '\r';
    public final static byte BYTE_LF = (byte) '\n';

    /*
    ////////////////////////////////////////////////////////////
    // Input source info
    ////////////////////////////////////////////////////////////
     */

    protected final String mPublicId;

    protected final SystemId mSystemId;

    /*
    ////////////////////////////////////////////////////////////
    // Input location data (similar to one in WstxInputData)
    ////////////////////////////////////////////////////////////
     */

    /**
     * Current number of characters that were processed in previous blocks,
     * before contents of current input buffer.
     */
    protected int mInputProcessed = 0;

    /**
     * Current row location of current point in input buffer, starting
     * from 1
     */
    protected int mInputRow = 1;

    /**
     * Current index of the first character of the current row in input
     * buffer. Needed to calculate column position, if necessary; benefit
     * of not having column itself is that this only has to be updated
     * once per line.
     */
    protected int mInputRowStart = 0;

    /*
    ////////////////////////////////////////
    // Info from XML declaration
    ////////////////////////////////////////
    */

    //boolean mHadDeclaration = false;

    /**
     * XML declaration from the input (1.0, 1.1 or 'unknown')
     */
    int mDeclaredXmlVersion = XmlConsts.XML_V_UNKNOWN;

    /**
     * Value of encoding pseudo-attribute from xml declaration, if
     * one was found; null otherwise.
     */
    String mFoundEncoding;

    String mStandalone;

    /**
     * Flag that indicates whether input read from this input source
     * needs to be xml 1.1 compliant or not; if not, xml 1.0 is assumed.
     * State of this flag depends on parent context (if one existed), or if
     * not, on xml declaration of this input source.
     */
    boolean mXml11Handling = false;

    /*
    ////////////////////////////////////////
    // Temporary data
    ////////////////////////////////////////
    */

    /**
     * Need a short buffer to read in values of pseudo-attributes (version,
     * encoding, standalone). Don't really need tons of space; just enough
     * for the longest anticipated encoding id... and maybe few chars just
     * in case (for additional white space that we ignore)
     */
    final char[] mKeyword = new char[60];

    /*
    ////////////////////////////////////////
    // Life-cycle
    ////////////////////////////////////////
    */

    protected InputBootstrapper(String pubId, SystemId sysId)
    {
        mPublicId = pubId;
        mSystemId = sysId;
    }

    protected void initFrom(InputBootstrapper src)
    {
        mInputProcessed = src.mInputProcessed;
        mInputRow = src.mInputRow;
        mInputRowStart = src.mInputRowStart;
        mDeclaredXmlVersion = src.mDeclaredXmlVersion;
        mFoundEncoding = src.mFoundEncoding;
        mStandalone = src.mStandalone;
        mXml11Handling = src.mXml11Handling;
    }

    /*
    ////////////////////////////////////////
    // Public API
    ////////////////////////////////////////
    */

    /**
     * @param xmlVersion Optional xml version identifier of the main parsed
     *   document (if not bootstrapping the main document).
     *   Currently only relevant for checking that XML 1.0 document does not
     *   include XML 1.1 external parsed entities.
     *   If null, no checks will be done; when bootstrapping parsing of the
     *   main document, null should be passed for this argument.
     */
    public abstract Reader bootstrapInput(ReaderConfig cfg, boolean mainDoc,
                                          int xmlVersion)
        throws IOException, XMLStreamException;

    // // // Source information:

    public String getPublicId() { return mPublicId; }

    public SystemId getSystemId() { return mSystemId; }

    // // // XML declaration data:

    public int getDeclaredVersion()
    {
        return mDeclaredXmlVersion;
    }

    /**
     * @return True, if the input bootstrapped declared that it conforms
     *   to xml 1.1 (independent of where it was included from)
     */
    public boolean declaredXml11() {
        return (mDeclaredXmlVersion == XmlConsts.XML_V_11);
    }

    public String getStandalone() {
        return mStandalone;
    }

    /**
     * @return Encoding declaration found from the xml declaration, if any;
     *    null if none.
     */
    public String getDeclaredEncoding() {
        return mFoundEncoding;
    }

    // // // Location/position info:

    /**
     * @return Total number of characters read from bootstrapped input
     *   (stream, reader)
     */
    public abstract int getInputTotal();

    public int getInputRow() {
        return mInputRow;
    }

    public abstract int getInputColumn();


    // // // Misc other info

    /**
     * Actual character encoding used by the underlying input source;
     * may have been passed by the application, or auto-detected
     * by byte stream boot strapper (can not be determined from a
     * Reader source).
     *
     * @return Input encoding in use, if it could be determined or was 
     *   passed by the calling application
     */
    public abstract String getInputEncoding();

    /*
    ////////////////////////////////////////
    // Package methods, parsing
    ////////////////////////////////////////
    */

    /**
     * @param xmlVersion Optional xml version identifier of the main parsed
     *   document (if not bootstrapping the main document).
     *   Currently only relevant for checking that XML 1.0 document does not
     *   include XML 1.1 external parsed entities.
     *   If null, no checks will be done; when bootstrapping parsing of the
     *   main document, null should be passed for this argument.
     */
    protected void readXmlDecl(boolean isMainDoc, int xmlVersion)
        throws IOException, WstxException
    {
        int c = getNextAfterWs(false);

        // First, version pseudo-attribute:

        if (c != 'v') { // version info obligatory for main docs
            if (isMainDoc) {
                reportUnexpectedChar(c, ERR_XMLDECL_KW_VERSION);
            }
        } else { // ok, should be version
            mDeclaredXmlVersion = readXmlVersion();
            c = getWsOrChar('?');
        }

        /* 17-Feb-2006, TSa: Whether we are to be xml 1.1 compliant or not
         *   depends on parent context, if any; and if not, on actual
         *   xml declaration. But in former case, it is illegal to include
         *   xml 1.1 declared entities from xml 1.0 context.
         */
        boolean thisIs11 = (mDeclaredXmlVersion == XmlConsts.XML_V_11);
        if (xmlVersion != XmlConsts.XML_V_UNKNOWN) { // happens when reading main doc
            mXml11Handling = (XmlConsts.XML_V_11 == xmlVersion);
            // Can not refer to xml 1.1 entities from 1.0 doc:
            if (thisIs11 && !mXml11Handling) {
                reportXmlProblem(ErrorConsts.ERR_XML_10_VS_11);
            }
        } else {
            mXml11Handling = thisIs11;
        }

        // Then, 'encoding'
        if (c != 'e') { // obligatory for external entities
            if (!isMainDoc) {
                reportUnexpectedChar(c, ERR_XMLDECL_KW_ENCODING);
            }
        } else {
            mFoundEncoding = readXmlEncoding();
            c = getWsOrChar('?');
        }

        // Then, 'standalone' (for main doc)
        if (isMainDoc && c == 's') {
            mStandalone = readXmlStandalone();
            c = getWsOrChar('?');
        }

        // And finally, need to have closing markers

        if (c != '?') {
            reportUnexpectedChar(c, ERR_XMLDECL_END_MARKER);
        }
        c = getNext();
        if (c != '>') {
            reportUnexpectedChar(c, ERR_XMLDECL_END_MARKER);
        }
    }

    /**
     * @return Xml version declaration read
     */
    private final int readXmlVersion()
        throws IOException, WstxException
    {
        int c = checkKeyword(XmlConsts.XML_DECL_KW_VERSION);
        if (c != CHAR_NULL) {
            reportUnexpectedChar(c, XmlConsts.XML_DECL_KW_VERSION);
        }
        c = handleEq(XmlConsts.XML_DECL_KW_VERSION);
        int len = readQuotedValue(mKeyword, c);

        if (len == 3) {
            if (mKeyword[0] == '1' && mKeyword[1] == '.') {
                c = mKeyword[2];
                if (c == '0') {
                    return XmlConsts.XML_V_10;
                }
                if (c == '1') {
                    return XmlConsts.XML_V_11;
                }
            }
        }

        // Nope; error. -1 indicates run off...
        String got;

        if (len < 0) {
            got = "'"+new String(mKeyword)+"[..]'";
        } else if (len == 0) {
            got = "<empty>";
        } else {
            got = "'"+new String(mKeyword, 0, len)+"'";
        }
        reportPseudoAttrProblem(XmlConsts.XML_DECL_KW_VERSION, got,
                                XmlConsts.XML_V_10_STR, XmlConsts.XML_V_11_STR);
        return XmlConsts.XML_V_UNKNOWN; // never gets here, but compiler needs it
    }

    private final String readXmlEncoding()
        throws IOException, WstxException
    {
        int c = checkKeyword(XmlConsts.XML_DECL_KW_ENCODING);
        if (c != CHAR_NULL) {
            reportUnexpectedChar(c, XmlConsts.XML_DECL_KW_ENCODING);
        }
        c = handleEq(XmlConsts.XML_DECL_KW_ENCODING);

        int len = readQuotedValue(mKeyword, c);

        /* Hmmh. How about "too long" encodings? Maybe just truncate them,
         * for now?
         */
        if (len == 0) { // let's still detect missing value...
            reportPseudoAttrProblem(XmlConsts.XML_DECL_KW_ENCODING, null,
                                    null, null);
        }

        if (len < 0) { // will be truncated...
            return new String(mKeyword);
        }
        return new String(mKeyword, 0, len);
    }

    private final String readXmlStandalone()
        throws IOException, WstxException
    {
        int c = checkKeyword(XmlConsts.XML_DECL_KW_STANDALONE);
        if (c != CHAR_NULL) {
            reportUnexpectedChar(c, XmlConsts.XML_DECL_KW_STANDALONE);
        }
        c = handleEq(XmlConsts.XML_DECL_KW_STANDALONE);
        int len = readQuotedValue(mKeyword, c);

        if (len == 2) {
            if (mKeyword[0] == 'n' && mKeyword[1] == 'o') {
                return XmlConsts.XML_SA_NO;
            }
        } else if (len == 3) {
            if (mKeyword[0] == 'y' && mKeyword[1] == 'e'
                && mKeyword[2] == 's') {
                return XmlConsts.XML_SA_YES;
            }
        }

        // Nope; error. -1 indicates run off...
        String got;

        if (len < 0) {
            got = "'"+new String(mKeyword)+"[..]'";
        } else if (len == 0) {
            got = "<empty>";
        } else {
            got = "'"+new String(mKeyword, 0, len)+"'";
        }

        reportPseudoAttrProblem(XmlConsts.XML_DECL_KW_STANDALONE, got,
                                XmlConsts.XML_SA_YES, XmlConsts.XML_SA_NO);
        return got; // never gets here, but compiler can't figure it out
    }

    private final int handleEq(String attr)
        throws IOException, WstxException
    {
        int c = getNextAfterWs(false);
        if (c != '=') {
            reportUnexpectedChar(c, ERR_XMLDECL_EXP_EQ+"'"+attr+"'");
        }

        c = getNextAfterWs(false);
        if (c != '"' && c != '\'') {
            reportUnexpectedChar(c, ERR_XMLDECL_EXP_ATTRVAL+"'"+attr+"'");
        }
        return c;
    }

    /**
     * Method that should get next character, which has to be either specified
     * character (usually end marker), OR, any character as long as there'
     * at least one space character before it.
     */
    private final int getWsOrChar(int ok)
        throws IOException, WstxException
    {
        int c = getNext();
        if (c == ok) {
            return c;
        }
        if (c > CHAR_SPACE) {
            reportUnexpectedChar(c, "; expected either '"+((char) ok)+"' or white space");
        }
        if (c == CHAR_LF || c == CHAR_CR) {
            // Need to push it back to be processed properly
            pushback();
        }
        return getNextAfterWs(false);
    }

    /*
    ////////////////////////////////////////////////////////
    // Abstract parsing methods for sub-classes to implement
    ////////////////////////////////////////////////////////
    */

    protected abstract void pushback();

    protected abstract int getNext()
        throws IOException, WstxException;

    protected abstract int getNextAfterWs(boolean reqWs)
        throws IOException, WstxException;

    /**
     * @return First character that does not match expected, if any;
     *    CHAR_NULL if match succeeded
     */
    protected abstract int checkKeyword(String exp)
        throws IOException, WstxException;

    protected abstract int readQuotedValue(char[] kw, int quoteChar)
        throws IOException, WstxException;

    protected abstract Location getLocation();

    /*
    ////////////////////////////////////////////////////////
    // Package methods available to sub-classes:
    ////////////////////////////////////////////////////////
    */

    protected void reportNull()
        throws WstxException
    {
        throw new WstxException("Illegal null byte in input stream",
                                getLocation());
    }

    protected void reportXmlProblem(String msg)
        throws WstxException
    {
        throw new WstxParsingException(msg, getLocation());
    }

    protected void reportUnexpectedChar(int i, String msg)
        throws WstxException
    {
        char c = (char) i;
        String excMsg;

        // WTF? JDK thinks null char is just fine as?!
        if (Character.isISOControl(c)) {
            excMsg = "Unexpected character (CTRL-CHAR, code "+i+")"+msg;
        } else {
            excMsg = "Unexpected character '"+c+"' (code "+i+")"+msg;
        }
        Location loc = getLocation();
        throw new WstxUnexpectedCharException(excMsg, loc, c);
    }

    /*
    ////////////////////////////////////////
    // Other private methods:
    ////////////////////////////////////////
    */

    private final void reportPseudoAttrProblem(String attrName, String got,
                                               String expVal1, String expVal2)
        throws WstxException
    {
        String expStr = (expVal1 == null) ? "" :
            ("; expected \""+expVal1+"\" or \""+expVal2+"\"");

        if (got == null || got.length() == 0) {
            throw new WstxParsingException("Missing XML pseudo-attribute '"+attrName+"' value"+expStr,
                                           getLocation());
        }
        throw new WstxParsingException("Invalid XML pseudo-attribute '"+attrName+"' value "+got+expStr,
                                       getLocation());
    }
}
