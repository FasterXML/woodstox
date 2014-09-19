package org.codehaus.stax.test.wstream;

import javax.xml.stream.*;

/**
 * Unit tests that verify handling of XMLOutputFactory properties.
 * This includes:
 *<ul>
 * <li>Property defaults as defined by Stax specs (see class javadocs for
 * @link javax.xml.stream.XMLInputFactory}
 *  </li>
 *</ul>
 *
 * @author Tatu Saloranta
 */
public class TestProperties
    extends BaseWriterTest
{
    public void testDefaultSettings()
    {
        XMLOutputFactory f = getNewOutputFactory();
        assertEquals(Boolean.FALSE, f.getProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES));
    }

}
