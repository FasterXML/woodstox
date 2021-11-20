package stax2.wstream;

import java.io.*;

import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLOutputFactory2;
import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.XMLStreamWriter2;

public class TestCopyEventFromReader132
    extends BaseWriterTest
{
    // [woodstox-core#132]
    public void testCopyPIEvent() throws Exception {
        _testCopyPIEvent(true);
        _testCopyPIEvent(false);
    }

    private void _testCopyPIEvent(boolean preserveContents) throws Exception {
        final XMLInputFactory2 xmlIn = getInputFactory();
        final XMLOutputFactory2 xmlOut = getOutputFactory();
        String xml = "<description><?pi?>foo</description>";

        XMLStreamReader2 reader = (XMLStreamReader2) xmlIn.createXMLStreamReader(new StringReader(xml));
        StringWriter w = new StringWriter();
        XMLStreamWriter2 writer = (XMLStreamWriter2) xmlOut.createXMLStreamWriter(w);
        while (reader.hasNext()) {
           reader.next();
           writer.copyEventFromReader(reader, preserveContents);
        }
        reader.close();
        writer.close();

        assertEquals("<description><?pi ?>foo</description>", w.toString().trim());
    }
}
