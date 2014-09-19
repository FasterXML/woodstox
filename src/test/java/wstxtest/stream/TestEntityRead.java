package wstxtest.stream;

import java.util.*;
import javax.xml.stream.*;

import org.codehaus.stax2.*;

import com.ctc.wstx.api.WstxInputProperties;
import com.ctc.wstx.ent.EntityDecl;
import com.ctc.wstx.exc.WstxLazyException;
import com.ctc.wstx.sr.BasicStreamReader;

/**
 * This unit test suite checks to see that Woodstox implementation dependant
 * functionality works the way it's planned to. In some cases future StAX
 * revisions may dictate exact behaviour expected, but for now expected
 * behaviour is based on
 * a combination of educated guessing and intuitive behaviour. 
 */
public class TestEntityRead
    extends BaseStreamTest
{
    /**
     * This unit test checks that the information received as part of the
     * event, in non-expanding mode, is as expected.
     */
    public void testDeclaredInNonExpandingMode()
        throws XMLStreamException
    {
        String XML = "<!DOCTYPE root [\n"
             +" <!ENTITY myent 'value'>\n"
             +"]><root>text:&myent;more</root>"
            ;

        // Non-expanding, coalescing:
        BasicStreamReader sr = getReader(XML, false, true);
        assertTokenType(DTD, sr.next());
        DTDInfo dtd = sr.getDTDInfo();
        assertNotNull(dtd);

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());

        assertTokenType(CHARACTERS, sr.next());
        assertTokenType(ENTITY_REFERENCE, sr.next());
        assertEquals("myent", sr.getLocalName());
        EntityDecl ed = sr.getCurrentEntityDecl();
        assertNotNull(ed);
        assertEquals("myent", ed.getName());
        assertEquals("value", ed.getReplacementText());

        // The pure stax way:
        assertEquals("value", sr.getText());

        // Finally, let's see that location info is about right?
        Location loc = ed.getLocation();
        assertNotNull(loc);
        assertEquals(2, loc.getLineNumber());

        /* Hmmh. Not 100% if this location makes sense, but... it's the
         * current behaviour, so we can regression test it.
         */
        assertEquals(3, loc.getColumnNumber());
        // don't care about offsets here... location tests catch them

        assertTokenType(CHARACTERS, sr.next());
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());

        assertTokenType(END_DOCUMENT, sr.next());
        sr.close();
    }

    /**
     * This unit test checks that in non-expanding mode it is acceptable
     * to refer to undeclared entities.
     */
    public void testUndeclaredInNonExpandingMode()
        throws Exception
    {
        String XML = "<!DOCTYPE root [\n"
             +"]><root>text:&myent;more</root>"
            ;

        // Non-expanding, coalescing:
        BasicStreamReader sr = getReader(XML, false, true);
        assertTokenType(DTD, sr.next());
        DTDInfo dtd = sr.getDTDInfo();
        assertNotNull(dtd);

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());

        assertTokenType(CHARACTERS, sr.next());

        /* Exception would be a real possibility, so let's catch one (if
         * any) for debugging purposes:
         */

        try {
            assertTokenType(ENTITY_REFERENCE, sr.next());
        } catch (XMLStreamException sex) {
            fail("Did not except a stream exception on undeclared entity in non-entity-expanding mode; got: "+sex);
        }

        assertEquals("myent", sr.getLocalName());
        EntityDecl ed = sr.getCurrentEntityDecl();
        assertNull(ed);

        assertTokenType(CHARACTERS, sr.next());
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());

        assertTokenType(END_DOCUMENT, sr.next());
        sr.close();
    }

    /**
     * This unit test verifies that it's possible to add a Map of
     * expansions from Entity names to 
     */
    @SuppressWarnings("deprecation")
	public void testUndeclaredUsingCustomMap()
        throws XMLStreamException
    {
        // First, let's check actual usage:

        String XML = "<root>ok: &myent;&myent2;</root>";
        String EXP_TEXT = "ok: (simple)expand to ([text])";
        XMLInputFactory fact = getConfiguredFactory(true, true);
        Map<String,String> m = new HashMap<String,String>();
        m.put("myent", "(simple)");
        m.put("myent3", "[text]");
        m.put("myent2", "expand to (&myent3;)");
        fact.setProperty(WstxInputProperties.P_CUSTOM_INTERNAL_ENTITIES, m);
        XMLStreamReader sr = constructStreamReader(fact, XML);
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertTokenType(CHARACTERS, sr.next());
        assertEquals(EXP_TEXT, getAndVerifyText(sr));
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();

        /* And then see if we can query configured value and get expected
         * types of results
         */
        @SuppressWarnings("unchecked")
        Map<?,?> mr = (Map<Object,Object>) fact.getProperty(WstxInputProperties.P_CUSTOM_INTERNAL_ENTITIES);
        assertNotNull(mr);
        assertEquals(3, mr.size());
        for (Map.Entry<?,?> entry : mr.entrySet()) {
            String name = entry.getKey().toString();
            if (name.equals("myent") || name.equals("myent2")
                || name.equals("myent3")) {
                // fine, let's just verify the type
                EntityDecl ed = (EntityDecl) entry.getValue();
                assertNotNull(ed);
            } else {
                fail("Unexpected entity '"+name+"' in the custom entity map");
            }
        }
    }

    /**
     * This unit test checks that it is possible to deal with undeclared
     * entities in resolving mode too, as long as a special resolver
     * is used.
     */
    public void testUndeclaredButResolved()
        throws Exception
    {
        XMLInputFactory fact = getConfiguredFactory(true, true);

        for (int i = 0; i < 3; ++i) {
            String XML, expText;
            XMLResolver resolver;

            switch (i) {
            case 0:
                XML = "<root>value: &myent;</root>";
                resolver = new Resolver("myent", "value");
                expText = "value: value";
                break;
            case 1:
                XML = "<root>expands to:&myent;...</root>";
                resolver = new Resolver("myent", "X&amp;Y");
                expText = "expands to:X&Y...";
                break;
            default:
                XML = "<root>testing: &myent;</root>";
                resolver = new Resolver("dummy", "foobar");
                expText = ""; // whatever;
                break;
            }

            fact.setProperty(WstxInputProperties.P_UNDECLARED_ENTITY_RESOLVER,
                             resolver);
            XMLStreamReader sr = constructStreamReader(fact, XML);
            assertTokenType(START_ELEMENT, sr.next());
            assertEquals("root", sr.getLocalName());

            if (i == 2) { // Should throw an exception, then:
                try {
                    sr.next();
                    /*String text =*/ sr.getText(); // to force parsing
                    fail("Expected an exception for undefined entity 'myent' that doesn't resolve via customer resolver");
                } catch (XMLStreamException sex) {
                    ; // good;
                } catch (WstxLazyException lex) {
                    ; // likewise
                }
            } else {
                try {
                    assertTokenType(CHARACTERS, sr.next());
                } catch (XMLStreamException sex) {
                    // only caught to provide more meaningful fail info:
                    fail("Did not expect an exception, since 'myent' should have resolved; got: "+sex);
                }
                assertEquals(expText, sr.getText());
                assertTokenType(END_ELEMENT, sr.next());
                assertEquals("root", sr.getLocalName());
            }
        }
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
    private BasicStreamReader getReader(String contents, boolean replEntities,
                                       boolean coalescing)
        throws XMLStreamException
    {
        XMLInputFactory f = getConfiguredFactory(replEntities, coalescing);
        return (BasicStreamReader) constructStreamReader(f, contents);
    }

    private XMLInputFactory getConfiguredFactory(boolean replEntities, boolean coalescing)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setNamespaceAware(f, true);
        setSupportDTD(f, true);
        setValidating(f, false);
        setReplaceEntities(f, replEntities);
        setCoalescing(f, coalescing);
        return f;
    }

    /*
    ///////////////////////////////////////////////////////////
    // Helper classes
    ///////////////////////////////////////////////////////////
     */

    final static class Resolver
        implements XMLResolver
    {
        final String mKey, mValue;

        public Resolver(String key, String value) {
            mKey = key;
            mValue = value;
        }

        public Object resolveEntity(String publicID, String systemID, String baseURI, String namespace)
        {
            if (mKey.equals(namespace)) {
                return mValue;
            }
            return null;
        }
    }
}
