package stax2.stream;

import com.ctc.wstx.api.WstxInputProperties;
import com.ctc.wstx.exc.WstxParsingException;
import org.codehaus.stax2.XMLInputFactory2;
import stax2.BaseStax2Test;

import javax.xml.stream.XMLStreamReader;

public class TestAllowXml11EscapedCharsInXml10 extends BaseStax2Test {
    /**
     * Unit test to verify workaround for XML 1.1 escaped chars in XML 1.0 file.
     */
    public void testAllowXml11EscapedCharsInXml10() throws Exception {
        XMLInputFactory2 f = getInputFactory();
        setNamespaceAware(f, true);
        setCoalescing(f, true);
        f.setProperty(WstxInputProperties.P_ALLOW_XML11_ESCAPED_CHARS_IN_XML10, true);
        XMLStreamReader sr = constructStreamReader(f, "<?xml version=\"1.0\" encoding=\"utf-8\"?><root>&#x2;</root>");
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());
        assertTokenType(END_ELEMENT, sr.next());
    }

    /**
     * Unit test to verify failure for XML 1.1 escaped chars in XML 1.0 file.
     */
    public void testDoNotAllowXml11EscapedCharsInXml10() throws Exception {
        XMLInputFactory2 f = getInputFactory();
        setNamespaceAware(f, true);
        setCoalescing(f, true);
        XMLStreamReader sr = constructStreamReader(f, "<?xml version=\"1.0\" encoding=\"utf-8\"?><root>&#x2;</root>");
        assertTokenType(START_ELEMENT, sr.next());
        try {
            assertTokenType(CHARACTERS, sr.next());
            fail("Should fail");
        } catch (WstxParsingException e) {
            // success
        }
        assertTokenType(END_ELEMENT, sr.next());
    }
}
