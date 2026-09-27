package stax2.stream;

import java.io.*;
import javax.xml.stream.*;
import javax.xml.transform.stream.StreamSource;

import org.codehaus.stax2.io.Stax2ByteArraySource;

import stax2.BaseStax2Test;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * This unit test suite verifies use of {@link StreamSource} as input
 * for {@link XMLInputFactory}.
 *
 * @author Tatu Saloranta
 *
 * @since 3.0
 */
public class TestStreamSource
    extends BaseStax2Test
{
    /**
     * This test is related to problem reported as [WSTX-182], inability
     * to use SystemId alone as source.
     */
    @Test
    public void testCreateUsingSystemId() throws Exception
    {
        File tmpF = File.createTempFile("staxtest", ".xml");
        tmpF.deleteOnExit();

        // First, need to write contents to the file
        Writer w = new OutputStreamWriter(new FileOutputStream(tmpF), UTF_8);
        w.write("<root />");
        w.close();

        XMLInputFactory f = getInputFactory();
        StreamSource src = new StreamSource();
        src.setSystemId(tmpF);
        XMLStreamReader sr = f.createXMLStreamReader(src);
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();
    }

    /**
     * System id of a source that also has content need not be a resolvable
     * URL, unless something is to be resolved relative to it.
     */
    @Test
    public void testNonUrlSystemIdWithContent() throws Exception
    {
        final String SYSTEM_ID = "urn:example:doc";
        XMLInputFactory f = getInputFactory();

        XMLStreamReader sr = f.createXMLStreamReader(new StreamSource(
                new ByteArrayInputStream("<root />".getBytes(UTF_8)), SYSTEM_ID));
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(SYSTEM_ID, sr.getLocation().getSystemId());
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();

        sr = f.createXMLStreamReader(new StreamSource(new StringReader("<root />"), SYSTEM_ID));
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(SYSTEM_ID, sr.getLocation().getSystemId());
        sr.close();
    }

    // For [woodstox-core#123]: edge case where content ends right after XML declaration
    // with unrecognized encoding
    @Test
    public void testInvalidDecl123() throws Exception
    {
        final byte[] XML = "<?xml version=\"1.1\" encoding=\"U\"?>".getBytes(UTF_8);
        final XMLInputFactory xmlF = getInputFactory();
        try {
            XMLStreamReader sr = xmlF.createXMLStreamReader(new Stax2ByteArraySource(XML, 0, XML.length));
            sr.next();
            fail("Should not pass");
        } catch (XMLStreamException e) {
            verifyException(e, "Unsupported encoding: U");
        }
    }
}
