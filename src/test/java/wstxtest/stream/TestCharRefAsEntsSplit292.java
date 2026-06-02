package wstxtest.stream;

import java.io.StringWriter;

import javax.xml.stream.XMLInputFactory;

import org.junit.jupiter.api.Test;
import org.codehaus.stax2.XMLStreamReader2;

import com.ctc.wstx.api.WstxInputProperties;

/**
 * Test for <a href="https://github.com/FasterXML/woodstox/issues/292">#292</a>:
 * with {@code doTreatCharRefsAsEnts} enabled, character references (and pre-defined
 * entities) that straddle the internal input buffer boundary used to be silently
 * dropped, corrupting the text content.
 */
public class TestCharRefAsEntsSplit292 extends BaseStreamTest
{
    // Build text long enough (and with a small input buffer) to guarantee that
    // some `&quot;` references end up split across buffer boundaries.
    private final static int ENTITY_COUNT = 1000;

    private static String buildXml() {
        StringBuilder sb = new StringBuilder("<root>");
        for (int i = 0; i < ENTITY_COUNT; i++) {
            sb.append(i).append("&quot;");
        }
        return sb.append("</root>").toString();
    }

    private static String expectedText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ENTITY_COUNT; i++) {
            sb.append(i).append('"');
        }
        return sb.toString();
    }

    private XMLInputFactory factory(boolean coalescing) throws Exception {
        XMLInputFactory f = getInputFactory();
        setCoalescing(f, coalescing);
        setReplaceEntities(f, true);
        setValidating(f, false);
        f.setProperty(WstxInputProperties.P_TREAT_CHAR_REFS_AS_ENTS, Boolean.TRUE);
        // Tiny buffer to force splitting of `&quot;` across reads:
        f.setProperty(WstxInputProperties.P_INPUT_BUFFER_LENGTH, Integer.valueOf(32));
        return f;
    }

    // getText() path, coalescing (single CHARACTERS event)
    @Test
    public void testNoCorruptionCoalescingGetText() throws Exception {
        XMLStreamReader2 sr = (XMLStreamReader2) constructStreamReader(factory(true), buildXml());
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());
        assertEquals(expectedText(), sr.getText());
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();
    }

    // getText() path, non-coalescing (segments accumulated)
    @Test
    public void testNoCorruptionNonCoalescingGetText() throws Exception {
        XMLStreamReader2 sr = (XMLStreamReader2) constructStreamReader(factory(false), buildXml());
        assertTokenType(START_ELEMENT, sr.next());
        StringBuilder actual = new StringBuilder();
        int t;
        while ((t = sr.next()) != END_ELEMENT) {
            if (t == CHARACTERS || t == ENTITY_REFERENCE) {
                actual.append(sr.getText());
            }
        }
        assertEquals(expectedText(), actual.toString());
        sr.close();
    }

    // getText(Writer) path -> readAndWriteText / readAndWriteCoalesced
    @Test
    public void testNoCorruptionGetTextWriter() throws Exception {
        XMLStreamReader2 sr = (XMLStreamReader2) constructStreamReader(factory(true), buildXml());
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());
        StringWriter w = new StringWriter();
        sr.getText(w, false);
        assertEquals(expectedText(), w.toString());
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();
    }
}
