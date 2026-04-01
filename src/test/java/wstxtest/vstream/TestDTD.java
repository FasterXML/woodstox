package wstxtest.vstream;

import java.io.*;
import java.net.*;

import javax.xml.stream.*;

import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.validation.*;

/**
 * This test suite should really be part of wstx-tools package, but since
 * there is some supporting code within core Woodstox, it was added here.
 * That way it is easier to check that no DTDFlatten functionality is
 * broken by low-level changes.
 */
public class TestDTD
    extends BaseValidationTest
{
    final static class MyReporter implements XMLReporter
    {
        public int count = 0;

        @Override
        public void report(String message, String errorType, Object relatedInformation, Location location)
        {
            ++count;
        }
    }

    final static String SIMPLE_DTD =
        "<!ELEMENT root (leaf+)>\n"
        +"<!ATTLIST root attr CDATA #REQUIRED>\n"
        +"<!ELEMENT leaf EMPTY>\n"
        ;

    /**
     * Test to show how [WSTX-190] occurs.
     */
    public void testMissingAttrWithReporter()
        throws XMLStreamException
    {
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"]><root attr='123' />";
        MyReporter rep = new MyReporter();
        XMLStreamReader sr = getValidatingReader(XML, rep);
        assertTokenType(DTD, sr.next());
        // and now should get a validation problem
        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();
        assertEquals(1, rep.count);
    }

    public void testFullValidationOk() throws XMLStreamException
    {
        String XML = "<root attr='123'><leaf /></root>";
        XMLValidationSchema schema = parseDTDSchema(SIMPLE_DTD);
        for (ValidationMode mode : ValidationMode.values()) {
            mode.validate(schema, XML, "<root attr=\"123\"><leaf/></root>");
        }
    }

    // [woodstox#23]
    public void testFullValidationIssue23() throws XMLStreamException
    {
        final String INPUT_DTD = "<!ELEMENT FreeFormText (#PCDATA) >\n"
                +"<!ATTLIST FreeFormText  xml:lang CDATA #IMPLIED >\n"
                ;
        String XML = "<FreeFormText xml:lang='en-US'>foobar</FreeFormText>";

        /*
        Resolver resolver = new XMLResolver() {
            @Override
            public Object resolveEntity(String publicID, String systemID, String baseURI, String namespace) {
                return new StringReader(DTD);
            }
        };
        f.setXMLResolver(resolver);
        */

        XMLValidationSchemaFactory schemaFactory =
                XMLValidationSchemaFactory.newInstance(XMLValidationSchema.SCHEMA_ID_DTD);
        XMLValidationSchema schema = schemaFactory.createSchema(new StringReader(INPUT_DTD));
        for (ValidationMode mode : ValidationMode.values()) {
            mode.validate(schema, XML, "<FreeFormText xml:lang=\"en-US\">foobar</FreeFormText>");
        }
    }

    /**
     * And then a test for validating starting when stream points
     * to START_ELEMENT
     */
    public void testPartialValidationOk()
        throws XMLStreamException
    {
        String XML = "<root attr=\"123\"><leaf/></root>";
        XMLValidationSchema schema = parseDTDSchema(SIMPLE_DTD);
        for (ValidationMode mode : ValidationMode.values()) {
            mode.validate(schema, XML);
        }
    }

    /*
     * Another test for checking that validation does end when
     * sub-tree ends...
     */
    /* 29-Apr-2009, TSa: Actually: not a valid test; as per
     *    Stax2 v3.0 javadocs, validation does NOT end with
     *   sub-tree...
     */
    /*
    public void testPartialValidationFollowedBy()
        throws XMLStreamException
    {
        String XML = "<x><root><leaf /></root><foobar /></x>";
        XMLValidationSchema schema = parseDTDSchema(SIMPLE_DTD);
        XMLStreamReader2 sr = getReader(XML);
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("x", sr.getLocalName());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
        sr.validateAgainst(schema);
        while (sr.next() != END_DOCUMENT) { }
        sr.close();
    }
    */

    /**
     * Test to verify [WSTX-207]
     */
    public void testSchemaWithFunnyFilename()
        throws Exception
    {
        // assuming CWD is the svn root
    	// 2014-05-19, tatu: Very fragile, should read via resource. But:
        File f = new File("").getAbsoluteFile();
        f = new File(f, "src");
        f = new File(f, "test");
        f = new File(f, "java");
        f = new File(f, "wstxtest");
        f = new File(f, "empty and spaces.dtd");

        URL url = f.toURI().toURL();

        XMLValidationSchema sch = parseSchema(url, XMLValidationSchema.SCHEMA_ID_DTD);
        assertNotNull(sch);
    }

    // Verify that EMPTY content model uses the correct error message (not #PCDATA message)
    public void testEmptyContentModelErrorMessage()
        throws XMLStreamException
    {
        final String DTD_STR = "<!ELEMENT root (child)>\n"
            +"<!ELEMENT child EMPTY>\n"
            +"<!ELEMENT nested EMPTY>\n";

        XMLValidationSchema schema = parseDTDSchema(DTD_STR);
        verifyFailure("<root><child><nested/></child></root>",
                schema,
                "element in EMPTY content model",
                "EMPTY content model");
    }

    // Regression test: DFA state construction used "> 0" instead of ">= 0"
    // for BitSet.nextSetBit(), skipping token at bit index 0. This could cause
    // incorrect validation of content models where multiple elements share
    // the same position in a choice/sequence group.
    public void testContentModelWithMultipleChoiceElements()
        throws XMLStreamException
    {
        // Use a content model with a choice of several elements;
        // the first declared element may land at bit index 0
        final String DTD_STR =
            "<!ELEMENT root (a|b|c)+>\n"
            +"<!ELEMENT a EMPTY>\n"
            +"<!ELEMENT b EMPTY>\n"
            +"<!ELEMENT c EMPTY>\n";

        XMLValidationSchema schema = parseDTDSchema(DTD_STR);

        // All three elements should be valid children — including whichever
        // lands at bit index 0
        for (ValidationMode mode : ValidationMode.values()) {
            mode.validate(schema, "<root><a/></root>");
            mode.validate(schema, "<root><b/></root>");
            mode.validate(schema, "<root><c/></root>");
            mode.validate(schema, "<root><a/><b/><c/></root>");
        }

        // And an invalid child should still be rejected
        verifyFailure("<root><d/></root>",
                schema,
                "undefined element in content model",
                "undefined");
    }

    /*
    //////////////////////////////////////////////////////
    // Helper methods
    //////////////////////////////////////////////////////
     */

    private XMLStreamReader getValidatingReader(String contents, XMLReporter rep)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        if (rep != null) {
            f.setProperty(XMLInputFactory.REPORTER, rep);
        }
        setSupportDTD(f, true);
        setValidating(f, true);
        return constructStreamReader(f, contents);
    }
}
