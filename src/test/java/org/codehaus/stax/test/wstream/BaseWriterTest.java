package org.codehaus.stax.test.wstream;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.stax.test.BaseStaxTest;

/**
 * Base class for all StaxTest unit tests that test basic
 * stream (cursor) writer API functionality.
 *
 * @author Tatu Saloranta
 */
public abstract class BaseWriterTest
    extends BaseStaxTest
{
    public XMLStreamWriter getRepairingWriter(Writer w)
        throws XMLStreamException
    {
        XMLOutputFactory f = getOutputFactory();
        f.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES,
                      Boolean.TRUE);
        return f.createXMLStreamWriter(w);
    }

    public XMLStreamWriter getNonRepairingWriter(Writer w)
        throws XMLStreamException
    {
        XMLOutputFactory f = getOutputFactory();
        f.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES,
                      Boolean.FALSE);
        return f.createXMLStreamWriter(w);
    }
}
