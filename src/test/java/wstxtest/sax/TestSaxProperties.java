package wstxtest.sax;

import java.io.StringReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;

import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import com.ctc.wstx.sax.WstxSAXParserFactory;

import wstxtest.BaseWstxTest;
import wstxtest.sax.TestBasicSax.MyHandler;
import org.junit.jupiter.api.Test;

public class TestSaxProperties extends BaseWstxTest
{
    private final static String EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";

    private final static String EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";

    // Document that refers to an external parameter entity from the internal
    // DTD subset; system id points to a file that does not exist, so an attempt
    // to actually resolve it fails with a distinctive error
    private final static String DOC_WITH_EXTERNAL_PE =
        "<!DOCTYPE root [ <!ENTITY % pe SYSTEM \"file:///no-such-file-woodstox-test.dtd\"> %pe; ]><root />";

    // [woodstox-core#77]: Don't barf on "secure processing" setting
    @Test
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

    @Test
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

    // Same feature is settable via the XMLReader the parser exposes
    @Test
    public void testSecureProcessingXmlReader() throws Exception
    {
        XMLReader r = new WstxSAXParserFactory().newSAXParser().getXMLReader();

        assertFalse(r.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        r.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        assertTrue(r.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
        r.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
        assertFalse(r.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    public void testExternalParameterEntitiesFactory() throws Exception
    {
        WstxSAXParserFactory f = new WstxSAXParserFactory();

        // enabled by default, like external general entities
        assertTrue(f.getFeature(EXTERNAL_PARAMETER_ENTITIES));

        f.setFeature(EXTERNAL_PARAMETER_ENTITIES, false);
        assertFalse(f.getFeature(EXTERNAL_PARAMETER_ENTITIES));
        assertFalse(f.getFeature(EXTERNAL_GENERAL_ENTITIES));

        try {
            f.newSAXParser().parse(new InputSource(new StringReader(DOC_WITH_EXTERNAL_PE)),
                    new MyHandler());
            fail("Should not resolve external parameter entity");
        } catch (Exception e) {
            verifyException(e, "isSupportingExternalEntities");
        }
    }

    @Test
    public void testExternalParameterEntitiesXmlReader() throws Exception
    {
        XMLReader r = new WstxSAXParserFactory().newSAXParser().getXMLReader();

        assertTrue(r.getFeature(EXTERNAL_PARAMETER_ENTITIES));

        r.setFeature(EXTERNAL_PARAMETER_ENTITIES, false);
        assertFalse(r.getFeature(EXTERNAL_PARAMETER_ENTITIES));
        assertFalse(r.getFeature(EXTERNAL_GENERAL_ENTITIES));

        r.setContentHandler(new MyHandler());
        try {
            r.parse(new InputSource(new StringReader(DOC_WITH_EXTERNAL_PE)));
            fail("Should not resolve external parameter entity");
        } catch (Exception e) {
            verifyException(e, "isSupportingExternalEntities");
        }
    }
}
