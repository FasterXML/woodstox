package stax2.typed;

import java.io.StringReader;

import javax.xml.parsers.*;
import javax.xml.stream.*;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import org.codehaus.stax2.XMLStreamReader2;

/**
 * Stax2 Typed Access API basic reader tests, using DOM-backed
 * implementation.
 */
public class TestDOMReader
    extends ReaderTestBase
{
    protected XMLStreamReader2 getReader(String contents)
        throws Exception
    {
        XMLInputFactory f = getInputFactory();
        setCoalescing(f, false); // shouldn't really matter
        setNamespaceAware(f, true);

        // First, need to parse using JAXP DOM:
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new InputSource(new StringReader(contents)));

        return (XMLStreamReader2) f.createXMLStreamReader(new DOMSource(doc));
    }

    /*
    ///////////////////////////////////////////////////////////////
    // Need to mask some tests, won't work with current DOM wrapper
    ///////////////////////////////////////////////////////////////
     */

    // @Override
    public void testValidQNameElem()
    {
        // Ugh: due to missing NS lookups, even this would fail...
        warn("(skipping TestDOMReader.testValidQNameElem()");
    }

    // @Override
    public void testInvalidQNameElemBadChars()
    {
        warn("(skipping TestDOMReader.testInvalidQNameElemBadChars)");
    }

    // @Override
    public void testInvalidQNameElemUnbound()
    {
        // Need DOM3 to support namespace lookups
        warn("(skipping TestDOMReader.testInvalidQNameElemUnbound()");
    }

    // @Override
    public void testValidQNameAttr()
    {
        warn("(skipping TestDOMReader.testValidQNameAttr()");
    }

    // @Override
    public void testInvalidQNameAttrBadChars()
    {
        warn("(skipping TestDOMReader.testInvalidQNameAttrBadChars)");
    }

    // @Override
    public void testInvalidQNameAttrUnbound()
    {
        // Need DOM3 to support namespace lookups
        warn("(skipping TestDOMReader.testInvalidQNameAttrUnbound()");
    }
}


