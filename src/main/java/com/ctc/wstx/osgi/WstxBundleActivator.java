package com.ctc.wstx.osgi;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import org.codehaus.stax2.osgi.Stax2InputFactoryProvider;
import org.codehaus.stax2.osgi.Stax2OutputFactoryProvider;
import org.codehaus.stax2.osgi.Stax2ValidationSchemaFactoryProvider;

/**
 * This class is responsible for registering OSGi service(s) that Woodstox
 * package provides. Currently it means registering all providers that are
 * needed to instantiate input, output and validation schema factories;
 * these are needed since JDK service-introspection (which is the standard
 * Stax instance instantiation mechanism) does not work with OSGi.
 */
public class WstxBundleActivator
    implements BundleActivator
{
    public WstxBundleActivator() { }

    /**
     * Method called on activation. We need to register all providers we have at
     * this point.
     */
    @Override
    public void start(BundleContext ctxt)
    {
        InputFactoryProviderImpl inputP = new InputFactoryProviderImpl();
        ctxt.registerService(Stax2InputFactoryProvider.class.getName(), inputP, inputP.getProperties());
        OutputFactoryProviderImpl outputP = new OutputFactoryProviderImpl();
        ctxt.registerService(Stax2OutputFactoryProvider.class.getName(), outputP, outputP.getProperties());
        ValidationSchemaFactoryProviderImpl[] impls = ValidationSchemaFactoryProviderImpl.createAll();
        for (int i = 0, len = impls.length; i < len; ++i) {
            ValidationSchemaFactoryProviderImpl impl = impls[i];
            ctxt.registerService(Stax2ValidationSchemaFactoryProvider.class.getName(), impl, impl.getProperties());
        }
    }

    @Override
    public void stop(BundleContext ctxt) {
        // Nothing to do here: OSGi automatically de-registers services upon
        // deactivation.
    }
}

