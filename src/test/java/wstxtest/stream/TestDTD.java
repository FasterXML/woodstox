package wstxtest.stream;

import javax.xml.stream.*;

import org.codehaus.stax2.*;

/**
 * Set of unit tests that checks some additional invariants Woodstox
 * guarantees with respect to DOCTYPE declaration handling.
 */
public class TestDTD
    extends BaseStreamTest
{

    /**
     * Tests that the DOCTYPE declaration can be succesfully skipped in
     * the non-DTD-support mode.
     */
    public void testSkipping()
        throws XMLStreamException
    {
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root ANY>\n"
            +"<!ATTLIST root attr CDATA #IMPLIED>\r\n"
            +"<!ENTITY xyz 'some &amp; value'>"
            +"<!-- comment -->"
            +"]>"
            +"<root />";

        XMLInputFactory2 f = getInputFactory();
        setSupportDTD(f, false);
        XMLStreamReader2 sr = constructStreamReader(f, XML);
        assertTokenType(DTD, sr.next());

        DTDInfo info = sr.getDTDInfo();
        assertNotNull(info);

        assertTokenType(START_ELEMENT, sr.next());
    }
}
