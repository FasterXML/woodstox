package stax2.dtd;

import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;

import stax2.BaseStax2Test;

public class TestReaderWithDTD extends BaseStax2Test
{
    public void testGetPrefixedName() throws XMLStreamException
    {
        doTestGetPrefixedName(false);
        doTestGetPrefixedName(true);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Helper methods
    ///////////////////////////////////////////////////////////////////////
     */
    
    public void doTestGetPrefixedName(boolean ns)
        throws XMLStreamException
    {
        final String XML =
            "<!DOCTYPE root [\n"
            +"<!ENTITY intEnt '<leaf />'>\n"
            +"]>"
            +"<root>"
            +"<xy:elem xmlns:xy='http://foo' xmlns:another='http://x'>"
            +"<?proc instr?>&intEnt;<another:x /></xy:elem>"
            +"</root>"
            ;
        XMLStreamReader2 sr = getReader(XML, ns);
        try {
            assertTokenType(DTD, sr.next());
            assertEquals("root", sr.getPrefixedName());
            assertTokenType(START_ELEMENT, sr.next());
            assertEquals("root", sr.getPrefixedName());
            assertTokenType(START_ELEMENT, sr.next());
            assertEquals("xy:elem", sr.getPrefixedName());
            assertTokenType(PROCESSING_INSTRUCTION, sr.next());
            assertEquals("proc", sr.getPrefixedName());
            assertTokenType(START_ELEMENT, sr.next());
            assertEquals("leaf", sr.getPrefixedName());
            assertTokenType(END_ELEMENT, sr.next());
            assertEquals("leaf", sr.getPrefixedName());
            assertTokenType(START_ELEMENT, sr.next());
            assertEquals("another:x", sr.getPrefixedName());
            assertTokenType(END_ELEMENT, sr.next());
            assertEquals("another:x", sr.getPrefixedName());
            assertTokenType(END_ELEMENT, sr.next());
            assertEquals("xy:elem", sr.getPrefixedName());
            assertTokenType(END_ELEMENT, sr.next());
            assertEquals("root", sr.getPrefixedName());
            assertTokenType(END_DOCUMENT, sr.next());
        } catch (XMLStreamException xse) {
            fail("Did not expect any problems during parsing, but got: "+xse);
        }
    }

    private XMLStreamReader2 getReader(String contents, boolean nsAware)
        throws XMLStreamException
    {
        XMLInputFactory2 f = getInputFactory();
        setCoalescing(f, true);
        setSupportDTD(f, true);
        setNamespaceAware(f, nsAware);
        setValidating(f, false);
        return constructStreamReader(f, contents);
    }
}
