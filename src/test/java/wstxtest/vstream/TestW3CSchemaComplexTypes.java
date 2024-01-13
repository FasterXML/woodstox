package wstxtest.vstream;

import java.io.StringWriter;

import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.XMLStreamWriter2;
import org.codehaus.stax2.validation.XMLValidationSchema;

import com.ctc.wstx.sw.NonNsStreamWriter;
import com.ctc.wstx.sw.RepairingNsStreamWriter;
import com.ctc.wstx.sw.SimpleNsStreamWriter;

public class TestW3CSchemaComplexTypes 
    extends BaseValidationTest
{
	/**
	 * For problem with MSV: https://github.com/kohsuke/msv/issues/2
	 *
	 * 29-Mar-2018, tatu: Oddly enough, problem itself allegedly resolved...
	 */
    public void testMSVGithubIssue2() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(
"<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' xmlns:tns='http://MySchema' elementFormDefault='qualified' targetNamespace='http://MySchema' version='1.0'>"
+"<xs:element name='Root' type='tns:Root'/>"
+"<xs:complexType name='Root'>"
+"    <xs:sequence>"
+"      <xs:element minOccurs='0' name='Child' type='xs:anyType'/>"
+"    </xs:sequence>"
+"</xs:complexType>"
+"<xs:complexType abstract='true' name='Child'>"
+"<xs:complexContent>"
+"  <xs:extension base='tns:Base'>"
+"    <xs:sequence/>"
+"  </xs:extension>"
+"</xs:complexContent>"
+"</xs:complexType>"
+"<xs:complexType abstract='true' name='Base'>"
+"<xs:sequence/>"
+"</xs:complexType>"
+"<xs:complexType name='ChildInst'>"
+"<xs:complexContent>"
+"  <xs:extension base='tns:Child'>"
+"    <xs:sequence>"
+"    </xs:sequence>"
+"  </xs:extension>"
+"</xs:complexContent>"
+"</xs:complexType>"
+"</xs:schema>");
        String XML = "<ns11:Root xmlns:ns11=\"http://MySchema\">"
            +"<ns11:Child xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"ns11:ChildInst\">"
            +"</ns11:Child>"
            +"</ns11:Root>";
        
        {
            StringWriter writer = new StringWriter();
            SimpleNsStreamWriter sw = (SimpleNsStreamWriter) constructStreamWriter(writer, true, false);
            _testMSVGithubIssue2(schema, XML, sw, writer);
        }
        {
            StringWriter writer = new StringWriter();
            RepairingNsStreamWriter sw = (RepairingNsStreamWriter) constructStreamWriter(writer, true, true);
            _testMSVGithubIssue2(schema, XML, sw, writer);
        }
    }

    private void _testMSVGithubIssue2(XMLValidationSchema schema, String XML, XMLStreamWriter2 sw, StringWriter writer) throws XMLStreamException {
        XMLStreamReader2 sr = getReader(XML);
        sr.validateAgainst(schema);
        sw.validateAgainst(schema);
        
        
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("Root", sr.getLocalName());
        sw.copyEventFromReader(sr, false);

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("Child", sr.getLocalName());
        sw.copyEventFromReader(sr, false);

        assertTokenType(END_ELEMENT, sr.next());
        sw.copyEventFromReader(sr, false);

        assertTokenType(END_ELEMENT, sr.next());
        sw.copyEventFromReader(sr, false);

        assertTokenType(END_DOCUMENT, sr.next());
        sw.copyEventFromReader(sr, false);
        
        assertTokenType(END_DOCUMENT, sr.getEventType());
        
        sr.close();
        sw.close();
        
        // the writers collapse empty elements
        String expectedXML = XML.replace("></ns11:Child>", "/>");
        assertEquals(expectedXML, writer.toString());
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Helper methods
    ///////////////////////////////////////////////////////////////////////
    */
    
    XMLStreamReader2 getReader(String contents) throws XMLStreamException
    {
        XMLInputFactory2 f = getInputFactory();
        setValidating(f, false);
        return constructStreamReader(f, contents);
    }
}
