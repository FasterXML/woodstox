package org.codehaus.stax.test.wstream;

import javax.xml.stream.*;

import java.io.*;

/**
 * Simple unit tests for ensuring that the Stax implementation does not
 * close the underlying output stream when XMLStreamWriter.close() is
 * called.
 *
 * @author Tatu Saloranta
 * @author Matt Solnit
 */
public class TestWriterClosing
    extends BaseWriterTest
{
    public void testClosing()
        throws IOException, XMLStreamException
    {
	File f = File.createTempFile("wstxtest", null);
	f.deleteOnExit();
	OutputStream stream = new FileOutputStream(f);
	OutputStreamWriter strw = new OutputStreamWriter(stream, "UTF-8");
        XMLStreamWriter xsw = getNonRepairingWriter(strw);
	xsw.writeStartDocument();
	xsw.writeStartElement("root");
	xsw.writeEndElement();
	xsw.writeEndDocument();
	xsw.close();
    
	/* If impl called stream.close() above, we'll get an IOEXception
	 * here...
	 */
	try {
	    strw.write("<!-- trailer -->");
	} catch (IOException ioe) {
	    fail("Should not have gotten IOException, impl. probably called stream.close(): "+ioe);
	}
	stream.close();
  }
}
