package stax2.wstream;

import java.io.StringWriter;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.codehaus.stax2.XMLOutputFactory2;

import com.ctc.wstx.api.WstxOutputProperties;

public class TestAutoEmptyElems extends BaseWriterTest
{
    public void testDefaultSetting() throws Exception
    {
        final XMLOutputFactory f = getOutputFactory();
        assertEquals(Boolean.TRUE, f.getProperty(XMLOutputFactory2.P_AUTOMATIC_EMPTY_ELEMENTS));
    }

    public void testAutoEmptyElemDisabled() throws Exception
    {
        assertEquals("<root><leaf></leaf></root>", _writeDoc(getFactory(false, false)));
        assertEquals("<root><leaf></leaf></root>", _writeDoc(getFactory(false, true)));
    }
    
    public void testAutoEmptyElemEnabled() throws Exception
    {
        assertEquals("<root><leaf/></root>", _writeDoc(getFactory(true, false)));
        assertEquals("<root><leaf /></root>", _writeDoc(getFactory(true, true)));
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Helper methods
    ///////////////////////////////////////////////////////////////////////
     */

    private XMLOutputFactory2 getFactory(boolean autoEndElems,
            boolean spaceAfterEmpty) throws XMLStreamException
    {
        XMLOutputFactory2 f = getOutputFactory();
        // ns-awareness, repairing shouldn't matter, just whether automatic end elems enabled
        f.setProperty(XMLOutputFactory2.P_AUTOMATIC_EMPTY_ELEMENTS,
                autoEndElems ? Boolean.TRUE : Boolean.FALSE);
        f.setProperty(WstxOutputProperties.P_ADD_SPACE_AFTER_EMPTY_ELEM,
                spaceAfterEmpty ? Boolean.TRUE : Boolean.FALSE);
        return f;
    }    

    private String _writeDoc(XMLOutputFactory f) throws XMLStreamException
    {
        StringWriter strw = new StringWriter();
        XMLStreamWriter sw = f.createXMLStreamWriter(strw);
        sw.writeStartElement("root");
        sw.writeStartElement("leaf");
        sw.writeEndElement();
        sw.writeEndElement();
        sw.close();
        return strw.toString();
    }
}
