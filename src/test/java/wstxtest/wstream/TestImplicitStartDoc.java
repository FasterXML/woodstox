package wstxtest.wstream;

import java.io.StringWriter;

import javax.xml.stream.*;

/**
 * This test suite verifies that it is ok to omit writing of
 * START_DOCUMENT event, to avoid getting xml declaration output
 * (for example to write xml fragments).
 * It was created to verify that issue
 * <a href="http://jira.codehaus.org/browse/WSTX-84">WSTX-84</a>
 * is not due to missing writeStartDocument() call.
 */
public class TestImplicitStartDoc
    extends BaseWriterTest
{
    public void testWriteImplicitStartDoc()
        throws XMLStreamException
    {
        XMLOutputFactory f = getOutputFactory();
        StringWriter strw = new StringWriter();
        XMLStreamWriter sw = f.createXMLStreamWriter(strw);
        try {
            sw.writeStartElement("root");
        } catch (Exception e) {
            fail("Did not expected writeStartElement to fail, got: "+e);
        }
        sw.writeCharacters("x");
        sw.writeEndElement();

        // Writing of end document should be optional, so let's check here
        sw.flush();
        assertEquals("<root>x</root>", strw.toString());

        sw.writeEndDocument();
        assertEquals("<root>x</root>", strw.toString());
    }
}
