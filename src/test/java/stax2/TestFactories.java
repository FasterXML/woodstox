package stax2;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.XMLStreamWriter2;

/**
 * Unit tests to verify expected handling of property accessors and
 * modifiers with Stax2 input and output factories, as well as
 * simple readers and writers.
 * These are mostly related to issue [WSTX-243]
 */
public class TestFactories extends BaseStax2Test
{
    private final String NO_SUCH_PROPERTY = "noSuchProperty";

    // [WSTX-243]; verify exception for input factory
    public void testPropertiesInputFactory() throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        // First, verify property is indeed unsupported
        assertFalse(f.isPropertySupported(NO_SUCH_PROPERTY));
        
        // First: error for trying to access unknown
        try {
            f.getProperty(NO_SUCH_PROPERTY);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            verifyException(e, NO_SUCH_PROPERTY);
        }

        // Ditto for trying to set such property
        try {
            f.setProperty(NO_SUCH_PROPERTY, "foobar");
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            verifyException(e, NO_SUCH_PROPERTY);
        }
    }

    public void testPropertiesStreamReader() throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        XMLStreamReader2 r = (XMLStreamReader2) f.createXMLStreamReader(new StringReader("<root></root>"));
        
        // First, verify property is indeed unsupported
        assertFalse(r.isPropertySupported(NO_SUCH_PROPERTY));

        /* Ok: as of Woodstox 4.0, behavior is such that no exception is thrown,
         * because javadocs do not indicate that it should be done (save for case
         * where property name is null). Whether this is right interpretation or not
         * is open to discussion; but for now we will verify that behavior does not
         * change from 4.0 without explicit decision.
         */
        /*
        try {
            Object ob = r.getProperty(NO_SUCH_PROPERTY);
            fail("Expected exception, instead got result: "+ob);
        } catch (IllegalArgumentException e) {
            verifyException(e, NO_SUCH_PROPERTY);
        }
        */
        Object ob = r.getProperty(NO_SUCH_PROPERTY);
        assertNull(ob);

        // And although setter is specified by Stax2, it too fails on unrecognized:
        try {
            r.setProperty(NO_SUCH_PROPERTY, "foobar");
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            verifyException(e, NO_SUCH_PROPERTY);
        }
    }

    // [WSTX-243]; verify exception for input factory
    public void testPropertiesOutputFactory() throws XMLStreamException
    {
        XMLOutputFactory f = getOutputFactory();
        // First, verify property is indeed unsupported
        assertFalse(f.isPropertySupported(NO_SUCH_PROPERTY));
        
        // First: error for trying to access unknown
        try {
            f.getProperty(NO_SUCH_PROPERTY);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            verifyException(e, NO_SUCH_PROPERTY);
        }

        // Ditto for trying to set such property
        try {
            f.setProperty(NO_SUCH_PROPERTY, "foobar");
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            verifyException(e, NO_SUCH_PROPERTY);
        }
    }

    public void testPropertiesStreamWriter() throws XMLStreamException
    {
        XMLOutputFactory f = getOutputFactory();
        XMLStreamWriter2 w = (XMLStreamWriter2) f.createXMLStreamWriter(new StringWriter());
        
        // First, verify property is indeed unsupported
        assertFalse(w.isPropertySupported(NO_SUCH_PROPERTY));
        
        // First: error for trying to access unknown, as per Stax 1.0 spec:
        try {
            w.getProperty(NO_SUCH_PROPERTY);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            verifyException(e, NO_SUCH_PROPERTY);
        }

        // And although setter is specified by Stax2, it too fails on unrecognized:
        try {
            w.setProperty(NO_SUCH_PROPERTY, "foobar");
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            verifyException(e, NO_SUCH_PROPERTY);
        }
    }
    
}
