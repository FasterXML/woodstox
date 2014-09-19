package wstxtest.stream;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.ctc.wstx.api.WstxInputProperties;

public class TestAttributeLimits extends BaseStreamTest
{
    public void testMaxAttributesLimit() throws Exception
    {
        final int max = 100;
        XMLInputFactory factory = getNewInputFactory();
        factory.setProperty(WstxInputProperties.P_MAX_ATTRIBUTES_PER_ELEMENT, Integer.valueOf(50));
        Reader reader = new Reader() {
            StringReader sreader = new StringReader("<ns:element xmlns:ns=\"http://foo.com\"");
            int count;
            boolean done;
            public int read(char[] cbuf, int off, int len) throws IOException {
                int i = sreader.read(cbuf, off, len);
                if (i == -1) {
                    if (count < max) {
                        sreader = new StringReader(" attribute" + count++ + "=\"foo\"");
                    } else if (!done) {
                        sreader = new StringReader("/>");
                        done = true;
                    }
                    i = sreader.read(cbuf, off, len);
                }
                return i;
            }
            public void close() throws IOException {
            }
        };
        XMLStreamReader xmlreader = factory.createXMLStreamReader(reader);
        try {
            xmlreader.nextTag();
            fail("Should have failed");
        } catch (XMLStreamException ex) {
            verifyException(ex, "Attribute limit (50)");
        }
    }

    public void testLongAttribute() throws Exception {
        final int max = 500;
        Reader reader = new Reader() {
            StringReader sreader = new StringReader("<ns:element xmlns:ns=\"http://foo.com\" blah=\"");
            int count;
            boolean done;
            public int read(char[] cbuf, int off, int len) throws IOException {
                int i = sreader.read(cbuf, off, len);
                if (i == -1) {
                    if (count < max) {
                        sreader = new StringReader("          ");
                        count++;
                    } else if (!done) {
                        sreader = new StringReader("\"/>");
                        done = true;
                    }
                    i = sreader.read(cbuf, off, len);
                }
                return i;
            }
            public void close() throws IOException { }
        };
        try {
            XMLInputFactory factory = getNewInputFactory();
            factory.setProperty(WstxInputProperties.P_MAX_ATTRIBUTE_SIZE, Integer.valueOf(100));
            XMLStreamReader xmlreader = factory.createXMLStreamReader(reader);
            while (xmlreader.next() != XMLStreamReader.END_DOCUMENT) {
            }
            fail("Should have failed");
        } catch (XMLStreamException ex) {
            verifyException(ex, "Maximum attribute size");
        }
        reader.close(); // never gets here
    }
}
