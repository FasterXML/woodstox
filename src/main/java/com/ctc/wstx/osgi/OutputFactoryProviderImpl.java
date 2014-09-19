package com.ctc.wstx.osgi;

import java.util.Properties;

import org.codehaus.stax2.XMLOutputFactory2;
import org.codehaus.stax2.osgi.Stax2OutputFactoryProvider;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.stax.WstxOutputFactory;

public class OutputFactoryProviderImpl
    implements Stax2OutputFactoryProvider
{
    public XMLOutputFactory2 createOutputFactory() {
        return new WstxOutputFactory();
    }

    protected Properties getProperties()
    {
        Properties props = new Properties();
        props.setProperty(OSGI_SVC_PROP_IMPL_NAME, ReaderConfig.getImplName());
        props.setProperty(OSGI_SVC_PROP_IMPL_VERSION, ReaderConfig.getImplVersion());
        return props;
    }
}
