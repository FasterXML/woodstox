package wstxtest.vstream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;

import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.validation.XMLValidationException;
import org.codehaus.stax2.validation.XMLValidationSchema;

import org.junit.jupiter.api.Test;

/**
 * Tests to verify that entities referring to external resources are not
 * resolved while reading schema documents.
 */
public class TestSchemaExternalEntity
    extends BaseValidationTest
{
    /**
     * External parameter entity: declarations it contains must not be
     * pulled into the schema.
     */
    @Test
    public void testW3CSchemaExternalParameterEntity() throws Exception
    {
        File dtd = writeTempFile("wstx-ext", ".dtd",
                "<!ENTITY injected \"viaExternalEntity\">");
        String schema =
            "<?xml version='1.0'?>\n"
            +"<!DOCTYPE xs:schema [\n"
            +"  <!ENTITY % ext SYSTEM '"+dtd.toURI()+"'>\n"
            +"  %ext;\n"
            +"]>\n"
            +"<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>\n"
            +"  <xs:element name='&injected;' type='xs:string'/>\n"
            +"</xs:schema>";
        try {
            parseW3CSchema(schema);
            fail("Expected failure for schema using an external parameter entity");
        } catch (XMLStreamException e) {
            verifyException(e, "was referenced, but not declared");
        }
    }

    /**
     * Same for the external subset of the schema's own DOCTYPE declaration.
     */
    @Test
    public void testW3CSchemaExternalSubset() throws Exception
    {
        File dtd = writeTempFile("wstx-ext", ".dtd",
                "<!ENTITY injected \"viaExternalSubset\">");
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
     * External general entity: contents of the referenced file must not end
     * up in the grammar.
     */
    @Test
    public void testRelaxNGExternalEntity() throws Exception
    {
        final String SECRET = "contentsOfLocalFile";
        File secret = writeTempFile("wstx-secret", ".txt", SECRET);
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
     * Entities declared in the internal subset are unaffected, as are
     * schemas that declare no DOCTYPE at all.
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
    // Helper methods
    ///////////////////////////////////////////////////////////////////////
     */

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

    private File writeTempFile(String prefix, String suffix, String contents) throws Exception
    {
        File f = File.createTempFile(prefix, suffix);
        f.deleteOnExit();
        Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
        try {
            w.write(contents);
        } finally {
            w.close();
        }
        return f;
    }
}
