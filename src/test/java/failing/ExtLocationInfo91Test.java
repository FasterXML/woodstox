package failing;

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
        XMLResolver resolver = new XMLResolver() {
            @Override
            public Object resolveEntity(String publicID, String systemID, String baseURI, String namespace) throws XMLStreamException {
                if (INCL_URI.equals(systemID)){
                    StreamSource src = new StreamSource(new StringReader(TEST_EXT_ENT_INCL), systemID);
                    return src;
                }
                fail("Unexpected systemID to resolve: " + systemID);
                return null;
            }
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

    /*
    ////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////
     */

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
