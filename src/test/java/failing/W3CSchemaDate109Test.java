package failing;

import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.validation.XMLValidationException;
import org.codehaus.stax2.validation.XMLValidationSchema;

import wstxtest.vstream.BaseValidationTest;

/**
 * Reproducer for woodstox issue #109: xs:date validation should reject
 * values like "2000-00-00" (month/day out of range), but the shaded MSV
 * xsdlib only checks lexical format, not value-space ranges.
 *<p>
 * Lives under {@code failing/} because the fix requires either an MSV
 * upstream release or a Woodstox-side post-validation hook.
 */
public class W3CSchemaDate109Test
    extends BaseValidationTest
{
    final static String SCHEMA_DATE =
        "<xsd:schema xmlns:xsd='http://www.w3.org/2001/XMLSchema'>\n"
        +"<xsd:simpleType name='ISODate'>\n"
        +"  <xsd:restriction base='xsd:date'/>\n"
        +"</xsd:simpleType>\n"
        +"<xsd:element name='MyDate' type='ISODate'/>\n"
        +"</xsd:schema>";

    public void testValidDate() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_DATE);
        ValidationMode.reader.validate(schema, "<MyDate>2000-01-01</MyDate>");
    }

    public void testInvalidDate_AllZeros() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_DATE);
        try {
            ValidationMode.reader.validate(schema, "<MyDate>2000-00-00</MyDate>");
            fail("Expected validation failure for '2000-00-00'");
        } catch (XMLValidationException e) {
            // expected
        } catch (XMLStreamException e) {
            fail("Expected XMLValidationException; got "
                + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    public void testInvalidDate_Month00() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_DATE);
        try {
            ValidationMode.reader.validate(schema, "<MyDate>2000-00-15</MyDate>");
            fail("Expected validation failure for '2000-00-15' (month 00)");
        } catch (XMLValidationException e) {
            // expected
        }
    }

    public void testInvalidDate_Day00() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_DATE);
        try {
            ValidationMode.reader.validate(schema, "<MyDate>2000-03-00</MyDate>");
            fail("Expected validation failure for '2000-03-00' (day 00)");
        } catch (XMLValidationException e) {
            // expected
        }
    }
}
