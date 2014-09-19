package stax2.dtd;

import java.io.*;
import java.net.URL;

import javax.xml.stream.*;
import javax.xml.transform.stream.StreamSource;

import org.codehaus.stax2.*;

import stax2.BaseStax2Test;

/**
 * Unit tests that verify that external DTD subsets are properly
 * referenced and accessed when using various input factory methods
 * that give enough information to reference relative paths (or explicitly
 * pass the whole path)
 */
public class TestExternalDTD
    extends BaseStax2Test
{
    final String EXTERNAL_FILENAME1 = "external1.xml";
    final String EXTERNAL_FILENAME2 = "external2.xml";

    final static String DTD1 = "external.dtd";

    final String EXTERNAL_XML1 =
        "<!DOCTYPE root SYSTEM 'external.dtd'>"
        +"<root>&simpleEntity;</root>"
        ;

    final String EXTERNAL_XML2 =
        "<!DOCTYPE root PUBLIC '//some//public//id' 'external.dtd'>"
        +"<root>&simpleEntity;</root>"
        ;

    final String SIMPLE_EXT_ENTITY_TEXT = "simple textual content";

    /**
     * This tests the basic dereferencing when giving an input stream
     * and system id (to use for figuring out the base); basic Stax 1.0
     * feature
     */
    public void testStreamWithSystemId()
        throws IOException, XMLStreamException
    {
        for (int i = 0; i < 2; ++i) {
            String filename = (i == 0) ? EXTERNAL_FILENAME1 : EXTERNAL_FILENAME2;
            String sysId = constructSystemId(resolveFile(filename));
            String XML = (i == 0) ? EXTERNAL_XML1 : EXTERNAL_XML2;
            XMLInputFactory2 f = getFactory();
            XMLStreamReader sr;
            try {
                sr = f.createXMLStreamReader(sysId, utf8StreamFromString(XML));
            } catch (XMLStreamException xse) {
                fail("Failed to construct a SystemID-based stream reader: "+xse);
                return; // never gets here
            }
            try {
                assertTokenType(DTD, sr.next());
                assertTokenType(START_ELEMENT, sr.next());
                assertTokenType(CHARACTERS, sr.next());
                assertEquals(SIMPLE_EXT_ENTITY_TEXT, getAndVerifyText(sr));
                assertTokenType(END_ELEMENT, sr.next());
            } catch (XMLStreamException xse) {
                fail("Failed to process content using SystemID-based stream reader: "+xse);
            }
        }
    }

    /**
     * This tests the basic dereferencing when giving a Reader
     * and system id (to use for figuring out the base); basic Stax 1.0
     * feature
     */
    public void testReaderWithSystemId()
        throws IOException, XMLStreamException
    {
        for (int i = 0; i < 2; ++i) {
            String filename = (i == 0) ? EXTERNAL_FILENAME1 : EXTERNAL_FILENAME2;
            String sysId = constructSystemId(resolveFile(filename));
            String XML = (i == 0) ? EXTERNAL_XML1 : EXTERNAL_XML2;
            XMLInputFactory2 f = getFactory();
            XMLStreamReader sr;
            try {
                sr = f.createXMLStreamReader(sysId, new StringReader(XML));
            } catch (XMLStreamException xse) {
                fail("Failed to construct a SystemID-based String reader: "+xse);
                return; // never gets here
            }
            try {
                assertTokenType(DTD, sr.next());
                assertTokenType(START_ELEMENT, sr.next());
                assertTokenType(CHARACTERS, sr.next());
                assertEquals(SIMPLE_EXT_ENTITY_TEXT, getAndVerifyText(sr));
                assertTokenType(END_ELEMENT, sr.next());
            } catch (XMLStreamException xse) {
                fail("Failed to process content using SystemID-based String reader: "+xse);
            }
        }
    }

    /**
     * This tests the basic dereferencing when giving an stream source
     * with both system id and input source; Stax 1.0 feature.
     */
    public void testStreamWithStreamSource()
        throws IOException, XMLStreamException
    {
        // First, using input source + sys id:
        for (int i = 0; i < 2; ++i) {
            String filename = (i == 0) ? EXTERNAL_FILENAME1 : EXTERNAL_FILENAME2;
            String sysId = constructSystemId(resolveFile(filename));
            String XML = (i == 0) ? EXTERNAL_XML1 : EXTERNAL_XML2;
            StreamSource src = new StreamSource(utf8StreamFromString(XML), sysId);
            XMLInputFactory2 f = getFactory();
            XMLStreamReader sr;
            try {
                sr = f.createXMLStreamReader(src);
            } catch (XMLStreamException xse) {
                fail("Failed to construct a StreamSource-based stream reader: "+xse);
                return; // never gets here
            }
            try {
                assertTokenType(DTD, sr.next());
                assertTokenType(START_ELEMENT, sr.next());
                assertTokenType(CHARACTERS, sr.next());
                assertEquals(SIMPLE_EXT_ENTITY_TEXT, getAndVerifyText(sr));
                assertTokenType(END_ELEMENT, sr.next());
            } catch (XMLStreamException xse) {
                fail("Failed to process content using StreamSource-based stream reader: "+xse);
            }
        }

        // Then just passing File:
        for (int i = 0; i < 2; ++i) {
            String filename = (i == 0) ? EXTERNAL_FILENAME1 : EXTERNAL_FILENAME2;
            File file = resolveFile(filename);
            StreamSource src = new StreamSource(file);            
            XMLInputFactory2 f = getFactory();
            XMLStreamReader sr;
            try {
                sr = f.createXMLStreamReader(src);
            } catch (XMLStreamException xse) {
                fail("Failed to construct a StreamSource/file-based stream reader: "+xse);
                return; // never gets here
            }
            try {
                assertTokenType(DTD, sr.next());
                assertTokenType(START_ELEMENT, sr.next());
                assertTokenType(CHARACTERS, sr.next());
                assertEquals(SIMPLE_EXT_ENTITY_TEXT, getAndVerifyText(sr));
                assertTokenType(END_ELEMENT, sr.next());
            } catch (XMLStreamException xse) {
                fail("Failed to process content using StreamSource/file-based stream reader: "+xse);
            }
        }
    }

    /**
     * Deref'ing with Stax2 factory method that takes a {@link java.net.URL}.
     */
    public void testURL()
        throws IOException, XMLStreamException
    {
        for (int i = 0; i < 2; ++i) {
            String filename = (i == 0) ? EXTERNAL_FILENAME1 : EXTERNAL_FILENAME2;
            URL src = resolveFile(filename).toURI().toURL();
            XMLInputFactory2 f = getFactory();
            XMLStreamReader sr;

            try {
                sr = f.createXMLStreamReader(src);
            } catch (XMLStreamException xse) {
                fail("Failed to construct an URL-based stream reader: "+xse);
                return; // never gets here
            }
            assertTokenType(DTD, sr.next());
            assertTokenType(START_ELEMENT, sr.next());
            assertTokenType(CHARACTERS, sr.next());
            assertEquals(SIMPLE_EXT_ENTITY_TEXT, getAndVerifyText(sr));
            assertTokenType(END_ELEMENT, sr.next());
        }
    }

    /**
     * Deref'ing with Stax2 factory method that takes a {@link java.io.File}
     */
    public void testFile()
        throws IOException, XMLStreamException
    {
        for (int i = 0; i < 2; ++i) {
            String filename = (i == 0) ? EXTERNAL_FILENAME1 : EXTERNAL_FILENAME2;
            File file = resolveFile(filename);
            XMLInputFactory2 f = getFactory();
            XMLStreamReader sr;
            try {
                sr = f.createXMLStreamReader(file);
            } catch (XMLStreamException xse) {
                fail("Failed to construct a file-based stream reader: "+xse);
                return; // never gets here
            }
            assertTokenType(DTD, sr.next());
            assertTokenType(START_ELEMENT, sr.next());
            assertTokenType(CHARACTERS, sr.next());
            assertEquals(SIMPLE_EXT_ENTITY_TEXT, getAndVerifyText(sr));
            assertTokenType(END_ELEMENT, sr.next());
        }
    }

    /*
    ////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////
     */

    private XMLInputFactory2 getFactory()
        throws XMLStreamException
    {
        // Let's prevent DTD caching...
        XMLInputFactory2 f = getNewInputFactory();
        // Need to support all entities
        setReplaceEntities(f, true);
        setSupportExternalEntities(f, true);
        // Need dtd support but not validation
        setSupportDTD(f, true);
        setValidating(f, false);
        // And to make test robust, coalescing
        setCoalescing(f, true);
        return f;
    }

    /**
     * Could/should make this more robust (refer as a resource via
     * class loader or such), but for now this should work:
     */
    private File resolveFile(String relName)
        throws IOException
    {
    	// not good -- fragile... but has to do for now
        File f = new File("src");
        f = new File(f, "test");
        f = new File(f, "java");
        f = new File(f, "stax2");
        f = new File(f, "stream");
        f = new File(f, relName);
        return f.getAbsoluteFile();
    }

    private String constructSystemId(File f)
    {
        return f.getAbsolutePath();
    }

    private InputStream utf8StreamFromString(String str)
        throws java.io.UnsupportedEncodingException
    {
        byte[] bytes = str.getBytes("UTF-8");
        return new ByteArrayInputStream(bytes);
    }
}
