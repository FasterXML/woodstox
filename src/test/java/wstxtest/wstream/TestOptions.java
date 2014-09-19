package wstxtest.wstream;

import java.io.*;

import javax.xml.stream.*;

import com.ctc.wstx.api.WstxOutputProperties;

/**
 * This unit test suite verifies Woodstox-specific writer-side options
 */
public class TestOptions
    extends BaseWriterTest
{
    public void testEmptyElemSpaces()
        throws IOException, XMLStreamException
    {
        /* Need to test both with and without space; as well as
         * using Writer and using an OutputStream (since backends
         * for the two are very different).
         */
        for (int i = 0; i < 6; ++i) {
            boolean space = ((i & 1) == 0);
            String str;
            boolean writer = (i < 2);
            StringWriter strw = new StringWriter();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            XMLStreamWriter sw;

            if (writer) {
                sw = getWriter(space, strw, null, null);
            } else {
                sw = getWriter(space, null, bos, (i < 4) ? "UTF-8" : "ISO-8859--1");
            }
            sw.writeStartDocument();
            sw.writeEmptyElement("root");
            sw.writeEndDocument();
            sw.close();
            // Should have a space!
            if (writer) {
                str = strw.toString();
            } else {
                str = new String(bos.toByteArray(), "UTF-8");
            }

            if (space) {
                if (str.indexOf("<root />") < 0) {
                    fail("Expected '<root />' when space is to be added: got '"+str+"'");
                }
            } else {
                if (str.indexOf("<root/>") < 0) {
                    fail("Expected '<root />' when space is NOT to be added: got '"+str+"'");
                }
            }
        }
    }

    private XMLStreamWriter getWriter(boolean addSpace, Writer sw, OutputStream out, String enc)
        throws IOException, XMLStreamException
    {
        XMLOutputFactory f = getOutputFactory();
        f.setProperty(WstxOutputProperties.P_ADD_SPACE_AFTER_EMPTY_ELEM,
                      Boolean.valueOf(addSpace));
        if (sw != null) {
            return f.createXMLStreamWriter(sw);
        }
        return f.createXMLStreamWriter(out, enc);
    }
}
