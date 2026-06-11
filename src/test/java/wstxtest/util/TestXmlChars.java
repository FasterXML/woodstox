package wstxtest.util;


import com.ctc.wstx.io.WstxInputData;
import org.junit.jupiter.api.Test;

/**
 * Simple unit tests for testing methods in {@link com.ctc.wstx.util.XmlChars}
 * and {@link com.ctc.wstx.io.WstxInputData}
 */
public class TestXmlChars
    extends wstxtest.BaseJUnit4Test
{
    @Test
    public void testXml10Chars()
    {
        // First, 8-bit range:
        assertTrue(WstxInputData.isNameStartChar('F', true, false));
        assertTrue(WstxInputData.isNameChar('F', true, false));
        assertTrue(WstxInputData.isNameStartChar('_', true, false));
        assertTrue(WstxInputData.isNameChar('_', true, false));
        assertTrue(WstxInputData.isNameChar('x', true, false));
        assertFalse(WstxInputData.isNameStartChar('-', true, false));
        assertTrue(WstxInputData.isNameChar('-', true, false));
        assertFalse(WstxInputData.isNameStartChar('.', true, false));
        assertTrue(WstxInputData.isNameChar('.', true, false));

        // Then more exotic chars:

        assertTrue(WstxInputData.isNameStartChar((char) 0x03ce, true, false));
        assertTrue(WstxInputData.isNameChar((char) 0x03ce, true, false));
        assertTrue(WstxInputData.isNameStartChar((char) 0x0e21, true, false));
        assertTrue(WstxInputData.isNameChar((char) 0x0e21, true, false));
        assertTrue(WstxInputData.isNameStartChar((char) 0x3007, true, false));
        assertFalse(WstxInputData.isNameStartChar(' ', true, false));
        /* colon is NOT a start char for this method; although it is
         * in xml specs -- reason has to do with namespace handling
         */
        assertFalse(WstxInputData.isNameStartChar(':', true, false));

        assertFalse(WstxInputData.isNameStartChar((char) 0x3008, true, false));
        assertFalse(WstxInputData.isNameChar((char) 0x3008, true, false));
        assertTrue(WstxInputData.isNameStartChar((char) 0x30ea, true, false));
        assertTrue(WstxInputData.isNameChar((char) 0x30ea, true, false));
    }

    @Test
    public void testXml11NameStartChars()
    {
        // First, 8-bit range:
        assertTrue(WstxInputData.isNameStartChar('F', true, true));
        assertTrue(WstxInputData.isNameChar('F', true, true));
        assertTrue(WstxInputData.isNameStartChar('_', true, true));
        assertTrue(WstxInputData.isNameChar('_', true, true));
        assertTrue(WstxInputData.isNameChar('x', true, true));
        assertFalse(WstxInputData.isNameStartChar('-', true, true));
        assertTrue(WstxInputData.isNameChar('-', true, true));
        assertFalse(WstxInputData.isNameStartChar('.', true, true));
        assertTrue(WstxInputData.isNameChar('.', true, true));

        // Then more exotic chars:

        assertTrue(WstxInputData.isNameStartChar((char) 0x03ce, true, true));
        assertTrue(WstxInputData.isNameChar((char) 0x03ce, true, true));
        assertTrue(WstxInputData.isNameStartChar((char) 0x0e21, true, true));
        assertTrue(WstxInputData.isNameChar((char) 0x0e21, true, true));
        assertTrue(WstxInputData.isNameStartChar((char) 0x3007, true, true));
        assertFalse(WstxInputData.isNameStartChar(' ', true, true));
        /* colon is NOT a start char for this method; although it is
         * in xml specs -- reason has to do with namespace handling
         */
        assertFalse(WstxInputData.isNameStartChar(':', true, true));
        assertFalse(WstxInputData.isNameStartChar((char) 0x3000, true, true));
    }

    // U+00D7 (MULTIPLICATION SIGN) and U+00F7 (DIVISION SIGN) sit in the gaps of
    // the XML 1.1 NameStartChar ranges ([#xC0-#xD6], [#xD8-#xF6], [#xF8-#x2FF])
    // and are not added back by NameChar, so neither is a legal name char.
    @Test
    public void testXml11MultiplicationDivisionNotNameChars()
    {
        // already rejected as name-start chars...
        assertFalse(WstxInputData.isNameStartChar((char) 0xD7, true, true));
        assertFalse(WstxInputData.isNameStartChar((char) 0xF7, true, true));
        // ...and must be rejected as later name chars too
        assertFalse(WstxInputData.isNameChar((char) 0xD7, true, true));
        assertFalse(WstxInputData.isNameChar((char) 0xF7, true, true));

        // neighbours stay legal
        assertTrue(WstxInputData.isNameChar((char) 0xD6, true, true));
        assertTrue(WstxInputData.isNameChar((char) 0xD8, true, true));
        assertTrue(WstxInputData.isNameChar((char) 0xF6, true, true));
        assertTrue(WstxInputData.isNameChar((char) 0xF8, true, true));

        // middle dot is a name char but not a name-start char
        assertTrue(WstxInputData.isNameChar((char) 0xB7, true, true));
        assertFalse(WstxInputData.isNameStartChar((char) 0xB7, true, true));
    }
}
