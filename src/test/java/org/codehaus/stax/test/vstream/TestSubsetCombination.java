package org.codehaus.stax.test.vstream;

import javax.xml.stream.*;

import org.codehaus.stax.test.SimpleResolver;

/**
 * Unit test suite that tests how implementation handles combining of
 * internal and external DTD subsets.
 *
 * @author Tatu Saloranta
 */
public class TestSubsetCombination
    extends BaseVStreamTest
{
    /**
     * This unit test checks that a DTD definition that is evenly split
     * between subsets will be properly combined, and results in a usable
     * definition for validation.
     */
    public void testValidSubsets()
        throws XMLStreamException
    {
        // Note: need to resolve using a custom resolver
        String XML =
            "<!DOCTYPE root SYSTEM 'dummy-url' [\n"
            +"<!ELEMENT root (leaf+)>\n"
            +"<!ATTLIST root attrInt CDATA #IMPLIED>\n"
            +"<!ENTITY ent1 '&ent2;'>\n"
            +"]><root attrInt='value' attrExt='someValue'>  <leaf>Test entities: &ent1;, &ent2;</leaf>"
            +"<leaf>...</leaf>"
            +"<leaf /></root>";
        String EXT_DTD = 
            "<!ELEMENT leaf (#PCDATA)>\n"
            +"<!ENTITY ent2 'some text'>\n"
            +"<!ATTLIST root attrExt CDATA #IMPLIED>\n"
            ;
        streamThrough(getReader(XML, true, EXT_DTD));
        // Let's also test that non-ns works, just in case it's different
        streamThrough(getReader(XML, false, EXT_DTD));
    }

    /**
     * This unit test checks that the internal subset has precedence
     * for attribute definitions -- it's ok to declare attributes multiple
     * times, but the first one sticks, and internal subset is considered
     * to come before external subset
     */
    public void testAttributePrecedence()
        throws XMLStreamException
    {
        String XML =
            "<!DOCTYPE root SYSTEM 'dummy-url' [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root attr1 CDATA #IMPLIED>\n"
            +"<!ATTLIST root attr2 CDATA 'intValue'>\n"
            +"]><root />"
            ;
        String EXT_DTD = 
            "<!ATTLIST root attr1 CDATA 'extValue'>\n"
            +"<!ATTLIST root attr2 CDATA #IMPLIED>\n"
            ;
        XMLStreamReader sr = getReader(XML, true, EXT_DTD);
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("attr2", sr.getAttributeLocalName(0));
        assertEquals("intValue", sr.getAttributeValue(0));
        assertFalse(sr.isAttributeSpecified(0));
    }

    /*
    ////////////////////////////////////////
    // Non-test methods
    ////////////////////////////////////////
     */

    private XMLStreamReader getReader(String contents, boolean nsAware,
                                      String extSubset)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setNamespaceAware(f, nsAware);
        setSupportDTD(f, true);
        setCoalescing(f, false);
        setReplaceEntities(f, true);
        setValidating(f, true);
        if (extSubset != null) {
            setResolver(f, new SimpleResolver(extSubset));
        } else {
            setResolver(f, null);
        }
        return constructStreamReader(f, contents);
    }
}

