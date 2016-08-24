package wstxtest.wstream;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.stax2.XMLStreamReader2;

import wstxtest.BaseWstxTest;

abstract class BaseWriterTest
    extends BaseWstxTest
{
    protected BaseWriterTest() { }

    @Override
    protected XMLStreamReader2 constructNsStreamReader(String content, boolean coal)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setNamespaceAware(f, true);
        setCoalescing(f, coal);
        return (XMLStreamReader2) f.createXMLStreamReader(new StringReader(content));
    }
}
