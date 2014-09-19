package wstxtest.wstream;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.stax2.*;

/**
 * This unit test suite verifies that output-side content validation
 * works as expected, when enabled.
 */
public class TestAttrValidation
    extends BaseWriterTest
{
    /**
     * Unit test suite for testing violations of structural checks, when
     * trying to output things in prolog/epilog.
     */
    public void testSimpleAttrs()
        throws Exception
    {
        XMLOutputFactory2 f = getOutputFactory();
        StringWriter w = new StringWriter();
        XMLStreamWriter sw = f.createXMLStreamWriter(w, "UTF-8");

        sw.writeStartDocument();
        sw.writeEmptyElement("root");
        try {
            sw.writeAttribute("foo", "Null is invalid: \0");
            fail("Expected an exception when trying to write attribute value with null character");
        } catch (XMLStreamException sex) {
            ;
        }
        sw.writeEndDocument();
    }
}
