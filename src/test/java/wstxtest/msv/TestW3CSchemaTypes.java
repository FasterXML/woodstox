package wstxtest.msv;

import javax.xml.stream.*;

import org.codehaus.stax2.*;
import org.codehaus.stax2.validation.*;

import wstxtest.vstream.BaseValidationTest;
import wstxtest.vstream.BaseValidationTest.ValidationMode;

import java.io.StringWriter;

/**
 * Simple testing of W3C Schema datatypes.
 * Added to test [WSTX-210].
 *
 * @author Tatu Saloranta
 */
public class TestW3CSchemaTypes
    extends BaseValidationTest
{
    final static String SCHEMA_INT =
        "<xsd:schema xmlns:xsd='http://www.w3.org/2001/XMLSchema'>\n"
        +"<xsd:element name='price' type='xsd:int' />"
        +"\n</xsd:schema>"
        ;

    final static String SCHEMA_FLOAT =
        "<xsd:schema xmlns:xsd='http://www.w3.org/2001/XMLSchema'>\n"
        +"<xsd:element name='price' type='xsd:float' />"
        +"\n</xsd:schema>"
        ;

    final static String SCHEMA_ATTRIBUTE =
        "<xsd:schema xmlns:xsd='http://www.w3.org/2001/XMLSchema'>\n"
        +"<xsd:element name='price'>\n"
        +"<xsd:complexType>\n"
        +"<xsd:attribute name='amount' type='xsd:int' use='required'/>\n"
        +"</xsd:complexType>\n"
        +"</xsd:element>\n"
        +"</xsd:schema>"
        ;

    // // // First 'int' datatype

    public void testSimpleValidInt() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_INT);
        for (ValidationMode mode : ValidationMode.values()) {
            mode.validate(schema, "<price>129</price>");
        }
    }

    public void testSimpleInvalidInt() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_INT);
        verifyFailure("<price>abc</price>", schema, "invalid 'int' value",
                      "does not satisfy the \"int\" type");
    }

    // // // Then 'float' datatype

    public void testSimpleValidFloat() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_FLOAT);
        for (ValidationMode mode : ValidationMode.values()) {
            mode.validate(schema, "<price>1.00</price>");
        }
    }

    public void testSimpleInvalidFloat() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_FLOAT);
        verifyFailure("<price>abc</price>", schema, "invalid 'float' value",
                      "does not satisfy the \"float\" type");
    }

    // // // Writing

    public void testValidationWhenWritingAttribute() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_ATTRIBUTE);

        XMLOutputFactory2 f = getOutputFactory();
        final StringWriter stringWriter = new StringWriter();
        XMLStreamWriter2 sw = (XMLStreamWriter2)f.createXMLStreamWriter(stringWriter);
        sw.validateAgainst(schema);

        sw.writeStartElement("price");
        sw.writeIntAttribute(null, null, "amount", 129);
        sw.writeEndElement();
        sw.flush();

        final String XML = stringWriter.toString();
        XMLStreamReader2 sr = getReader(XML);
        sr.validateAgainst(schema);
        streamThrough(sr);
    }

    public void testValdiationWhenWritingCharactersFromArray() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_INT);

        XMLOutputFactory2 f = getOutputFactory();
        XMLStreamWriter2 sw = (XMLStreamWriter2)f.createXMLStreamWriter(new StringWriter());
        sw.validateAgainst(schema);

        String xml = "<price>129</price>";

        sw.writeStartElement("price");
        sw.writeCharacters(xml.toCharArray(), xml.indexOf("1"), 3);
        sw.writeEndElement();
        sw.flush();
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Helper
    ///////////////////////////////////////////////////////////////////////
    */

    XMLStreamReader2 getReader(String contents) throws XMLStreamException
    {
        XMLInputFactory2 f = getInputFactory();
        setValidating(f, false);
        return constructStreamReader(f, contents);
    }
}
