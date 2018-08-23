package wstxtest.evt;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

import org.codehaus.stax2.XMLEventReader2;

import com.ctc.wstx.api.WstxInputProperties;

import wstxtest.stream.BaseStreamTest;

public class TestParsingModeForEvents
    extends BaseStreamTest
{
    final static String XML_MULTI_DOC =
            "<?xml version='1.0'?><root>text</root><!--comment-->\n"
            +"<?xml version='1.0'?><root>text</root>\n"
            +"<?xml version='1.0' standalone='yes'?><root>text</root><?proc instr><!--comment-->"
            +"<?xml version='1.0'?><root>text</root><!--comment-->"
            ;

    // [woodstox-core#42]
    public void testMultiDocumentWithEventReader() throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setCoalescing(f, true);
        f.setProperty(WstxInputProperties.P_INPUT_PARSING_MODE, WstxInputProperties.PARSING_MODE_DOCUMENTS);
        XMLEventReader2 er = constructEventReader(f, XML_MULTI_DOC);

        _checkEventDoc(er, 0);
        _checkEventDoc(er, 1);
        _checkEventDoc(er, 2);
        _checkEventDoc(er, 3);

        // and then the end
        assertFalse(er.hasNextEvent());
    }
    
    private void _checkEventDoc(XMLEventReader2 er, int seq) throws XMLStreamException
    {
        if (!er.hasNextEvent()) {
            fail("No more events: should start document #"+seq+" in multi-doc mode");
        }
        XMLEvent event;
        assertTokenType(START_DOCUMENT, er.nextEvent());
        assertTokenType(START_ELEMENT, (event = er.nextEvent()));
        assertEquals("root", event.asStartElement().getName().getLocalPart());
        assertTokenType(CHARACTERS, (event = er.nextEvent()));
        assertEquals("text", event.asCharacters().getData());
        assertTokenType(END_ELEMENT, (event = er.nextEvent()));
        assertEquals("root", event.asEndElement().getName().getLocalPart());

        // may get other types
        while (true) {
            event = er.nextEvent();
            switch (event.getEventType()) {
            case END_DOCUMENT:
                return;
            case PROCESSING_INSTRUCTION:
            case COMMENT:
                break;
            default:
                fail("Unexpected XMLEvent after document: "+event);
            }
        }
    }
}
