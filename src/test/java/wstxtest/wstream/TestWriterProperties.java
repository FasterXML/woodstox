package wstxtest.wstream;

import java.io.*;

import javax.xml.stream.*;

import com.ctc.wstx.api.WstxOutputProperties;

/**
 * This unit test suite checks that per-writer properties are
 * accessible as expected.
 */
public class TestWriterProperties
    extends BaseWriterTest
{
    public void testAccessStream()
        throws XMLStreamException
    {
        XMLOutputFactory f = getOutputFactory();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter sw = f.createXMLStreamWriter(bos, "UTF-8");

        assertSame(bos, sw.getProperty(WstxOutputProperties.P_OUTPUT_UNDERLYING_STREAM));
    }

    public void testAccessWriter()
        throws XMLStreamException
    {
        XMLOutputFactory f = getOutputFactory();
        StringWriter strw = new StringWriter();
        XMLStreamWriter sw = f.createXMLStreamWriter(strw);

        assertSame(strw, sw.getProperty(WstxOutputProperties.P_OUTPUT_UNDERLYING_WRITER));
    }
}
