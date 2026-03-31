package failing;
// ^^^ Move under "wstxtest/msv" once passing

import java.io.StringReader;
import java.io.StringWriter;

import org.codehaus.stax2.*;
import org.codehaus.stax2.validation.*;

import wstxtest.vstream.BaseValidationTest;

/**
 * Test whether nillable elements are handled correctly by both reader and writer.
 * A reproducer for <a href="https://github.com/FasterXML/woodstox/issues/179">https://github.com/FasterXML/woodstox/issues/179</a>.
 */
public class W3CSchemaNillable179Test
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
    public void testNillableDateTime() throws Exception
    {
        final String xmlDocument = 
                "<nl:nillableParent xmlns:nl=\"http://server.hw.demo/nillable\">\n"
                + "    <nl:nillableDateTime xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"/>\n"
                + "</nl:nillableParent>";
        testNillable(xmlDocument, true, true);
        
        // The same document without xsi:nil="true" must fail for both reader and writer validation
        try {
            testNillable(xmlDocument.replace(" xsi:nil=\"true\"", ""), true, false);
            fail("Expected a LocalValidationError");
        } catch (LocalValidationError expected) {
            assertEquals("Unknown reason (at end element </nl:nillableDateTime>)", expected.problem.getMessage());
        }
        try{
            testNillable(xmlDocument.replace(" xsi:nil=\"true\"", ""), false, true);
            fail("Expected a LocalValidationError");
        } catch (LocalValidationError expected) {
            assertEquals("Unknown reason (at end element </nl:nillableDateTime>)", expected.problem.getMessage());
        }

    }

    // for [woodstox-core#179]
    public void testNillableInt() throws Exception
    {
        final String xmlDocument = 
                "<nl:nillableParent xmlns:nl=\"http://server.hw.demo/nillable\">\n"
                + "    <nl:nillableInt xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"/>\n"
                + "</nl:nillableParent>";
        testNillable(xmlDocument, true, true);
        
        // The same document without xsi:nil="true" must fail for both reader and writer validation
        try {
            testNillable(xmlDocument.replace(" xsi:nil=\"true\"", ""), true, false);
            fail("Expected a LocalValidationError");
        } catch (LocalValidationError expected) {
            assertEquals("Unknown reason (at end element </nl:nillableInt>)", expected.problem.getMessage());
        }
        try{
            testNillable(xmlDocument.replace(" xsi:nil=\"true\"", ""), false, true);
            fail("Expected a LocalValidationError");
        } catch (LocalValidationError expected) {
            assertEquals("Unknown reason (at end element </nl:nillableInt>)", expected.problem.getMessage());
        }

    }

    // for [woodstox-core#179]
    public void testNillableString() throws Exception
    {
        final String xmlDocument = 
                "<nl:nillableParent xmlns:nl=\"http://server.hw.demo/nillable\">\n"
                + "    <nl:nillableString xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"/>\n"
                + "</nl:nillableParent>";
        testNillable(xmlDocument, true, true);

        // Empty strings are legal
        testNillable(xmlDocument.replace(" xsi:nil=\"true\"", ""), true, false);
        testNillable(xmlDocument.replace(" xsi:nil=\"true\"", ""), false, true);

    }

    void testNillable(String xmlDocument, boolean validateReader, boolean validateWriter) throws Exception
    {
        StringWriter writer = new StringWriter();
        XMLStreamReader2 xmlReader = null;
        XMLStreamWriter2 xmlWriter = null;
        try {
            XMLValidationSchemaFactory schF = XMLValidationSchemaFactory.newInstance(XMLValidationSchema.SCHEMA_ID_W3C_SCHEMA);
            XMLValidationSchema schema = schF.createSchema(new StringReader(SCHEMA));
            XMLInputFactory2 f = getInputFactory();
            setValidating(f, validateReader);

            xmlReader = (XMLStreamReader2) f.createXMLStreamReader(new StringReader(xmlDocument));

            if (validateReader) {
                xmlReader.setValidationProblemHandler(problem -> {
                    throw new LocalValidationError(problem);
                });
                xmlReader.validateAgainst(schema);
            }

            xmlWriter = (XMLStreamWriter2) getOutputFactory().createXMLStreamWriter(writer);
            
            if (validateWriter) {
                xmlWriter.setValidationProblemHandler(problem -> {
                    throw new LocalValidationError(problem);
                });
                xmlWriter.validateAgainst(schema);
            }

            xmlWriter.copyEventFromReader(xmlReader, false);
            while (xmlReader.hasNext()) {
                xmlReader.next();
                xmlWriter.copyEventFromReader(xmlReader, false);
            }
        } finally {
            if (xmlReader != null) {
                xmlReader.close();
            }
            if (xmlWriter != null) {
                xmlWriter.close();
            }
        }
        assertEquals(xmlDocument, writer.toString());
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Helper classes
    ///////////////////////////////////////////////////////////////////////
    */

    public static class LocalValidationError extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        protected XMLValidationProblem problem;

        LocalValidationError(XMLValidationProblem problem) {
            this.problem = problem;
        }

        public XMLValidationProblem getProblem() {
            return problem;
        }

        @Override
        public String toString() {
            return problem.getMessage();
        }
    }
}
