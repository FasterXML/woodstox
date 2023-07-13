package stax2.stream;

import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;
import stax2.BaseStax2Test;

import javax.xml.stream.XMLStreamException;

/**
 * Test entities in round-trip mode
 *
 * @author Guillaume Nodet
 */
public class TestCharEntityRef
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
}
