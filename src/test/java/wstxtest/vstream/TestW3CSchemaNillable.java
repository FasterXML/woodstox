package wstxtest.vstream;
// ^^^ Move under "wstxtest/msv" once passing

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.*;
import org.codehaus.stax2.validation.*;

import com.ctc.wstx.sw.RepairingNsStreamWriter;
import com.ctc.wstx.sw.SimpleNsStreamWriter;

/**
 * Test whether nillable elements are handled correctly by both reader and writer.
 * A reproducer for <a href="https://github.com/FasterXML/woodstox/issues/179">https://github.com/FasterXML/woodstox/issues/179</a>.
 */
public class TestW3CSchemaNillable
    extends BaseValidationTest
{
    private static final String SCHEMA = 
            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n"
            + "           xmlns=\"http://server.hw.demo/nillable\"\n"
            + "           targetNamespace=\"http://server.hw.demo/nillable\"\n"
            + "           elementFormDefault=\"qualified\" attributeFormDefault=\"qualified\">\n"
            + "  <xs:element name=\"nillableParent\">\n"
            + "    <xs:complexType>\n"
            + "      <xs:sequence>\n"
            + "        <xs:element name=\"nillableDateTime\" type=\"xs:dateTime\" nillable=\"true\" minOccurs=\"0\"/>\n"
            + "        <xs:element name=\"nillableInt\" type=\"xs:int\" nillable=\"true\" minOccurs=\"0\"/>\n"
            + "        <xs:element name=\"nillableString\" type=\"xs:string\" nillable=\"true\"  minOccurs=\"0\"/>\n"
            + "      </xs:sequence>\n"
            + "    </xs:complexType>\n"
            + "  </xs:element>\n"
            + "</xs:schema>";

    // for [woodstox-core#179]
    public void testNillableString() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA);
        final String XML = 
                "<nl:nillableParent xmlns:nl=\"http://server.hw.demo/nillable\">\n"
                + "    <nl:nillableString xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"/>\n"
                + "</nl:nillableParent>";
        // A document with xsi:nil="true" should pass for both reader and writer side validation
        // Reader and SimpleNsStreamWriter validation with xsi:nil="true"
        {
            StringWriter writer = new StringWriter();
            SimpleNsStreamWriter sw = (SimpleNsStreamWriter) constructStreamWriter(writer, true, false);
            _testNillable(XML, schema, true, sw, writer);
        }
        // Reader and RepairingNsStreamWriter validation with xsi:nil="true"
        {
            StringWriter writer = new StringWriter();
            RepairingNsStreamWriter sw = (RepairingNsStreamWriter) constructStreamWriter(writer, true, true);
            _testNillable(XML, schema, true, sw, writer);
        }
    
        // Empty strings are legal
        {
            StringWriter writer = new StringWriter();
            SimpleNsStreamWriter sw = (SimpleNsStreamWriter) constructStreamWriter(writer, true, false);
            _testNillable(XML.replace(" xsi:nil=\"true\"", ""), schema, true, sw, writer);
        }
        {
            StringWriter writer = new StringWriter();
            RepairingNsStreamWriter sw = (RepairingNsStreamWriter) constructStreamWriter(writer, true, true);
            _testNillable(XML.replace(" xsi:nil=\"true\"", ""), schema, true, sw, writer);
        }
    
    }

    // for [woodstox-core#179]
    public void testNillableDateTime() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA);
        final String XML = 
                "<nl:nillableParent xmlns:nl=\"http://server.hw.demo/nillable\">\n"
                + "    <nl:nillableDateTime xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"/>\n"
                + "</nl:nillableParent>";
        
        _testNillable(schema, XML, "nillableDateTime");

    }

    // for [woodstox-core#179]
    public void testNillableInt() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA);
        final String XML = 
                "<nl:nillableParent xmlns:nl=\"http://server.hw.demo/nillable\">\n"
                + "    <nl:nillableInt xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"/>\n"
                + "</nl:nillableParent>";
        
        _testNillable(schema, XML, "nillableInt");

    }

    private void _testNillable(XMLValidationSchema schema, final String XML, String localName) throws XMLStreamException, Exception {
        // A document with xsi:nil="true" should pass for both reader and writer side validation
        // Reader and SimpleNsStreamWriter validation with xsi:nil="true"
        {
            StringWriter writer = new StringWriter();
            SimpleNsStreamWriter sw = (SimpleNsStreamWriter) constructStreamWriter(writer, true, false);
            _testNillable(XML, schema, true, sw, writer);
        }
        // Reader and RepairingNsStreamWriter validation with xsi:nil="true"
        {
            StringWriter writer = new StringWriter();
            RepairingNsStreamWriter sw = (RepairingNsStreamWriter) constructStreamWriter(writer, true, true);
            _testNillable(XML, schema, true, sw, writer);
        }
        
        
        // The same document without xsi:nil="true" must fail for both reader and writer validation
    
        // reader only validation without xsi:nil="true"
        try {
            _testNillable(XML.replace(" xsi:nil=\"true\"", ""), schema, true, null, null);
            fail("Expected a LocalValidationError");
        } catch (XMLValidationException vex) {
            assertMessageContains(vex, "Unknown reason (at end element </nl:"+ localName +">)");
        }
        
        // SimpleNsStreamWriter validation without xsi:nil="true"
        try {
            StringWriter writer = new StringWriter();
            SimpleNsStreamWriter sw = (SimpleNsStreamWriter) constructStreamWriter(writer, true, false);
            _testNillable(XML.replace(" xsi:nil=\"true\"", ""), schema, false, sw, writer);
            fail("Expected a LocalValidationError");
        } catch (XMLValidationException vex) {
            assertMessageContains(vex, "Unknown reason (at end element </nl:"+ localName +">)");
        }
        
        // RepairingNsStreamWriter validation without xsi:nil="true"
        try {
            StringWriter writer = new StringWriter();
            RepairingNsStreamWriter sw = (RepairingNsStreamWriter) constructStreamWriter(writer, true, true);
            _testNillable(XML.replace(" xsi:nil=\"true\"", ""), schema, false, sw, writer);
            fail("Expected a LocalValidationError");
        } catch (XMLValidationException vex) {
            assertMessageContains(vex, "Unknown reason (at end element </nl:"+ localName +">)");
        }
    }

    private void _testNillable(String XML, XMLValidationSchema schema, boolean validateReader, XMLStreamWriter2 sw, StringWriter writer) throws Exception
    {
        XMLStreamReader2 sr = (XMLStreamReader2) getInputFactory().createXMLStreamReader(new StringReader(XML));

        if (validateReader) {
            sr.validateAgainst(schema);
        }

        if (sw != null) {
            sw.validateAgainst(schema);
            sw.copyEventFromReader(sr, false);
        }

        while (sr.hasNext()) {
            sr.next();
            if (sw != null) {
                sw.copyEventFromReader(sr, false);
            }
        }
        sr.close();
        if (sw != null) {
            sw.close();
            assertEquals(XML, writer.toString());
        }
    }

}
