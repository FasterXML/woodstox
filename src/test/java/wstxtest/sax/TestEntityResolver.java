package wstxtest.sax;

import java.io.*;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;

import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

import com.ctc.wstx.exc.WstxException;
import com.ctc.wstx.sax.WstxSAXParserFactory;
import com.ctc.wstx.stax.WstxInputFactory;

import wstxtest.BaseWstxTest;

/**
 * Simple unit tests to verify that most fundamental parsing functionality
 * works via Woodstox SAX implementation.
 */
public class TestEntityResolver
    extends BaseWstxTest
{
    public void testWithDummyExtSubset()
        throws Exception
    {
        final String XML =
            "<!DOCTYPE root PUBLIC '//some//public//id' 'no-such-thing.dtd'>\n"
            +"<root />"
            ;

        SAXParserFactory spf = new WstxSAXParserFactory();
        spf.setNamespaceAware(true);
        SAXParser sp = spf.newSAXParser();
        DefaultHandler h = new DefaultHandler();

        /* First: let's verify that we get an exception for
         * unresolved reference...
         */
        try {
            sp.parse(new InputSource(new StringReader(XML)), h);
            fail("Should not pass");
        } catch (SAXException e) {
            Throwable cause = e.getCause();
            assertNotNull(cause);
            assertTrue(cause instanceof WstxException);
            // [woodstox-core#84]: actual message varies by OS so only verify
            // the file name appears (locale-independent)
            verifyException(e, "no-such-thing.dtd");
            // [woodstox-core#231]: previously also verified " file " substring,
            // but that part of the message is localized by the JVM/OS.
            // The "(was java.io.FileNotFoundException)" tag prepended by
            // Woodstox is locale-independent, so check for that instead.
            verifyException(e, FileNotFoundException.class.getName());
        }

        // And then with dummy resolver; should work ok now
        sp = spf.newSAXParser();
        sp.getXMLReader().setEntityResolver(new MyResolver("   "));
        h = new DefaultHandler();
        try {
            sp.parse(new InputSource(new StringReader(XML)), h);
        } catch (SAXException e) {
            fail("Should not have failed with entity resolver, got ("+e.getClass()+"): "+e.getMessage());
        }
    }

    /**
     * Test for [woodstox-core#226]: the {@link XMLResolver} configured on the
     * underlying {@link WstxInputFactory} should be used by the SAX parser
     * when no SAX EntityResolver is explicitly set on the parser itself.
     */
    public void testFactoryXMLResolverIsInherited()
        throws Exception
    {
        final String XML =
            "<!DOCTYPE root PUBLIC '//some//public//id' 'no-such-thing.dtd'>\n"
            +"<root />"
            ;

        WstxInputFactory inputFactory = new WstxInputFactory();
        final boolean[] resolverCalled = new boolean[] { false };
        inputFactory.setXMLResolver(new XMLResolver() {
            @Override
            public Object resolveEntity(String publicID, String systemID, String baseURI, String namespace)
                throws XMLStreamException {
                resolverCalled[0] = true;
                // Return an empty stream, equivalent to an empty DTD subset
                return new ByteArrayInputStream(new byte[0]);
            }
        });
        WstxSAXParserFactory spf = new WstxSAXParserFactory(inputFactory);
        SAXParser sp = spf.newSAXParser();

        DefaultHandler h = new DefaultHandler();
        try {
            sp.parse(new InputSource(new StringReader(XML)), h);
        } catch (SAXException e) {
            fail("Should not have failed when XMLResolver is set on factory, got ("
                    + e.getClass() + "): " + e.getMessage());
        }
        assertTrue("Expected XMLResolver configured on factory to be invoked", resolverCalled[0]);
    }

    /*
    ///////////////////////////////////////////////////////
    // Helper classes
    ///////////////////////////////////////////////////////
     */

    static class MyResolver
        implements EntityResolver
    {
        final String mContents;

        public MyResolver(String c) {
            mContents = c;
        }

        @Override
        public InputSource resolveEntity(String publicId, String systemId) {
            return new InputSource(new StringReader(mContents));
        }
    }
}
