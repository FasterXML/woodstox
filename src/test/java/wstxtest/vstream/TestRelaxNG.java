package wstxtest.vstream;

import java.io.StringWriter;

import javax.xml.stream.*;

import org.codehaus.stax2.*;
import org.codehaus.stax2.validation.*;

import com.ctc.wstx.sw.NonNsStreamWriter;
import com.ctc.wstx.sw.RepairingNsStreamWriter;
import com.ctc.wstx.sw.SimpleNsStreamWriter;

/**
 * This is a simple base-line "smoke test" that checks that RelaxNG
 * validation works at least minimally.
 */
public class TestRelaxNG
    extends BaseValidationTest
{
    protected final static String SIMPLE_RNG_SCHEMA =
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

    /**
     * Test validation against a simple document valid according
     * to a simple RNG schema.
     */
    public void testSimpleNonNs()
        throws XMLStreamException
    {
        String XML =
            "<?xml version='1.0'?>"
            +"<dict>\n"
            +" <term type=\"name\">\n"
            +"  <word>foobar</word>\n"
            +"  <description>Foo Bar</description>\n"
            +" </term>"
            +" <term type=\"word\" extra=\"123\">\n"
            +"  <word>fuzzy</word>\n"
            +"  <description>adjective</description>\n"
            +" </term>"
            +"</dict>"
            ;

        XMLValidationSchema schema = parseRngSchema(SIMPLE_RNG_SCHEMA);
        for (ValidationMode mode : ValidationMode.values()) {
            mode.validate(schema, XML);
        }
    }

    /**
     * This unit test checks for couple of simple validity problems
     * against the simple rng schema. It does not use namespaces
     * (a separate test is needed for ns handling).
     */
    public void testInvalidNonNs()
        throws XMLStreamException
    {
        XMLValidationSchema schema = parseRngSchema(SIMPLE_RNG_SCHEMA);

        // First, wrong root element:
        String XML = "<term type='x'>\n"
            +"  <word>foobar</word>\n"
            +"  <description>Foo Bar</description>\n"
            +"</term>";
        verifyFailure(XML, schema, "wrong root element",
                         "is not allowed. Possible tag names are");

        // Then, wrong child ordering:
        XML = "<dict>\n"
            +"<term type='x'>\n"
            +"  <description>Foo Bar</description>\n"
            +"  <word>foobar</word>\n"
            +"</term></dict>";
        verifyFailure(XML, schema, "illegal child element ordering",
                         "tag name \"description\" is not allowed. Possible tag names are");

        // Then, missing children:
        XML = "<dict>\n"
            +"<term type='x'>\n"
            +"</term></dict>";
        verifyFailure(XML, schema, "missing children",
                         "uncompleted content model. expecting: <word>");

        XML = "<dict>\n"
            +"<term type='x'>\n"
            +"<word>word</word>"
            +"</term></dict>";
        verifyFailure(XML, schema, "incomplete children",
                         "uncompleted content model. expecting: <description>");

        // Then illegal text in non-mixed element
        XML = "<dict>\n"
            +"<term type='x'>No text allowed here"
            +"  <word>foobar</word>\n"
            +"  <description>Foo Bar</description>\n"
            +"</term></dict>";
        verifyFailure(XML, schema, "invalid non-whitespace text",
                         "Element <term> has non-mixed content specification; can not contain non-white space text");

        // missing attribute
        XML = "<dict>\n"
            +"<term>"
            +"  <word>foobar</word>\n"
            +"  <description>Foo Bar</description>\n"
            +"</term></dict>";
        // Then undeclared attributes
        XML = "<dict>\n"
            +"<term attr='value' type='x'>"
            +"  <word>foobar</word>\n"
            +"  <description>Foo Bar</description>\n"
            +"</term></dict>";
        verifyFailure(XML, schema, "undeclared attribute",
                         "unexpected attribute \"attr\"");
        XML = "<dict>\n"
            +"<term type='x'>"
            +"  <word type='noun'>foobar</word>\n"
            +"  <description>Foo Bar</description>\n"
            +"</term></dict>";
        verifyFailure(XML, schema, "undeclared attribute",
                         "unexpected attribute \"type\"");
    }

    public void testSimpleNs()
        throws XMLStreamException
    {
        String XML = "<root>\n"
            +" <myns:leaf xmlns:myns=\"http://test\" attr1=\"123\"/>\n"
            +" <ns2:leaf xmlns:ns2=\"http://test\" ns2:attr2=\"123\"/>\n"
            +"</root>"
            ;

        XMLValidationSchema schema = parseRngSchema(SIMPLE_RNG_NS_SCHEMA);
        for (ValidationMode mode : ValidationMode.values()) {
            mode.validate(schema, XML);
        }
    }

    /**
     * Unit test checks that the namespace matching works as
     * expected.
     */
    public void testInvalidNs()
        throws XMLStreamException
    {
        XMLValidationSchema schema = parseRngSchema(SIMPLE_RNG_NS_SCHEMA);

        // First, wrong root element:
        String XML = "<root xmlns='http://test'>\n"
            +"<leaf />\n"
            +"</root>";
        verifyFailure(XML, schema, "wrong root element",
                         "namespace URI of tag \"root\" is wrong");

        // Wrong child namespace
        XML = "<root>\n"
            +"<leaf xmlns='http://other' />\n"
            +"</root>";
        verifyFailure(XML, schema, "wrong child element namespace",
                         "namespace URI of tag \"leaf\" is wrong.");

        // Wrong attribute namespace
        XML = "<root>\n"
            +"<ns:leaf xmlns:ns='http://test' ns:attr1='123' />\n"
            +"</root>";
        verifyFailure(XML, schema, "wrong attribute namespace",
                         "unexpected attribute \"attr1\"");
    }

    /**
     * This unit test verifies that the validation can be stopped
     * half-way through processing, so that sub-trees (for example)
     * can be validated. In this case, we will verify this functionality
     * by trying to validate invalid document up to the point where it
     * is (or may) still be valid, stop validation, and then continue
     * reading. This should not result in an exception.
     */
    public void testSimplePartialNonNs()
        throws XMLStreamException
    {
        String XML =
            "<?xml version='1.0'?>"
            +"<dict>"
            +"<term type=\"name\"><invalid/>"
            +"</term>"
            +"</dict>"
            ;

        XMLValidationSchema schema = parseRngSchema(SIMPLE_RNG_SCHEMA);

        for (StopValidatingMethod method : StopValidatingMethod.values()) {
            {
                StringWriter writer = new StringWriter();
                SimpleNsStreamWriter sw = (SimpleNsStreamWriter) constructStreamWriter(writer, true, false);
                _testSimplePartialNonNS(XML, schema, sw, writer, method);
            }
            {
                StringWriter writer = new StringWriter();
                RepairingNsStreamWriter sw = (RepairingNsStreamWriter) constructStreamWriter(writer, true, true);
                _testSimplePartialNonNS(XML, schema, sw, writer, method);
            }
            {
                StringWriter writer = new StringWriter();
                NonNsStreamWriter sw = (NonNsStreamWriter) constructStreamWriter(writer, false, false);
                _testSimplePartialNonNS(XML, schema, sw, writer, method);
            }
        }
    }
    
    private enum StopValidatingMethod {schema, validator}

    private void _testSimplePartialNonNS(String XML, XMLValidationSchema schema, XMLStreamWriter2 sw, 
            StringWriter writer, StopValidatingMethod method) throws XMLStreamException {
        XMLStreamReader2 sr = getReader(XML);
        XMLValidator vtor = sr.validateAgainst(schema);
        
        XMLValidator wVtor = sw.validateAgainst(schema);

        sw.copyEventFromReader(sr, false);
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("dict", sr.getLocalName());
        sw.copyEventFromReader(sr, false);
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("term", sr.getLocalName());
        sw.copyEventFromReader(sr, false);

        /* So far so good; but here we'd get an exception... so
         * let's stop validating
         */
        switch (method) {
        case schema:
            assertSame(vtor, sr.stopValidatingAgainst(schema));
            assertSame(wVtor, sw.stopValidatingAgainst(schema));
            break;
        case validator:
            assertSame(vtor, sr.stopValidatingAgainst(vtor));
            assertSame(wVtor, sw.stopValidatingAgainst(wVtor));
            break;
        default:
            throw new IllegalStateException(); 
        }
        try {
            // And should be good to go
            assertTokenType(START_ELEMENT, sr.next());
            assertEquals("invalid", sr.getLocalName());
            sw.copyEventFromReader(sr, false);
            assertTokenType(END_ELEMENT, sr.next());
            assertEquals("invalid", sr.getLocalName());
            sw.copyEventFromReader(sr, false);
            assertTokenType(END_ELEMENT, sr.next());
            assertEquals("term", sr.getLocalName());
            sw.copyEventFromReader(sr, false);
            assertTokenType(END_ELEMENT, sr.next());
            assertEquals("dict", sr.getLocalName());
            sw.copyEventFromReader(sr, false);
            assertTokenType(END_DOCUMENT, sr.next());
            sw.copyEventFromReader(sr, false);
        } catch (XMLValidationException vex) {
            fail("Did not expect validation exception after stopping validation, got: "+vex);
        }
        sr.close();
        sw.close();
        
        assertEquals(XML, writer.toString());
    }

    public void testSimpleEnumAttr()
        throws XMLStreamException
    {
        final String schemaDef =
            "<element xmlns='http://relaxng.org/ns/structure/1.0' name='root'>\n"
            +" <attribute name='enumAttr'>\n"
            +"  <choice>\n"
            +"   <value>value1</value>\n"
            +"   <value>another</value>\n"
            +"   <value>42</value>\n"
            +"  </choice>\n"
            +" </attribute>\n"
            +"</element>"
        ;
        XMLValidationSchema schema = parseRngSchema(schemaDef);

        // First, simple valid document
        String XML = "<root enumAttr=\"another\"/>";
        for (ValidationMode mode : ValidationMode.values()) {
            mode.validate(schema, XML);
        }

        // And then invalid one, with unrecognized value
        XML = "<root enumAttr='421' />";
        verifyFailure(XML, schema, "enum attribute with unknown value",
                         "attribute \"enumAttr\" has a bad value");
    }

    /**
     * Test case for testing handling ID/IDREF/IDREF attributes, using
     * schema datatype library.
     */
    public void testSimpleIdAttrs()
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

        // First, a simple valid document
        String XML = "<root>"
            +" <leaf id=\"first\" ref=\"second\"/>\n"
            +" <leaf id=\"second\" ref=\"third\"/>\n"
            +" <leaf id=\"third\" refs=\"first second third\"/>\n"
            +"</root>"
            ;
        for (ValidationMode mode : ValidationMode.values()) {
            mode.validate(schema, XML);
        }

        // Then one with malformed id
        XML = "<root><leaf id='123invalidid' /></root>";
        verifyFailure(XML, schema, "malformed id",
                         "attribute \"id\" has a bad value");

        // Then with malformed IDREF value (would be valid IDREFS)
        XML = "<root>"
            +" <leaf id='a' ref='a c' />\n"
            +" <leaf id='c' />\n"
            +"</root>"
            ;
        verifyFailure(XML, schema, "malformed id",
                         "attribute \"ref\" has a bad value");

        // And then invalid one, with dangling ref
        XML = "<root>"
            +" <leaf id='a' ref='second' />\n"
            +"</root>"
            ;
        verifyFailure(XML, schema, "reference to undefined id",
                         "Undefined ID", true, true, false);

        // and another one with some of refs undefined
        XML = "<root>"
            +" <leaf refs='this other' id='this' />\n"
            +"</root>"
            ;
        verifyFailure(XML, schema, "reference to undefined id",
                         "Undefined ID", true, true, false);
    }

    public void testSimpleIntAttr()
        throws XMLStreamException
    {
        final String schemaDef =
            "<element xmlns='http://relaxng.org/ns/structure/1.0'"
            +"  datatypeLibrary='http://www.w3.org/2001/XMLSchema-datatypes' name='root'>\n"
            +"  <element name='leaf'>\n"
            +"   <attribute name='nr'><data type='integer' /></attribute>\n"
            +"  </element>\n"
            +"</element>"
        ;

        XMLValidationSchema schema = parseRngSchema(schemaDef);

        // First, a simple valid document
        String XML = "<root><leaf nr=\"  123  \"/></root>";
        for (ValidationMode mode : ValidationMode.values()) {
            mode.validate(schema, XML);
        }

        // Then one with invalid element value
        verifyFailure("<root><leaf nr='12.03' /></root>",
                         schema, "invalid integer attribute value",
                         "does not satisfy the \"integer\" type");
        // And missing attribute
        verifyFailure("<root><leaf /></root>",
                         schema, "missing integer attribute value",
                         "is missing \"nr\" attribute");

        // And then two variations of having empty value
        verifyFailure("<root><leaf nr=\"\"/></root>",
                         schema, "missing integer attribute value",
                         "does not satisfy the \"integer\" type");
        verifyFailure("<root><leaf nr='\r\n'/></root>",
                         schema, "missing integer attribute value",
                         "does not satisfy the \"integer\" type");
    }

    /**
     * Another test, but one that verifies that empty tags do not
     * cause problems with validation
     */
    public void testSimpleBooleanElem2()
        throws XMLStreamException
    {
        final String schemaDef =
            "<element xmlns='http://relaxng.org/ns/structure/1.0'"
            +"  datatypeLibrary='http://www.w3.org/2001/XMLSchema-datatypes' name='root'>\n"
            +"  <element name='leaf1'><text /></element>\n"
            +"  <element name='leaf2'>\n"
            +"   <data type='boolean' />\n"
            +"  </element>\n"
            +"</element>"
        ;

        XMLValidationSchema schema = parseRngSchema(schemaDef);

        // First, a simple valid document
        String XML = "<root><leaf1>abc</leaf1><leaf2>true</leaf2></root>";
        for (ValidationMode mode : ValidationMode.values()) {
            mode.validate(schema, XML);
        }

        // Then another valid, but with empty tag for leaf1
        XML = "<root><leaf1/><leaf2>false</leaf2></root>";
        for (ValidationMode mode : ValidationMode.values()) {
            mode.validate(schema, XML);
        }

        // And then one more invalid case
        verifyFailure("<root><leaf1 /><leaf2>true false</leaf2></root>",
                         schema, "missing boolean element value",
                         "does not satisfy the \"boolean\" type");
    }

    /**
     * And then a test for validating starting when stream points
     * to START_ELEMENT
     */
    public void testPartialValidationOk()
        throws XMLStreamException
    {
        /* Hmmh... RelaxNG does define expected root. So need to
         * wrap the doc...
         */
        String XML =
                "<dummy>\n"
                +"<dict>\n"
                +"<term type=\"name\">\n"
                +"  <word>foobar</word>\n"
                +"  <description>Foo Bar</description>\n"
                +"</term></dict>\n"
                +"</dummy>"
                ;
        XMLValidationSchema schema = parseRngSchema(SIMPLE_RNG_SCHEMA);
        {
            StringWriter writer = new StringWriter();
            SimpleNsStreamWriter sw = (SimpleNsStreamWriter) constructStreamWriter(writer, true, false);
            _testPartialValidationOk(XML, schema, sw, writer);
        }
        {
            StringWriter writer = new StringWriter();
            NonNsStreamWriter sw = (NonNsStreamWriter) constructStreamWriter(writer, false, false);
            _testPartialValidationOk(XML, schema, sw, writer);
        }
    }

    protected void _testPartialValidationOk(String XML, XMLValidationSchema schema, XMLStreamWriter2 sw, StringWriter writer) throws XMLStreamException {
        XMLStreamReader2 sr = getReader(XML);
        assertTokenType(START_ELEMENT, sr.next());
        sw.copyEventFromReader(sr, false);

        sr.validateAgainst(schema);
        sw.validateAgainst(schema);
        while (sr.hasNext()) {
            sr.next();
            sw.copyEventFromReader(sr, false);
        }
        sr.close();
        sw.close();
        
        assertEquals(XML, writer.toString());
    }


    /*
    //////////////////////////////////////////////////////////////
    // Helper methods
    //////////////////////////////////////////////////////////////
     */

    private XMLStreamReader2 getReader(String contents) throws XMLStreamException
    {
        XMLInputFactory2 f = getInputFactory();
        setValidating(f, false);
        return constructStreamReader(f, contents);
    }

}
