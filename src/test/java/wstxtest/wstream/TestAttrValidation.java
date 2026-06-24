package wstxtest.wstream;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.stax2.*;
import org.junit.jupiter.api.Test;

import com.ctc.wstx.stax.WstxOutputFactory;

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
    @Test
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

    /**
     * Reproduction for [woodstox#308]: with non-namespace-aware output and
     * attribute validation enabled, writing consecutive sibling elements that
     * each carry the same attribute name erroneously failed with
     * "Trying to write attribute '...' twice", because the per-element
     * attribute tracking was not cleared on the empty-element close path.
     */
    @Test
    public void testNonNsSiblingAttrs()
        throws Exception
    {
        WstxOutputFactory f = new WstxOutputFactory();
        f.getConfig().doValidateAttributes(true);
        f.getConfig().doSupportNamespaces(false);

        StringWriter w = new StringWriter();
        XMLStreamWriter sw = f.createXMLStreamWriter(w);

        sw.writeStartDocument();
        sw.writeStartElement("List");

        sw.writeStartElement("Row");
        sw.writeAttribute("id", "1");
        sw.writeEndElement();

        sw.writeStartElement("Row");
        sw.writeAttribute("id", "2");
        sw.writeEndElement();

        sw.writeEndElement(); // </List>
        sw.writeEndDocument();
        sw.close();

        assertEquals("<List><Row id=\"1\"/><Row id=\"2\"/></List>",
                w.toString().substring(w.toString().indexOf("<List>")));
    }
}
