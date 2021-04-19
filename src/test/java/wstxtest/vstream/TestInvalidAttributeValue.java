package wstxtest.vstream;

import stax2.BaseStax2Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.*;

import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.validation.ValidationProblemHandler;
import org.codehaus.stax2.validation.XMLValidationException;
import org.codehaus.stax2.validation.XMLValidationProblem;
import org.codehaus.stax2.validation.XMLValidationSchema;
import org.codehaus.stax2.validation.XMLValidationSchemaFactory;

public class TestInvalidAttributeValue 
    extends BaseStax2Test
{
    public void testInvalidAttributeValue() throws Exception
    {
        final String DOC = "<root note='note' verbose='yes'/>\n";

        final String INPUT_DTD =
"<!ELEMENT root ANY>\n"
+"<!ATTLIST root note CDATA #IMPLIED>\n"
;

        XMLInputFactory f = getInputFactory();
        setCoalescing(f, true);

        XMLValidationSchemaFactory schemaFactory =
                XMLValidationSchemaFactory.newInstance(XMLValidationSchema.SCHEMA_ID_DTD);
        XMLValidationSchema schema = schemaFactory.createSchema(new StringReader(INPUT_DTD));
        XMLStreamReader2 sr = (XMLStreamReader2)f.createXMLStreamReader(
                new StringReader(DOC));

        final List<XMLValidationProblem> probs = new ArrayList<XMLValidationProblem>();
        
        sr.validateAgainst(schema);
        sr.setValidationProblemHandler(new ValidationProblemHandler() {
            @Override
            public void reportProblem(XMLValidationProblem problem)
                    throws XMLValidationException {
                probs.add(problem);
            }
        });

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());

        final String verboseValue = sr.getAttributeValue(null, "verbose");

        assertEquals("yes", verboseValue);
    }
}
