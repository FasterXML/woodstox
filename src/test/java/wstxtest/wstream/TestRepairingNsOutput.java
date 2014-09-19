package wstxtest.wstream;

import java.io.*;

import javax.xml.stream.*;

public class TestRepairingNsOutput
    extends BaseWriterTest
{

    /*
    ////////////////////////////////////////////////////
    // Main test methods
    ////////////////////////////////////////////////////
     */

    public void testNoDummyDefaultNs()
        throws XMLStreamException
    {
        XMLOutputFactory f = getFactory();
        StringWriter strw = new StringWriter();
        XMLStreamWriter sw = f.createXMLStreamWriter(strw);

        sw.writeStartDocument();
        sw.writeStartElement("", "root", "");
        sw.writeAttribute("attr", "value");
        sw.writeAttribute("", "", "attr2", "value2");
        sw.writeStartElement("", "leaf", "");
        sw.writeAttribute("", "", "foop", "value2");
        sw.writeCharacters("Sub-text\n");
        sw.writeEndElement();
        sw.writeEndDocument();
        sw.close();

        String result = strw.toString();

        if (result.indexOf("xmlns=\"\"") > 0) {
            fail("Did not expect unnecessary default NS declarations, but found some in result: ["+result+"]");
        }
    }

    /**
     * Starting with Woodstox 3.1, the repairing writer is to honour
     * non-conflicting namespace write requests. This may be needed to
     * either try to preserve ns declaration canonicality, and/or to
     * minimize number of declarations (a common root can bind a namespace
     * for children, even without having to use it for its own attributes
     * or element name).
     *<p>
     * Since this functionality is not required (or even suggested from what
     * I can tell) by Stax 1.0 specs (and Stax2 does not change definitions
     * of core API), this is in woodstox-specific section of tests.
     */
    public void testExplicitNsWrites()
        throws XMLStreamException
    {
        final String URI = "http://bar";
        XMLOutputFactory f = getFactory();
        StringWriter strw = new StringWriter();
        XMLStreamWriter sw = f.createXMLStreamWriter(strw);

        sw.writeStartDocument();
        /* root in no namespace, no attributes; but want to add
         * an 'early' ns declaration for ns prefix 'foo',
         * with URI 'http://bar'
         */
        sw.writeStartElement("", "root");
        sw.writeNamespace("foo", URI);
        // leaf in that namespace, then:
        sw.writeStartElement(URI, "leaf");
        sw.writeEndElement();
        sw.writeEndElement();
        sw.writeEndDocument();
        sw.close();

        String result = strw.toString();

        // Ok, so let's parse and verify:
        XMLStreamReader sr = constructNsStreamReader(result, false);
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertElemNotInNamespace(sr);
        
        int nsCount = sr.getNamespaceCount();
        assertEquals("Expected one (and only one) namespace declaration, got "+nsCount, 1, nsCount);
        assertEquals("foo", sr.getNamespacePrefix(0));
        assertEquals(URI, sr.getNamespaceURI(0));
        
        // And then the branch should have no ns decls:
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());
        assertEquals(URI, sr.getNamespaceURI());
        
        assertEquals(0, sr.getNamespaceCount());
        
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());
        
        // fine, rest is ok
        sr.close();
    }

    /**
     * Similar to {@link #testExplicitNsWrites}, but tests behavior
     * of calls to <code>XMLStreamWriter.writeDefaultNamespace</code>
     */
    public void testExplicitDefaultNsWrite()
        throws XMLStreamException
    {
        final String URI1 = "http://foo";
        final String URI2 = "http://bar";
        XMLOutputFactory f = getFactory();
        StringWriter strw = new StringWriter();
        XMLStreamWriter sw = f.createXMLStreamWriter(strw);

        sw.writeStartDocument();
        /* root in explicit namespace, but additionally want to
         * reserve the default ns:
         */
        sw.writeStartElement("myns", "root", URI1);
        sw.writeDefaultNamespace(URI2);
        // leaf in that namespace, then:
        sw.writeStartElement(URI2, "leaf");
        sw.writeEndElement();
        sw.writeEndElement();
        sw.writeEndDocument();
        sw.close();

        String result = strw.toString();

        // Ok, so let's parse and verify:
        XMLStreamReader sr = constructNsStreamReader(result, false);
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertEquals("myns", sr.getPrefix());
        assertEquals(URI1, sr.getNamespaceURI());
        
        int nsCount = sr.getNamespaceCount();
        assertEquals("Expected two namespace declarations, got "+nsCount, 2, nsCount);
        
        // And then leaf should have no ns decls:
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());
        assertNoElemPrefix(sr);
        assertEquals(URI2, sr.getNamespaceURI());
        assertEquals(0, sr.getNamespaceCount());
        
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());
        
        // fine, rest is ok
        sr.close();
    }

    /**
     * This test further verifies that caller's prefix-preference
     * has higher priority than that of trying to find an existing
     * prefix to use.
     */
    public void testExplicitDupNsWrite()
        throws XMLStreamException
    {
        final String URI = "http://bar";
        XMLOutputFactory f = getFactory();
        StringWriter strw = new StringWriter();
        XMLStreamWriter sw = f.createXMLStreamWriter(strw);

        sw.writeStartDocument();
        sw.writeStartElement("ns", "root", URI);
        sw.writeAttribute("attrns", URI, "attr", "value");

        sw.writeEndElement();
        sw.writeEndDocument();
        sw.close();

        String result = strw.toString();

        // Ok, so let's parse and verify:
        XMLStreamReader sr = constructNsStreamReader(result, false);
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertEquals(URI, sr.getNamespaceURI());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("attr", sr.getAttributeLocalName(0));
        assertEquals(URI, sr.getAttributeNamespace(0));
        // so far so good: but let's verify prefix is also what caller specified
        assertEquals("attrns", sr.getAttributePrefix(0));
        
        assertEquals(2, sr.getNamespaceCount());
        
        // fine, rest is ok
        sr.close();
    }

    /*
    ////////////////////////////////////////////////////
    // Internal methods
    ////////////////////////////////////////////////////
     */

    private XMLOutputFactory getFactory()
        throws XMLStreamException
    {
        XMLOutputFactory f = getOutputFactory();
        setNamespaceAware(f, true);
        setRepairing(f, true);
        return f;
    }

}

