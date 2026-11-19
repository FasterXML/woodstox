package wstxtest.stream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.xml.stream.*;

import org.codehaus.stax2.io.Stax2ByteArraySource;
import org.junit.jupiter.api.Test;

/**
 * Verifies that malformed input in encodings decoded by the JDK (as
 * opposed to Woodstox's own UTF-8/Latin1/ASCII/UTF-32 readers) is reported
 * as an error (XML 1.0 #4.3.3) instead of being replaced with U+FFFD.
 * The JDK UTF-16 decoder replaces an unpaired high surrogate together with
 * the code unit that follows it, so replacement used to silently drop a
 * markup character: below, the {@code '<'} that opens the comment.
 */
public class TestMalformedJdkDecodedInput
    extends BaseStreamTest
{
    // Unpaired high surrogate right before "<!--", which it used to swallow
    private final static String DOC_BEFORE = "<root>";
    private final static String DOC_AFTER = "<!--<x/>--></root>";

    @Test
    public void testUnpairedHighSurrogateUtf16BE() throws Exception
    {
        byte[] doc = utf16(true, true, DOC_BEFORE, '\uD800', DOC_AFTER);
        streamThroughFailing(getReader(doc), "unpaired high surrogate (UTF-16BE)");
    }

    @Test
    public void testUnpairedHighSurrogateUtf16LE() throws Exception
    {
        byte[] doc = utf16(true, false, DOC_BEFORE, '\uD800', DOC_AFTER);
        streamThroughFailing(getReader(doc), "unpaired high surrogate (UTF-16LE)");
    }

    @Test
    public void testUnpairedHighSurrogateAppEncoding() throws Exception
    {
        // Encoding passed by the application: different reader construction path
        byte[] doc = utf16(false, true, DOC_BEFORE, '\uD800', DOC_AFTER);
        try {
            // may already fail here, as the start of input is decoded eagerly
            XMLStreamReader sr = getInputFactory()
                    .createXMLStreamReader(new ByteArrayInputStream(doc), "UTF-16BE");
            streamThrough(sr);
            fail("Expected an exception for unpaired high surrogate (application-specified UTF-16BE)");
        } catch (XMLStreamException expected) { }
    }

    @Test
    public void testUnpairedLowSurrogate() throws Exception
    {
        byte[] doc = utf16(true, true, DOC_BEFORE, '\uDC00', "</root>");
        streamThroughFailing(getReader(doc), "unpaired low surrogate (UTF-16BE)");
    }

    @Test
    public void testValidSurrogatePair() throws Exception
    {
        byte[] doc = "﻿<root>𝄞<!--c--></root>".getBytes("UTF-16BE");
        XMLStreamReader sr = getReader(doc);
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());
        assertEquals("𝄞", getAndVerifyText(sr));
        assertTokenType(COMMENT, sr.next());
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();
    }

    @Test
    public void testUnmappableStillReplaced() throws Exception
    {
        // 0x81 is well-formed but has no mapping in windows-1252: still
        // replaced, as before (only malformed input is reported)
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("<?xml version='1.0' encoding='windows-1252'?><root>a".getBytes("US-ASCII"));
        out.write(0x81);
        out.write("b</root>".getBytes("US-ASCII"));
        XMLStreamReader sr = getReader(out.toByteArray());
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());
        assertEquals("a�b", getAndVerifyText(sr));
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();
    }

    /*
    /////////////////////////////////////////
    // Non-test methods
    /////////////////////////////////////////
     */

    private static byte[] utf16(boolean bom, boolean bigEndian,
            String before, char unit, String after) throws Exception
    {
        String enc = bigEndian ? "UTF-16BE" : "UTF-16LE";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (bom) {
            out.write(String.valueOf('﻿').getBytes(enc));
        }
        out.write(before.getBytes(enc));
        // written by hand: String.getBytes() would replace a lone surrogate
        if (bigEndian) {
            out.write(unit >> 8);
            out.write(unit & 0xFF);
        } else {
            out.write(unit & 0xFF);
            out.write(unit >> 8);
        }
        out.write(after.getBytes(enc));
        return out.toByteArray();
    }

    private XMLStreamReader getReader(byte[] b) throws Exception
    {
        return getInputFactory().createXMLStreamReader(
                new Stax2ByteArraySource(b, 0, b.length));
    }
}
