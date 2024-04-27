package stax2.stream;

import com.ctc.wstx.api.WstxInputProperties;
import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;
import stax2.BaseStax2Test;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;

/**
 * Test entities in round-trip mode
 *
 * @author Guillaume Nodet
 */
public class TestExternalEntityRef
    extends BaseStax2Test
{
    private static final String TEST_BASIC = "<project>" +
        "<?foo?>" +
        "<name>&oelig;</name>" +
        "</project>";

    public void testEntityRef()
        throws XMLStreamException {

        XMLInputFactory2 f = getInputFactory();
        f.configureForRoundTripping();
        XMLStreamReader2 sr = constructStreamReader(f, TEST_BASIC);

        assertEquals(START_ELEMENT, sr.next());
        assertEquals("project", sr.getLocalName());
        assertEquals(PROCESSING_INSTRUCTION, sr.next());
        assertEquals("foo", sr.getPITarget());
        assertEquals(START_ELEMENT, sr.next());
        assertEquals("name", sr.getLocalName());
        assertEquals(ENTITY_REFERENCE, sr.next());
        assertEquals("oelig", sr.getLocalName());
    }

    private static final String TEST_DTD = "<!DOCTYPE foo [\n" +
        "        <!ENTITY desc SYSTEM \"file:desc.xml\">\n" +
        "        ]>\n" +
        "<project>" +
        "&desc;" +
        "</project>";

    public void testWithDtd()
        throws XMLStreamException {

        XMLInputFactory2 f = getInputFactory();
        f.configureForRoundTripping();
        XMLStreamReader2 sr = constructStreamReader(f, TEST_DTD);

        assertEquals(DTD, sr.next());
        assertEquals(SPACE, sr.next());
        assertEquals(START_ELEMENT, sr.next());
        assertEquals("project", sr.getLocalName());
        assertEquals(ENTITY_REFERENCE, sr.next());
        assertEquals("desc", sr.getLocalName());
        assertEquals(END_ELEMENT, sr.next());
        assertEquals("project", sr.getLocalName());
    }

    public void testWithDtdExpand()
        throws XMLStreamException {

        XMLInputFactory2 f = getInputFactory();
        f.configureForRoundTripping();
        f.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, true);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, true);
        f.setProperty(WstxInputProperties.P_ENTITY_RESOLVER, new XMLResolver() {
            @Override
            public Object resolveEntity(String publicID, String systemID, String baseURI, String namespace) throws XMLStreamException {
                if ("file:desc.xml".equals(systemID)) {
                    return "<?xml version='1.0' encoding='UTF-8'?><description>foo</description>";
                }
                return null;
            }
        });
        XMLStreamReader2 sr = constructStreamReader(f, TEST_DTD);

        assertEquals(DTD, sr.next());
        assertEquals(SPACE, sr.next());
        assertEquals(START_ELEMENT, sr.next());
        assertEquals("project", sr.getLocalName());
        assertEquals(ENTITY_REFERENCE, sr.next());
        assertEquals("desc", sr.getLocalName());
        assertEquals(START_ELEMENT, sr.next());
        assertEquals("description", sr.getLocalName());
        assertEquals(CHARACTERS, sr.next());
        assertEquals("foo", sr.getText());
        assertEquals(END_ELEMENT, sr.next());
        assertEquals("description", sr.getLocalName());
        assertEquals(END_ELEMENT, sr.next());
        assertEquals("project", sr.getLocalName());
    }
}
