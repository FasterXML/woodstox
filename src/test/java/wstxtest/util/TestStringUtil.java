package wstxtest.util;

import java.util.*;

import com.ctc.wstx.util.StringUtil;

/**
 * Simple unit tests for testing methods of {@link StringUtil} utility
 * class.
 */
public class TestStringUtil
    extends wstxtest.BaseWstxTest
{
    public void testConcatEntries()
    {
        List<String> l = new ArrayList<String>();
        l.add("first");
        l.add("second");
        l.add("third");
        assertEquals("first, second and third",
                     StringUtil.concatEntries(l, ", ", " and "));

        l = new ArrayList<String>();
        l.add("the only");
        assertEquals("the only",
                     StringUtil.concatEntries(l, ", ", " and "));
    }

    public void testIsAllWhitespace()
    {
        assertTrue(StringUtil.isAllWhitespace("  \r   \r\n    \t"));
        assertTrue(StringUtil.isAllWhitespace(" "));
        assertTrue(StringUtil.isAllWhitespace(" ".toCharArray(), 0, 1));
        assertTrue(StringUtil.isAllWhitespace("\r\n\t"));
        assertTrue(StringUtil.isAllWhitespace("\r\n\t".toCharArray(), 0, 3));
        assertTrue(StringUtil.isAllWhitespace("x \t".toCharArray(), 1, 2));
        assertTrue(StringUtil.isAllWhitespace(""));
        assertTrue(StringUtil.isAllWhitespace(new char[0], 0, 0));

        assertFalse(StringUtil.isAllWhitespace("x"));
        assertFalse(StringUtil.isAllWhitespace("                      !"));
    }

    public void testNormalizeSpaces()
    {
        String str = " my   my";
        assertEquals("my my", StringUtil.normalizeSpaces(str.toCharArray(), 0,
                                                         str.length()));

        str = "foo  bar";
        assertEquals("foo bar", StringUtil.normalizeSpaces(str.toCharArray(), 0,
                                                           str.length()));

        str = "my_my";
        assertFalse("my my".equals(StringUtil.normalizeSpaces(str.toCharArray(),
                                                              0, str.length())));

        str = "Xoh no  Z!";
        assertEquals("oh no",
                     StringUtil.normalizeSpaces(str.toCharArray(), 1,
                                                str.length() - 3));


        /* Also, how about other white-space; not to be normalized fully,
         * so in this case should get null (no normalization done)
         */
        str = "some \t text";
        String result = StringUtil.normalizeSpaces(str.toCharArray(), 0, str.length());        
        if (result != null) {
            fail("Expected <null>, not '"+quotedPrintable(result)+"' when normalizing '"+quotedPrintable(str)+"'");
        }
    }

    public void testEqualEncodings()
    {
        assertTrue(StringUtil.equalEncodings("utf-8", "utf-8"));
        assertTrue(StringUtil.equalEncodings("UTF-8", "utf-8"));
        assertTrue(StringUtil.equalEncodings("UTF-8", "utf8"));
        assertTrue(StringUtil.equalEncodings("UTF8", "utf_8"));
        assertTrue(StringUtil.equalEncodings("US_ASCII", "us-ascii"));
        assertTrue(StringUtil.equalEncodings("utf 8", "Utf-8"));

        assertFalse(StringUtil.equalEncodings("utf-8", "utf-16"));
        assertFalse(StringUtil.equalEncodings("isolatin", "iso-8859-1"));
        assertFalse(StringUtil.equalEncodings("utf8", "utf"));
    }

    public void testMatches()
    {
        String STR = "fooBar!";
        String STR2 = "foobar_";
        assertTrue(StringUtil.matches(STR, STR.toCharArray(), 0, STR.length()));
        assertFalse(StringUtil.matches(STR, STR.toCharArray(), 0, STR.length()-1));
        assertFalse(StringUtil.matches(STR, STR2.toCharArray(), 0, STR2.length()));
    }
}
