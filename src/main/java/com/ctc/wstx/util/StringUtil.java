package com.ctc.wstx.util;

import java.util.Collection;
import java.util.Iterator;

public final class StringUtil
{
    final static char CHAR_SPACE = ' '; // 0x0020
    private final static char INT_SPACE = 0x0020;

    static String sLF = null;

    public static String getLF()
    {
        String lf = sLF;
        if (lf == null) {
            try {
                lf = System.getProperty("line.separator");
                sLF = (lf == null) ? "\n" : lf;
            } catch (Throwable t) {
                // Doh.... whatever; most likely SecurityException
                sLF = lf = "\n";
            }
        }
        return lf;
    }

    public static void appendLF(StringBuilder sb) {
        sb.append(getLF());
    }

    public static String concatEntries(Collection<?> coll, String sep, String lastSep) {
        if (lastSep == null) {
            lastSep = sep;
        }
        int len = coll.size();
        StringBuilder sb = new StringBuilder(16 + (len << 3));
        Iterator<?> it = coll.iterator();
        int i = 0;
        while (it.hasNext()) {
            if (i == 0) {
                ;
            } else if (i == (len - 1)) {
                sb.append(lastSep);
            } else {
                sb.append(sep);
            }
            ++i;
            sb.append(it.next());
        }
        return sb.toString();
    }

    /**
     * Method that will check character array passed, and remove all
     * "extra" spaces (leading and trailing space), and normalize
     * other white space (more than one consequtive space character
     * replaced with a single space).
     *<p>
     * NOTE: we only remove explicit space characters (char code 0x0020);
     * the reason being that other white space must have come from
     * non-normalizable sources, ie. via entity expansion, and is thus
     * not to be normalized
     *
     * @param buf Buffer that contains the String to check
     * @param origStart Offset of the first character of the text to check
     *   in the buffer
     * @param origEnd Offset of the character following the last character
     *   of the text (as per usual Java API convention)
     *
     * @return Normalized String, if any white space was removed or
     *   normalized; null if no changes were necessary.
     */
    public static String normalizeSpaces(char[] buf, int origStart, int origEnd)
    {
        --origEnd;

        int start = origStart;
        int end = origEnd;

        // First let's trim start...
        while (start <= end && buf[start] == CHAR_SPACE) {
            ++start;
        }
        // Was it all empty?
        if (start > end) {
            return "";
        }

        /* Nope, need to trim from the end then (note: it's known that char
         * at index 'start' is not a space, at this point)
         */
        while (end > start && buf[end] == CHAR_SPACE) {
            --end;
        }

        /* Ok, may have changes or not: now need to normalize
         * intermediate duplicate spaces. We also now that the
         * first and last characters can not be spaces.
         */
        int i = start+1;

        while (i < end) {
            if (buf[i] == CHAR_SPACE) {
                if (buf[i+1] == CHAR_SPACE) {
                    break;
                }
                // Nah; no hole for these 2 chars!
                i += 2;
            } else {
                ++i;
            }
        }

        // Hit the end?
        if (i >= end) {
            // Any changes?
            if (start == origStart && end == origEnd) {
                return null; // none
            }
            return new String(buf, start, (end-start)+1);
        }

        /* Nope, got a hole, need to constuct the damn thing. Shouldn't
         * happen too often... so let's just use StringBuilder()
         */
        StringBuilder sb = new StringBuilder(end-start); // can't be longer
        sb.append(buf, start, i-start); // won't add the starting space

        while (i <= end) {
            char c = buf[i++];
            if (c == CHAR_SPACE) {
                sb.append(CHAR_SPACE);
                // Need to skip dups
                while (true) {
                    c = buf[i++];
                    if (c != CHAR_SPACE) {
                        sb.append(c);
                        break;
                    }
                }
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    public static boolean isAllWhitespace(String str)
    {
        for (int i = 0, len = str.length(); i < len; ++i) {
            if (str.charAt(i) > CHAR_SPACE) {
                return false;
            }
        }
        return true;
    }

    public static boolean isAllWhitespace(char[] ch, int start, int len)
    {
        len += start;
        for (; start < len; ++start) {
            if (ch[start] > CHAR_SPACE) {
                return false;
            }
        }
        return true;
    }

    /**
     * Internal constant used to denote END-OF-STRING
     */
    private final static int EOS = 0x10000;

    /**
     * Method that implements a loose String compairon for encoding
     * Strings. It will work like {@link String#equalsIgnoreCase},
     * except that it will also ignore all hyphen, underscore and
     * space characters.
     */
    public static boolean equalEncodings(String str1, String str2)
    {
        final int len1 = str1.length();
        final int len2 = str2.length();

        // Need to loop completely over both Strings
        for (int i1 = 0, i2 = 0; i1 < len1 || i2 < len2; ) {
            int c1 = (i1 >= len1) ?  EOS : str1.charAt(i1++);
            int c2 = (i2 >= len2) ?  EOS : str2.charAt(i2++);

            // Can first do a quick comparison (usually they are equal)
            if (c1 == c2) {
                continue;
            }

            // if not equal, maybe there are WS/hyphen/underscores to skip
            while (c1 <= INT_SPACE || c1 == '_' || c1 == '-') {
                c1 = (i1 >= len1) ?  EOS : str1.charAt(i1++);
            }
            while (c2 <= INT_SPACE || c2 == '_' || c2 == '-') {
                c2 = (i2 >= len2) ?  EOS : str2.charAt(i2++);
            }
            // Ok, how about case differences, then?
            if (c1 != c2) {
                // If one is EOF, can't match (one is substring of the other)
                if (c1 == EOS || c2 == EOS) {
                    return false;
                }
                if (c1 < 127) { // ascii is easy...
                    if (c1 <= 'Z' && c1 >= 'A') {
                        c1 = c1 + ('a' - 'A');
                    }
                } else {
                    c1 = Character.toLowerCase((char)c1);
                }
                if (c2 < 127) { // ascii is easy...
                    if (c2 <= 'Z' && c2 >= 'A') {
                        c2 = c2 + ('a' - 'A');
                    }
                } else {
                    c2 = Character.toLowerCase((char)c2);
                }
                if (c1 != c2) {
                    return false;
                }
            }
        }

        // If we got this far, we are ok as long as we got through it all
        return true; 
    }

    public static boolean encodingStartsWith(String enc, String prefix)
    {
        int len1 = enc.length();
        int len2 = prefix.length();

        int i1 = 0, i2 = 0;

        // Need to loop completely over both Strings
        while (i1 < len1 || i2 < len2) {
            int c1 = (i1 >= len1) ?  EOS : enc.charAt(i1++);
            int c2 = (i2 >= len2) ?  EOS : prefix.charAt(i2++);

            // Can first do a quick comparison (usually they are equal)
            if (c1 == c2) {
                continue;
            }

            // if not equal, maybe there are WS/hyphen/underscores to skip
            while (c1 <= CHAR_SPACE || c1 == '_' || c1 == '-') {
                c1 = (i1 >= len1) ?  EOS : enc.charAt(i1++);
            }
            while (c2 <= CHAR_SPACE || c2 == '_' || c2 == '-') {
                c2 = (i2 >= len2) ?  EOS : prefix.charAt(i2++);
            }
            // Ok, how about case differences, then?
            if (c1 != c2) {
                if (c2 == EOS) { // Prefix done, good!
                    return true;
                }
                if (c1 == EOS) { // Encoding done, not good
                    return false;
                }
                if (Character.toLowerCase((char)c1) != Character.toLowerCase((char)c2)) {
                    return false;
                }
            }
        }

        // Ok, prefix was exactly the same as encoding... that's fine
        return true; 
    }

    /**
     * Method that will remove all non-alphanumeric characters, and optionally
     * upper-case included letters, from the given String.
     */
    public static String trimEncoding(String str, boolean upperCase)
    {
        int i = 0;
        int len = str.length();

        // Let's first check if String is fine as is:
        for (; i < len; ++i) {
            char c = str.charAt(i);
            if (c <= CHAR_SPACE || !Character.isLetterOrDigit(c)) {
                break;
            }
        }

        if (i == len) {
            return str;
        }

        // Nope: have to trim it
        StringBuilder sb = new StringBuilder();
        if (i > 0) {
            sb.append(str.substring(0, i));
        }
        for (; i < len; ++i) {
            char c = str.charAt(i);
            if (c > CHAR_SPACE && Character.isLetterOrDigit(c)) {
                if (upperCase) {
                    c = Character.toUpperCase(c);
                }
                sb.append(c);
            }
        }

        return sb.toString();
    }

    public static boolean matches(String str, char[] cbuf, int offset, int len)
    {
        if (str.length() != len) {
            return false;
        }
        for (int i = 0; i < len; ++i) {
            if (str.charAt(i) != cbuf[offset+i]) {
                return false;
            }
        }
        return true;
    }

    /**
     *<p>
     * Note that it is assumed that any "weird" white space
     * (xml 1.1 LSEP and NEL) have been replaced by canonical
     * alternatives (linefeed for element content, regular space
     * for attributes)
     */
    @SuppressWarnings("cast")
	public final static boolean isSpace(char c)
    {
        return ((int) c) <= 0x0020;
    }
}
