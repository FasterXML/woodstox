package wstxtest.vstream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.validation.XMLValidationException;
import org.codehaus.stax2.validation.XMLValidationSchema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ctc.wstx.msv.W3CMultiSchemaFactory;

/**
 * Tests to verify that entities referring to external resources are not
 * resolved while reading schema documents; and, conversely, that schema
 * composition ({@code xs:include}, {@code xs:import}, RELAX NG
 * {@code externalRef}) -- which MSV resolves itself, not via entity
 * resolution -- keeps working.
 */
public class TestSchemaExternalEntity
    extends BaseValidationTest
{
    @TempDir
    File tempDir;

    /*
    ///////////////////////////////////////////////////////////////////////
    // External entities: must not be resolved
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * External parameter entity: declarations it contains must not be
     * pulled into the schema.
     */
    @Test
    public void testW3CSchemaExternalParameterEntity() throws Exception
    {
        try {
            parseW3CSchema(schemaWithExternalParameterEntity());
            fail("Expected failure for schema using an external parameter entity");
        } catch (XMLStreamException e) {
            // Exact wording is parser-specific; entity name is not
            verifyException(e, "injected");
        }
    }

    /**
     * Same for the external subset of the schema's own DOCTYPE declaration.
     */
    @Test
    public void testW3CSchemaExternalSubset() throws Exception
    {
        File dtd = writeFile("ext.dtd", "<!ENTITY injected \"viaExternalSubset\">");
        String schema =
            "<?xml version='1.0'?>\n"
            +"<!DOCTYPE xs:schema SYSTEM '"+dtd.toURI()+"'>\n"
            +"<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>\n"
            +"  <xs:element name='&injected;' type='xs:string'/>\n"
            +"</xs:schema>";
        try {
            parseW3CSchema(schema);
            fail("Expected failure for schema using an external DTD subset");
        } catch (XMLStreamException e) {
            verifyException(e, "accessExternalDTD");
        }
    }

    /**
     * Same for schemas read through {@link W3CMultiSchemaFactory}, which
     * uses a SAX parser factory of its own.
     */
    @Test
    public void testW3CMultiSchemaExternalParameterEntity() throws Exception
    {
        File xsd = writeFile("multi.xsd", schemaWithExternalParameterEntity());
        Map<String,Source> sources = new HashMap<String,Source>();
        sources.put("", new StreamSource(xsd.toURI().toString()));
        try {
            new W3CMultiSchemaFactory().createSchema(tempDir.toURI().toString(), sources);
            fail("Expected failure for schema using an external parameter entity");
        } catch (XMLStreamException e) {
            verifyException(e, "Failed to load schemas");
        }
    }

    /**
     * External general entity: contents of the referenced file must not end
     * up in the grammar.
     */
    @Test
    public void testRelaxNGExternalEntity() throws Exception
    {
        final String SECRET = "contentsOfLocalFile";
        File secret = writeFile("secret.txt", SECRET);
        String schema =
            "<?xml version='1.0'?>\n"
            +"<!DOCTYPE element [\n"
            +"  <!ENTITY xxe SYSTEM '"+secret.toURI()+"'>\n"
            +"]>\n"
            +"<element name='root' xmlns='http://relaxng.org/ns/structure/1.0'>\n"
            +"  <value>&xxe;</value>\n"
            +"</element>";
        XMLValidationSchema sch = parseRngSchema(schema);
        assertFalse("File contents must not be readable through an external entity",
                validates("<root>"+SECRET+"</root>", sch));
    }

    /**
     * Entities declared in the internal subset are unaffected.
     */
    @Test
    public void testW3CSchemaInternalEntity() throws Exception
    {
        String schema =
            "<?xml version='1.0'?>\n"
            +"<!DOCTYPE xs:schema [ <!ENTITY local 'declaredLocally'> ]>\n"
            +"<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>\n"
            +"  <xs:element name='&local;' type='xs:string'/>\n"
            +"</xs:schema>";
        XMLValidationSchema sch = parseW3CSchema(schema);
        assertTrue("Internal entity should still be expanded",
                validates("<declaredLocally>x</declaredLocally>", sch));
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Schema composition: must keep working
    ///////////////////////////////////////////////////////////////////////
     */

    @Test
    public void testW3CSchemaInclude() throws Exception
    {
        writeFile("included.xsd",
                "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>\n"
                +"  <xs:element name='root' type='xs:string'/>\n"
                +"</xs:schema>");
        File main = writeFile("including.xsd",
                "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>\n"
                +"  <xs:include schemaLocation='included.xsd'/>\n"
                +"</xs:schema>");
        XMLValidationSchema sch = parseSchema(main.toURI().toURL(),
                XMLValidationSchema.SCHEMA_ID_W3C_SCHEMA);
        assertTrue("'xs:include' should still be resolved",
                validates("<root>x</root>", sch));
    }

    @Test
    public void testW3CSchemaImport() throws Exception
    {
        writeFile("imported.xsd",
                "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:sub'>\n"
                +"  <xs:element name='child' type='xs:string'/>\n"
                +"</xs:schema>");
        File main = writeFile("importing.xsd",
                "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' xmlns:s='urn:sub'>\n"
                +"  <xs:import namespace='urn:sub' schemaLocation='imported.xsd'/>\n"
                +"  <xs:element name='root'>\n"
                +"    <xs:complexType><xs:sequence><xs:element ref='s:child'/></xs:sequence></xs:complexType>\n"
                +"  </xs:element>\n"
                +"</xs:schema>");
        XMLValidationSchema sch = parseSchema(main.toURI().toURL(),
                XMLValidationSchema.SCHEMA_ID_W3C_SCHEMA);
        assertTrue("'xs:import' should still be resolved",
                validates("<root><child xmlns='urn:sub'>x</child></root>", sch));
    }

    @Test
    public void testRelaxNGExternalRef() throws Exception
    {
        writeFile("referenced.rng",
                "<element name='root' xmlns='http://relaxng.org/ns/structure/1.0'><text/></element>");
        File main = writeFile("referring.rng",
                "<grammar xmlns='http://relaxng.org/ns/structure/1.0'>\n"
                +"  <start><externalRef href='referenced.rng'/></start>\n"
                +"</grammar>");
        XMLValidationSchema sch = parseSchema(main.toURI().toURL(),
                XMLValidationSchema.SCHEMA_ID_RELAXNG);
        assertTrue("'externalRef' should still be resolved",
                validates("<root>x</root>", sch));
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Helper methods
    ///////////////////////////////////////////////////////////////////////
     */

    private String schemaWithExternalParameterEntity() throws Exception
    {
        File dtd = writeFile("ext.dtd", "<!ENTITY injected \"viaExternalEntity\">");
        return "<?xml version='1.0'?>\n"
            +"<!DOCTYPE xs:schema [\n"
            +"  <!ENTITY % ext SYSTEM '"+dtd.toURI()+"'>\n"
            +"  %ext;\n"
            +"]>\n"
            +"<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>\n"
            +"  <xs:element name='&injected;' type='xs:string'/>\n"
            +"</xs:schema>";
    }

    private boolean validates(String doc, XMLValidationSchema schema) throws XMLStreamException
    {
        XMLStreamReader2 sr = (XMLStreamReader2) getInputFactory().createXMLStreamReader(new StringReader(doc));
        try {
            sr.validateAgainst(schema);
            while (sr.hasNext()) {
                sr.next();
            }
            return true;
        } catch (XMLValidationException e) {
            return false;
        } finally {
            sr.close();
        }
    }

    private File writeFile(String name, String contents) throws Exception
    {
        File f = new File(tempDir, name);
        Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
        try {
            w.write(contents);
        } finally {
            w.close();
        }
        return f;
    }
}
