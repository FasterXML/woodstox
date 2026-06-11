package org.codehaus.stax.test.wstream;

import java.io.*;

import javax.xml.stream.*;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for [woodstox#xxx]: when a supplementary (non-BMP)
 * character has to be emitted as a numeric character reference (because the
 * output encoding can not represent it natively), the {@code Writer}-backed
 * output path ({@code BufferingXmlWriter}) used to write each half of the
 * UTF-16 surrogate pair as its own reference, e.g. {@code &#xd83d;&#xde00;}.
 * Those reference surrogate code points, which are not legal XML characters,
 * so the result is not well-formed and is rejected on re-parse (including by
 * Woodstox's own reader). The character must instead be written as a single
 * reference, here {@code &#x1f600;}.
 *<p>
 * The byte-backed writers (US-ASCII / ISO-8859-1) already handled this; the
 * gap was specific to other (8-bit) encodings, which take the buffering path.
 */
public class TestNonBmpOutput
    extends BaseWriterTest
{
    // U+1F600 GRINNING FACE, built from its code point so the source-file
    // encoding can not corrupt the literal.
    private final String NON_BMP = new String(Character.toChars(0x1F600));

    // windows-1252 is an ordinary 8-bit encoding that routes through the
    // buffering (Writer-backed) output path.
    private final String ENCODING = "windows-1252";

    @Test
    public void testNonBmpInCharacters() throws Exception
    {
        byte[] doc = write(true);
        String serialized = new String(doc, ENCODING);

        assertTrue("Should emit a single combined character reference, got: " + serialized,
                serialized.contains("&#x1f600;"));
        assertFalse("Must not emit lone-surrogate references: " + serialized,
                serialized.toLowerCase().contains("d83d"));

        // ... and the output must be well-formed and round-trip faithfully:
        XMLStreamReader sr = getInputFactory().createXMLStreamReader(
                new ByteArrayInputStream(doc));
        assertTokenType(START_ELEMENT, sr.nextTag());
        assertTokenType(CHARACTERS, sr.next());
        assertEquals(NON_BMP, getAllText(sr));
        sr.close();
    }

    @Test
    public void testNonBmpInAttribute() throws Exception
    {
        byte[] doc = write(false);
        String serialized = new String(doc, ENCODING);

        assertTrue("Should emit a single combined character reference, got: " + serialized,
                serialized.contains("&#x1f600;"));
        assertFalse("Must not emit lone-surrogate references: " + serialized,
                serialized.toLowerCase().contains("d83d"));

        XMLStreamReader sr = getInputFactory().createXMLStreamReader(
                new ByteArrayInputStream(doc));
        assertTokenType(START_ELEMENT, sr.nextTag());
        assertEquals(1, sr.getAttributeCount());
        assertEquals(NON_BMP, sr.getAttributeValue(0));
        sr.close();
    }

    /**
     * A genuinely unpaired surrogate can not be represented and must be
     * reported rather than silently emitted as an illegal reference.
     */
    @Test
    public void testUnpairedSurrogateRejected() throws Exception
    {
        XMLOutputFactory f = getOutputFactory();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter w = f.createXMLStreamWriter(bos, ENCODING);
        w.writeStartElement("root");
        try {
            w.writeCharacters("x\uD83Dy"); // high surrogate not followed by a low one
            w.writeEndElement();
            w.writeEndDocument();
            w.close();
            fail("Expected an exception for an unpaired surrogate, got: "
                    + new String(bos.toByteArray(), ENCODING));
        } catch (XMLStreamException expected) {
            // ok
        } catch (RuntimeException expected) {
            // some StAX impls wrap the IOException; ok as long as it is reported
        }
    }

    private byte[] write(boolean asText) throws Exception
    {
        XMLOutputFactory f = getOutputFactory();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter w = f.createXMLStreamWriter(bos, ENCODING);
        w.writeStartDocument(ENCODING, "1.0");
        w.writeStartElement("root");
        if (asText) {
            w.writeCharacters(NON_BMP);
        } else {
            w.writeAttribute("attr", NON_BMP);
        }
        w.writeEndElement();
        w.writeEndDocument();
        w.close();
        return bos.toByteArray();
    }
}
