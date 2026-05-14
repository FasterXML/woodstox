package failing;

import java.io.StringReader;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.validation.XMLValidationSchema;
import org.codehaus.stax2.validation.XMLValidationSchemaFactory;

import wstxtest.vstream.BaseValidationTest;

/**
 * Reproducer for woodstox issue #33: when using {@code validateAgainst(schema)}
 * with a DTD that declares an {@code xml:lang} attribute, validation fails with
 * "Element &lt;X&gt; has no attribute \"xml:lang\"" if the input factory is
 * configured in non-namespace-aware mode.
 *<p>
 * The namespace-aware case is already exercised (and passing) by
 * {@code TestDTD#testFullValidationIssue23}; this test covers the
 * non-namespace-aware variant from the original bug report.
 */
public class DTDXmlLang33Test
    extends BaseValidationTest
{
    final static String DTD =
        "<!ELEMENT FreeFormText (#PCDATA)>\n"
        + "<!ATTLIST FreeFormText xml:lang CDATA #IMPLIED>\n";

    final static String XML =
        "<FreeFormText xml:lang='en-US'>foobar</FreeFormText>";

    public void testXmlLangNonNamespaceAware() throws XMLStreamException
    {
        XMLValidationSchemaFactory schF =
            XMLValidationSchemaFactory.newInstance(XMLValidationSchema.SCHEMA_ID_DTD);
        XMLValidationSchema schema = schF.createSchema(new StringReader(DTD));

        XMLInputFactory f = getInputFactory();
        setNamespaceAware(f, false);
        XMLStreamReader2 sr = (XMLStreamReader2) f.createXMLStreamReader(new StringReader(XML));
        sr.validateAgainst(schema);

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("FreeFormText", sr.getLocalName());
        assertTokenType(CHARACTERS, sr.next());
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();
    }
}
