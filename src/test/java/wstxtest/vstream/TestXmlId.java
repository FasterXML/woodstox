package wstxtest.vstream;

import javax.xml.stream.*;

import wstxtest.stream.BaseStreamTest;

public class TestXmlId
    extends BaseStreamTest
{
    /**
     * This is a simple regression test -- at one point, last character
     * of id attributes was dropped.
     */
    public void testSimpleNonNs()
        throws XMLStreamException
    {
        doTestSimple(false);
    }

    public void testSimpleNs()
        throws XMLStreamException
    {
        doTestSimple(true);
    }

    /*
    //////////////////////////////////////////////////
    // Helper methods
    //////////////////////////////////////////////////
     */

    private void doTestSimple(boolean ns)
        throws XMLStreamException
    {
        final String XML =
            "<!DOCTYPE test [\n"
            +"<!ELEMENT test (sub+)>\n"
            +"<!ELEMENT sub EMPTY>\n"
            +"<!ATTLIST sub gh ID #REQUIRED>\n"
            +"]>\n<test>"
            +"<sub gh='xxxa'/><sub gh='xxxb'/>\n"
            +"<sub gh='xxxc'/><sub gh='xxxd'/>\n"
            +"<sub gh='yyya'/>\n"
            +"</test>\n"
            ;
        XMLStreamReader sr = getReader(XML, ns);
        // Should succeed 
        streamThrough(sr);
    }

    private XMLStreamReader getReader(String xml, boolean nsAware)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setNamespaceAware(f, nsAware);
        setSupportDTD(f, true);
        setValidating(f, true);
        return constructStreamReader(f, xml);
    }
}
