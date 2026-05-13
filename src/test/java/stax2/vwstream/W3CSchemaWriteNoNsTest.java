package stax2.vwstream;

import java.io.StringWriter;

import javax.xml.stream.XMLOutputFactory;

import org.codehaus.stax2.XMLStreamWriter2;
import org.codehaus.stax2.XMLOutputFactory2;
import org.codehaus.stax2.XMLStreamProperties;
import org.codehaus.stax2.validation.XMLValidationSchema;

/**
 * Regression test: writing an element via the 1-argument
 * {@code writeStartElement(localName)} form must not inherit the parent's
 * default namespace URI for validation.  Schema child elements declared in
 * no namespace ({@code elementFormDefault="unqualified"}) must pass
 * validation even when a default namespace is in scope on the parent.
 *
 * @see <a href="https://github.com/FasterXML/woodstox/pull/245#issuecomment-4142056568">PR 245 comment</a>
 */
public class W3CSchemaWriteNoNsTest extends BaseOutputTest {

    private static final String SCHEMA_UNQUALIFIED =
        "<?xml version='1.0' encoding='UTF-8'?>\n" +
        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'\n" +
        "           elementFormDefault='unqualified'>\n" +
        "    <xs:element name='Envelope'>\n" +
        "        <xs:complexType>\n" +
        "            <xs:sequence>\n" +
        "                <xs:element name='MessageHeader'>\n" +
        "                    <xs:complexType>\n" +
        "                        <xs:sequence>\n" +
        "                            <xs:element name='MessageId' type='xs:string'/>\n" +
        "                        </xs:sequence>\n" +
        "                    </xs:complexType>\n" +
        "                </xs:element>\n" +
        "                <xs:element name='Body'>\n" +
        "                    <xs:complexType>\n" +
        "                        <xs:sequence>\n" +
        "                            <xs:element name='Payload' type='xs:string'/>\n" +
        "                        </xs:sequence>\n" +
        "                    </xs:complexType>\n" +
        "                </xs:element>\n" +
        "            </xs:sequence>\n" +
        "        </xs:complexType>\n" +
        "    </xs:element>\n" +
        "</xs:schema>";

    /**
     * 1-arg writeStartElement(localName) must not inherit the parent's
     * default namespace for validation purposes.
     */
    public void testWriteStartElementNoNsWithDefaultNsBound() throws Exception {
        StringWriter strw = new StringWriter();
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_UNQUALIFIED);

        XMLOutputFactory2 outf = getOutputFactory();
        outf.setProperty(XMLStreamProperties.XSP_NAMESPACE_AWARE, Boolean.TRUE);
        outf.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, Boolean.FALSE);

        XMLStreamWriter2 sw = (XMLStreamWriter2) outf.createXMLStreamWriter(strw);
        sw.validateAgainst(schema);

        sw.writeStartDocument();
        sw.writeStartElement("Envelope");
        sw.writeDefaultNamespace("urn:some:ns");

        sw.writeStartElement("MessageHeader");
        sw.writeStartElement("MessageId");
        sw.writeCharacters("MSG-001");
        sw.writeEndElement();
        sw.writeEndElement();

        sw.writeStartElement("Body");
        sw.writeStartElement("Payload");
        sw.writeCharacters("Hello");
        sw.writeEndElement();
        sw.writeEndElement();

        sw.writeEndElement();
        sw.writeEndDocument();
        sw.close();

        String xml = strw.toString();
        assertTrue("Output should contain 'MessageHeader'", xml.contains("MessageHeader"));
        assertTrue("Output should contain 'MSG-001'", xml.contains("MSG-001"));
    }

    private static final String SCHEMA_WITH_EMPTY_LEAF =
        "<?xml version='1.0' encoding='UTF-8'?>\n" +
        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'\n" +
        "           elementFormDefault='unqualified'>\n" +
        "    <xs:element name='Root'>\n" +
        "        <xs:complexType>\n" +
        "            <xs:sequence>\n" +
        "                <xs:element name='Child' type='xs:string'/>\n" +
        "                <xs:element name='Empty'>\n" +
        "                    <xs:complexType/>\n" +
        "                </xs:element>\n" +
        "            </xs:sequence>\n" +
        "        </xs:complexType>\n" +
        "    </xs:element>\n" +
        "</xs:schema>";

    /**
     * 1-arg writeEmptyElement(localName) must also pass the correct empty
     * URI to the validator when a default namespace is in scope on the parent.
     */
    public void testWriteEmptyElementNoNsWithDefaultNsBound() throws Exception {
        StringWriter strw = new StringWriter();
        XMLValidationSchema schema = parseW3CSchema(SCHEMA_WITH_EMPTY_LEAF);

        XMLOutputFactory2 outf = getOutputFactory();
        outf.setProperty(XMLStreamProperties.XSP_NAMESPACE_AWARE, Boolean.TRUE);
        outf.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, Boolean.FALSE);

        XMLStreamWriter2 sw = (XMLStreamWriter2) outf.createXMLStreamWriter(strw);
        sw.validateAgainst(schema);

        sw.writeStartDocument();
        sw.writeStartElement("Root");
        sw.writeDefaultNamespace("urn:some:ns");

        sw.writeStartElement("Child");
        sw.writeCharacters("value");
        sw.writeEndElement();

        sw.writeEmptyElement("Empty");

        sw.writeEndElement();
        sw.writeEndDocument();
        sw.close();

        String xml = strw.toString();
        assertTrue("Output should contain 'Empty'", xml.contains("Empty"));
    }
}
