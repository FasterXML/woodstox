package stax2.stream;

import java.io.*;
import java.util.*;

import javax.xml.stream.*;

import stax2.BaseStax2Test;

/**
 * Unit test(s) that verify correct functioning of
 * {@link XMLStreamReader#getElementText}. This might actually
 * belong more to the core StaxTest, but as bug was found from
 * Woodstox, let's start by just adding them here first.
 *
 * @author Tatu Saloranta
 *
 * @since 3.0
 */
public class TestGetElement
    extends BaseStax2Test
{
    public void testLargeDocCoalesce() throws XMLStreamException
    {
        _testLargeDoc(true);
    }

    public void testLargeDocNonCoalesce() throws XMLStreamException
    {
        _testLargeDoc(false);
    }

    public void testLongSegmentCoalesce() throws XMLStreamException
    {
        _testLongSegment(true);
    }

    public void testLongSegmentNonCoalesce() throws XMLStreamException
    {
        _testLongSegment(false);
    }

    /*
    ///////////////////////////////////////////////
    // Second level test methods
    ///////////////////////////////////////////////
     */

    private void _testLargeDoc(boolean coalesce)
        throws XMLStreamException
    {
        final int LEN = 258000;
        final long SEED = 72;

        ByteArrayOutputStream bos = new ByteArrayOutputStream(LEN+2000);
        int rowCount = generateDoc(SEED, LEN, bos);
        XMLInputFactory f = getInputFactory();
        byte[] docData = bos.toByteArray();

        // Let's test both coalescing and non-coalescing:
        setCoalescing(f, coalesce);
        XMLStreamReader sr = f.createXMLStreamReader(new ByteArrayInputStream(docData));
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("data", sr.getLocalName());
        int actRowCount = 0;
        Random r = new Random(SEED);
        
        while (sr.nextTag() == START_ELEMENT) { // <row>
            ++actRowCount;
            assertEquals("row", sr.getLocalName());
            expectElemText(sr, "a", String.valueOf(r.nextInt()));
            expectElemText(sr, "b", String.valueOf(r.nextLong()));
            expectElemText(sr, "c", String.valueOf(r.nextBoolean()));
            assertTokenType(END_ELEMENT, sr.nextTag()); // match </row>
        }
        assertEquals(rowCount, actRowCount);
    }

    private void _testLongSegment(boolean coalesce)
        throws XMLStreamException
    {
        final int LEN = 129000;
        Random r = new Random(17);

        StringBuilder sb = new StringBuilder(LEN + 2000);
        while (sb.length() < LEN) {
            switch (r.nextInt() & 7) {
            case 0:
                sb.append("123");
                break;
            case 1:
                sb.append("foo\nbar");
                break;
            case 2:
                sb.append("rock & roll!");
                break;
            default:
                sb.append(sb.length());
                break;
            }
        }

        String contentStr = sb.toString();
        StringWriter strw = new StringWriter(LEN + 4000);
        XMLStreamWriter sw = getOutputFactory().createXMLStreamWriter(strw);

        sw.writeStartDocument();
        sw.writeStartElement("data");
        sw.writeCharacters(contentStr);
        sw.writeEndElement();
        sw.writeEndDocument();
        sw.close();
 
        XMLInputFactory f = getInputFactory();
        setCoalescing(f, coalesce);
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader(strw.toString()));
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("data", sr.getLocalName());
        String actStr = sr.getElementText();
        assertEquals(contentStr, actStr);
        assertTokenType(END_ELEMENT, sr.getEventType());
        assertTokenType(END_DOCUMENT, sr.next());
    }

    /*
    ///////////////////////////////////////////////
    // Helper methods
    ///////////////////////////////////////////////
     */

    private int generateDoc(long SEED, int LEN, ByteArrayOutputStream out)
        throws XMLStreamException
    {
        XMLStreamWriter sw = getOutputFactory().createXMLStreamWriter(out, "UTF-8");
        Random r = new Random(SEED);

        sw.writeStartDocument();
        sw.writeStartElement("data");

        int rowCount = 0;

        while (out.size() < LEN) {
            sw.writeStartElement("row");

            sw.writeStartElement("a");
            sw.writeCharacters(String.valueOf(r.nextInt()));
            sw.writeEndElement();
            sw.writeStartElement("b");
            sw.writeCharacters(String.valueOf(r.nextLong()));
            sw.writeEndElement();
            sw.writeStartElement("c");
            sw.writeCharacters(String.valueOf(r.nextBoolean()));
            sw.writeEndElement();
            sw.writeCharacters("\n"); // to make debugging easier

            sw.writeEndElement();
            sw.flush();
            ++rowCount;
        }
        sw.writeEndElement();
        sw.writeEndDocument();
        sw.close();
        return rowCount;
    }

    private void expectElemText(XMLStreamReader sr, String elem, String value)
        throws XMLStreamException
    {
        assertTokenType(START_ELEMENT, sr.nextTag());
        assertEquals(elem, sr.getLocalName());
        String actValue = sr.getElementText();
        if (!value.equals(actValue)) {
            fail("Expected value '"+value+"' (for element '"+elem+"'), got '"+actValue+"' (len "+actValue.length()+"): location "+sr.getLocation());
        }
        assertTokenType(END_ELEMENT, sr.getEventType());
    }
}
