package stax2.typed;

import javax.xml.stream.*;

import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.ri.Stax2ReaderAdapter;

/**
 * Stax2 Typed Access API basic reader tests for binary content handling
 * using wrapped Stax2 typed reader implementation.
 */
public class TestWrappedBinaryReader
    extends ReaderBinaryTestBase
{
    @Override
    protected XMLStreamReader2 getReader(String contents)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setCoalescing(f, false); // shouldn't really matter
        setNamespaceAware(f, true);

        /* Twist: let's wrap, as if it was a regular stax1 reader;
         *  let's force wrapping via constructor 
         * (i.e. not call "wrapIfNecessary")
         */
        return new MyAdapter(constructStreamReader(f, contents));
    }

    /**
     * Need a dummy base class to be able to access protected
     * constructor for testing purposes.
     */
    final static class MyAdapter
        extends Stax2ReaderAdapter
    {
        public MyAdapter(XMLStreamReader sr)
        {
            super(sr);
        }
    }
}

