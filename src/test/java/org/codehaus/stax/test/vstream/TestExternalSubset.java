package org.codehaus.stax.test.vstream;

import javax.xml.stream.*;

import org.codehaus.stax.test.SimpleResolver;

/**
 * Unit test suite that verifies that external subsets can be used, and
 * also tests some of features only legal in there (include/exclude,
 * parameter entities within declarations)
 *
 * @author Tatu Saloranta 
 */
public class TestExternalSubset
    extends BaseVStreamTest
{
    public void testSimpleValidExternalSubset()
        throws XMLStreamException
    {
        String XML = "<!DOCTYPE root SYSTEM 'myurl' >"
            +"<root>text</root>";
//        String EXT_ENTITY_VALUE = "just testing";
        String EXT_SUBSET =
            "<!ELEMENT root (#PCDATA)>\n"
            +"<!-- comments are ok!!! -->";

        XMLStreamReader sr = getReader(XML, true,
                                       new SimpleResolver(EXT_SUBSET));
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertTokenType(CHARACTERS, sr.next());
        assertEquals("text", getAndVerifyText(sr));
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();
    }

    public void testEntityInExternalSubset()
        throws XMLStreamException
    {
        String XML = "<!DOCTYPE root SYSTEM 'myurl' >"
            +"<root>&extEnt;</root>";
        String EXT_ENTITY_VALUE = "just testing";
        String EXT_SUBSET =
            "<!ELEMENT root (#PCDATA)>\n"
            +"<!ENTITY extEnt '"+EXT_ENTITY_VALUE+"'>\n";

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

