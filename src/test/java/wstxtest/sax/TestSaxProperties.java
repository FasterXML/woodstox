package wstxtest.sax;

import java.io.StringReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import com.ctc.wstx.sax.WstxSAXParserFactory;

import wstxtest.BaseWstxTest;
import wstxtest.sax.TestBasicSax.MyHandler;
import org.junit.jupiter.api.Test;

public class TestSaxProperties extends BaseWstxTest
{
    private final static String EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";

    private final static String EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";

    /**
     * Document that pulls in an external parameter entity from the internal DTD
     * subset. The referenced file really does exist and declares a defaulted
     * attribute, so that:
     *<ul>
     * <li>a successful parse is observable ({@code MyHandler._attrs} becomes 1
     *   only if the external subset was actually read), and
     *  </li>
     * <li>a failed parse can only be due to the feature being disabled, not due
     *   to the system id being unresolvable
     *  </li>
     *</ul>
     */
    private static String docWithExternalPE() {
        String systemId = TestSaxProperties.class.getResource("external-pe.dtd").toString();
        return "<!DOCTYPE root [ <!ENTITY % pe SYSTEM \""+systemId+"\"> %pe; ]><root />";
    }

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

    // Positive control for the two tests below: with the feature left at its
    // default, the external parameter entity really is resolved and the
    // declarations it contains take effect
    @Test
    public void testExternalParameterEntitiesEnabled() throws Exception
    {
        WstxSAXParserFactory f = new WstxSAXParserFactory();

        // enabled by default, like external general entities
        assertTrue(f.getFeature(EXTERNAL_PARAMETER_ENTITIES));
        assertTrue(f.getFeature(EXTERNAL_GENERAL_ENTITIES));

        MyHandler h = new MyHandler();
        f.newSAXParser().parse(new InputSource(new StringReader(docWithExternalPE())), h);

        assertEquals("Should have parsed the root element", 1, h._elems);
        // and the defaulted attribute only exists if the external subset was read
        assertEquals("Should have defaulted attribute from external parameter entity",
                1, h._attrs);
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
            f.newSAXParser().parse(new InputSource(new StringReader(docWithExternalPE())),
                    new MyHandler());
            fail("Should not resolve external parameter entity");
        } catch (SAXException e) {
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
            r.parse(new InputSource(new StringReader(docWithExternalPE())));
            fail("Should not resolve external parameter entity");
        } catch (SAXException e) {
            verifyException(e, "isSupportingExternalEntities");
        }
    }
}
