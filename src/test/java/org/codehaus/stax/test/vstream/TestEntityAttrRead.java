package org.codehaus.stax.test.vstream;

import javax.xml.stream.*;

/**
 * Unit test suite that tests handling of attributes that are declared
 * by DTD to be of type NOTATION.
 *
 * @author Tatu Saloranta
 */
public class TestEntityAttrRead
    extends BaseVStreamTest
{
    /*
    ///////////////////////////////////////
    // Test cases
    ///////////////////////////////////////
     */

    public void testValidEntityAttrDecl()
        throws XMLStreamException
    {
        // Following should be ok; notations have been declared ok
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!NOTATION not1 PUBLIC 'public-notation-id'>\n"
            +"<!ENTITY unpEnt SYSTEM 'system-ent-id' NDATA not1>\n"
            +"<!ATTLIST root ent ENTITY #IMPLIED>\n"
            +"]>\n<root />";
        streamThrough(getValidatingReader(XML));

        // Likewise for default values
        XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!NOTATION not1 PUBLIC 'public-notation-id'>\n"
            +"<!ENTITY unpEnt SYSTEM 'system-ent-id' NDATA not1>\n"
            +"<!ATTLIST root ent ENTITY 'unpEnt'>\n"
            +"]>\n<root />";
        streamThrough(getValidatingReader(XML));
    }

    public void testValidUnorderedEntityAttrDecl()
        throws XMLStreamException
    {
        // Following should be ok even though ordering is reversed
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ENTITY unpEnt SYSTEM 'system-ent-id' NDATA not1>\n"
            +"<!NOTATION not1 PUBLIC 'public-notation-id'>\n"
            +"<!ATTLIST root attr ENTITY #IMPLIED>\n"
            +"]>\n<root />";
        try {
            streamThrough(getValidatingReader(XML));
        } catch (XMLStreamException e) {
            fail("Entity declaration order should not matter, but failed due to: "+e.getMessage());
        }
    }

    public void testValidEntitiesAttrDecl()
        throws XMLStreamException
    {
        // Following should be ok; notations have been declared ok
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!NOTATION not1 PUBLIC 'public-notation-id'>\n"
            +"<!ENTITY unpEnt SYSTEM 'system-ent-id' NDATA not1>\n"
            +"<!ATTLIST root ent ENTITIES #IMPLIED>\n"
            +"]>\n<root />";
        streamThrough(getValidatingReader(XML));

        // and for default values
        XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!NOTATION not1 PUBLIC 'public-notation-id'>\n"
            +"<!NOTATION not2 PUBLIC 'public-notation-id2'>\n"
            +"<!ENTITY unpEnt SYSTEM 'system-ent-id' NDATA not1>\n"
            +"<!ENTITY unpEnt2 SYSTEM 'system-ent-id' NDATA not1>\n"
            +"<!ATTLIST root ent ENTITIES 'unpEnt   unpEnt2  '>\n"
            +"<!ATTLIST root ent2 ENTITIES '  unpEnt  '>\n"
            +"]>\n<root />";
        streamThrough(getValidatingReader(XML));
    }

    public void testInvalidEntityAttrDecl()
        throws XMLStreamException
    {
        // First, let's check that undeclared notation throws an exception
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!NOTATION notX PUBLIC 'public-notation-id'>\n"
            +"<!ENTITY unpEnt SYSTEM 'system-ent-id' NDATA not1>\n"
            +"<!ATTLIST root ent ENTITY #IMPLIED>\n"
            +"]>\n<root />";
        streamThroughFailing(getValidatingReader(XML),
                             "undeclared notation for ENTITY attribute");

        // Similarly, undeclared entity via default value
        XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!NOTATION notX PUBLIC 'public-notation-id'>\n"
            +"<!ENTITY unpEnt SYSTEM 'system-ent-id' NDATA notX>\n"
            +"<!ATTLIST root ent ENTITY 'foobar'>\n"
            +"]>\n<root />";
        streamThroughFailing(getValidatingReader(XML),
                             "undeclared entity for ENTITY default value");
    }

    public void testInvalidEntitiesAttrDecl()
        throws XMLStreamException
    {
        // First, let's check that undeclared notation throws an exception
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!NOTATION notX PUBLIC 'public-notation-id'>\n"
            +"<!ENTITY unpEnt SYSTEM 'system-ent-id' NDATA not1>\n"
            +"<!ATTLIST root ent ENTITIES #IMPLIED>\n"
            +"]>\n<root />";
        streamThroughFailing(getValidatingReader(XML),
                             "undeclared notation for ENTITIES attribute");

        // Similarly, undeclared entity via default value
        XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!NOTATION notX PUBLIC 'public-notation-id'>\n"
            +"<!ENTITY unpEnt SYSTEM 'system-ent-id' NDATA notX>\n"
            +"<!ATTLIST root ent ENTITIES 'foobar'>\n"
            +"]>\n<root />";
        streamThroughFailing(getValidatingReader(XML),
                             "undeclared entity for ENTITIES default value");

        XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!NOTATION notX PUBLIC 'public-notation-id'>\n"
            +"<!ENTITY unpEnt SYSTEM 'system-ent-id' NDATA notX>\n"
            +"<!ATTLIST root ent ENTITIES 'unpEnt  notDeclEnt'>\n"
            +"]>\n<root />";
        streamThroughFailing(getValidatingReader(XML),
                             "undeclared entity for ENTITIES default value");
    }

    public void testValidEntityAttrUse()
        throws XMLStreamException
    {
        // Following should be ok; notations have been declared ok
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!NOTATION not1 PUBLIC 'public-notation-id'>\n"
            +"<!NOTATION not2 PUBLIC 'public-notation-id2'>\n"
            +"<!ENTITY unpEnt SYSTEM 'system-ent-id' NDATA not1>\n"
            +"<!ENTITY unpEnt2 SYSTEM 'system-ent-id' NDATA not2>\n"
            +"<!ATTLIST root ent ENTITY #REQUIRED>\n"
            +"]><root ent='  unpEnt2  '/>";

        XMLStreamReader sr = getValidatingReader(XML);
        // Let's ensure white space normalization too:
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("unpEnt2", sr.getAttributeValue(0));
    }

    public void testValidEntitiesAttrUse()
        throws XMLStreamException
    {
        // Following should be ok; notations have been declared ok
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!NOTATION not1 PUBLIC 'public-notation-id'>\n"
            +"<!NOTATION not2 PUBLIC 'public-notation-id2'>\n"
            +"<!NOTATION not3 PUBLIC 'public-notation-id3'>\n"
            +"<!ENTITY unpEnt1 SYSTEM 'system-ent-id' NDATA not1>\n"
            +"<!ENTITY unpEnt2 SYSTEM 'system-ent-id' NDATA not2>\n"
            +"<!ENTITY unpEnt3 SYSTEM 'system-ent-id' NDATA not3>\n"
            +"<!ATTLIST root ent ENTITIES #REQUIRED>\n"
            +"]><root ent='unpEnt2\tunpEnt3   \r \nunpEnt1 '/>";
        streamThrough(getValidatingReader(XML));

        XMLStreamReader sr = getValidatingReader(XML);
        // Let's ensure white space normalization too:
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("unpEnt2 unpEnt3 unpEnt1", sr.getAttributeValue(0));
    }

    /*
    public void testInvalidEntityAttrUse()
        throws XMLStreamException
    {
    }
    */

    /*
    public void testInvalidEntitiesAttrUse()
        throws XMLStreamException
    {
    }
    */

}
