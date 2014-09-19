package stax2.stream;

import java.io.*;
import javax.xml.stream.*;
import javax.xml.transform.stream.StreamSource;

import stax2.BaseStax2Test;

/**
 * This unit test suite verifies use of {@link StreamSource} as input
 * for {@link XMLInputFactory}.
 *
 * @author Tatu Saloranta
 *
 * @since 3.0
 */
public class TestStreamSource
    extends BaseStax2Test
{
    /**
     * This test is related to problem reported as [WSTX-182], inability
     * to use SystemId alone as source.
     */
    public void testCreateUsingSystemId()
        throws IOException, XMLStreamException
    {
        File tmpF = File.createTempFile("staxtest", ".xml");
        tmpF.deleteOnExit();

        // First, need to write contents to the file
        Writer w = new OutputStreamWriter(new FileOutputStream(tmpF), "UTF-8");
        w.write("<root />");
        w.close();

        XMLInputFactory f = getInputFactory();
        StreamSource src = new StreamSource();
        src.setSystemId(tmpF);
        XMLStreamReader sr = f.createXMLStreamReader(src);
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();
    }
}
