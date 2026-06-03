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

    // getText() path, non-coalescing (multiple CHARACTERS segments).
    // Also asserts the token contract: with the default (high) min-text-segment,
    // mid-content char refs are inlined into CHARACTERS rather than systematically
    // promoted to standalone ENTITY_REFERENCE events -- so a regression that flips
    // inlining back into separate entity events is caught, not masked by
    // concatenating both event types.
    //
    // Note: this is a "vast majority are inlined" assertion, not "exactly zero".
    // A non-coalescing segment boundary can legitimately fall on a '&' (it is
    // driven by output-buffer sizing, not content), making an occasional ref a
    // standalone event with no data loss; only a wholesale flip (every ref) is a
    // real regression, and that would push entityRefs to ~ENTITY_COUNT.
    @Test
    public void testNoCorruptionNonCoalescingGetText() throws Exception {
        for (int r = 0; r < REFS.length; ++r) {
            XMLStreamReader2 sr = (XMLStreamReader2) constructStreamReader(factory(false), buildText(REFS[r]));
            assertTokenType(START_ELEMENT, sr.next());
            StringBuilder actual = new StringBuilder();
            int chars = 0, entityRefs = 0;
            int t;
            while ((t = sr.next()) != END_ELEMENT) {
                if (t == CHARACTERS) {
                    ++chars;
                    actual.append(sr.getText());
                } else if (t == ENTITY_REFERENCE) {
                    ++entityRefs;
                    actual.append(sr.getText());
                }
            }
            assertEquals("ref="+REFS[r], expected(REPLACEMENTS[r]), actual.toString());
            if (chars < 1) {
                fail("ref="+REFS[r]+": expected at least one CHARACTERS event");
            }
            // Tolerate incidental boundary-aligned events; catch a wholesale flip:
            if (entityRefs >= ENTITY_COUNT / 10) {
                fail("ref="+REFS[r]+": expected char refs to be inlined as CHARACTERS, but got "
                        +entityRefs+" ENTITY_REFERENCE event(s) out of "+ENTITY_COUNT+" refs");
            }
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

    // Deterministic coverage: rather than relying on volume to probabilistically
    // straddle a boundary, sweep the leading-pad length so the reference's start
    // offset covers every alignment relative to the (small) input buffer. This
    // guarantees the split path is exercised regardless of future buffer-refill
    // alignment changes -- in both element content and attribute values.
    @Test
    public void testSplitBoundarySweep() throws Exception {
        final int bufLen = 11;
        for (int pad = 0; pad <= 2 * bufLen + 8; ++pad) {
            StringBuilder p = new StringBuilder();
            for (int i = 0; i < pad; ++i) {
                p.append('x');
            }
            for (int r = 0; r < REFS.length; ++r) {
                String body = p + REFS[r];
                String exp = p + REPLACEMENTS[r];
                String desc = "pad="+pad+",ref="+REFS[r];

                // element content: accumulate across CHARACTERS/ENTITY_REFERENCE,
                // since a ref at the very start of content is a standalone event
                XMLStreamReader2 sr = (XMLStreamReader2) constructStreamReader(
                        factory(false, false, bufLen), "<root>"+body+"</root>");
                assertTokenType(START_ELEMENT, sr.next());
                StringBuilder actual = new StringBuilder();
                int t;
                while ((t = sr.next()) != END_ELEMENT) {
                    if (t == CHARACTERS || t == ENTITY_REFERENCE) {
                        actual.append(sr.getText());
                    }
                }
                assertEquals(desc, exp, actual.toString());
                sr.close();

                // attribute value
                sr = (XMLStreamReader2) constructStreamReader(
                        factory(false, false, bufLen), "<root a=\""+body+"\"/>");
                assertTokenType(START_ELEMENT, sr.next());
                assertEquals(desc, exp, sr.getAttributeValue(0));
                sr.close();
            }
        }
    }

    // Deterministic coverage of the getText(Writer) sink (readAndWriteText /
    // readAndWriteCoalesced), for BOTH coalescing and non-coalescing readers --
    // the cursor sweep above never exercises the Writer path. A leading text char
    // keeps the body a single CHARACTERS event (content stays < min-text-segment),
    // and the pad sweep makes the trailing ref straddle the buffer at every
    // alignment.
    @Test
    public void testGetTextWriterSplitSweep() throws Exception {
        final int bufLen = 11;
        final boolean[] coalescingModes = { false, true };
        for (boolean coalescing : coalescingModes) {
            for (int pad = 0; pad <= 2 * bufLen + 8; ++pad) {
                StringBuilder p = new StringBuilder("z"); // leading text -> CHARACTERS, not ENTITY_REFERENCE
                for (int i = 0; i < pad; ++i) {
                    p.append('x');
                }
                for (int r = 0; r < REFS.length; ++r) {
                    String exp = p + REPLACEMENTS[r];
                    String desc = "coalescing="+coalescing+",pad="+pad+",ref="+REFS[r];

                    XMLStreamReader2 sr = (XMLStreamReader2) constructStreamReader(
                            factory(coalescing, false, bufLen), "<root>"+p+REFS[r]+"</root>");
                    assertTokenType(START_ELEMENT, sr.next());
                    assertTokenType(CHARACTERS, sr.next());
                    StringWriter w = new StringWriter();
                    sr.getText(w, false);
                    assertEquals(desc, exp, w.toString());
                    sr.close();
                }
            }
        }
    }

    // Nested input source interop: a general-entity reference (`&ge;`) creates a
    // new input source whose expansion itself contains char refs. This exercises
    // the `mInput` discriminator in takeInlineCharRefReplacement(): the general
    // entity pushes a source (mInput changes -> must NOT be inlined as a char ref),
    // while the `&quot;` refs within the expansion are inlined while the active
    // source is the entity's, not the root document's. Covers content + attribute.
    @Test
    public void testNestedEntityCharRefs() throws Exception {
        // Expansion with several char refs embedded among longer runs of text:
        final String geRaw = "aaaaaaaaaaaaaaaaaaaa&quot;bbbb&quot;cccccccccccccccccccc&quot;dddd";
        final String geExpanded = "aaaaaaaaaaaaaaaaaaaa\"bbbb\"cccccccccccccccccccc\"dddd";
        final String dtd = "<!DOCTYPE root [<!ENTITY ge \"" + geRaw + "\">]>";
        final String xml = dtd + "<root a=\"X&ge;Y\">P&ge;Q</root>";

        for (int bufLen : new int[] { 16, 24, 32, 64 }) {
            XMLStreamReader2 sr = (XMLStreamReader2) constructStreamReader(
                    factory(true, true, bufLen), xml);
            int t;
            while ((t = sr.next()) != START_ELEMENT) {
                ; // skip DTD (and any prolog) event(s)
            }
            String desc = "bufLen=" + bufLen;
            assertEquals(desc, "X" + geExpanded + "Y", sr.getAttributeValue(0));

            StringBuilder content = new StringBuilder();
            while ((t = sr.next()) != END_ELEMENT) {
                if (t == CHARACTERS || t == ENTITY_REFERENCE) {
                    content.append(sr.getText());
                }
            }
            assertEquals(desc, "P" + geExpanded + "Q", content.toString());
            sr.close();
        }
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Helper methods
    ///////////////////////////////////////////////////////////////////////
     */

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
        // Tiny buffer to force splitting of references across reads:
        return factory(coalescing, false, 32);
    }

    private XMLInputFactory factory(boolean coalescing, boolean supportDTD, int bufLen) throws Exception {
        XMLInputFactory f = getInputFactory();
        setCoalescing(f, coalescing);
        setReplaceEntities(f, true);
        setValidating(f, false);
        setSupportDTD(f, supportDTD);
        f.setProperty(WstxInputProperties.P_TREAT_CHAR_REFS_AS_ENTS, Boolean.TRUE);
        f.setProperty(WstxInputProperties.P_INPUT_BUFFER_LENGTH, Integer.valueOf(bufLen));
        return f;
    }
}
