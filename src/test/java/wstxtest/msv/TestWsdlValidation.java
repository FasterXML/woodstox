/**
 * 
 */
package wstxtest.msv;

import java.io.IOException;
import java.net.URL;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.transform.dom.DOMSource;

import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;
import org.w3c.dom.Document;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import stax2.BaseStax2Test;

import com.ctc.wstx.msv.W3CSchema;
import com.sun.msv.grammar.xmlschema.XMLSchemaGrammar;
import com.sun.msv.reader.GrammarReaderController2;
import com.sun.msv.reader.xmlschema.WSDLSchemaReader;

public class TestWsdlValidation extends BaseStax2Test {
	
	 private static class LocalController implements GrammarReaderController2 {

	     @Override
	     public LSResourceResolver getLSResourceResolver() {
	         return null;
	     }

	     @Override
	     public void error(Locator[] locs, String errorMessage, Exception nestedException) {
	         StringBuffer errors = new StringBuffer();
	         for (Locator loc : locs) {
	             errors.append("in " + loc.getSystemId() + " " + loc.getLineNumber() + ":"
	                     + loc.getColumnNumber());
	         }
	         throw new RuntimeException(errors.toString(), nestedException);
	     }

	     @Override
	     public void warning(Locator[] locs, String errorMessage) {
	         StringBuffer errors = new StringBuffer();
	         for (Locator loc : locs) {
	             errors.append("in " + loc.getSystemId() + " " + loc.getLineNumber() + ":"
	                     + loc.getColumnNumber());
	         }
	         // no warning allowed.
	         throw new RuntimeException("warning: " + errors.toString());
	     }

	     @Override
	     public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
	         return null;
	     }
	 }

	private XMLSchemaGrammar wsdlgrammar;
	private W3CSchema schema;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        URL wsdlUri = getClass().getResource("test.wsdl");
        Document wsdl = documentBuilder.parse(wsdlUri.openStream());
        String wsdlSystemId = wsdlUri.toExternalForm();
        DOMSource source = new DOMSource(wsdl);
        source.setSystemId(wsdlSystemId);

        LocalController controller = new LocalController();
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        wsdlgrammar = WSDLSchemaReader.read(source, factory, controller);
        schema = new W3CSchema(wsdlgrammar);
	}
	
	public void testWsdlValidation() throws Exception {
		String runMe = System.getProperty("testWsdlValidation");
		if (runMe == null || "".equals(runMe)) {
			return;
		}
		XMLInputFactory2 factory = getInputFactory();
		XMLStreamReader2 reader = (XMLStreamReader2) factory.createXMLStreamReader(getClass().getResourceAsStream("test-message.xml"), "utf-8");
		QName msgQName = new QName("http://server.hw.demo/", "sayHi");
		while (true) {
			int what = reader.nextTag();
			if (what == XMLStreamConstants.START_ELEMENT) {
				if (reader.getName().equals(msgQName)) {
					reader.validateAgainst(schema);
				}
			} else if (what == XMLStreamConstants.END_ELEMENT) {
				if (reader.getName().equals(msgQName)) {
					reader.stopValidatingAgainst(schema);
				}
			} else if (what == XMLStreamConstants.END_DOCUMENT) {
				break;
			}
		}
	}
}
