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

import org.codehaus.stax2.validation.ValidationContext;
import org.codehaus.stax2.validation.XMLValidationSchema;
import org.codehaus.stax2.validation.XMLValidator;

import com.ctc.wstx.api.WstxInputProperties;
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
    /* 13-Dec-2005, TSa: Should use a more efficient Set/Map value
     *   for this in future -- specifically one that could use
     *   ns/local-name pairs without intermediate objects
     */
    private HashMap<Attribute, Attribute> mAttrMap;
    private ArrayList<Attribute> mAttrList;
    private AttributeCollector mAttributeCollector;
    private XMLValidator mValidator;

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
        mValidator = parent.mValidator;
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
        mValidator = parent.mValidator;
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
        mAttrList = null;
        mAttrMap = null;
        return new SimpleOutputElement(this, null, localName,
                                       mDefaultNsURI, mNsMapping);
    }

    /**
     * @return New head of the recycle pool
     */
    protected SimpleOutputElement reuseAsChild(SimpleOutputElement parent,
                                               String localName)
    {
        mAttrList = null;
        mAttrMap = null;
        SimpleOutputElement poolHead = mParent;
        relink(parent, null, localName, mDefaultNsURI);
        return poolHead;
    }

    protected SimpleOutputElement reuseAsChild(SimpleOutputElement parent,
                                               String prefix, String localName,
                                               String uri)
    {
        mAttrList = null;
        mAttrMap = null;
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
        mAttrList = null;
        mAttrMap = null;
        return new SimpleOutputElement(this, prefix, localName, uri, mNsMapping);
    }

    /**
     * Method called to temporarily link this instance to a pool, to
     * allow reusing of instances with the same reader.
     */
    protected void addToPool(SimpleOutputElement poolHead)
    {
        mParent = poolHead;
        mAttrList = null;
        mAttrMap = null;
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
        if (mPrefix != null) {
            return QNameCreator.create(mURI, mLocalName, mPrefix);
        } else {
            return new QName(mURI, mLocalName);
        }
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Public API, ns binding, checking
    ///////////////////////////////////////////////////////////////////////
     */

    public void addAttribute(String nsURI, String localName, String prefix, String value)
        throws XMLStreamException
    {
        if (mAttrList == null) {
            mAttrList = new ArrayList<Attribute>();
            mAttrMap = new HashMap<Attribute, Attribute>();
        }
        Attribute an = new Attribute(nsURI, localName, prefix, value, mAttrList.size());
        if (mAttrMap.put(an, an) != null) {
            throw new XMLStreamException("Duplicate attribute write for attribute '"+an+"'");
        }
        mAttrList.add(an);
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

    void setValidator(XMLValidator validator) {
        mValidator = validator;
        if (mParent != null) {
            mParent.setValidator(validator);
        }
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
    
    public int getAttributeCount() {
        return mAttrList == null ? 0 : mAttrList.size();
    }

    public String getAttributeLocalName(int index) {
        return mAttrList == null ? null : mAttrList.get(index).mLocalName;
    }

    public String getAttributeNamespace(int index) {
        return mAttrList == null ? null : mAttrList.get(index).mNsURI;
    }

    public String getAttributePrefix(int index) {
        return mAttrList == null ? null : mAttrList.get(index).mPrefix;
    }

    public String getAttributeValue(int index) {
        return mAttrList == null ? null : mAttrList.get(index).mValue;
    }

    public String getAttributeValue(String nsURI, String localName) {
        if (mAttrMap == null) {
            return null;
        }
        final Attribute attr = mAttrMap.get(new Attribute(nsURI, localName, null, null, -1));
        return attr == null ? null : attr.mValue;
    }

    public String getAttributeType(int index) {
         return (mValidator == null) ? WstxInputProperties.UNKNOWN_ATTR_TYPE :
             mValidator.getAttributeType(index);
    }

    public int findAttributeIndex(String nsURI, String localName) {
        if (mAttrMap == null) {
            return -1;
        }
        final Attribute attr = mAttrMap.get(new Attribute(nsURI, localName, null, null, -1));
        return attr == null ? -1 : attr.mIndex;
    }

    /**
     * Returns the {@link XMLValidator} set via {@link #setValidator(XMLValidator)} wrapped in a
     * {@link AttributeCollector} to be able to record the attribute values received through
     * {@link XMLValidator#validateAttribute(String, String, String, char[], int, int)}
     * and {@link XMLValidator#validateAttribute(String, String, String, String)}.
     *
     * @return an instance of {@link AttributeCollector}
     */
    XMLValidator getAttributeCollector() {
        AttributeCollector coll = mAttributeCollector;
        if (coll == null) {
            coll = mAttributeCollector = new AttributeCollector();
        }
        return coll;
    }

    public int validateElementStartAndAttributes() throws XMLStreamException {
        XMLValidator vld = mValidator;
        vld.validateElementStart(mLocalName, mURI, mPrefix);
        if (mAttrList != null && mAttrList.size() > 0) {
            for (Attribute attr : mAttrList) {
                vld.validateAttribute(attr.mLocalName, attr.mNsURI, attr.mPrefix, attr.mValue);
            }
        }
        return vld.validateElementAndAttributes();
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Helper classes:
    ///////////////////////////////////////////////////////////////////////
     */


    final class AttributeCollector extends XMLValidator {

        public AttributeCollector() {
            super();
        }

        @Override
        public String getSchemaType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public XMLValidationSchema getSchema() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void validateElementStart(String localName, String uri, String prefix) throws XMLStreamException {
            throw new UnsupportedOperationException();
        }

        @Override
        public String validateAttribute(String localName, String uri, String prefix, String value) throws XMLStreamException {
            addAttribute(uri, localName, prefix, value);
            return value;
        }

        @Override
        public String validateAttribute(String localName, String uri, String prefix, char[] valueChars, int valueStart,
                int valueEnd) throws XMLStreamException {
            final String value = new String(valueChars, valueStart, valueEnd - valueStart);
            addAttribute(uri, localName, prefix, value);
            return value;
        }

        @Override
        public int validateElementAndAttributes() throws XMLStreamException {
            throw new UnsupportedOperationException();
        }

        @Override
        public int validateElementEnd(String localName, String uri, String prefix) throws XMLStreamException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void validateText(String text, boolean lastTextSegment) throws XMLStreamException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void validateText(char[] cbuf, int textStart, int textEnd, boolean lastTextSegment) throws XMLStreamException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void validationCompleted(boolean eod) throws XMLStreamException {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getAttributeType(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getIdAttrIndex() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getNotationAttrIndex() {
            throw new UnsupportedOperationException();
        }

    }

    /**
     * Simple key class used to represent two-piece (attribute) names;
     * first part being optional (URI), and second non-optional (local name).
     */
    final static class Attribute
        implements Comparable<Attribute>
    {
        final String mNsURI;
        final String mLocalName;
        // mPrefix, mValue and mIndex are intentionally not a part of {@link #equals(Object)} and {@link #hashCode()}
        final String mPrefix;
        final String mValue;
        final int mIndex;

        /**
         * Let's cache the hash code, since although hash calculation is
         * fast, hash code is needed a lot as this is always used as a 
         * HashSet/TreeMap key.
         */
        final int mHashCode;

        public Attribute(String nsURI, String localName, String prefix, String value, int index) {
            mNsURI = (nsURI == null) ? "" : nsURI;
            mLocalName = localName;
            mHashCode = mNsURI.hashCode() * 31 ^ mLocalName.hashCode();
            mPrefix = prefix;
            mValue = value;
            mIndex = index;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof Attribute)) {
                return false;
            }
            Attribute other = (Attribute) o;
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
        public int compareTo(Attribute other) {
            // Let's first order by namespace:
            int result = mNsURI.compareTo(other.mNsURI);
            if (result == 0) {
                result = mLocalName.compareTo(other.mLocalName);
            }
            return result;
        }
    }
}
