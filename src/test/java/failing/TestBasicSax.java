package failing;

import java.io.*;

import javax.xml.parsers.SAXParser;

import org.xml.sax.*;
import org.xml.sax.ext.DefaultHandler2;

import com.ctc.wstx.sax.*;

import wstxtest.BaseWstxTest;

/**
 * Simple unit tests to verify that most fundamental parsing functionality
 * works via Woodstox SAX implementation.
 */
public class TestBasicSax
    extends BaseWstxTest
{
    /**
     * Test for [WSTX_227]
     */
    public void testCData() throws Exception
    {
        SAXParser parser = new WstxSAXParser();
        StringBuffer buffer = new StringBuffer("<root><![CDATA[");
        for (int i=0; i<100000; i++) {
            buffer.append('a');
        }
        buffer.append("]]></root>");
        CDATASectionCounter handler = new CDATASectionCounter();
        parser.setProperty("http://xml.org/sax/properties/lexical-handler", handler);
        parser.parse(new InputSource(new StringReader(buffer.toString())), handler);
        // Should get as many cdata sections as text segments
        int cdatas = handler.getCDATASectionCount();
        int segments = handler.getSegmentCount();

        assertEquals("Should only get a single CDATA segments, got "+cdatas+" (for "+segments+" text segments)", 1, cdatas);
    }

    /*
    ////////////////////////////////////////////////////
    // Helper methods
    ////////////////////////////////////////////////////
     */

    private static class CDATASectionCounter extends DefaultHandler2 {
        private int cdataSectionCount;
        private int segmentCount;
        
        public void startCDATA() throws SAXException {
            cdataSectionCount++;
        }
        
        public void characters(char[] ch, int start, int length) throws SAXException {
            segmentCount++;
        }
        
        public int getCDATASectionCount() {
            return cdataSectionCount;
        }
        
        public int getSegmentCount() {
            return segmentCount;
        }
    }
}
