package wstxtest.evt;

import java.io.StringWriter;
import java.net.URL;
import java.util.*;

import javax.xml.stream.*;
import javax.xml.stream.events.DTD;
import javax.xml.stream.events.XMLEvent;

import org.codehaus.stax2.XMLEventReader2;
import org.codehaus.stax2.evt.NotationDeclaration2;

import com.ctc.wstx.api.WstxInputProperties;
import com.ctc.wstx.exc.*;
import org.junit.jupiter.api.Test;

/**
 * Set of unit tests that verify that Woodstox implementation of
 * {@link XMLEventReader} does obey additional constraints Woodstox
 * guarantees. Specifically:
 *<ul>
 * <li>Event readers never read things in lazy manner: even if lazy parsing
 *   is enabled. (this restriction is added since lazy parsing does not
 *   significantly benefit Event API since there's no way to skip events,
 *   but it creates class of non-checked exceptions used to wrap real
 *   stream exceptions)
 *  </li>
 * <li>Unless coalesce is explicitly set to false, event readers always read
 *   the full text segment, instead of returning fragments (ie. min. segment
 *   length will be replace with MAX_INT). This is done for more convenient
 *   access, as well as since the overhead of multiple Event objects may
 *   outweigh potential benefits from returning shorter segments.
 *  </li>
 *</ul>
 */

public class TestEventReader
    extends wstxtest.BaseWstxTest
{
    @Test
    public void testEventReaderNonLaziness()
        throws XMLStreamException
    {
        /* We can test this by forcing coalescing to happen, and injecting
         * an intentional error after first two segments. In lazy mode,
         * coalescing is done not when event type is fetched, but only
         * when getText() is called. In non-lazy mode, it's thrown right
         * from next() method. Although the exact mechanism is hidden by
         * the Event API, what we do see is the type of exception we get --
         * that should be XMLStreamException, NOT a runtime wrapper instead
         * of it.
         */
        final String XML =
            "<root>Some text and &amp; <![CDATA[also cdata]]> &error;</root>"
            ;
        XMLEventReader er = getReader(XML, true);
        XMLEvent evt = er.nextEvent(); // start document
        assertTrue(evt.isStartDocument());
        assertTrue(er.nextEvent().isStartElement());

        // Ok, and now...
        try {
            evt = er.nextEvent();
            // should NOT get this far...
            fail("Expected an exception for invalid content: coalescing not workig?");
        } catch (WstxParsingException wex) {
            // This is correct... parsing exc for entity, hopefully
            //System.err.println("GOOD: got "+wex.getClass()+": "+wex);
        } catch (WstxException wex2) {
            // Unexpected... not a catastrophe, but not right
            fail("Should have gotten a non-lazy parsing exception; got non-lazy other wstx exception (why?): "+wex2);
        } catch (WstxLazyException lex) {
            // Not good...
            fail("Should not get a lazy exception via (default) event reader; received: "+lex);
        } catch (Throwable t) {
            fail("Unexpected excpetion caught: "+t);
        }
    }

    @Test
    public void testEventReaderLongSegments()
        throws XMLStreamException
    {
        /* Ok. And here we should just check that we do not get 2 adjacent
         * separate Characters event. We can try to trigger this by long
         * segment and a set of char entities...
         */
        final String XML =
            "<root>Some text and &amp; also &quot;quoted&quot; stuff..."
            +" not sure If we\r\nreally need anything much more but"
            +" let's still make this longer"
            +"</root>";
        
        // Single text event expected (default value, explicit coalescing=true):

        String message = "Even in the absence of coalescing, event reader should not split CHARACTERS segments (Woodstox guarantee): did get 2 separate Characters events.";
        // the default behaviour for event readers is to not break text segments into multiple events
        assertEquals(message, 1, numTextEvents(getReader(XML, null)));
        // if coalescing is set to true event readers do not break text segments into multiple events
        assertEquals(message, 1, numTextEvents(getReader(XML, true)));

        // Multiple text events expected (explicit coalescing=false):

        // if coalescing is explicitly set to false, multiple text events may be returned for a text segment
        String messageMultiple = "If coalescing is set to false, multiple text events are expected for this input xml.";
        assertTrue(messageMultiple, numTextEvents(getReader(XML, false)) > 1);
    }

    /**
     * As of Stax 3.0 (Woodstox 4.0+), there is additional info for
     * NotationDeclarations (base URI). Let's verify it gets properly
     * populated.
     */
    @Test
    public void testDtdNotations()
        throws Exception
    {
        final String URI = "http://test";

        /* Ok. And here we should just check that we do not get 2 adjacent
         * separate Characters event. We can try to trigger this by long
         * segment and a set of char entities...
         */
        final String XML = "<?xml version='1.0'?>"
            +"<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!NOTATION not PUBLIC 'some-public-id'>\n"
            +"]>"
            +"<root/>";
	
        // Need to disable coalescing though for test to work:
        XMLEventReader2 er = getReader(XML, false);
        // Need to set Base URI; can do it for factory or instance
        er.setProperty(WstxInputProperties.P_BASE_URL, new URL(URI));
        assertTrue(er.nextEvent().isStartDocument());
        XMLEvent evt = er.nextEvent(); // DTD
        assertTokenType(DTD, evt.getEventType());

        DTD dtd = (DTD) evt;
        List<?> nots = dtd.getNotations();
        assertEquals(1, nots.size());
        NotationDeclaration2 notDecl = (NotationDeclaration2) nots.get(0);

        assertEquals(URI, notDecl.getBaseURI());
    }

    /**
     * Namespace URI is an attribute value in the document, and may contain
     * characters that have to be escaped when the start element is written
     * out with {@link XMLEvent#writeAsEncodedUnicode}: if not, a quote ends
     * the value early and rest of the URI is taken as markup. See
     * [woodstox-core#353].
     */
    @Test
    public void testNsDeclEscapingOnWrite()
        throws Exception
    {
        final String XML = "<root xmlns='urn:d&quot;>&lt;injected/>&lt;x y=&quot;'"
            +" xmlns:p='urn:x&quot; role=&quot;admin'"
            +" xmlns:q='http://example.com/ns?a=1&amp;b=2' />";
        final String OUTPUT = writeStartElement(XML);

        assertEquals("<root xmlns=\"urn:d&quot;>&lt;injected/>&lt;x y=&quot;\""
                +" xmlns:p=\"urn:x&quot; role=&quot;admin\""
                +" xmlns:q=\"http://example.com/ns?a=1&amp;b=2\">", OUTPUT);

        // and what was written has to read back as the same declarations
        XMLStreamReader sr = constructNsStreamReader(OUTPUT+"</root>", true);
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        assertEquals(0, sr.getAttributeCount());
        assertEquals(3, sr.getNamespaceCount());
        assertEquals("urn:d\"><injected/><x y=\"", sr.getNamespaceURI(0));
        assertEquals("urn:x\" role=\"admin", sr.getNamespaceURI(1));
        assertEquals("http://example.com/ns?a=1&b=2", sr.getNamespaceURI(2));
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();
    }

    /**
     * Default namespace unbound by a DTD attribute default has no URI
     * (null); it should be written as an empty value.
     */
    @Test
    public void testUnboundNsDeclOnWrite()
        throws Exception
    {
        final String XML = "<!DOCTYPE root [\n"
            +"<!ATTLIST root xmlns CDATA ''>\n"
            +"]>"
            +"<root/>";
        assertEquals("<root xmlns=\"\">", writeStartElement(XML));
    }

    /*
    //////////////////////////////////////////////////////
    // Internal methods
    //////////////////////////////////////////////////////
    */
    private XMLEventReader2 getReader(String contents, Boolean coalescing)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setNamespaceAware(f, true);
        if (coalescing != null) {
            setCoalescing(f, coalescing);
        }
        setLazyParsing(f, true); // shouldn't have effect for event readers!
        setMinTextSegment(f, 8); // likewise
        return constructEventReader(f, contents);
    }

    /**
     * Helper method that writes the first start element of given document
     * using {@link XMLEvent#writeAsEncodedUnicode}.
     */
    private String writeStartElement(String contents)
        throws XMLStreamException
    {
        XMLEventReader2 er = getReader(contents, null);
        XMLEvent evt = er.nextEvent();
        while (!evt.isStartElement()) {
            evt = er.nextEvent();
        }
        StringWriter sw = new StringWriter();
        evt.writeAsEncodedUnicode(sw);
        er.close();
        return sw.toString();
    }

    private int numTextEvents(XMLEventReader er) throws XMLStreamException {
        int numTextEvents = 0;
        while (er.hasNext()) {
            if (er.nextEvent().isCharacters()) {
                numTextEvents++;
            }
        }
        return numTextEvents;
    }
}


