/* Woodstox XML processor
 *
 * Copyright (c) 2005 Tatu Saloranta, tatu.saloranta@iki.fi
 *
 * Licensed under the License specified in file LICENSE, included with
 * the source code.
 * You may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ctc.wstx.sw;

import java.util.*;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamException;

import com.ctc.wstx.util.BijectiveNsMap;
import com.ctc.wstx.util.DataUtil;

/**
 * Class that encapsulates information about a specific element in virtual
 * output stack for namespace-aware writers.
 * It provides support for URI-to-prefix mappings as well as namespace
 * mapping generation.
 *<p>
 * One noteworthy feature of the class is that it is designed to allow
 * "short-term recycling", ie. instances can be reused within context
 * of a simple document output. While reuse/recycling of such lightweight
 * object is often useless or even counter productive, here it may
 * be worth using, due to simplicity of the scheme (basically using
 * a very simple free-elements linked list).
 */
public abstract class OutputElementBase
    implements NamespaceContext
{
    public final static int PREFIX_UNBOUND = 0;
    public final static int PREFIX_OK = 1;
    public final static int PREFIX_MISBOUND = 2;

    final static String sXmlNsPrefix = XMLConstants.XML_NS_PREFIX;
    final static String sXmlNsURI = XMLConstants.XML_NS_URI;

    /*
    ////////////////////////////////////////////
    // Namespace binding/mapping information
    ////////////////////////////////////////////
     */

    /**
     * Namespace context end application may have supplied, and that
     * (if given) should be used to augment explicitly defined bindings.
     */
    protected NamespaceContext mRootNsContext;

    protected String mDefaultNsURI;

    /**
     * Mapping of namespace prefixes to URIs and back.
     */
    protected BijectiveNsMap mNsMapping;

    /**
     * True, if {@link #mNsMapping} is a shared copy from the parent;
     * false if a local copy was created (which happens when namespaces
     * get bound etc).
     */
    protected boolean mNsMapShared;

    /*
    ////////////////////////////////////////////
    // Life-cycle
    ////////////////////////////////////////////
     */

    /**
     * Constructor for the virtual root element
     */
    protected OutputElementBase()
    {
        mNsMapping = null;
        mNsMapShared = false;
        mDefaultNsURI = "";
        mRootNsContext = null;
    }

    protected OutputElementBase(OutputElementBase parent, BijectiveNsMap ns)
    {
        mNsMapping = ns;
        mNsMapShared = (ns != null);
        mDefaultNsURI = parent.mDefaultNsURI;
        mRootNsContext = parent.mRootNsContext;
    }

    /**
     * Method called to reuse a pooled instance.
     */
    protected void relink(OutputElementBase parent)
    {
        mNsMapping = parent.mNsMapping;
        mNsMapShared = (mNsMapping != null);
        mDefaultNsURI = parent.mDefaultNsURI;
        mRootNsContext = parent.mRootNsContext;
    }

    protected abstract void setRootNsContext(NamespaceContext ctxt);

    /*
    ////////////////////////////////////////////
    // Public API, accessors
    ////////////////////////////////////////////
     */

    public abstract boolean isRoot();

    /**
     * @return String presentation of the fully-qualified name, in
     *   "prefix:localName" format (no URI). Useful for error and
     *   debugging messages.
     */
    public abstract String getNameDesc();

    public final String getDefaultNsUri() {
        return mDefaultNsURI;
    }

    /*
    ////////////////////////////////////////////
    // Public API, ns binding, checking
    ////////////////////////////////////////////
     */

    /**
     * Method similar to {@link #getPrefix}, but one that will not accept
     * the default namespace, only an explicit one. Usually used when
     * trying to find a prefix for attributes.
     */
    public final String getExplicitPrefix(String uri)
    {
        if (mNsMapping != null) {
            String prefix = mNsMapping.findPrefixByUri(uri);
            if (prefix != null) {
                return prefix;
            }
        }
        if (mRootNsContext != null) {
            String prefix = mRootNsContext.getPrefix(uri);
            if (prefix != null) {
                // Hmmh... still can't use the default NS:
                if (prefix.length() > 0) {
                    return prefix;
                }
                // ... should we try to find an explicit one?
            }
        }
        return null;
    }

    /**
     * Method that verifies that passed-in prefix indeed maps to the specified
     * namespace URI; and depending on how it goes returns a status for
     * caller.
     *
     * @param isElement If true, rules for the default NS are those of elements
     *   (ie. empty prefix can map to non-default namespace); if false,
     *   rules are those of attributes (only non-default prefix can map to
     *   a non-default namespace).
     *
     * @return PREFIX_OK, if passed-in prefix matches matched-in namespace URI
     *    in current scope; PREFIX_UNBOUND if it's not bound to anything, 
     *    and PREFIX_MISBOUND if it's bound to another URI.
     *
     * @throws XMLStreamException True if default (no) prefix is allowed to
     *    match a non-default URI (elements); false if not (attributes)
     */
    public final int isPrefixValid(String prefix, String nsURI,
                                   boolean isElement)
        throws XMLStreamException
    {
        // Hmmm.... caller shouldn't really pass null.
        if (nsURI == null) {
            nsURI = "";
        }

        /* First thing is to see if specified prefix is bound to a namespace;
         * and if so, verify it matches with data passed in:
         */

        // Checking default namespace?
        if (prefix == null || prefix.length() == 0) {
            if (isElement) {
                // It's fine for elements only if the URI actually matches:
                if (nsURI == mDefaultNsURI || nsURI.equals(mDefaultNsURI)) {
                    return PREFIX_OK;
                }
            } else {
                /* Attributes never use the default namespace: "no prefix"
                 * can only mean "no namespace"
                 */
                if (nsURI.length() == 0) {
                    return PREFIX_OK;
                }
            }
            return PREFIX_MISBOUND;
        }

        /* Need to handle 'xml' prefix and its associated
         *   URI; they are always declared by default
         */
        if (prefix.equals(sXmlNsPrefix)) {
            // Should we thoroughly verify its namespace matches...?
            // 01-Apr-2005, TSa: Yes, let's always check this
            if (!nsURI.equals(sXmlNsURI)) {
                throwOutputError("Namespace prefix '"+sXmlNsPrefix
                                 +"' can not be bound to non-default namespace ('"+nsURI+"'); has to be the default '"
                                 +sXmlNsURI+"'");
            }
            return PREFIX_OK;
        }

        // Nope checking some other namespace
        String act;

        if (mNsMapping != null) {
            act = mNsMapping.findUriByPrefix(prefix);
        } else {
            act = null;
        }

        if (act == null && mRootNsContext != null) {
            act = mRootNsContext.getNamespaceURI(prefix);
        }
 
        // Not (yet) bound...
        if (act == null) {
            return PREFIX_UNBOUND;
        }

        return (act == nsURI || act.equals(nsURI)) ?
            PREFIX_OK : PREFIX_MISBOUND;
    }

    /*
    ////////////////////////////////////////////
    // Public API, mutators
    ////////////////////////////////////////////
     */

    public abstract void setDefaultNsUri(String uri);

    public final String generateMapping(String prefixBase, String uri, int[] seqArr)
    {
        // This is mostly cut'n pasted from addPrefix()...
        if (mNsMapping == null) {
            // Didn't have a mapping yet? Need to create one...
            mNsMapping = BijectiveNsMap.createEmpty();
        } else if (mNsMapShared) {
            /* Was shared with parent(s)? Need to create a derivative, to
             * allow for nesting/scoping of new prefix
             */
            mNsMapping = mNsMapping.createChild();
            mNsMapShared = false;
        }
        return mNsMapping.addGeneratedMapping(prefixBase, mRootNsContext,
                                              uri, seqArr);
    }

    public final void addPrefix(String prefix, String uri)
    {
        if (mNsMapping == null) {
            // Didn't have a mapping yet? Need to create one...
            mNsMapping = BijectiveNsMap.createEmpty();
        } else if (mNsMapShared) {
            /* Was shared with parent(s)? Need to create a derivative, to
             * allow for nesting/scoping of new prefix
             */
            mNsMapping = mNsMapping.createChild();
            mNsMapShared = false;
        }
        mNsMapping.addMapping(prefix, uri);
    }

    /*
    //////////////////////////////////////////////////
    // NamespaceContext implementation
    //////////////////////////////////////////////////
     */

    @Override
    public final String getNamespaceURI(String prefix)
    {
        if (prefix.length() == 0) { //default NS
            return mDefaultNsURI;
        }
        if (mNsMapping != null) {
            String uri = mNsMapping.findUriByPrefix(prefix);
            if (uri != null) {
                return uri;
            }
        }
        return (mRootNsContext != null) ?
            mRootNsContext.getNamespaceURI(prefix) : null;
    }

    @Override
    public final String getPrefix(String uri)
    {
        if (mDefaultNsURI.equals(uri)) {
            return "";
        }
        if (mNsMapping != null) {
            String prefix = mNsMapping.findPrefixByUri(uri);
            if (prefix != null) {
                return prefix;
            }
        }
        return (mRootNsContext != null) ?
            mRootNsContext.getPrefix(uri) : null;
    }

    @Override
    public final Iterator<String> getPrefixes(String uri)
    {
        List<String> l = null;

        if (mDefaultNsURI.equals(uri)) {
            l = new ArrayList<String>();
            l.add("");
        }
        if (mNsMapping != null) {
            l = mNsMapping.getPrefixesBoundToUri(uri, l);
        }
        // How about the root namespace context? (if any)
        /* Note: it's quite difficult to properly resolve masking, when
         * combining these things (not impossible, just tricky); for now
         * let's do best effort without worrying about masking:
         */
        if (mRootNsContext != null) {
            Iterator<?> it = mRootNsContext.getPrefixes(uri);
            while (it.hasNext()) {
                String prefix = (String) it.next();
                if (prefix.length() == 0) { // default NS already checked
                    continue;
                }
                // slow check... but what the heck
                if (l == null) {
                    l = new ArrayList<String>();
                } else if (l.contains(prefix)) { // double-defined...
                    continue;
                }
                l.add(prefix);
            }
        }
        if (l == null) {
            return DataUtil.emptyIterator();
        }
        return l.iterator();
    }

    /*
    ////////////////////////////////////////////
    // Internal methods
    ////////////////////////////////////////////
     */

    protected final void throwOutputError(String msg)
        throws XMLStreamException
    {
        throw new XMLStreamException(msg);
    }
}
