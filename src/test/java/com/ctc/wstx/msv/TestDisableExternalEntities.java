package com.ctc.wstx.msv;

import javax.xml.parsers.SAXParserFactory;

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
