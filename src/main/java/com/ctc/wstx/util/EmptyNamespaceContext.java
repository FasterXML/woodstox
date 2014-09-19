package com.ctc.wstx.util;

import java.io.Writer;
import java.util.Iterator;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.Namespace;

import org.codehaus.stax2.ri.EmptyIterator;

/**
 * Dummy {@link NamespaceContext} (and {@link BaseNsContext})
 * implementation that is usually used in
 * non-namespace-aware mode.
 *<p>
 * Note: differs from Stax2 reference implementation's version
 * slightly, since it needs to support Woodstox specific extensions
 * for efficient namespace declaration serialization.
 */
public final class EmptyNamespaceContext
    extends BaseNsContext
{
    final static EmptyNamespaceContext sInstance = new EmptyNamespaceContext();
    
    private EmptyNamespaceContext() { }

    public static EmptyNamespaceContext getInstance() { return sInstance; }

    /*
    /////////////////////////////////////////////
    // Extended API
    /////////////////////////////////////////////
     */

    public Iterator<Namespace> getNamespaces() {
      	return EmptyIterator.getInstance();
    }

    /**
     * Method called by the matching start element class to
     * output all namespace declarations active in current namespace
     * scope, if any.
     */
    public void outputNamespaceDeclarations(Writer w)
    {
        ; // nothing to output
    }

    public void outputNamespaceDeclarations(XMLStreamWriter w)
    {
        ; // nothing to output
    }

    /*
    /////////////////////////////////////////////////
    // Template methods sub-classes need to implement
    /////////////////////////////////////////////////
     */

    public String doGetNamespaceURI(String prefix) {
        return null;
    }

    public String doGetPrefix(String nsURI) {
        return null;
    }

    public Iterator<String> doGetPrefixes(String nsURI) {
        return EmptyIterator.getInstance();
    }
}
