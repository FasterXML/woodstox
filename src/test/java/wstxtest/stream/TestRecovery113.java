package wstxtest.stream;

import java.io.StringReader;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.ctc.wstx.stax.WstxInputFactory;

/**
 * Regression test for [woodstox-core#113]: parsing should be able to
 * continue after catching a {@link javax.xml.stream.XMLStreamException}
 * caused by an unbalanced (extra) close tag, even when the unbalanced
 * close tag is not preceded by whitespace.
 */
public class TestRecovery113
    extends BaseStreamTest
{
    // Unbalanced close tag preceded by whitespace
    public void testRecoveryFromUnbalancedCloseTagWithWS()
        throws XMLStreamException
    {
        String xml =
                "<CATALOG>"
              + "<CD><T>x</T></CD>"
              + " </CD>"                 // extra close tag, preceded by WS
              + "<CD><T>y</T></CD>"
              + "</CATALOG>";
        _verifyRecovery(xml);
    }

    // Unbalanced close tag with no preceding whitespace (the originally
    // unrecoverable case in the bug report).
    public void testRecoveryFromAdjacentUnbalancedCloseTag()
        throws XMLStreamException
    {
        String xml =
                "<CATALOG>"
              + "<CD><T>x</T></CD></CD>" // unbalanced, no separator
              + "<CD><T>y</T></CD>"
              + "</CATALOG>";
        _verifyRecovery(xml);
    }

    /**
     * Helper: read every event and assert that a single parse error is
     * thrown when reaching the unbalanced close tag, after which parsing
     * must continue and successfully reach END_DOCUMENT.
     */
    private void _verifyRecovery(String xml)
        throws XMLStreamException
    {
        WstxInputFactory f = new WstxInputFactory();
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader(xml));

        int errorCount = 0;
        boolean reachedEnd = false;
        // Bound the loop so a regression cannot hang the test
        for (int i = 0; i < 200; ++i) {
            try {
                int type = sr.next();
                if (type == END_DOCUMENT) {
                    reachedEnd = true;
                    break;
                }
            } catch (XMLStreamException e) {
                ++errorCount;
                // Should never throw IllegalStateException, only a proper
                // XMLStreamException (which we are already in the catch for).
                if (errorCount > 1) {
                    fail("Expected at most one parse error during recovery"
                            + ", got at least " + errorCount
                            + "; latest: " + e.getMessage());
                }
            } catch (RuntimeException e) {
                fail("Should not throw RuntimeException during recovery, got "
                        + e.getClass().getName() + ": " + e.getMessage());
            }
        }
        assertTrue("Should have thrown one parse error for the unbalanced close tag",
                errorCount == 1);
        assertTrue("Parsing should have recovered and reached END_DOCUMENT",
                reachedEnd);
        sr.close();
    }
}
