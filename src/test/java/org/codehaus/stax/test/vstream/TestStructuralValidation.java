package org.codehaus.stax.test.vstream;

import javax.xml.stream.*;

/**
 * Unit test suite that tests structural validation using DTD.
 */
public class TestStructuralValidation
    extends BaseVStreamTest
{
    public TestStructuralValidation() {
        super();
        // Uncomment to see if we get exceptions we should be getting:
        //PRINT_EXP_EXCEPTION = true;
    }

    public void testValidStructure()
        throws XMLStreamException
    {
        for (int i = 0; i < 2; ++i) {
            boolean nsAware = (i > 0);
            String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root (branch+, end)>\n"
                +"<!ELEMENT branch (#PCDATA)>\n"
                +"<!ELEMENT end EMPTY>\n"
                +"]>\n<root><branch />  <branch>Text</branch>  <end /> </root>";
            streamThrough(getReader(XML, nsAware));
        }
    }

    public void testValidStructure2()
        throws XMLStreamException
    {
        for (int i = 0; i < 2; ++i) {
            boolean nsAware = (i > 0);
            String XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root (branch+)>\n"
                +"<!ELEMENT branch (branch | leaf)*>\n"
                +"<!ELEMENT leaf (#PCDATA)>\n"
                +"]>\n<root><branch /> <branch> <leaf>text</leaf></branch></root>";
            streamThrough(getReader(XML, nsAware));

            // Ok, as leaf is optional...
            XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root (leaf?)>\n"
                +"<!ELEMENT leaf (#PCDATA)>\n"
                +"]>\n<root></root>";
            streamThrough(getReader(XML, nsAware));

            XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root (leaf?)>\n"
                +"<!ELEMENT leaf (#PCDATA)>\n"
                +"]>\n<root>   <leaf>text &amp; and more</leaf></root>";
            streamThrough(getReader(XML, nsAware));
        }
    }

    public void testValidSimpleSeqStructure()
        throws XMLStreamException
    {
        for (int i = 0; i < 2; ++i) {
            boolean nsAware = (i > 0);
            String XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root (a1, a2, a3, a4)>\n"
                +"<!ELEMENT a1 EMPTY>\n"
                +"<!ELEMENT a2 (#PCDATA)>\n"
                +"<!ELEMENT a3 EMPTY>\n"
                +"<!ELEMENT a4 (#PCDATA)>\n"
                +"]>\n"
                +"<root>\n"
                +"<a1 /><a2></a2><a3 /><a4></a4>"
                +"</root>";
            streamThrough(getReader(XML, nsAware));
        }
    }

    public void testInvalidSimpleSeqStructure()
        throws XMLStreamException
    {
        for (int i = 0; i < 2; ++i) {
            boolean nsAware = (i > 0);
            String XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root (a1, a2, a3, a4)>\n"
                +"<!ELEMENT a1 EMPTY>\n"
                +"<!ELEMENT a2 (#PCDATA)>\n"
                +"<!ELEMENT a3 EMPTY>\n"
                +"<!ELEMENT a4 (#PCDATA)>\n"
                +"]>\n"
                +"<root>\n"
                +"<a1 /><a2></a2><a4></a4>"
                +"</root>";
            streamThroughFailing(getReader(XML, nsAware),
                                 "invalid simple content sequence: missing 'a3' element");

            XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root (a1, a2, a3, a4)>\n"
                +"<!ELEMENT a1 EMPTY>\n"
                +"<!ELEMENT a2 (#PCDATA)>\n"
                +"<!ELEMENT a3 EMPTY>\n"
                +"<!ELEMENT a4 (#PCDATA)>\n"
                +"]>\n"
                +"<root>\n"
                +"<a1 /><a2></a2><a3 />"
                +"</root>";
            streamThroughFailing(getReader(XML, nsAware),
                                 "invalid simple content sequence: missing 'a4' element");
        }
    }

    public void testValidSimpleChoiceStructure()
        throws XMLStreamException
    {
        for (int i = 0; i < 2; ++i) {
            boolean nsAware = (i > 0);
            String XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root (a1 | a2 | a3 | a4 | a5 | b1 | b2 | b3 | b4)*>\n"
                +"<!ELEMENT a1 EMPTY>\n"
                +"<!ELEMENT a2 (#PCDATA)>\n"
                +"<!ELEMENT a3 EMPTY>\n"
                +"<!ELEMENT a4 (#PCDATA)>\n"
                +"<!ELEMENT a5 (#PCDATA)>\n"
                +"<!ELEMENT b1 EMPTY>\n"
                +"<!ELEMENT b2 EMPTY>\n"
                +"<!ELEMENT b3 EMPTY>\n"
                +"<!ELEMENT b4 EMPTY>\n"
                +"]>\n"
                +"<root>\n"
                +"<a1 /><a2></a2><a3 /><b1 /><a4></a4>"
                +"</root>";
            streamThrough(getReader(XML, nsAware));
        }
    }

    public void testInvalidSimpleChoiceStructure()
        throws XMLStreamException
    {
        for (int i = 0; i < 2; ++i) {
            boolean nsAware = (i > 0);
            String XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root (a1 | a2 | a3 | a4)+>\n"
                +"<!ELEMENT a1 EMPTY>\n"
                +"<!ELEMENT a2 (#PCDATA)>\n"
                +"<!ELEMENT a3 EMPTY>\n"
                +"<!ELEMENT a4 (#PCDATA)>\n"
                +"]>\n"
                +"<root>\n"
                +"</root>";
            streamThroughFailing(getReader(XML, nsAware),
                                 "invalid choice content sequence: no children for root");

            XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root (a1 | a2 | a3 | a4)?>\n"
                +"<!ELEMENT a1 EMPTY>\n"
                +"<!ELEMENT a2 (#PCDATA)>\n"
                +"<!ELEMENT a3 EMPTY>\n"
                +"<!ELEMENT a4 (#PCDATA)>\n"
                +"]>\n"
                +"<root><a1 /><a3 />"
                +"</root>";
            streamThroughFailing(getReader(XML, nsAware),
                                 "invalid choice content sequence: more than one child");

            XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root (a1|a2|a3|a4|a5|b1|b2|b3|b4|b5)?>\n"
                +"<!ELEMENT a1 EMPTY><!ELEMENT a2 EMPTY><!ELEMENT a3 EMPTY><!ELEMENT a4 EMPTY><!ELEMENT a5 EMPTY>\n"
                +"<!ELEMENT b1 EMPTY><!ELEMENT b2 EMPTY><!ELEMENT b3 EMPTY><!ELEMENT b4 EMPTY><!ELEMENT b5 EMPTY>\n"
                +"<!ELEMENT c1 EMPTY>\n"
                +"]>\n"
                +"<root><a1 /><c1 />"
                +"</root>";
            streamThroughFailing(getReader(XML, nsAware),
                                 "invalid choice content sequence: c1 not one of legal children for root");
        }
    }

    public void testValidFullChoiceStructure()
        throws XMLStreamException
    {
        for (int i = 0; i < 2; ++i) {
            boolean nsAware = (i > 0);
            String XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root ((a1 | a2)+ | (a3 | a4))>\n"
                +"<!ELEMENT a1 EMPTY>\n"
                +"<!ELEMENT a2 (#PCDATA)>\n"
                +"<!ELEMENT a3 EMPTY>\n"
                +"<!ELEMENT a4 (#PCDATA)>\n"
                +"]>\n"
                +"<root>\n"
                +"<a1 />"
                +"</root>";
            streamThrough(getReader(XML, nsAware));
        }
    }

    public void testValidMixed()
        throws XMLStreamException
    {
        for (int i = 0; i < 2; ++i) {
            boolean nsAware = (i > 0);
            String XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root (#PCDATA | leaf)*>\n"
                +"<!ELEMENT leaf (#PCDATA)>\n"
                +"]><root>Text <leaf /></root>";
            streamThrough(getReader(XML, nsAware));
        }
    }

    public void testInvalidWrongRoot()
        throws XMLStreamException
    {
        for (int i = 0; i < 2; ++i) {
            boolean nsAware = (i > 0);
            String XML = "<!DOCTYPE root>  <fubar />";
            streamThroughFailing(getReader(XML, nsAware), "wrong root element");
        }
    }

    public void testInvalidMixed()
        throws XMLStreamException
    {
        for (int i = 0; i < 2; ++i) {
            boolean nsAware = (i > 0);
            String XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root (leaf)*>\n"
                +"<!ELEMENT leaf (#PCDATA)>\n"
                +"]><root>Text <leaf></leaf></root>";
            streamThroughFailing(getReader(XML, nsAware),
                                 "invalid mixed content");

            // same, but after a child elem...
            XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root (leaf)*>\n"
                +"<!ELEMENT leaf (#PCDATA)>\n"
                +"]><root>   <leaf></leaf>   x </root>";
            streamThroughFailing(getReader(XML, nsAware),
                                 "invalid mixed content");
        }
    }

    public void testInvalidStructureRoot()
        throws XMLStreamException
    {
        for (int i = 0; i < 2; ++i) {
            boolean nsAware = (i > 0);

            // First, wrong root element
            String XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root (branch+, end)>\n"
                +"<!ELEMENT branch (#PCDATA)>\n"
                +"<!ELEMENT end EMPTY>\n"
                +"]>\n<branch />";
            streamThroughFailing(getReader(XML, nsAware),
                                 "wrong root element");
            
            // Then undeclared (root) element
            XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT branch EMPTY>\n"
                +"]>\n  <root />";
            streamThroughFailing(getReader(XML, nsAware),
                                 "undeclared element");
            
            // Then one wrong element content for root
            XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root (branch+, end)>\n"
                +"<!ELEMENT branch (#PCDATA)>\n"
                +"<!ELEMENT end EMPTY>\n"
                +"]>\n  <root />";
            streamThroughFailing(getReader(XML, nsAware),
                                 "wrong element content (expected branch+, end; got nothing) for root");
        }
    }

    public void testInvalidStructure()
        throws XMLStreamException
    {
        for (int i = 0; i < 2; ++i) {
            boolean nsAware = (i > 0);

            // And then just wrong ordering of child elements
            String XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root (branch+, end)>\n"
                +"<!ELEMENT branch (#PCDATA)>\n"
                +"<!ELEMENT end EMPTY>\n"
                +"]>\n  <root><end /></root>";
            streamThroughFailing(getReader(XML, nsAware),
                                 "wrong element content (ordering) for root");
            
            XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root (branch+, end)>\n"
                +"<!ELEMENT branch (#PCDATA)>\n"
                +"<!ELEMENT end EMPTY>\n"
                +"]>\n  <root><branch>xyz</branch></root>";
            streamThroughFailing(getReader(XML, nsAware),
                                 "wrong element content (missing 'end' element) for root");

            XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root (branch | child)+>\n"
                +"<!ELEMENT branch (#PCDATA)>\n"
                +"<!ELEMENT child EMPTY>\n"
                +"]>\n  <root></root>";
            streamThroughFailing(getReader(XML, nsAware),
                                 "missing children for root");

            XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root (branch | child)+>\n"
                +"<!ELEMENT branch (child?)>\n"
                +"<!ELEMENT child EMPTY>\n"
                +"<!ELEMENT other EMPTY>\n"
                +"]>\n  <root><child /><branch> <other /> </branch></root>";
            streamThroughFailing(getReader(XML, nsAware),
                                 "wrong child element for branch");
        }
    }

    final static String COMPLEX_DTD =
        "<!ELEMENT root (first?, second+, (third | fourth))>\n"
        +"<!ELEMENT first EMPTY>\n"
        +"<!ELEMENT second EMPTY>\n"
        +"<!ELEMENT third (a, b, (c | d)?, e*)>\n"
        +"<!ELEMENT a (#PCDATA)>\n"
        +"<!ELEMENT b (#PCDATA)>\n"
        +"<!ELEMENT c (#PCDATA)>\n"
        +"<!ELEMENT d (#PCDATA)>\n"
        +"<!ELEMENT e (#PCDATA)>\n"
        +"<!ELEMENT fourth (#PCDATA)>\n"
        ;

    public void testValidStructureComplex()
        throws XMLStreamException
    {
        for (int i = 0; i < 2; ++i) {
            boolean nsAware = (i > 0);

            // And then just wrong ordering of child elements
            String XML = "<!DOCTYPE root [ "+COMPLEX_DTD+" ]>"
                +"<root>"
                +" <second />"
                +" <third>"
                +" <a /><b /><d /><e/><e></e>"
                +" </third>"
                +"</root>"
                ;
            streamThrough(getReader(XML, nsAware));
        }
    }

    public void testInvalidStructureComplex()
        throws XMLStreamException
    {
        for (int i = 0; i < 2; ++i) {
            boolean nsAware = (i > 0);

            // And then just wrong ordering of child elements
            String XML = "<!DOCTYPE root [ "+COMPLEX_DTD+" ]>"
                +"<root>"
                +" <second />"
                +" <third>"
                // b is missing:
                +" <a /><d /><e/><e></e>"
                +" </third>"
                +"</root>"
                ;
            streamThroughFailing(getReader(XML, nsAware),
                                 "wrong element structure; missing element <b>");
        }
    }

    /**
     * Unit test that checks that it's illegal to add any content (including
     * comment, processing instructions or white space) within an element that has
     * content declaration of EMPTY.
     */
    public void testInvalidEmpty()
        throws XMLStreamException
    {
        for (int i = 0; i < 2; ++i) {
            boolean nsAware = (i > 0);
            String XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root EMPTY>\n"
                +"]><root><!-- comment --></root>";
            streamThroughFailing(getReader(XML, nsAware),
                                 "comment within element that has EMPTY content type declaration");

            XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root EMPTY>\n"
                +"]><root><?proc instr?></root>";
            streamThroughFailing(getReader(XML, nsAware),
                                 "processing instruction within element that has EMPTY content type declaration");

            XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root EMPTY>\n"
                +"]><root>     </root>";
            streamThroughFailing(getReader(XML, nsAware),
                                 "white space within element that has EMPTY content type declaration");

            XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root EMPTY>\n"
                +"<!ELEMENT leaf EMPTY>\n"
                +"]><root><branch /></root>";
            streamThroughFailing(getReader(XML, nsAware),
                                 "element within element that has EMPTY content type declaration");
        }
    }

    public void testValidAny()
        throws XMLStreamException
    {
        for (int i = 0; i < 2; ++i) {
            boolean nsAware = (i > 0);
            String XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root ANY>\n"
                +"<!ELEMENT branch EMPTY>\n"
                +"<!ELEMENT leaf EMPTY>\n"
                +"]><root>  <leaf />  <branch></branch></root>";
            streamThrough(getReader(XML, nsAware));
        }
    }

    public void testInvalidAny()
        throws XMLStreamException
    {
        for (int i = 0; i < 2; ++i) {
            boolean nsAware = (i > 0);
            String XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root ANY>\n"
                +"]><root><unknown /></root>";
            streamThroughFailing(getReader(XML, nsAware),
                                 "undeclared element in element with ANY content type");
        }
    }

    /*
    ////////////////////////////////////////
    // Private methods, other
    ////////////////////////////////////////
     */

    private XMLStreamReader getReader(String contents, boolean nsAware)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setCoalescing(f, false); // shouldn't really matter
        setNamespaceAware(f, nsAware);
        setSupportDTD(f, true);
        setValidating(f, true);
        return constructStreamReader(f, contents);
    }
}
