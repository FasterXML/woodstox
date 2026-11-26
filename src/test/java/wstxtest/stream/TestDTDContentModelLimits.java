package wstxtest.stream;

import java.io.StringReader;

import javax.xml.stream.*;

import com.ctc.wstx.api.WstxInputProperties;
import org.junit.jupiter.api.Test;

/**
 * Tests that verify that the size of DFAs built for DTD content models
 * is limited: content models that are not deterministic may otherwise
 * need a DFA exponential in their length.
 */
public class TestDTDContentModelLimits
    extends BaseStreamTest
{
    @Test
    public void testExponentialContentModel() throws XMLStreamException
    {
        // ((a|b)*, a, (a|b), ..., (a|b)): DFA needs 2^(n+1) states
        StringBuilder sb = new StringBuilder("((a|b)*,a");
        for (int i = 0; i < 16; ++i) {
            sb.append(",(a|b)");
        }
        sb.append(')');
        final String XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root "+sb+">\n"
                +"<!ELEMENT a EMPTY>\n"
                +"<!ELEMENT b EMPTY>\n"
                +"]><root/>";

        // Fails when validating, with the default limit
        XMLInputFactory f = getValidatingFactory();
        verifyLimitExceeded(f, XML);

        // but can be raised
        f.setProperty(WstxInputProperties.P_MAX_DTD_DFA_SIZE, 1000000);
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader(XML));
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        sr.close();

        // and content models are not compiled when not validating
        f = getNewInputFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, true);
        f.setProperty(XMLInputFactory.IS_VALIDATING, false);
        sr = f.createXMLStreamReader(new StringReader(XML));
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();
    }

    @Test
    public void testWideContentModel() throws XMLStreamException
    {
        // Fewer states, but each with hundreds of transitions
        StringBuilder sb = new StringBuilder("((a|b");
        for (int i = 0; i < 800; ++i) {
            sb.append("|c").append(i);
        }
        sb.append(")*,a");
        for (int i = 0; i < 8; ++i) {
            sb.append(",(a|b)");
        }
        sb.append(')');
        final String XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root "+sb+">\n"
                +"]><root/>";
        verifyLimitExceeded(getValidatingFactory(), XML);
    }

    @Test
    public void testLimitAcrossContentModels() throws XMLStreamException
    {
        final String XML = "<!DOCTYPE root [\n"
                +"<!ELEMENT root (a,b?,a*)>\n"
                +"<!ELEMENT a (b,b?,a*)>\n"
                +"<!ELEMENT b EMPTY>\n"
                +"]><root><a><b/></a></root>";

        // Fine with default limit
        XMLInputFactory f = getValidatingFactory();
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader(XML));
        while (sr.hasNext()) {
            sr.next();
        }
        sr.close();

        // Each content model fits in 10, but both together do not
        f.setProperty(WstxInputProperties.P_MAX_DTD_DFA_SIZE, 10);
        verifyLimitExceeded(f, XML);
    }

    private XMLInputFactory getValidatingFactory()
    {
        XMLInputFactory f = getNewInputFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, true);
        f.setProperty(XMLInputFactory.IS_VALIDATING, true);
        return f;
    }

    private void verifyLimitExceeded(XMLInputFactory f, String xml)
        throws XMLStreamException
    {
        XMLStreamReader sr = f.createXMLStreamReader(new StringReader(xml));
        try {
            while (sr.hasNext()) {
                sr.next();
            }
            fail("Should have failed with DTD content model size limit");
        } catch (XMLStreamException e) {
            verifyException(e, "Maximum DTD content model size");
        }
        sr.close();
    }
}
