package failing;

import javax.xml.stream.*;

import org.codehaus.stax2.*;
import org.codehaus.stax2.validation.*;

import wstxtest.vstream.BaseValidationTest;

/**
 * Test for XML Schema value constraints (default, required) for
 * elements and attributes.
 */
public class TestW3CDefaultValues
    extends BaseValidationTest
{
    final static String SCHEMA_WITH_DEFAULTS =
        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>\n"
        + "<xs:element name='price' default='10'>\n"
        + " <xs:complexType>\n"
        + "   <xs:simpleContent>\n"
        + "     <xs:extension base='xs:int'>"
        + "       <xs:attribute name='currency' type='xs:string' default='USD' />"
        + "     </xs:extension>"
        + "   </xs:simpleContent>\n"
        + " </xs:complexType>\n"
        + "</xs:element>\n"
        +"</xs:schema>"
        ;

    final static String SCHEMA_WITH_REQUIRED = "";
    
    public void testAttributeDefault() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_WITH_DEFAULTS);
        XMLStreamReader2 sr = getReader("<price currency='FIM'>129</price>");
        sr.validateAgainst(schema);
        // first: if explicitly defined, should show that value
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("price", sr.getLocalName());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("FIM", sr.getAttributeValue(null, "currency"));
        sr.close();

        // then, if missing, default to given default
        sr = getReader("<price>129</price>");
        sr.validateAgainst(schema);
        // first: if explicitly defined, should show that value
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("price", sr.getLocalName());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("USD", sr.getAttributeValue(null, "currency"));
        sr.close();
    }

    public void testElementDefault() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_WITH_DEFAULTS);
        XMLStreamReader2 sr = getReader("<price>129</price>");
        sr.validateAgainst(schema);
        // first: if explicitly defined, should show that value
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("price", sr.getLocalName());
        assertEquals("129", sr.getElementText());
        assertTokenType(END_ELEMENT, sr.getEventType());
        sr.close();

        // then, if missing, default to given default
        sr = getReader("<price />");
        sr.validateAgainst(schema);
        // first: if explicitly defined, should show that value
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("price", sr.getLocalName());
        assertEquals("10", sr.getElementText());
        assertTokenType(END_ELEMENT, sr.getEventType());
        sr.close();
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
    
}
