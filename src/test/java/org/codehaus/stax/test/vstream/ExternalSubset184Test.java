package org.codehaus.stax.test.vstream;

import javax.xml.stream.*;

import org.codehaus.stax.test.SimpleResolver;

/**
 * Currently failing case(s) of {@code ExternalSubsetTest}
 */
public class ExternalSubset184Test
    extends BaseVStreamTest
{
    public void testNotationReferenceInInternalSubset()
            throws XMLStreamException
    {
        String EXT_ENTITY_VALUE = "Overridden value";
        String XML = "<!DOCTYPE root SYSTEM 'myurl' [ <!ENTITY gr2 SYSTEM \"gr2\" NDATA IMAGE> " +
                " <!ENTITY extEnt '"+EXT_ENTITY_VALUE+"'>" + " ]>"
                +"<root>&extEnt;</root>";

        String EXT_SUBSET =
                "<!ELEMENT root (#PCDATA)>\n"
                        +"<!ENTITY extEnt 'Original DTD value'>\n" +
                        "<!NOTATION IMAGE       PUBLIC \"-//ES//NOTATION image format//EN\" \n" +
                        "                       \"http://www.test.com/xml/common/dtd/notation/image\">";

        XMLStreamReader sr = getReader(XML, true,
                new SimpleResolver(EXT_SUBSET));
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertTokenType(CHARACTERS, sr.next());
        assertEquals(EXT_ENTITY_VALUE, getAndVerifyText(sr));
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();
    }

    /*
    ////////////////////////////////////////
    // Private methods, other
    ////////////////////////////////////////
     */

    private XMLStreamReader getReader(String contents, boolean nsAware,
                                      XMLResolver resolver)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setCoalescing(f, false); // shouldn't really matter
        setNamespaceAware(f, nsAware);
        setSupportDTD(f, true);
        setValidating(f, true);
        // This shouldn't be required but let's play it safe:
        setSupportExternalEntities(f, true);
        setResolver(f, resolver);
        return constructStreamReader(f, contents);
    }
}

