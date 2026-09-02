package stax2.stream;

import java.io.ByteArrayInputStream;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.codehaus.stax2.XMLInputFactory2;
import org.junit.jupiter.api.Test;

import stax2.BaseStax2Test;

/**
 * Tests for [woodstox-core]: XML 1.1 "restricted characters" of the C1 control
 * range (U+007F&ndash;U+009F, except U+0085 NEL) must be rejected when they
 * appear literally (i.e. not via character references), regardless of whether
 * the document is supplied as an {@code InputStream} or as a {@code Reader}.
 *<p>
 * Before the fix, the check lived only in Woodstox's own byte-decoding Readers,
 * so {@code Reader}-based (or exotic-encoding) input silently accepted these
 * restricted control characters, while the same bytes were rejected when read
 * from an {@code InputStream}. These tests pin the now-consistent behavior;
 * they construct the reader from a {@link java.io.StringReader} (the path that
 * used to bypass the check) and assert rejection.
 */
public class TestXml11RestrictedCharsViaReader extends BaseStax2Test
{
    private final static String XML11_DECL = "<?xml version=\"1.1\"?>";

    // // // Restricted C1 controls: must be rejected when read via a Reader

    @Test
    public void testRestrictedC1InContentViaReader() throws Exception
    {
        // sample across the restricted range: 0x7F (DEL), 0x84, 0x86, 0x90, 0x9F
        for (int c : new int[] { 0x7F, 0x84, 0x86, 0x90, 0x9F }) {
            String doc = XML11_DECL + "<root>A" + ((char) c) + "B</root>";
            assertRejectedViaReader(doc, c);
        }
    }

    @Test
    public void testRestrictedC1InAttributeViaReader() throws Exception
    {
        String doc = XML11_DECL + "<root attr=\"x" + ((char) 0x90) + "y\">z</root>";
        assertRejectedViaReader(doc, 0x90);
    }

    @Test
    public void testRestrictedC1InCDataViaReader() throws Exception
    {
        String doc = XML11_DECL + "<root><![CDATA[A" + ((char) 0x93) + "B]]></root>";
        assertRejectedViaReader(doc, 0x93);
    }

    @Test
    public void testRestrictedC1AcrossBufferBoundaryViaReader() throws Exception
    {
        // Push the restricted char well past the first buffer fill so that the
        // readMore() refill path is exercised, not just the initial readInto().
        StringBuilder sb = new StringBuilder(XML11_DECL).append("<root>");
        for (int i = 0; i < 50000; ++i) {
            sb.append('x');
        }
        sb.append((char) 0x90).append("</root>");
        assertRejectedViaReader(sb.toString(), 0x90);
    }

    // // // Things that must STILL be accepted (no over-rejection)

    @Test
    public void testAllowedCharsViaReaderXml11() throws Exception
    {
        // U+0085 (NEL) and U+00A0 (NBSP) are NOT restricted; plain content too.
        for (int c : new int[] { 0x09, 0x41, 0x85, 0xA0, 0x100 }) {
            String doc = XML11_DECL + "<root>A" + ((char) c) + "B</root>";
            assertAcceptedViaReader(doc);
        }
    }

    @Test
    public void testRestrictedC1AsCharRefAllowedViaReader() throws Exception
    {
        // Restricted chars ARE legal when expressed as character references.
        String doc = XML11_DECL + "<root>A&#x90;B</root>";
        assertAcceptedViaReader(doc);
    }

    @Test
    public void testRestrictedC1AllowedInXml10ViaReader() throws Exception
    {
        // In XML 1.0 the C1 range is ordinary content and must not be rejected.
        String doc = "<?xml version=\"1.0\"?><root>A" + ((char) 0x90) + "B</root>";
        assertAcceptedViaReader(doc);
    }

    // // // Cross-check: InputStream path already behaved this way and is unchanged

    @Test
    public void testRestrictedC1ViaInputStreamStillRejected() throws Exception
    {
        String doc = XML11_DECL + "<root>A" + ((char) 0x90) + "B</root>";
        XMLInputFactory2 f = getInputFactory();
        XMLStreamReader sr = (XMLStreamReader) f.createXMLStreamReader(
                new ByteArrayInputStream(doc.getBytes("UTF-8")));
        try {
            streamThrough(sr);
            fail("Expected XML 1.1 restricted char 0x90 to be rejected for InputStream input");
        } catch (XMLStreamException e) {
            // expected
        }
    }

    // // // Helpers

    private void assertRejectedViaReader(String doc, int expectedChar) throws Exception
    {
        XMLInputFactory2 f = getInputFactory();
        XMLStreamReader sr = constructStreamReader(f, doc);
        try {
            streamThrough(sr);
            fail("Expected XML 1.1 restricted char 0x" + Integer.toHexString(expectedChar)
                    + " to be rejected when read via a Reader");
        } catch (XMLStreamException e) {
            // expected
        }
    }

    private void assertAcceptedViaReader(String doc) throws Exception
    {
        XMLInputFactory2 f = getInputFactory();
        XMLStreamReader sr = constructStreamReader(f, doc);
        // Should consume the whole document without throwing:
        streamThrough(sr);
        sr.close();
    }
}
