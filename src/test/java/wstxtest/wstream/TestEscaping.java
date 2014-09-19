package wstxtest.wstream;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.stax2.*;

import com.ctc.wstx.api.WstxOutputProperties;

/**
 * This unit test suite verifies Woodstox-specific output-side
 * character escaping options
 */
public class TestEscaping
    extends BaseWriterTest
{
    public void testCrHandlingEscaping()
        throws XMLStreamException
    {
        doTestCrHandling(true, "Cr: \r.", "Cr: \r.", "Cr: \r.");
        doTestCrHandling(true, "CrLF: \r\n.", "CrLF: \r\n.", "CrLF: \r\n.");
    }

    public void testCrHandlingNonEscaping()
        throws XMLStreamException
    {
        doTestCrHandling(false, "Cr: \r.", "Cr: \n.", "Cr:  .");
        // attribute output is same, but parser handling differs, so:
        doTestCrHandling(false, "CrLF: \r\n.", "CrLF: \n.", "CrLF:  \n.");
    }

    /*
    ////////////////////////////////////////////////////
    // Helper methods
    ////////////////////////////////////////////////////
     */

    private void doTestCrHandling(boolean escaping, String input,
                                  String elemOutput, String attrOutput)
        throws XMLStreamException
    {
        // Let's try out 2 main encoding types:
        String[] ENC = new String[] { "UTF-8", "ISO-8859-1", "US-ASCII" };
        for (int encIx = 0; encIx < ENC.length; ++encIx) {
            // And 3 writer types:
            for (int type = 0; type < 3; ++type) {
                String enc = ENC[encIx];
                XMLOutputFactory2 f = getFactory(type, escaping);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                XMLStreamWriter sw = f.createXMLStreamWriter(out, enc);
                writeDoc(sw, input);
                sw.close();

                // Ok, do we get what we should?
                verifyDoc(escaping, enc, out.toByteArray(),
                          elemOutput, attrOutput, sw);
            }
        }
    }

    private void writeDoc(XMLStreamWriter sw, String text)
        throws XMLStreamException
    {
        sw.writeStartDocument();
        sw.writeStartElement("root");
        sw.writeAttribute("attr", text);
        sw.writeCharacters(text);
        sw.writeEndElement();
        sw.writeEndDocument();
    }

    private void verifyDoc(boolean escaping, String encoding, byte[] data,
                           String expElem, String expAttr, XMLStreamWriter sw)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = constructNsStreamReader(new ByteArrayInputStream(data), true);
        String actualText;
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());

        assertEquals(1, sr.getAttributeCount());
        actualText = sr.getAttributeValue(0);
        if (!expAttr.equals(actualText)) {
            failStrings("Attribute value incorrect (CR-escaping: "+escaping+", encoding: "+encoding+"; writer: "+sw+")", expAttr, actualText);
        }

        assertTokenType(CHARACTERS, sr.next());
        actualText = getAndVerifyText(sr);
        if (!expElem.equals(actualText)) {
            failStrings("Element value incorrect (CR-escaping: "+escaping+", encoding: "+encoding+", writer "+sw+")", expElem, actualText);
        }
        assertTokenType(END_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
    }

    private XMLOutputFactory2 getFactory(int type, boolean escapeCr)
        throws XMLStreamException
    {
        XMLOutputFactory2 f = getOutputFactory();
        // type 0 -> non-ns, 1 -> ns, non-repairing, 2 -> ns, repairing
        setNamespaceAware(f, type > 0); 
        setRepairing(f, type > 1); 

        f.setProperty(WstxOutputProperties.P_OUTPUT_ESCAPE_CR, escapeCr ? Boolean.TRUE : Boolean.FALSE);

        return f;
    }
}

