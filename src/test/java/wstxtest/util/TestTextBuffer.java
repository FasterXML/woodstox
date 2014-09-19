package wstxtest.util;

import junit.framework.TestCase;

import com.ctc.wstx.util.TextBuffer;

/**
 * Simple unit tests for testing {@link TextBuffer}.
 */
public class TestTextBuffer
    extends TestCase
{
    public void testBasic()
    {
        String INPUT = "Whatever input text doesn't really matter but should have some content "
            +"so as not to be too short";
        TextBuffer tb = TextBuffer.createTemporaryBuffer();
        final char[] ch = new char[1];
        for (int i = 0, len = INPUT.length(); i < len; ++i) {
            if ((i & 1) != 0) {
                ch[0] = INPUT.charAt(i);
                tb.append(ch, 0, 1);
            } else {
                tb.append(INPUT.substring(i, i+1));
            }
        }

        assertEquals(INPUT, tb.toString());
        assertEquals(INPUT, tb.contentsAsString());
        assertFalse(tb.endsWith("shor"));
        assertTrue(tb.endsWith("so as not to be too short"));
        assertFalse(tb.isAllWhitespace());

        assertTrue(tb.equalsString(INPUT));

        /*
        tb.clear();

        assertEquals("", tb.toString());
        assertEquals("", tb.contentsAsString());
        */
    }
}

