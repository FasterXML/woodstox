package wstxtest.msv;

import javax.xml.stream.*;

import org.codehaus.stax2.*;
import org.codehaus.stax2.validation.*;

import wstxtest.vstream.BaseValidationTest;

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

    // // // First 'int' datatype

    public void testSimpleValidInt() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_INT);
        XMLStreamReader2 sr = getReader("<price>129</price>");
        sr.validateAgainst(schema);
        streamThrough(sr);
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
        XMLStreamReader2 sr = getReader("<price>1.00</price>");
        sr.validateAgainst(schema);
        streamThrough(sr);
    }

    public void testSimpleInvalidFloat() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_FLOAT);
        verifyFailure("<price>abc</price>", schema, "invalid 'float' value",
                      "does not satisfy the \"float\" type");
    }

    // // // Writing

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
