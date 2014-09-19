package stax2.vwstream;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.stax2.XMLStreamWriter2;
import org.codehaus.stax2.validation.*;

/**
 * Unit tests for testing structural validation (except for test for
 * special content types like EMPTY and ANY).
 */
public class TestStructuralValidation
    extends BaseOutputTest
{
    final String NS_PREFIX = "ns";
    final String NS_PREFIX2 = "ns2";
    final String NS_URI = "http://ns";

    final String SIMPLE_DTD =
        "<!ELEMENT root (branch+, end)>\n"
        +"<!ELEMENT branch (#PCDATA)>\n"
        +"<!ELEMENT end EMPTY>\n"
        +"<!ATTLIST end endAttr CDATA #IMPLIED>\n"
        ;

    final String SIMPLE_NS_DTD =
        "<!ELEMENT "+NS_PREFIX+":root (branch*)>\n"
        +"<!ELEMENT branch (#PCDATA)>\n"
        ;

    public void testInvalidRootElem()
        throws XMLStreamException
    {
        for (int i = 0; i < 3; ++i) {
            boolean nsAware, repairing;
            String modeDesc;

            switch (i) {
            case 0:
                modeDesc = "[non-namespace-aware]";
                nsAware = repairing = false;
                break;
            case 1:
                modeDesc = "[namespace-aware, non-repairing]";
                nsAware = true;
                repairing = false;
                break;
            default:
                modeDesc = "[namespace-aware, repairing]";
                nsAware = repairing = true;
                break;
            }

            StringWriter strw = new StringWriter();
            
            /* Ok; can test for "wrong" root element only if we explicitly
             * output DOCTYPE declaration with specific name...
             */
            XMLStreamWriter2 sw = getDTDValidatingWriter(strw, SIMPLE_DTD, nsAware, repairing);
            sw.writeDTD("root", "http://foo", "public-id", SIMPLE_DTD);
            try {
                sw.writeStartElement("branch");
                fail(modeDesc+" Expected a validation exception when trying to write wrong root element");
            } catch (XMLValidationException vex) {
                // expected...
            }
            // should not continue after exception; state may not be valid
            
            // And then undeclared root:
            sw = getDTDValidatingWriter(strw, SIMPLE_DTD, nsAware, repairing);
            try {
                sw.writeStartElement("undefined");
                fail(modeDesc+" Expected a validation exception when trying to write an undefined root element");
            } catch (XMLValidationException vex) {
                // expected...
            }
            
            // and same for explicitly empty element; wrong root
            sw = getDTDValidatingWriter(strw, SIMPLE_DTD, nsAware, repairing);
            sw.writeDTD("root", "http://foo", "public-id", SIMPLE_DTD);
            try {
                sw.writeEmptyElement("branch");
                fail(modeDesc+" Expected a validation exception when trying to write wrong root element");
            } catch (XMLValidationException vex) {
                // expected...
            }
        }
    }

    public void testValidStructure()
        throws XMLStreamException
    {
        for (int i = 0; i < 3; ++i) {
            boolean nsAware = (i >= 1);
            boolean repairing = (i == 2);

            StringWriter strw = new StringWriter();
            XMLStreamWriter2 sw = getDTDValidatingWriter(strw, SIMPLE_DTD, nsAware, repairing);
            sw.writeStartElement("root");
            sw.writeCharacters("  "); // imitating indentation
            sw.writeStartElement("branch");
            sw.writeEndElement();
            sw.writeStartElement("branch");
            sw.writeCharacters("test");
            sw.writeComment("comment");
            sw.writeEndElement();
            sw.writeEmptyElement("branch");
            sw.writeEmptyElement("end");
            sw.writeAttribute("endAttr", "value");
            sw.writeCharacters("\n"); // imitating indentation
            sw.writeEndElement(); // for root
        }
    }

    public void testInvalidStructure()
        throws XMLStreamException
    {
        for (int i = 0; i < 3; ++i) {
            boolean nsAware, repairing;
            String modeDesc;

            switch (i) {
            case 0:
                modeDesc = "[non-namespace-aware]";
                nsAware = repairing = false;
                break;
            case 1:
                modeDesc = "[namespace-aware, non-repairing]";
                nsAware = true;
                repairing = false;
                break;
            default:
                modeDesc = "[namespace-aware, repairing]";
                nsAware = repairing = true;
                break;
            }

            StringWriter strw = new StringWriter();
            
            // Let's try omitting the end element, first...

            XMLStreamWriter2 sw = getDTDValidatingWriter(strw, SIMPLE_DTD, nsAware, repairing);
            sw.writeStartElement("root");
            sw.writeCharacters("  "); // imitating indentation
            sw.writeStartElement("branch");
            sw.writeEndElement();
            sw.writeStartElement("branch");
            sw.writeCharacters("test");
            sw.writeComment("comment");
            sw.writeEndElement();
            sw.writeEmptyElement("branch");
            sw.writeCharacters("\n"); // imitating indentation
            try {
                sw.writeEndElement(); // for root
                fail(modeDesc+" Expected a validation exception when omitting non-optional <end> element");
            } catch (XMLValidationException vex) {
                // expected...
            }
            // should not continue after exception; state may not be valid

            // And then leaving out branch...
            sw = getDTDValidatingWriter(strw, SIMPLE_DTD, nsAware, repairing);
            sw.writeStartElement("root");
            sw.writeCharacters("  "); // imitating indentation
            sw.writeComment("comment");
            try {
                sw.writeEmptyElement("end");
                fail(modeDesc+" Expected a validation exception when omitting non-optional <branch> element");
            } catch (XMLValidationException vex) {
                // expected...
            }
        }
    }

    public void testValidNsElem()
        throws XMLStreamException
    {
        for (int i = 0; i < 3; ++i) {
            boolean repairing = (i == 2);
            StringWriter strw = new StringWriter();

            XMLStreamWriter2 sw = getDTDValidatingWriter(strw, SIMPLE_NS_DTD, true, repairing);
            // prefix, local name, uri (for elems)
            sw.writeStartElement(NS_PREFIX, "root", NS_URI);
            if (!repairing) {
                sw.writeNamespace(NS_PREFIX, NS_URI);
            }
            sw.writeEndElement();
            sw.writeEndDocument();
            sw.close();

            // and same with empty elem
            sw = getDTDValidatingWriter(strw, SIMPLE_NS_DTD, true, repairing);
            sw.writeEmptyElement(NS_PREFIX, "root", NS_URI);
            if (!repairing) {
                sw.writeNamespace(NS_PREFIX, NS_URI);
            }
            sw.writeEndDocument();
            sw.close();
        }
    }

    /**
     * Let's also do quick testing on structure that would be ok but
     * where namespace prefix is not what dtd expects...
     */
    public void testInvalidNsElem()
        throws XMLStreamException
    {
        for (int i = 0; i < 2; ++i) {
            boolean repairing;
            String modeDesc;

            switch (i) {
            case 0:
                modeDesc = "[namespace-aware, non-repairing]";
                repairing = false;
                break;
            default:
                modeDesc = "[namespace-aware, repairing]";
                repairing = true;
                break;
            }

            StringWriter strw = new StringWriter();
            
            // Let's try omitting the end element, first...

            XMLStreamWriter2 sw = getDTDValidatingWriter(strw, SIMPLE_NS_DTD, true, repairing);
            // prefix, local name, uri (for elems)
            try {
                sw.writeStartElement(NS_PREFIX2, "root", NS_URI);
                fail(modeDesc+" Expected a validation exception when passing wrong (unexpected) ns for element");
            } catch (XMLValidationException vex) {
                // expected...
            }
            // should not continue after exception; state may not be valid

            // and then the same for empty elem
            sw = getDTDValidatingWriter(strw, SIMPLE_NS_DTD, true, repairing);
            // prefix, local name, uri (for elems)
            try {
                sw.writeEmptyElement(NS_PREFIX2, NS_URI, "root");
                fail(modeDesc+" Expected a validation exception when passing wrong (unexpected) ns for element");
            } catch (XMLValidationException vex) {
                // expected...
            }

            // Oh, and finally, using non-ns DTD:
            sw = getDTDValidatingWriter(strw, SIMPLE_DTD, true, repairing);
            // prefix, local name, uri (for elems)
            try {
                sw.writeEmptyElement(NS_PREFIX, NS_URI, "root");
                fail(modeDesc+" Expected a validation exception when passing wrong (unexpected) ns for element");
            } catch (XMLValidationException vex) {
                // expected...
            }
        }
    }
}

