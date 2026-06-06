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
