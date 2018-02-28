package org.codehaus.stax.test.wstream;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.stax2.XMLOutputFactory2;
import org.codehaus.stax2.io.EscapingWriterFactory;

public class CharacterEscapingTest
    extends BaseWriterTest
{
    public void testSimpleCdataEscaping() throws Exception
    {
        XMLOutputFactory outF = getNewOutputFactory();
        outF.setProperty(XMLOutputFactory2.P_TEXT_ESCAPER, new Escapers());

        _testSimpleEscaping(outF, "<tag>", "<tag>",
                "<root attr=\"&lt;tag>\">[tag]</root>");
        _testSimpleEscaping(outF, "r&d", "b&w",
                "<root attr=\"r&amp;d\">b&#x26;w</root>");
        _testSimpleEscaping(outF, "'donald'", "\"duck\"",
                "<root attr=\"'donald'\">'duck'</root>");
    }

    public void testSimpleAttributeEscaping() throws Exception
    {
        XMLOutputFactory outF = getNewOutputFactory();
        outF.setProperty(XMLOutputFactory2.P_ATTR_VALUE_ESCAPER, new Escapers());

        _testSimpleEscaping(outF, "<tag>", "<tag>",
                "<root attr=\"[tag]\">&lt;tag></root>");
        _testSimpleEscaping(outF, "r&d", "b&w",
                "<root attr=\"r&#x26;d\">b&amp;w</root>");
        _testSimpleEscaping(outF, "'donald'", "\"duck\"",
                "<root attr=\"&apos;donald&apos;\">\"duck\"</root>");
    }
    
    protected void _testSimpleEscaping(XMLOutputFactory outF,
            String attrValue, String elemValue, String expDoc) throws Exception
    {
        // First using Writer
        StringWriter strW = new StringWriter();
        XMLStreamWriter w = outF.createXMLStreamWriter(strW);
        _writeSimpleCData(w, attrValue, elemValue);
        w.close();
        assertEquals(expDoc, stripXmlDecl(strW.toString()).trim());

        // then OutputStream
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        w = outF.createXMLStreamWriter(bytes, "UTF-8");
        _writeSimpleCData(w, attrValue, elemValue);
        w.close();
        assertEquals(expDoc, stripXmlDecl(bytes.toString("UTF-8")).trim());
    }

    protected void _writeSimpleCData(XMLStreamWriter w, String attrValue, String elemValue) throws Exception
    {
        w.writeStartDocument();
        w.writeStartElement("root");
        w.writeAttribute("attr", attrValue);
        w.writeCharacters(elemValue);
        w.writeEndElement();
        w.writeEndDocument();
    }
    
    static class Escapers implements EscapingWriterFactory
    {
        @Override
        public Writer createEscapingWriterFor(Writer w, String enc) throws UnsupportedEncodingException {
            return new JsonValueWriter(w);
        }

        @Override
        public Writer createEscapingWriterFor(OutputStream out, String enc) throws UnsupportedEncodingException {
            return new JsonValueWriter(new OutputStreamWriter(out, enc));
        }
    }

    static class JsonValueWriter extends Writer {
        protected final Writer _out;

        public JsonValueWriter(Writer out) {
            _out = out;
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            for (int i = off, end = off+len; i < end; ++i) {
                write(cbuf[i]);
            }
        }

        @Override
        public void write(int ch) throws IOException
        {
            switch (ch) {
            case '<':
                _out.write("[");
                break;
            case '>':
                _out.write("]");
                break;
            case '&':
                _out.write("&#x26;");
                break;
            case '"': // replace with apostrophes for funsies
                _out.write("'");
                break;
            case '\'': // replace with XML escape for apostrophes
                _out.write("&apos;");
                break;
            default:
                _out.write(ch);
            }
        }
//         w.writeCharacters("'<r> & \"b\"'");

        @Override
        public void flush() throws IOException {
            _out.flush();
        }

        @Override
        public void close() throws IOException {
            _out.close();
        }
    }
}
