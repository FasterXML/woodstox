package com.ctc.wstx.evt;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.Namespace;

import com.ctc.wstx.util.BaseNsContext;
import com.ctc.wstx.util.DataUtil;

/**
 * Hierarchic {@link NamespaceContext} implementation used when constructing
 * event and namespace information explicitly via
 * {@link javax.xml.stream.XMLEventFactory},
 * not by a stream reader.
 *<p>
 * TODO:
 *<ul>
 * <li>Figure out a way to check for namespace masking; tricky but not
 *    impossible to determine
 *  </li>
 *</ul>
 */
public class MergedNsContext
    extends BaseNsContext
{
    final NamespaceContext mParentCtxt;

    /**
     * List of {@link Namespace} instances.
     */
    final List<Namespace> mNamespaces;

    Map<String,Namespace> mNsByPrefix = null;

    Map<String,Namespace> mNsByURI = null;

    protected MergedNsContext(NamespaceContext parentCtxt, List<Namespace> localNs)
    {
        mParentCtxt = parentCtxt;
        if (localNs == null){
            mNamespaces = Collections.emptyList();
        } else {
            mNamespaces = localNs;
        }
    }

    public static BaseNsContext construct(NamespaceContext parentCtxt,
                                          List<Namespace> localNs)
    {
        return new MergedNsContext(parentCtxt, localNs);
    }

    /*
    /////////////////////////////////////////////
    // NamespaceContext API
    /////////////////////////////////////////////
     */

    @Override
    public String doGetNamespaceURI(String prefix)
    {
        // Note: base class checks for 'known' problems and prefixes:
        if (mNsByPrefix == null) {
            mNsByPrefix = buildByPrefixMap();
        }
        Namespace ns = mNsByPrefix.get(prefix);
        if (ns == null && mParentCtxt != null) {
            return mParentCtxt.getNamespaceURI(prefix);
        }
        return (ns == null) ? null : ns.getNamespaceURI();
    }

    @Override
    public String doGetPrefix(String nsURI)
    {
        // Note: base class checks for 'known' problems and prefixes:
        if (mNsByURI == null) {
            mNsByURI = buildByNsURIMap();
        }
        Namespace ns = mNsByURI.get(nsURI);
        if (ns == null && mParentCtxt != null) {
            return mParentCtxt.getPrefix(nsURI);
        }
        return (ns == null) ? null : ns.getPrefix();
    }

    @Override
    public Iterator<String> doGetPrefixes(String nsURI)
    {
        // Note: base class checks for 'known' problems and prefixes:
        ArrayList<String> l = null;

        for (int i = 0, len = mNamespaces.size(); i < len; ++i) {
            Namespace ns = mNamespaces.get(i);
            String uri = ns.getNamespaceURI();
            if (uri == null) {
                uri = "";
            }
            if (uri.equals(nsURI)) {
                if (l == null) {
                    l = new ArrayList<String>();
                }
                String prefix = ns.getPrefix();
                l.add((prefix == null) ? "" : prefix);
            }
        }

        if (mParentCtxt != null) {
            @SuppressWarnings("unchecked")
            Iterator<String> it = /*(Iterator<String>)*/mParentCtxt.getPrefixes(nsURI);
            if (l == null) {
                return it;
            }
            while (it.hasNext()) {
                l.add(it.next());
            }
        }

        if (l == null) {
            return DataUtil.emptyIterator();
        }
        return l.iterator();
    }

    /*
    /////////////////////////////////////////////
    // Extended API
    /////////////////////////////////////////////
     */

    /**
     * Method that returns information about namespace definition declared
     * in this scope; not including ones declared in outer scopes.
     */
    @Override
    public Iterator<Namespace> getNamespaces() {
        return mNamespaces.iterator();
    }

    @Override
    public void outputNamespaceDeclarations(Writer w) throws IOException
    {
        for (int i = 0, len = mNamespaces.size(); i < len; ++i) {
            Namespace ns = mNamespaces.get(i);
            w.write(' ');
            w.write(XMLConstants.XMLNS_ATTRIBUTE);
            if (!ns.isDefaultNamespaceDeclaration()) {
                w.write(':');
                w.write(ns.getPrefix());
            }
            w.write("=\"");
            w.write(ns.getNamespaceURI());
            w.write('"');
        }
    }

    /**
     * Method called by the matching start element class to
     * output all namespace declarations active in current namespace
     * scope, if any.
     */
    @Override
    public void outputNamespaceDeclarations(XMLStreamWriter w) throws XMLStreamException
    {
        for (int i = 0, len = mNamespaces.size(); i < len; ++i) {
            Namespace ns = mNamespaces.get(i);
            if (ns.isDefaultNamespaceDeclaration()) {
                w.writeDefaultNamespace(ns.getNamespaceURI());
            } else {
                w.writeNamespace(ns.getPrefix(), ns.getNamespaceURI());
            }
        }
    }

    /*
    /////////////////////////////////////////////
    // Private methods:
    /////////////////////////////////////////////
     */

    private Map<String,Namespace> buildByPrefixMap()
    {
        int len = mNamespaces.size();
        if (len == 0) {
            return Collections.emptyMap();
        }

        LinkedHashMap<String,Namespace> m = new LinkedHashMap<String,Namespace>(1 + len + (len>>1));
        for (int i = 0; i < len; ++i) {
            Namespace ns = mNamespaces.get(i);
            String prefix = ns.getPrefix();
            if (prefix == null) { // shouldn't happen but...
                prefix = "";
            }
            m.put(prefix, ns);
        }
        return m;
    }

    private Map<String,Namespace> buildByNsURIMap()
    {
        int len = mNamespaces.size();
        if (len == 0) {
            return Collections.emptyMap();
        }

        LinkedHashMap<String,Namespace> m = new LinkedHashMap<String,Namespace>(1 + len + (len>>1));
        for (int i = 0; i < len; ++i) {
            Namespace ns = mNamespaces.get(i);
            String uri = ns.getNamespaceURI();
            if (uri == null) { // shouldn't happen but...
                uri = "";
            }
            m.put(uri, ns);
        }
        return m;
    }
}
