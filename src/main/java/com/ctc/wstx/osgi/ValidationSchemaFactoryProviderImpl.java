package com.ctc.wstx.osgi;

import java.util.Properties;

import org.codehaus.stax2.validation.XMLValidationSchema;
import org.codehaus.stax2.validation.XMLValidationSchemaFactory;
import org.codehaus.stax2.osgi.Stax2ValidationSchemaFactoryProvider;

import com.ctc.wstx.api.ValidatorConfig;
import com.ctc.wstx.dtd.DTDSchemaFactory;
import com.ctc.wstx.msv.RelaxNGSchemaFactory;
import com.ctc.wstx.msv.W3CSchemaFactory;

public abstract class ValidationSchemaFactoryProviderImpl
    implements Stax2ValidationSchemaFactoryProvider
{
    final String mSchemaType;

    protected ValidationSchemaFactoryProviderImpl(String st)
    {
        mSchemaType = st;
    }

    public static ValidationSchemaFactoryProviderImpl[] createAll()
    {
        return new ValidationSchemaFactoryProviderImpl[] {
            new DTD(), new RelaxNG(), new W3CSchema()
        };
    }

    @Override
    public abstract XMLValidationSchemaFactory createValidationSchemaFactory();

    @Override
    public String getSchemaType() { return mSchemaType; }

    public Properties getProperties()
    {
        Properties props = new Properties();
        props.setProperty(OSGI_SVC_PROP_IMPL_NAME, ValidatorConfig.getImplName());
        props.setProperty(OSGI_SVC_PROP_IMPL_VERSION, ValidatorConfig.getImplVersion());
        props.setProperty(OSGI_SVC_PROP_SCHEMA_TYPE, mSchemaType);
        return props;
    }

    /*
    ////////////////////////////////////////////////////////
    // Actual provider instances, one per type supported
    ////////////////////////////////////////////////////////
     */

    final static class DTD
        extends ValidationSchemaFactoryProviderImpl
    {
        DTD() { super(XMLValidationSchema.SCHEMA_ID_DTD); }

        @Override
        public XMLValidationSchemaFactory createValidationSchemaFactory() {
            return new DTDSchemaFactory();
        }
    }

    final static class RelaxNG
        extends ValidationSchemaFactoryProviderImpl
    {
        RelaxNG() { super(XMLValidationSchema.SCHEMA_ID_RELAXNG); }

        @Override
        public XMLValidationSchemaFactory createValidationSchemaFactory() {
            return new RelaxNGSchemaFactory();
        }
    }

    final static class W3CSchema
        extends ValidationSchemaFactoryProviderImpl
    {
        W3CSchema() { super(XMLValidationSchema.SCHEMA_ID_W3C_SCHEMA); }

        @Override
        public XMLValidationSchemaFactory createValidationSchemaFactory() {
            return new W3CSchemaFactory();
        }
    }
}
