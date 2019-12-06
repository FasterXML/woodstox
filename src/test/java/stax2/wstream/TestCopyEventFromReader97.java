package stax2.wstream;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLOutputFactory2;
import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.XMLStreamWriter2;

public class TestCopyEventFromReader97
    extends BaseWriterTest
{
    // [woodstox-core#97]
    public void testUTF8MsLinefeedCopyEvent() throws Exception
    {
        final XMLInputFactory2 xmlIn = getInputFactory();
        final XMLOutputFactory2 xmlOut = getOutputFactory();
        InputStream in = getClass().getResource("issue97.xml").openStream();

        ByteArrayOutputStream bogus = new ByteArrayOutputStream();
        XMLStreamReader2 reader = (XMLStreamReader2) xmlIn.createXMLStreamReader(in);
        XMLStreamWriter2 writer = (XMLStreamWriter2) xmlOut.createXMLStreamWriter(bogus, "UTF-8");
        while (reader.hasNext()) {
           reader.next();
           writer.copyEventFromReader(reader, false);
        }

        in.close();
    }
}
