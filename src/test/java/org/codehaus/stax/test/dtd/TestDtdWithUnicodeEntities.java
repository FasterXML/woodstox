package org.codehaus.stax.test.dtd;

import javax.xml.stream.XMLStreamReader;

public class TestDtdWithUnicodeEntities extends BaseTestForDTD
{
    /**
     * Test to verify [WSTX-256], issues with character entities for DTD-declared
     * entities.
     */
    public void testEntitiesEmbedded() throws Exception
    {
        final String XML = "<!DOCTYPE component [\n"
            +"<!ENTITY Abc '&#00038;&#00035;&#00120;&#00049;&#00068;&#00053;&#00048;&#00052;&#00059;'>"
            +"]>\n"
            +"<root>&Abc;\n&Abc;</root>"
            ;        
        XMLStreamReader sr = getDTDAwareReader(XML, false);
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertTokenType(CHARACTERS, sr.next());
        String text = getAllText(sr);
        // we should have two surrogate pairs in there...
        assertEquals(5, text.length());
        assertEquals(0x1D504, Character.codePointAt(text, 0));
        assertEquals(0x1D504, Character.codePointAt(text, 3));
        assertTokenType(END_ELEMENT, sr.getEventType());
        assertEquals("root", sr.getLocalName());
        assertTokenType(END_DOCUMENT, sr.next());
    }
}
