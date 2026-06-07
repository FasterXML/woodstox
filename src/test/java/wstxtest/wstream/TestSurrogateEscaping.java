package wstxtest.wstream;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.stax2.XMLStreamReader2;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a supplementary character (one requiring a surrogate pair)
 * is escaped as a single character entity, not two, when the target encoding
 * can not represent it natively and output goes through a {@code Writer}
 * (i.e. {@code BufferingXmlWriter}).
 */
public class TestSurrogateEscaping
    extends BaseWriterTest
{
    // U+1D11E MUSICAL SYMBOL G CLEF, surrogate pair 0xD834 0xDD1E
    private final static String ASTRAL = "𝄞";

    @Test
    public void testSupplementaryCharInTextAndAttr()
        throws Exception
    {
        for (String enc : new String[] { "US-ASCII", "ISO-8859-1" }) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Writer w = new OutputStreamWriter(bos, enc);
            XMLStreamWriter sw = getOutputFactory().createXMLStreamWriter(w);
            sw.writeStartElement("root");
            sw.writeAttribute("v", "a" + ASTRAL + "b");
            sw.writeCharacters("c" + ASTRAL + "d");
            sw.writeEndElement();
            sw.close();
            w.flush();

            String xml = bos.toString(enc);
            // Single, valid entity; never two surrogate-half entities
            if (xml.indexOf("&#x1d11e;") < 0) {
                fail("Expected '&#x1d11e;' in output for encoding "+enc+", got: "+xml);
            }
            if (xml.indexOf("&#xd834;") >= 0 || xml.indexOf("&#xdd1e;") >= 0) {
                fail("Output for encoding "+enc+" emitted surrogate-half entities: "+xml);
            }
            verifyRoundtrip(xml, "a" + ASTRAL + "b", "c" + ASTRAL + "d");
        }
    }

    @Test
    public void testSupplementaryCharSplitAcrossCalls()
        throws Exception
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Writer w = new OutputStreamWriter(bos, "US-ASCII");
        XMLStreamWriter sw = getOutputFactory().createXMLStreamWriter(w);
        sw.writeStartElement("root");
        // Surrogate halves delivered in separate writeCharacters() calls
        sw.writeCharacters("\uD834");
        sw.writeCharacters("\uDD1E");
        sw.writeEndElement();
        sw.close();
        w.flush();

        String xml = bos.toString("US-ASCII");
        if (xml.indexOf("&#x1d11e;") < 0) {
            fail("Expected combined '&#x1d11e;' for split surrogate, got: "+xml);
        }
        verifyRoundtrip(xml, null, ASTRAL);
    }

    @Test
    public void testUnpairedSurrogateRejected()
        throws Exception
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Writer w = new OutputStreamWriter(bos, "US-ASCII");
        XMLStreamWriter sw = getOutputFactory().createXMLStreamWriter(w);
        sw.writeStartElement("root");
        try {
            sw.writeCharacters("\uDD1E"); // lone low surrogate
            sw.writeEndElement();
            sw.close();
            fail("Expected failure on unpaired surrogate");
        } catch (XMLStreamException expected) {
        }
    }

    /**
     * A high surrogate held for pairing at the end of a text segment must not
     * leak into a following, unrelated write. Previously the held half was
     * silently combined with the next escaped content (e.g. a different
     * element's attribute), moving the character and corrupting output; it now
     * must be rejected, matching the byte-backed writers.
     */
    @Test
    public void testPendingSurrogateDoesNotLeakIntoAttribute()
        throws Exception
    {
        XMLStreamWriter sw = startAsciiDoc();
        try {
            sw.writeCharacters("a\uD834"); // trailing high surrogate held
            sw.writeStartElement("child");
            sw.writeAttribute("k", "\uDD1Ez"); // low half of an unrelated value
            sw.writeEndElement();
            sw.writeEndElement();
            sw.close();
            fail("Expected failure: pending surrogate leaked into following attribute");
        } catch (XMLStreamException expected) {
        }
    }

    /**
     * A high surrogate left pending by a text write must be rejected when the
     * next operation ends the element rather than continuing character data.
     */
    @Test
    public void testPendingSurrogateRejectedAtEndElement()
        throws Exception
    {
        XMLStreamWriter sw = startAsciiDoc();
        try {
            sw.writeCharacters("a\uD834");
            sw.writeEndElement();
            sw.close();
            fail("Expected failure: pending surrogate at end of element");
        } catch (XMLStreamException expected) {
        }
    }

    /**
     * An attribute value is atomic, so a high surrogate as its final char can
     * never be completed and must be rejected immediately (not held).
     */
    @Test
    public void testTrailingHighSurrogateInAttributeRejected()
        throws Exception
    {
        XMLStreamWriter sw = startAsciiDoc();
        try {
            sw.writeAttribute("k", "x\uD834");
            sw.writeEndElement();
            sw.close();
            fail("Expected failure: trailing high surrogate in attribute value");
        } catch (XMLStreamException expected) {
        }
    }

    private XMLStreamWriter startAsciiDoc()
        throws Exception
    {
        Writer w = new OutputStreamWriter(new ByteArrayOutputStream(), "US-ASCII");
        XMLStreamWriter sw = getOutputFactory().createXMLStreamWriter(w);
        sw.writeStartElement("root");
        return sw;
    }

    private void verifyRoundtrip(String xml, String expAttr, String expText)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = constructNsStreamReader(xml, true);
        assertTokenType(START_ELEMENT, sr.next());
        if (expAttr != null) {
            assertEquals(expAttr, sr.getAttributeValue(0));
        }
        assertTokenType(CHARACTERS, sr.next());
        assertEquals(expText, getAndVerifyText(sr));
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();
    }
}
