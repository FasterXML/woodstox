package com.ctc.wstx.sr;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BasicStreamReader#isSpecialTextChar}, the branch-free
 * check for chars that need special handling in text content.
 */
class TestIsSpecialTextChar extends wstxtest.BaseJUnit4Test
{
    @Test
    void testAllChars()
    {
        for (int i = 0; i <= 0xFFFF; ++i) {
            char c = (char) i;
            // Control chars other than tab (incl. linefeeds), and '&', '<' and '>'
            boolean expected = (c < 0x20 && c != '\t') || c == '&' || c == '<' || c == '>';
            assertEquals("Char 0x"+Integer.toHexString(i), expected,
                    BasicStreamReader.isSpecialTextChar(c));
        }
    }

    // Shift distance of a long only uses its lowest 6 bits: chars from 64 up
    // must not be mistaken for the special char with the same low 6 bits
    @Test
    void testNoAliasingAbove63()
    {
        assertFalse(BasicStreamReader.isSpecialTextChar('@')); // NUL
        assertFalse(BasicStreamReader.isSpecialTextChar('J')); // LF
        assertFalse(BasicStreamReader.isSpecialTextChar('f')); // '&'
        assertFalse(BasicStreamReader.isSpecialTextChar('|')); // '<'
        assertFalse(BasicStreamReader.isSpecialTextChar('~')); // '>'
        assertFalse(BasicStreamReader.isSpecialTextChar('ļ')); // '<'
        assertFalse(BasicStreamReader.isSpecialTextChar('￀')); // NUL
    }
}
