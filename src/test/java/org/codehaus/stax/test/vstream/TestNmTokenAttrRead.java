package org.codehaus.stax.test.vstream;

import javax.xml.stream.*;
import org.junit.jupiter.api.Test;

/**
 * Unit test suite that tests handling of attributes that are declared
 * by DTD to be of type NMTOKEN or NMTOKENS; such information is only
 * guranteed to be available in validation mode.
 */
public class TestNmTokenAttrRead
    extends BaseVStreamTest
{
    /*
    ///////////////////////////////////////
    // Test cases
    ///////////////////////////////////////
     */

    /**
     * Test case that verifies behaviour of valid NMTOKEN/NMTOKENS
     * attribute declarations.
     */
    @Test
    public void testValidNmTokenAttrDecl()
        throws XMLStreamException
    {
        // Following should be ok
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root name NMTOKEN #IMPLIED\n"
            +"    names NMTOKENS #IMPLIED>\n"
            +"]>\n<root />";
        streamThrough(getValidatingReader(XML));
    }

    /**
     * Test case that verifies behaviour of invalid NMTOKEN/NMTOKENS
     * attribute declarations.
     */
    @Test
    public void testInvalidNmTokenAttrDecl()
        throws XMLStreamException
    {
        // ??? Are there any such cases?
    }

    @Test
    public void testValidNmTokenAttrUse()
        throws XMLStreamException
    {
        String XML = "<!DOCTYPE elem [\n"
            +"<!ELEMENT elem (elem*)>\n"
            +"<!ATTLIST elem name NMTOKEN #IMPLIED>\n"
            +"<!ATTLIST elem names NMTOKENS #IMPLIED>\n"
            +"]>\n<elem name='some-Name'> <elem names='a few names1' /> </elem>";
        streamThrough(getValidatingReader(XML));
    }

    @Test
    public void testInvalidNmTokenAttrUse()
        throws XMLStreamException
    {
        // Error: invalid NMTOKEN, ? not valid
        String XML = "<!DOCTYPE elem [\n"
            +"<!ELEMENT elem (elem*)>\n"
            +"<!ATTLIST elem name NMTOKEN #IMPLIED>\n"
            +"]>\n<elem name='?foo'/>";
        streamThroughFailing(getValidatingReader(XML),
                             "invalid char ('?') in NMTOKEN");

        // Error: invalid NMTOKENS, / not valid
        XML = "<!DOCTYPE elem [\n"
            +"<!ELEMENT elem (elem*)>\n"
            +"<!ATTLIST elem name NMTOKEN #IMPLIED>\n"
            +"]>\n<elem name='foo foo/bar'/>";
        streamThroughFailing(getValidatingReader(XML),
                             "invalid char ('/') in NMTOKENS");
    }

    /**
     * Unit test for a declared NMTOKENS default value whose tokens are a
     * single character long. The token-scanning loop in the NMTOKENS default
     * validator used to skip the char right after a token start and could let
     * its index run past the value length, throwing a raw
     * StringIndexOutOfBoundsException (e.g. for default "x") instead of
     * accepting the perfectly valid value -- and falsely rejecting values
     * like "a b" whose first token is a single char.
     */
    @Test
    public void testNmTokensDefaultWithSingleCharTokens()
        throws XMLStreamException
    {
        // Default with a single-char token (last token single char), plus
        // extra spaces, to also exercise normalization/tokenization:
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root (child)>\n"
            +"<!ELEMENT child EMPTY>\n"
            +"<!ATTLIST child a NMTOKENS 'x  y z'>\n"
            +"]>\n<root><child/></root>";
        XMLStreamReader sr = getValidatingReader(XML);
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next()); // root
        assertTokenType(START_ELEMENT, sr.next()); // child (gets default)
        assertEquals("child", sr.getLocalName());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("a", sr.getAttributeLocalName(0));
        // Single-char tokens must be preserved and space-normalized:
        assertEquals("x y z", sr.getAttributeValue(0));
        sr.close();

        // A single-char-only default must be accepted (used to throw):
        streamThrough(getValidatingReader("<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root a NMTOKENS 'x'>\n"
            +"]>\n<root/>"));

        // ...but an invalid token (illegal char, non-first position) is still
        // rejected cleanly rather than crashing or being accepted:
        streamThroughFailing(getValidatingReader("<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root a NMTOKENS 'ok bad/tok'>\n"
            +"]>\n<root/>"),
            "invalid NMTOKENS default value (illegal char '/')");
    }

    /**
     * Unit test that verifies that values of attributes of type NMTOKEN and
     * NMTOKENS will get properly normalized.
     */
    @Test
    public void testNmTokenAttrNormalization()
        throws XMLStreamException
    {
        String XML = "<!DOCTYPE elem [\n"
            +"<!ELEMENT elem (elem*, elem2?, elem3?)>\n"
            +"<!ATTLIST elem name NMTOKEN #IMPLIED>\n"
            +"<!ATTLIST elem names NMTOKENS #IMPLIED>\n"
            +"<!ELEMENT elem2 EMPTY>\n"
            +"<!ATTLIST elem2 name NMTOKEN 'somename  '>\n"
            +"<!ELEMENT elem3 EMPTY>\n"
            +"<!ATTLIST elem3 names NMTOKENS 'name1\tname2   name3  '>\n"
            +"]>"
            +"<elem name='nmToken  '>"
            +"<elem name='  name' />"
            +"<elem names='first_name  \tsecond last' />"
            +"<elem2 /><elem3 />"
            +"</elem>";
            ;
        XMLStreamReader sr = getValidatingReader(XML);
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("elem", sr.getLocalName());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("nmToken", sr.getAttributeValue(0));
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("name", sr.getAttributeValue(0));
        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("first_name second last", sr.getAttributeValue(0));
        assertTokenType(END_ELEMENT, sr.next());

        // then the defaults
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("elem2", sr.getLocalName());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("name", sr.getAttributeLocalName(0));
        assertEquals("somename", sr.getAttributeValue(0));
        assertTokenType(END_ELEMENT, sr.next());

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("elem3", sr.getLocalName());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("names", sr.getAttributeLocalName(0));
        assertEquals("name1 name2 name3", sr.getAttributeValue(0));
        assertTokenType(END_ELEMENT, sr.next());

        assertTokenType(END_ELEMENT, sr.next());
    }

    /*
    ////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////
     */
}
