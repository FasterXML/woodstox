package org.codehaus.stax.test.stream;

import javax.xml.stream.*;

import org.codehaus.stax.test.SimpleResolver;

/**
 * Unit test suite that tests handling of various kinds of entities.
 */
public class TestEntityRead
    extends BaseStreamTest
{
    /**
     * Method that tests properties of unresolved DTD event.
     */
    public void testEntityProperties()
        throws XMLStreamException
    {
        // Ns-awareness should make no different, but let's double-check it:
        doTestProperties(true);
        doTestProperties(false);
    }

    public void testValidPredefdEntities()
        throws XMLStreamException
    {
        String EXP = "Testing \"this\" & 'that' !? !";
        String XML = "<root>Testing &quot;this&quot; &amp; &apos;that&apos; &#x21;&#63; &#33;</root>";

        XMLStreamReader sr = getReader(XML, false, true, true);

        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());

        // Let's not count on coalescing working, though...
        StringBuffer sb = new StringBuffer(getAndVerifyText(sr));
        int type;

        while ((type = sr.next()) == CHARACTERS) {
            sb.append(getAndVerifyText(sr));
        }
        assertEquals(EXP, sb.toString());
        assertTokenType(END_ELEMENT, type);
    }

    /**
     * This unit test checks that handling of character entities works
     * as expected, including corner cases like characters that expand
     * to surrogate pairs in Java.
     */
    public void testValidCharEntities()
        throws XMLStreamException
    {
        String XML = "<root>surrogates: &#x50000;.</root>";
        XMLStreamReader sr = getReader(XML, true, true, true);

        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());
        // may still be split, though (buggy coalescing)
        StringBuffer sb = new StringBuffer(getAndVerifyText(sr));
        int type;

        while ((type = sr.next()) == CHARACTERS) {
            sb.append(getAndVerifyText(sr));
        }
        String result = sb.toString();
        String exp = "surrogates: \uD900\uDC00.";

        if (!exp.equals(result)) {
            failStrings("Expected character entity &x#50000 to expand to surrogate pair with chars 0xD900 and 0xDC00", exp, result);
        }

        assertTokenType(END_ELEMENT, type);
        sr.close();
    }

    public void testValidGeneralEntities()
        throws XMLStreamException
    {
        String EXP = "y yabc abc&";
        String XML = "<!DOCTYPE root [\n"
            +"<!ENTITY x 'y'><!ENTITY aa 'abc'>\n"
            +"<!ENTITY both '&x;&aa;'\n>"
            +"<!ENTITY myAmp '&amp;'\n>"
            +"]>\n"
            +"<root>&x; &both; &aa;&myAmp;</root>";

        XMLStreamReader sr = getReader(XML, false, true, true);

        assertTokenType(DTD, sr.next());
        int type = sr.next();
        if (type == SPACE) {
            type = sr.next();
        }
        assertTokenType(START_ELEMENT, type);
        try {
            assertTokenType(CHARACTERS, sr.next());
        } catch (XMLStreamException xse) {
            fail("Expected succesful entity expansion, got: "+xse);
        }

        String actual = getAndVerifyText(sr);
        assertEquals(EXP, actual);

        /* !!! TBI: test things like:
         *
         * - Allow using single and double quotes in entity expansion value
         *   via param entity expansion (see next entry)
         * - Rules for expanding char entities vs. generic entities (esp.
         *   regarding parameter entities)
         */
    }

    /**
     * Test that checks that generic parsed entities are returned as
     * entity reference events, when automatic entity expansion is disabled.
     */
    public void testUnexpandedEntities()
        throws XMLStreamException
    {
        /*
        String TEXT1 = "&quot;Start&quot;";
        String TEXT2 = "&End...";
        */
        String XML = "<!DOCTYPE root [\n"
            +" <!ENTITY myent 'data'>]>\n"
            +"<root>&amp;Start&quot;&myent;End&#33;</root>";

        XMLStreamReader sr = getReader(XML, false, true, false);

        assertTokenType(DTD, sr.next());
        int type = sr.next();

        // May or may not get SPACE events in epilog (before root)
        while (type == SPACE) {
            type = sr.next();
        }

        assertTokenType(START_ELEMENT, type);

        assertTokenType(CHARACTERS, sr.next());
        assertEquals("&Start\"", getAndVerifyText(sr));

        assertTokenType(ENTITY_REFERENCE, sr.next());
        assertEquals("myent", sr.getLocalName());
        assertEquals("data", getAndVerifyText(sr));

        assertTokenType(CHARACTERS, sr.next());
        assertEquals("End!", getAndVerifyText(sr));

        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(END_DOCUMENT, sr.next());

        /* And then, for good measure, let's just do a longer
         * one, but without exact type checks, and both with and
         * without coalescing:
         */
        
        XML = "<!DOCTYPE root [\n"
            +" <!ENTITY myent 'data'>]>\n"
            +"<root>&amp;Start&quot;&myent;End&#33;\n"
            +"   &#x21;&myent;&myent;<![CDATA[!]]>&myent;<![CDATA[...]]>&amp;"
            +"</root>";

        // First, no coalescing
        sr = getReader(XML, false, false, false);
        streamThrough(sr);

        // then with coalescing
        sr = getReader(XML, false, true, false);
        streamThrough(sr);
    }

    public void testUnexpandedEntities2()
        throws XMLStreamException
    {
        /* Note: as per XML 1.0 specs, non-char entities (including pre-defined
         * entities like 'amp' and 'lt'!) are not to be expanded before entity
         * that contains them is expanded... so they are returned unexpanded.
         * Char entities, however, are to be expanded.
         */
        String ENTITY_VALUE = "Something slightly longer &amp; challenging\nwhich may or may not work";
        String XML = "<!DOCTYPE root [\n"
            +" <!ENTITY myent '"+ENTITY_VALUE+"'>]>"
            +"<root>&myent;</root>";

        XMLStreamReader sr = getReader(XML, false, true, false);

        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(ENTITY_REFERENCE, sr.next());
        assertEquals("myent", sr.getLocalName());

        // Ok, let's try the other access method:
        /* 05-Apr-2006, TSa: Actually, getTextXxx() methods are not
         *   legal for ENTITY_REFERENCEs, can't check:
         */
        /*
        int len = sr.getTextLength();
        assertEquals(ENTITY_VALUE.length(), len);
        int start = sr.getTextStart();
        char[] ch = new char[len];
        sr.getTextCharacters(0, ch, 0, len);
        assertEquals(ENTITY_VALUE, new String(ch));
        */
        assertEquals(ENTITY_VALUE, sr.getText());

        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(END_DOCUMENT, sr.next());
    }

    /**
     * Test that checks that entities that expand to elements, comments
     * and processing instructions are properly handled.
     */
    public void testElementEntities()
        throws XMLStreamException
    {
        String XML = "<!DOCTYPE root [\n"
            +" <!ENTITY ent1 '<tag>text</tag>'>\n"
            +" <!ENTITY ent2 '<!--comment-->'>\n"
            +" <!ENTITY ent3 '<?proc instr?>'>\n"
            +" <!ENTITY ent4a '&ent4b;'>\n"
            +" <!ENTITY ent4b '&#65;'>\n"
            +"]>\n"
            +"<root>&ent1;&ent2;&ent3;&ent4a;</root>";

        XMLStreamReader sr = getReader(XML, true, true, true);

        assertTokenType(DTD, sr.next());
        // May or may not get whitespace
        int type = sr.next();
        if (type == SPACE) {
            type = sr.next();
        }
        assertTokenType(START_ELEMENT, type);
        assertEquals("root", sr.getLocalName());

        // First, entity that expands to element
        try {
            type = sr.next();
        } catch (XMLStreamException xse) {
            fail("Expected succesful entity expansion, got: "+xse);
        }
        if (type != START_ELEMENT) { // failure
            if (type == ENTITY_REFERENCE) { // most likely failure?
                fail("Implementation fails to re-parse general entity expansion text: instead of element <tag>, received entity reference &"+sr.getLocalName()+";");
            }
            if (type == CHARACTERS) {
                String text = sr.getText();
                fail("Implementation fails to re-parse general entity expansion text: instead of element <tag>, received text ["+text.length()+"]: '"+text+"'");
            }
            assertTokenType(START_ELEMENT, type);
        }
        assertEquals("tag", sr.getLocalName());
        assertTokenType(CHARACTERS, sr.next());
        assertEquals("text", sr.getText());
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("tag", sr.getLocalName());

        // Then one that expands to comment
        assertTokenType(COMMENT, sr.next());
        assertEquals("comment", sr.getText());

        // Then one that expands to a PI
        assertTokenType(PROCESSING_INSTRUCTION, sr.next());
        assertEquals("proc", sr.getPITarget());
        assertEquals("instr", sr.getPIData().trim());

        // Then one that expands to text (single char)
        assertTokenType(CHARACTERS, sr.next());
        assertEquals("A", sr.getText());

        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
    }

    /**
     * Test that verifies that it is possible to quote CDATA end marker
     * ("]]>") using character and general entities.
     */
    public void testQuotedCDataEndMarker()
        throws XMLStreamException
    {
        try {
            // First, using pre-defined/char entities
            String XML = "<root>"
                +"Ok the usual one: ]]&gt;"
                +" and then alternatives: &#93;]>"
                +", &#93;&#93;&gt;"
                +"</root>";
            XMLStreamReader sr = getReader(XML, true, false, true);
            streamThrough(sr);
        } catch (Exception e) {
            fail("Didn't except problems with pre-def/char entity quoted ']]>'; got: "+e);
        }

        try {
            // Then using general entities:
            String XML = "<!DOCTYPE root [\n"
                +"<!ENTITY doubleBracket ']]'>\n"
                +"]>\n"
                +"<root>"
                +" &doubleBracket;> and &doubleBracket;&gt;"
                +"</root>";
            XMLStreamReader sr = getReader(XML, true, false, true);
            streamThrough(sr);
        } catch (Exception e) {
            fail("Didn't except problems with general entity quoted ']]>'; got: "+e);
        }
    }

    /**
     * Test that ensures that entities can have quotes in them, if quotes
     * are expanded from (parameter) entities. For that need to use
     * external entities, or at least ext. subset.
     */
    /*
    public void testValidEntityWithQuotes()
        throws XMLStreamException
    {
    }
    */

    public void testInvalidEntityUndeclared()
        throws XMLStreamException
    {
        XMLStreamReader sr = getReader("<root>&myent;</root>",
                                       true, false, true);
        try {
            streamThrough(sr);
            fail("Expected an exception for invalid comment content");
        } catch (Exception e) { }
    }

    public void testInvalidEntityRecursive()
        throws XMLStreamException
    {
        XMLStreamReader sr = getReader
            ("<!DOCTYPE root [\n"
             +"<!ENTITY ent1 '&ent2;'>\n"
             +"<!ENTITY ent2 '&ent2;'>\n"
             +"]> <root>&ent1;</root>",
             false, true, true);

        streamThroughFailing(sr, "recursive general entity/ies");

        /* !!! TBI: test things like:
         *
         * - Incorrectly nested entities (only start element, no end etc)
         */
    }

    public void testInvalidEntityPEInIntSubset()
        throws XMLStreamException
    {
        /* Although PEs are allowed in int. subset, they can only be
         * used to replace whole declarations; not in entity value
         * expansions.
         */
        XMLStreamReader sr = getReader
            ("<!DOCTYPE root [\n"
             +"<!ENTITY % pe 'xxx'>\n"
             +"<!ENTITY foobar '%pe;'>\n"
             +"]> <root />",
             false, true, true);

        streamThroughFailing(sr, "declaring a parameter entity in the internal DTD subset");
    }

    /**
     * Test that ensures that an invalid 'partial' entity is caught;
     * partial meaning that only beginning part of an entity (ie ampersand
     * and zero or more of the first characters of entity id) come from
     * another expanded entity, and rest comes from content following.
     * Such partial entities are not legal according to XML specs.
     */
    public void testInvalidEntityPartial()
        throws XMLStreamException
    {
        XMLStreamReader sr = getReader
            ("<!DOCTYPE root [\n"
             +"<!ENTITY partial '&amp'>\n"
             +"]><root>&partial;;</root>",
             false, false, true);

        /* Hmmh. Actually, fully conforming implementations should throw
         * an exception when parsing internal DTD subset. But better
         * late than never; it's ok to fail on expansion too, as far as
         * this test is concerned.
         */
        int type1, type2;
        int lastType;

        try {
            type1 = sr.next();
            type2 = sr.next();
            while ((lastType = sr.next()) == CHARACTERS) {
                ;
            }
        } catch (XMLStreamException e) {
            return; // ok
        } catch (RuntimeException e) { // some impls throw lazy exceptions
            return; // ok
        }
        assertTokenType(DTD, type1);
        assertTokenType(START_ELEMENT, type2);
        fail("Expected an exception for partial entity reference: current token after text: "+tokenTypeDesc(lastType));
    }

    /**
     * This unit test checks that external entities can be resolved; and
     * to do that without requiring external files, will use a simple
     * helper resolver
     */
    public void testExternalEntityWithResolver()
        throws XMLStreamException
    {
        String ENTITY_VALUE1 = "some text from the external entity";
        String ACTUAL_VALUE1 = "ent='"+ENTITY_VALUE1+"'";
        String XML =
            "<!DOCTYPE root [\n"
            +"<!ENTITY extEnt SYSTEM 'myurl'>\n"
            +"]><root>ent='&extEnt;'</root>";

        // ns-aware, coalescing (to simplify verifying), entity expanding
        XMLInputFactory f = doGetFactory(true, true, true);

        if (!setSupportExternalEntities(f, true)) {
            reportNADueToExtEnt("testExternalEntityWithResolver");
            return;
        }

        setResolver(f, new SimpleResolver(ENTITY_VALUE1));

        // First, simple content without further expansion etc
        XMLStreamReader sr = constructStreamReader(f, XML);
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());
        assertEquals(ACTUAL_VALUE1, getAndVerifyText(sr));
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();

        // Then bit more complicated one:

        String ENTITY_VALUE2 = "external entity: <leaf /> this &amp; that &intEnt;";
        String ACTUAL_VALUE2a = "ent='external entity: ";
        String ACTUAL_VALUE2b = " this & that & more!'";
        String XML2 =
            "<!DOCTYPE root [\n"
            +"<!ENTITY extEnt SYSTEM 'myurl'>\n"
            +"<!ENTITY intEnt '&amp; more!'>\n"
            +"]><root>ent='&extEnt;'</root>";
        setResolver(f, new SimpleResolver(ENTITY_VALUE2));

        sr = constructStreamReader(f, XML2);
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());
        assertEquals(ACTUAL_VALUE2a, getAndVerifyText(sr));
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());
        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());
        assertEquals(ACTUAL_VALUE2b, getAndVerifyText(sr));
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();
    }

    /*
    ///////////////////////////////////////////////////////////
    // Private methods, shared test code
    ///////////////////////////////////////////////////////////
     */

    private void doTestProperties(boolean nsAware)
        throws XMLStreamException
    {
        XMLStreamReader sr = getReader
            ("<!DOCTYPE root [\n"
             +"<!ENTITY myent 'value'>\n"
             +"<!ENTITY ent2 PUBLIC 'myurl' 'whatever.xml'>\n"
             +"]><root>&myent;&ent2;</root>",
             nsAware, false, false);

        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(ENTITY_REFERENCE, sr.next());

        /* Ok, now we can test actual properties we
         * are interested in:
         */

        // Type info
        assertEquals(false, sr.isStartElement());
        assertEquals(false, sr.isEndElement());
        assertEquals(false, sr.isCharacters());
        assertEquals(false, sr.isWhiteSpace());

        // indirect type info
        /* 29-Jul-2004: It's kind of unintuitive, but API says hasName()
         *   is only true for start/end elements...
         */
        assertEquals(false, sr.hasName());

        /* And this returns true at least for internal entities, like the one
         * we hit first
         */
        assertEquals(true, sr.hasText());

        // Now, local name is accessible, still:
        assertEquals("myent", sr.getLocalName());

        // And replacement text too:
        assertEquals("value", getAndVerifyText(sr));
        
        assertNotNull(sr.getLocation());
        if (nsAware) {
            assertNotNull(sr.getNamespaceContext());
        }

        // And then let's check methods that should throw specific exception
        for (int i = 0; i <= 9; ++i) {
            String method = "";

            try {
                @SuppressWarnings("unused")
                Object result = null;
                switch (i) {
                case 0:
                    method = "getName";
                    result = sr.getName();
                    break;
                case 1:
                    method = "getPrefix";
                    result = sr.getPrefix();
                    break;
                case 2:
                    method = "getNamespaceURI";
                    result = sr.getNamespaceURI();
                    break;
                case 3:
                    method = "getNamespaceCount";
                    result = new Integer(sr.getNamespaceCount());
                    break;
                case 4:
                    method = "getAttributeCount";
                    result = new Integer(sr.getAttributeCount());
                    break;
                case 5:
                    method = "getPITarget";
                    result = sr.getPITarget();
                    break;
                case 6:
                    method = "getPIData";
                    result = sr.getPIData();
                    break;
                case 7:
                    method = "getTextCharacters";
                    result = sr.getTextCharacters();
                    break;
                case 8:
                    method = "getTextStart";
                    result = new Integer(sr.getTextStart());
                    break;
                case 9:
                    method = "getTextLength";
                    result = new Integer(sr.getTextLength());
                    break;
                }
                fail("Expected IllegalArgumentException, when calling "
                     +method+"() for ENTITY_REFERENCE");
            } catch (IllegalStateException iae) {
                ; // good
            }
        }


        // // Ok, and the second entity; an external one

        assertTokenType(ENTITY_REFERENCE, sr.next());

        assertEquals("ent2", sr.getLocalName());

        /* Now, text replacement... it seems like hasText() should still
         * return true, by default, but getText() (etc) should return
         * null?
         */
        String text = sr.getText();

        if (text != null && text.length() > 0) {
            fail("Expected getText() for external entity 'ent2' to return null or empty String; instead got '"+text+"'");
        }

        // // ok, should be good:
        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(END_DOCUMENT, sr.next());

    }

    /*
    ///////////////////////////////////////////////////////////
    // Private methods, other
    ///////////////////////////////////////////////////////////
     */

    /**
     * Note: all readers for this set of unit tests enable DTD handling;
     * otherwise entity definitions wouldn't be read. Validation shouldn't
     * need to be enabled just for that purpose.
     */
    private XMLStreamReader getReader(String contents, boolean nsAware,
                                      boolean coalescing, boolean replEntities)
        throws XMLStreamException
    {
        XMLInputFactory f = doGetFactory(nsAware, coalescing, replEntities);
        return constructStreamReader(f, contents);
    }

    private XMLInputFactory doGetFactory(boolean nsAware,
                                         boolean coalescing, boolean replEntities)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setNamespaceAware(f, nsAware);
        setSupportDTD(f, true);
        setCoalescing(f, coalescing);
        setSupportExternalEntities(f, true);
        setReplaceEntities(f, replEntities);
        setValidating(f, false);
        return f;
    }
}
