/* Woodstox XML processor
 *
 * Copyright (c) 2004 Tatu Saloranta, tatu.saloranta@iki.fi
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

package com.ctc.wstx.io;

import com.ctc.wstx.util.XmlChars;

/**
 * Base class used by readers (specifically, by
 * {@link com.ctc.wstx.sr.StreamScanner}, and its sub-classes)
 * to encapsulate input buffer portion of the class. Philosophically
 * this should probably be done via containment (composition), not
 * sub-classing but for performance reason, this "core" class is generally
 * extended from instead.
 *<p>
 * Main reason for the input data portion to be factored out of main
 * class is that this way it can also be passed to nested input handling
 * Objects, which can then manipulate input buffers of the caller,
 * efficiently.
 */
public class WstxInputData
{
    // // // Some well-known chars:

    /**
     * Null-character is used as return value from some method(s), since
     * it is not a legal character in an XML document.
     */
    public final static char CHAR_NULL = '\u0000';
    public final static char INT_NULL = 0;

    public final static char CHAR_SPACE = (char) 0x0020;
    public final static char INT_SPACE = 0x0020;

    /**
     * This constant defines the highest Unicode character allowed
     * in XML content.
     */
    public final static int MAX_UNICODE_CHAR = 0x10FFFF;

    /*
    ////////////////////////////////////////////////////
    // Character validity constants, structs
    ////////////////////////////////////////////////////
     */

    /**
     * We will only use validity array for first 256 characters, mostly
     * because after those characters it's easier to do fairly simple
     * block checks.
     */
    private final static int VALID_CHAR_COUNT = 0x100;

    // These are the same for both 1.0 and 1.1...
//    private final static int FIRST_VALID_FOR_FIRST = 0x0041; // 'A'
//    private final static int FIRST_VALID_FOR_REST = 0x002D; // '.'

    private final static byte NAME_CHAR_INVALID_B = (byte) 0;
    private final static byte NAME_CHAR_ALL_VALID_B = (byte) 1;
    private final static byte NAME_CHAR_VALID_NONFIRST_B = (byte) -1;

    private final static byte[] sCharValidity = new byte[VALID_CHAR_COUNT];

    static {
        /* First, since all valid-as-first chars are also valid-as-other chars,
         * we'll initialize common chars:
         */
        sCharValidity['_'] = NAME_CHAR_ALL_VALID_B;
        for (int i = 0, last = ('z' - 'a'); i <= last; ++i) {
            sCharValidity['A' + i] = NAME_CHAR_ALL_VALID_B;
            sCharValidity['a' + i] = NAME_CHAR_ALL_VALID_B;
        }
        // not all are fully valid, but
        for (int i = 0xC0; i < VALID_CHAR_COUNT; ++i) {
            sCharValidity[i] = NAME_CHAR_ALL_VALID_B;
        }
        // ... now we can 'revert' ones not fully valid:
        sCharValidity[0xD7] = NAME_CHAR_INVALID_B;
        sCharValidity[0xF7] = NAME_CHAR_INVALID_B;

        /* And then we can proceed with ones only valid-as-other.
         */
        sCharValidity['-'] = NAME_CHAR_VALID_NONFIRST_B;
        sCharValidity['.'] = NAME_CHAR_VALID_NONFIRST_B;
        sCharValidity[0xB7] = NAME_CHAR_VALID_NONFIRST_B;
        for (int i = '0'; i <= '9'; ++i) {
            sCharValidity[i] = NAME_CHAR_VALID_NONFIRST_B;
        }
    }

    /**
     * Public identifiers only use 7-bit ascii range.
     */
    private final static int VALID_PUBID_CHAR_COUNT = 0x80;
    private final static byte[] sPubidValidity = new byte[VALID_PUBID_CHAR_COUNT];
//    private final static byte PUBID_CHAR_INVALID_B = (byte) 0;
    private final static byte PUBID_CHAR_VALID_B = (byte) 1;
    static {
        for (int i = 0, last = ('z' - 'a'); i <= last; ++i) {
            sPubidValidity['A' + i] = PUBID_CHAR_VALID_B;
            sPubidValidity['a' + i] = PUBID_CHAR_VALID_B;
        }
        for (int i = '0'; i <= '9'; ++i) {
            sPubidValidity[i] = PUBID_CHAR_VALID_B;
        }

        // 3 main white space types are valid
        sPubidValidity[0x0A] = PUBID_CHAR_VALID_B;
        sPubidValidity[0x0D] = PUBID_CHAR_VALID_B;
        sPubidValidity[0x20] = PUBID_CHAR_VALID_B;

        // And many of punctuation/separator ascii chars too:
        sPubidValidity['-'] = PUBID_CHAR_VALID_B;
        sPubidValidity['\''] = PUBID_CHAR_VALID_B;
        sPubidValidity['('] = PUBID_CHAR_VALID_B;
        sPubidValidity[')'] = PUBID_CHAR_VALID_B;
        sPubidValidity['+'] = PUBID_CHAR_VALID_B;
        sPubidValidity[','] = PUBID_CHAR_VALID_B;
        sPubidValidity['.'] = PUBID_CHAR_VALID_B;
        sPubidValidity['/'] = PUBID_CHAR_VALID_B;
        sPubidValidity[':'] = PUBID_CHAR_VALID_B;
        sPubidValidity['='] = PUBID_CHAR_VALID_B;
        sPubidValidity['?'] = PUBID_CHAR_VALID_B;
        sPubidValidity[';'] = PUBID_CHAR_VALID_B;
        sPubidValidity['!'] = PUBID_CHAR_VALID_B;
        sPubidValidity['*'] = PUBID_CHAR_VALID_B;
        sPubidValidity['#'] = PUBID_CHAR_VALID_B;
        sPubidValidity['@'] = PUBID_CHAR_VALID_B;
        sPubidValidity['$'] = PUBID_CHAR_VALID_B;
        sPubidValidity['_'] = PUBID_CHAR_VALID_B;
        sPubidValidity['%'] = PUBID_CHAR_VALID_B;
    }

    /*
    ////////////////////////////////////////////////////
    // Configuration
    ////////////////////////////////////////////////////
     */

    /**
     * Flag that indicates whether XML content is to be treated as per
     * XML 1.1 specification or not (if not, it'll use xml 1.0).
     */
    protected boolean mXml11 = false;

    /*
    ////////////////////////////////////////////////////
    // Current input data
    ////////////////////////////////////////////////////
     */

    /**
     * Current buffer from which data is read; generally data is read into
     * buffer from input source, but not always (especially when using nested
     * input contexts when expanding parsed entity references etc).
     */
    protected char[] mInputBuffer;

    /**
     * Pointer to next available character in buffer
     */
    protected int mInputPtr = 0;

    /**
     * Index of character after last available one in the buffer.
     */
    protected int mInputEnd = 0;

    /*
    ////////////////////////////////////////////////////
    // Current input location information
    ////////////////////////////////////////////////////
     */

    /**
     * Number of characters that were contained in previous blocks
     * (blocks that were already processed prior to the current buffer).
     */
    protected long mCurrInputProcessed = 0L;

    /**
     * Current row location of current point in input buffer, starting
     * from 1
     */
    protected int mCurrInputRow = 1;

    /**
     * Current index of the first character of the current row in input
     * buffer. Needed to calculate column position, if necessary; benefit
     * of not having column itself is that this only has to be updated
     * once per line.
     */
    protected int mCurrInputRowStart = 0;

    /*
    ////////////////////////////////////////////////////
    // Life-cycle
    ////////////////////////////////////////////////////
     */

    protected WstxInputData() {
    }

    /**
     * Note: Only public due to sub-classes needing to call this on
     * base class instance from different package (confusing?)
     */
    public void copyBufferStateFrom(WstxInputData src)
    {
        mInputBuffer = src.mInputBuffer;
        mInputPtr = src.mInputPtr;
        mInputEnd = src.mInputEnd;

        mCurrInputProcessed = src.mCurrInputProcessed;
        mCurrInputRow = src.mCurrInputRow;
        mCurrInputRowStart = src.mCurrInputRowStart;
    }

    /*
    ////////////////////////////////////////////////////
    // Public/package API, character classes
    ////////////////////////////////////////////////////
     */

    /**
     * Method that can be used to check whether specified character
     * is a valid first character of an XML 1.0/1.1 name; except that
     * colon (:) is not recognized as a start char here: caller has
     * to verify it separately (since it generally affects namespace
     * mapping of a qualified name).
     */
    protected final boolean isNameStartChar(char c)
    {
        /* First, let's handle 7-bit ascii range (identical between xml
         * 1.0 and 1.1)
         */
        if (c <= 0x7A) { // 'z' or earlier
            if (c >= 0x61) { // 'a' - 'z' are ok
                return true;
            }
            if (c < 0x41) { // before 'A' just white space
                return false;
            }
            return (c <= 0x5A) || (c == '_'); // 'A' - 'Z' and '_' are ok
        }
        /* Ok, otherwise need to use a big honking bit sets... which
         * differ between 1.0 and 1.1
         */
        return mXml11 ? XmlChars.is11NameStartChar(c) : XmlChars.is10NameStartChar(c);
    }

    /**
     * Method that can be used to check whether specified character
     * is a valid character of an XML 1.0/1.1 name as any other char than
     * the first one; except that colon (:) is not recognized as valid here:
     * caller has to verify it separately (since it generally affects namespace
     * mapping of a qualified name).
     */
    protected final boolean isNameChar(char c)
    {
        // First, let's handle 7-bit ascii range
        if (c <= 0x7A) { // 'z' or earlier
            if (c >= 0x61) { // 'a' - 'z' are ok
                return true;
            }
            if (c <= 0x5A) {
                if (c >= 0x41) { // 'A' - 'Z' ok too
                    return true;
                }
                // As are 0-9, '.' and '-'
                return (c >= 0x30 && c <= 0x39) || (c == '.') || (c == '-');
            }
            return (c == 0x5F); // '_' is ok too
        }
        return mXml11 ? XmlChars.is11NameChar(c) : XmlChars.is10NameChar(c);
    }

    public final static boolean isNameStartChar(char c, boolean nsAware, boolean xml11)
    {
        /* First, let's handle 7-bit ascii range (identical between xml
         * 1.0 and 1.1)
         */
        if (c <= 0x7A) { // 'z' or earlier
            if (c >= 0x61) { // 'a' - 'z' are ok
                return true;
            }
            if (c < 0x41) { // before 'A' just white space (and colon)
                if (c == ':' && !nsAware) {
                    return true;
                }
                return false;
            }
            return (c <= 0x5A) || (c == '_'); // 'A' - 'Z' and '_' are ok
        }
        /* Ok, otherwise need to use a big honking bit sets... which
         * differ between 1.0 and 1.1
         */
        return xml11 ? XmlChars.is11NameStartChar(c) : XmlChars.is10NameStartChar(c);
    }

    public final static boolean isNameChar(char c, boolean nsAware, boolean xml11)
    {
        // First, let's handle 7-bit ascii range
        if (c <= 0x7A) { // 'z' or earlier
            if (c >= 0x61) { // 'a' - 'z' are ok
                return true;
            }
            if (c <= 0x5A) {
                if (c >= 0x41) { // 'A' - 'Z' ok too
                    return true;
                }
                // As are 0-9, '.' and '-'
                return (c >= 0x30 && c <= 0x39) || (c == '.') || (c == '-')
                    || (c == ':' && !nsAware);
            }
            return (c == 0x5F); // '_' is ok too
        }
        return xml11 ? XmlChars.is11NameChar(c) : XmlChars.is10NameChar(c);
    }

    /**
     * Method that can be called to check whether given String contains
     * any characters that are not legal XML names.
     *
     * @return Index of the first illegal xml name characters, if any;
     *   -1 if the name is completely legal
     */
    public final static int findIllegalNameChar(String name, boolean nsAware, boolean xml11)
    {
        int len = name.length();
        if (len < 1) {
            return -1;
        }

        char c = name.charAt(0);
        
        // First char legal?
        if (c <= 0x7A) { // 'z' or earlier
            if (c < 0x61) { // 'a' - 'z' (0x61 - 0x7A) are ok
                if (c < 0x41) { // before 'A' just white space (except colon)
                    if (c != ':' || nsAware) { // ':' == 0x3A
                        return 0;
                    }
                } else if ((c > 0x5A) && (c != '_')) {
                    // 'A' - 'Z' and '_' are ok
                    return 0;
                }
            }
        } else { 
            if (xml11) {
                if (!XmlChars.is11NameStartChar(c)) {
                    return 0;
                }
            } else {
                if (!XmlChars.is10NameStartChar(c)) {
                    return 0;
                }
            }
        }
        
        for (int i = 1; i < len; ++i) {
            c = name.charAt(i);
            if (c <= 0x7A) { // 'z' or earlier
                if (c >= 0x61) { // 'a' - 'z' are ok
                    continue;
                }
                if (c <= 0x5A) {
                    if (c >= 0x41) { // 'A' - 'Z' ok too
                        continue;
                    }
                    // As are 0-9, '.' and '-'
                    if ((c >= 0x30 && c <= 0x39) || (c == '.') || (c == '-')) {
                        continue;
                    }
                    // And finally, colon, in non-ns-aware mode
                    if (c == ':' && !nsAware) { // ':' == 0x3A
                        continue;
                    }
                } else if (c == 0x5F) { // '_' is ok too
                    continue;
                }
            } else {
                if (xml11) {
                    if (XmlChars.is11NameChar(c)) {
                        continue;
                    }
                } else {
                    if (XmlChars.is10NameChar(c)) {
                        continue;
                    }
                }
            }
            return i;
        }

        return -1;
    }

    public final static int findIllegalNmtokenChar(String nmtoken, boolean nsAware, boolean xml11)
    {
        int len = nmtoken.length();
        // No special handling for the first char, just the loop
        for (int i = 1; i < len; ++i) {
            char c = nmtoken.charAt(i);
            if (c <= 0x7A) { // 'z' or earlier
                if (c >= 0x61) { // 'a' - 'z' are ok
                    continue;
                }
                if (c <= 0x5A) {
                    if (c >= 0x41) { // 'A' - 'Z' ok too
                        continue;
                    }
                    // As are 0-9, '.' and '-'
                    if ((c >= 0x30 && c <= 0x39) || (c == '.') || (c == '-')) {
                        continue;
                    }
                    // And finally, colon, in non-ns-aware mode
                    if (c == ':' && !nsAware) { // ':' == 0x3A
                        continue;
                    }
                } else if (c == 0x5F) { // '_' is ok too
                    continue;
                }
            } else {
                if (xml11) {
                    if (XmlChars.is11NameChar(c)) {
                        continue;
                    }
                } else {
                    if (XmlChars.is10NameChar(c)) {
                        continue;
                    }
                }
            }
            return i;
        }
        return -1;
    }

    public final static boolean isSpaceChar(char c)
    {
        return (c <= CHAR_SPACE);
    }

    @SuppressWarnings("cast")
	public static String getCharDesc(char c)
    {
        int i = (int) c;
        if (Character.isISOControl(c)) {
            return "(CTRL-CHAR, code "+i+")";
        }
        if (i > 255) {
            return "'"+c+"' (code "+i+" / 0x"+Integer.toHexString(i)+")";
        }
        return "'"+c+"' (code "+i+")";
    }

}
