package wstxtest.stream;

import java.io.StringReader;

import javax.xml.stream.*;

/**
 * Simple unit tests to try to verify that underlying buffers are
 * properly recycled.
 *<p>
 * Please note that due to arbitrary nature of GC and its interactions
 * with soft reference, as well as the way JUnit may run its unit
 * tests, these tests may not be as robust as they should be.
 */
public class TestBufferRecycling
    extends BaseStreamTest
{
    final static String DOC = "<root>text</root>";

    /**
     * Test that verifies that the underlying character buffer should
     * be reused between two parsing rounds
     */
    public void testCharBufferRecycling()
	throws Exception
    {
	XMLInputFactory f = getInputFactory();

	char[] buf1 = getCharBuffer(f.createXMLStreamReader(new StringReader(DOC)), true);
	char[] buf2 = getCharBuffer(f.createXMLStreamReader(new StringReader(DOC)), true);

	if (buf1 != buf2) {
	    fail("Expected underlying character buffer to be recycled");
	}
    }

    /**
     * Inverted test to verify that the buffers are NOT shared when they
     * can not be.
     */
    public void testCharBufferNonRecycling()
	throws Exception
    {
	XMLInputFactory f = getInputFactory();

	XMLStreamReader sr1 = f.createXMLStreamReader(new StringReader(DOC));
	XMLStreamReader sr2 = f.createXMLStreamReader(new StringReader(DOC));
	char[] buf1 = getCharBuffer(sr1, false);
	char[] buf2 = getCharBuffer(sr2, false);

	sr1.close();
	sr2.close();

	if (buf1 == buf2) {
	    fail("Should not have identical underlying character buffers when using concurrent stream readers");
	}
    }

    /*
    //////////////////////////////////////////////////////
    // Internal methods
    //////////////////////////////////////////////////////
     */

    char[] getCharBuffer(XMLStreamReader sr, boolean close)
	    throws XMLStreamException
    {
	    assertTokenType(START_ELEMENT, sr.next());
	    assertTokenType(CHARACTERS, sr.next());
	    char[] buf = sr.getTextCharacters();
	    if (close) {
	       sr.close();
	    }
	    return buf;
    }
}
