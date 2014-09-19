
package wstxtest;

import java.io.StringReader;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;

import com.ctc.wstx.api.WstxInputProperties;

/**
 * @since 4.1.2
 */
public class TestDefaultNamespacePrefix extends BaseWstxTest
{
    public void testDefaultNamespacePrefixAsNull() throws Exception
    {
        String XML = "<blah xmlns=\"http://blah.org\"><foo>foo</foo></blah>";
        System.setProperty("com.ctc.wstx.returnNullForDefaultNamespace", "true");
        XMLInputFactory factory = getInputFactory();
        XMLStreamReader r = factory.createXMLStreamReader(new StringReader(XML));
        while (r.hasNext()) {
            r.next();
            if ((r.getEventType() == XMLEvent.START_ELEMENT) && (r.getLocalName().equals("blah"))) {
                String prefix = r.getNamespacePrefix(0);
                if (prefix != null) {
                    throw new Exception("Null value is not returned for the default namespace prefix while "
                            + WstxInputProperties.P_RETURN_NULL_FOR_DEFAULT_NAMESPACE + " is set true");
                }
                break;
            }
        }
    }

    public void testDefaultNamespacePrefixAsEmptyString() throws Exception
    {
        String XML = "<blah xmlns=\"http://blah.org\"><foo>foo</foo></blah>";
        System.setProperty("com.ctc.wstx.returnNullForDefaultNamespace", "false");
        XMLInputFactory factory = getInputFactory();
        XMLStreamReader r = factory.createXMLStreamReader(new StringReader(XML));
        while (r.hasNext()) {
            r.next();
            if ((r.getEventType() == XMLEvent.START_ELEMENT) && (r.getLocalName().equals("blah"))) {
                String prefix = r.getNamespacePrefix(0);
                if (!"".equals(prefix)) {
                    throw new Exception("Null value is not returned for the default namespace prefix while "
                            + WstxInputProperties.P_RETURN_NULL_FOR_DEFAULT_NAMESPACE + " is set true");
                }
                break;
            }
        }
    }
}
