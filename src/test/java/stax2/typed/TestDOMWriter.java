package stax2.typed;

import java.io.*;

import javax.xml.parsers.*;
import javax.xml.stream.*;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.*;
import org.codehaus.stax2.*;

/**
 * Stax2 Typed Access API basic reader tests, using DOM-backed
 * typed writer implementation.
 *<p>
 * Note: currently some functionality is only supported with native
 * writers
 */
public class TestDOMWriter
    extends WriterTestBase
{
    /**
     * Nasty hack: we need to remember DOM document we are serializing into,
     * to be able to fetch back the results.
     */
    Document mDoc;

    @Override
    protected XMLStreamWriter2 getTypedWriter(ByteArrayOutputStream out,
                                              boolean repairing)
        throws XMLStreamException
    {
        out.reset();
        XMLOutputFactory outf = getOutputFactory();
	mDoc = createDOMDoc(true);
        setRepairing(outf, repairing);
        return (XMLStreamWriter2) outf.createXMLStreamWriter(new DOMResult(mDoc));
    }

    @Override
    protected byte[] closeWriter(XMLStreamWriter sw, ByteArrayOutputStream out)
        throws XMLStreamException
    {
	sw.close();

	// Let's use Trax identity "transformer"
	try {
	    Transformer t = TransformerFactory.newInstance().newTransformer();
	    t.transform(new DOMSource(mDoc), new StreamResult(out));
	} catch (Exception e) {
	    throw new RuntimeException(e);
	}
	return out.toByteArray();
    }

    /*
    ///////////////////////////////////////////////////////
    // Helper methods
    ///////////////////////////////////////////////////////
     */

    private Document createDOMDoc(boolean nsAware)
    {
	try {
	    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	    dbf.setNamespaceAware(nsAware);
	    return dbf.newDocumentBuilder().newDocument();
	} catch (Exception e) {
	    throw new RuntimeException(e);
	}
    }
}
