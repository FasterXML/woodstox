package stax2.typed;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.stax2.*;
import org.codehaus.stax2.ri.Stax2WriterAdapter;

/**
 * Stax2 Typed Access API basic reader tests, using Stax2 adapter
 * which implements Stax2 functionality non-natively, on top of
 * any regular Stax 1.0 implementation.
 */
public class TestWrappedWriter
    extends WriterTestBase
{
    protected XMLStreamWriter2 getTypedWriter(ByteArrayOutputStream out,
                                              boolean repairing)
        throws XMLStreamException
    {
        out.reset();
        XMLOutputFactory outf = getOutputFactory();
        setRepairing(outf, repairing);
        return new MyAdapter(outf.createXMLStreamWriter(out, "UTF-8"));
    }

    protected byte[] closeWriter(XMLStreamWriter sw, ByteArrayOutputStream out)
        throws XMLStreamException
    {
	sw.close();
	return out.toByteArray();
    }

    /*
    ////////////////////////////////////////
    // Helper class
    ////////////////////////////////////////
    */
    
    /**
     * Need a dummy base class to be able to access protected
     * constructor for testing purposes.
     */
    final static class MyAdapter
        extends Stax2WriterAdapter
    {
        public MyAdapter(XMLStreamWriter sw)
        {
            super(sw);
        }
    }
}
