package org.codehaus.stax.test.stream;

import java.io.*;

import javax.xml.stream.*;

/**
 * Unit test suite that tests that the stream is really fully streaming:
 * that is, it doesn't need to fill buffers completely before being
 * able to return events for things for which it has already read
 * text. Tests were added after reports that some implementations did
 * in fact have problems with such buffering, and as a result using
 * such readers on network (http, tcp) streams wasn't working as well
 * as it should.
 *<p>
 * Note: should we test Ascii or ISO-Latin, or only UTF-8 (since that's
 * the only encoding XML parsers HAVE to understand)? Most parsers handle
 * them all. Also; is sub-optimal behaviour (blocking too early) really
 * a bug, or just sub-standard implementation?
 */
public class TestStreaming
    extends BaseStreamTest
{
    public void testAscii()
        throws XMLStreamException, UnsupportedEncodingException
    {
        testWith("US-ASCII");
    }

    public void testISOLatin()
        throws XMLStreamException, UnsupportedEncodingException
    {
        testWith("ISO-8859-1");
    }

    public void testUTF8()
        throws XMLStreamException, UnsupportedEncodingException
    {
        testWith("UTF-8");
    }

    /*
    ////////////////////////////////////////
    // Private methods, tests
    ////////////////////////////////////////
     */

    private void testWith(String enc)
        throws XMLStreamException, UnsupportedEncodingException
    {
        BlockingStream bs = getStream(enc);
        XMLStreamReader sr = getReader(bs);
        assertTokenType(START_ELEMENT, sr.next());
        if (bs.hasBlocked()) {
            fail("Stream reader causes blocking before returning START_ELEMENT event that should be parsed before blocking");
        }
    }

    /*
    ////////////////////////////////////////
    // Private methods, other
    ////////////////////////////////////////
     */

    private BlockingStream getStream(String enc)
        throws XMLStreamException, UnsupportedEncodingException
    {
        String contents = "<?xml version='1.0' encoding='"+enc+"'?><root>Some test</root><!-- comment -->";
        byte[] data = contents.getBytes(enc);
        return new BlockingStream(new ByteArrayInputStream(data));
    }

    private XMLStreamReader getReader(BlockingStream in)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setValidating(f, false);
        return f.createXMLStreamReader((InputStream) in);
    }
}
