package org.codehaus.stax.test.wstream;

import javax.xml.stream.*;

import java.io.*;

/**
 * Set of unit tests for verifying operation of {@link XMLStreamWriter}
 * in "non-repairing" mode. It also includes writer tests for things
 * for which repair/non-repair modes should not matter (comments, PIs
 * etc).
 *
 * @author Tatu Saloranta
 */
public class TestSimpleWriter
    extends BaseWriterTest
{
    final String ISO_LATIN_ENCODING = "ISO-8859-1";

    public void testProlog()
        throws IOException, XMLStreamException
    {
        StringWriter strw = new StringWriter();
        XMLStreamWriter w = getNonRepairingWriter(strw);

        w.writeStartDocument();
        w.writeCharacters("\r\n  ");
        w.writeEmptyElement("test");
        w.writeCharacters("  \r");
        w.writeEndDocument();
        w.close();
        
        /* And then let's parse and verify it all. But are we guaranteed
         * to get SPACE? Let's not assume that
         */
        XMLStreamReader sr = constructNsStreamReader(strw.toString(), true);
        assertTokenType(START_DOCUMENT, sr.getEventType());

        int type = sr.next();
        if (type != START_ELEMENT) {
            assertTokenType(SPACE, type);
            assertEquals("\n  ", getAndVerifyText(sr));
            assertTokenType(START_ELEMENT, sr.next());
        }
        assertEquals("test", sr.getLocalName());
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("test", sr.getLocalName());
        // Another SPACE?
        type = sr.next();
        if (type != END_DOCUMENT) {
            assertTokenType(SPACE, type);
            assertEquals("  \n", getAndVerifyText(sr));
            assertTokenType(END_DOCUMENT, sr.next());
        }
    }

    public void testCData()
        throws IOException, XMLStreamException
    {
        StringWriter strw = new StringWriter();
        XMLStreamWriter w = getNonRepairingWriter(strw);

        final String CDATA_TEXT = "Let's test it with some ]] ]> data; <tag>s and && chars and all!";

        w.writeStartDocument();
        w.writeStartElement("test");
        w.writeCData(CDATA_TEXT);
        w.writeEndElement();
        w.writeEndDocument();
        w.close();
        
        // And then let's parse and verify it all:

        XMLStreamReader sr = constructNsStreamReader(strw.toString(), true);
        assertTokenType(START_DOCUMENT, sr.getEventType());
        assertTokenType(START_ELEMENT, sr.next());

        // Now, parsers are allowed to report CHARACTERS or CDATA
        int tt = sr.next();
        if (tt != CHARACTERS && tt != CDATA) {
            assertTokenType(CDATA, tt); // to cause failure
        }
        assertFalse(sr.isWhiteSpace());
        assertEquals(CDATA_TEXT, getAndVerifyText(sr));
        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(END_DOCUMENT, sr.next());
    }

    public void testCharacters()
        throws IOException, XMLStreamException
    {
        StringWriter strw = new StringWriter();
        XMLStreamWriter w = getNonRepairingWriter(strw);

        final String TEXT = "Ok; some content\nwith linefeeds and stuff (let's leave encoding as is though, no entities)\n";

        w.writeStartDocument();
        w.writeStartElement("test");
        w.writeCharacters(TEXT);
        w.writeStartElement("leaf");
        // Let's also test the other method...
        char[] tmp = new char[TEXT.length() + 4];
        TEXT.getChars(0, TEXT.length(), tmp, 2);
        w.writeCharacters(tmp, 2, TEXT.length());
        w.writeEndElement();
        w.writeEndElement();
        w.writeEndDocument();
        w.close();
        
        // And then let's parse and verify it all:

        XMLStreamReader sr = constructNsStreamReader(strw.toString(), true);
        assertTokenType(START_DOCUMENT, sr.getEventType());
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());
        assertFalse(sr.isWhiteSpace());
        assertEquals(TEXT, getAndVerifyText(sr));
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());
        assertFalse(sr.isWhiteSpace());
        assertEquals(TEXT, getAndVerifyText(sr));
        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(END_DOCUMENT, sr.next());
    }

    public void testComment()
        throws IOException, XMLStreamException
    {
        StringWriter strw = new StringWriter();
        XMLStreamWriter w = getNonRepairingWriter(strw);
        final String COMMENT1 = "comments are cool";
        final String COMMENT2 = "  some more\ncomments & other stuff";
        final String COMMENT3 = "Hah: <tag> </tag>";
        final String COMMENT4 = "  - - - \t   - -   \t";

        w.writeStartDocument();

        // Let's start with a comment
        w.writeComment(COMMENT1);

        w.writeStartElement("root");
        w.writeCharacters(" ");
        w.writeComment(COMMENT2);

        w.writeStartElement("branch");
        w.writeEndElement();
        w.writeStartElement("branch");
        w.writeComment(COMMENT3);
        w.writeEndElement();

        w.writeEndElement();
        // and trailing comment too
        w.writeComment(COMMENT4);

        w.writeEndDocument();
        w.close();
        
        // And then let's parse and verify it all:
        XMLStreamReader sr = constructNsStreamReader(strw.toString());
        assertTokenType(START_DOCUMENT, sr.getEventType());

        // First, PI with just target:
        assertTokenType(COMMENT, sr.next());
        assertEquals(COMMENT1, sr.getText());

        // start root element:
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());

        int tt = sr.next();
        if (tt != CHARACTERS && tt != SPACE) {
            fail("Expected a single space (CHARACTERS or SPACE), got "
                 +tokenTypeDesc(tt));
        }

        assertTokenType(COMMENT, sr.next());
        assertEquals(COMMENT2, sr.getText());

        // empty element ('branch')
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("branch", sr.getLocalName());
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("branch", sr.getLocalName());

        // another 'branch' element:
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("branch", sr.getLocalName());
        assertTokenType(COMMENT, sr.next());
        assertEquals(COMMENT3, sr.getText());
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("branch", sr.getLocalName());

        // closing root element
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());

        // trailing (prolog) comment:
        assertTokenType(COMMENT, sr.next());
        assertEquals(COMMENT4, sr.getText());

        assertTokenType(END_DOCUMENT, sr.next());
    }

    public void testDTD()
        throws IOException, XMLStreamException
    {
        // !!! TBI
    }

    /**
     * Unit test that tests how element writing works, including
     * checks for the namespace output.
     */
    public void testElements()
        throws IOException, XMLStreamException
    {
        StringWriter strw = new StringWriter();
        XMLStreamWriter w = getNonRepairingWriter(strw);
        final String URL_P1 = "http://p1.org";
        final String URL_P2 = "http://ns.p2.net/yeehaw.html";
        final String URL_DEF = "urn:default";

        final String TEXT = "  some text\n";

        w.writeStartDocument();
 
        w.setPrefix("p1", URL_P1);
        w.writeStartElement("test");
        w.writeNamespace("p1", URL_P1);

        w.setDefaultNamespace(URL_DEF);
        w.setPrefix("p2", URL_P2);
        w.writeStartElement("", "branch", URL_DEF);
        w.writeDefaultNamespace(URL_DEF);
        w.writeNamespace("p2", URL_P2);

        // Ok, let's see that we can also clear out the def ns:
        w.setDefaultNamespace("");
        w.writeStartElement("", "leaf", "");
        w.writeDefaultNamespace("");

        w.writeCharacters(TEXT);

        w.writeEndElement(); // first leaf

        w.writeEmptyElement(URL_P1, "leaf"); // second leaf

        w.writeEndElement(); // branch
        w.writeEndElement(); // root elem
        w.writeEndDocument();
        w.close();

        // And then let's parse and verify it all:
        XMLStreamReader sr = constructNsStreamReader(strw.toString());
        assertTokenType(START_DOCUMENT, sr.getEventType());

        // root element
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("test", sr.getLocalName());
        assertNoPrefixOrNs(sr);
        assertEquals(1, sr.getNamespaceCount());
        assertEquals("p1", sr.getNamespacePrefix(0));
        assertEquals(URL_P1, sr.getNamespaceURI(0));
        
        // first branch:
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("branch", sr.getLocalName());
        assertEquals(2, sr.getNamespaceCount());
        assertNoPrefix(sr);
        assertEquals(URL_DEF, sr.getNamespaceURI());

        // leaf
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());
        assertEquals(1, sr.getNamespaceCount());
        assertNoPrefix(sr);

        assertTokenType(CHARACTERS, sr.next());
        assertEquals(TEXT, getAllText(sr));
        // not: getAllText ^^^ moves cursor!

        assertTokenType(END_ELEMENT, sr.getEventType());
        assertEquals("leaf", sr.getLocalName());
        assertNoPrefixOrNs(sr);

        // another leaf:
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());
        assertEquals(0, sr.getNamespaceCount());
        assertEquals("p1", sr.getPrefix());
        assertEquals(URL_P1, sr.getNamespaceURI());

        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());
        assertEquals("p1", sr.getPrefix());
        assertEquals(URL_P1, sr.getNamespaceURI());

        // (close) branch
        try { // catching exception to add more info to failure msg...
            assertTokenType(END_ELEMENT, sr.next());
        } catch (XMLStreamException sex) {
            fail("Failed when trying to match </p1> (input '"+strw+"'): "+sex);
        }
        assertEquals("branch", sr.getLocalName());
        assertNoPrefix(sr);

        // closing root element
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("test", sr.getLocalName());
        assertNoPrefixOrNs(sr);

        assertTokenType(END_DOCUMENT, sr.next());
    }

    /**
     * Unit tests for documents that just do not use namespaces (independent
     * of whether namespace support is enabled for the writer or not)
     */
    public void testNonNsElements()
        throws IOException, XMLStreamException
    {
        StringWriter strw = new StringWriter();
        XMLStreamWriter w = getNonRepairingWriter(strw);
        final String TEXT = "Just some text...";
        final String TEXT_SPACE = "\n  ";

        w.writeStartDocument();

        w.writeStartElement("doc");
        w.writeCharacters(TEXT);

        w.writeStartElement("branch");
        w.writeEndElement();
        w.writeCharacters(TEXT_SPACE);
        w.writeStartElement("branch.2");
        w.writeCData(TEXT);
        w.writeEndElement();
        w.writeEmptyElement("_empty");

        w.writeEndElement();
        w.writeEndDocument();
        w.close();
        
        // And then let's parse and verify it all:
        XMLStreamReader sr = constructNsStreamReader(strw.toString());
        assertTokenType(START_DOCUMENT, sr.getEventType());

        // opening doc element
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("doc", sr.getLocalName());
        assertNoPrefixOrNs(sr);

        assertTokenType(CHARACTERS, sr.next());
        assertEquals(TEXT, getAllText(sr));
        // not: getAllText ^^^ moves cursor!

        // branch elements:
        assertTokenType(START_ELEMENT, sr.getEventType());
        assertEquals("branch", sr.getLocalName());
        assertNoPrefixOrNs(sr);
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("branch", sr.getLocalName());
        assertNoPrefixOrNs(sr);

        assertTokenType(CHARACTERS, sr.next());
        assertEquals(TEXT_SPACE, getAllText(sr));

        assertTokenType(START_ELEMENT, sr.getEventType());
        assertEquals("branch.2", sr.getLocalName());
        assertNoPrefixOrNs(sr);
        assertTextualTokenType(sr.next());
        assertEquals(TEXT, getAllText(sr));
        assertTokenType(END_ELEMENT, sr.getEventType());
        assertEquals("branch.2", sr.getLocalName());
        assertNoPrefixOrNs(sr);

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("_empty", sr.getLocalName());
        assertNoPrefixOrNs(sr);
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("_empty", sr.getLocalName());
        assertNoPrefixOrNs(sr);

        // closing doc element
        try {
            assertTokenType(END_ELEMENT, sr.next());
         } catch (XMLStreamException sex) {
            fail("Failed when trying to match </doc> (input '"+strw+"'): "+sex);
        }
        assertEquals("doc", sr.getLocalName());
        assertNoPrefixOrNs(sr);

        assertTokenType(END_DOCUMENT, sr.next());
    }

    public void testEmptyElements()
        throws IOException, XMLStreamException
    {
        StringWriter strw = new StringWriter();
        XMLStreamWriter w = getNonRepairingWriter(strw);

        w.writeStartDocument();

        w.writeStartElement("root");
        w.writeStartElement("branch");
        w.writeEmptyElement("leaf");

        w.writeEndElement(); // branch
        w.writeComment("comment"); // should be at same level as branch
        w.writeEndElement(); // root elem
        w.writeEndDocument();
        w.close();
        
        // And then let's parse and verify it all:
        XMLStreamReader sr = constructNsStreamReader(strw.toString());
        assertTokenType(START_DOCUMENT, sr.getEventType());

        // root element
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        // branch:
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("branch", sr.getLocalName());
        // leaf
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("leaf", sr.getLocalName());
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("branch", sr.getLocalName());

        assertTokenType(COMMENT, sr.next());
        assertEquals("comment", getAndVerifyText(sr));

        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());

        assertTokenType(END_DOCUMENT, sr.next());
    }

    public void testEntityRef()
        throws IOException, XMLStreamException
    {
        // !!! TBI
    }

    public void testProcInstr()
        throws IOException, XMLStreamException
    {
        StringWriter strw = new StringWriter();
        XMLStreamWriter w = getNonRepairingWriter(strw);
        final String LONG_DATA = "content & spaces <cool!>...  \t  ";
        final String LONG_DATA2 = "? >? ? >  ";

        w.writeStartDocument();

        // Let's start with a proc instr:
        w.writeProcessingInstruction("my_target");

        w.writeStartElement("root");
        w.writeCharacters("x");
        w.writeProcessingInstruction("target", "data");

        w.writeStartElement("branch");
        w.writeEndElement();
        w.writeStartElement("branch");
        w.writeProcessingInstruction("t", LONG_DATA);
        w.writeEndElement();

        w.writeEndElement();
        // and trailing proc instr too
        w.writeProcessingInstruction("xxx", LONG_DATA2);

        w.writeEndDocument();
        w.close();
        
        // And then let's parse and verify it all:
        XMLStreamReader sr = constructNsStreamReader(strw.toString());
        assertTokenType(START_DOCUMENT, sr.getEventType());

        // First, PI with just target:
        assertTokenType(PROCESSING_INSTRUCTION, sr.next());
        assertEquals("my_target", sr.getPITarget());
        String data = sr.getPIData();
        if (data != null && data.length() > 0) {
            fail("Expected empty (or null) data; got '"+data+"'");
        }
        // start root element:
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());

        assertTokenType(CHARACTERS, sr.next());
        assertEquals("x", sr.getText());

        // 'full' PI:
        assertTokenType(PROCESSING_INSTRUCTION, sr.next());
        assertEquals("target", sr.getPITarget());
        assertEquals("data", sr.getPIData());

        // empty element ('branch')
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("branch", sr.getLocalName());
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("branch", sr.getLocalName());

        // another 'branch' element:
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("branch", sr.getLocalName());
        assertTokenType(PROCESSING_INSTRUCTION, sr.next());
        assertEquals("t", sr.getPITarget());
        assertEquals(LONG_DATA, sr.getPIData());
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("branch", sr.getLocalName());

        // closing root element
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());

        // trailing (prolog) PI:
        assertTokenType(PROCESSING_INSTRUCTION, sr.next());
        assertEquals("xxx", sr.getPITarget());
        assertEquals(LONG_DATA2, sr.getPIData());

        assertTokenType(END_DOCUMENT, sr.next());
    }


    public void testXmlDeclImplicit()
        throws IOException, XMLStreamException
    {
        doTextXmlDecl(3);
    }

    public void testXmlDecl0args()
        throws IOException, XMLStreamException
    {
        doTextXmlDecl(0);
    }

    public void testXmlDecl1arg()
        throws IOException, XMLStreamException
    {
        doTextXmlDecl(1);
    }

    public void testXmlDecl2args()
        throws IOException, XMLStreamException
    {
        doTextXmlDecl(2);
    }

    /**
     * This simple unit tests checks handling of namespace prefix
     * information in non-repairing mode, wrt explictly defined
     * bindings.
     */
    public void testExplicitNsPrefixes()
        throws XMLStreamException
    {
        XMLStreamWriter writer = getNonRepairingWriter(new StringWriter());
        final String NS1 = "http://foo.com";
        final String NS2 = "http://bar.com";

        writer.writeStartDocument();
        writer.writeStartElement("root");

        // First, explicit binding:
        writer.writeStartElement("branch1");
        writer.setPrefix("ns", NS1);
        writer.setDefaultNamespace(NS2);
        assertEquals("ns", writer.getPrefix(NS1));
        assertEquals("", writer.getPrefix(NS2));
        // and just for fun, let's check they are not mixed up
        assertNull(writer.getPrefix("ns"));
        assertNull(writer.getPrefix("nosuchPrefix"));
        writer.writeEndElement();

        // these should be element scoped, and not exist any more
        assertNull(writer.getPrefix(NS1));
        assertNull(writer.getPrefix(NS2));

        // Next: should we check NamespaceContext?
        /* For now, let's not: it's unclear if that should
         * be unmodified "root" context, or live version
         */

        writer.writeEndElement(); // root
        writer.writeEndDocument();
    }

    /**
     * This simple unit tests checks handling of namespace prefix
     * information in non-repairing mode, wrt implied
     * bindings, generated by namespace output method.
     * Since information in 1.0
     * specs and associated Javadocs are sparse, these are based
     * on consensus on stax_builders list, as well as information
     * from Stax TCK unit tests.
     */
    public void testImplicitNsPrefixes()
        throws XMLStreamException
    {
        XMLStreamWriter writer = getNonRepairingWriter(new StringWriter());
        final String NS1 = "http://foo.com";
        final String NS2 = "http://bar.com";

        writer.writeStartDocument();
        writer.writeStartElement("root");

        /* And then, implicit binding(s). It is not obvious
         * whether such should be generated, from the specs,
         * but TCK indicates they should.
         */
        writer.writeNamespace("ns", NS2);
        writer.writeDefaultNamespace(NS1);

        assertEquals("", writer.getPrefix(NS1));
        assertEquals("ns", writer.getPrefix(NS2));

        /* Not quite sure if these should/need be scoped?
         * Probably?
         */
        writer.writeEndElement();
        assertNull(writer.getPrefix(NS1));
        assertNull(writer.getPrefix(NS2));

        writer.writeEndDocument();
    }

    /*
    ///////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////
     */

    private void doTextXmlDecl(int i)
        throws IOException, XMLStreamException
    {
        /* 4 modes: writeStartDocument with 0 args, 1 arg, 2 args,
         *   and without a call
         */
        StringWriter strw = new StringWriter();
        XMLStreamWriter w = getNonRepairingWriter(strw);
        
        switch (i) {
        case 0:
            w.writeStartDocument();
            break;
        case 1:
            /* Might well be ok to output other than 1.0, but the
             * reader may choke on others (like 1.1)?
             */
            w.writeStartDocument("1.0");
            break;
        case 2:
            w.writeStartDocument(ISO_LATIN_ENCODING, "1.0");
            break;
        case 3:
            // No output (shouldn't print out xml decl)
            break;
        }
        w.writeEmptyElement("root");
        w.writeEndDocument();
        w.close();
        
        XMLStreamReader sr = constructNsStreamReader(strw.toString());
        assertTokenType(START_DOCUMENT, sr.getEventType());
        
        // correct version?
        if (i == 3) {
            // Shouldn't have output anything:
            String ver = sr.getVersion();
            if (ver != null && ver.length() > 0) {
                fail("Non-null/empty version ('"+ver+"') when no START_DOCUMENT written explicitly");
            }
        } else {
            assertEquals("1.0", sr.getVersion());
        }
        
        // encoding?
        String enc = sr.getCharacterEncodingScheme();
        switch (i) {
        case 0:
            /* Not sure why the encoding has to default to utf-8... would
             * make sense to rather leave it out
             */
            /* quick note: the proper usage (as per xml specs) would be to
             * use UTF-8; Stax 1.0 mentions "utf-8", so let's accept
             * both for now (but let's not accept mixed cases)
             */
            if (!"utf-8".equals(enc) && !"UTF-8".equals(enc)) {
                fail("Expected either 'UTF-8' (xml specs) or 'utf-8' (stax specs) as the encoding output with no-arg 'writeStartDocument()' call (result doc = '"+strw.toString()+"')");
            }
            break;
        case 1:
            /* Interestingly enough, API comments do not indicate an encoding
             * default for 1-arg method!
             */
            assertNull(enc);
            break;
        case 2:
            assertEquals(ISO_LATIN_ENCODING, enc);
            break;
        case 3:
            assertNull(enc);
            break;
        }
        
        // What should sr.getEncoding() return? null? can't check...
        
        /* but stand-alone we can check; specifically:
         */
        assertFalse("XMLStreamReader.standalonSet() should return false if pseudo-attr not found",
                    sr.standaloneSet());

        /* now... it's too bad there's no way to explicitly specify
         * stand-alone value... so probably can not really test the
         * other method
         */ 
        //assertFalse("XMLStreamReader.isStandalone() should return false if pseudo-attr not found", sr.isStandalone());
        sr.close();
    }
}
