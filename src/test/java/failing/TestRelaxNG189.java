package failing;

import javax.xml.stream.*;

import org.codehaus.stax2.validation.*;

import wstxtest.vstream.BaseValidationTest;

/**
 * A reproducer for https://github.com/FasterXML/woodstox/issues/189
 * Move to {@link wstxtest.vstream.TestRelaxNG} once fixed.
 */
public class TestRelaxNG189
    extends BaseValidationTest
{

    /**
     * Test case for testing handling ID/IDREF/IDREF attributes, using
     * schema datatype library.
     */
    public void testSimpleIdAttrsWriter()
        throws XMLStreamException
    {
        final String schemaDef =
            "<element xmlns='http://relaxng.org/ns/structure/1.0'"
            +"  datatypeLibrary='http://www.w3.org/2001/XMLSchema-datatypes' name='root'>\n"
            +" <oneOrMore>\n"
            +"  <element name='leaf'>\n"
            +"   <attribute name='id'><data type='ID' /></attribute>\n"
            +"   <optional>\n"
            +"    <attribute name='ref'><data type='IDREF' /></attribute>\n"
            +"   </optional>\n"
            +"   <optional>\n"
            +"    <attribute name='refs'><data type='IDREFS' /></attribute>\n"
            +"   </optional>\n"
            +"  </element>\n"
            +" </oneOrMore>\n"
            +"</element>"
        ;

        XMLValidationSchema schema = parseRngSchema(schemaDef);

        String XML;

        // And then invalid one, with dangling ref
        XML = "<root>"
            +" <leaf id='a' ref='second' />\n"
            +"</root>"
            ;
        verifyFailure(XML, schema, "reference to undefined id",
                         "Undefined ID", true, false, true);

        // and another one with some of refs undefined
        XML = "<root>"
            +" <leaf refs='this other' id='this' />\n"
            +"</root>"
            ;
        verifyFailure(XML, schema, "reference to undefined id",
                         "Undefined ID", true, false, true);
    }

}
