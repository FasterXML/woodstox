package wstxtest.wstream;

import java.io.*;

import javax.xml.stream.*;

import com.ctc.wstx.api.WstxOutputProperties;

/**
 * Unit tests for verifying that [WSTX-165] works ok.
 */
public class TestAutoEndElems
    extends BaseWriterTest
{
    public void testAutomaticEndElemsEnabled()
        throws XMLStreamException
    {
        StringWriter strw = new StringWriter();
        XMLStreamWriter sw = getFactory(true).createXMLStreamWriter(strw);
        sw.writeStartElement("root");
        sw.writeStartElement("leaf");
        sw.writeCharacters(""); // to prevent empty elem, simplify testing
        sw.close();

        assertEquals("<root><leaf></leaf></root>", strw.toString());
    }

    public void testAutomaticEndElemsDisabled()
        throws XMLStreamException
    {
        StringWriter strw = new StringWriter();
        XMLStreamWriter sw = getFactory(false).createXMLStreamWriter(strw);
        sw.writeStartElement("root");
        sw.writeStartElement("leaf");
        sw.writeCharacters(""); // to prevent empty elem, simplify testing
        sw.close();

        assertEquals("<root><leaf>", strw.toString());
    }

    /*
    ////////////////////////////////////////////////////
    // Helper methods
    ////////////////////////////////////////////////////
     */

    private XMLOutputFactory getFactory(boolean autoEndElems)
        throws XMLStreamException
    {
        XMLOutputFactory f = getOutputFactory();
        // ns-awareness, repairing shouldn't matter, just whether automatic end elems enabled
        f.setProperty(WstxOutputProperties.P_AUTOMATIC_END_ELEMENTS, autoEndElems ? Boolean.TRUE : Boolean.FALSE);
        return f;
    }
    
}
