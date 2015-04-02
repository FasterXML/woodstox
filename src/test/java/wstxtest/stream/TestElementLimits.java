package wstxtest.stream;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import javax.xml.stream.*;

import com.ctc.wstx.api.WstxInputProperties;
import com.ctc.wstx.sr.BasicStreamReader;

/**
 * Unit test suite that tests handling of limits for elements in
 * XML documents.
 *
 * @since 4.2
 */
@SuppressWarnings("resource")
public class TestElementLimits extends BaseStreamTest
{
    public void testSuperDeep() throws Exception 
    {
        final int max = Integer.MAX_VALUE;
        Reader reader = new Reader() {
            StringReader sreader = new StringReader("<ns:element xmlns:ns=\"http://foo.com\">");
            int count;
            boolean done;
            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
                int i = sreader.read(cbuf, off, len);
                if (i == -1) {
                    if (count < max) {
                        sreader = new StringReader("<ns:element>");
                        count++;
                    } else if (!done) {
                        sreader = new StringReader("</ns:element>");
                        done = true;
                    }
                    i = sreader.read(cbuf, off, len);
                }
                return i;
            }
            @Override
            public void close() throws IOException {
            }
        };
        XMLInputFactory factory = getNewInputFactory();
        factory.setProperty(WstxInputProperties.P_MAX_ELEMENT_DEPTH, Integer.valueOf(25));
        XMLStreamReader xmlreader = factory.createXMLStreamReader(reader);
        try {
            while (xmlreader.next() != XMLStreamReader.END_ELEMENT) {
                ;
            }
            fail("Should have failed");
        } catch (XMLStreamException ex) {
            verifyException(ex, "Maximum Element Depth limit");
        }
    }      
    public void testManyChildren() throws Exception 
    {
        final int max = Integer.MAX_VALUE;
        Reader reader = new Reader() {
            StringReader sreader = new StringReader("<ns:element xmlns:ns=\"http://foo.com\">");
            int count;
            boolean done;
            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
                int i = sreader.read(cbuf, off, len);
                if (i == -1) {
                    if (count < max) {
                        sreader = new StringReader("<ns:element/>");
                        count++;
                    } else if (!done) {
                        sreader = new StringReader("</ns:element>");
                        done = true;
                    }
                    i = sreader.read(cbuf, off, len);
                }
                return i;
            }
            @Override
            public void close() throws IOException {
            }
        };
        XMLInputFactory factory = getNewInputFactory();
        factory.setProperty(WstxInputProperties.P_MAX_CHILDREN_PER_ELEMENT, Integer.valueOf(100));
        try {
            XMLStreamReader xmlreader = factory.createXMLStreamReader(reader);
            while (xmlreader.next() != XMLStreamReader.END_DOCUMENT) {
            }
            fail("Should have failed");
        } catch (XMLStreamException ex) {
            verifyException(ex, "Maximum Number of Child Elements");
        }
    }  

    public void testManyElements() throws Exception 
    {
        
        try {
            XMLInputFactory factory = getNewInputFactory();
            factory.setProperty(WstxInputProperties.P_MAX_ELEMENT_COUNT, Integer.valueOf(100));
            XMLStreamReader xmlreader = factory.createXMLStreamReader(createManyElementReader());
            while (xmlreader.next() != XMLStreamReader.END_DOCUMENT) {
            }
            fail("Should have failed");
        } catch (XMLStreamException ex) {
            //expected
        }
        XMLInputFactory factory = getNewInputFactory();
        XMLStreamReader xmlreader = factory.createXMLStreamReader(createManyElementReader());
        try {
            ((BasicStreamReader)xmlreader).setProperty(WstxInputProperties.P_MAX_ELEMENT_COUNT,
                    Integer.valueOf(100));
            while (xmlreader.next() != XMLStreamReader.END_DOCUMENT) {
            }
            fail("Should have failed");
        } catch (XMLStreamException ex) {
            verifyException(ex, "Maximum Element Count limit");
        }
    }
    
    public void testCharacterLimit() throws Exception {
        try {
            XMLInputFactory factory = getNewInputFactory();
            factory.setProperty(WstxInputProperties.P_MAX_CHARACTERS, Integer.valueOf(100));
            XMLStreamReader xmlreader = factory.createXMLStreamReader(createManyElementReader());
            while (xmlreader.next() != XMLStreamReader.END_DOCUMENT) {
            }
            fail("Should have failed");
        } catch (XMLStreamException ex) {
            verifyException(ex, "Maximum document characters limit");
        }        
    }

    private Reader createManyElementReader() {
        final int max = Integer.MAX_VALUE;
        Reader reader = new Reader() {
            StringReader sreader = new StringReader("<ns:element xmlns:ns=\"http://foo.com\"><ns:child0>");
            int count;
            int count2;
            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
                int i = sreader.read(cbuf, off, len);
                if (i == -1) {
                    if ((count % 5000) == 1) {
                        String close = "</ns:child" + count2 + ">";
                        count2++;
                        sreader = new StringReader(close + "<ns:child" + count2 + "><ns:element/>");
                    } else if (count < max) {
                        sreader = new StringReader("<ns:element/>");
                        count++;
                    }
                    
                    i = sreader.read(cbuf, off, len);
                }
                return i;
            }
            @Override
            public void close() throws IOException {
            }
        };
        return reader;
    }
}

