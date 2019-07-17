package wstxtest.sax;

import java.io.StringReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;

import org.xml.sax.InputSource;

import com.ctc.wstx.sax.WstxSAXParserFactory;

import wstxtest.BaseWstxTest;
import wstxtest.sax.TestBasicSax.MyHandler;

public class TestSaxProperties extends BaseWstxTest
{
    // [woodstox-core#77]: Don't barf on "secure processing" setting
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
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        SAXParser sp = f.newSAXParser();

        // 13-Jul-2019, tatu: as far as I can see, there is no way to set or get
        //    feature setting via parser instance (only "properties", not "features",
        //    accessible). So... can't verify or change
//        assertNull(sp.getProperty(XMLConstants.FEATURE_SECURE_PROCESSING));


        // so let's simply check that basic parsing still works:
        MyHandler h = new MyHandler();
        InputSource src = new InputSource(new StringReader("<root></root>"));
        sp.parse(src, h);
    }
}
