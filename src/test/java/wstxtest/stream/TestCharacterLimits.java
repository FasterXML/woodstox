package wstxtest.stream;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.ctc.wstx.api.WstxInputProperties;

@SuppressWarnings("resource")
public class TestCharacterLimits
    extends BaseStreamTest
{
    public TestCharacterLimits() { }

    public void testLongGetElementText() throws Exception {
        try {
            Reader reader = createLongReader("", "", false);
            XMLInputFactory factory = getNewInputFactory();
            factory.setProperty(WstxInputProperties.P_MAX_TEXT_LENGTH, Integer.valueOf(1000));
            XMLStreamReader xmlreader = factory.createXMLStreamReader(reader);
            while (xmlreader.next() != XMLStreamReader.START_ELEMENT) {
            }
            System.out.println(xmlreader.getElementText());
            fail("Should have failed");
        } catch (XMLStreamException ex) {
            _verifyTextLimitException(ex);
        }
    }
    
    public void testLongElementText() throws Exception {
        try {
            Reader reader = createLongReader("", "", false);
            XMLInputFactory factory = getNewInputFactory();
            factory.setProperty(WstxInputProperties.P_MAX_TEXT_LENGTH, Integer.valueOf(100000));
            XMLStreamReader xmlreader = factory.createXMLStreamReader(reader);
            while (xmlreader.next() != XMLStreamReader.START_ELEMENT) {
            }
            assertEquals(XMLStreamReader.CHARACTERS, xmlreader.next());
            while (xmlreader.next() != XMLStreamReader.START_ELEMENT) {
            }
            fail("Should have failed");
        } catch (XMLStreamException ex) {
            _verifyTextLimitException(ex);
        }
    }    
    public void testLongWhitespaceNextTag() throws Exception {
        try {
            Reader reader = createLongReader("", "", true);
            XMLInputFactory factory = getNewInputFactory();
            factory.setProperty(WstxInputProperties.P_MAX_TEXT_LENGTH, Integer.valueOf(1000));
            XMLStreamReader xmlreader = factory.createXMLStreamReader(reader);
            while (xmlreader.next() != XMLStreamReader.START_ELEMENT) {
            }
            xmlreader.nextTag();
            fail("Should have failed");
        } catch (XMLStreamException ex) {
            _verifyTextLimitException(ex);
        }
    }
        
    public void testLongWhitespace() throws Exception {
        try {
            Reader reader = createLongReader("", "", true);
            XMLInputFactory factory = getNewInputFactory();
            factory.setProperty(WstxInputProperties.P_MAX_TEXT_LENGTH, Integer.valueOf(50000));
            XMLStreamReader xmlreader = factory.createXMLStreamReader(reader);
            while (xmlreader.next() != XMLStreamReader.START_ELEMENT) {
            }
            assertEquals(XMLStreamReader.CHARACTERS, xmlreader.next());
            while (xmlreader.next() != XMLStreamReader.START_ELEMENT) {
            }
            fail("Should have failed");
        } catch (XMLStreamException ex) {
            _verifyTextLimitException(ex);
        }
    }
    
    public void testLongCDATA() throws Exception {
        try {
            Reader reader = createLongReader("<![CDATA[", "]]>", true);
            XMLInputFactory factory = getNewInputFactory();
            factory.setProperty(WstxInputProperties.P_MAX_TEXT_LENGTH, Integer.valueOf(50000));
            XMLStreamReader xmlreader = factory.createXMLStreamReader(reader);
            while (xmlreader.next() != XMLStreamReader.START_ELEMENT) {
            }
            assertEquals(XMLStreamReader.CDATA, xmlreader.next());
            while (xmlreader.next() != XMLStreamReader.START_ELEMENT) {
            }
            fail("Should have failed");
        } catch (XMLStreamException ex) {
            _verifyTextLimitException(ex);
        }
    }
    public void testLongCDATANextTag() throws Exception {
        try {
            Reader reader = createLongReader("<![CDATA[", "]]>", true);
            XMLInputFactory factory = getNewInputFactory();
            factory.setProperty(WstxInputProperties.P_MAX_TEXT_LENGTH, Integer.valueOf(1000));
            XMLStreamReader xmlreader = factory.createXMLStreamReader(reader);
            while (xmlreader.next() != XMLStreamReader.START_ELEMENT) {
            }
            xmlreader.nextTag();
            fail("Should have failed");
        } catch (XMLStreamException ex) {
            _verifyTextLimitException(ex);
        }
    }
    public void testLongComment() throws Exception {
        try {
            Reader reader = createLongReader("<!--", "-->", true);
            XMLInputFactory factory = getNewInputFactory();
            factory.setProperty(WstxInputProperties.P_MAX_TEXT_LENGTH, Integer.valueOf(1000));
            XMLStreamReader xmlreader = factory.createXMLStreamReader(reader);
            while (xmlreader.next() != XMLStreamReader.COMMENT) {
            }
            System.out.println(xmlreader.getText());
            fail("Should have failed");
        } catch (Exception ex) {
            _verifyTextLimitException(ex);
        }
    }
    public void testLongComment2() throws Exception {
        try {
            Reader reader = createLongReader("<!--", "-->", true);
            XMLInputFactory factory = getNewInputFactory();
            factory.setProperty(WstxInputProperties.P_MAX_TEXT_LENGTH, Integer.valueOf(1000));
            XMLStreamReader xmlreader = factory.createXMLStreamReader(reader);
            while (xmlreader.next() != XMLStreamReader.COMMENT) {
            }
            System.out.println(new String(xmlreader.getTextCharacters()));
            fail("Should have failed");
        } catch (Exception ex) {
            _verifyTextLimitException(ex);
        }
    }
    public void testLongCommentNextTag() throws Exception {
        try {
            Reader reader = createLongReader("<!--", "-->", true);
            XMLInputFactory factory = getNewInputFactory();
            factory.setProperty(WstxInputProperties.P_MAX_TEXT_LENGTH, Integer.valueOf(1000));
            XMLStreamReader xmlreader = factory.createXMLStreamReader(reader);
            while (xmlreader.next() != XMLStreamReader.COMMENT) {
            }
            xmlreader.nextTag();
            fail("Should have failed");
        } catch (XMLStreamException ex) {
            _verifyTextLimitException(ex);
        }
    }
    
    public void testLongCommentCoalescing() throws Exception {
        try {
            Reader reader = createLongReader("<!--", "-->", true);
            XMLInputFactory factory = getNewInputFactory();
            factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
            factory.setProperty(WstxInputProperties.P_MAX_TEXT_LENGTH, Integer.valueOf(1000));
            XMLStreamReader xmlreader = factory.createXMLStreamReader(reader);
            while (xmlreader.next() != XMLStreamReader.START_ELEMENT) {
            }
            xmlreader.nextTag();
            fail("Should have failed");
        } catch (XMLStreamException ex) {
            _verifyTextLimitException(ex);
        }
    }

    public void testLongWhitespaceCoalescing() throws Exception {
        try {
            Reader reader = createLongReader("", "", true);
            XMLInputFactory factory = getNewInputFactory();
            factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
            factory.setProperty(WstxInputProperties.P_MAX_TEXT_LENGTH, Integer.valueOf(1000));
            XMLStreamReader xmlreader = factory.createXMLStreamReader(reader);
            while (xmlreader.next() != XMLStreamReader.START_ELEMENT) {
            }
            xmlreader.nextTag();
            fail("Should have failed");
        } catch (XMLStreamException ex) {
            _verifyTextLimitException(ex);
        }
    }
    
    private Reader createLongReader(final String pre, final String post, final boolean ws) {
        final int max = Integer.MAX_VALUE;
        final StringBuffer start = new StringBuffer("<ns:element xmlns:ns=\"http://foo.com\">" + pre);
        return new Reader() {
            StringReader sreader = new StringReader(start.toString());
            int count;
            boolean done;
            
            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
                int i = sreader.read(cbuf, off, len);
                if (i == -1) {
                    if (count < max) {
                        if (ws) {
                            sreader = new StringReader("                              ");
                        } else {
                            sreader = new StringReader("1234567890123<?foo?>78901234567890");                            
                        }
                        count++;
                    } else if (!done) {
                        if (ws) {
                            sreader = new StringReader(post + "</ns:element>");
                        } else {
                            sreader = new StringReader(post + "<ns:el2>foo</ns:el2></ns:element>");
                        }
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
    }

    private void _verifyTextLimitException(Exception ex) {
        verifyException(ex, "Text size limit");
        verifyException(ex, "exceeded");
    }
}
