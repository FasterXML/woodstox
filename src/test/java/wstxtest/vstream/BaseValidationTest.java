package wstxtest.vstream;

import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;

import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.XMLStreamWriter2;
import org.codehaus.stax2.validation.*;

import com.ctc.wstx.stax.WstxInputFactory;
import com.ctc.wstx.stax.WstxOutputFactory;

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
        verifyFailure(xml, schema, failMsg, failPhrase, strict, true, false);
        verifyFailure(xml, schema, failMsg, failPhrase, strict, false, true);
    }
    
    protected void verifyFailure(String xml, XMLValidationSchema schema, String failMsg,
                                 String failPhrase, boolean strict, 
                                 boolean validateReader, boolean validateWriter) throws XMLStreamException
    {
        XMLStreamReader2 sr = constructStreamReader(getInputFactory(), xml);
        if (validateReader) {
            sr.validateAgainst(schema);
        }
        XMLStreamWriter2 sw = null;
        if (validateWriter) {
            sw = (XMLStreamWriter2) getOutputFactory().createXMLStreamWriter(new StringWriter());
            sw.validateAgainst(schema);
            sw.copyEventFromReader(sr, false);
        }
        try {
            while (sr.hasNext()) {
                /* int type = */sr.next();
                if (validateWriter) {
                    sw.copyEventFromReader(sr, false);
                }
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
    
    public enum ValidationMode {
        reader() {
            @Override
            public void validate(XMLValidationSchema schema, String XML, String expectedXML) throws XMLStreamException {
                XMLStreamReader2 sr = (XMLStreamReader2) new WstxInputFactory().createXMLStreamReader(new StringReader(XML));
                sr.validateAgainst(schema);
                streamThrough(sr);
                assertTokenType(END_DOCUMENT, sr.getEventType());
                sr.close();
            }

        }, 
        writerNsSimple() {

            @Override
            public void validate(XMLValidationSchema schema, String XML, String expectedXML)
                    throws XMLStreamException {
                XMLStreamReader2 sr = (XMLStreamReader2) new WstxInputFactory().createXMLStreamReader(new StringReader(XML));
                
                StringWriter writer = new StringWriter();
                WstxOutputFactory f = new WstxOutputFactory();
                f.getConfig().doSupportNamespaces(true);
                f.getConfig().enableAutomaticNamespaces(false);
                XMLStreamWriter2 sw =  (XMLStreamWriter2) f.createXMLStreamWriter(writer);
                sw.validateAgainst(schema);
                sw.copyEventFromReader(sr, false);
                while (sr.hasNext()) {
                    /* int type = */sr.next();
                    sw.copyEventFromReader(sr, false);
                }
                assertTokenType(END_DOCUMENT, sr.getEventType());
                sr.close();
                sw.close();
                assertEquals(expectedXML, writer.toString());
            }
        };
        public void validate(XMLValidationSchema schema, String XML) throws XMLStreamException {
            validate(schema, XML, XML);
        }
        public abstract void validate(XMLValidationSchema schema, String XML, String expectedXML) throws XMLStreamException;
    }
}
