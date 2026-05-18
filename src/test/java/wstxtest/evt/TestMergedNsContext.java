package wstxtest.evt;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.Namespace;

import com.ctc.wstx.evt.MergedNsContext;
import com.ctc.wstx.util.BaseNsContext;
import org.junit.jupiter.api.Test;


/**
 * Unit tests for {@link MergedNsContext}, the hierarchical
 * {@link NamespaceContext} used by the event-construction API.
 */
public class TestMergedNsContext extends wstxtest.BaseJUnit4Test
{
    private final XMLEventFactory eventFactory = XMLEventFactory.newInstance();

    // ---------- basic prefix/URI lookup ----------

    @Test
    public void testLookupNullParent()
    {
        BaseNsContext ctxt = MergedNsContext.construct(null, ns("a", "uri-a", "b", "uri-b"));
        assertEquals("uri-a", ctxt.getNamespaceURI("a"));
        assertEquals("uri-b", ctxt.getNamespaceURI("b"));
        // unknown prefix -> null
        assertNull(ctxt.getNamespaceURI("nope"));

        assertEquals("a", ctxt.getPrefix("uri-a"));
        assertEquals("b", ctxt.getPrefix("uri-b"));
        assertNull(ctxt.getPrefix("uri-missing"));
    }

    @Test
    public void testEmptyLocalListWithNullParent()
    {
        // construct(null, null) should be safe and yield an empty context
        BaseNsContext ctxt = MergedNsContext.construct(null, null);
        assertNull(ctxt.getNamespaceURI("anything"));
        assertNull(ctxt.getPrefix("uri-x"));
        assertFalse(ctxt.getPrefixes("uri-x").hasNext());
        assertFalse(ctxt.getNamespaces().hasNext());
    }

    @Test
    public void testDefaultNamespace()
    {
        // Default namespace is represented by empty-string prefix
        BaseNsContext ctxt = MergedNsContext.construct(
                null, Collections.singletonList(eventFactory.createNamespace("default-uri")));
        assertEquals("default-uri", ctxt.getNamespaceURI(""));
        assertEquals("", ctxt.getPrefix("default-uri"));
    }

    // ---------- parent fallback ----------

    @Test
    public void testParentFallbackForUnknownPrefix()
    {
        BaseNsContext parent = MergedNsContext.construct(null, ns("p", "parent-uri"));
        BaseNsContext child = MergedNsContext.construct(parent, ns("c", "child-uri"));

        // Local wins
        assertEquals("child-uri", child.getNamespaceURI("c"));
        // Unknown locally -> delegate to parent
        assertEquals("parent-uri", child.getNamespaceURI("p"));
        // Same for prefix lookup
        assertEquals("p", child.getPrefix("parent-uri"));
        assertEquals("c", child.getPrefix("child-uri"));
    }

    @Test
    public void testGetPrefixesCombinesLocalAndParent()
    {
        // Same URI mapped to two different prefixes — local AND parent
        BaseNsContext parent = MergedNsContext.construct(null, ns("p1", "shared-uri"));
        BaseNsContext child = MergedNsContext.construct(parent,
                ns("p2", "shared-uri", "other", "other-uri"));

        Set<String> prefixes = collect(child.getPrefixes("shared-uri"));
        assertEquals(new HashSet<>(Arrays.asList("p1", "p2")), prefixes);
    }

    @Test
    public void testChildPrefixShadowsParent()
    {
        // When the SAME prefix is declared in both child and parent, child wins
        // for prefix->URI lookup — the most important shadowing semantic.
        BaseNsContext parent = MergedNsContext.construct(null, ns("x", "parent-uri"));
        BaseNsContext child  = MergedNsContext.construct(parent, ns("x", "child-uri"));

        assertEquals("child-uri", child.getNamespaceURI("x"));
        assertEquals("x", child.getPrefix("child-uri"));
    }

    @Test
    public void testGetPrefixesOnlyInParent()
    {
        BaseNsContext parent = MergedNsContext.construct(null, ns("only", "parent-only"));
        BaseNsContext child = MergedNsContext.construct(parent, ns("c", "child-uri"));

        Set<String> prefixes = collect(child.getPrefixes("parent-only"));
        assertEquals(Collections.singleton("only"), prefixes);
    }

    @Test
    public void testGetPrefixesUnknown()
    {
        BaseNsContext ctxt = MergedNsContext.construct(null, ns("a", "uri-a"));
        assertFalse(ctxt.getPrefixes("nothing").hasNext());
    }

    // ---------- predefined prefixes handled by BaseNsContext ----------

    @Test
    public void testPredefinedXmlPrefix()
    {
        BaseNsContext ctxt = MergedNsContext.construct(null, ns("a", "uri-a"));
        assertEquals(XMLConstants.XML_NS_URI,
                ctxt.getNamespaceURI(XMLConstants.XML_NS_PREFIX));
        assertEquals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI,
                ctxt.getNamespaceURI(XMLConstants.XMLNS_ATTRIBUTE));

        assertEquals(XMLConstants.XML_NS_PREFIX,
                ctxt.getPrefix(XMLConstants.XML_NS_URI));
        assertEquals(XMLConstants.XMLNS_ATTRIBUTE,
                ctxt.getPrefix(XMLConstants.XMLNS_ATTRIBUTE_NS_URI));
    }

    @Test
    public void testNullPrefixThrows()
    {
        BaseNsContext ctxt = MergedNsContext.construct(null, ns("a", "uri-a"));
        try {
            ctxt.getNamespaceURI(null);
            fail("Expected IllegalArgumentException for null prefix");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testNullOrEmptyUriThrows()
    {
        BaseNsContext ctxt = MergedNsContext.construct(null, ns("a", "uri-a"));
        try {
            ctxt.getPrefix(null);
            fail("Expected IllegalArgumentException for null URI");
        } catch (IllegalArgumentException e) { /* expected */ }
        try {
            ctxt.getPrefix("");
            fail("Expected IllegalArgumentException for empty URI");
        } catch (IllegalArgumentException e) { /* expected */ }
        try {
            ctxt.getPrefixes(null);
            fail("Expected IllegalArgumentException for null URI");
        } catch (IllegalArgumentException e) { /* expected */ }
    }

    // ---------- getNamespaces() ----------

    @Test
    public void testGetNamespacesReturnsLocalOnly()
    {
        BaseNsContext parent = MergedNsContext.construct(null, ns("p", "parent-uri"));
        BaseNsContext child = MergedNsContext.construct(parent, ns("c", "child-uri"));

        Iterator<Namespace> it = child.getNamespaces();
        assertTrue(it.hasNext());
        Namespace ns = it.next();
        assertEquals("c", ns.getPrefix());
        assertEquals("child-uri", ns.getNamespaceURI());
        assertFalse(it.hasNext());
    }

    // ---------- outputNamespaceDeclarations(Writer) ----------

    @Test
    public void testOutputNamespaceDeclarationsToWriter() throws IOException
    {
        // Mix of default namespace and a prefixed one to cover both branches
        List<Namespace> nsList = new ArrayList<>();
        nsList.add(eventFactory.createNamespace("default-uri"));
        nsList.add(eventFactory.createNamespace("p", "prefix-uri"));
        BaseNsContext ctxt = MergedNsContext.construct(null, nsList);

        StringWriter sw = new StringWriter();
        ctxt.outputNamespaceDeclarations(sw);
        String out = sw.toString();

        // Default namespace declaration (no ":prefix" segment)
        assertTrue("expected default xmlns declaration in '" + out + "'",
                out.contains(" xmlns=\"default-uri\""));
        // Prefixed namespace declaration
        assertTrue("expected prefixed xmlns declaration in '" + out + "'",
                out.contains(" xmlns:p=\"prefix-uri\""));
    }

    // ---------- outputNamespaceDeclarations(XMLStreamWriter) ----------

    @Test
    public void testOutputNamespaceDeclarationsToStreamWriter() throws Exception
    {
        List<Namespace> nsList = new ArrayList<>();
        nsList.add(eventFactory.createNamespace("default-uri"));
        nsList.add(eventFactory.createNamespace("p", "prefix-uri"));
        BaseNsContext ctxt = MergedNsContext.construct(null, nsList);

        // Use a recording stub so writeDefaultNamespace/writeNamespace are
        // observed directly without entanglement in real-writer state tracking.
        RecordingStreamWriter rec = new RecordingStreamWriter();
        ctxt.outputNamespaceDeclarations(rec);

        assertEquals(Arrays.asList(
                "default:default-uri",
                "prefixed:p=prefix-uri"), rec.calls);
    }

    // ---------- helpers ----------

    /** Build a Namespace list from prefix/uri pairs. */
    private List<Namespace> ns(String... pairs)
    {
        if ((pairs.length & 1) != 0) {
            throw new IllegalArgumentException("Need even number of args");
        }
        List<Namespace> list = new ArrayList<>(pairs.length / 2);
        for (int i = 0; i < pairs.length; i += 2) {
            list.add(eventFactory.createNamespace(pairs[i], pairs[i + 1]));
        }
        return list;
    }

    private static Set<String> collect(Iterator<String> it)
    {
        Set<String> s = new HashSet<>();
        while (it.hasNext()) {
            s.add(it.next());
        }
        return s;
    }

    /**
     * Minimal {@link XMLStreamWriter} stub that records only the namespace
     * declaration calls exercised by {@link MergedNsContext#outputNamespaceDeclarations(XMLStreamWriter)}.
     */
    static final class RecordingStreamWriter implements XMLStreamWriter
    {
        final List<String> calls = new ArrayList<>();

        @Override public void writeDefaultNamespace(String uri) {
            calls.add("default:" + uri);
        }
        @Override public void writeNamespace(String prefix, String uri) {
            calls.add("prefixed:" + prefix + "=" + uri);
        }

        // ----- everything else is unused by the method under test -----
        @Override public void writeStartElement(String localName) { unsupported(); }
        @Override public void writeStartElement(String nsURI, String localName) { unsupported(); }
        @Override public void writeStartElement(String prefix, String localName, String nsURI) { unsupported(); }
        @Override public void writeEmptyElement(String nsURI, String localName) { unsupported(); }
        @Override public void writeEmptyElement(String prefix, String localName, String nsURI) { unsupported(); }
        @Override public void writeEmptyElement(String localName) { unsupported(); }
        @Override public void writeEndElement() { unsupported(); }
        @Override public void writeEndDocument() { unsupported(); }
        @Override public void close() { /* no-op */ }
        @Override public void flush() { /* no-op */ }
        @Override public void writeAttribute(String localName, String value) { unsupported(); }
        @Override public void writeAttribute(String prefix, String nsURI, String localName, String value) { unsupported(); }
        @Override public void writeAttribute(String nsURI, String localName, String value) { unsupported(); }
        @Override public void writeComment(String data) { unsupported(); }
        @Override public void writeProcessingInstruction(String target) { unsupported(); }
        @Override public void writeProcessingInstruction(String target, String data) { unsupported(); }
        @Override public void writeCData(String data) { unsupported(); }
        @Override public void writeDTD(String dtd) { unsupported(); }
        @Override public void writeEntityRef(String name) { unsupported(); }
        @Override public void writeStartDocument() { unsupported(); }
        @Override public void writeStartDocument(String version) { unsupported(); }
        @Override public void writeStartDocument(String encoding, String version) { unsupported(); }
        @Override public void writeCharacters(String text) { unsupported(); }
        @Override public void writeCharacters(char[] text, int start, int len) { unsupported(); }
        @Override public String getPrefix(String uri) { return null; }
        @Override public void setPrefix(String prefix, String uri) { /* no-op */ }
        @Override public void setDefaultNamespace(String uri) { /* no-op */ }
        @Override public void setNamespaceContext(NamespaceContext ctx) { /* no-op */ }
        @Override public NamespaceContext getNamespaceContext() { return null; }
        @Override public Object getProperty(String name) { return null; }

        private void unsupported() {
            throw new UnsupportedOperationException("Not used by test");
        }
    }
}
