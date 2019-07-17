package org.codehaus.stax.test.stream;

import javax.xml.XMLConstants;
import javax.xml.stream.*;

/**
 * Unit tests that verify handling of XMLInputFactory properties.
 * This includes:
 *<ul>
 * <li>Property defaults as defined by Stax specs (see class javadocs for
 * {@link javax.xml.stream.XMLInputFactory}
 *  </li>
 *</ul>
 *
 * @author Tatu Saloranta
 */
public class TestFactoryProperties
    extends BaseStreamTest
{
    private final XMLInputFactory DEFAULT_FACTORY = getNewInputFactory();

    public void testDefaultEntitySettings()
    {
        assertEquals(Boolean.TRUE, DEFAULT_FACTORY.getProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES));
        Object o = DEFAULT_FACTORY.getProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES);
        if (!(o instanceof Boolean)) {
            fail("Property value for XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES not of type Boolean, but "+((o == null) ? "[null]" : o.getClass().getName()));
        }
    }

    public void testDefaultValidationSettings()
    {
        assertEquals(Boolean.FALSE, DEFAULT_FACTORY.getProperty(XMLInputFactory.IS_VALIDATING));
        // A few impls might not support this, but it is the default...
        assertEquals(Boolean.TRUE, DEFAULT_FACTORY.getProperty(XMLInputFactory.SUPPORT_DTD));
    }

    public void testDefaultMiscSettings()
    {
        assertEquals(Boolean.TRUE, DEFAULT_FACTORY.getProperty(XMLInputFactory.IS_NAMESPACE_AWARE));
        assertEquals(Boolean.FALSE, DEFAULT_FACTORY.getProperty(XMLInputFactory.IS_COALESCING));
        // Shouldn't have default handler objects either
        assertNull(DEFAULT_FACTORY.getProperty(XMLInputFactory.REPORTER));
        assertNull(DEFAULT_FACTORY.getProperty(XMLInputFactory.RESOLVER));
        assertNull(DEFAULT_FACTORY.getProperty(XMLInputFactory.ALLOCATOR));
    }

    // JEP-185, add nominal support for "secure processing"
    public void testFeatureSecureProcessing()
    {
        // should probably return `null` for "indeterminate" but...
        assertEquals(Boolean.FALSE,
                DEFAULT_FACTORY.getProperty(XMLConstants.FEATURE_SECURE_PROCESSING));

        // but also verify that it can be enabled:
        XMLInputFactory f = getNewInputFactory();
        f.setProperty(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
        assertEquals(Boolean.TRUE,
                f.getProperty(XMLConstants.FEATURE_SECURE_PROCESSING));
    }
}

