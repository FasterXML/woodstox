package wstxtest.sax;

import java.io.*;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;

import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

import com.ctc.wstx.exc.WstxException;
import com.ctc.wstx.sax.WstxSAXParserFactory;
import com.ctc.wstx.stax.WstxInputFactory;

import wstxtest.BaseWstxTest;
import org.junit.jupiter.api.Test;

/**
 * Simple unit tests to verify that most fundamental parsing functionality
 * works via Woodstox SAX implementation.
 */
public class TestEntityResolver
    extends BaseWstxTest
{
    @Test
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
    @Test
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

    /**
     * Test for [woodstox-core#226]: when {@code SUPPORT_DTD} is disabled on the
     * underlying {@link WstxInputFactory}, the SAX parser should honor that
     * setting and not attempt to resolve the external DTD subset.
     */
    @Test
    public void testFactorySupportDtdIsInherited()
        throws Exception
    {
        final String XML =
            "<!DOCTYPE root PUBLIC '//some//public//id' 'no-such-thing.dtd'>\n"
            +"<root />"
            ;

        WstxInputFactory inputFactory = new WstxInputFactory();
        inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        WstxSAXParserFactory spf = new WstxSAXParserFactory(inputFactory);
        SAXParser sp = spf.newSAXParser();

        DefaultHandler h = new DefaultHandler();
        try {
            sp.parse(new InputSource(new StringReader(XML)), h);
        } catch (SAXException e) {
            fail("Should not have failed when SUPPORT_DTD is disabled on factory, got ("
                    + e.getClass() + "): " + e.getMessage());
        }
    }

    /**
     * Test for [woodstox-core#226]: when a SAX EntityResolver is explicitly
     * registered on the parser, it should win over the {@link XMLResolver}
     * configured on the factory (the factory resolver is only a fallback).
     */
    @Test
    public void testSAXEntityResolverWinsOverFactoryResolver()
        throws Exception
    {
        final String XML =
            "<!DOCTYPE root PUBLIC '//some//public//id' 'no-such-thing.dtd'>\n"
            +"<root />"
            ;

        WstxInputFactory inputFactory = new WstxInputFactory();
        final boolean[] factoryResolverCalled = new boolean[] { false };
        inputFactory.setXMLResolver(new XMLResolver() {
            @Override
            public Object resolveEntity(String publicID, String systemID, String baseURI, String namespace)
                throws XMLStreamException {
                factoryResolverCalled[0] = true;
                return new ByteArrayInputStream(new byte[0]);
            }
        });
        WstxSAXParserFactory spf = new WstxSAXParserFactory(inputFactory);
        SAXParser sp = spf.newSAXParser();

        final boolean[] saxResolverCalled = new boolean[] { false };
        sp.getXMLReader().setEntityResolver(new EntityResolver() {
            @Override
            public InputSource resolveEntity(String publicId, String systemId) {
                saxResolverCalled[0] = true;
                return new InputSource(new StringReader(""));
            }
        });

        DefaultHandler h = new DefaultHandler();
        try {
            sp.parse(new InputSource(new StringReader(XML)), h);
        } catch (SAXException e) {
            fail("Parsing failed: ("+e.getClass()+"): "+e.getMessage());
        }
        assertTrue("Expected SAX EntityResolver to be invoked", saxResolverCalled[0]);
        assertFalse("Factory XMLResolver must not be consulted when SAX EntityResolver resolves",
                factoryResolverCalled[0]);
    }

    /**
     * A SAX {@link EntityResolver} may redirect a reference by returning
     * an {@link InputSource} that carries only a system id; the parser
     * must then open that system id instead of the one in the document.
     */
    @Test
    public void testRedirectExtSubsetBySystemId()
        throws Exception
    {
        final String XML =
            "<!DOCTYPE root SYSTEM 'no-such-thing.dtd'>\n"
            +"<root>&greeting;</root>"
            ;
        String localSysId = createTempResource(".dtd",
                "<!ENTITY greeting 'hello from local copy'>");
        assertEquals("hello from local copy", parseWithRedirect(XML, localSysId));
    }

    @Test
    public void testRedirectExtGeneralEntityBySystemId()
        throws Exception
    {
        final String XML =
            "<!DOCTYPE root [<!ENTITY ext SYSTEM 'no-such-thing.txt'>]>\n"
            +"<root>&ext;</root>"
            ;
        String localSysId = createTempResource(".txt", "hello from local copy");
        assertEquals("hello from local copy", parseWithRedirect(XML, localSysId));
    }

    private String createTempResource(String suffix, String contents)
        throws IOException
    {
        File f = File.createTempFile("wstxtest", suffix);
        f.deleteOnExit();
        Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
        try {
            w.write(contents);
        } finally {
            w.close();
        }
        return f.toURI().toString();
    }

    private String parseWithRedirect(String xml, final String redirectSysId)
        throws Exception
    {
        // Stax factory passed explicitly to keep entity replacement enabled
        SAXParserFactory spf = new WstxSAXParserFactory(new WstxInputFactory());
        spf.setNamespaceAware(true);
        XMLReader xr = spf.newSAXParser().getXMLReader();
        xr.setEntityResolver(new EntityResolver() {
            @Override
            public InputSource resolveEntity(String publicId, String systemId) {
                // system id only, no stream
                return new InputSource(redirectSysId);
            }
        });
        final StringBuilder text = new StringBuilder();
        xr.setContentHandler(new DefaultHandler() {
            @Override
            public void characters(char[] ch, int start, int len) {
                text.append(ch, start, len);
            }
        });
        try {
            xr.parse(new InputSource(new StringReader(xml)));
        } catch (SAXException e) {
            fail("Should have read the redirected resource, got ("+e.getClass()+"): "+e.getMessage());
        }
        return text.toString();
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
