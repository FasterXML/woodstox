package wstxtest.stream;

import java.io.StringReader;

import javax.xml.stream.*;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.api.WstxInputProperties;
import org.junit.jupiter.api.Test;

/**
 * Tests that verify that it is possible to limit aspects of general parsed
 * entity handling: specifically, total number of expansions per document,
 * maximum nesting depth of entity expansion, and total length of
 * expanded content.
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

    @Test
    public void testMaxEntityExpansionLengthDefault() throws XMLStreamException
    {
        XMLInputFactory f = getNewInputFactory();
        assertEquals("Default max entity expansion length",
                Long.valueOf(ReaderConfig.DEFAULT_MAX_ENTITY_EXPANSION_LENGTH),
                f.getProperty(WstxInputProperties.P_MAX_ENTITY_EXPANSION_LENGTH));
    }

    @Test
    public void testMaxEntityExpansionLength() throws XMLStreamException
    {
        String XML = "<!DOCTYPE root [\n"
                +" <!ENTITY top '&middle;&middle;&middle;&middle;'>\n"
                +" <!ENTITY middle '&bottom;&bottom;&bottom;&bottom;'>\n"
                +" <!ENTITY bottom '0123456789'>\n"
                +"]><root>&top;</root>"
               ;
        // expands to 160 chars of text (plus entity reference text in between)

        // fine with default settings
        XMLInputFactory f = getNewInputFactory();
        setCoalescing(f, true);
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader(XML));
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(160, sr.getElementText().length());
        sr.close();

        // and with a limit comfortably above the expanded size
        f.setProperty(WstxInputProperties.P_MAX_ENTITY_EXPANSION_LENGTH, 1000L);
        sr = f.createXMLStreamReader(new StringReader(XML));
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(160, sr.getElementText().length());
        sr.close();

        // but not with a limit below it
        f.setProperty(WstxInputProperties.P_MAX_ENTITY_EXPANSION_LENGTH, 100L);
        sr = f.createXMLStreamReader(new StringReader(XML));
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        try {
            sr.getElementText();
            fail("Should have failed with entity expansion length limit");
        } catch (XMLStreamException e) {
            verifyException(e, "Maximum entity expansion length");
        }
        sr.close();
    }

    /**
     * Parameter entity references within entity values (allowed in the external
     * DTD subset) are expanded when the DTD is read, so the limit needs to
     * apply to DTD processing as well.
     */
    @Test
    public void testMaxEntityExpansionLengthInDTD() throws XMLStreamException
    {
        final String XML = "<!DOCTYPE root SYSTEM 'ext.dtd'><root>&top;</root>";

        // fine with a limit comfortably above the expanded size
        XMLStreamReader sr = getExtDtdFactory(1000L).createXMLStreamReader(new StringReader(XML));
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(160, sr.getElementText().length());
        sr.close();

        // but not with a limit below what reading of the DTD itself expands
        // (200 chars), even though expanding "&top;" in content would be fine (160 chars)
        sr = getExtDtdFactory(180L).createXMLStreamReader(new StringReader(XML));
        try {
            while (sr.hasNext()) {
                sr.next();
            }
            fail("Should have failed with entity expansion length limit");
        } catch (XMLStreamException e) {
            verifyException(e, "Maximum entity expansion length");
        }
        sr.close();
    }

    // New factory per call, since external DTD subsets are cached per factory
    private XMLInputFactory getExtDtdFactory(long maxExpansionLength)
        throws XMLStreamException
    {
        final String extDtd = "<!ENTITY % bottom '0123456789'>\n"
                +"<!ENTITY % middle '%bottom;%bottom;%bottom;%bottom;'>\n"
                +"<!ENTITY top '%middle;%middle;%middle;%middle;'>\n"
               ;
        XMLInputFactory f = getNewInputFactory();
        setCoalescing(f, true);
        setSupportDTD(f, true);
        f.setProperty(WstxInputProperties.P_DTD_RESOLVER, new XMLResolver() {
            @Override
            public Object resolveEntity(String publicID, String systemID, String baseURI, String namespace) {
                return new StringReader(extDtd);
            }
        });
        f.setProperty(WstxInputProperties.P_MAX_ENTITY_EXPANSION_LENGTH, maxExpansionLength);
        return f;
    }
}
