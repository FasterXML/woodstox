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

    final static Character REPL_CHAR = Character.valueOf('*');

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
	// [WSTX-173] affects backends that do not do their own encoding:
	doTestInvalid(evtType, f.createXMLStreamWriter(new StringWriter()), false);
	doTestInvalid(evtType, f.createXMLStreamWriter(new ByteArrayOutputStream(), "UTF-8"), false);
    }

    /**
     * @param strictChecks Due to [WSTX-173], may need to relax some checks
     *   to pass for now. Not needed once bug is fixed.
     */
    private void doTestInvalid(int evtType, XMLStreamWriter sw1,
			       boolean strictChecks)
	throws XMLStreamException
    {
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

    private void doTestValid(int evtType)
	throws IOException, XMLStreamException
    {
        XMLOutputFactory2 f = getFactory(REPL_CHAR);
	doTestValid(f, evtType, "ISO-8859-1", true);
	doTestValid(f, evtType, "US-ASCII", true);

	// [WSTX-173] affects backends that do not do their own encoding:
	doTestValid(f, evtType, "UTF-8", false);

	StringWriter strw = new StringWriter();
	XMLStreamWriter sw = f.createXMLStreamWriter(strw);
	buildValid(evtType, sw);
	verifyValidReplacement(evtType, sw, strw.toString(), false);
    }

    private void doTestValid(XMLOutputFactory2 f, int evtType, String enc, boolean strict)
	throws IOException, XMLStreamException
    {
	ByteArrayOutputStream out = new ByteArrayOutputStream();
	XMLStreamWriter sw = f.createXMLStreamWriter(out, enc);
	buildValid(evtType, sw);
	verifyValidReplacement(evtType, sw, out.toString(enc), strict);
    }

    private void verifyValidReplacement(int evtType, XMLStreamWriter sw, String doc, boolean strict)
    {
	if (doc.indexOf(REPL_CHAR.charValue()) < 0) { // no replacement...
	    handleFailure(sw, "Failed to replace invalid char, event "+tokenTypeDesc(evtType)+", xml = '"+doc+"'", strict);
	}
    }

    private void buildValid(int evtType, XMLStreamWriter sw1)
	throws XMLStreamException
    {
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

    private void handleFailure(XMLStreamWriter sw, String msg, boolean doFail)
    {
	if (doFail) {
	    fail(msg+" (stream writer: "+sw+")");
	} else {
	    warn("suppressing failure '"+msg+"' (stream writer: "+sw+")");
	}
    }

    /*
    //////////////////////////////////////////////
    // Helper methods, low-level
    //////////////////////////////////////////////
     */

    private XMLOutputFactory2 getFactory(Character replChar)
        throws XMLStreamException
    {
        XMLOutputFactory2 f = getOutputFactory();
        setRepairing(f, false);
        setValidateContent(f, true);
	f.setProperty(WstxOutputProperties.P_OUTPUT_INVALID_CHAR_HANDLER,
		      (replChar == null) ? null : new InvalidCharHandler.ReplacingHandler(replChar.charValue()));
        return f;
    }
}

