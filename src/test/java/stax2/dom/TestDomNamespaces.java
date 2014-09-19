package stax2.dom;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.*;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import stax2.BaseStax2Test;

/**
 * Additional reader-side tests for namespace handling with DOM input
 */
public class TestDomNamespaces
    extends BaseStax2Test
{
    private String xml = "<ns2:root xmlns:ns2='http://testnamespace/'>"
        +"<arg0>"
        +"<obj xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"ns2:mycomplextype\">"
        +"<a>321</a>"
        +"</obj>"
        +"</arg0>"
        +"</ns2:root>"; 

    public void testDOMSource() throws Exception
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        
        InputSource source = new InputSource(new StringReader(xml));
        Document doc = builder.parse(source);
        
        //Fails when using DOMWrappingReader
        XMLStreamReader reader = getInputFactory().createXMLStreamReader(new DOMSource(doc));

        reader.next(); //root
        assertEquals(0, reader.getAttributeCount());
        assertEquals(1, reader.getNamespaceCount());
        assertEquals("http://testnamespace/", reader.getNamespaceURI());
        assertEquals("ns2", reader.getPrefix());
        assertEquals("root", reader.getLocalName());
        
        reader.next(); //arg0
        reader.next(); //obj

        assertEquals("obj", reader.getLocalName());
        assertEquals("ns2:mycomplextype", reader.getAttributeValue("http://www.w3.org/2001/XMLSchema-instance", "type"));
        assertEquals("http://testnamespace/", reader.getNamespaceURI("ns2"));
        assertEquals("http://testnamespace/", reader.getNamespaceContext().getNamespaceURI("ns2"));

        assertEquals("ns2", reader.getNamespaceContext().getPrefix("http://testnamespace/"));
    }
}
