package com.ctc.wstx.compat;

import java.util.logging.Logger;

import javax.xml.namespace.QName;

/**
 * Helper class used to solve [WSTX-174]: some older AppServers were
 * shipped with incompatible version of QName class, which is missing
 * the 3 argument constructor. To address this, we'll use bit of
 * ClassLoader hacker to gracefully (?) downgrade to using 2 arg
 * alternatives if necessary.
 *<p>
 * Note: choice of java.util.logging logging is only based on the
 * fact that it is guaranteed to be present (we have JDK 1.4 baseline
 * requirement) so that we do not add external dependencies.
 * It is not a recommendation for using JUL per se; most users would
 * do well to just use slf4j or log4j directly instead.
 *
 * @author Tatu Saloranta
 * 
 * @since 3.2.8
 */
public final class QNameCreator
{
    /**
     * Creator object that creates QNames using proper 3-arg constructor.
     * If dynamic class loading fails
     */
    private final static Helper _helper;
    static {
        Helper h = null;
        try {
            // Not sure where it'll fail, constructor or create...
            Helper h0 = new Helper();
            /*QName n =*/ h0.create("elem", "http://dummy", "ns");
            h = h0;
        } catch (Throwable t) {
            String msg = "Could not construct QNameCreator.Helper; assume 3-arg QName constructor not available and use 2-arg method instead. Problem: "+t.getMessage();
            try {
                Logger.getLogger("com.ctc.wstx.compat.QNameCreator").warning(msg);
            } catch (Throwable t2) { // just in case JUL craps out...
                System.err.println("ERROR: failed to log error using Logger (problem "+t.getMessage()+"), original problem: "+msg);
            }
        }
        _helper = h;
    }

    public static QName create(String uri, String localName, String prefix)
    {
        if (_helper == null) { // can't use 3-arg constructor; but 2-arg will be there
            return new QName(uri, localName);
        }
        return _helper.create(uri, localName, prefix);
    }

    /**
     * Helper class used to encapsulate calls to the missing method.
     */
    private final static class Helper
    {
        public Helper() { }
        
        public QName create(String localName, String nsURI, String prefix)
        {
            return new QName(localName, nsURI, prefix);
        }
    }
}

