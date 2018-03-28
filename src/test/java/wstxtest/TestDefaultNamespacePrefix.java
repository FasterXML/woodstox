
package wstxtest;

import java.io.StringReader;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import com.ctc.wstx.api.WstxInputProperties;

/**
 * @since 4.1.2
 */
public class TestDefaultNamespacePrefix extends BaseWstxTest
{
    public void testDefaultNamespacePrefixAsNull() throws Exception
    {
        String XML = "<blah xmlns=\"http://blah.org\"><foo>foo</foo></blah>";
//        System.setProperty("com.ctc.wstx.returnNullForDefaultNamespace", "true");
        XMLInputFactory factory = getNewInputFactory();

        assertEquals(Boolean.FALSE, factory.getProperty(WstxInputProperties.P_RETURN_NULL_FOR_DEFAULT_NAMESPACE));
        
        factory.setProperty(WstxInputProperties.P_RETURN_NULL_FOR_DEFAULT_NAMESPACE, true);
        XMLStreamReader r = factory.createXMLStreamReader(new StringReader(XML));
        assertTokenType(START_ELEMENT, r.next());
        String prefix = r.getNamespacePrefix(0);
        if (prefix != null) {
            fail("Null value is not returned for the default namespace prefix while "
                    + WstxInputProperties.P_RETURN_NULL_FOR_DEFAULT_NAMESPACE + " is set true");
        }
    }

    public void testDefaultNamespacePrefixAsEmptyString() throws Exception
    {
        String XML = "<blah xmlns=\"http://blah.org\"><foo>foo</foo></blah>";
//        System.setProperty("com.ctc.wstx.returnNullForDefaultNamespace", "false");
        XMLInputFactory factory = getNewInputFactory();
        assertEquals(Boolean.FALSE, factory.getProperty(WstxInputProperties.P_RETURN_NULL_FOR_DEFAULT_NAMESPACE));
//        factory.setProperty(WstxInputProperties.P_RETURN_NULL_FOR_DEFAULT_NAMESPACE, false);
        XMLStreamReader r = factory.createXMLStreamReader(new StringReader(XML));
        assertTokenType(START_ELEMENT, r.next());
        String prefix = r.getNamespacePrefix(0);
        if (!"".equals(prefix)) {
            fail("Null value is returned for the default namespace prefix while "
                    + WstxInputProperties.P_RETURN_NULL_FOR_DEFAULT_NAMESPACE + " is set false");
        }
    }
}
