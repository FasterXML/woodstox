package wstxtest.util;

import junit.framework.TestCase;

import com.ctc.wstx.io.WstxInputData;

/**
 * Simple unit tests for testing methods in {@link com.ctc.wstx.util.XmlChars}
 * and {@link com.ctc.wstx.io.WstxInputData}
 */
public class TestXmlChars
    extends TestCase
{
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
}
