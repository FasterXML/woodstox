package stax2.dtd;

import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.*;

import stax2.BaseStax2Test;

public class TestNonValidatingDtdAware
    extends BaseStax2Test
{
    public void testSpaceNs()
        throws XMLStreamException
    {
        doTestWS(true, false);
        doTestWS(true, true);
    }

    public void testSpaceNonNs()
        throws XMLStreamException
    {
        doTestWS(false, false);
        doTestWS(false, true);
    }

    public void testFalseSpace()
        throws XMLStreamException
    {
        doTestFalseWS(true, false);
        doTestFalseWS(true, true);
        doTestFalseWS(false, false);
        doTestFalseWS(false, true);
    }

    /*
    ////////////////////////////////////////
    // Private methods, shared test code
    ////////////////////////////////////////
     */

    public void doTestWS(boolean ns, boolean coalesce)
        throws XMLStreamException
    {
        final String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root (leaf+)>\n"
            +"<!ELEMENT leaf EMPTY>\n"
            +"]>"
            +"<root>\n"
            +"  <leaf />\n"
            +"</root>";
        XMLStreamReader2 sr = getReader(XML, ns, coalesce);

        assertTokenType(START_DOCUMENT, sr.getEventType());
        assertTokenType(DTD, sr.next());

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());

        assertTokenType(SPACE, sr.next());
        assertEquals("\n  ", getAndVerifyText(sr));

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());

        assertTokenType(SPACE, sr.next());
        assertEquals("\n", getAndVerifyText(sr));

        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());

        assertTokenType(END_DOCUMENT, sr.next());
    }

    public void doTestFalseWS(boolean ns, boolean coalesce)
        throws XMLStreamException
    {
        final String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root (leaf+)>\n"
            +"<!ELEMENT leaf EMPTY>\n"
            +"]>"
            +"<root>\n"
            +"  Foo<leaf />bar"
            +"</root>";
        XMLStreamReader2 sr = getReader(XML, ns, coalesce);

        assertTokenType(START_DOCUMENT, sr.getEventType());
        assertTokenType(DTD, sr.next());

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());

        /* not 100% if this is expected in coalescing mode too,
         * but it is the way it's implemented, and kind of makes
         * sense even though there are alternatives
         */
        assertTokenType(SPACE, sr.next());
        assertEquals("\n  ", getAndVerifyText(sr));
        assertTokenType(CHARACTERS, sr.next());
        assertEquals("Foo", getAndVerifyText(sr));

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());

        assertTokenType(CHARACTERS, sr.next());
        assertEquals("bar", getAndVerifyText(sr));

        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());

        assertTokenType(END_DOCUMENT, sr.next());
    }


    /*
    ////////////////////////////////////////
    // Private methods, other
    ////////////////////////////////////////
     */

    private XMLStreamReader2 getReader(String contents, boolean nsAware,
                                       boolean coal)
        throws XMLStreamException
    {
        XMLInputFactory2 f = getInputFactory();
        setNamespaceAware(f, nsAware);
        setCoalescing(f, coal);
        setSupportDTD(f, true);
        setValidating(f, false);
        return constructStreamReader(f, contents);
    }
}

