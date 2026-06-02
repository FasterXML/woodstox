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
 * dropped, corrupting both text content and attribute values.
 */
public class TestCharRefAsEntsSplit292 extends BaseStreamTest
{
    // Enough references (against a tiny input buffer) to guarantee that some end
    // up split across buffer boundaries:
    private final static int ENTITY_COUNT = 1000;

    // Reference forms to exercise: pre-defined entity, decimal char ref, hex char
    // ref, and a supplementary (surrogate-pair) char ref -- the last one takes a
    // distinct 2-char replacement path.
    private final static String[] REFS = {
        "&quot;", "&#34;", "&#x22;", "&#128512;"
    };
    private final static String[] REPLACEMENTS = {
        "\"", "\"", "\"", "😀" // U+1F600
    };

    private static String buildText(String ref) {
        StringBuilder sb = new StringBuilder("<root>");
        for (int i = 0; i < ENTITY_COUNT; i++) {
            sb.append(i).append(ref);
        }
        return sb.append("</root>").toString();
    }

    private static String buildAttr(String ref) {
        StringBuilder sb = new StringBuilder("<root a=\"");
        for (int i = 0; i < ENTITY_COUNT; i++) {
            sb.append(i).append(ref);
        }
        return sb.append("\"/>").toString();
    }

    private static String expected(String replacement) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ENTITY_COUNT; i++) {
            sb.append(i).append(replacement);
        }
        return sb.toString();
    }

    private XMLInputFactory factory(boolean coalescing) throws Exception {
        XMLInputFactory f = getInputFactory();
        setCoalescing(f, coalescing);
        setReplaceEntities(f, true);
        setValidating(f, false);
        f.setProperty(WstxInputProperties.P_TREAT_CHAR_REFS_AS_ENTS, Boolean.TRUE);
        // Tiny buffer to force splitting of references across reads:
        f.setProperty(WstxInputProperties.P_INPUT_BUFFER_LENGTH, Integer.valueOf(32));
        return f;
    }

    // getText() path, coalescing (single CHARACTERS event)
    @Test
    public void testNoCorruptionCoalescingGetText() throws Exception {
        for (int r = 0; r < REFS.length; ++r) {
            XMLStreamReader2 sr = (XMLStreamReader2) constructStreamReader(factory(true), buildText(REFS[r]));
            assertTokenType(START_ELEMENT, sr.next());
            assertTokenType(CHARACTERS, sr.next());
            assertEquals("ref="+REFS[r], expected(REPLACEMENTS[r]), sr.getText());
            assertTokenType(END_ELEMENT, sr.next());
            sr.close();
        }
    }

    // getText() path, non-coalescing (segments + entity events accumulated)
    @Test
    public void testNoCorruptionNonCoalescingGetText() throws Exception {
        for (int r = 0; r < REFS.length; ++r) {
            XMLStreamReader2 sr = (XMLStreamReader2) constructStreamReader(factory(false), buildText(REFS[r]));
            assertTokenType(START_ELEMENT, sr.next());
            StringBuilder actual = new StringBuilder();
            int t;
            while ((t = sr.next()) != END_ELEMENT) {
                if (t == CHARACTERS || t == ENTITY_REFERENCE) {
                    actual.append(sr.getText());
                }
            }
            assertEquals("ref="+REFS[r], expected(REPLACEMENTS[r]), actual.toString());
            sr.close();
        }
    }

    // getText(Writer) path -> readAndWriteText / readAndWriteCoalesced
    @Test
    public void testNoCorruptionGetTextWriter() throws Exception {
        for (int r = 0; r < REFS.length; ++r) {
            XMLStreamReader2 sr = (XMLStreamReader2) constructStreamReader(factory(true), buildText(REFS[r]));
            assertTokenType(START_ELEMENT, sr.next());
            assertTokenType(CHARACTERS, sr.next());
            StringWriter w = new StringWriter();
            sr.getText(w, false);
            assertEquals("ref="+REFS[r], expected(REPLACEMENTS[r]), w.toString());
            assertTokenType(END_ELEMENT, sr.next());
            sr.close();
        }
    }

    // parseAttrValue() path
    @Test
    public void testNoCorruptionAttributeValue() throws Exception {
        for (int r = 0; r < REFS.length; ++r) {
            XMLStreamReader2 sr = (XMLStreamReader2) constructStreamReader(factory(false), buildAttr(REFS[r]));
            assertTokenType(START_ELEMENT, sr.next());
            assertEquals(1, sr.getAttributeCount());
            assertEquals("ref="+REFS[r], expected(REPLACEMENTS[r]), sr.getAttributeValue(0));
            assertTokenType(END_ELEMENT, sr.next());
            sr.close();
        }
    }
}
