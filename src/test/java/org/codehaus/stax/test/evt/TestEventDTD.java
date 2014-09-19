package org.codehaus.stax.test.evt;

import javax.xml.stream.*;
import javax.xml.stream.events.*;

import java.util.*;

/**
 * Tests for verifying behavior of Event API implementation with events
 * that depend on (internal) DTD subset(s).
 *
 * @author Tatu Saloranta
 */
public class TestEventDTD
    extends BaseEventTest
{
    /**
     * Test that checks that entity objects are properly returned in
     * non-expanding mode.
     */
    public void testNonExpandingEntities()
        throws XMLStreamException
    {
        // Let's test all entity types
        final String URL1 = "nosuchdir/dummyent.xml";
        String XML = "<?xml version='1.0' ?>"
            +"<!DOCTYPE root [\n"
            +"<!ENTITY intEnt 'internal'>\n"
            +"<!ENTITY extParsedEnt SYSTEM '"+URL1+"'>\n"
            +"<!NOTATION notation PUBLIC 'notation-public-id'>\n"
            // Hmmh: can't test this, but let's declare it anyway
            +"<!ENTITY extUnparsedEnt SYSTEM 'http://localhost/dummy2' NDATA notation>\n"
            +"]>"
            //+"<root>&intEnt;&extParsedEnt;&extUnparsedEnt;</root>"
            +"<root>&intEnt;&extParsedEnt;</root>"
            ;

        for (int i = 0; i < 2; ++i) {
            boolean ns = (i & 1) != 0;
            /* 08-Sep-2007, TSa: Alas, not all impls (like sjsxp) support
             *   combination of non-expanding and coalescing; thus,
             *   can only test non-coalescing mode here.
             */
            //boolean coal = (i & 2) != 0;
            boolean coal = false;
            // false -> do not expand entities
            XMLEventReader er = getReader(XML, ns, coal, false);
            
            assertTokenType(START_DOCUMENT, er.nextEvent().getEventType());
            assertTokenType(DTD, er.nextEvent().getEventType());
            XMLEvent evt = er.nextEvent();
            assertTrue(evt.isStartElement());

            evt = er.nextEvent();
            assertTokenType(ENTITY_REFERENCE, evt.getEventType());
            EntityReference ref = (EntityReference) evt;
            assertNotNull(ref);
            assertTrue(ref.isEntityReference());
            assertEquals("intEnt", ref.getName());
            EntityDeclaration ed = ref.getDeclaration();
            assertNotNull("Declaration of internal entity 'intEnt' should not be null", ed);
            assertEquals("intEnt", ed.getName());
            assertEquals("internal", ed.getReplacementText());
            assertNullOrEmpty(ed.getNotationName());
            assertNullOrEmpty(ed.getPublicId());
            assertNullOrEmpty(ed.getSystemId());

            evt = er.nextEvent();
            assertTokenType(ENTITY_REFERENCE, evt.getEventType());
            ref = (EntityReference) evt;
            assertNotNull(ref);
            assertTrue(ref.isEntityReference());
            assertEquals("extParsedEnt", ref.getName());
            ed = ref.getDeclaration();
            assertNotNull("Declaration of external entity 'extParsedEnt' should not be null", ed);
            assertEquals("extParsedEnt", ed.getName());
            assertNullOrEmpty(ed.getNotationName());
            assertNullOrEmpty(ed.getPublicId());
            assertEquals(URL1, ed.getSystemId());

            /*
            evt = er.nextEvent();
            assertTokenType(ENTITY_REFERENCE, evt.getEventType());
            ref = (EntityReference) evt;
            assertEquals("extUnparsedEnt", ref.getName());
            assertNotNull(ref);
            assertTrue(ref.isEntityReference());
            ed = ref.getDeclaration();
            assertNotNull(ed);
            assertEquals("notation", ed.getNotationName());
            */

            evt = er.nextEvent();
            assertTrue(evt.isEndElement());
            assertTokenType(END_DOCUMENT, er.nextEvent().getEventType());
            assertFalse(er.hasNext());
        }
    }

    /**
     * This unit test checks that a DTD event that results from parsing
     * a valid document, to the degree it is done without having to
     * validate anything
     */
    public void testValidDtdEvent()
        throws XMLStreamException
    {
        String XML = "<?xml version='1.0' ?>"
            +"<!DOCTYPE root [\n"
            +"<!ENTITY intEnt 'internal'>\n"
            +"<!ENTITY extParsedEnt SYSTEM 'url:dummy'>\n"
            +"<!NOTATION notation PUBLIC 'notation-public-id'>\n"
            +"<!NOTATION notation2 SYSTEM 'url:dummy'>\n"
            +"<!ENTITY extUnparsedEnt SYSTEM 'url:dummy2' NDATA notation>\n"
            +"]>"
            +"<root />"
            ;
        for (int i = 0; i < 4; ++i) {
            boolean ns = (i & 1) != 0;
            boolean coal = (i & 2) != 0;
            XMLEventReader er = getReader(XML, ns, coal);
            
            assertTokenType(START_DOCUMENT, er.nextEvent().getEventType());
            XMLEvent evt = er.nextEvent();
            assertTokenType(DTD, evt.getEventType());
            DTD dtd = (DTD) evt;
            /* isXxx() methods and writability are tested by a different
             * unit test (in TestEventTypes()); here let's just check for
             * entities and notations
             */
            List<?> entities = dtd.getEntities();
            assertNotNull("Entity list for a DTD declaration with entities should not be null", entities);
            assertEquals(3, entities.size());

            // Let's also verify they are all of right type...
            testListElems(entities, EntityDeclaration.class);

            List<?> notations = dtd.getNotations();

            // Let's also verify they are all of right type...
            testListElems(notations, NotationDeclaration.class);

            assertNotNull("Notation list for a DTD declaration with notations should not be null", entities);
            assertNotNull(notations);
            assertEquals(2, notations.size());
        }
    }

    /*
    ///////////////////////////////////////////////////////////
    // Internal methods:
    ///////////////////////////////////////////////////////////
     */

    private XMLEventReader getReader(String contents, boolean nsAware,
                                     boolean coalesce)
        throws XMLStreamException
    {
        return getReader(contents, nsAware, coalesce, true);
    }

    private XMLEventReader getReader(String contents, boolean nsAware,
                                     boolean coalesce, boolean expandEnt)
        throws XMLStreamException
    {
        //XMLInputFactory f = getInputFactory();
        XMLInputFactory f = getNewInputFactory();
        setNamespaceAware(f, nsAware);
        setCoalescing(f, coalesce);
        setSupportDTD(f, true);
        setValidating(f, false);
        setReplaceEntities(f, expandEnt);
        return constructEventReader(f, contents);
    }

    private void testListElems(List<?> l, Class<?> expType)
    {
        Iterator<?> it = l.iterator();
        while (it.hasNext()) {
            Object o = it.next();
            assertNotNull(o);
            assertTrue(expType.isAssignableFrom(o.getClass()));
        }
    }
}
