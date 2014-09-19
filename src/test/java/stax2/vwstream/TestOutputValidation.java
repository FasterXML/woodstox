package stax2.vwstream;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.stax2.XMLStreamWriter2;
import org.codehaus.stax2.validation.*;

/**
 * Unit test suite that test basic aspects of (DTD validation,
 * mostly regarding specialized content types (EMPTY, ANY, #PCDATA)
 * 
 */
public class TestOutputValidation
    extends BaseOutputTest
{
    public void testValidMixedContent()
        throws XMLStreamException
    {
        final String dtdStr =
            "<!ELEMENT root (#PCDATA | branch)*>\n"
            +"<!ELEMENT branch (branch)*>\n"
        ;

        for (int i = 0; i < 3; ++i) {
            boolean nsAware = (i >= 1);
            boolean repairing = (i == 2);
            StringWriter strw = new StringWriter();
            XMLStreamWriter2 sw = getDTDValidatingWriter(strw, dtdStr, nsAware, repairing);
            sw.writeStartElement("root");
            // Should be fine now
            sw.writeCharacters("Text that should be ok");
            sw.writeStartElement("branch");
            // Also, all-whitespace is ok in non-mixed too
            sw.writeCharacters("\t \t   \r   \n");
            sw.writeEndElement();
            sw.writeEndElement();
            sw.writeEndDocument();
        }
    }

    public void testInvalidMixedContent()
        throws XMLStreamException
    {
        final String dtdStr =
            "<!ELEMENT root (branch)>\n"
            +"<!ELEMENT branch ANY>\n"
        ;

        for (int i = 0; i < 3; ++i) {
            boolean nsAware = (i >= 1);
            boolean repairing = (i == 2);
            StringWriter strw = new StringWriter();
            XMLStreamWriter2 sw = getDTDValidatingWriter(strw, dtdStr, nsAware, repairing);
            sw.writeStartElement("root");
            // Should get validation exception here:
            try {
                sw.writeCharacters("Illegal text!");
                fail("Expected a validation exception for non-whitespace text output on non-mixed element content");
            } catch (XMLValidationException vex) {
                // expected...
            }
        }
    }

    public void testValidEmptyContent()
        throws XMLStreamException
    {
        final String dtdStr = "<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root attr CDATA #IMPLIED>\n";

        for (int i = 0; i < 3; ++i) {
            boolean nsAware = (i >= 1);
            boolean repairing = (i == 2);
            StringWriter strw = new StringWriter();

            XMLStreamWriter2 sw = getDTDValidatingWriter(strw, dtdStr, nsAware, repairing);

            sw.writeStartElement("root");
            // No content whatsoever is allowed...
            sw.writeEndElement();
            sw.writeEndDocument();
            sw.close();

            // Next; same but with an attribute
            sw = getDTDValidatingWriter(strw, dtdStr, nsAware, repairing);

            sw.writeStartElement("root");
            // no content, but attribute is fine
            sw.writeAttribute("attr", "value");

            sw.writeEndElement();
            sw.writeEndDocument();
            sw.close();

            // And then using empty element write method(s)
            sw = getDTDValidatingWriter(strw, dtdStr, nsAware, repairing);
            sw.writeEmptyElement("root");
            // note: empty element need/can not be closed
            sw.writeEndDocument();
            sw.close();

            // and finally empty with attribute
            sw = getDTDValidatingWriter(strw, dtdStr, nsAware, repairing);
            sw.writeEmptyElement("root");
            sw.writeAttribute("attr", "otherValue");
            sw.writeEndDocument();
            sw.close();
        }
    }

    public void testInvalidEmptyContent()
        throws XMLStreamException
    {
        final String dtdStr = "<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root attr CDATA #IMPLIED>\n"
            +"<!ELEMENT leaf ANY>\n"
            ;

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

            // No content whatsoever is allowed with EMPTY.
            // Let's first test with a regualr child element:

            XMLStreamWriter2 sw = getDTDValidatingWriter(strw, dtdStr, nsAware, repairing);
            sw.writeStartElement("root");
            try {
                sw.writeStartElement("leaf");
                fail(modeDesc+" Expected a validation exception when trying to add an element into EMPTY content model");
            } catch (XMLValidationException vex) {
                // expected...
            }
            sw.close();

            // Then with an empty child
            sw = getDTDValidatingWriter(strw, dtdStr, nsAware, repairing);
            sw.writeStartElement("root");
            try {
                sw.writeEmptyElement("leaf");
                fail(modeDesc+" Expected a validation exception when trying to add an element into EMPTY content model");
            } catch (XMLValidationException vex) {
                // expected...
            }
            sw.close();

            // Then with any text (even just white space):
            sw = getDTDValidatingWriter(strw, dtdStr, nsAware, repairing);
            sw.writeStartElement("root");
            try {
                sw.writeCharacters(" ");
                fail(modeDesc+" Expected a validation exception when trying to any text into EMPTY content model");
            } catch (XMLValidationException vex) { }
            sw.close();

            // Then CDATA
            sw = getDTDValidatingWriter(strw, dtdStr, nsAware, repairing);
            sw.writeStartElement("root");
            try {
                sw.writeCData("foo");
                fail(modeDesc+" Expected a validation exception when trying to add CDATA into EMPTY content model");
            } catch (XMLValidationException vex) { }
            sw.close();

            // Then ENTITY
            sw = getDTDValidatingWriter(strw, dtdStr, nsAware, repairing);
            sw.writeStartElement("root");
            try {
                sw.writeEntityRef("amp");
                fail(modeDesc+" Expected a validation exception when trying to add CDATA into EMPTY content model");
            } catch (XMLValidationException vex) { }
            sw.close();

            // Then comment
            sw = getDTDValidatingWriter(strw, dtdStr, nsAware, repairing);
            sw.writeStartElement("root");
            try {
                sw.writeComment("comment");
                fail(modeDesc+" Expected a validation exception when trying to add comment into EMPTY content model");
            } catch (XMLValidationException vex) { }
            sw.close();

            // Then proc. instr.
            sw = getDTDValidatingWriter(strw, dtdStr, nsAware, repairing);
            sw.writeStartElement("root");
            try {
                sw.writeProcessingInstruction("target", "data");
                fail(modeDesc+" Expected a validation exception when trying to add processing instruction into EMPTY content model");
            } catch (XMLValidationException vex) { }
            sw.close();
        }
    }

    public void testValidAnyContent()
        throws XMLStreamException
    {
        final String dtdStr = "<!ELEMENT root ANY>\n"
            +"<!ATTLIST root attr CDATA #IMPLIED>\n"
            +"<!ELEMENT leaf ANY>\n"
                ;

        for (int i = 0; i < 3; ++i) {
            boolean nsAware = (i >= 1);
            boolean repairing = (i == 2);
            StringWriter strw = new StringWriter();

            // First simplest case
            XMLStreamWriter2 sw = getDTDValidatingWriter(strw, dtdStr, nsAware, repairing);
            sw.writeStartElement("root");
            sw.writeStartElement("leaf");
            sw.writeCharacters("whatever");
            sw.writeEndElement();
            sw.writeEndElement();
            sw.writeEndDocument();
            sw.close();

            // Then one with no content
            sw = getDTDValidatingWriter(strw, dtdStr, nsAware, repairing);
            sw.writeStartElement("root");
            sw.writeEndElement();
            sw.writeEndDocument();
            sw.close();

            // Then one with explicitly empty elem
            sw = getDTDValidatingWriter(strw, dtdStr, nsAware, repairing);
            sw.writeStartElement("root");
            sw.writeEmptyElement("leaf");
            sw.writeEndElement();
            sw.writeEndDocument();
            sw.close();

            // Then one with an attribute
            sw = getDTDValidatingWriter(strw, dtdStr, nsAware, repairing);
            sw.writeStartElement("root");
            sw.writeAttribute("attr", "value");
            sw.writeStartElement("leaf");
            sw.writeEndElement();
            sw.writeEndElement();
            sw.writeEndDocument();
            sw.close();
        }
    }

    public void testInvalidAnyContent()
        throws XMLStreamException
    {
        final String dtdStr = "<!ELEMENT root ANY>\n"
            +"<!ATTLIST root attr CDATA #IMPLIED>\n"
            +"<!ELEMENT leaf ANY>\n";

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

            XMLStreamWriter2 sw = getDTDValidatingWriter(strw, dtdStr, nsAware, repairing);

            /* The only obviously invalid cases are using non-declared
             * elements or attributes... so let's test them here (these
             * may be redundant to some degree)
             */
            sw.writeStartElement("root");
            try {
                sw.writeStartElement("unknown");
                fail(modeDesc+" Expected a validation exception when trying to add an undeclared element");
            } catch (XMLValidationException vex) {
                // expected...
            }
            sw.close();

            // undecl attr:
            sw = getDTDValidatingWriter(strw, dtdStr, nsAware, repairing);
            sw.writeStartElement("root");
            try {
                sw.writeAttribute("unknown", "value");
                fail(modeDesc+" Expected a validation exception when trying to add an undeclared attribute");
            } catch (XMLValidationException vex) {
                // expected...
            }
            sw.close();
        }
    }
}
