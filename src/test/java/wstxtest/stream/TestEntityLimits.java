package wstxtest.stream;

import java.io.StringReader;

import javax.xml.stream.*;

import com.ctc.wstx.api.WstxInputProperties;
import org.junit.jupiter.api.Test;

/**
 * Tests that verify that it is possible to limit aspects of general parsed
 * entity handling: specifically, total number of expansions per document,
 * and maximum nesting depth of entity expansion.
 * 
 * @since 4.3
 */
public class TestEntityLimits
    extends BaseStreamTest
{
    @Test
    public void testMaxEntityNesting() throws XMLStreamException
    {
        String XML = "<!DOCTYPE root [\n"
                +" <!ENTITY top '&middle;'>\n"
                +" <!ENTITY middle '&bottom;'>\n"
                +" <!ENTITY bottom 'yay!'>\n"
                +"]><root>&top;</root>"
               ;

        // First: with default limits (high), should be fine
        XMLInputFactory f = getNewInputFactory();
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader(XML));
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();

        // and with max depth of 3 as well
        f.setProperty(WstxInputProperties.P_MAX_ENTITY_DEPTH, 3);
        sr = f.createXMLStreamReader(new StringReader(XML));
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();

        // but not with 2
        f.setProperty(WstxInputProperties.P_MAX_ENTITY_DEPTH, 2);
        sr = f.createXMLStreamReader(new StringReader(XML));
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        try {
            sr.next();
            fail("Should have failed with entity depth limit extension");
        } catch (XMLStreamException e) {
            verifyException(e, "Maximum entity expansion depth");
        }
        sr.close();
    }

    @Test
    public void testMaxEntityExpansionCount() throws XMLStreamException
    {
        String XML = "<!DOCTYPE root [\n"
                +" <!ENTITY top '&middle; &middle; &middle; &middle;'>\n"
                +" <!ENTITY middle '&bottom; &bottom; &bottom; &bottom;'>\n"
                +" <!ENTITY bottom 'yay!'>\n"
                +"]><root>&top;</root>"
               ;
        
        // expands to 16 segments, via 21 expansions (1 -> 4 -> 16)

        // fine with default settings
        XMLInputFactory f = getNewInputFactory();
        setCoalescing(f, true);
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader(XML));
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();

        // and with max set to 21 expansions
        f.setProperty(WstxInputProperties.P_MAX_ENTITY_COUNT, 21);
        sr = f.createXMLStreamReader(new StringReader(XML));
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());
        sr.getText();
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();

        // but not with one less
        f.setProperty(WstxInputProperties.P_MAX_ENTITY_COUNT, 20);
        sr = f.createXMLStreamReader(new StringReader(XML));
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        try {
            sr.next();
            assertTokenType(CHARACTERS, sr.getEventType());
            // may require either reading of content (getText()) or advancing to next;
            // in former case, will get lazily thrown exception unfortunately so:
            sr.next();
            fail("Should have failed with entity count limit extension");
        } catch (XMLStreamException e) {
            verifyException(e, "Maximum entity expansion count");
        }
        sr.close();
    }

    // Parameter entity references in an entity value are expanded into
    // the value, so doubling a value takes 2 expansions per level and
    // P_MAX_ENTITY_COUNT never trips
    @Test
    public void testMaxEntityValueSize() throws XMLStreamException
    {
        // 16 chars doubled 3 times: 128 chars
        String XML = doublingPEs(3);
        XMLInputFactory f = getNewInputFactory();
        f.setProperty(WstxInputProperties.P_MAX_ENTITY_VALUE_SIZE, 128);
        streamThrough(f.createXMLStreamReader(new StringReader(XML)));

        f.setProperty(WstxInputProperties.P_MAX_ENTITY_VALUE_SIZE, 127);
        verifyLimitFail(f, XML, "Maximum entity value size");

        // and with default limit: 16 chars doubled 16 times, 1M chars
        verifyLimitFail(getNewInputFactory(), doublingPEs(16),
                "Maximum entity value size");
    }

    // Attribute default values in DTD must obey P_MAX_ATTRIBUTE_SIZE
    // just like attribute values in content
    @Test
    public void testMaxAttributeSizeOfDefault() throws XMLStreamException
    {
        String XML = "<!DOCTYPE root [\n"
                +" <!ENTITY a '0123456789'>\n"
                +" <!ENTITY b '&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;'>\n"
                +" <!ENTITY c '&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;'>\n"
                +" <!ATTLIST root attr CDATA '&c;'>\n"
                +"]><root />"
               ;
        // default value is 1000 chars
        XMLInputFactory f = getNewInputFactory();
        f.setProperty(WstxInputProperties.P_MAX_ATTRIBUTE_SIZE, 1000);
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader(XML));
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(1000, sr.getAttributeValue(0).length());
        sr.close();

        f.setProperty(WstxInputProperties.P_MAX_ATTRIBUTE_SIZE, 999);
        verifyLimitFail(f, XML, "Maximum attribute size");
    }

    private String doublingPEs(int levels)
    {
        StringBuilder sb = new StringBuilder("<!DOCTYPE root [\n");
        sb.append(" <!ENTITY % e0 '0123456789abcdef'>\n");
        for (int i = 1; i <= levels; ++i) {
            sb.append(" <!ENTITY % d").append(i)
                .append(" \"<!ENTITY &#37; e").append(i)
                .append(" '&#37;e").append(i-1).append(";&#37;e").append(i-1)
                .append(";'>\"> %d").append(i).append(";\n");
        }
        return sb.append("]><root />").toString();
    }

    private void verifyLimitFail(XMLInputFactory f, String XML, String msg)
        throws XMLStreamException
    {
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader(XML));
        try {
            while (sr.hasNext()) {
                sr.next();
            }
            fail("Should have failed with limit: "+msg);
        } catch (XMLStreamException e) {
            verifyException(e, msg);
        }
        sr.close();
    }
}
