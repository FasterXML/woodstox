package com.ctc.wstx.io;

import junit.framework.TestCase;

/**
 * Regression test: CharsetNames.normalize() had a duplicate check for
 * "UnicodeAscii" where the first returned CS_ISO_LATIN1 (wrong) and
 * the second (dead code) returned CS_US_ASCII. The first should have
 * been checking "UnicodeLatin1" instead.
 */
public class TestCharsetNames extends TestCase
{
    public void testCsUnicodeAsciiNormalization()
    {
        // "csUnicodeAscii" should normalize to US-ASCII
        assertEquals(CharsetNames.CS_US_ASCII,
                CharsetNames.normalize("csUnicodeAscii"));
    }

    public void testCsUnicodeLatin1Normalization()
    {
        // "csUnicodeLatin1" should normalize to ISO-8859-1
        assertEquals(CharsetNames.CS_ISO_LATIN1,
                CharsetNames.normalize("csUnicodeLatin1"));
    }

    public void testCsUnicodeNormalization()
    {
        // "csUnicode" should normalize to UTF-16
        assertEquals(CharsetNames.CS_UTF16,
                CharsetNames.normalize("csUnicode"));
    }
}
