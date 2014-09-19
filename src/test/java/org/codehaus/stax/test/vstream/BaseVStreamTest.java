package org.codehaus.stax.test.vstream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.codehaus.stax.test.stream.BaseStreamTest;

/**
 * Base class for all StaxTest unit tests that test validation-dependant
 * parts of stream (cursor) API functionality.
 *
 * @author Tatu Saloranta
 */
abstract class BaseVStreamTest
    extends BaseStreamTest
{
    protected XMLStreamReader getValidatingReader(String contents)
        throws XMLStreamException
    {
        return getValidatingReader(contents, true);
    }

    protected XMLStreamReader getValidatingReader(String contents, boolean nsAware)
        throws XMLStreamException
    {
        XMLInputFactory f = getValidatingFactory(nsAware);
        return constructStreamReader(f, contents);
    }

    protected XMLInputFactory getValidatingFactory(boolean nsAware)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setCoalescing(f, false); // shouldn't really matter
        setNamespaceAware(f, nsAware);
        setSupportDTD(f, true);
        // Let's make sure DTD is really parsed?
        setValidating(f, true);
        return f;
    }
}
