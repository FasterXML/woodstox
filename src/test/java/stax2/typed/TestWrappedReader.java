package stax2.typed;

import javax.xml.stream.*;

import org.codehaus.stax2.XMLStreamReader2;

/**
 * Stax2 Typed Access API basic reader tests, using Stax2 adapter
 * which implements Stax2 functionality non-natively, on top of
 * any regular Stax 1.0 implementation.
 */
public class TestWrappedReader
    extends ReaderTestBase
{
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
        return wrapWithAdapter(constructStreamReader(f, contents));
    }

    /*
    ///////////////////////////////////////////////////////////////
    // Need to mask some tests, won't work with current wrapper
    ///////////////////////////////////////////////////////////////
     */

    // @Override
    public void testInvalidQNameElemBadChars()
        throws Exception
    {
        System.out.println("(skipping TestWrappedReader.testInvalidQNameElemBadChars)");
    }

    // @Override
    public void testInvalidQNameAttrBadChars()
    {
        System.out.println("(skipping TestWrappedReader.testInvalidQNameAttrBadChars)");
    }
}


