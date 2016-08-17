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
        Reader reader = createLongReader("<![CDATA[", "]]>", true);
        XMLInputFactory factory = getNewInputFactory();
        factory.setProperty(WstxInputProperties.P_MAX_TEXT_LENGTH, Integer.valueOf(1000));
        XMLStreamReader xmlreader = factory.createXMLStreamReader(reader);
        try {
            int tokens = 0;
            while (xmlreader.next() != XMLStreamReader.START_ELEMENT) {
                ++tokens;
            }
            int code = xmlreader.nextTag();
            fail("Should have failed: instead got "+tokens+" tokens; and one following START_ELEMENT: "+code);
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
            // important, due to lazy handling only triggers problem here:
            String str = xmlreader.getText();
            fail("Should have failed; instead got: "+str);
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
    
    private Reader createLongReader(final String pre, final String post, final boolean ws)
    {
        // 17-Aug-2016, tatu: used to be Integer.MAX_VALUE, but since we are testing with
        //     way smaller limit, just do 1 meg
        final int maxLength = 16 * 1024 * 1024;
        final StringBuffer start = new StringBuffer("<ns:element xmlns:ns=\"http://foo.com\">" + pre);

        return new Reader() {
            StringReader sreader = new StringReader(start.toString());
            int count;
            boolean done;
            
            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
                if (done) {
                    return -1;
                }
                int i = sreader.read(cbuf, off, len);
                if (i < 0) {
                    String text;
                    if (count < maxLength) {
                        text = ws ? "                              "
                                : "1234567890123<?foo?>78901234567890";
                    } else {
                        text = post +
                                (ws ? "</ns:element>"
                                : "<ns:el2>foo</ns:el2></ns:element>");
                        done = true;
                    }
                    sreader = new StringReader(text);
                    count += text.length();
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
