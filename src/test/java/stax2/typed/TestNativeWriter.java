package stax2.typed;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.stax2.*;

/**
 * Stax2 Typed Access API basic reader tests, using native Stax2
 * typed writer implementation.
 *<p>
 * Note: currently some functionality is only supported with native
 * writers
 */
public class TestNativeWriter
    extends WriterTestBase
{
    @Override
    protected XMLStreamWriter2 getTypedWriter(ByteArrayOutputStream out,
                                              boolean repairing)
        throws XMLStreamException
    {
        out.reset();
        XMLOutputFactory outf = getOutputFactory();
        setRepairing(outf, repairing);
        return (XMLStreamWriter2) outf.createXMLStreamWriter(out, "UTF-8");
    }

    @Override
    protected byte[] closeWriter(XMLStreamWriter sw, ByteArrayOutputStream out)
        throws XMLStreamException
    {
        sw.close();
        return out.toByteArray();
    }
}
