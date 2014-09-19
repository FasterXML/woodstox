package wstxtest.vstream;

import javax.xml.stream.*;

import org.codehaus.stax2.XMLReporter2;
import org.codehaus.stax2.validation.XMLValidationProblem;

import wstxtest.stream.BaseStreamTest;

/**
 * Simple testing to ensure that {@link XMLReporter} works as
 * expected with respect to validation errors.
 *<p>
 * As of Woodstox 4.0, we will be actually using {@link XMLReporter2}
 * interface, both to test that the improved interface works, and
 * to get access to more accurate information.
 */
public class TestXMLReporter
    extends BaseStreamTest
{
    /**
     * Basic unit test for verifying that XMLReporter gets validation
     * errors reported.
     */
    public void testValidationError()
        throws XMLStreamException
    {
        String XML =
            "<!DOCTYPE root [\n"
            +" <!ELEMENT root (#PCDATA)>\n"
            +"]><root>...</root>"
            ;
        testOldReporterProblems(XML, 0);
        testNewReporterProblems(XML, null);

        // Then invalid, with one error
        XML =
            "<!DOCTYPE root [\n"
            +" <!ELEMENT root (leaf+)>\n"
            +"]><root></root>";
        ;
        testOldReporterProblems(XML, 1);
        testNewReporterProblems(XML, "at least one element <leaf>");
    }

    /**
     * Test for specific validation error, mostly to verify
     * fix to [WSTX-155] (and guard against regression)
     */
    public void testMissingAttrError()
        throws XMLStreamException
    {
        String XML =
            "<!DOCTYPE root [\n"
            +" <!ELEMENT root (#PCDATA)>\n"
            +" <!ATTLIST root attr CDATA #REQUIRED>\n"
            +"]><root />";
            ;
        testOldReporterProblems(XML, 1);
        testNewReporterProblems(XML, "Required attribute");
    }

    public void testInvalidFixedAttr()
        throws XMLStreamException
    {
        // Not ok to have any other value, either completely different
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root attr CDATA #FIXED 'fixed'>\n"
            +"]>\n<root attr='wrong'/>";
        testOldReporterProblems(XML, 1);
        testNewReporterProblems(XML, "FIXED attribute");

        // Or one with extra white space (CDATA won't get fully normalized)
        XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root attr CDATA #FIXED 'fixed'>\n"
            +"]>\n<root attr=' fixed '/>";
        testOldReporterProblems(XML, 1);
        testNewReporterProblems(XML, "FIXED attribute");
    }

    public void testInvalidIdAttr()
        throws XMLStreamException
    {
        // Error: undefined id 'someId'
        String XML = "<!DOCTYPE elem [\n"
            +"<!ELEMENT elem (elem*)>\n"
            +"<!ATTLIST elem id ID #IMPLIED>\n"
            +"<!ATTLIST elem ref IDREF #IMPLIED>\n"
            +"]>\n<elem ref='someId'/>";
        testOldReporterProblems(XML, 1);
        testNewReporterProblems(XML, "Undefined id");

        // Error: empty idref value
        XML = "<!DOCTYPE elem [\n"
            +"<!ELEMENT elem (elem*)>\n"
            +"<!ATTLIST elem id ID #IMPLIED>\n"
            +"<!ATTLIST elem ref IDREF #IMPLIED>\n"
            +"]>\n<elem ref=''/>";
        testOldReporterProblems(XML, 1);
        testNewReporterProblems(XML, "IDREF value");
    }

    public void testInvalidSimpleChoiceStructure()
        throws XMLStreamException
    {
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root (a1 | a2)+>\n"
            +"<!ELEMENT a1 EMPTY>\n"
            +"<!ELEMENT a2 (#PCDATA)>\n"
            +"]>\n"
            +"<root />";
        testOldReporterProblems(XML, 1);
        testNewReporterProblems(XML, "Expected at least one of elements");
    }
        
    /**
     * This test verifies that exception XMLReporter rethrows gets
     * properly propagated.
     */
    public void testErrorRethrow()
        throws XMLStreamException
    {
        String XML =
            "<!DOCTYPE root [\n"
            +" <!ELEMENT root (leaf+)>\n"
            +"]><root></root>";
        ;
        MyReporterOld rep = new MyReporterOld();
        rep.enableThrow();
        XMLStreamReader sr = getReader(XML, rep);
        try {
            streamThrough(sr);
            fail("Expected a re-thrown exception for invalid content");
        } catch (XMLStreamException xse) {
            ;
        }
        sr.close();
        assertEquals(1, rep.getCount());
        testNewReporterProblems(XML, "element <leaf>");
    }

    /*
    //////////////////////////////////////////////////
    // Helper methods
    //////////////////////////////////////////////////
     */

    private XMLStreamReader getReader(String xml, XMLReporter rep)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setNamespaceAware(f, true);
        setSupportDTD(f, true);
        setValidating(f, true);
        f.setXMLReporter(rep);
        return constructStreamReader(f, xml);
    }

    private void testOldReporterProblems(String XML, int expFails)
        throws XMLStreamException
    {
        MyReporterOld rep = new MyReporterOld();
        XMLStreamReader sr = getReader(XML, rep);

        streamThrough(sr);
        sr.close();
        int actFails = rep.getCount();
        assertEquals("Expected "+expFails+" fail(s), got "+actFails,
                     expFails, actFails);
    }

    private void testNewReporterProblems(String XML, String expMsg)
        throws XMLStreamException
    {
        MyReporter2 rep = new MyReporter2();
        XMLStreamReader sr = getReader(XML, rep);

        streamThrough(sr);
        sr.close();
        int actFails = rep.getCount();
        int expFails = (expMsg == null) ? 0 : 1;

        assertEquals("Expected "+expFails+" fail(s), got "+actFails,
                     expFails, actFails);
        if (expFails > 0) {
            String actMsg = rep.getMessage();
            if (actMsg == null) {
                actMsg = "";
            }
            if (actMsg.indexOf(expMsg) < 0) {
                fail("Expected failure to contain phrase '"+expMsg+"', did not, was: '"+actMsg+"'");
            }
        }
    }
        
    /*
    //////////////////////////////////////////////////
    // Helper classes
    //////////////////////////////////////////////////
     */

    /**
     * Base Report class, used to verify some aspects of using
     * plain old XMLReporter class (for example that we do
     * get 'relatedInfo' populated with XMLValidationProblem)
     */
    static class MyReporterOld
        implements XMLReporter
    {
        protected int _count = 0;

        protected String _firstMessage;

        protected boolean _doThrow = false;

        public MyReporterOld() { }

        public void enableThrow() { _doThrow = true; }

        public void report(String message,
                           String errorType,
                           Object relatedInfo,
                           Location location)
            throws XMLStreamException
        {
            ++_count;
            if (_firstMessage != null) {
                _firstMessage = message;
            }
            if (_doThrow) {
                throw new XMLStreamException(message, location);
            }
            /* 30-May-2008, TSa: Need to ensure that extraArg is of
             *   type XMLValidationProblem; new constraint for Woodstox
             */
            if (relatedInfo == null) {
                throw new IllegalArgumentException("relatedInformation null, should be an instance of XMLValidationProblem");
            }
            if (!(relatedInfo instanceof XMLValidationProblem)) {
                throw new IllegalArgumentException("relatedInformation not an instance of XMLValidationProblem (but "+relatedInfo.getClass().getName()+")");
            }
        }

        public int getCount() { return _count; }
        public String getMessage() { return _firstMessage; }
    }

    static class MyReporter2
        extends MyReporterOld
        implements XMLReporter2
    {
        public MyReporter2() { super(); }

        public void report(String message, String errorType,
                           Object relatedInfo, Location location)
            throws XMLStreamException
        {
            throw new Error("Should not get a call through old XMLReporter interface, when registering XMLReporter2");
        }

        public void report(XMLValidationProblem prob)
            throws XMLStreamException
        {
            ++_count;
            String msg = prob.getMessage();
            // Let's require a message here... for now
            if (msg == null) {
                throw new RuntimeException("Problem object missing 'message' property");
            }
            if (_firstMessage == null) {
                _firstMessage = msg;
            }
            if (_doThrow) {
                throw prob.toException();
            }
        }
    }
}


