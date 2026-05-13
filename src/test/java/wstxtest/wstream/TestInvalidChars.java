package wstxtest.wstream;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.stax2.*;

import com.ctc.wstx.api.InvalidCharHandler;
import com.ctc.wstx.api.WstxOutputProperties;

/**
 * This unit test suite verifies handling of invalid/illegal xml
 * characters, with or without explicit handlers
 *
 * @since 4.0
 */
public class TestInvalidChars
    extends BaseWriterTest
{
    final static String INVALID_TEXT = "\u0003";

    final static Character REPL_CHAR = '*';

    // // First let's verify that we do catch problematic chars

    public void testInvalidCatchingCharacters() throws XMLStreamException
    {
        doTestInvalid(CHARACTERS);
    }

    public void testInvalidCatchingCData() throws XMLStreamException
    {
        doTestInvalid(CDATA);
    }

    public void testInvalidCatchingComment() throws XMLStreamException
    {
        doTestInvalid(COMMENT);
    }

    public void testInvalidCatchingPI() throws XMLStreamException
    {
        doTestInvalid(PROCESSING_INSTRUCTION);
    }

    public void testInvalidCatchingAttribute() throws XMLStreamException
    {
        doTestInvalid(ATTRIBUTE);
    }

    // // And then also that we can fix problems

    public void testValidReplacingCharacters() throws Exception
    {
        doTestValid(CHARACTERS);
    }

    public void testValidReplacingCData() throws Exception
    {
        doTestValid(CDATA);
    }
 
    public void testValidReplacingComment() throws Exception
    {
        doTestValid(COMMENT);
    }

    public void testValidReplacingPI() throws Exception
    {
        doTestValid(PROCESSING_INSTRUCTION);
    }

    public void testValidReplacingAttribute() throws Exception
    {
        doTestValid(ATTRIBUTE);
    }

    // // [woodstox#201] regression coverage: char[] overload of writeCData,
    // // and XML 1.1 mode (RestrictedChars cannot be escaped inside CDATA).

    /**
     * Exercises the {@code writeCData(char[], int, int)} overload, which was
     * not covered by the parameterized {@link #doTestInvalid}/{@link #doTestValid}
     * paths (those only call the {@code String} overload).
     */
    public void testCDataCharArrayInvalidIsCaught() throws Exception
    {
        XMLOutputFactory2 f = getFactory(null);
        // Embed INVALID_TEXT in middle of a larger buffer, with non-zero offset:
        char[] cbuf = ("xx" + INVALID_TEXT + "yy").toCharArray();
        for (XMLStreamWriter sw : new XMLStreamWriter[] {
                f.createXMLStreamWriter(new StringWriter()),
                f.createXMLStreamWriter(new ByteArrayOutputStream(), "UTF-8"),
                f.createXMLStreamWriter(new ByteArrayOutputStream(), "ISO-8859-1"),
                f.createXMLStreamWriter(new ByteArrayOutputStream(), "US-ASCII"),
        }) {
            XMLStreamWriter2 sw2 = (XMLStreamWriter2) sw;
            sw2.writeStartDocument();
            sw2.writeStartElement("root");
            try {
                sw2.writeCData(cbuf, 0, cbuf.length);
                fail("Expected exception for invalid char in writeCData(char[]) (writer: " + sw2 + ")");
            } catch (XMLStreamException expected) {
                sw2.closeCompletely();
            }
        }
    }

    public void testCDataCharArrayInvalidIsReplaced() throws Exception
    {
        XMLOutputFactory2 f = getFactory(REPL_CHAR);
        char[] cbuf = ("xx" + INVALID_TEXT + "yy").toCharArray();
        // Cover both encoded byte-stream and raw Writer backends:
        StringWriter strw = new StringWriter();
        ByteArrayOutputStream utf8Out = new ByteArrayOutputStream();
        OutputDest[] dests = new OutputDest[] {
                new OutputDest("StringWriter", f.createXMLStreamWriter(strw), strw, null, null),
                new OutputDest("UTF-8", f.createXMLStreamWriter(utf8Out, "UTF-8"), null, utf8Out, "UTF-8"),
        };
        for (OutputDest d : dests) {
            d.sw.writeStartDocument();
            d.sw.writeStartElement("root");
            d.sw.writeCData(cbuf, 0, cbuf.length);
            d.sw.writeEndElement();
            d.sw.writeEndDocument();
            d.sw.closeCompletely();
            String out = d.text();
            if (out.indexOf(INVALID_TEXT) >= 0) {
                fail(d.name + ": invalid char (U+0003) still present in output: '" + out + "'");
            }
            if (out.indexOf("xx" + REPL_CHAR + "yy") < 0) {
                fail(d.name + ": expected '" + REPL_CHAR + "' between 'xx'/'yy' in CDATA. Got: '" + out + "'");
            }
        }
    }

    /**
     * XML 1.1 RestrictedChars (0x01-0x1F minus tab/LF/CR) must appear as
     * character references in content, which is not possible inside
     * CDATA / comment / PI. Verify they're still caught when the document
     * is XML 1.1, for all of CDATA, COMMENT and PROCESSING_INSTRUCTION.
     */
    public void testCDataXml11RestrictedCharIsCaught() throws Exception {
        doTestXml11UnescapableInvalidIsCaught(CDATA);
    }

    public void testCommentXml11RestrictedCharIsCaught() throws Exception {
        doTestXml11UnescapableInvalidIsCaught(COMMENT);
    }

    public void testPIXml11RestrictedCharIsCaught() throws Exception {
        doTestXml11UnescapableInvalidIsCaught(PROCESSING_INSTRUCTION);
    }

    public void testCDataXml11RestrictedCharIsReplaced() throws Exception {
        doTestXml11UnescapableInvalidIsReplaced(CDATA);
    }

    public void testCommentXml11RestrictedCharIsReplaced() throws Exception {
        doTestXml11UnescapableInvalidIsReplaced(COMMENT);
    }

    public void testPIXml11RestrictedCharIsReplaced() throws Exception {
        doTestXml11UnescapableInvalidIsReplaced(PROCESSING_INSTRUCTION);
    }

    private void doTestXml11UnescapableInvalidIsCaught(int evtType) throws Exception
    {
        XMLOutputFactory2 f = getFactory(null);
        for (XMLStreamWriter sw : new XMLStreamWriter[] {
                f.createXMLStreamWriter(new StringWriter()),
                f.createXMLStreamWriter(new ByteArrayOutputStream(), "UTF-8"),
        }) {
            XMLStreamWriter2 sw2 = (XMLStreamWriter2) sw;
            sw2.writeStartDocument("1.1");
            sw2.writeStartElement("root");
            try {
                writeUnescapable(sw2, evtType);
                fail("Expected exception for XML 1.1 RestrictedChar in "
                        + tokenTypeDesc(evtType) + " (writer: " + sw2 + ")");
            } catch (XMLStreamException expected) {
                sw2.closeCompletely();
            }
        }
    }

    private void doTestXml11UnescapableInvalidIsReplaced(int evtType) throws Exception
    {
        XMLOutputFactory2 f = getFactory(REPL_CHAR);
        StringWriter strw = new StringWriter();
        XMLStreamWriter2 sw = (XMLStreamWriter2) f.createXMLStreamWriter(strw);
        sw.writeStartDocument("1.1");
        sw.writeStartElement("root");
        writeUnescapable(sw, evtType);
        sw.writeEndElement();
        sw.writeEndDocument();
        sw.closeCompletely();
        String out = strw.toString();
        if (out.indexOf(INVALID_TEXT) >= 0) {
            fail("XML 1.1 " + tokenTypeDesc(evtType)
                    + ": invalid char still present in output: '" + out + "'");
        }
        if (out.indexOf(REPL_CHAR) < 0) {
            fail("XML 1.1 " + tokenTypeDesc(evtType)
                    + ": expected replacement '" + REPL_CHAR + "' in output. Got: '" + out + "'");
        }
    }

    private void writeUnescapable(XMLStreamWriter2 sw, int evtType) throws XMLStreamException {
        switch (evtType) {
        case CDATA:
            sw.writeCData(INVALID_TEXT);
            break;
        case COMMENT:
            sw.writeComment(INVALID_TEXT);
            break;
        case PROCESSING_INSTRUCTION:
            sw.writeProcessingInstruction("pi", INVALID_TEXT);
            break;
        default:
            throw new IllegalArgumentException("evtType=" + evtType);
        }
    }

    /** Small struct so the replaced-CDATA test can iterate over backends with mixed output destinations. */
    private static final class OutputDest {
        final String name;
        final XMLStreamWriter2 sw;
        final StringWriter strw;
        final ByteArrayOutputStream baos;
        final String encoding;
        OutputDest(String name, XMLStreamWriter sw, StringWriter strw, ByteArrayOutputStream baos, String encoding) {
            this.name = name;
            this.sw = (XMLStreamWriter2) sw;
            this.strw = strw;
            this.baos = baos;
            this.encoding = encoding;
        }
        String text() throws IOException {
            return (strw != null) ? strw.toString() : baos.toString(encoding);
        }
    }

    /*
    //////////////////////////////////////////////
    // Shared test code
    //////////////////////////////////////////////
     */

    private void doTestInvalid(int evtType)
        throws XMLStreamException
    {
        XMLOutputFactory2 f = getFactory(null);
        doTestInvalid(evtType, f.createXMLStreamWriter(new ByteArrayOutputStream(), "ISO-8859-1"), true);
        doTestInvalid(evtType, f.createXMLStreamWriter(new ByteArrayOutputStream(), "US-ASCII"), true);
        // [WSTX-173] / [woodstox#201]: BufferingXmlWriter (StringWriter and
        // UTF-8 byte-stream backends) used to skip invalid-char checks for
        // CDATA/COMMENT/PI; the failures were suppressed via this flag rather
        // than uncovered. Now fixed -- strict for all backends.
        doTestInvalid(evtType, f.createXMLStreamWriter(new StringWriter()), true);
        doTestInvalid(evtType, f.createXMLStreamWriter(new ByteArrayOutputStream(), "UTF-8"), true);
    }

    /**
     * @param strictChecks Due to [WSTX-173], may need to relax some checks to pass
     *                     for now. Not needed once bug is fixed.
     */
    private void doTestInvalid(int evtType, XMLStreamWriter sw1, boolean strictChecks) throws XMLStreamException {
        XMLStreamWriter2 sw = (XMLStreamWriter2) sw1;
        sw.writeStartDocument();
        sw.writeStartElement("root");
        try {
            switch (evtType) {
            case ATTRIBUTE:
                sw.writeAttribute("attr", INVALID_TEXT);
                // always strict for attributes and characters
                handleFailure(sw, "Expected an exception for ATTRIBUTE", true);
                break;
            case CHARACTERS:
                sw.writeCharacters(INVALID_TEXT);
                handleFailure(sw, "Expected an exception for CHARACTERS", true);
                break;
            case CDATA:
                sw.writeCData(INVALID_TEXT);
                handleFailure(sw, "Expected an exception for CDATA", strictChecks);
                break;
            case COMMENT:
                sw.writeComment(INVALID_TEXT);
                handleFailure(sw, "Expected an exception for COMMENT", strictChecks);
                break;
            case PROCESSING_INSTRUCTION:
                sw.writeProcessingInstruction("pi", INVALID_TEXT);
                handleFailure(sw, "Expected an exception for PROCESSING_INSTRUCTION", strictChecks);
                break;
            }
        } catch (XMLStreamException xse) {
            sw.closeCompletely();
        }
    }

    private void doTestValid(int evtType) throws IOException, XMLStreamException {
        XMLOutputFactory2 f = getFactory(REPL_CHAR);
        doTestValid(f, evtType, "ISO-8859-1", true);
        doTestValid(f, evtType, "US-ASCII", true);

        // [WSTX-173] / [woodstox#201]: BufferingXmlWriter (UTF-8 and raw
        // Writer backends) used to skip CDATA/COMMENT/PI invalid-char
        // handling; failures were suppressed here. Now fixed -- strict.
        doTestValid(f, evtType, "UTF-8", true);

        StringWriter strw = new StringWriter();
        XMLStreamWriter sw = f.createXMLStreamWriter(strw);
        buildValid(evtType, sw);
        verifyValidReplacement(evtType, sw, strw.toString(), true);
    }

    private void doTestValid(XMLOutputFactory2 f, int evtType, String enc, boolean strict)
            throws IOException, XMLStreamException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        XMLStreamWriter sw = f.createXMLStreamWriter(out, enc);
        buildValid(evtType, sw);
        verifyValidReplacement(evtType, sw, out.toString(enc), strict);
    }

    private void verifyValidReplacement(int evtType, XMLStreamWriter sw, String doc, boolean strict) {
        if (doc.indexOf(REPL_CHAR) < 0) { // no replacement...
            handleFailure(sw,
                    "Failed to replace invalid char, event " + tokenTypeDesc(evtType) + ", xml = '" + doc + "'",
                    strict);
        }
    }

    private void buildValid(int evtType, XMLStreamWriter sw1) throws XMLStreamException {
        XMLStreamWriter2 sw = (XMLStreamWriter2) sw1;
        sw.writeStartDocument();
        sw.writeStartElement("root");

        switch (evtType) {
        case ATTRIBUTE:
            sw.writeAttribute("attr", INVALID_TEXT);
            break;
        case CHARACTERS:
            sw.writeCharacters(INVALID_TEXT);
            break;
        case CDATA:
            sw.writeCData(INVALID_TEXT);
            break;
        case COMMENT:
            sw.writeComment(INVALID_TEXT);
            break;
        case PROCESSING_INSTRUCTION:
            sw.writeProcessingInstruction("pi", INVALID_TEXT);
            break;
        }
        sw.writeEndElement();
        sw.writeEndDocument();
        sw.closeCompletely();
    }

    private void handleFailure(XMLStreamWriter sw, String msg, boolean doFail) {
        if (doFail) {
            fail(msg + " (stream writer: " + sw + ")");
        } else {
            warn("suppressing failure '" + msg + "' (stream writer: " + sw + ")");
        }
    }

    /*
    //////////////////////////////////////////////
    // Helper methods, low-level
    //////////////////////////////////////////////
     */

    private XMLOutputFactory2 getFactory(Character replChar) throws XMLStreamException {
        XMLOutputFactory2 f = getOutputFactory();
        setRepairing(f, false);
        setValidateContent(f, true);
        f.setProperty(WstxOutputProperties.P_OUTPUT_INVALID_CHAR_HANDLER,
                (replChar == null) ? null : new InvalidCharHandler.ReplacingHandler(replChar));
        return f;
    }
}
