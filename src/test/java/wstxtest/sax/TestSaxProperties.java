package wstxtest.sax;

import java.io.ByteArrayInputStream;
import java.io.StringReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;

import com.ctc.wstx.sax.WstxSAXParserFactory;

import wstxtest.BaseWstxTest;
import wstxtest.sax.TestBasicSax.MyHandler;

public class TestSaxProperties extends BaseWstxTest
{
    // [woodstox-core#77]: Don't barf of "secure processing" setting
    public void testSecureProcessingFactory() throws Exception
    {
        WstxSAXParserFactory f = new WstxSAXParserFactory();        

        // default setting is `false`
        assertFalse(f.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));

        // but may change
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        assertTrue(f.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));

        // as well as revert
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
        assertFalse(f.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    public void testSecureProcessingReader() throws Exception
    {
        WstxSAXParserFactory f = new WstxSAXParserFactory();        
        SAXParser sp = f.newSAXParser();
        /*
        assertFalse(sp.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
                MyHandler h = new MyHandler();

                InputSource src;

                if (useReader) {
                    src = new InputSource(new StringReader(XML));
                } else {
                    src = new InputSource(new ByteArrayInputStream(XML.getBytes("UTF-8")));
                }

                sp.parse(src, h);

*/
    }
}
