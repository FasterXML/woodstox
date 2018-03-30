package stax2.vwstream;

import java.io.StringWriter;

import org.codehaus.stax2.*;

import org.codehaus.stax2.validation.XMLValidationSchema;

import wstxtest.vstream.BaseValidationTest;

// for [woodstox-core#16]
public class W3CSchemaWrite16Test
    extends BaseValidationTest
{
    final static String SIMPLE_WRITE_SCHEMA =
"<?xml version='1.0' encoding='UTF-8'?>\n"+
"<xs:schema elementFormDefault='qualified'\n"+
"           xmlns:xs='http://www.w3.org/2001/XMLSchema'>\n"+
"    <xs:element name='JobStatus'>\n"+
"        <xs:complexType>\n"+
"            <xs:sequence>\n"+
"                <xs:element name='Document' maxOccurs='unbounded'>\n"+
"                    <xs:complexType>\n"+
"                        <xs:sequence>\n"+
"                            <xs:element name='DocumentId' type='xs:string'/>\n"+
"                        </xs:sequence>\n"+
"                    </xs:complexType>\n"+
"                </xs:element>\n"+
"            </xs:sequence>\n"+
"            <xs:attribute name='xsdVersion' type='xs:string' use='required'/>\n"+
"        </xs:complexType>\n"+
"    </xs:element>\n"+
"</xs:schema>\n"+
"";

    public void testSimpleWriteValidation() throws Exception
    {
        final String XML =
"<JobStatus xsdVersion='NA'>\n"+
"    <Document>\n"+
"        <DocumentId>1234567890</DocumentId>\n"+
"    </Document>\n"+
"    <Document>\n"+
"        <DocumentId>1234567891</DocumentId>\n"+
"    </Document>\n"+
"</JobStatus>\n"
        ;                

        XMLInputFactory2 f = getInputFactory();
        
        XMLValidationSchema schema = parseW3CSchema(SIMPLE_WRITE_SCHEMA);
        XMLStreamReader2 xmlReader = constructStreamReader(f, XML);
        StringWriter strw = new StringWriter();

        XMLStreamWriter2 xmlWriter = (XMLStreamWriter2)
                getOutputFactory().createXMLStreamWriter(strw);
        xmlWriter.validateAgainst(schema);

        xmlWriter.copyEventFromReader(xmlReader, false);

        while (xmlReader.hasNext()) {
            xmlReader.next();

            xmlWriter.copyEventFromReader(xmlReader, true);
        }

        String validatedXML = strw.toString();
        if (validatedXML.indexOf("JobStatus") <= 0) {
            fail("Wrong XML: "+validatedXML);
        }
//System.err.println("XML: "+validatedXML);
    }
}
