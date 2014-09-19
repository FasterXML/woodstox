package wstxtest.stream;

import javax.xml.stream.*;

import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.validation.XMLValidationException;

/**
 * Set of unit tests that check that Woodstox support for Xml:id works
 * as expected.
 */
public class TestXmlId
    extends BaseStreamTest
{
    final static String XML_WITH_XMLID =
        "<root id='123'>"
        +"<leaf xml:id='abc' />"
        +"<leaf id='foobar'/>"
        +"<leaf xml:id='  _otherId ' />"
        +"</root id='123'>"
        ;

    final static String XML_WITH_XMLID_INVALID =
        "<!DOCTYPE root [\n"
        +"<!ELEMENT root ANY>\n"
        +"<!ATTLIST root xml:id CDATA #IMPLIED>\n"
        +"]><root />"
        ;

    public void testXmlIdEnabledNs()
        throws XMLStreamException
    {
        doTestXmlId(true, true, false); // xmlid enabled, non-coal
        doTestXmlId(true, true, true);  // xmlid enabled, coal
    }

    public void testXmlIdDisabledNs()
        throws XMLStreamException
    {
        doTestXmlId(false, true, false); // xmlid disabled, non-coal
        doTestXmlId(false, true, true); // xmlid disabled, coal
    }

    public void testXmlIdEnabledNonNs()
        throws XMLStreamException
    {
        doTestXmlId(true, false, false); // xmlid enabled, non-coal
        doTestXmlId(true, false, true);  // xmlid enabled, coal
    }

    public void testXmlIdDisabledNonNs()
        throws XMLStreamException
    {
        doTestXmlId(false, false, false); // xmlid disabled, non-coal
        doTestXmlId(false, false, true); // xmlid disabled, coal
    }

    /**
     * This unit test verifies that incorrect DTD attribute type for
     * xml:id causes a validation exception
     */ 
    public void testInvalidXmlIdNs()
        throws XMLStreamException
    {
        doTestInvalid(true, false);
        doTestInvalid(true, true);
    }

    public void testInvalidXmlIdNonNs()
        throws XMLStreamException
    {
        doTestInvalid(false, false);
        doTestInvalid(false, true);
    }

    public void testInvalidXmlIdDisabledNs()
        throws XMLStreamException
    {
        doTestInvalidDisabled(true, false);
        doTestInvalidDisabled(true, true);
    }

    public void testInvalidXmlIdDisabledNonNs()
        throws XMLStreamException
    {
        doTestInvalidDisabled(false, false);
        doTestInvalidDisabled(false, true);
    }

    /*
    /////////////////////////////////////
    //
    /////////////////////////////////////
     */

    private void doTestXmlId(boolean xmlidEnabled,
                             boolean nsAware, boolean coal)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getReader(XML_WITH_XMLID, xmlidEnabled, nsAware, coal);
        final String xmlidType = xmlidEnabled ? "ID" : "CDATA";

        // root:
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("id", sr.getAttributeLocalName(0));
        assertEquals("CDATA", sr.getAttributeType(0));
        assertEquals(-1, sr.getAttributeInfo().getIdAttributeIndex());

        // leaf#1:
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("abc", sr.getAttributeValue(0));
        if (xmlidEnabled) {
            assertEquals(0, sr.getAttributeInfo().getIdAttributeIndex());
        } else {
            assertEquals(-1, sr.getAttributeInfo().getIdAttributeIndex());
        }
        assertEquals(xmlidType, sr.getAttributeType(0));
        assertTokenType(END_ELEMENT, sr.next());

        // leaf#2:
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(1, sr.getAttributeCount());
        assertEquals("foobar", sr.getAttributeValue(0));
        assertEquals("id", sr.getAttributeLocalName(0));
        assertEquals(-1, sr.getAttributeInfo().getIdAttributeIndex());
        assertEquals("CDATA", sr.getAttributeType(0));
        assertTokenType(END_ELEMENT, sr.next());

        // leaf#3:
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(1, sr.getAttributeCount());
        assertEquals(xmlidType, sr.getAttributeType(0));
        if (xmlidEnabled) {
            assertEquals(0, sr.getAttributeInfo().getIdAttributeIndex());
        } else {
            assertEquals(-1, sr.getAttributeInfo().getIdAttributeIndex());
        }

        // also, should be normalized:
        if (xmlidEnabled) {
            assertEquals("_otherId", sr.getAttributeValue(0));
        } else {
            assertEquals("  _otherId ", sr.getAttributeValue(0));
        }
        assertTokenType(END_ELEMENT, sr.next());

        sr.close();
    }

    private void doTestInvalid(boolean nsAware, boolean coal)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getValidatingReader(XML_WITH_XMLID_INVALID, nsAware, coal);
        try {
            assertTokenType(DTD, sr.next());
            assertTokenType(START_ELEMENT, sr.next());
            fail("Expected a validation exception for invalid Xml:id attribute declaration");
        } catch (XMLValidationException vex) {
            //System.err.println("VLD exc -> "+vex);
        }
    }

    private void doTestInvalidDisabled(boolean nsAware, boolean coal)
        throws XMLStreamException
    {
        /* In non-validating mode, shouldn't matter: but just to make sure,
         * let's also disable xml:id processing
         */
        XMLStreamReader2 sr = getReader(XML_WITH_XMLID_INVALID, false, nsAware, coal);
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
    }

    private XMLStreamReader2 getReader(String contents,
                                      boolean xmlidEnabled,
                                      boolean nsAware, boolean coal)
        throws XMLStreamException
    {
        XMLInputFactory2 f = getInputFactory();
        setSupportDTD(f, true);
        setValidating(f, false);
        setCoalescing(f, coal);
        setNamespaceAware(f, nsAware);
        f.setProperty(XMLInputFactory2.XSP_SUPPORT_XMLID,
                      xmlidEnabled
                      ? XMLInputFactory2.XSP_V_XMLID_TYPING
                      : XMLInputFactory2.XSP_V_XMLID_NONE);
        return constructStreamReader(f, contents);
    }

    private XMLStreamReader2 getValidatingReader(String contents,
                                                boolean nsAware, boolean coal)
        throws XMLStreamException
    {
        XMLInputFactory2 f = getInputFactory();
        setSupportDTD(f, true);
        setValidating(f, true);
        setCoalescing(f, coal);
        setNamespaceAware(f, nsAware);
        f.setProperty(XMLInputFactory2.XSP_SUPPORT_XMLID,
                      XMLInputFactory2.XSP_V_XMLID_TYPING);
        return constructStreamReader(f, contents);
    }
}
