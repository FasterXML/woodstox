package wstxtest.sax;

import com.ctc.wstx.sax.WstxSAXParserFactory;
import com.ctc.wstx.stax.WstxInputFactory;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;
import wstxtest.BaseWstxTest;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.net.URL;

/**
 * Unit tests that verify handling of entity references during parsing.
 */
public class TestLexicalHandler extends BaseWstxTest {

    public void testReplaceEntityRefs() throws Exception {
        WstxInputFactory staxFactory = new WstxInputFactory();
        SAXParserFactory spf = new WstxSAXParserFactory(staxFactory);
        SAXParser sp = spf.newSAXParser();
        EventListener listener = Mockito.mock(EventListener.class);
        sp.parse(getInputSource("eyephone.xml"), new EventListenerHandler(listener));

        InOrder orderVerifier = Mockito.inOrder(listener);
        orderVerifier.verify(listener).startElement("prodname");
        orderVerifier.verify(listener).characters("eyePhone© 2.0");
        orderVerifier.verify(listener).endElement("prodname");
    }

    public void testWithoutReplaceEntityRefs() throws Exception {
        SAXParserFactory spf = new WstxSAXParserFactory();
        SAXParser sp = spf.newSAXParser();
        EventListener listener = Mockito.mock(EventListener.class);
        sp.parse(getInputSource("eyephone.xml"), new EventListenerHandler(listener));

        InOrder orderVerifier = Mockito.inOrder(listener);
        orderVerifier.verify(listener).startElement("prodname");
        orderVerifier.verify(listener).characters("eyePhone");
        orderVerifier.verify(listener).skippedEntity("copyright");
        orderVerifier.verify(listener).characters(" 2.0");
        orderVerifier.verify(listener).endElement("prodname");
    }

    public void testWithoutReplaceEntityRefsAndWithLexicalHandler() throws Exception {
        SAXParserFactory spf = new WstxSAXParserFactory();
        SAXParser sp = spf.newSAXParser();
        EventListener listener = Mockito.mock(EventListener.class);
        sp.setProperty("http://xml.org/sax/properties/lexical-handler", new EventListenerHandler(listener));
        sp.parse(getInputSource("eyephone.xml"), new EventListenerHandler(listener));

        InOrder orderVerifier = Mockito.inOrder(listener);
        orderVerifier.verify(listener).startElement("prodname");
        orderVerifier.verify(listener).characters("eyePhone");
        orderVerifier.verify(listener).startEntity("copyright");
        orderVerifier.verify(listener).characters("©");
        orderVerifier.verify(listener).endEntity("copyright");
        orderVerifier.verify(listener).characters(" 2.0");
        orderVerifier.verify(listener).endElement("prodname");
    }

    private InputSource getInputSource(String resource) {
        URL url = TestLexicalHandler.class.getResource(resource);
        return new InputSource(url.toString());
    }

    private static class EventListenerHandler extends DefaultHandler2 {

        private final EventListener eventListener;

        private EventListenerHandler(EventListener eventListener) {
            this.eventListener = eventListener;
        }

        @Override
        public void startEntity(String name) throws SAXException {
            eventListener.startEntity(name);
        }

        @Override
        public void endEntity(String name) throws SAXException {
            eventListener.endEntity(name);
        }

        @Override
        public void skippedEntity(String name) throws SAXException {
            eventListener.skippedEntity(name);
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            eventListener.characters(String.copyValueOf(ch, start, length));
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            eventListener.startElement(qName);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            eventListener.endElement(qName);
        }
    }

    private interface EventListener {

        void startElement(String name);

        void endElement(String name);

        void startEntity(String name);

        void endEntity(String name);

        void skippedEntity(String name);

        void characters(String content);
    }
}
