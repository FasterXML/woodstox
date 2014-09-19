package wstxtest.stream;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.stax2.XMLStreamReader2;

import com.ctc.wstx.stax.WstxInputFactory;

/**
 * This test verifies that the "fully streaming" text access method(s)
 * do not return partial text/CDATA segments no matter what the mode
 * is.
 *<p>
 * Note that although this test should really be part of StAX2 test
 * suite, currently there is no standard way to define properties that
 * would make it more likely that the parser may return partial
 * text segments; but Woodstox does. So, for now we can at least
 * test that Woodstox is conformant... ;-)
 */
public class TestStreaming
    extends BaseStreamTest
{
    public void testTextStreaming()
        throws IOException, XMLStreamException
    {
        String CONTENT_IN =
            "Some content\nthat will be "
            +"&quot;streamed&quot; &amp; sliced"
            +" and\nprocessed...";
            ;
        String CONTENT_OUT = 
            "Some content\nthat will be "
            +"\"streamed\" & sliced"
            +" and\nprocessed...";
            ;
        /* Let's also add trailing CDATA, to ensure no coalescing is done
         * when not requested
         */
        String XML = "<root>" + CONTENT_IN + "<![CDATA[cdata]]></root>";
        XMLStreamReader2 sr = getReader(XML, false);
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CHARACTERS, sr.next());
        StringWriter sw = new StringWriter();
        sr.getText(sw, false);
        String act = sw.toString();
        if (!act.equals(CONTENT_OUT)) {
            if (CONTENT_OUT.startsWith(act)) {
                fail("Streaming text accessors returned partial match; first "
                     +act.length()+" chars of the expected "
                     +CONTENT_OUT.length()+" chars");
            }
            fail("Content accessed using streaming text accessor (len "
                     +act.length()+"; exp "+CONTENT_OUT.length()+" chars) wrong: "
                 +"expected ["+CONTENT_OUT+"], got ["+act+"]");
        }

        // And should get the following CDATA, then:
        assertTokenType(CDATA, sr.next());
        // and then closing element; let's not check CDATA contents here
        assertTokenType(END_ELEMENT, sr.next());
    }

    public void testCDataStreaming()
        throws IOException, XMLStreamException
    {
        String CONTENT_INOUT =
            "Some content\nthat will be stored in a\n"
            +"CDATA <yes!> Block <[*]>\n"
            +" yet not be split in any way...."
            ;
        /* Let's also add trailing text, to ensure no coalescing is done
         * when not requested
         */
        String XML = "<root><![CDATA[" + CONTENT_INOUT + "]]>some text!</root>";
        XMLStreamReader2 sr = getReader(XML, false);
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(CDATA, sr.next());
        StringWriter sw = new StringWriter();
        sr.getText(sw, false);
        String act = sw.toString();
        if (!act.equals(CONTENT_INOUT)) {
            if (CONTENT_INOUT.startsWith(act)) {
                fail("Streaming text accessors returned partial match; first "
                     +act.length()+" chars of the expected "
                     +CONTENT_INOUT.length()+" chars");
            }
            fail("Content accessed using streaming text accessor (len "
                     +act.length()+"; exp "+CONTENT_INOUT.length()+" chars) wrong: "
                 +"expected ["+CONTENT_INOUT+"], got ["+act+"]");
        }

        // And should get the following CHARACTERS then:
        assertTokenType(CHARACTERS, sr.next());
        // and then closing element; let's not check text contents here
        assertTokenType(END_ELEMENT, sr.next());
    }

    /**
     * Let's also ensure that coalescing still works ok with streaming
     * as well...
     */
    public void testCoalescingStreaming()
        throws IOException, XMLStreamException
    {
        String CONTENT_IN1 =
            "First text\n<![CDATA[ and ]]> cdata <![CDATA[]]><![CDATA[...]]>"
            ;
        String CONTENT_OUT1 = "First text\n and  cdata ...";
        String CONTENT_IN2 =
            "<![CDATA[Then CDATA]]> and text<![CDATA[...\n]]>neat-o<![CDATA[]]>!";
        ;
        String CONTENT_OUT2 = "Then CDATA and text...\nneat-o!";

        for (int i = 0; i < 2; ++i) {
            boolean first = (i == 0);
            String XML = "<root>" + (first ? CONTENT_IN1 : CONTENT_IN2) + "</root>";
            XMLStreamReader2 sr = getReader(XML, true);
            assertTokenType(START_ELEMENT, sr.next());
            assertTokenType(CHARACTERS, sr.next());
            StringWriter sw = new StringWriter();
            sr.getText(sw, false);
            String act = sw.toString();
            String exp = first ? CONTENT_OUT1 : CONTENT_OUT2;
            if (!act.equals(exp)) {
                if (exp.startsWith(act)) {
                    fail("Streaming text accessors returned partial match; first "
                         +act.length()+" chars of the expected "
                         +exp.length()+" chars");
                }
                fail("Content accessed using streaming text accessor (len "
                     +act.length()+"; exp "+exp.length()+" chars) wrong: "
                     +"expected ["+exp+"], got ["+act+"]");
            }
            // and then closing element
            assertTokenType(END_ELEMENT, sr.next());
        }
    }

    /*
    //////////////////////////////////////////////////////
    // Internal methods
    //////////////////////////////////////////////////////
     */

    private XMLStreamReader2 getReader(String contents, boolean coalesce)
        throws XMLStreamException
    {
        WstxInputFactory f = getWstxInputFactory();
        f.getConfig().doSupportNamespaces(true);
        f.getConfig().doCoalesceText(coalesce);
        f.getConfig().setInputBufferLength(16);
        f.getConfig().setShortestReportedTextSegment(4);
        return constructStreamReader(f, contents);
    }

}
