package org.codehaus.stax.test.vstream;

import java.io.StringReader;

import javax.xml.stream.*;

import org.codehaus.stax.test.SimpleResolver;

/**
 * Simple unit tests to check and verify that DTD handler properly deals
 * with conditional sections.
 *
 * @author Tatu Saloranta
 */
public class TestIncludes
    extends BaseVStreamTest
{
    public void testSimpleInclude()
        throws XMLStreamException
    {
        final String XML =
            "<!DOCTYPE root SYSTEM 'foobar'><root>&myent;</root>";
            ;
        final String EXT_DTD =
            "<!ELEMENT root (#PCDATA)>\n"
            +"<![INCLUDE["
            +"  <!ENTITY myent 'value'>"
            +"]]>\n"
            ;

        XMLInputFactory f = getValidatingFactory(true);
        setResolver(f, new SimpleResolver(EXT_DTD));
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader(XML));
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());
        assertEquals("value", getAndVerifyText(sr));
        assertTokenType(END_ELEMENT, sr.next());
    }

    public void testSimpleIgnore()
        throws XMLStreamException
    {
        final String XML =
            "<!DOCTYPE root SYSTEM 'foobar'><root>&myent;</root>";
            ;

            /* Let's add something that'd be invalid in there...
             */
        final String EXT_DTD =
            "<!ELEMENT root (#PCDATA)>\n"
            +"<![IGNORE["
            +"  <!FOOBAR> <!-- nonsense, but ignored! -->"
            +"]]>\n"
            +"<!ENTITY myent 'value'>"
            ;

        XMLInputFactory f = getValidatingFactory(true);
        setResolver(f, new SimpleResolver(EXT_DTD));
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader(XML));
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());
        assertEquals("value", getAndVerifyText(sr));
        assertTokenType(END_ELEMENT, sr.next());
    }

    /**
     * Conditional sections can NOT be used in the internal subset --
     * let's quickly verify this.
     */
    public void testFailingInIntSubset()
        throws XMLStreamException
    {
        // first inclusion:
        String XML =
            "<!DOCTYPE root [\n"
            +"<![INCLUDE["
            +"  <!ENTITY myent 'value'>"
            +"]]>"
            +"]>\n<root />"
            ;
        streamThroughFailing(getValidatingReader(XML),
                             "Condition INCLUDE not allowed in internal DTD subset");

        // Then IGNORE:
        XML =
            "<!DOCTYPE root [\n"
            +"<![IGNORE["
            +"  <!ENTITY myent 'value'>"
            +"]]>"
            +"  <!ENTITY myent2 'value'>"
            +"]>\n<root />"
            ;
        streamThroughFailing(getValidatingReader(XML),
                             "Condition INCLUDE not allowed in internal DTD subset");
    }

    /**
     * Ok, and then we better consider parameter entity expanded variations
     * of INCLUDE/IGNORE directives (see example under XML 1.0.3 section 3.4
     * for a sample)
     */
    public void testPEIncludeAndIgnore()
        throws XMLStreamException
    {
        final String XML = "<!DOCTYPE root SYSTEM 'foobar'><root>&myent;</root>";
            ;
        final String EXT_DTD =
             "<!ENTITY % yup 'INCLUDE' >\n"
            +"<!ENTITY % nope 'IGNORE' >\n"
            +"<![%nope;[\n"
            +"<!ENTITY myent 'ignore'>\n"
            +"]]>\n"
            +"<![%yup;[\n"
            +"<!ENTITY myent 'include'>\n"
            +"]]>\n"
            +"<!ELEMENT root (#PCDATA)>\n"
            ;

        XMLInputFactory f = getValidatingFactory(true);
        setResolver(f, new SimpleResolver(EXT_DTD));
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader(XML));
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());
        String text = getAndVerifyText(sr);
        if (!text.equals("include")) {
            fail("Expected 'myent' to expand to 'include', not '"+text+"'");
        }
        assertTokenType(END_ELEMENT, sr.next());
    }
}
