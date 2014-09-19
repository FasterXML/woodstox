package stax2.typed;

import javax.xml.stream.*;

import org.codehaus.stax2.XMLStreamReader2;

/**
 * Stax2 Typed Access API basic reader tests for array handling,
 * using native Stax2 typed reader implementation.
 */
public class TestNativeArrayReader
    extends ReaderArrayTestBase
{
    protected XMLStreamReader2 getReader(String contents)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setCoalescing(f, false); // shouldn't really matter
        setNamespaceAware(f, true);
        return constructStreamReader(f, contents);
    }
}

