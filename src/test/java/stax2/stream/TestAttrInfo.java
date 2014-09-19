package stax2.stream;

import javax.xml.stream.*;

import org.codehaus.stax2.*;

import stax2.BaseStax2Test;

/**
 * Set of unit tests that checks that the {@link AttributeInfo} implementation
 * works as expected.
 *
 * @author Tatu Saloranta
 */
public class TestAttrInfo
    extends BaseStax2Test
{
    final static String DEFAULT_VALUE = "default value";

    final static String TEST_DOC_BASIC =
        "<?xml version='1.0'?>"
        +"<root idAttr='idValue' textAttr='value' notation='not2' textAttr3='1'"
        +"><leaf />"
        +"</root>";

    final static String TEST_DOC_DTD =
        "<?xml version='1.0'?>"
        +"<!DOCTYPE root [\n"
        +"<!ELEMENT root (leaf)>\n"
        +"<!ATTLIST root defaultAttr CDATA '"+DEFAULT_VALUE+"'>\n"
        +"<!ATTLIST root textAttr CDATA #IMPLIED>\n"
        +"<!ATTLIST root idAttr ID #IMPLIED>\n"
        +"<!NOTATION not1 PUBLIC 'some-public-id'>\n"
        +"<!NOTATION not2 PUBLIC 'other-public-id'>\n"
        +"<!ATTLIST root notation NOTATION (not1 | not2) #IMPLIED>\n"
        +"<!ATTLIST root textAttr2 CDATA #IMPLIED>\n"
        +"<!ATTLIST root textAttr3 CDATA #IMPLIED>\n"
        +"<!ELEMENT leaf EMPTY>\n"
        +"<!ATTLIST leaf dummyAttr CDATA #IMPLIED>\n"
        +"]>"
        +"<root idAttr='idValue' textAttr='value' notation='not2' textAttr3='1'"
        +"><leaf />"
        +"</root>";

    /**
     * Baseline test case that does not use any information originating
     * from DTDs.
     */
    public void testAttrFindBasic()
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getAttrReader(TEST_DOC_BASIC);

        // Let's verify basic facts...
        assertEquals(4, sr.getAttributeCount());

        AttributeInfo info = sr.getAttributeInfo();
        assertNotNull(info);

        // And then see if we can find the attributes
        {
            int ix = info.findAttributeIndex(null, "textAttr");
            if (ix < 0) {
                fail("Failed to find index of attribute 'textAttr'");
            }
            assertEquals("value", sr.getAttributeValue(ix));

            ix = info.findAttributeIndex(null, "textAttr2");
            if (ix >= 0) {
                fail("Found a phantom index for (missing) attribute 'textAttr2'");
            }

            ix = info.findAttributeIndex(null, "textAttr3");
            if (ix < 0) {
                fail("Failed to find index of attribute 'textAttr3'");
            }
            assertEquals("1", sr.getAttributeValue(ix));
        }

        // Notation attr?
        {
            int ix = info.findAttributeIndex(null, "notation");
            if (ix < 0) {
                fail("Failed to find index of attribute 'notation'");
            }
            assertEquals("not2", sr.getAttributeValue(ix));
            // No DTD info available, should NOT find via this:

            int notIx = info.getNotationAttributeIndex();
            if (notIx >= 0) {
                fail("Found a bogus notation attribute index ("+notIx+")");
            }
        }

        // Ok, how about the id attr?
        {
            int ix = info.findAttributeIndex(null, "idAttr");
            if (ix < 0) {
                fail("Failed to find index of attribute 'id'");
            }
            assertEquals("idValue", sr.getAttributeValue(ix));

            // .. but not by type, as that depends on DTD or Xml:id
        }

        // Ok; but how about of non-existing ones?
        assertEquals(-1, info.findAttributeIndex(null, "foo"));
        assertEquals(-1, info.findAttributeIndex("http://foo", "id"));

        // and then the other (empty) element:
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(0, sr.getAttributeCount());
        assertEquals(-1, info.findAttributeIndex(null, "dummyAttr"));

        finishAttrReader(sr);
    }

    /**
     * More complex test case, in which information from DTD
     * (like attribute default values, notations) are needed.
     */
    public void testAttrFindDTD()
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getAttrReader(TEST_DOC_DTD);

        // Let's verify basic facts...
        assertEquals(5, sr.getAttributeCount());

        AttributeInfo info = sr.getAttributeInfo();
        assertNotNull(info);

        // And then see if we can find the attributes
        {
            int ix = info.findAttributeIndex(null, "textAttr");
            if (ix < 0) {
                fail("Failed to find index of attribute 'textAttr'");
            }
            assertEquals("value", sr.getAttributeValue(ix));

            ix = info.findAttributeIndex(null, "textAttr2");
            if (ix >= 0) {
                fail("Found a phantom index for (missing) attribute 'textAttr2'");
            }

            ix = info.findAttributeIndex(null, "textAttr3");
            if (ix < 0) {
                fail("Failed to find index of attribute 'textAttr3'");
            }
            assertEquals("1", sr.getAttributeValue(ix));
        }

        // Notation attr?
        {
            int ix = info.findAttributeIndex(null, "notation");
            if (ix < 0) {
                fail("Failed to find index of attribute 'notation'");
            }
            int notIx = info.getNotationAttributeIndex();
            if (notIx < 0) {
                fail("Failed to find index of the notation attribute");
            }
            assertEquals(ix, notIx);
            assertEquals("not2", sr.getAttributeValue(notIx));
        }

        // Ok, how about the id attr?
        {
            int ix = info.findAttributeIndex(null, "idAttr");
            if (ix < 0) {
                fail("Failed to find index of attribute 'id'");
            }
            int idIx = info.getIdAttributeIndex();
            if (idIx < 0) {
                fail("Failed to find index of the id attribute");
            }
            assertEquals(ix, idIx);
            assertEquals("idValue", sr.getAttributeValue(idIx));
        }

        // Ok; but how about of non-existing ones?
        assertEquals(-1, info.findAttributeIndex(null, "foo"));
        assertEquals(-1, info.findAttributeIndex("http://foo", "id"));

        // and then the other (empty) element:
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(0, sr.getAttributeCount());
        assertEquals(-1, info.findAttributeIndex(null, "dummyAttr"));

        finishAttrReader(sr);
    }

    /*
    ////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////
     */

    private XMLStreamReader2 getAttrReader(String str)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getReader(str);
        assertTokenType(START_DOCUMENT, sr.getEventType());
        int type = sr.next();
        if (type == DTD) {
            type = sr.next();
        }
        assertTokenType(START_ELEMENT, type);
        return sr;
    }

    private void finishAttrReader(XMLStreamReader sr)
        throws XMLStreamException
    {
        while (sr.getEventType() != END_DOCUMENT) {
            sr.next();
        }
    }

    private XMLStreamReader2 getReader(String contents)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setCoalescing(f, false); // shouldn't really matter
        setNamespaceAware(f, true);
        setSupportDTD(f, true);
        /* Probably need to enable validation, to get all the attribute
         * type info processed and accessible?
         */
        setValidating(f, true);
        return constructStreamReader(f, contents);
    }
}
