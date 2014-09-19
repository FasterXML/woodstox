package stax2.stream;

import javax.xml.stream.*;

import org.codehaus.stax2.*;

import stax2.BaseStax2Test;

/**
 * Set of unit tests that checks that configuring of
 * {@link XMLInputFactory2} works ok.
 */
public class TestConfig
    extends BaseStax2Test
{
    public void testForXmlConformanceProfile()
        throws XMLStreamException
    {
        // configureForXmlConformance
        XMLInputFactory2 ifact = getNewInputFactory();
        ifact.configureForXmlConformance();
        assertEquals(Boolean.TRUE, ifact.getProperty(XMLInputFactory.SUPPORT_DTD));
        assertEquals(Boolean.TRUE, ifact.getProperty(XMLInputFactory.IS_NAMESPACE_AWARE));
        assertEquals(Boolean.TRUE, ifact.getProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES));
        assertEquals(Boolean.TRUE, ifact.getProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES));
    }

    public void testForConvenienceProfile()
        throws XMLStreamException
    {
        // configureForConvenience
        XMLInputFactory2 ifact = getNewInputFactory();
        ifact.configureForConvenience();
        assertEquals(Boolean.TRUE, ifact.getProperty(XMLInputFactory.IS_COALESCING));
        assertEquals(Boolean.TRUE, ifact.getProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES));
        assertEquals(Boolean.FALSE, ifact.getProperty(XMLInputFactory2.P_REPORT_PROLOG_WHITESPACE));
        assertEquals(Boolean.FALSE, ifact.getProperty(XMLInputFactory2.P_REPORT_CDATA));
        assertEquals(Boolean.TRUE, ifact.getProperty(XMLInputFactory2.P_PRESERVE_LOCATION));
    }

    public void testForSpeedProfile()
        throws XMLStreamException
    {
        // configureForSpeed
        XMLInputFactory2 ifact = getNewInputFactory();
        ifact.configureForSpeed();
        assertEquals(Boolean.FALSE, ifact.getProperty(XMLInputFactory.IS_COALESCING));
        assertEquals(Boolean.FALSE, ifact.getProperty(XMLInputFactory2.P_PRESERVE_LOCATION));
        assertEquals(Boolean.FALSE, ifact.getProperty(XMLInputFactory2.P_REPORT_PROLOG_WHITESPACE));
        assertEquals(Boolean.TRUE, ifact.getProperty(XMLInputFactory2.P_INTERN_NAMES));
        assertEquals(Boolean.TRUE, ifact.getProperty(XMLInputFactory2.P_INTERN_NS_URIS));
    }

    public void testForLowMemProfile()
        throws XMLStreamException
    {
        // configureForLowMemUsage
        XMLInputFactory2 ifact = getNewInputFactory();
        ifact.configureForLowMemUsage();
        assertEquals(Boolean.FALSE, ifact.getProperty(XMLInputFactory.IS_COALESCING));
        assertEquals(Boolean.FALSE, ifact.getProperty(XMLInputFactory2.P_PRESERVE_LOCATION));
    }

    public void testForRoundTrippingProfile()
        throws XMLStreamException
    {
        // configureForRoundTripping
        XMLInputFactory2 ifact = getNewInputFactory();
        ifact.configureForRoundTripping();
        assertEquals(Boolean.FALSE, ifact.getProperty(XMLInputFactory.IS_COALESCING));
        assertEquals(Boolean.FALSE, ifact.getProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES));
        assertEquals(Boolean.TRUE, ifact.getProperty(XMLInputFactory2.P_REPORT_CDATA));
        assertEquals(Boolean.TRUE, ifact.getProperty(XMLInputFactory2.P_REPORT_PROLOG_WHITESPACE));
    }
}
