package com.ctc.wstx.io;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TextEscaper}, used when serialising StAX2 events to a
 * raw {@link java.io.Writer} via {@code writeAsEncodedUnicode}.
 */
public class TestTextEscaper extends wstxtest.BaseJUnit4Test
{
    @Test
    public void testMarkupAndQuoteEscaping() throws Exception
    {
        StringWriter w = new StringWriter();
        TextEscaper.writeEscapedAttrValue(w, "a<b&c\"d");
        assertEquals("a&lt;b&amp;c&quot;d", w.toString());
    }

    // Tab, LF and CR in an attribute value have to be escaped as character
    // references; left literal they get folded to spaces by attribute-value
    // normalisation (XML 1.0 #3.3.3) when the document is read back.
    @Test
    public void testWhitespaceEscaping() throws Exception
    {
        StringWriter w = new StringWriter();
        TextEscaper.writeEscapedAttrValue(w, "x\ty\nz\rq");
        assertEquals("x&#x9;y&#xA;z&#xD;q", w.toString());
    }

    @Test
    public void testWhitespaceRoundTrip() throws Exception
    {
        final String value = "tab\there\nnl\rcr";

        StringWriter w = new StringWriter();
        TextEscaper.writeEscapedAttrValue(w, value);
        String doc = "<root attr=\"" + w.toString() + "\"/>";

        XMLInputFactory f = XMLInputFactory.newInstance();
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader(doc));
        assertEquals(XMLStreamConstants.START_ELEMENT, sr.next());
        assertEquals(value, sr.getAttributeValue(0));
        sr.close();
    }
}
