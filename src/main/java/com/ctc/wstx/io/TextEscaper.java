package com.ctc.wstx.io;

import java.io.*;

public final class TextEscaper
{
    private TextEscaper() { }

    /*
    /////////////////////////////////////////////////////////////
    // Static utility methods, for non-state-aware escaping
    /////////////////////////////////////////////////////////////
     */

    public static void writeEscapedAttrValue(Writer w, String value)
        throws IOException
    {
        int i = 0;
        int len = value.length();
        do {
            int start = i;
            char c = '\u0000';

            for (; i < len; ++i) {
                c = value.charAt(i);
                if (c == '<' || c == '&' || c == '"'
                    || c == '\t' || c == '\n' || c == '\r') {
                    break;
                }
            }
            int outLen = i - start;
            if (outLen > 0) {
                w.write(value, start, outLen);
            }
            if (i < len) {
                if (c == '<') {
                    w.write("&lt;");
                } else if (c == '&') {
                    w.write("&amp;");
                } else if (c == '"') {
                    w.write("&quot;");
                } else if (c == '\t') {
                    // Whitespace chars must be escaped, or attribute-value
                    // normalization (XML 1.0 #3.3.3) collapses them to spaces
                    w.write("&#x9;");
                } else if (c == '\n') {
                    w.write("&#xA;");
                } else if (c == '\r') {
                    w.write("&#xD;");
                }
            }
        } while (++i < len);
    }

    /**
     * Quoting method used when outputting content that will be part of
     * DTD (internal/external subset). Additional quoting is needed for
     * percentage char, which signals parameter entities.
     */
    public static void outputDTDText(Writer w, char[] ch, int offset, int len)
        throws IOException
    {
        int i = offset;
        len += offset;
        do {
            int start = i;
            char c = '\u0000';

            for (; i < len; ++i) {
                c = ch[i];
                if (c == '&' || c == '%' || c == '"') {
                    break;
                }
            }
            int outLen = i - start;
            if (outLen > 0) {
                w.write(ch, start, outLen);
            }
            if (i < len) {
                if (c == '&') {
                    /* Only need to quote to prevent it from being accidentally
                     * taken as part of char entity...
                     */
                    w.write("&amp;");
                } else if (c == '%') {
                    // Need to quote, to prevent use as Param Entity marker
                    w.write("&#37;");
                } else if (c == '"') {
                    // Need to quote assuming it encloses entity value
                    w.write("&#34;");
                }
            }
        } while (++i < len);
    }
}

