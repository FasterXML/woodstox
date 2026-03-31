package stax2.dtd;

import javax.xml.stream.*;

import org.codehaus.stax2.*;
import org.codehaus.stax2.validation.DTDValidationSchema;

import stax2.BaseStax2Test;

/**
 * Set of unit tests that checks that the {@link AttributeInfo} implementation
 * works as expected.
 */
public class TestDTDInfo
    extends BaseStax2Test
{
    final static String TEST_DOC =
        "<?xml version='1.0'?>"
        +"<!DOCTYPE root [\n"
        +"<!ELEMENT root ANY>\n"
        +"<!ENTITY % paramEnt 'mystuff'>\n" // param entity
        +"<!ENTITY genInt 'someValue'>\n" // general internal parsed entity
        +"<!ENTITY genExt SYSTEM 'http://foo'>\n" // gen. ext. parsed entity
        +"<!NOTATION DVI SYSTEM 'DVI'>\n"
        +"<!NOTATION EPS PUBLIC '+//ISBN 0-201-18127-4::Adobe//NOTATION PostScript Language Ref. Manual//EN'>\n"
        +"<!ENTITY unparsedExt SYSTEM 'url' NDATA EPS>\n" // gen. ext. unparsed
        +"]>"
        +"<root />"
        ;

    public void testDTDInfo()
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getReader(TEST_DOC);

        assertTokenType(DTD, sr.next());

        DTDInfo info = sr.getDTDInfo();
        assertNotNull(info);
        DTDValidationSchema dtd = info.getProcessedDTDSchema();
        assertNotNull(dtd);

        // 4 entities, but one is parameter entity...
        assertEquals(3, dtd.getEntityCount());

        // 2 notations:
        assertEquals(2, dtd.getNotationCount());

        // Also, don't want a creepy exception afterwards...
        assertTokenType(START_ELEMENT, sr.next());
    }

    // [woodstox-core#250]: getProcessedDTDSchema() should respect DTD override
    public void testGetProcessedDTDSchemaWithOverride()
        throws XMLStreamException
    {
        // First, parse a document to get a DTD schema to use as override
        XMLStreamReader2 sr1 = getReader(TEST_DOC);
        assertTokenType(DTD, sr1.next());
        DTDValidationSchema overrideSchema = sr1.getDTDInfo().getProcessedDTDSchema();
        assertNotNull(overrideSchema);
        sr1.close();

        // Now parse a different document with a different DTD (fewer entities/notations)
        final String otherDoc =
            "<?xml version='1.0'?>"
            +"<!DOCTYPE root [\n"
            +"<!ELEMENT root ANY>\n"
            +"]>"
            +"<root />";
        XMLStreamReader2 sr2 = getReader(otherDoc);
        assertTokenType(DTD, sr2.next());

        // Verify the parsed DTD is different from the override
        DTDValidationSchema parsedSchema = sr2.getDTDInfo().getProcessedDTDSchema();
        assertNotNull(parsedSchema);
        assertEquals(0, parsedSchema.getEntityCount());
        assertNotSame(overrideSchema, parsedSchema);

        // Now set the override on the reader's config AFTER parsing
        sr2.setProperty(XMLInputFactory2.P_DTD_OVERRIDE, overrideSchema);

        // getProcessedDTDSchema() should now return the override, not the parsed DTD
        DTDValidationSchema result = sr2.getDTDInfo().getProcessedDTDSchema();
        assertSame("getProcessedDTDSchema() should return DTD override when set",
                overrideSchema, result);
        assertEquals(3, result.getEntityCount());

        sr2.close();
    }

    /*
    ////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////
     */

    private XMLStreamReader2 getReader(String contents)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        // Need dtd support, may need validation...
        setSupportDTD(f, true);
        setValidating(f, true);
        return constructStreamReader(f, contents);
    }
}
