package stax2.stream;

import java.io.ByteArrayInputStream;
import java.io.StringReader;

import javax.xml.stream.*;

import org.codehaus.stax2.*;

import stax2.BaseStax2Test;

public class TestStreamReader
    extends BaseStax2Test
{
    /**
     * Unit test to verify fixing of (and guard against regression of)
     * [WSTX-201].
     */
    public void testIsCharacters() throws Exception
    {
        XMLInputFactory2 f = getInputFactory();
        setNamespaceAware(f, true);
        setCoalescing(f, true);
        XMLStreamReader sr = constructStreamReader(f, "<root><![CDATA[abc]]></root>");
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        // should both return CHARACTERS
        assertTokenType(CHARACTERS, sr.next());
        // and be considered of characters...
        assertEquals(CHARACTERS, sr.getEventType());
        assertTrue(sr.isCharacters());
        assertTokenType(END_ELEMENT, sr.next());
    }

    /**
     * Unit test related to [WSTX-274]
     */
    public void testCData() throws Exception
    {
        XMLInputFactory2 f = getInputFactory();
        setNamespaceAware(f, true);
        setCoalescing(f, true);
        String strMessage = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
                "<soapenv:Body>" +
                  "<echoRequest xmlns=\"http://test.ibm.com/xsd\">" +
                    "<arg0>outside cdata <![CDATA[<data>inside cdata</data>]]></arg0>" +
                  "</echoRequest>" +
                "</soapenv:Body>" +
              "</soapenv:Envelope>";                  
          XMLStreamReader reader = f.createXMLStreamReader(new ByteArrayInputStream(strMessage.getBytes("UTF-8")));
          assertTokenType(START_ELEMENT, reader.next());
          assertTokenType(START_ELEMENT, reader.next());
          assertTokenType(START_ELEMENT, reader.next());
          assertTokenType(START_ELEMENT, reader.next());
          // since we are coalescing, must be reported as CHARACTERS, not CDATA
          assertTokenType(CHARACTERS, reader.next());
          String cdata = new String(reader.getTextCharacters(), reader.getTextStart(), reader.getTextLength());
          assertEquals("outside cdata <data>inside cdata</data>", cdata);
    }

    // Unit test for [WSTX-299]: use of "exotic" URLs
    public void testCustomSystemIds() throws Exception
    {
        XMLInputFactory2 f = getNewInputFactory();
        setCoalescing(f, true);
        // important: must prevent access of DTD
        setSupportDTD(f, false);
        final String sysId = "foobar:slartibartfast";

        String DOC = "<!DOCTYPE root SYSTEM '"+sysId+"'><root>stuff</root>";
        XMLStreamReader2 reader = (XMLStreamReader2) f.createXMLStreamReader(new StringReader(DOC));
        assertTokenType(DTD, reader.next());

        DTDInfo dtd = reader.getDTDInfo();
        assertNotNull(dtd);
        assertEquals(sysId, dtd.getDTDSystemId());

        assertTokenType(START_ELEMENT, reader.next());
        assertEquals("root", reader.getLocalName());
        assertTokenType(CHARACTERS, reader.next());
        assertEquals("stuff", reader.getText());
        assertTokenType(END_ELEMENT, reader.next());
        assertEquals("root", reader.getLocalName());
        reader.close();
    }

    // Another test for [WSTX-299]
    public void testCustomSystemIdWithResolver() throws Exception
    {
        XMLInputFactory2 f = getNewInputFactory();
        setCoalescing(f, true);
        setSupportDTD(f, true);
        final String sysId = "foobar:slartibartfast";
        final String EXTERNAL_DTD = "<!ENTITY foo 'bar'>";
        f.setProperty(XMLInputFactory.RESOLVER,
                new ExoticResolver(sysId, EXTERNAL_DTD));

        String DOC = "<!DOCTYPE root SYSTEM '"+sysId+"'><root>&foo;</root>";
        XMLStreamReader2 reader = (XMLStreamReader2) f.createXMLStreamReader(new StringReader(DOC));
        assertTokenType(DTD, reader.next());

        DTDInfo dtd = reader.getDTDInfo();
        assertNotNull(dtd);
        assertEquals(sysId, dtd.getDTDSystemId());

        assertTokenType(START_ELEMENT, reader.next());
        assertEquals("root", reader.getLocalName());
        assertTokenType(CHARACTERS, reader.next());
        assertEquals("bar", reader.getText());
        assertTokenType(END_ELEMENT, reader.next());
        assertEquals("root", reader.getLocalName());
        reader.close();
    }

    /*
    ///////////////////////////////////////////////////////////
    // Helper classes
    ///////////////////////////////////////////////////////////
     */

    final static class ExoticResolver implements XMLResolver
    {
        protected final String sysId, dtd;

        public ExoticResolver(String sysId, String dtd) {
            this.sysId = sysId;
            this.dtd = dtd;
        }

        @Override
        public Object resolveEntity(String publicID, String systemID, String baseURI, String namespace)
        {
            if (sysId.equals(systemID)) {
                return dtd;
            }
            return null;
        }
    }
}
