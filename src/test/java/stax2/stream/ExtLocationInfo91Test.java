package stax2.stream;

import org.codehaus.stax2.LocationInfo;
import org.codehaus.stax2.XMLStreamReader2;
import stax2.BaseStax2Test;

import javax.xml.stream.*;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;

/**
 * Set of unit tests that checks that the {@link LocationInfo} implementation
 * works as expected, provides proper values or -1 to indicate "don't know".
 */
public class ExtLocationInfo91Test
    extends BaseStax2Test
{
    final static String URI = "main.xml";
    final static String INCL_URI = "include.xml";

    /**
     * This document fragment tries ensure that external entities works ok
     */
    final static String TEST_EXT_ENT =
        "<?xml version='1.0'?>"
        +"<!DOCTYPE main [\n" // first char: 21; row 1
        +"<!ENTITY incl SYSTEM '" + INCL_URI + "'>\n" // fc: 38; row 2
        +"]>\n" // fc: 74; row 3
        +"<main>" // fc: 77; row 4
        +"&incl;" // fc: 83; row 4
        +"</main>"; // fc: 89; row 4
    // EOF, fc: 98; row 7

    /**
     * This document fragment is used to be included from TEST_EXT_ENT
     */
    final static String TEST_EXT_ENT_INCL =
        "<include></include>"; // first char: 0; row 1
    // EOF, fc: 19; row 1

    // For [woodstox-core#91]
    public void testLocationsWithExtEntity()
            throws XMLStreamException
    {
        XMLResolver resolver = (publicID, systemID, baseURI, namespace) -> {
            if (INCL_URI.equals(systemID)){
                StreamSource src = new StreamSource(new StringReader(TEST_EXT_ENT_INCL), systemID);
                return src;
            }
            fail("Unexpected systemID to resolve: " + systemID);
            return null;
        };
        XMLStreamReader2 sr = getReader(TEST_EXT_ENT, URI, resolver);

        assertOffset(sr, 0, 21);

        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("main", sr.getLocalName());
        assertOffset(sr, 77, 83, URI);

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("include", sr.getLocalName());
        assertOffset(sr, 0, 9, INCL_URI);



        assertTokenType(END_ELEMENT, sr.next());
        assertOffset(sr, 9, 19, INCL_URI);


        assertTokenType(END_ELEMENT, sr.next());
        assertOffset(sr, 89, 96, URI);

        sr.close();
    }

    // [woodstox-core#91] Text content inside an external entity must report
    // the external entity's system id, not the parent document's.
    public void testCharactersInExtEntity()
        throws XMLStreamException
    {
        final String mainXml =
            "<!DOCTYPE main ["
            + "<!ENTITY ext SYSTEM 'ext.xml'>"
            + "]>"
            + "<main>&ext;</main>";
        final String extXml = "<a>hello</a>";
        // ext.xml offsets:
        //   <a>     : start 0, end 3
        //   hello   : start 3, end 8
        //   </a>    : start 8, end 12

        XMLStreamReader2 sr = getReader(mainXml, URI, resolverFor("ext.xml", extXml));

        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("main", sr.getLocalName());
        assertSystemId(sr, URI);

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("a", sr.getLocalName());
        assertOffset(sr, 0, 3, "ext.xml");

        assertTokenType(CHARACTERS, sr.next());
        assertEquals("hello", sr.getText());
        assertOffset(sr, 3, 8, "ext.xml");

        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("a", sr.getLocalName());
        assertOffset(sr, 8, 12, "ext.xml");

        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("main", sr.getLocalName());
        assertSystemId(sr, URI);

        sr.close();
    }

    // [woodstox-core#91] A comment inside an external entity must report the
    // external entity's system id, not the parent document's.
    public void testCommentInExtEntity()
        throws XMLStreamException
    {
        final String mainXml =
            "<!DOCTYPE main ["
            + "<!ENTITY ext SYSTEM 'ext.xml'>"
            + "]>"
            + "<main>&ext;</main>";
        final String extXml = "<a><!-- hi --></a>";
        // ext.xml offsets:
        //   <a>           : 0..2 (end 3)
        //   <!-- hi -->   : 3..13 (end 14)
        //   </a>          : 14..17 (end 18)

        XMLStreamReader2 sr = getReader(mainXml, URI, resolverFor("ext.xml", extXml));

        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("main", sr.getLocalName());

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("a", sr.getLocalName());
        assertOffset(sr, 0, 3, "ext.xml");

        assertTokenType(COMMENT, sr.next());
        assertOffset(sr, 3, 14, "ext.xml");

        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("a", sr.getLocalName());
        assertOffset(sr, 14, 18, "ext.xml");

        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("main", sr.getLocalName());
        assertSystemId(sr, URI);

        sr.close();
    }

    // [woodstox-core#91] Three-level nested external entities: locations must
    // track the innermost source on entry and the correct outer source on each
    // pop. Both entities are declared in the main document DTD (XML rules
    // prohibit DOCTYPE inside external parsed entities).
    public void testNestedExternalEntities()
        throws XMLStreamException
    {
        final String mainXml =
            "<!DOCTYPE main ["
            + "<!ENTITY outer SYSTEM 'outer.xml'>"
            + "<!ENTITY inner SYSTEM 'inner.xml'>"
            + "]>"
            + "<main>&outer;</main>";
        final String outerXml = "<o>&inner;</o>";
        // outer.xml offsets:
        //   <o>        : 0..2 (end 3)
        //   &inner;    : 3..9
        //   </o>       : 10..13 (end 14)
        final String innerXml = "<i></i>";
        // inner.xml offsets:
        //   <i>        : 0..2 (end 3)
        //   </i>       : 3..6 (end 7)

        XMLResolver resolver = (publicID, systemID, baseURI, namespace) -> {
            if ("outer.xml".equals(systemID)) {
                return new StreamSource(new StringReader(outerXml), systemID);
            }
            if ("inner.xml".equals(systemID)) {
                return new StreamSource(new StringReader(innerXml), systemID);
            }
            fail("Unexpected systemID to resolve: " + systemID);
            return null;
        };
        XMLStreamReader2 sr = getReader(mainXml, URI, resolver);

        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("main", sr.getLocalName());
        assertSystemId(sr, URI);

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("o", sr.getLocalName());
        assertOffset(sr, 0, 3, "outer.xml");

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("i", sr.getLocalName());
        assertOffset(sr, 0, 3, "inner.xml");

        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("i", sr.getLocalName());
        assertOffset(sr, 3, 7, "inner.xml");

        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("o", sr.getLocalName());
        assertOffset(sr, 10, 14, "outer.xml");

        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("main", sr.getLocalName());
        assertSystemId(sr, URI);

        sr.close();
    }

    /*
    ////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////
     */

    private XMLResolver resolverFor(final String wantSystemId, final String contents)
    {
        return (publicID, systemID, baseURI, namespace) -> {
            if (wantSystemId.equals(systemID)) {
                return new StreamSource(new StringReader(contents), systemID);
            }
            fail("Unexpected systemID to resolve: " + systemID);
            return null;
        };
    }

    private void assertSystemId(XMLStreamReader2 sr, String expected)
        throws XMLStreamException
    {
        LocationInfo li = sr.getLocationInfo();
        assertEquals("Wrong starting systemID for " + tokenTypeDesc(sr.getEventType()),
                expected, li.getStartLocation().getSystemId());
        assertEquals("Wrong ending systemID for " + tokenTypeDesc(sr.getEventType()),
                expected, li.getEndLocation().getSystemId());
    }

    private void assertOffset(XMLStreamReader2 sr, int startOffset, int endOffset, String systemId)
        throws XMLStreamException
    {
        LocationInfo li = sr.getLocationInfo();
        Location startLoc = li.getStartLocation();
        assertEquals("Incorrect starting systemID for event " + tokenTypeDesc(sr.getEventType()), systemId, startLoc.getSystemId());
        Location endLoc = li.getEndLocation();
        assertEquals("Incorrect ending systemID for event " + tokenTypeDesc(sr.getEventType()), systemId, endLoc.getSystemId());
        assertOffset(sr, startOffset, endOffset);
    }
    private void assertOffset(XMLStreamReader2 sr, int startOffset, int endOffset)
        throws XMLStreamException
    {
        LocationInfo li = sr.getLocationInfo();
        Location startLoc = li.getStartLocation();
        assertEquals("Incorrect starting offset for event "+tokenTypeDesc(sr.getEventType()), startOffset, startLoc.getCharacterOffset());
        Location endLoc = li.getEndLocation();
        assertEquals("Incorrect ending offset for event "+tokenTypeDesc(sr.getEventType()), endOffset, endLoc.getCharacterOffset());
    }


    private XMLStreamReader2 getReader(String contents, String systemId, XMLResolver xmlResolver)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();

        if(xmlResolver != null){
            f.setXMLResolver(xmlResolver);
        }

        setCoalescing(f, false); // shouldn't really matter
        setNamespaceAware(f, true);
        setSupportExternalEntities(f, true);
        setReplaceEntities(f, true);


        // No need to validate, just need entities
        setValidating(f, false);

        return (XMLStreamReader2) f.createXMLStreamReader(new StreamSource(new StringReader(contents), systemId));
    }
}
