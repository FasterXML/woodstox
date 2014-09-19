package stax2.stream;

import javax.xml.stream.*;

import org.codehaus.stax2.*;
 
import stax2.BaseStax2Test;

/**
 * Set of unit tests that checks that the {@link LocationInfo} implementation
 * works as expected, provides proper values or -1 to indicate "don't know".
 */
public class TestLocationInfo
    extends BaseStax2Test
{
    final static String TEST_DTD_DOC =
        "<?xml version='1.0'?>"
        +"<!DOCTYPE root [\n" // first char: 21; row 1
        +"<!ENTITY ent 'simple\ntext'>\n" // fc: 38; row 2
        +"<!ENTITY ent2 '<tag>foo</tag>'>\n" // fc: 66; row 4
        +"]>\n" // fc: 98; row 5
        +"<root>Entity: " // fc: 101; row 6
        +"&ent; " // fc: 115; row 6
        +"<leaf />\r\n" // fc: 121; row 6
        +"&ent2;" // fc: 137; row 7
        +"</root>"; // fc: 144; row 7
    // EOF, fc: 150; row 7

    /**
     * This document fragment tries ensure that linefeed handling works ok
     * as well.
     */
    final static String TEST_LF_DOC =
        "<root>\n" // row 1
        +"<branch>\r\n" // row 2
        +" <branch2>\r" // row 3
        +"\t\t<leaf />\n" // row 4
        +" </branch2>\r\n" // row 5
        +"\t </branch> "// row 6
        +"</root>"; // row6
        ;

    public void testInitialLocationNoDecl()
        throws XMLStreamException
    {
        // First, let's test 'missing' start doc:
        XMLStreamReader2 sr = getReader("<root />", false);
        LocationInfo loc = sr.getLocationInfo();
        assertLocation(sr, loc.getStartLocation(), 1, 1,
                       0, loc.getStartingByteOffset(),
                       0, loc.getStartingCharOffset());
        assertLocation(sr, loc.getEndLocation(), 1, 1,
                       0, loc.getEndingByteOffset(),
                       0, loc.getEndingCharOffset());
        sr.close();
    }

    public void testInitialLocationWithDecl()
        throws XMLStreamException
    {
        // and then a real one
        XMLStreamReader2 sr = getReader("<?xml version='1.0'\r\n?>", false);
        LocationInfo loc = sr.getLocationInfo();
        assertLocation(sr, loc.getStartLocation(), 1, 1,
                       0, loc.getStartingByteOffset(),
                       0, loc.getStartingCharOffset());
        assertLocation(sr, loc.getEndLocation(), 3, 2,
                       23, loc.getEndingByteOffset(),
                       23, loc.getEndingCharOffset());
        sr.close();
    }

    public void testRowAccuracy()
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getReader(TEST_LF_DOC, false);

        assertRow(sr, 1, 1); // (missing) xml decl

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertRow(sr, 1, 1);
        assertTokenType(CHARACTERS, sr.next());
        assertRow(sr, 1, 2); // lf

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("branch", sr.getLocalName());
        assertRow(sr, 2, 2);
        assertTokenType(CHARACTERS, sr.next());
        assertRow(sr, 2, 3); // lf

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("branch2", sr.getLocalName());
        assertRow(sr, 3, 3);
        assertTokenType(CHARACTERS, sr.next());
        assertRow(sr, 3, 4); // lf

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());
        assertRow(sr, 4, 4);
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());
        assertRow(sr, 4, 4);
        assertTokenType(CHARACTERS, sr.next());
        assertRow(sr, 4, 5); // lf

        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("branch2", sr.getLocalName());
        assertRow(sr, 5, 5);
        assertTokenType(CHARACTERS, sr.next());
        assertRow(sr, 5, 6);

        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("branch", sr.getLocalName());
        assertRow(sr, 6, 6);
        assertTokenType(CHARACTERS, sr.next());
        assertRow(sr, 6, 6);

        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertRow(sr, 6, 6);

        sr.close();
    }

    /**
     * Test that uses a document with internal DTD subset, to verify that
     * location info is still valid.
     */
    public void testLocationsWithDTD()
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getReader(TEST_DTD_DOC, true);
        // will return null if SUPPORT_DTD fails:
        if (sr == null) {
            System.err.println("WARN: SupportDTD can not be enabled, need to skip test");
            return;
        }

        LocationInfo loc = sr.getLocationInfo();
        assertLocation(sr, loc.getStartLocation(), 1, 1,
                       0, loc.getStartingByteOffset(),
                       0, loc.getStartingCharOffset());
        assertLocation(sr, loc.getEndLocation(), 22, 1,
                       21, loc.getEndingByteOffset(),
                       21, loc.getEndingCharOffset());

        assertTokenType(DTD, sr.next());
        loc = sr.getLocationInfo();
        assertLocation(sr, loc.getStartLocation(), 22, 1,
                       21, loc.getStartingByteOffset(),
                       21, loc.getStartingCharOffset());
        assertLocation(sr, loc.getEndLocation(), 3, 5,
                       100, loc.getEndingByteOffset(),
                       100, loc.getEndingCharOffset());

        // Let's ignore text/space, if there is one:
        while (sr.next() != START_ELEMENT) {
            ;
        }

        loc = sr.getLocationInfo();
        assertLocation(sr, loc.getStartLocation(), 1, 6,
                       101, loc.getStartingByteOffset(),
                       101, loc.getStartingCharOffset());
        assertLocation(sr, loc.getEndLocation(), 7, 6,
                       107, loc.getEndingByteOffset(),
                       107, loc.getEndingCharOffset());

        // !!! TBI
    }

    /*
    ////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////
     */

    private void assertRow(XMLStreamReader2 sr, int startRow, int endRow)
        throws XMLStreamException
    {
        LocationInfo li = sr.getLocationInfo();
        Location startLoc = li.getStartLocation();
        assertEquals("Incorrect starting row for event "+tokenTypeDesc(sr.getEventType()), startRow, startLoc.getLineNumber());
        Location endLoc = li.getEndLocation();
        assertEquals("Incorrect ending row for event "+tokenTypeDesc(sr.getEventType()), endRow, endLoc.getLineNumber());
    }

    private void assertLocation(XMLStreamReader sr, XMLStreamLocation2 loc,
                                int expCol, int expRow,
                                int expByteOffset, long actByteOffset,
                                int expCharOffset, long actCharOffset)
    {
        assertEquals("Incorrect column for "+tokenTypeDesc(sr.getEventType()),
                     expCol, loc.getColumnNumber());
        assertEquals("Incorrect row for "+tokenTypeDesc(sr.getEventType()),
                     expRow, loc.getLineNumber());

        if (actByteOffset == -1) { // no info, that's fine
            ;
        } else {
            assertEquals(expByteOffset, actByteOffset);
        }
        if (actCharOffset == -1) { // no info, that's fine
            ;
        } else {
            assertEquals(expCharOffset, actCharOffset);
        }
    }

    private XMLStreamReader2 getReader(String contents, boolean needDTD)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setCoalescing(f, false); // shouldn't really matter
        setNamespaceAware(f, true);
        if (!setSupportDTD(f, needDTD)) {
            return null;
        }
        // No need to validate, just need entities
        setValidating(f, false);
        return constructStreamReader(f, contents);
    }
}
