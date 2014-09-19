package wstxtest.vstream;

import javax.xml.stream.*;

import org.codehaus.stax2.validation.*;

import wstxtest.stream.BaseStreamTest;

/**
 * Simple test for ensuring handling of "xml:space" attribute. Not
 * sure if this should go in the main StaxTest (or perhaps Stax2), since
 * it's not 100% clear if the validity violations should result in
 * XMLStreamException, or something else.
 */
public class TestXmlSpace
    extends BaseStreamTest
{
    public void testSimpleNonNs()
        throws XMLStreamException
    {
	// First, legal declarations:
	for (int i = 0; i < 2; ++i) {
	    boolean nsAware = (i > 0);

	    String XML = "<!DOCTYPE root [\n"
		+"<!ELEMENT root ANY>\n"
		+"<!ATTLIST root xml:space (preserve | default) #IMPLIED>\n"
		+"]><root/>";
	    XMLStreamReader sr = getReader(XML, nsAware);
	    assertTokenType(DTD, sr.next());
	    assertTokenType(START_ELEMENT, sr.next());
	    assertTokenType(END_ELEMENT, sr.next());
	    sr.close();
	    
	    XML = "<!DOCTYPE root [\n"
		+"<!ELEMENT root ANY>\n"
		+"<!ATTLIST root xml:space (preserve) #FIXED 'preserve'>\n"
		+"]><root/>";
	    sr = getReader(XML, nsAware);
	    assertTokenType(DTD, sr.next());
	    assertTokenType(START_ELEMENT, sr.next());
	    assertTokenType(END_ELEMENT, sr.next());
	    sr.close();
	    
	    // And then some non-legal ones:
	    XML = "<!DOCTYPE root [\n"
		+"<!ELEMENT root ANY>\n"
		+"<!ATTLIST root xml:space CDATA #IMPLIED>\n"
		+"]><root/>";
	    sr = getReader(XML, nsAware);
	    try {
		int type = sr.next();
		assertTokenType(DTD, type);
		type = sr.next();
		assertTokenType(START_ELEMENT, type);
		fail("Expected a validity exception for invalid xml:space declaration (ns-aware: "+nsAware+")");
	    } catch (XMLValidationException vex) {
		; // good
	    }
	    sr.close();

	    XML = "<!DOCTYPE root [\n"
		+"<!ELEMENT root ANY>\n"
		+"<!ATTLIST root xml:space (default | foobar) #IMPLIED>\n"
		+"]><root/>";
	    sr = getReader(XML, nsAware);
	    try {
		int type = sr.next();
		assertTokenType(DTD, type);
		type = sr.next();
		assertTokenType(START_ELEMENT, type);
		fail("Expected a validity exception for invalid xml:space declaration (ns-aware: "+nsAware+")");
	    } catch (XMLValidationException vex) {
		; // good
	    }
	    sr.close();
	}
    }

    /*
    //////////////////////////////////////////////////
    // Helper methods
    //////////////////////////////////////////////////
     */

    private XMLStreamReader getReader(String xml, boolean nsAware)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setNamespaceAware(f, nsAware);
        setSupportDTD(f, true);
        setValidating(f, true);
        return constructStreamReader(f, xml);
    }
}
