package org.codehaus.stax.test.vstream;

import javax.xml.stream.*;

import org.codehaus.stax.test.SimpleResolver;
import org.junit.jupiter.api.Test;

/**
 * Unit test suite that verifies that external subsets can be used, and
 * also tests some of features only legal in there (include/exclude,
 * parameter entities within declarations)
 *
 * @author Tatu Saloranta 
 */
public class ExternalSubsetTest
    extends BaseVStreamTest
{
    @Test
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

    @Test
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

    /**
     * Regression test for [woodstox-core#184]: a NOTATION declared in the
     * external DTD subset must be resolvable from the internal subset
     * (per XML 1.0 §2.8 the internal subset is considered to appear
     * <em>before</em> the external subset, so order of declarations does
     * not constrain resolution).
     */
    @Test
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

    @Test
    public void testParameterEntityOverrideInInternalSubset()
            throws XMLStreamException
    {
        String XML = "<!DOCTYPE root SYSTEM 'myurl' [ <!ENTITY  % PATRR  \"image CDATA #IMPLIED\"> " +
                " ]>" +
                "<root id=\"id1\" image=\"img1\">Some text</root>";

        String EXT_SUBSET =
                "<!ENTITY % PATRR  \"photo CDATA #IMPLIED\">\n" +
                "<!ELEMENT root (#PCDATA)>\n" +
                        "<!ATTLIST root id CDATA #REQUIRED\n" +
                        "          %PATRR; >";

        XMLStreamReader sr = getReader(XML, true,
                new SimpleResolver(EXT_SUBSET));
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertTokenType(CHARACTERS, sr.next());
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

