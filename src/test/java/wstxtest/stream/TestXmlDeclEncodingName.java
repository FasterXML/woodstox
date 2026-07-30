package wstxtest.stream;

import java.io.StringReader;

import javax.xml.stream.*;

import org.codehaus.stax2.XMLStreamReader2;

import com.ctc.wstx.api.WstxInputProperties;

import org.junit.jupiter.api.Test;

/**
 * Verifies that the 'encoding' pseudo-attribute of the XML (and text)
 * declaration is rejected unless it matches the XML 1.0 <code>EncName</code>
 * production (section 4.3.3). 'version' and 'standalone' were already validated
 * against their allowed values; 'encoding' used to be accepted verbatim, so via
 * a Reader source (where the declared name is not otherwise resolved to a
 * charset) a malformed name was returned by getCharacterEncodingScheme().
 */
public class TestXmlDeclEncodingName
    extends BaseStreamTest
{
    @Test
    public void testValidEncodingNames() throws Exception
    {
        // Legal EncName values must keep working unchanged
        for (String enc : new String[] {
                "UTF-8", "ISO-8859-1", "US-ASCII", "windows-1252", "Shift_JIS"
        }) {
            String xml = "<?xml version='1.0' encoding='"+enc+"'?><root/>";
            XMLStreamReader2 sr = constructStreamReader(getInputFactory(), xml);
            assertEquals(enc, sr.getCharacterEncodingScheme());
            assertTokenType(START_ELEMENT, sr.next());
            sr.close();
        }
    }

    @Test
    public void testInvalidEncodingNameReader() throws Exception
    {
        // leading digit, leading hyphen/dot, embedded space/slash, empty
        for (String enc : new String[] {
                "123", "-utf-8", ".weird", "ISO 8859 1", "utf/8", "utf8&", ""
        }) {
            String xml = "<?xml version='1.0' encoding='"+enc+"'?><root/>";
            try {
                XMLStreamReader sr = getInputFactory()
                        .createXMLStreamReader(new StringReader(xml));
                sr.next();
                sr.close();
                fail("Expected an exception for illegal encoding name '"+enc+"'");
            } catch (XMLStreamException expected) {
                assertTrue("Expected message about 'encoding', got: "+expected.getMessage(),
                        expected.getMessage().contains("encoding"));
            }
        }
    }

    @Test
    public void testInvalidEncodingNameMultiDoc() throws Exception
    {
        // The 2nd and later declarations of a multi-document stream are parsed
        // on a separate path; an illegal encoding name there has to fail too.
        String xml = "<?xml version='1.0'?><root/>"
            +"<?xml version='1.0' encoding='not valid'?><root/>";
        XMLInputFactory f = getInputFactory();
        f.setProperty(WstxInputProperties.P_INPUT_PARSING_MODE,
                WstxInputProperties.PARSING_MODE_DOCUMENTS);
        XMLStreamReader sr = constructStreamReader(f, xml);
        streamThroughFailing(sr, "illegal encoding name in 2nd xml declaration");
    }
}
