package stax2.stream;

import javax.xml.stream.*;

import stax2.BaseStax2Test;

public class TestAttrBasic
    extends BaseStax2Test
{
    public void testNormalization()
        throws XMLStreamException
    {
        String[] LFs = new String[] { "\n", "\r", "\r\n" };
        for (int i = 0; i < LFs.length; ++i) {
            XMLStreamReader sr = constructNsStreamReader("<root attr='"+LFs[i]+"' />", true);
            assertTokenType(START_DOCUMENT, sr.getEventType());
            assertTokenType(START_ELEMENT, sr.next());
            assertEquals(1, sr.getAttributeCount());
            // line feeds to be normalized into space
            assertEquals(" ", sr.getAttributeValue(0));
            assertTokenType(END_ELEMENT, sr.next());
            assertTokenType(END_DOCUMENT, sr.next());
        }

        XMLStreamReader sr = constructNsStreamReader("<root attr='&#xD;\n' />", true);
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(1, sr.getAttributeCount());
        // entity as is, \n as space
        assertEquals("\r ", sr.getAttributeValue(0));
    }
}
