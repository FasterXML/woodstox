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

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import com.ctc.wstx.compat.QNameCreator;
import com.ctc.wstx.util.BijectiveNsMap;

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
public final class SimpleOutputElement
    extends OutputElementBase
{
    /*
    ///////////////////////////////////////////////////////////////////////
    // Information about element itself:
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Reference to the parent element, element enclosing this element.
     * Null for root element.
     * Non-final only to allow temporary pooling
     * (on per-writer basis, to keep these short-lived).
     */
    protected SimpleOutputElement mParent;

    /**
     * Prefix that is used for the element. Can not be final, since sometimes
     * it needs to be dynamically generated and bound after creating the
     * element instance.
     */
    protected String mPrefix;

    /**
     * Local name of the element.
     * Non-final only to allow reuse.
     */
    protected String mLocalName;

    /**
     * Namespace of the element, whatever {@link #mPrefix} maps to.
     * Non-final only to allow reuse.
     */
    protected String mURI;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Attribute information
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Map used to check for duplicate attribute declarations, if
     * feature is enabled.
     */
    protected HashSet<AttrName> mAttrSet = null;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Constructor for the virtual root element
     */
    private SimpleOutputElement()
    {
        super();
        mParent = null;
        mPrefix = null;
        mLocalName = "";
        mURI = null;
    }

    private SimpleOutputElement(SimpleOutputElement parent,
                                String prefix, String localName, String uri,
                                BijectiveNsMap ns)
    {
        super(parent, ns);
        mParent = parent;
        mPrefix = prefix;
        mLocalName = localName;
        mURI = uri;
    }

    /**
     * Method called to reuse a pooled instance.
     *
     * @returns Chained pooled instance that should now be head of the
     *   reuse chain
     */
    private void relink(SimpleOutputElement parent,
                        String prefix, String localName, String uri)
    {
        super.relink(parent);
        mParent = parent;
        mPrefix = prefix;
        mLocalName = localName;
        mURI = uri;
        mNsMapping = parent.mNsMapping;
        mNsMapShared = (mNsMapping != null);
        mDefaultNsURI = parent.mDefaultNsURI;
        mRootNsContext = parent.mRootNsContext;
    }

    public static SimpleOutputElement createRoot()
    {
        return new SimpleOutputElement();
    }

    /**
     * Simplest factory method, which gets called when a 1-argument
     * element output method is called. It is, then, assumed to
     * use the default namespce.
     */
    protected SimpleOutputElement createChild(String localName)
    {
        /* At this point we can also discard attribute Map; it is assumed
         * that when a child element has been opened, no more attributes
         * can be output.
         */
        mAttrSet = null;
        return new SimpleOutputElement(this, null, localName,
                                       mDefaultNsURI, mNsMapping);
    }

    /**
     * @return New head of the recycle pool
     */
    protected SimpleOutputElement reuseAsChild(SimpleOutputElement parent,
                                               String localName)
    {
        mAttrSet = null;
        SimpleOutputElement poolHead = mParent;
        relink(parent, null, localName, mDefaultNsURI);
        return poolHead;
    }

    protected SimpleOutputElement reuseAsChild(SimpleOutputElement parent,
                                               String prefix, String localName,
                                               String uri)
    {
        mAttrSet = null;
        SimpleOutputElement poolHead = mParent;
        relink(parent, prefix, localName, uri);
        return poolHead;
    }

    /**
     * Full factory method, used for 'normal' namespace qualified output
     * methods.
     */
    protected SimpleOutputElement createChild(String prefix, String localName,
                                              String uri)
    {
        /* At this point we can also discard attribute Map; it is assumed
         * that when a child element has been opened, no more attributes
         * can be output.
         */
        mAttrSet = null;
        return new SimpleOutputElement(this, prefix, localName, uri, mNsMapping);
    }

    /**
     * Method called to temporarily link this instance to a pool, to
     * allow reusing of instances with the same reader.
     */
    protected void addToPool(SimpleOutputElement poolHead)
    {
        mParent = poolHead;
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Public API, accessors
    ///////////////////////////////////////////////////////////////////////
     */

    public SimpleOutputElement getParent() {
        return mParent;
    }

    @Override
    public boolean isRoot() {
        // (Virtual) Root element has no parent...
        return (mParent == null);
    }

    /**
     * @return String presentation of the fully-qualified name, in
     *   "prefix:localName" format (no URI). Useful for error and
     *   debugging messages.
     */
    @Override
    public String getNameDesc() {
        if (mPrefix != null && mPrefix.length() > 0) {
            return mPrefix + ":" +mLocalName;
        }
        if (mLocalName != null && mLocalName.length() > 0) {
            return mLocalName;
        }
        return "#error"; // unexpected case
    }

    public String getPrefix() {
        return mPrefix;
    }

    public String getLocalName() {
        return mLocalName;
    }

    public String getNamespaceURI() {
        return mURI;
    }

    public QName getName() {
        return QNameCreator.create(mURI, mLocalName, mPrefix);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Public API, ns binding, checking
    ///////////////////////////////////////////////////////////////////////
     */

    public void checkAttrWrite(String nsURI, String localName)
        throws XMLStreamException
    {
        AttrName an = new AttrName(nsURI, localName);
        if (mAttrSet == null) {
            /* 13-Dec-2005, TSa: Should use a more efficient Set/Map value
             *   for this in future -- specifically one that could use
             *   ns/local-name pairs without intermediate objects
             */
            mAttrSet = new HashSet<AttrName>();
        }
        if (!mAttrSet.add(an)) {
            throw new XMLStreamException("Duplicate attribute write for attribute '"+an+"'");
        }
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Public API, mutators
    ///////////////////////////////////////////////////////////////////////
     */

    public void setPrefix(String prefix) {
        mPrefix = prefix;
    }

    @Override
    public void setDefaultNsUri(String uri) {
        mDefaultNsURI = uri;
    }

    /**
     * Note: this method can and will only be called before outputting
     * the root element.
     */
    @Override
    protected final void setRootNsContext(NamespaceContext ctxt)
    {
        mRootNsContext = ctxt;
        // Let's also figure out the default ns binding, if any:
        String defURI = ctxt.getNamespaceURI("");
        if (defURI != null && defURI.length() > 0) {
            mDefaultNsURI = defURI;
        }
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Helper classes:
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Simple key class used to represent two-piece (attribute) names;
     * first part being optional (URI), and second non-optional (local name).
     */
    final static class AttrName
        implements Comparable<AttrName>
    {
        final String mNsURI;
        final String mLocalName;

        /**
         * Let's cache the hash code, since although hash calculation is
         * fast, hash code is needed a lot as this is always used as a 
         * HashSet/TreeMap key.
         */
        final int mHashCode;

        public AttrName(String nsURI, String localName) {
            mNsURI = (nsURI == null) ? "" : nsURI;
            mLocalName = localName;
            mHashCode = mNsURI.hashCode() * 31 ^ mLocalName.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof AttrName)) {
                return false;
            }
            AttrName other = (AttrName) o;
            String otherLN = other.mLocalName;
            // Local names are shorter, more varying:
            if (otherLN != mLocalName && !otherLN.equals(mLocalName)) {
                return false;
            }
            String otherURI = other.mNsURI;
            return (otherURI == mNsURI || otherURI.equals(mNsURI));
        }

        @Override
        public String toString() {
            if (mNsURI.length() > 0) {
                return "{"+mNsURI + "} " +mLocalName;
            }
            return mLocalName;
        }

        @Override
        public int hashCode() {
            return mHashCode;
        }

        @Override
        public int compareTo(AttrName other) {
            // Let's first order by namespace:
            int result = mNsURI.compareTo(other.mNsURI);
            if (result == 0) {
                result = mLocalName.compareTo(other.mLocalName);
            }
            return result;
        }
    }
}
