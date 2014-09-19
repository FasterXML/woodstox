package wstxtest.vstream;

import java.io.StringReader;
import java.net.URL;

import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.validation.*;

public abstract class BaseValidationTest
    extends wstxtest.stream.BaseStreamTest
{
    protected XMLValidationSchema parseSchema(String contents, String schemaType)
        throws XMLStreamException
    {
        XMLValidationSchemaFactory schF = XMLValidationSchemaFactory.newInstance(schemaType);
        return schF.createSchema(new StringReader(contents));
    }

    protected XMLValidationSchema parseSchema(URL ref, String schemaType)
        throws XMLStreamException
    {
        XMLValidationSchemaFactory schF = XMLValidationSchemaFactory.newInstance(schemaType);
        return schF.createSchema(ref);
    }

    protected XMLValidationSchema parseRngSchema(String contents)
        throws XMLStreamException
    {
        return parseSchema(contents, XMLValidationSchema.SCHEMA_ID_RELAXNG);
    }

    protected XMLValidationSchema parseDTDSchema(String contents)
        throws XMLStreamException
    {
        return parseSchema(contents, XMLValidationSchema.SCHEMA_ID_DTD);
    }

    protected XMLValidationSchema parseW3CSchema(String contents)
        throws XMLStreamException
    {
        return parseSchema(contents, XMLValidationSchema.SCHEMA_ID_W3C_SCHEMA);
    }

    protected void verifyFailure(String xml, XMLValidationSchema schema, String failMsg,
                                 String failPhrase) throws XMLStreamException
    {
        // default to strict handling:
        verifyFailure(xml, schema, failMsg, failPhrase, true);
    }
    
    protected void verifyFailure(String xml, XMLValidationSchema schema, String failMsg,
                                 String failPhrase, boolean strict) throws XMLStreamException
    {
        XMLStreamReader2 sr = constructStreamReader(getInputFactory(), xml);
        sr.validateAgainst(schema);
        try {
            while (sr.hasNext()) {
                /* int type = */sr.next();
            }
            fail("Expected validity exception for " + failMsg);
        } catch (XMLValidationException vex) {
            String origMsg = vex.getMessage();
            String msg = (origMsg == null) ? "" : origMsg.toLowerCase();
            if (msg.indexOf(failPhrase.toLowerCase()) < 0) {
                String actualMsg = "Expected validation exception for "
                    + failMsg + ", containing phrase '" + failPhrase
                    + "': got '" + origMsg + "'";
                if (strict) {
                    fail(actualMsg);
                }
                warn("suppressing failure due to MSV bug, failure: '"
                     + actualMsg + "'");
            }
            // should get this specific type; not basic stream exception
        } catch (XMLStreamException sex) {
            fail("Expected XMLValidationException for " + failMsg
                 + "; instead got " + sex.getMessage());
        }
    }
}
