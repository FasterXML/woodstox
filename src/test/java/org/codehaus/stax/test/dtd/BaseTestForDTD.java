package org.codehaus.stax.test.dtd;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.codehaus.stax.test.stream.BaseStreamTest;

abstract class BaseTestForDTD extends BaseStreamTest
{
    protected XMLStreamReader getDTDAwareReader(String contents, boolean validate)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setCoalescing(f, false);
        setNamespaceAware(f, true);
        setSupportDTD(f, true);
        // Let's make sure DTD is really parsed?
        setValidating(f, validate);
        return constructStreamReader(f, contents);
    }
}
