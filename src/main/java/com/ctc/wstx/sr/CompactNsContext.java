package com.ctc.wstx.sr;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;

import javax.xml.XMLConstants;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.Namespace;

// This is unfortunate dependency, but...
import org.codehaus.stax2.ri.evt.NamespaceEventImpl;

import com.ctc.wstx.util.BaseNsContext;
import com.ctc.wstx.util.DataUtil;

/**
 * Simple implementation of separate non-transient namespace context
 * object. Created for start-element event by transient namespace
 * instance updated by stream reader.
 *<p>
 * Note about implementation: Location information is only needed (and
 * only needs to passed) if access is made via extended interface; one
 * that can return information about actual Namespace event objects.
 */
public final class CompactNsContext
    extends BaseNsContext
{
    final Location mLocation;

    /**
     * Array that contains 2 Strings for each declared default namespace
     * (including default namespace declarations); first is the prefix,
     * second URI.
     */
    final String[] mNamespaces;

    /**
     * Number of entries in {@link #mNamespaces} (which is twice the number
     * of bindings)
     */
    final int mNsLength;

    /**
     * Index of first namespace pair in mNamespaces that is declared
     * in scope of element for which this context was constructed. May be
     * equal to {@link #mNsLength} (which indicates there are no local
     * bindings).
     */
    final int mFirstLocalNs;

    /**
     * List only needed to support List accessor from start-element event;
     * created lazily if/as needed.
     */
    transient ArrayList<Namespace> mNsList;

    public CompactNsContext(Location loc,
                            String[] namespaces, int nsLen,
                            int firstLocal)
    {
        mLocation = loc;
        mNamespaces = namespaces;
        mNsLength = nsLen;
        mFirstLocalNs = firstLocal;
    }

    /**
     * @param prefix Non-null, non-empty prefix (base-class verifies these
     *  constraints) to find namespace URI for.
     */
    @Override
    public String doGetNamespaceURI(String prefix)
    {
        /* Let's search from beginning towards end; this way we'll first
         * find the innermost (or, in case of same-level declaration, last)
         * declaration for prefix.
         */
        // (note: default namespace will be there too)
        String[] ns = mNamespaces;
        if (prefix.length() == 0) {
            for (int i = mNsLength-2; i >= 0; i -= 2) {
                if (ns[i] == null) {
                    return ns[i+1];
                }
            }
            return null; // default ns not bound
        }
        for (int i = mNsLength-2; i >= 0; i -= 2) {
            if (prefix.equals(ns[i])) {
                return ns[i+1];
            }
        }
        return null;
    }

    @Override
    public String doGetPrefix(String nsURI)
    {
        // Note: base class checks for 'known' problems and prefixes:

        String[] ns = mNamespaces;
        int len = mNsLength;

        main_loop:
        for (int i = len-1; i > 0; i -= 2) {
            if (nsURI.equals(ns[i])) {
                /* 29-Sep-2004, TSa: Actually, need to make sure that this
                 *    declaration is not masked by a later declaration.
                 *    This happens when same prefix is declared on a later
                 *    entry (ie. for child element)
                 */
                String prefix = ns[i-1];
                for (int j = i+1; j < len; j += 2) {
                    // Prefixes are interned, can do straight equality check
                    if (ns[j] == prefix) {
                        continue main_loop; // was masked!
                    }
                }
                String uri = ns[i-1];
                /* 19-Mar-2006, TSa: Empty namespaces are represented by
                 *    null prefixes; but need to be represented as empty
                 *    strings (to distinguish from unbound URIs).
                 */
                return (uri == null) ? "" : uri;
            }
        }
        return null;
    }

    @Override
    public Iterator<String> doGetPrefixes(String nsURI)
    {
        // Note: base class checks for 'known' problems and prefixes:

        String[] ns = mNamespaces;
        int len = mNsLength;
        String first = null;
        ArrayList<String> all = null;

        main_loop:
        for (int i = len-1; i > 0; i -= 2) {
            String currNS = ns[i];
            if (currNS == nsURI || currNS.equals(nsURI)) {
                /* 29-Sep-2004, TSa: Need to ensure it's not masked by
                 *    a later ns declaration in a child element.
                 */
                String prefix = ns[i-1];
                for (int j = i+1; j < len; j += 2) {
                    // Prefixes are interned, can do straight equality check
                    if (ns[j] == prefix) {
                        continue main_loop; // was masked, need to ignore
                    }
                }
                /* 19-Mar-2006, TSa: Empty namespaces are represented by
                 *    null prefixes; but need to be represented as empty
                 *    strings (to distinguish from unbound URIs).
                 */
                if (prefix == null) {
                    prefix = "";
                }
                if (first == null) {
                    first = prefix;
                } else {
                    if (all == null) {
                        all = new ArrayList<String>();
                        all.add(first);
                    }
                    all.add(prefix);
                }
            }
        }
        if (all != null) {
            return all.iterator();
        }
        if (first != null) {
            return DataUtil.singletonIterator(first);
        }
        return DataUtil.emptyIterator();
    }

    /*
    ///////////////////////////////////////////////////////
    // Extended API, needed by Wstx classes
    ///////////////////////////////////////////////////////
     */

    @Override
    public Iterator<Namespace> getNamespaces()
    {
        if (mNsList == null) {
            int firstLocal = mFirstLocalNs;
            int len = mNsLength - firstLocal;
            if (len == 0) { // can this happen?
                return DataUtil.emptyIterator();
            }
            if (len == 2) { // only one NS
                return DataUtil.<Namespace>singletonIterator(NamespaceEventImpl.constructNamespace
                        (mLocation,
                                mNamespaces[firstLocal],
                                mNamespaces[firstLocal+1]));
            }
            ArrayList<Namespace> l = new ArrayList<Namespace>(len >> 1);
            String[] ns = mNamespaces;
            for (len = mNsLength; firstLocal < len;
                 firstLocal += 2) {
                l.add(NamespaceEventImpl.constructNamespace(mLocation, ns[firstLocal],
                                              ns[firstLocal+1]));
            }
            mNsList = l;
        }
        return mNsList.iterator();
    }
    
    /**
     * Method called by {@link com.ctc.wstx.evt.CompactStartElement}
     * to output all 'local' namespace declarations active in current
     * namespace scope, if any. Local means that declaration was done in
     * scope of current element, not in a parent element.
     */
    @Override
    public void outputNamespaceDeclarations(Writer w) throws IOException
    {
        String[] ns = mNamespaces;
        for (int i = mFirstLocalNs, len = mNsLength; i < len; i += 2) {
            w.write(' ');
            w.write(XMLConstants.XMLNS_ATTRIBUTE);
            String prefix = ns[i];
            if (prefix != null && prefix.length() > 0) {
                w.write(':');
                w.write(prefix);
            }
            w.write("=\"");
            w.write(ns[i+1]);
            w.write('"');
        }
    }

    @Override
    public void outputNamespaceDeclarations(XMLStreamWriter w) throws XMLStreamException
    {
        String[] ns = mNamespaces;
        for (int i = mFirstLocalNs, len = mNsLength; i < len; i += 2) {
            String nsURI = ns[i+1];
            String prefix = ns[i];
            if (prefix != null && prefix.length() > 0) {
                w.writeNamespace(prefix, nsURI);
            } else {
                w.writeDefaultNamespace(nsURI);
            }
        }
    }
}
