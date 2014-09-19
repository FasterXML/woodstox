package org.codehaus.stax2.ri;

import java.io.*;

import javax.xml.stream.*;

import stax2.BaseStax2Test;

/**
 * @author tsaloranta
 *
 * @since 4.1
 */
public class TestStax2ReaderAdapter extends BaseStax2Test
{
    public void testSimple() throws Exception
    {
        final String XML = "<root><a>xyz</a><b>abc</b></root>";
        XMLInputFactory f = getInputFactory();
        XMLStreamReader reader1 = f.createXMLStreamReader(new StringReader(XML));
        Stax2ReaderAdapter adapter = new Stax2ReaderAdapter(reader1);
        assertTokenType(START_DOCUMENT, adapter.getEventType());
        assertEquals(0, adapter.getDepth());

        assertTokenType(START_ELEMENT, adapter.next());
        assertEquals("root", adapter.getLocalName());
        assertEquals(1, adapter.getDepth());

        assertTokenType(START_ELEMENT, adapter.next());
        assertEquals(2, adapter.getDepth());
        assertEquals("a", adapter.getLocalName());
        assertTokenType(CHARACTERS, adapter.next());
        assertEquals(2, adapter.getDepth());
        assertEquals("xyz", adapter.getText());
        assertTokenType(END_ELEMENT, adapter.next());
        assertEquals(2, adapter.getDepth());
        assertEquals("a", adapter.getLocalName());

        assertTokenType(START_ELEMENT, adapter.next());
        assertEquals(2, adapter.getDepth());
        assertEquals("b", adapter.getLocalName());
        assertTokenType(CHARACTERS, adapter.next());
        assertEquals(2, adapter.getDepth());
        assertEquals("abc", adapter.getText());
        assertTokenType(END_ELEMENT, adapter.next());
        assertEquals(2, adapter.getDepth());
        assertEquals("b", adapter.getLocalName());

        assertTokenType(END_ELEMENT, adapter.next());
        assertEquals("root", adapter.getLocalName());
        assertEquals(1, adapter.getDepth());

        assertTokenType(END_DOCUMENT, adapter.next());
        assertEquals(0, adapter.getDepth());
    }

    public void testSimpleWithTypedText() throws Exception
    {
        final String XML = "<root><a>xyz</a><b>abc</b></root>";
        XMLInputFactory f = getInputFactory();
        XMLStreamReader reader1 = f.createXMLStreamReader(new StringReader(XML));
        Stax2ReaderAdapter adapter = new Stax2ReaderAdapter(reader1);
        assertTokenType(START_DOCUMENT, adapter.getEventType());
        assertEquals(0, adapter.getDepth());

        assertTokenType(START_ELEMENT, adapter.next());
        assertEquals("root", adapter.getLocalName());
        assertEquals(1, adapter.getDepth());

        assertTokenType(START_ELEMENT, adapter.next());
        assertEquals(2, adapter.getDepth());
        assertEquals("a", adapter.getLocalName());
        assertEquals("xyz", adapter.getElementText());
        assertTokenType(END_ELEMENT, adapter.getEventType());
        assertEquals(2, adapter.getDepth());
        assertEquals("a", adapter.getLocalName());

        assertTokenType(START_ELEMENT, adapter.next());
        assertEquals(2, adapter.getDepth());
        assertEquals("b", adapter.getLocalName());
        assertEquals("abc", adapter.getElementText());
        assertTokenType(END_ELEMENT, adapter.getEventType());
        assertEquals(2, adapter.getDepth());
        assertEquals("b", adapter.getLocalName());

        assertTokenType(END_ELEMENT, adapter.next());
        assertEquals(1, adapter.getDepth());
        assertEquals("root", adapter.getLocalName());

        assertTokenType(END_DOCUMENT, adapter.next());
        assertEquals(0, adapter.getDepth());
    }

    /**
     * Test actually copied from 'stax2.stream.TestXMLStreamReader2'
     */
    public void testWithDepthAndStuff() throws Exception
    {
        final String XML = "<root><child attr='123' /><child2>xxx</child2></root>";
        XMLInputFactory f = getInputFactory();
        XMLStreamReader reader1 = f.createXMLStreamReader(new StringReader(XML));
        Stax2ReaderAdapter sr = new Stax2ReaderAdapter(reader1);
    
        assertTokenType(START_DOCUMENT, sr.getEventType());
        assertEquals(0, sr.getDepth());
        assertFalse(sr.isEmptyElement());
    
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertEquals(1, sr.getDepth());
        assertFalse(sr.isEmptyElement());
    
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("child", sr.getLocalName());
        assertEquals(2, sr.getDepth());

        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("child", sr.getLocalName());
        assertEquals(2, sr.getDepth());
    
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("child2", sr.getLocalName());
        assertEquals(2, sr.getDepth());
    
        assertTokenType(CHARACTERS, sr.next());
        assertEquals("xxx", getAndVerifyText(sr));
        assertEquals(2, sr.getDepth());
    
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("child2", sr.getLocalName());
        assertEquals(2, sr.getDepth());
    
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertEquals(1, sr.getDepth());

        assertTokenType(END_DOCUMENT, sr.next());
        assertEquals(0, sr.getDepth());
    }
    
}
