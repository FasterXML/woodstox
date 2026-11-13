package wstxtest.msv;

import javax.xml.stream.*;

import org.codehaus.stax2.*;
import org.codehaus.stax2.validation.*;

import wstxtest.vstream.BaseValidationTest;
import org.junit.jupiter.api.Test;

/**
 * Tests that text which {@link XMLStreamReader#getElementText} and the
 * Stax2 typed accessors coalesce on their own (CDATA following text, rest
 * of a text segment that crosses an input buffer boundary) is passed to
 * the validator, and not just the part {@link XMLStreamReader#next}
 * had read.
 */
public class TestW3CSchemaCoalescedText
    extends BaseValidationTest
{
    final static String SCHEMA_INT =
        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>"
        +"<xs:element name='v' type='xs:int'/>"
        +"</xs:schema>";

    final static String SCHEMA_DIGITS =
        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>"
        +"<xs:element name='v'><xs:simpleType><xs:restriction base='xs:string'>"
        +"<xs:pattern value='[0-9\\s]+'/>"
        +"</xs:restriction></xs:simpleType></xs:element>"
        +"</xs:schema>";

    final static String SCHEMA_MAX_100 =
        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>"
        +"<xs:element name='v'><xs:simpleType><xs:restriction base='xs:int'>"
        +"<xs:maxInclusive value='100'/>"
        +"</xs:restriction></xs:simpleType></xs:element>"
        +"</xs:schema>";

    final static String SCHEMA_LIST_MAX_100 =
        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>"
        +"<xs:element name='v'><xs:simpleType><xs:list><xs:simpleType>"
        +"<xs:restriction base='xs:int'><xs:maxInclusive value='100'/></xs:restriction>"
        +"</xs:simpleType></xs:list></xs:simpleType></xs:element>"
        +"</xs:schema>";

    // Long enough to cross the first input buffer load
    final static String ZEROS = repeat('0', 4000);

    @Test
    public void testGetElementTextWithCData() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_INT);
        assertEquals("123", getElementText(schema, "<v>1<![CDATA[2]]>3</v>"));
        verifyInvalid(schema, "<v>12<![CDATA[abc]]></v>", "\"int\" type", new TextAccess() {
            @Override
            public void read(XMLStreamReader2 sr) throws XMLStreamException {
                sr.getElementText();
            }
        });
    }

    @Test
    public void testGetElementTextWithLongText() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_DIGITS);
        String digits = repeat('1', 1000);
        assertEquals(digits, getElementText(schema, "<v>"+digits+"</v>"));
        verifyInvalid(schema, "<v>"+digits+"abc</v>", "regular expression", new TextAccess() {
            @Override
            public void read(XMLStreamReader2 sr) throws XMLStreamException {
                sr.getElementText();
            }
        });
    }

    // Text copied off the input buffer before validation must not hide CDATA
    // coalesced after it
    @Test
    public void testGetElementTextWithCopiedTextAndCData() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_DIGITS);
        assertEquals("1\n23", getElementText(schema, "<v>1\r\n2<![CDATA[3]]></v>"));
        verifyInvalid(schema, "<v>1\r\n2<![CDATA[x]]></v>", "regular expression", new TextAccess() {
            @Override
            public void read(XMLStreamReader2 sr) throws XMLStreamException {
                sr.getElementText();
            }
        });
    }

    @Test
    public void testGetElementAsIntWithLongText() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_MAX_100);
        TextAccess asInt = new TextAccess() {
            @Override
            public void read(XMLStreamReader2 sr) throws XMLStreamException {
                sr.getElementAsInt();
            }
        };
        XMLStreamReader2 sr = startReader(schema, "<v>"+ZEROS+"50</v>");
        assertEquals(50, sr.getElementAsInt());
        verifyInvalid(schema, "<v>"+ZEROS+"50000</v>", "out of the range", asInt);
        // first segment complete, rest coalesced after the comment
        verifyInvalid(schema, "<v>0<!--x-->"+ZEROS+"50000</v>", "out of the range", asInt);
    }

    @Test
    public void testReadElementAsIntArrayWithLongText() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_LIST_MAX_100);
        verifyInvalid(schema, "<v>1 "+ZEROS+"500</v>", "out of the range", new TextAccess() {
            @Override
            public void read(XMLStreamReader2 sr) throws XMLStreamException {
                int[] result = new int[10];
                while (sr.readElementAsIntArray(result, 0, result.length) > 0) { }
            }
        });
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Helper methods
    ///////////////////////////////////////////////////////////////////////
     */

    interface TextAccess {
        void read(XMLStreamReader2 sr) throws XMLStreamException;
    }

    private XMLStreamReader2 startReader(XMLValidationSchema schema, String xml)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = constructStreamReader(getInputFactory(), xml);
        sr.validateAgainst(schema);
        assertTokenType(START_ELEMENT, sr.next());
        return sr;
    }

    private String getElementText(XMLValidationSchema schema, String xml)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = startReader(schema, xml);
        String text = sr.getElementText();
        assertTokenType(END_ELEMENT, sr.getEventType());
        assertTokenType(END_DOCUMENT, sr.next());
        sr.close();
        return text;
    }

    private void verifyInvalid(XMLValidationSchema schema, String xml, String failPhrase,
            TextAccess access)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = startReader(schema, xml);
        try {
            access.read(sr);
            while (sr.hasNext()) {
                sr.next();
            }
            fail("Expected validation exception for: "+xml.substring(0, Math.min(40, xml.length())));
        } catch (XMLValidationException vex) {
            String msg = String.valueOf(vex.getMessage());
            if (!msg.contains(failPhrase)) {
                fail("Expected validation exception containing '"+failPhrase+"', got: "+msg);
            }
        }
        sr.close();
    }

    private static String repeat(char c, int count)
    {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; ++i) {
            sb.append(c);
        }
        return sb.toString();
    }
}
