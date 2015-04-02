package org.codehaus.stax.test.stream;

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
public class TestProperties
    extends BaseStreamTest
{
    public void testDefaultEntitySettings()
    {
        XMLInputFactory f = getNewInputFactory();
        assertEquals(Boolean.TRUE, f.getProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES));
        Object o = f.getProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES);
        if (!(o instanceof Boolean)) {
            fail("Property value for XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES not of type Boolean, but "+((o == null) ? "[null]" : o.getClass().getName()));
        }
    }

    public void testDefaultValidationSettings()
    {
        XMLInputFactory f = getNewInputFactory();
        assertEquals(Boolean.FALSE, f.getProperty(XMLInputFactory.IS_VALIDATING));
        // A few impls might not support this, but it is the default...
        assertEquals(Boolean.TRUE, f.getProperty(XMLInputFactory.SUPPORT_DTD));
    }

    public void testDefaultMiscSettings()
    {
        XMLInputFactory f = getNewInputFactory();

        assertEquals(Boolean.TRUE, f.getProperty(XMLInputFactory.IS_NAMESPACE_AWARE));
        assertEquals(Boolean.FALSE, f.getProperty(XMLInputFactory.IS_COALESCING));
        // Shouldn't have default handlero objects either
        assertNull(f.getProperty(XMLInputFactory.REPORTER));
        assertNull(f.getProperty(XMLInputFactory.RESOLVER));
        assertNull(f.getProperty(XMLInputFactory.ALLOCATOR));
    }

}

