package stax2.dom;

import javax.xml.parsers.*;
import javax.xml.stream.*;
import javax.xml.transform.dom.DOMResult;

import org.w3c.dom.*;

import stax2.wstream.BaseWriterTest;

/**
 * Unit tests initially written to verify [WSTX-183], problems with
 * using DOM Element for DOMResult (instead of DOM Document).
 *
 * @author Christopher Paul Simmons (original tests)
 * @author Tatu Saloranta (slight modifications)
 */
public class TestDomResultHandling
    extends BaseWriterTest
{
    public void testWriteToDocument() throws Exception
    {
        // First: write to a regular DOM document
        createXMLEventWriter(createDomDoc(true));
    }

    public void testWriteToRootElementNotInDOM() throws Exception
    {
        // let's try outputting under specified element
        Document doc = createDomDoc(true);
        Element root = doc.createElementNS("ns", "my:root");
        createXMLEventWriter(root);
        /* Should not (try to) attach the element to creating document;
         * that is up to caller to do
         */
        assertNull(doc.getDocumentElement());
    }

    public void testWriteToRootElementInDOM() throws Exception
    {
        Document doc = createDomDoc(true);
        Element root = doc.createElementNS("ns", "my:root");
        doc.appendChild(root);
        createXMLEventWriter(root);
    }

    public void testWriteBeforeSibling() throws Exception
    {
        Document doc = createDomDoc(true);
        Element root = doc.createElementNS("ns", "my:root");
        doc.appendChild(root);
        Element insertBefore = doc.createElementNS("ns", "my:beforeMe");
        root.appendChild(insertBefore);
        createXMLEventWriter(root, insertBefore);
    }

    /*
    ///////////////////////////////////////////////////////////
    // Helper methods
    ///////////////////////////////////////////////////////////
     */

    private XMLEventWriter createXMLEventWriter(final Node parent, final Node insertBefore)
        throws XMLStreamException
    {
        XMLOutputFactory f = getOutputFactory();
        return f.createXMLEventWriter(new DOMResult(parent, insertBefore));
    }

    /**
     * @param resultNode The node to write to.
     * @return The result.
     * @throws XMLStreamException On error.
     */
    private XMLEventWriter createXMLEventWriter(final Node resultNode)
        throws XMLStreamException
    {
        return createXMLEventWriter(resultNode, null);
    }

    private Document createDomDoc(boolean nsAware)
        throws Exception
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(nsAware);
        return dbf.newDocumentBuilder().newDocument();
    }
}
