package wstxtest.wstream;

import java.io.*;
import java.util.*;

import javax.xml.stream.*;

import com.ctc.wstx.api.EmptyElementHandler;
import com.ctc.wstx.api.WstxOutputProperties;

/**
 * Unit tests to verify that [WSTX-252] (ability to control whether
 * an empty element can be written using empty element instead of
 * separate start/end tags) has been completely implemented.
 * 
 * @since 4.1
 */
public class TestEmptyElementWriter
    extends BaseWriterTest
{
    public void testDefaults() throws Exception
    {
        XMLOutputFactory f = getOutputFactory();
        // by default, empty elements can be used for everything
        StringWriter sw = new StringWriter();
        XMLStreamWriter w = f.createXMLStreamWriter(sw);
        w.writeStartElement("root");
        w.writeStartElement("a");
        w.writeEndElement();
        w.writeStartElement("b");
        w.writeEndElement();
        w.writeEndElement();
        w.writeEndDocument();
        w.close();
        assertEquals("<root><a/><b/></root>", sw.toString());
    }

    public void testSimple() throws Exception
    {
        XMLOutputFactory f = getOutputFactory();
        // test with simple handler that lists explicitly all tags to close
        Set<String> tags = new HashSet<String> ();
        tags.add("a");
        f.setProperty(WstxOutputProperties.P_OUTPUT_EMPTY_ELEMENT_HANDLER,
                new EmptyElementHandler.SetEmptyElementHandler(tags));
        StringWriter sw = new StringWriter();
        XMLStreamWriter w = f.createXMLStreamWriter(sw);
        w.writeStartElement("root");
        w.writeStartElement("a");
        w.writeEndElement();
        w.writeStartElement("b");
        w.writeEndElement();
        w.writeEndElement();
        w.writeEndDocument();
        w.close();
        assertEquals("<root><a/><b></b></root>", sw.toString());
    }

    public void testHTML() throws Exception
    {
        XMLOutputFactory f = getOutputFactory();
        f.setProperty(WstxOutputProperties.P_OUTPUT_EMPTY_ELEMENT_HANDLER,
                EmptyElementHandler.HtmlEmptyElementHandler.getInstance());
        StringWriter sw = new StringWriter();
        XMLStreamWriter w = f.createXMLStreamWriter(sw);
        w.writeStartElement("root");
        w.writeStartElement("a");
        w.writeEndElement();
        w.writeStartElement("br");
        w.writeEndElement();
        w.writeEndElement();
        w.writeEndDocument();
        w.close();
        assertEquals("<root><a></a><br/></root>", sw.toString());
    }

}
