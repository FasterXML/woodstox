package com.ctc.wstx.msv;

import java.io.StringReader;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.xml.sax.SAXNotRecognizedException;


import com.ctc.wstx.api.WstxInputProperties;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Unit tests for {@link BaseSchemaFactory#disableExternalEntities}: failure to
 * disable external entities must not be silently ignored.
 */
class TestDisableExternalEntities extends wstxtest.BaseJUnit4Test
{
    private final static String FEATURE_GENERAL = "http://xml.org/sax/features/external-general-entities";
    private final static String FEATURE_PARAM = "http://xml.org/sax/features/external-parameter-entities";
    private final static String FEATURE_LOAD_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    @Test
    void testFailsIfGeneralEntitiesNotSupported() throws Exception
    {
        _verifyFailsFor(FEATURE_GENERAL);
    }

    @Test
    void testFailsIfParameterEntitiesNotSupported() throws Exception
    {
        _verifyFailsFor(FEATURE_PARAM);
    }

    // Xerces-specific feature, so lack of support is fine
    @Test
    void testLoadExternalDtdOptional() throws Exception
    {
        SAXParserFactory f = Mockito.mock(SAXParserFactory.class);
        Mockito.doThrow(new SAXNotRecognizedException(FEATURE_LOAD_DTD))
            .when(f).setFeature(eq(FEATURE_LOAD_DTD), anyBoolean());
        BaseSchemaFactory.disableExternalEntities(f);
        Mockito.verify(f).setFeature(FEATURE_GENERAL, false);
        Mockito.verify(f).setFeature(FEATURE_PARAM, false);
    }

    // Legacy no-arg accessor must return the secure factory
    @SuppressWarnings("deprecation")
    @Test
    void testLegacyGetSaxFactory()
    {
        assertSame(BaseSchemaFactory.getSaxFactory(false), BaseSchemaFactory.getSaxFactory());
    }

    // Schema loading must report the failure as declared XMLStreamException,
    // not as unchecked exception
    @Test
    void testSchemaLoadingReportsStreamException() throws Exception
    {
        final String PROP = "javax.xml.parsers.SAXParserFactory";
        final String oldProp = System.getProperty(PROP);
        final SAXParserFactory oldFactory;
        synchronized (BaseSchemaFactory.class) {
            oldFactory = BaseSchemaFactory.sSaxFactory;
            BaseSchemaFactory.sSaxFactory = null;
        }
        System.setProperty(PROP, RejectingSAXParserFactory.class.getName());
        try {
            final String SCHEMA = "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'/>";
            try {
                new W3CSchemaFactory().createSchema(new StringReader(SCHEMA));
                fail("Should have failed for W3CSchemaFactory");
            } catch (XMLStreamException e) {
                assertTrue(e.getMessage(), e.getMessage().contains(FEATURE_GENERAL));
            }
            try {
                new W3CMultiSchemaFactory().createSchema(null, java.util.Collections.emptyMap());
                fail("Should have failed for W3CMultiSchemaFactory");
            } catch (XMLStreamException e) {
                assertTrue(e.getMessage(), e.getMessage().contains(FEATURE_GENERAL));
            }
        } finally {
            if (oldProp == null) {
                System.clearProperty(PROP);
            } else {
                System.setProperty(PROP, oldProp);
            }
            synchronized (BaseSchemaFactory.class) {
                BaseSchemaFactory.sSaxFactory = oldFactory;
            }
        }
    }

    /**
     * SAX parser factory that recognizes no features; must be public for
     * {@link SAXParserFactory#newInstance()} to instantiate it.
     */
    public static class RejectingSAXParserFactory extends SAXParserFactory
    {
        @Override
        public SAXParser newSAXParser() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setFeature(String name, boolean value) throws SAXNotRecognizedException {
            throw new SAXNotRecognizedException(name);
        }

        @Override
        public boolean getFeature(String name) throws SAXNotRecognizedException {
            throw new SAXNotRecognizedException(name);
        }
    }

    private void _verifyFailsFor(String feature) throws Exception
    {
        SAXParserFactory f = Mockito.mock(SAXParserFactory.class);
        Mockito.doNothing().when(f).setFeature(anyString(), anyBoolean());
        Mockito.doThrow(new SAXNotRecognizedException(feature))
            .when(f).setFeature(eq(feature), anyBoolean());
        try {
            BaseSchemaFactory.disableExternalEntities(f);
            fail("Should have failed when '"+feature+"' not supported");
        } catch (IllegalStateException e) {
            String msg = e.getMessage();
            assertTrue("Unexpected message: "+msg, msg.contains(feature));
            assertTrue("Unexpected message: "+msg,
                    msg.contains(WstxInputProperties.P_MSV_SCHEMA_EXTERNAL_ACCESS));
        }
    }
}
