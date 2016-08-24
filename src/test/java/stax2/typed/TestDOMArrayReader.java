package stax2.typed;

import java.io.StringReader;

import javax.xml.parsers.*;
import javax.xml.stream.*;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import org.codehaus.stax2.XMLStreamReader2;

/**
 * Stax2 Typed Access API basic reader tests for array handling,
 * using native Stax2 typed reader implementation.
 */
public class TestDOMArrayReader
    extends ReaderArrayTestBase
{
    @Override
    protected XMLStreamReader2 getReader(String contents)
        throws XMLStreamException
    {
        try {
            XMLInputFactory f = getInputFactory();
            setCoalescing(f, false); // shouldn't really matter
            setNamespaceAware(f, true);
            
            // First, need to parse using JAXP DOM:
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new InputSource(new StringReader(contents)));
            
            return (XMLStreamReader2) f.createXMLStreamReader(new DOMSource(doc));
        } catch (Exception e) {
            throw new XMLStreamException(e);
        }
    }
}

