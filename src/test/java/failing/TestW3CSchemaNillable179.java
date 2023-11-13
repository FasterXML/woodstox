package failing;
// ^^^ Move under "wstxtest/msv" once passing

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.codehaus.stax2.*;
import org.codehaus.stax2.validation.*;
import org.xml.sax.SAXException;

import wstxtest.vstream.BaseValidationTest;

/**
 * Test whether MSV validator behaves the same w.r.t. nillable elements as javax.xml.validation validator.
 * A reproducer for <a href="https://github.com/FasterXML/woodstox/issues/179">https://github.com/FasterXML/woodstox/issues/179</a>.
 */
public class TestW3CSchemaNillable179
    extends BaseValidationTest
{
    // for [woodstox-core#179]
    public void testNillableDateTime() throws Exception
    {
        /*
<nl:nillableParent xmlns:nl="http://server.hw.demo/nillable">
    <nl:nillableDateTime xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:nil="true"/>
</nl:nillableParent>
         */
        testNillable("wstxtest/msv/nillableDateTime.xml");
    }

    // for [woodstox-core#179]
    public void testNillableInt() throws Exception
    {
        /*
<nl:nillableParent xmlns:nl="http://server.hw.demo/nillable">
    <nl:nillableInt xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:nil="true"/>
</nl:nillableParent>
         */
        testNillable("wstxtest/msv/nillableInt.xml");
    }

    // for [woodstox-core#179]
    public void testNillableString() throws Exception
    {
        /*
<nl:nillableParent xmlns:nl="http://server.hw.demo/nillable">
    <nl:nillableString xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:nil="true"/>
</nl:nillableParent>
         */
        testNillable("wstxtest/msv/nillableString.xml");
    }

    void testNillable(String xmlResource) throws Exception
    {
        boolean woodstoxPassed = true;
        Exception woodstoxE = null;
        // Woodstox
        final String xsdResource = "wstxtest/msv/nillable.xsd";
        {
            XMLValidationSchemaFactory schF = XMLValidationSchemaFactory.newInstance(XMLValidationSchema.SCHEMA_ID_W3C_SCHEMA);
            InputStream schemaInput = getClass().getClassLoader().getResourceAsStream(xsdResource);
            InputStream xmlInput = getClass().getClassLoader().getResourceAsStream(xmlResource);
            try {
                XMLValidationSchema schema = schF.createSchema(schemaInput);
                XMLInputFactory2 f = getInputFactory();
                setValidating(f, false);

                XMLStreamReader2 xmlReader = (XMLStreamReader2) f.createXMLStreamReader(xmlInput);

                /* the validation exception is only thrown from the writer
                xmlReader.setValidationProblemHandler(new ValidationProblemHandler() {
                    @Override
                    public void reportProblem(XMLValidationProblem problem)
                            throws XMLValidationException {
                        throw new LocalValidationError(problem);
                    }
                });
                xmlReader.validateAgainst(schema);
                */

                StringWriter writer = new StringWriter();
                XMLStreamWriter2 xmlWriter = (XMLStreamWriter2) getOutputFactory().createXMLStreamWriter(writer);
                xmlWriter.setValidationProblemHandler(new ValidationProblemHandler() {
                    @Override
                    public void reportProblem(XMLValidationProblem problem)
                            throws XMLValidationException {
                        throw new LocalValidationError(problem);
                    }
                });
                xmlWriter.validateAgainst(schema);

                try {
                    xmlWriter.copyEventFromReader(xmlReader, false);
                    while (xmlReader.hasNext()) {
                        xmlReader.next();
                        xmlWriter.copyEventFromReader(xmlReader, false);
                    }
                } catch (LocalValidationError e) {
                    woodstoxPassed = false;
                    woodstoxE = e;
                }
            } finally {
                if (xmlInput != null) {
                    xmlInput.close();
                }
                if (schemaInput != null) {
                    schemaInput.close();
                }
            }
        }

        // javax.xml.validation
        boolean javaxXmlValidationPassed = true;
        {
            InputStream schemaInput = getClass().getClassLoader().getResourceAsStream(xsdResource);
            InputStream xmlInput = getClass().getClassLoader().getResourceAsStream(xmlResource);
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            try {
                Source schemaFile = new StreamSource(schemaInput);
                Schema schema = factory.newSchema(schemaFile);
                Validator validator = schema.newValidator();
                try {
                    validator.validate(new StreamSource(xmlInput));
                } catch (SAXException e) {
                    javaxXmlValidationPassed = false;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } finally {
                if (xmlInput != null) {
                    xmlInput.close();
                }
                if (schemaInput != null) {
                    schemaInput.close();
                }
            }
        }

        if (woodstoxPassed != javaxXmlValidationPassed) {
            if (woodstoxPassed) {
                fail("Woodstox MSV validator passed"
                        + " but javax.xml.validation validator did not pass"
                        + " for " + xsdResource + " and "+ xmlResource);
                
            } else {
                fail("Woodstox MSV validator did not pass"
                        + " but javax.xml.validation validator passed"
                        + " for " + xsdResource + " and "+ xmlResource
                        +".\nFailure: "+woodstoxE);
            }
        }
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
