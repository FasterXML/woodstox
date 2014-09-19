package org.codehaus.stax.test.evt;

import java.util.*;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.*;

/**
 * Class that contains simple tests for making sure that event objets
 * created by the {@link XMLEventFactory} have expected properties.
 *
 * @author Tatu Saloranta
 */
public class TestEventFactory
    extends BaseEventTest
{
    public void testAttribute()
        throws XMLStreamException
    {
        XMLEventFactory f = getEventFactory();
        final String URI = "http://foo.com";

        QName attrName = new QName(URI, "attr", "ns");
        Attribute attr = f.createAttribute(attrName, "value");

        checkEventIsMethods(ATTRIBUTE, attr);
        testEventWritability(attr);

        Attribute attr2 = f.createAttribute("ns", URI, "attr", "value'2'");
        Attribute attr3 = f.createAttribute("attr", "this&more");

        /* No way to associate with a DTD, should have fairly basic
         * settings:
         */
        assertEquals("CDATA", attr.getDTDType());
        assertEquals("CDATA", attr2.getDTDType());
        assertEquals("CDATA", attr3.getDTDType());

        /* 26-Jan-2008, TSa: Hmmh. Should these be constructed as
         *   defined/specified or not?
         */
        if (!attr.isSpecified()) {
            /*
        assertTrue("Attribute 'ns:attr' should be created as 'defined'",
                   attr.isSpecified());
        assertTrue("Attribute 'ns:attr' should be created as 'defined'", 
                   attr2.isSpecified());
        assertTrue("Attribute 'attr' should be created as 'defined'", 
                   attr3.isSpecified());
            */
            warn("Attribute.isSpecified() returns false for XMLEventFactory generated Attribute events -- does that make sense?");
        }
        assertEquals("value", attr.getValue());
        assertEquals("value'2'", attr2.getValue());
        assertEquals("this&more", attr3.getValue());

        // Ok, then names...
        assertEquals(attrName, attr.getName());
        assertEquals(attrName, attr2.getName());

        QName name3 = attr3.getName();
        /* Alas, QName doesn't seem to retain nulls... so let's
         * be bit more lenient here:
         */
        assertEquals("attr", name3.getLocalPart());
        String str = name3.getPrefix();
        assertTrue(str == null || str.length() == 0);
        str = name3.getNamespaceURI();
        assertTrue(str == null || str.length() == 0);
    }

    public void testCData()
        throws XMLStreamException
    {
        final String contents = "test <some> text & more! [[]] --";
        XMLEventFactory f = getEventFactory();
        Characters c = f.createCData(contents);
        checkEventIsMethods(CHARACTERS, c);
        testEventWritability(c);

        assertEquals(contents, c.getData());
        assertTrue(c.isCData());
        assertFalse(c.isIgnorableWhiteSpace());
        assertFalse(c.isWhiteSpace());
    }

    public void testCharacters()
        throws XMLStreamException
    {
        final String contents = "test <some> text & more! [[]] --";
        XMLEventFactory f = getEventFactory();
        Characters c = f.createCharacters(contents);

        checkEventIsMethods(CHARACTERS, c);
        testEventWritability(c);

        assertEquals(contents, c.getData());
        assertFalse(c.isCData());
        assertFalse(c.isIgnorableWhiteSpace());
        assertFalse(c.isWhiteSpace());
    }

    public void testComment()
        throws XMLStreamException
    {
        final String content = "Comment - how interesting!";

        XMLEventFactory f = getEventFactory();
        Comment c = f.createComment(content);

        checkEventIsMethods(COMMENT, c);
        testEventWritability(c);

        assertEquals(content, c.getText());
    }

    public void testDTD()
        throws XMLStreamException
    {
        XMLEventFactory f = getEventFactory();
        DTD d = f.createDTD("<!DOCTYPE root SYSTEM 'http://foo' [ ]>");

        checkEventIsMethods(DTD, d);
        testEventWritability(d);

        // !!! TBI
    }

    public void testEndDocument()
        throws XMLStreamException
    {
        XMLEventFactory f = getEventFactory();
        EndDocument ed = f.createEndDocument();

        // No properties -- as long as we got instance of right type, it's ok
        checkEventIsMethods(END_DOCUMENT, ed);
        testEventWritability(ed);
    }

    public void testEndElement()
        throws XMLStreamException
    {
        XMLEventFactory f = getEventFactory();
        final String LOCALNAME = "elem";

        // prefix, uri, localName
        EndElement ee = f.createEndElement("", "", LOCALNAME);
        checkEventIsMethods(END_ELEMENT, ee);
        testEventWritability(ee);

        QName n = ee.getName();
        assertNotNull(n);
        assertEquals(LOCALNAME, n.getLocalPart());
    }

    public void testEntityReference()
        throws XMLStreamException
    {
//        XMLEventFactory f = getEventFactory();

        /* 22-Dec-2005, TSa: ... but how can we create the entity declaration
         *   that is needed? Should null be ok? For now, can't really test...
         */
        //EntityReference ref = f.createEntityReference("ref", decl);
        //checkEventIsMethods(ENTITY_REFERENCE, ref);
        //testEventWritability(ref);
    }

    public void testIgnorableSpace()
        throws XMLStreamException
    {
        final String contents = "  \t  \n  ";
        XMLEventFactory f = getEventFactory();
        Characters c = f.createIgnorableSpace(contents);

        checkEventIsMethods(CHARACTERS, c);
        testEventWritability(c);

        assertEquals(contents, c.getData());
        assertFalse(c.isCData());
        assertTrue(c.isIgnorableWhiteSpace());
        assertTrue(c.isWhiteSpace());
    }

    public void testNamespace()
        throws XMLStreamException
    {
        XMLEventFactory f = getEventFactory();
        final String PREFIX = "prefix";
        final String URI = "http://foo";

        // First default:
        Namespace ns = f.createNamespace(URI);

        checkEventIsMethods(NAMESPACE, ns);
        testEventWritability(ns);

        String prefix = ns.getPrefix();
        // Both null and empty are ok?
        if (prefix != null && prefix.length() > 0) {
            fail("Expected prefix to be null or empty for default namespace event object");
        }
        assertEquals(URI, ns.getNamespaceURI());
        assertTrue(ns.isDefaultNamespaceDeclaration());

        // Then non-default:
        ns = f.createNamespace(PREFIX, URI);
        checkEventIsMethods(NAMESPACE, ns);
        assertEquals(PREFIX, ns.getPrefix());
        assertEquals(URI, ns.getNamespaceURI());
        assertFalse(ns.isDefaultNamespaceDeclaration());
    }

    public void testProcInstr()
        throws XMLStreamException
    {
        XMLEventFactory f = getEventFactory();
        ProcessingInstruction pi = f.createProcessingInstruction("target", "data");
        checkEventIsMethods(PROCESSING_INSTRUCTION, pi);
        testEventWritability(pi);

        assertEquals("target", pi.getTarget());
        assertEquals("data", pi.getData());

    }

    public void testSpace()
        throws XMLStreamException
    {
        final String contents = "  \t  \n  ";
        XMLEventFactory f = getEventFactory();
        Characters c = f.createSpace(contents);
        assertEquals(contents, c.getData());

        checkEventIsMethods(CHARACTERS, c);
        testEventWritability(c);

        assertFalse(c.isCData());
        assertFalse(c.isIgnorableWhiteSpace());
        assertTrue(c.isWhiteSpace());
    }

    public void testStartDocument()
        throws XMLStreamException
    {
        XMLEventFactory f = getEventFactory();
        StartDocument sd = f.createStartDocument();
        checkEventIsMethods(START_DOCUMENT, sd);
        testEventWritability(sd);

        assertFalse(sd.encodingSet());
        assertFalse(sd.standaloneSet());

        final String ENCODING = "ISO-8859-1";
        final String VERSION = "1.0";
        sd = f.createStartDocument(ENCODING, VERSION, true);
        checkEventIsMethods(START_DOCUMENT, sd);
        assertTrue("Expected StartDocument.encodingSet() to be true when constructing via factory method that passes in non-empty encoding value", sd.encodingSet());
        assertTrue(sd.standaloneSet());
        assertEquals(ENCODING, sd.getCharacterEncodingScheme());
        assertEquals(VERSION, sd.getVersion());
    }

    public void testStartElement()
        throws XMLStreamException
    {
        final String LOCALNAME = "root";
        final String PREFIX = "ns";
        final String URI = "urn:whatever";

        XMLEventFactory f = getEventFactory();
        // prefix, uri, localname
        StartElement se = f.createStartElement("", "", LOCALNAME);
        testEventWritability(se);

        checkEventIsMethods(START_ELEMENT, se);
        QName n = se.getName();
        assertNotNull(n);
        assertEquals(LOCALNAME, n.getLocalPart());

        se = f.createStartElement(PREFIX, URI, LOCALNAME);
        checkEventIsMethods(START_ELEMENT, se);
        n = se.getName();
        assertNotNull(n);
        assertEquals(LOCALNAME, n.getLocalPart());
        assertEquals(PREFIX, n.getPrefix());
        assertEquals(URI, n.getNamespaceURI());
    }

    public void testStartElementWithAttrs()
        throws XMLStreamException
    {
        final String NS_URI = "http://foo";

        XMLEventFactory f = getEventFactory();
        ArrayList<Attribute> attrs = new ArrayList<Attribute>();
        Attribute attr1 = f.createAttribute(new QName("attr1"), "value");
        testEventWritability(attr1);
        attrs.add(attr1);
        checkEventIsMethods(ATTRIBUTE, attr1);
        attrs.add(f.createAttribute(new QName(NS_URI, "attr2"), "value2"));

        // Ok, so let's create the start element, and check it's ok:
        StartElement se = f.createStartElement(new QName("root"),
                                               attrs.iterator(), null);
        testEventWritability(se);
        checkEventIsMethods(START_ELEMENT, se);
        QName n = se.getName();
        assertNotNull(n);
        assertEquals("root", n.getLocalPart());

        // Then let's check both existing attrs:
        Attribute resultAttr = se.getAttributeByName(new QName("attr1"));
        assertNotNull(resultAttr);
        n = resultAttr.getName();
        assertEquals("attr1", n.getLocalPart());
        assertEquals("value", resultAttr.getValue());

        resultAttr = se.getAttributeByName(new QName(NS_URI, "attr2"));
        assertNotNull(resultAttr);
        n = resultAttr.getName();
        assertEquals("attr2", n.getLocalPart());
        assertEquals("value2", resultAttr.getValue());

        // Then non-existing ones (switch ns URIs around)
        assertNull(se.getAttributeByName(new QName(NS_URI, "attr1")));
        assertNull(se.getAttributeByName(new QName("attr2")));

        // and finally raw count
        Iterator<?> it = se.getAttributes();
        assertNotNull(it);
        int count = 0;

        while (it.hasNext()) {
            it.next();
            ++count;
        }
        assertEquals(2, count);
    }
}

