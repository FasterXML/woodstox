package com.ctc.wstx.io;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;

import junit.framework.TestCase;

/**
 * Unit tests for {@link CharsetNames}. Originally a regression test for a
 * duplicate-check bug around "UnicodeAscii"; expanded to broadly exercise
 * the {@link CharsetNames#normalize(String)} branches as well as
 * {@link CharsetNames#findEncodingFor(Writer)}.
 */
public class TestCharsetNames extends TestCase
{
    // ---------- Original regression tests ----------

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

    // ---------- normalize(): degenerate input ----------

    public void testNullOrShortReturnedAsIs()
    {
        assertNull(CharsetNames.normalize(null));
        assertEquals("", CharsetNames.normalize(""));
        assertEquals("ab", CharsetNames.normalize("ab"));
    }

    public void testUnknownReturnedAsIs()
    {
        assertEquals("not-a-real-encoding",
                CharsetNames.normalize("not-a-real-encoding"));
    }

    // ---------- normalize(): UTF/Unicode family ----------

    public void testUtf8Variants()
    {
        assertEquals(CharsetNames.CS_UTF8, CharsetNames.normalize("UTF-8"));
        // hyphen vs no-hyphen, case insensitivity (loose comparison)
        assertEquals(CharsetNames.CS_UTF8, CharsetNames.normalize("utf-8"));
        assertEquals(CharsetNames.CS_UTF8, CharsetNames.normalize("UTF8"));
        assertEquals(CharsetNames.CS_UTF8, CharsetNames.normalize("utf_8"));
    }

    public void testUtf16Variants()
    {
        assertEquals(CharsetNames.CS_UTF16, CharsetNames.normalize("UTF-16"));
        assertEquals(CharsetNames.CS_UTF16BE, CharsetNames.normalize("UTF-16BE"));
        assertEquals(CharsetNames.CS_UTF16LE, CharsetNames.normalize("UTF-16LE"));
        // "UTF" alone is treated as UTF-16
        assertEquals(CharsetNames.CS_UTF16, CharsetNames.normalize("UTF"));
    }

    public void testUtf32Variants()
    {
        assertEquals(CharsetNames.CS_UTF32, CharsetNames.normalize("UTF-32"));
        assertEquals(CharsetNames.CS_UTF32BE, CharsetNames.normalize("UTF-32BE"));
        assertEquals(CharsetNames.CS_UTF32LE, CharsetNames.normalize("UTF-32LE"));
    }

    public void testUcsVariants()
    {
        // UCS-2 -> UTF-16, UCS-4 -> UTF-32 (and via ISO-10646 prefixed form)
        assertEquals(CharsetNames.CS_UTF16, CharsetNames.normalize("UCS-2"));
        assertEquals(CharsetNames.CS_UTF32, CharsetNames.normalize("UCS-4"));
        assertEquals(CharsetNames.CS_UTF16,
                CharsetNames.normalize("ISO-10646-UCS-2"));
        assertEquals(CharsetNames.CS_UTF32,
                CharsetNames.normalize("ISO-10646-UCS-4"));
    }

    public void testIso10646SubtypeAliases()
    {
        assertEquals(CharsetNames.CS_US_ASCII,
                CharsetNames.normalize("ISO-10646-UCS-Basic"));
        assertEquals(CharsetNames.CS_ISO_LATIN1,
                CharsetNames.normalize("ISO-10646-Unicode-Latin1"));
        assertEquals(CharsetNames.CS_US_ASCII,
                CharsetNames.normalize("ISO-10646-UTF-1"));
        assertEquals(CharsetNames.CS_US_ASCII,
                CharsetNames.normalize("ISO-10646-J-1"));
        assertEquals(CharsetNames.CS_US_ASCII,
                CharsetNames.normalize("ISO-10646-US-ASCII"));
    }

    // ---------- normalize(): ASCII / Latin1 / JIS ----------

    public void testAsciiVariants()
    {
        assertEquals(CharsetNames.CS_US_ASCII, CharsetNames.normalize("ASCII"));
        assertEquals(CharsetNames.CS_US_ASCII, CharsetNames.normalize("ascii"));
        assertEquals(CharsetNames.CS_US_ASCII, CharsetNames.normalize("US-ASCII"));
    }

    public void testIsoLatin1Variants()
    {
        assertEquals(CharsetNames.CS_ISO_LATIN1,
                CharsetNames.normalize("ISO-8859-1"));
        assertEquals(CharsetNames.CS_ISO_LATIN1,
                CharsetNames.normalize("ISO-Latin1"));
        // loose-comparison friendly variant
        assertEquals(CharsetNames.CS_ISO_LATIN1,
                CharsetNames.normalize("iso_8859_1"));
    }

    public void testJisVariants()
    {
        assertEquals(CharsetNames.CS_SHIFT_JIS,
                CharsetNames.normalize("Shift_JIS"));
        assertEquals(CharsetNames.CS_SHIFT_JIS,
                CharsetNames.normalize("JIS_Encoding"));
    }

    // ---------- normalize(): EBCDIC / IBM / Cp ----------

    public void testEbcdicCpAliases()
    {
        assertEquals("IBM037", CharsetNames.normalize("EBCDIC-CP-US"));
        assertEquals("IBM037", CharsetNames.normalize("EBCDIC-CP-CA"));
        assertEquals("IBM037", CharsetNames.normalize("EBCDIC-CP-WT"));
        assertEquals("IBM037", CharsetNames.normalize("EBCDIC-CP-NL"));
        assertEquals("IBM277", CharsetNames.normalize("EBCDIC-CP-DK"));
        assertEquals("IBM277", CharsetNames.normalize("EBCDIC-CP-NO"));
        assertEquals("IBM278", CharsetNames.normalize("EBCDIC-CP-FI"));
        assertEquals("IBM278", CharsetNames.normalize("EBCDIC-CP-SE"));
        assertEquals("IBM870", CharsetNames.normalize("EBCDIC-CP-ROECE"));
        assertEquals("IBM870", CharsetNames.normalize("EBCDIC-CP-YU"));
        assertEquals("IBM280", CharsetNames.normalize("EBCDIC-CP-IT"));
        assertEquals("IBM284", CharsetNames.normalize("EBCDIC-CP-ES"));
        assertEquals("IBM285", CharsetNames.normalize("EBCDIC-CP-GB"));
        assertEquals("IBM297", CharsetNames.normalize("EBCDIC-CP-FR"));
        assertEquals("IBM420", CharsetNames.normalize("EBCDIC-CP-AR1"));
        assertEquals("IBM918", CharsetNames.normalize("EBCDIC-CP-AR2"));
        assertEquals("IBM424", CharsetNames.normalize("EBCDIC-CP-HE"));
        assertEquals("IBM500", CharsetNames.normalize("EBCDIC-CP-CH"));
        assertEquals("IBM871", CharsetNames.normalize("EBCDIC-CP-IS"));
        // Unknown EBCDIC sub-type defaults to canonical EBCDIC subset
        assertEquals(CharsetNames.CS_EBCDIC_SUBSET,
                CharsetNames.normalize("EBCDIC-CP-XX"));
        // lower-case prefix also accepted
        assertEquals("IBM037", CharsetNames.normalize("ebcdic-cp-us"));
    }

    public void testCpAndIbmAliases()
    {
        // "cpXXX" is treated as EBCDIC alias "IBMXXX"
        assertEquals("IBM037", CharsetNames.normalize("cp037"));
        assertEquals("IBM277", CharsetNames.normalize("Cp277"));
        // "csIBMxx" peels off "cs" prefix
        assertEquals("IBM037", CharsetNames.normalize("csIBM037"));
        // raw "IBMxxx" returned as-is
        assertEquals("IBM500", CharsetNames.normalize("IBM500"));
    }

    // ---------- findEncodingFor(Writer) ----------

    public void testFindEncodingForOutputStreamWriter() throws Exception
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Writer w = new OutputStreamWriter(bos, "UTF-8");
        // Whatever the JDK reports (legacy "UTF8" or canonical "UTF-8") must
        // come back normalized to the canonical form.
        assertEquals(CharsetNames.CS_UTF8, CharsetNames.findEncodingFor(w));
        w.close();

        Writer w2 = new OutputStreamWriter(new ByteArrayOutputStream(), "ISO-8859-1");
        assertEquals(CharsetNames.CS_ISO_LATIN1, CharsetNames.findEncodingFor(w2));
        w2.close();
    }

    public void testFindEncodingForNonOutputStreamWriter()
    {
        // For non-OutputStreamWriter writers (e.g. StringWriter) we have no
        // encoding info, so the method must return null rather than guess.
        assertNull(CharsetNames.findEncodingFor(new StringWriter()));
    }
}
