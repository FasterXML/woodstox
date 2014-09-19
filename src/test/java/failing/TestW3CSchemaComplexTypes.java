package failing;

import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.validation.XMLValidationException;
import org.codehaus.stax2.validation.XMLValidationSchema;

import wstxtest.vstream.BaseValidationTest;

public class TestW3CSchemaComplexTypes 
    extends BaseValidationTest
{
	/**
	 * For problem with MSV: https://github.com/kohsuke/msv/issues/2
	 * 
	 */
    public void testGithubIssue2() throws Exception
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
        XMLStreamReader2 sr = getReader("<ns11:Root xmlns:ns11='http://MySchema'>"
            +"<ns11:Child xsi:type='ns11:ChildInst' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'>"
            +"</ns11:Child>"
            +"</ns11:Root>");
        sr.validateAgainst(schema);
        
        try {
            assertTokenType(START_ELEMENT, sr.next());
            assertEquals("Root", sr.getLocalName());
            assertTokenType(START_ELEMENT, sr.next());
            assertEquals("Child", sr.getLocalName());
            assertTokenType(END_ELEMENT, sr.next());
            assertTokenType(END_ELEMENT, sr.next());
            assertTokenType(END_DOCUMENT, sr.next());
        } catch (XMLValidationException vex) {
            fail("Did not expect validation exception, got: " + vex);
        }
        assertTokenType(END_DOCUMENT, sr.getEventType());        
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
