package com.ctc.wstx.osgi;

import java.util.Properties;

import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.osgi.Stax2InputFactoryProvider;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.stax.WstxInputFactory;

public class InputFactoryProviderImpl
    implements Stax2InputFactoryProvider
{
    public XMLInputFactory2 createInputFactory() {
        return new WstxInputFactory();
    }

    protected Properties getProperties()
    {
        Properties props = new Properties();
        props.setProperty(OSGI_SVC_PROP_IMPL_NAME, ReaderConfig.getImplName());
        props.setProperty(OSGI_SVC_PROP_IMPL_VERSION, ReaderConfig.getImplVersion());
        return props;
    }
}
