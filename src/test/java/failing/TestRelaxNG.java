package failing;

import javax.xml.stream.*;

import org.codehaus.stax2.*;
import org.codehaus.stax2.validation.*;

import wstxtest.vstream.BaseValidationTest;

/**
 * This is a simple base-line "smoke test" that checks that RelaxNG
 * validation works at least minimally.
 */
public class TestRelaxNG
    extends BaseValidationTest
{
    final static String SIMPLE_RNG_SCHEMA =
        "<element name='dict' xmlns='http://relaxng.org/ns/structure/1.0'>\n"
        +" <oneOrMore>\n"
        +"  <element name='term'>\n"
        +"   <attribute name='type' />\n"
        +"   <optional>\n"
        +"     <attribute name='extra' />\n"
        +"   </optional>\n"
        +"   <element name='word'><text />\n"
        +"   </element>\n"
        +"   <element name='description'> <text />\n"
        +"   </element>\n"
        +"  </element>\n"
        +" </oneOrMore>\n"
        +"</element>"
        ;

    /**
     * Similar schema, but one that uses namespaces
     */
    final static String SIMPLE_RNG_NS_SCHEMA =
        "<element xmlns='http://relaxng.org/ns/structure/1.0' name='root'>\n"
        +" <zeroOrMore>\n"
        +"  <element name='ns:leaf' xmlns:ns='http://test'>\n"
        +"   <optional>\n"
        +"     <attribute name='attr1' />\n"
        +"   </optional>\n"
        +"   <optional>\n"
        +"     <attribute name='ns:attr2' />\n"
        +"   </optional>\n"
        +"   <text />\n"
        +"  </element>\n"
        +" </zeroOrMore>\n"
        +"</element>"
        ;

    public void testSimpleBooleanElem() throws XMLStreamException
    {
        final String schemaDef =
            "<element xmlns='http://relaxng.org/ns/structure/1.0'"
            +"  datatypeLibrary='http://www.w3.org/2001/XMLSchema-datatypes' name='root'>\n"
            +"  <element name='leaf'>\n"
            +"   <data type='boolean' />\n"
            +"  </element>\n"
            +"</element>"
        ;

        XMLValidationSchema schema = parseRngSchema(schemaDef);

        // First, a simple valid document
        XMLStreamReader2 sr = getReader("<root><leaf>true</leaf></root>");
        sr.validateAgainst(schema);
        while (sr.next() != END_DOCUMENT) { }
        sr.close();

        // Then one with invalid element value
        verifyRngFailure("<root><leaf>foobar</leaf></root>",
                         schema, "invalid boolean element value",
                         "does not satisfy the \"boolean\" type");

        // And one with empty value
	// 12-Nov-2008, TSa: still having MSV bug here, need to suppress failure
        verifyRngFailure("<root><leaf>   </leaf></root>",
                         schema, "missing boolean element value",
                         "does not satisfy the \"boolean\" type", true);

        // And then 2 variations of completely missing value
	// 12-Nov-2008, TSa: still having MSV bug here, need to suppress failure
        verifyRngFailure("<root><leaf></leaf></root>",
                         schema, "missing boolean element value",
                         "does not satisfy the \"boolean\" type", true);

	// 12-Nov-2008, TSa: still having MSV bug here, need to suppress failure
        verifyRngFailure("<root><leaf /></root>",
                         schema, "missing boolean element value",
                         "does not satisfy the \"boolean\" type", true);
    }

    private void verifyRngFailure(String xml, XMLValidationSchema schema, String failMsg, String failPhrase)
        throws XMLStreamException
    {
    	// By default, yes we are strict...
    	verifyRngFailure(xml, schema, failMsg, failPhrase, true);
    }

    private void verifyRngFailure(String xml, XMLValidationSchema schema, String failMsg, String failPhrase,
    		boolean strict) throws XMLStreamException
	{
	      XMLStreamReader2 sr = getReader(xml);
	      sr.validateAgainst(schema);
	      try {
	          while (sr.hasNext()) {
	              /*int type =*/ sr.next();
	          }
	          fail("Expected validity exception for "+failMsg);
	      } catch (XMLValidationException vex) {
	          String origMsg = vex.getMessage();
	          String msg = (origMsg == null) ? "" : origMsg.toLowerCase();
	          if (msg.indexOf(failPhrase.toLowerCase()) < 0) {
			String actualMsg = "Expected validation exception for "+failMsg+", containing phrase '"+failPhrase+"': got '"+origMsg+"'";
			if (strict) {
			    fail(actualMsg);
			}
			warn("suppressing failure due to MSV bug, failure: '"+actualMsg+"'");
	          }
	          // should get this specific type; not basic stream exception
	      } catch (XMLStreamException sex) {
	          fail("Expected XMLValidationException for "+failMsg);
	      }
	  }


    private XMLStreamReader2 getReader(String contents) throws XMLStreamException
    {
        XMLInputFactory2 f = getInputFactory();
        setValidating(f, false);
        return constructStreamReader(f, contents);
    }
}
