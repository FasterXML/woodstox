package failing;

import org.codehaus.stax2.validation.*;

import wstxtest.vstream.BaseValidationTest;

/**
 * Simple testing of W3C Schema datatypes.
 * Added to test [WSTX-210].
 *
 * @author Tatu Saloranta
 */
public class TestW3CSchemaTypes
    extends BaseValidationTest
{
	private final static String SCHEMA_INT =
        "<xsd:schema xmlns:xsd='http://www.w3.org/2001/XMLSchema'>\n"
        +"<xsd:element name='price' type='xsd:int' />"
        +"\n</xsd:schema>"
        ;

    private final static String SCHEMA_FLOAT =
        "<xsd:schema xmlns:xsd='http://www.w3.org/2001/XMLSchema'>\n"
        +"<xsd:element name='price' type='xsd:float' />"
        +"\n</xsd:schema>"
        ;

    // // // First 'int' datatype

    public void testSimpleMissingInt() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_INT);
        verifyFailure("<price></price>", schema, "missing 'int' value",
                      "does not satisfy the \"int\" type");
    }

    public void testSimpleMissingFloat() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_FLOAT);
        verifyFailure("<price></price>", schema, "missing 'float' value",
                      "Undefined ID 'm3'");
    }
}
