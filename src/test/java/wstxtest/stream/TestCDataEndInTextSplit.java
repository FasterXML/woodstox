package wstxtest.stream;

import java.io.StringWriter;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;
import org.codehaus.stax2.XMLStreamReader2;

import com.ctc.wstx.api.WstxInputProperties;

/**
 * Test that the well-formedness check forbidding a literal {@code "]]>"} in
 * character content (XML 1.0/1.1 #2.4) still fires when the sequence is split
 * across the internal input-buffer boundary. Previously the look-back used to
 * spot {@code "]]>"} only inspected the current buffer, so a sequence whose
 * {@code "]]"} ended one buffer and whose {@code '>'} started the next was
 * silently accepted.
 *<p>
 * The complementary case must keep working: {@code "]]"} produced by a general
 * entity followed by a literal {@code '>'} is legitimately quoted (the literal
 * string never appears in the source) and must parse cleanly at every alignment.
 */
public class TestCDataEndInTextSplit extends BaseStreamTest
{
    // small enough that the pad sweep below straddles the boundary at every offset
    private final static int BUF_LEN = 11;

    // Illegal literal "]]>" in element content, swept across all buffer alignments.
    // Includes longer bracket runs ("]]]>", "]]]]>") whose trailing "]]>" must be
    // caught even when two or more brackets end one buffer and "]>" opens the next.
    @Test
    public void testSplitBracketEndContent() throws Exception {
        String[] tails = { "]]>", "]]]>", "]]]]>", "x]]]>" };
        for (String tail : tails) {
            for (int pad = 0; pad <= 3 * BUF_LEN + 8; ++pad) {
                String xml = "<root>" + pad(pad) + tail + "</root>";
                assertRejected("content tail="+tail+" pad="+pad, factory(false, false), xml);
                assertRejected("content-coalescing tail="+tail+" pad="+pad, factory(true, false), xml);
            }
        }
    }

    // Same, through the getText(Writer) sink (readAndWriteText)
    @Test
    public void testSplitBracketEndGetTextWriter() throws Exception {
        for (int pad = 0; pad <= 3 * BUF_LEN + 8; ++pad) {
            // leading 'z' keeps content a single CHARACTERS event
            String xml = "<root>z" + pad(pad) + "]]></root>";
            XMLStreamReader2 sr = (XMLStreamReader2) constructStreamReader(factory(true, false), xml);
            boolean rejected = false;
            try {
                assertTokenType(START_ELEMENT, sr.next());
                sr.next();
                sr.getText(new StringWriter(), false);
                while (sr.next() != END_ELEMENT) { }
            } catch (Exception e) {
                if (!isBracketError(e)) {
                    throw e;
                }
                rejected = true;
            }
            if (!rejected) {
                fail("getText(Writer) pad="+pad+": expected split ']]>' to be rejected");
            }
            sr.close();
        }
    }

    // Legal: a general entity expanding to "]]" then a literal '>' is quoted and
    // must NOT be flagged, regardless of where the boundary falls.
    @Test
    public void testEntityQuotedBracketsAllowed() throws Exception {
        final String dtd = "<!DOCTYPE root [<!ENTITY bb \"]]\">]>";
        for (int pad = 0; pad <= 3 * BUF_LEN + 8; ++pad) {
            String p = pad(pad);
            String xml = dtd + "<root>" + p + "&bb;></root>";
            XMLStreamReader2 sr = (XMLStreamReader2) constructStreamReader(factory(false, true), xml);
            int t;
            while ((t = sr.next()) != START_ELEMENT) {
                ; // skip DTD / prolog
            }
            StringBuilder actual = new StringBuilder();
            while ((t = sr.next()) != END_ELEMENT) {
                if (t == CHARACTERS || t == ENTITY_REFERENCE) {
                    actual.append(sr.getText());
                }
            }
            assertEquals("pad="+pad, p + "]]>", actual.toString());
            sr.close();
        }
    }

    // Legal: single ']' before '>' (and a trailing "]]" not followed by '>') are
    // fine, including across the boundary.
    @Test
    public void testLegalBracketsAllowed() throws Exception {
        String[] bodies = { "]>", "]]", "x]]x", "]]]" };
        for (String body : bodies) {
            for (int pad = 0; pad <= 3 * BUF_LEN + 8; ++pad) {
                String content = pad(pad) + body;
                String xml = "<root>" + content + "</root>";
                XMLStreamReader2 sr = (XMLStreamReader2) constructStreamReader(factory(false, false), xml);
                assertTokenType(START_ELEMENT, sr.next());
                StringBuilder actual = new StringBuilder();
                int t;
                while ((t = sr.next()) != END_ELEMENT) {
                    if (t == CHARACTERS || t == ENTITY_REFERENCE) {
                        actual.append(sr.getText());
                    }
                }
                assertEquals("body="+body+",pad="+pad, content, actual.toString());
                sr.close();
            }
        }
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Helper methods
    ///////////////////////////////////////////////////////////////////////
     */

    private void assertRejected(String desc, XMLInputFactory f, String xml) throws Exception {
        XMLStreamReader2 sr = (XMLStreamReader2) constructStreamReader(f, xml);
        try {
            int t;
            while ((t = sr.next()) != END_ELEMENT) {
                if (t == CHARACTERS || t == ENTITY_REFERENCE) {
                    sr.getText();
                }
            }
            fail(desc + ": expected split ']]>' in content to be rejected");
        } catch (Exception e) {
            if (!isBracketError(e)) {
                throw e;
            }
        } finally {
            sr.close();
        }
    }

    // getText() may surface the deferred error lazily (as a RuntimeException), so
    // accept either form as long as it is the "]]>" well-formedness error.
    private static boolean isBracketError(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg != null && msg.indexOf("]]>") >= 0) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private static String pad(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; ++i) {
            sb.append('x');
        }
        return sb.toString();
    }

    private XMLInputFactory factory(boolean coalescing, boolean supportDTD) throws XMLStreamException {
        XMLInputFactory f = getInputFactory();
        setCoalescing(f, coalescing);
        setReplaceEntities(f, true);
        setValidating(f, false);
        setSupportDTD(f, supportDTD);
        f.setProperty(WstxInputProperties.P_INPUT_BUFFER_LENGTH, Integer.valueOf(BUF_LEN));
        return f;
    }
}
