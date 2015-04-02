package wstxtest.sax;

import java.io.*;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

import com.ctc.wstx.sax.*;

import wstxtest.BaseWstxTest;

/**
 * Simple unit tests to verify that most fundamental parsing functionality
 * works via Woodstox SAX implementation.
 */
public class TestBasicSax
    extends BaseWstxTest
{
    final static String XML = "<?xml version='1.0'?>\n"
        +"<!DOCTYPE root []>"
        +"<root><!-- comment -->"
        +"<leaf attr='a&amp;b!'>rock&apos;n <![CDATA[roll]]></leaf><?proc instr?></root>  ";

    public void testSimpleNs()
        throws Exception
    {
        doTestSimple(true, false);
        doTestSimple(true, true);
    }

    public void testSimpleNonNs()
        throws Exception
    {
        doTestSimple(false, false);
        doTestSimple(false, true);
    }

    /**
     * Test for [WSTX-226]: ensure that given encoding is used
     * as specified
     */
    public void testEncoding() throws Exception
    {
        SAXParser parser = new WstxSAXParser();
        String encoding = "ISO-8859-1";
        String text = "mit hei\u00DFem Bem\u00FCh'n";
        byte[] content = ("<root>" + text + "</root>").getBytes(encoding);
        InputSource is = new InputSource(new ByteArrayInputStream(content));
        is.setEncoding(encoding);
        TextExtractor handler = new TextExtractor();
        parser.parse(is, handler);
        assertEquals(text, handler.getText());

        // And second time around, with declaration
        /* 02-Jan-2010, tatu: Looks like we can NOT reuse parser... why?
         *   Is that a bug or unsupported way to use it. Hmmh. Need to check.
         */
        parser = new WstxSAXParser();
        content = ("<?xml version='1.0' encoding='"+encoding+"' ?><root>" + text + "</root>").getBytes(encoding);
        is = new InputSource(new ByteArrayInputStream(content));
        is.setEncoding(encoding);
        handler = new TextExtractor();
        parser.parse(is, handler);
        assertEquals(text, handler.getText());
    }

    /*
    ////////////////////////////////////////////////////
    // Helper methods
    ////////////////////////////////////////////////////
     */

    public void doTestSimple(boolean ns, boolean useReader)
        throws Exception
    {
        // no need to test JAXP introspection...
        SAXParserFactory spf = new WstxSAXParserFactory();
        spf.setNamespaceAware(ns);
        SAXParser sp = spf.newSAXParser();
        MyHandler h = new MyHandler();

        InputSource src;

        if (useReader) {
            src = new InputSource(new StringReader(XML));
        } else {
            src = new InputSource(new ByteArrayInputStream(XML.getBytes("UTF-8")));
        }

        sp.parse(src, h);
        assertEquals(2, h._elems);
        assertEquals(1, h._attrs);
        assertEquals(11, h._charCount);
    }

    static class TextExtractor extends DefaultHandler {
        private final StringBuffer buffer = new StringBuffer();
        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            buffer.append(ch, start, length);
        }
        
        public String getText() {
            return buffer.toString();
        }
    }

    final static class MyHandler
        extends DefaultHandler
    {
        public int _elems, _attrs;

        public int _charCount;

        @Override
        public void characters(char[] ch, int start, int length) {
            _charCount += length;
        }
     
        @Override
        public void startElement(String uri, String ln, String qname,
                                 Attributes a)
        {
            ++_elems;
            int ac = a.getLength();
            _attrs += ac;

            for (int i = 0; i < ac; ++i) {
                a.getLocalName(i);
                a.getQName(i);
                a.getURI(i);
                a.getValue(i);
                a.getType(i);
            }
        }
    }
}
