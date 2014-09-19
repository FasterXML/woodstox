package stax2.dom;

import javax.xml.parsers.*;
import javax.xml.stream.*;
import javax.xml.transform.dom.DOMResult;

import org.w3c.dom.*;

import org.codehaus.stax2.typed.TypedXMLStreamWriter;

import stax2.wstream.BaseWriterTest;

/**
 * Unit test suite that checks that output-side DOM-compatibility
 * features (DOMResult for output) are implemented as expected.
 */
public class TestDomWrite
    extends BaseWriterTest
{
    final static int TYPE_NON_NS = 0;
    final static int TYPE_NS = 1;
    final static int TYPE_NS_REPAIRING = 2;
    
    public void testNonNsOutput() throws Exception
    {
        /* 23-Dec-2008, TSa: Not all Stax2 impls support non-namespace-aware
         *  modes: need to first ensure one tested does...
         */
        XMLOutputFactory of = getFactory(TYPE_NON_NS);
        if (of == null) {
            System.err.println("Skipping "+getClass().getName()+"#testNonNsOutput: non-namespace-aware mode not supported");
            return;
        }

        Document doc = createDomDoc(false);
        XMLStreamWriter sw = of.createXMLStreamWriter(new DOMResult(doc));

        sw.writeStartDocument();
        sw.writeStartElement("root");
        sw.writeAttribute("attr", "value");
        sw.writeAttribute("ns:attr2", "value2");
        sw.writeEmptyElement("leaf");
        sw.writeCharacters("text?<ok>");
        sw.writeEndElement();
        sw.writeEndDocument();
        sw.close();

        Element root = doc.getDocumentElement();

        assertNotNull(root);
        assertEquals("root", root.getTagName());
        NamedNodeMap attrs = root.getAttributes();
        assertEquals(2, attrs.getLength());
        assertEquals("value", root.getAttribute("attr"));
        assertEquals("value2", root.getAttribute("ns:attr2"));

        Node child = root.getFirstChild();
        assertNotNull(child);
        assertEquals(Node.ELEMENT_NODE, child.getNodeType());
        Element elem = (Element) child;
        assertEquals("leaf", elem.getTagName());
        attrs = elem.getAttributes();
        assertEquals(0, attrs.getLength());

        child = child.getNextSibling();
        assertNotNull(child);
        assertEquals(Node.TEXT_NODE, child.getNodeType());
        // Alas, getTextContent() is DOM 3 (Jdk 1.5+)
        //assertEquals("text?<ok>", child.getTextContent());
        // ... so we'll use less refined method
        assertEquals("text?<ok>", child.getNodeValue());
    }

    public void testMiscOutput() throws Exception
    {
        XMLOutputFactory of = getFactory(TYPE_NON_NS);
        if (of == null) {
            System.err.println("Skipping "+getClass().getName()+"#testNonNsOutput: non-namespace-aware mode not supported");
            return;
        }
        Document doc = createDomDoc(false);
        XMLStreamWriter sw = of.createXMLStreamWriter(new DOMResult(doc));

        sw.writeStartDocument();
        sw.writeStartElement("root");
        sw.writeComment("comment!");
        sw.writeCData("cdata!");
        sw.writeEndElement();
        sw.writeEndDocument();
        sw.close();

        Element root = doc.getDocumentElement();

        assertNotNull(root);
        assertEquals("root", root.getTagName());

        Node child = root.getFirstChild();
        assertNotNull(child);
        assertEquals(Node.COMMENT_NODE, child.getNodeType());
        assertEquals("comment!", child.getNodeValue());

        child = child.getNextSibling();
        assertNotNull(child);
        assertEquals(Node.CDATA_SECTION_NODE, child.getNodeType());
        assertEquals("cdata!", child.getNodeValue());
        assertNull(child.getNextSibling());
    }
    
    public void testNsOutput()
        throws Exception
    {
        Document doc = createDomDoc(false);
        XMLOutputFactory of = getFactory(TYPE_NS);
        XMLStreamWriter sw = of.createXMLStreamWriter(new DOMResult(doc));
        final String NS_URI = "http://foo";

        sw.writeStartDocument();
        sw.writeStartElement("ns", "root", NS_URI);
        sw.writeNamespace("ns", NS_URI);
        sw.writeAttribute("ns", NS_URI, "attr", "value");
        sw.writeCharacters("...");
        sw.writeEndElement();
        sw.writeEndDocument();
        sw.close();

        Element root = doc.getDocumentElement();

        assertNotNull(root);
        assertEquals("ns:root", root.getTagName());
        NamedNodeMap attrs = root.getAttributes();
        // DOM includes ns decls as attributes, hence 2:
        assertEquals(2, attrs.getLength());
        assertEquals(NS_URI, root.getAttribute("xmlns:ns"));
        assertEquals("value", root.getAttribute("ns:attr"));

        Node child = root.getFirstChild();
        assertNotNull(child);
        assertEquals(Node.TEXT_NODE, child.getNodeType());
        // Alas, getTextContent() is DOM 3 (Jdk 1.5+)
        //assertEquals("text?<ok>", child.getTextContent());
        // ... so we'll use less refined method
        assertEquals("...", child.getNodeValue());
    }

    public void testRepairingNsOutput()
        throws Exception
    {
        final String URI1 = "urn:1";
        final String URI2 = "urn:2";

        Document doc = createDomDoc(false);
        XMLOutputFactory of = getFactory(TYPE_NS_REPAIRING);
        XMLStreamWriter sw = of.createXMLStreamWriter(new DOMResult(doc));

        sw.writeStartDocument();
        sw.writeStartElement(URI1, "root");
        sw.writeAttribute("attr", "x");
        sw.writeEmptyElement(URI2, "leaf");
        sw.writeStartElement(URI2, "leaf2");
        sw.writeAttribute(URI2, "attr2", "<value>");
        sw.writeEndElement();
        sw.writeEndElement();
        sw.writeEndDocument();
        sw.close();

        Element root = doc.getDocumentElement();

        //System.err.println("DOC -> "+((org.w3c.dom.ls.DOMImplementationLS) doc.getImplementation()).createLSSerializer().writeToString(doc));

        assertNotNull(root);
        assertEquals("root", root.getLocalName());
        assertEquals(URI1, root.getNamespaceURI());
        NamedNodeMap attrs = root.getAttributes();
        // 1 attribute, 1 ns decl:
        assertEquals(2, attrs.getLength());
        // interesting -- as per javadocs, must pass null for "no namespace"
        assertEquals("x", root.getAttributeNS(null, "attr"));

        Node child = root.getFirstChild();
        assertNotNull(child);
        assertEquals(Node.ELEMENT_NODE, child.getNodeType());
        Element elem = (Element) child;
        assertEquals("leaf", elem.getLocalName());
        attrs = elem.getAttributes();
        // 1 ns decl:
        assertEquals(1, attrs.getLength());

        child = child.getNextSibling();
        assertNotNull(child);
        assertEquals(Node.ELEMENT_NODE, child.getNodeType());
        elem = (Element) child;
        assertEquals("leaf2", elem.getLocalName());
        attrs = elem.getAttributes();
        /* At least 1 attribute, 1 ns decl; but depending on
         * how writer chooses to do it, may be 2 ns decls. So...
         */
        int count = attrs.getLength();
        if (count < 2 || count > 3) {
            fail("Expected 2 or 3 attributes (including namespace declarations), got "+count);
        }
        assertEquals("<value>", elem.getAttributeNS(URI2, "attr2"));
    }

    public void testTypedOutputInt()
        throws Exception
    {
        Document doc = createDomDoc(false);
        // let's use namespace-aware just because some impls might not support non-ns
        XMLOutputFactory of = getFactory(TYPE_NS);
        TypedXMLStreamWriter sw = (TypedXMLStreamWriter) of.createXMLStreamWriter(new DOMResult(doc));

        sw.writeStartDocument();
        sw.writeStartElement("root");
        sw.writeIntAttribute(null, null, "attr", -900);
        sw.writeInt(123);
        sw.writeEndElement();
        sw.writeEndDocument();
        sw.close();

        Element root = doc.getDocumentElement();

        assertNotNull(root);
        assertEquals("root", root.getTagName());
        NamedNodeMap attrs = root.getAttributes();
        assertEquals(1, attrs.getLength());
        assertEquals("-900", root.getAttribute("attr"));

        Node child = root.getFirstChild();
        assertNotNull(child);
        assertEquals(Node.TEXT_NODE, child.getNodeType());
        assertEquals("123", child.getNodeValue());
    }
    
    /*
    ///////////////////////////////////////////////////////////
    // Helper methods
    ///////////////////////////////////////////////////////////
     */

    private Document createDomDoc(boolean nsAware)
        throws Exception
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(nsAware);
        return dbf.newDocumentBuilder().newDocument();
    }

    private XMLOutputFactory getFactory(int type)
        throws Exception
    {
        XMLOutputFactory f = getOutputFactory();
        // type 0 -> non-ns, 1 -> ns, non-repairing, 2 -> ns, repairing
        boolean ns = (type > 0);
        /* 23-Dec-2008, TSa: Not all Stax2 impls support non-namespace-aware
         *  modes: need to first ensure one tested does...
         */
        if (!setNamespaceAware(f, ns) && !ns) {
            return null;
        }
        setRepairing(f, type > 1); 
        return f;
    }
}
