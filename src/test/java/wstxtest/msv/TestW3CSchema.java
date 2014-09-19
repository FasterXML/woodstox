package wstxtest.msv;

import javax.xml.stream.*;

import org.codehaus.stax2.*;
import org.codehaus.stax2.validation.*;

import wstxtest.vstream.BaseValidationTest;

/**
 * This is a simple base-line "smoke test" that checks that W3C Schema
 * validation works at least minimally.
 */
public class TestW3CSchema
    extends BaseValidationTest
{
    /**
     * Sample schema, using sample 'personal.xsd' found from the web
     */
    final static String SIMPLE_NON_NS_SCHEMA = "<?xml version='1.0' encoding='UTF-8'?>\n"
            + "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>\n"
            + "<xs:element name='personnel'>\n"
            + "<xs:complexType>\n"
            + "<xs:sequence>\n"
            + "<xs:element ref='person' minOccurs='1' maxOccurs='unbounded'/>\n"
            + "</xs:sequence>\n"
            + "</xs:complexType>\n"
            + "<xs:unique name='unique1'>\n"
            + "<xs:selector xpath='person'/>\n"
            + "<xs:field xpath='name/given'/>\n"
            + "<xs:field xpath='name/family'/>\n"
            + "</xs:unique>\n"
            + "</xs:element>\n"
            + "<xs:element name='person'>\n"
            + "<xs:complexType>\n"
            + "<xs:sequence>\n"
            + "<xs:element ref='name'/>\n"
            + "<xs:element ref='email' minOccurs='0' maxOccurs='unbounded'/>\n"
            + "<xs:element ref='url'   minOccurs='0' maxOccurs='unbounded'/>\n"
            + "<xs:element ref='link'  minOccurs='0' maxOccurs='1'/>\n"
            + "</xs:sequence>\n"
            + "<xs:attribute name='id'  type='xs:ID' use='required'/>\n"
            + "<xs:attribute name='note' type='xs:string'/>\n"
            + "<xs:attribute name='contr' default='false'>\n"
            + "<xs:simpleType>\n"
            + "<xs:restriction base = 'xs:string'>\n"
            + "<xs:enumeration value='true'/>\n"
            + "<xs:enumeration value='false'/>\n"
            + "</xs:restriction>\n"
            + "</xs:simpleType>\n"
            + "</xs:attribute>\n"
            + "<xs:attribute name='salary' type='xs:integer'/>\n"
            + "</xs:complexType>\n"
            + "</xs:element>\n"
            + "<xs:element name='name'>\n"
            + "<xs:complexType>\n"
            + "<xs:all>\n"
            + "<xs:element ref='family'/>\n"
            + "<xs:element ref='given'/>\n"
            + "</xs:all>\n"
            + "</xs:complexType>\n"
            + "</xs:element>\n"
            + "<xs:element name='family' type='xs:string'/>\n"
            + "<xs:element name='given' type='xs:string'/>\n"
            + "<xs:element name='email' type='xs:string'/>\n"
            + "<xs:element name='url'>\n"
            + "<xs:complexType>\n"
            + "<xs:attribute name='href' type='xs:string' default='http://'/>\n"
            + "</xs:complexType>\n"
            + "</xs:element>\n"
            
            + "<xs:element name='link'>\n"
            + "<xs:complexType>\n"
            + "<xs:attribute name='manager' type='xs:IDREF'/>\n"
            + "<xs:attribute name='subordinates' type='xs:IDREFS'/>\n"
            + "</xs:complexType>\n" + "</xs:element>\n" + "</xs:schema>\n";
    
	final static String SIMPLE_XML = "<personnel>"
            + "<person id='a123' contr='true'>" + "    <name>"
            + "<family>Family</family><given>Fred</given>" + "    </name>"
            + "    <url href='urn:something' />" + "  </person>"
            + "  <person id='b12'>"
            + "    <name><family>Blow</family><given>Joe</given>"
            + "    </name>" + "    <url />" + "  </person>" + "</personnel>";

    /**
     * Test validation against a simple document valid according to a very
     * simple W3C schema.
     */
    public void testSimpleNonNs() throws XMLStreamException
    {
        XMLValidationSchema schema = parseW3CSchema(SIMPLE_NON_NS_SCHEMA);
        XMLStreamReader2 sr = getReader(SIMPLE_XML);
        sr.validateAgainst(schema);
        
        try {
            assertTokenType(START_ELEMENT, sr.next());
            assertEquals("personnel", sr.getLocalName());
            
            while (sr.hasNext()) {
                /* int type = */sr.next();
            }
        } catch (XMLValidationException vex) {
            fail("Did not expect validation exception, got: " + vex);
        }
        assertTokenType(END_DOCUMENT, sr.getEventType());
    }
    
    public void testSimplePartialNonNs() throws XMLStreamException
    {
        XMLValidationSchema schema = parseW3CSchema(SIMPLE_NON_NS_SCHEMA);
        XMLStreamReader2 sr = getReader(SIMPLE_XML);
        
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("personnel", sr.getLocalName());
        sr.validateAgainst(schema);
        try {
            assertTokenType(START_ELEMENT, sr.next());
            assertEquals("person", sr.getLocalName());
            while (sr.hasNext()) {
                /* int type = */sr.next();
            }
        } catch (XMLValidationException vex) {
            fail("Did not expect validation exception, got: " + vex);
        }
        assertTokenType(END_DOCUMENT, sr.getEventType());
    }
    
    /**
     * Test validation of a simple document that is invalid according to the
     * simple test schema.
     */
    public void testSimpleNonNsMissingId() throws XMLStreamException
    {
        XMLValidationSchema schema = parseW3CSchema(SIMPLE_NON_NS_SCHEMA);
        String XML = "<personnel><person>"
            + "<name><family>F</family><given>G</given>"
            + "</name></person></personnel>";
        verifyFailure(XML, schema, "missing id attribute",
                      "is missing \"id\" attribute");
    }
    
    public void testSimpleNonNsUndefinedId() throws XMLStreamException
    {
        XMLValidationSchema schema = parseW3CSchema(SIMPLE_NON_NS_SCHEMA);
        String XML = "<personnel><person id='a1'>"
            + "<name><family>F</family><given>G</given>"
            + "</name><link manager='m3' /></person></personnel>";
        verifyFailure(XML, schema, "undefined referenced id ('m3')",
                      "Undefined ID 'm3'");
    }

    public void testSimpleDataTypes() throws XMLStreamException
    {
        // Another sample schema, from
        String SCHEMA = "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>\n"
            + "<xs:element name='item'>\n"
            + " <xs:complexType>\n"
            + "  <xs:sequence>\n"
            + "   <xs:element name='quantity' type='xs:positiveInteger'/>"
            + "   <xs:element name='price' type='xs:decimal'/>"
            + "  </xs:sequence>"
            + " </xs:complexType>"
            + "</xs:element>"
            + "</xs:schema>";
        
        XMLValidationSchema schema = parseW3CSchema(SCHEMA);
        
        // First, valid doc:
        String XML = "<item><quantity>3  </quantity><price>\r\n4.05</price></item>";
        XMLStreamReader2 sr = getReader(XML);
        sr.validateAgainst(schema);
        
        try {
            assertTokenType(START_ELEMENT, sr.next());
            assertEquals("item", sr.getLocalName());
            
            assertTokenType(START_ELEMENT, sr.next());
            assertEquals("quantity", sr.getLocalName());
            String str = sr.getElementText();
            assertEquals("3", str.trim());
            
            assertTokenType(START_ELEMENT, sr.next());
            assertEquals("price", sr.getLocalName());
            str = sr.getElementText();
            assertEquals("4.05", str.trim());
            
            assertTokenType(END_ELEMENT, sr.next());
            assertTokenType(END_DOCUMENT, sr.next());
        } catch (XMLValidationException vex) {
            fail("Did not expect validation exception, got: " + vex);
        }
        sr.close();
        
        // Then invalid (wrong type for value)
        XML = "<item><quantity>34b</quantity><price>1.00</price></item>";
        sr.validateAgainst(schema);
        verifyFailure(XML, schema, "invalid 'positive integer' datatype",
                      "does not satisfy the \"positiveInteger\"");
        sr.close();
        
        // Another invalid, empty value
        XML = "<item><quantity>    </quantity><price>1.00</price></item>";
        sr.validateAgainst(schema);
        // 12-Nov-2008, TSa: still having MSV bug here, need to suppress failure
        verifyFailure(XML, schema, "invalid (missing) positive integer value",
                      "does not satisfy the \"positiveInteger\"", false);
        sr.close();
        
        // Another invalid, missing value
        XML = "<item><quantity></quantity><price>1.00</price></item>";
        sr.validateAgainst(schema);
        // 12-Nov-2008, TSa: still having MSV bug here, need to suppress failure
        verifyFailure(XML, schema, "invalid (missing) positive integer value",
                      "does not satisfy the \"positiveInteger\"", false);
        sr.close();
    }

    public void testSimpleText() throws XMLStreamException
    {
        String SCHEMA = "<?xml version='1.0' encoding='utf-8' ?>\n"
            + "<xs:schema elementFormDefault='qualified' xmlns:xs='http://www.w3.org/2001/XMLSchema'>\n"
            + "<xs:element name='root' type='xs:string' />"
            + "</xs:schema>";
        XMLValidationSchema schema = parseW3CSchema(SCHEMA);
        
        // First, 3 valid docs:
        String XML = "<root>xyz</root>";
        XMLStreamReader2 sr = getReader(XML);
        sr.validateAgainst(schema);
        streamThrough(sr);
        sr.close();
        
        XML = "<root />";
        sr = getReader(XML);
        sr.validateAgainst(schema);
        streamThrough(sr);
        sr.close();
        
        XML = "<root></root>";
        sr = getReader(XML);
        sr.validateAgainst(schema);
        streamThrough(sr);
        sr.close();
        
        // Then invalid?
        XML = "<foobar />";
        sr = getReader(XML);
        sr.validateAgainst(schema);
        verifyFailure(XML, schema, "should warn about wrong root element",
                      "tag name \"foobar\" is not allowed", false);
    }
    
    /**
     * Test for reproducing [WSTX-191]
     */
    public void testConstrainedText() throws XMLStreamException
    {
        String SCHEMA = "<?xml version='1.0' encoding='UTF-8'?>\n"
            + "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' xmlns='http://www.mondomaine.fr/framework'\n"
            + "  targetNamespace='http://www.mondomaine.fr/framework' elementFormDefault='qualified' version='1.2'>\n"
            + " <xs:element name='catalog'>\n"
            + "  <xs:complexType>\n"
            + "   <xs:sequence>\n"
            + "    <xs:element ref='description' minOccurs='1' maxOccurs='5'/>\n"
            + "   </xs:sequence>\n" + "  </xs:complexType>\n"
            + " </xs:element>\n"
            + " <xs:element name='description' nillable='true'>\n"
            + "  <xs:simpleType>\n"
            + "   <xs:restriction base='xs:string'>\n"
            + "    <xs:maxLength value='255'/>\n"
            + "   </xs:restriction>\n" + "  </xs:simpleType>\n"
            + " </xs:element>\n" + "</xs:schema>\n";
        
        XMLValidationSchema schema = parseW3CSchema(SCHEMA);
        
        // first cases where there is text, and 1 to 5 descs
        _testValidDesc(schema, "<description>Du Texte</description>");
        _testValidDesc(
                       schema,
                       "<description>1</description><description>2</description><description>3</description>");
        _testValidDesc(schema,
                       "<description><![CDATA[Du Texte]]></description>");
        _testValidDesc(schema,
                       "<description>??</description><description><![CDATA[...]]></description>");
        _testValidDesc(schema, "<description></description>");
        _testValidDesc(schema, "<description />");
        _testValidDesc(schema, "<description><![CDATA[]]></description>");
    }
    
    private void _testValidDesc(XMLValidationSchema schema, String descSnippet) throws XMLStreamException
    {
        // These should all be valid according to the schema
        String XML = "<catalog xmlns='http://www.mondomaine.fr/framework'>"
            + descSnippet + "</catalog>";
        XMLStreamReader2 sr = getReader(XML);
        sr.validateAgainst(schema);
        streamThrough(sr);
        sr.close();
    }

    public void testValidationHandler() throws XMLStreamException
    {
        String SCHEMA = "<?xml version='1.0' encoding='utf-8' ?>\n"
            + "<xs:schema elementFormDefault='qualified' xmlns:xs='http://www.w3.org/2001/XMLSchema'>\n"
            + "<xs:element name='root' type='xs:string' />"
            + "</xs:schema>";
        XMLValidationSchema schema = parseW3CSchema(SCHEMA);
        
        // Then invalid?
        String XML = "<foobar />";
        XMLStreamReader2 sr = getReader(XML);
        sr.setValidationProblemHandler(new ValidationProblemHandler() {
                
                public void reportProblem(XMLValidationProblem problem)
                    throws XMLValidationException {
                    throw new LocalValidationError(problem);
                    
                }
            });
        sr.validateAgainst(schema);
        boolean threw = false;
        try {
            while (sr.hasNext()) {
                /* int type = */sr.next();
            }
        } catch (LocalValidationError lve) {
            threw = true;
        }
        assertTrue(threw);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Helper methods
    ///////////////////////////////////////////////////////////////////////
    */
    
    XMLStreamReader2 getReader(String contents) throws XMLStreamException
    {
        XMLInputFactory2 f = getInputFactory();
        setValidating(f, false);
        return constructStreamReader(f, contents);
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
    }
}
