package wstxtest.stream;

import java.io.*;

import javax.xml.stream.*;

import com.ctc.wstx.stax.WstxInputFactory;

import wstxtest.cfg.*;

/**
 * Set of unit tests that check how Woodstox handles white space in
 * prolog and/or epilog.
 */
import com.ctc.wstx.api.ReaderConfig;

public class TestPrologWS
    extends BaseStreamTest
{
    final static String XML1 = "<?xml version='1.0'?>   <root />\n";
    final static String XML2 = "\n \n<root />   ";

    public void testReportPrologWS()
        throws IOException, XMLStreamException
    {
        for (int i = 0; i < 8; ++i) {
            boolean lazy = (i & 1) == 0;
            boolean firstDoc = (i & 2) == 0;
            String content = firstDoc ? XML1 : XML2;
            boolean streaming = (i & 4) != 0;
            XMLStreamReader sr = getReader(content, true, lazy);

            assertTokenType(START_DOCUMENT, sr.getEventType());

            assertTokenType(SPACE, sr.next());
            String text = streaming ? getStreamingText(sr):getAndVerifyText(sr);
            if (firstDoc) {
                assertEquals("   ", text);
            } else {
                assertEquals("\n \n", text);
            }

            assertTokenType(START_ELEMENT, sr.next());
            assertTokenType(END_ELEMENT, sr.next());

            assertTokenType(SPACE, sr.next());

            text = streaming ? getStreamingText(sr):getAndVerifyText(sr);
            if (firstDoc) {
                assertEquals("\n", text);
            } else {
                assertEquals("   ", text);
            }

            assertTokenType(END_DOCUMENT, sr.next());
        }
    }

    public void testIgnorePrologWS()
        throws XMLStreamException
    {
        for (int i = 0; i < 4; ++i) {
            boolean lazy = (i & 1) == 0;
            String content = ((i & 2) == 0) ? XML1 : XML2;
            XMLStreamReader sr = getReader(content, false, lazy);

            assertTokenType(START_DOCUMENT, sr.getEventType());

            assertTokenType(START_ELEMENT, sr.next());
            assertEquals("root", sr.getLocalName());
            assertTokenType(END_ELEMENT, sr.next());

            assertTokenType(END_DOCUMENT, sr.next());
        }
    }

    /*
    //////////////////////////////////////////////////////
    // Internal methods
    //////////////////////////////////////////////////////
     */

    /**
     * Method called via input config iterator, with all possible
     * configurations
     */
    public void runTest(XMLInputFactory f, InputConfigIterator it)
        throws Exception
    {
        String XML = "<root>"
            +"<!-- first comment -->\n"
            +"  <!-- - - - - -->"
            +"<!-- Longer comment that contains quite a bit of content\n"
            +" so that we can check boundary - conditions too... -->"
            +"<!----><!-- and entities: &amp; &#12;&#x1d; -->\n"
            +"</root>";
        XMLStreamReader sr = constructStreamReader(f, XML);

        streamAndCheck(sr, it, XML, XML, false);
        // Let's also try 'real' streaming...
        streamAndCheck(sr, it, XML, XML, true);
    }

    /*
    ////////////////////////////////////////
    // Private methods, other
    ////////////////////////////////////////
     */

    private XMLStreamReader getReader(String contents, boolean prologWS,
                                      boolean lazyParsing)
        throws XMLStreamException
    {
        WstxInputFactory f = (WstxInputFactory) getInputFactory();
        ReaderConfig cfg = f.getConfig();
        cfg.doReportPrologWhitespace(prologWS);
        cfg.doParseLazily(lazyParsing);
        return constructStreamReader(f, contents);
    }
}

