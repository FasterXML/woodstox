package wstxtest.vstream;

import stax2.BaseStax2Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.stream.*;

import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.XMLStreamWriter2;
import org.codehaus.stax2.validation.ValidationProblemHandler;
import org.codehaus.stax2.validation.XMLValidationException;
import org.codehaus.stax2.validation.XMLValidationProblem;
import org.codehaus.stax2.validation.XMLValidationSchema;
import org.codehaus.stax2.validation.XMLValidationSchemaFactory;

import com.ctc.wstx.stax.WstxOutputFactory;
import com.ctc.wstx.sw.RepairingNsStreamWriter;
import com.ctc.wstx.sw.SimpleNsStreamWriter;

public class TestDTDErrorCollection104Test
    extends BaseStax2Test
{
    // [woodstox-core#103]
    public void testValidationBeyondUnknownElement() throws Exception
    {
        final String DOC =
                "<map>\n" + 
                "  <val>\n" + 
                "    <prop att=\"product\" val=\"win\" action=\"flag\" color=\"black\"/>\n" +
                "  </val>\n" + 
                "</map>";

        final String INPUT_DTD =
"<!ELEMENT map (notval+)>\n"
+"<!ELEMENT notval EMPTY>\n"
;

        final List<XMLValidationProblem> probs = new ArrayList<XMLValidationProblem>();
        
        XMLInputFactory f = getInputFactory();
        setCoalescing(f, true);

        XMLValidationSchemaFactory schemaFactory =
                XMLValidationSchemaFactory.newInstance(XMLValidationSchema.SCHEMA_ID_DTD);
        XMLValidationSchema schema = schemaFactory.createSchema(new StringReader(INPUT_DTD));
        {
            XMLStreamReader2 sr = (XMLStreamReader2)f.createXMLStreamReader(
                    new StringReader(DOC));
            
            sr.validateAgainst(schema);
            sr.setValidationProblemHandler(new ValidationProblemHandler() {
                @Override
                public void reportProblem(XMLValidationProblem problem)
                        throws XMLValidationException {
                    probs.add(problem);
                }
            });

            assertTokenType(START_ELEMENT, sr.next());
            assertEquals("map", sr.getLocalName());

            sr.next(); // SPACE or CHARACTERS
            assertTokenType(START_ELEMENT, sr.next());
            assertEquals("val", sr.getLocalName());
            assertEquals(1, probs.size());
            assertEquals("Undefined element <val> encountered", 
                    probs.get(0).getMessage());

            sr.next(); // SPACE or CHARACTERS
            assertEquals(1, probs.size());

            // From this point on, however, behavior bit unclear except
            // that for DTD I guess we can at least check for undefined
            // cases....
            
            assertTokenType(START_ELEMENT, sr.next());
            assertEquals("prop", sr.getLocalName());
            // <prop> not defined either so:
            assertEquals(2, probs.size());
            assertEquals("Undefined element <prop> encountered", 
                    probs.get(1).getMessage());

            assertTokenType(END_ELEMENT, sr.next());
            assertEquals("prop", sr.getLocalName());
            assertEquals(2, probs.size());

            sr.next(); // SPACE or CHARACTERS
            assertEquals(2, probs.size());
            assertTokenType(END_ELEMENT, sr.next());
            assertEquals("val", sr.getLocalName());
            assertEquals(2, probs.size());
            
            sr.next(); // SPACE or CHARACTERS
            assertTokenType(END_ELEMENT, sr.next());
            assertEquals("map", sr.getLocalName());
            assertEquals(3, probs.size());
            assertEquals("Validation error, element </map>: Expected at least one element <notval>", 
                    probs.get(2).getMessage());

            // Plus content model now missing <notval>(s)
            assertTokenType(END_DOCUMENT, sr.next());
            assertEquals(3, probs.size());

            sr.close();
        }
        
        // now do the same on the writer side 
        // and make sure that the reported problems are the same

        {
            // SimpleNsStreamWriter
            StringWriter writer = new StringWriter();
            SimpleNsStreamWriter sw = (SimpleNsStreamWriter) stax2.BaseStax2Test.constructStreamWriter(writer, true, false);
            validateWriter(DOC, probs, f, schema, writer, sw);
        }
        {
            // RepairingNsStreamWriter
            StringWriter writer = new StringWriter();
            RepairingNsStreamWriter sw = (RepairingNsStreamWriter) stax2.BaseStax2Test.constructStreamWriter(writer, true, true);
            validateWriter(DOC, probs, f, schema, writer, sw);
        }
    }

}
