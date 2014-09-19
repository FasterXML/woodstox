package stax2.dom;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.*;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.DocumentFragment;

import stax2.BaseStax2Test;

public class TestFragments extends BaseStax2Test
{
    // [WSTX-257]
    public void testFragmentIssue257() throws Exception
    {
        DocumentFragment fragment = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument().createDocumentFragment();
        XMLStreamWriter xmlWriter = getOutputFactory().createXMLStreamWriter(new DOMResult(fragment));
        // create equivalent of "<a>value1</a><b>value2</b>"
        xmlWriter.writeStartElement("a");
        xmlWriter.writeCharacters("value1");
        xmlWriter.writeEndElement();
        xmlWriter.writeStartElement("b");
        xmlWriter.writeCharacters("value2");
        xmlWriter.writeEndElement();
        xmlWriter.close();

        XMLStreamReader sr = getInputFactory().createXMLStreamReader(new DOMSource(fragment));
        assertTokenType(START_DOCUMENT, sr.getEventType());

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("a", sr.getLocalName());
        assertTokenType(CHARACTERS, sr.next());
        assertEquals("value1", sr.getText());
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("a", sr.getLocalName());

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("b", sr.getLocalName());
        assertTokenType(CHARACTERS, sr.next());
        assertEquals("value2", sr.getText());
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("b", sr.getLocalName());
        
        assertTokenType(END_DOCUMENT, sr.next());
        assertFalse(sr.hasNext());
    }

}
