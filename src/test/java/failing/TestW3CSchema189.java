package failing;

import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.validation.XMLValidationSchema;

import wstxtest.msv.TestW3CSchema;

/**
 */
public class TestW3CSchema189
    extends TestW3CSchema
{

    /**
     * A reproducer for https://github.com/FasterXML/woodstox/issues/189
     * Move to {@link TestW3CSchema} once fixed.
     */
    public void testSimpleNonNsUndefinedIdWriter189() throws XMLStreamException
    {
        XMLValidationSchema schema = parseW3CSchema(SIMPLE_NON_NS_SCHEMA);
        String XML = "<personnel><person id='a1'>"
            + "<name><family>F</family><given>G</given>"
            + "</name><link manager='m3' /></person></personnel>";
        verifyFailure(XML, schema, "undefined referenced id ('m3')",
                      "Undefined ID 'm3'", true, false, true);
    }

}
