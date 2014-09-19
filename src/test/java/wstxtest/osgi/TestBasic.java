package wstxtest.osgi;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import wstxtest.BaseWstxTest;

import org.osgi.framework.*;

import com.ctc.wstx.osgi.*;

public class TestBasic
    extends BaseWstxTest
{
    public void testBundleActivation()
    {
        // Hmmh. Context is a beastly class... let's just proxy it
        InvocationHandler h = new ContextHandler();
        BundleContext ctxt = (BundleContext) Proxy.newProxyInstance(BundleContext.class.getClassLoader(), new Class[] { BundleContext.class }, h);
        WstxBundleActivator act = new WstxBundleActivator();

        // won't prove much... but at least there's noo fundamental flaw:
        act.start(ctxt);
    }

    /*
    //////////////////////////////////////////
    // Helper classes
    //////////////////////////////////////////
     */

    final static class ContextHandler
        implements InvocationHandler
    {
        public Object invoke(Object proxy, Method method, Object[] args)
        {
            // !!! TODO: make do something...
            return null;
        }
    }
}
