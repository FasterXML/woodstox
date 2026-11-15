package wstxtest.stream;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.junit.jupiter.api.Test;

/**
 * Tests that namespace prefixes resolve correctly when a large number of
 * bindings is in scope (prefix lookups then go through an index instead of
 * a scan of all bindings).
 */
public class TestManyNsBindings extends BaseStreamTest
{
    @Test
    public void testScopingWithManyBindings() throws Exception
    {
        final int COUNT = 200;
        StringBuilder sb = new StringBuilder("<a:root xmlns:a='urn:outer'");
        for (int i = 0; i < COUNT; ++i) {
            sb.append(" xmlns:p").append(i).append("='urn:outer").append(i).append("'");
        }
        sb.append("><mid");
        for (int i = 0; i < COUNT; i += 2) {
            sb.append(" xmlns:p").append(i).append("='urn:inner").append(i).append("'");
        }
        sb.append(" xmlns:a='urn:inner'><a:leaf p0:x='1' p1:y='2'/></mid>");
        sb.append("<a:leaf p0:x='1' p1:y='2'/></a:root>");

        XMLStreamReader sr = constructNsStreamReader(sb.toString(), true);
        assertTokenType(START_ELEMENT, sr.next()); // root
        assertEquals("urn:outer", sr.getNamespaceURI());
        assertTokenType(START_ELEMENT, sr.next()); // mid
        assertEquals("urn:inner", sr.getNamespaceURI("a"));
        assertEquals("urn:inner10", sr.getNamespaceURI("p10"));
        assertEquals("urn:outer11", sr.getNamespaceURI("p11"));

        assertTokenType(START_ELEMENT, sr.next()); // leaf inside mid
        assertEquals("urn:inner", sr.getNamespaceURI());
        assertEquals("urn:inner0", sr.getAttributeNamespace(0));
        assertEquals("urn:outer1", sr.getAttributeNamespace(1));
        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(END_ELEMENT, sr.next()); // mid

        assertTokenType(START_ELEMENT, sr.next()); // leaf after mid
        assertEquals("urn:outer", sr.getNamespaceURI());
        assertEquals("urn:outer0", sr.getAttributeNamespace(0));
        assertEquals("urn:outer1", sr.getAttributeNamespace(1));
        assertEquals("urn:outer10", sr.getNamespaceURI("p10"));
        assertNull(sr.getNamespaceURI("q"));
        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();

        // and a prefix bound only in a closed scope is no longer bound
        sb = new StringBuilder("<root");
        for (int i = 0; i < COUNT; ++i) {
            sb.append(" xmlns:p").append(i).append("='urn:x").append(i).append("'");
        }
        sb.append("><q:a xmlns:q='urn:q'/><q:b/></root>");
        sr = constructNsStreamReader(sb.toString(), true);
        assertTokenType(START_ELEMENT, sr.next()); // root
        assertTokenType(START_ELEMENT, sr.next()); // q:a
        assertEquals("urn:q", sr.getNamespaceURI());
        assertTokenType(END_ELEMENT, sr.next());
        try {
            sr.next();
            fail("Should have failed on unbound prefix 'q'");
        } catch (XMLStreamException e) {
            verifyException(e, "Undeclared namespace prefix \"q\"");
        }
    }
}
