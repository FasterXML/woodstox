/* Woodstox XML processor
 *
 * Copyright (c) 2004- Tatu Saloranta, tatu.saloranta@iki.fi
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

package com.ctc.wstx.sr;

import java.io.IOException;
import java.util.Arrays;

import javax.xml.XMLConstants;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import javax.xml.namespace.QName;

import org.codehaus.stax2.ri.typed.CharArrayBase64Decoder;
import org.codehaus.stax2.ri.typed.ValueDecoderFactory;
import org.codehaus.stax2.typed.Base64Variant;
import org.codehaus.stax2.typed.TypedArrayDecoder;
import org.codehaus.stax2.typed.TypedValueDecoder;
import org.codehaus.stax2.typed.TypedXMLStreamException;
import org.codehaus.stax2.validation.XMLValidator;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.cfg.ErrorConsts;
import com.ctc.wstx.sw.XmlWriter;
import com.ctc.wstx.util.*;

/**
 * Shared base class that defines API stream reader uses to communicate
 * with the attribute collector implementation, independent of whether it's
 * operating in namespace-aware or non-namespace modes.
 * Collector class is used to build up attribute lists; for the most part
 * will just hold references to few specialized {@link TextBuilder}s that
 * are used to create efficient semi-shared value Strings.
 */
public final class AttributeCollector
{
    final static int INT_SPACE = 0x0020;

    /**
     * Threshold value that indicates minimum length for lists instances
     * that need a Map structure, for fast attribute access by fully-qualified
     * name.
     */
    protected final static int LONG_ATTR_LIST_LEN = 4;

    /**
     * Expected typical maximum number of attributes for any element;
     * chosen to minimize need to resize, while trying not to waste space.
     * Dynamically grown; better not to set too high to avoid excessive
     * overhead for small attribute-less documents.
     */
    protected final static int EXP_ATTR_COUNT = 12;

    protected final static int EXP_NS_COUNT = 6;

    /**
     * This value is used to indicate that we shouldn't keep track
     * of index of xml:id attribute -- generally done when Xml:id
     * support is disabled
     */
    protected final static int XMLID_IX_DISABLED = -2;

    protected final static int XMLID_IX_NONE = -1;

    protected final static InternCache sInternCache = InternCache.getInstance();

    /*
    ///////////////////////////////////////////////////////////
    // Configuration
    ///////////////////////////////////////////////////////////
     */

    // // Settings for matching Xml:id attribute

    final String mXmlIdPrefix;
    final String mXmlIdLocalName;

    /*
    ///////////////////////////////////////////////////////////
    // Collected attribute (incl namespace attrs) information:
    ///////////////////////////////////////////////////////////
     */

    /**
     * Array of attributes collected for this element.
     */
    protected Attribute[] mAttributes;

    /**
     * Actual number of attributes collected, including attributes
     * added via default values.
     */
    protected int mAttrCount;

    /**
     * Number of attribute values actually parsed, not including
     * ones created via default value expansion. Equal to or less than
     * {@link #mAttrCount}.
     */
    protected int mNonDefCount;

    /**
     * Array of namespace declaration attributes collected for this element;
     * not used in non-namespace-aware mode
     */
    protected Attribute[] mNamespaces;

    /**
     * Number of valid namespace declarations in {@link #mNamespaces}.
     */
    protected int mNsCount;

    /**
     * Flag to indicate whether the default namespace has already been declared
     * for the current element.
     */
    protected boolean mDefaultNsDeclared = false;

    /**
     * Index of "xml:id" attribute, if one exists for the current
     * element; {@link #XMLID_IX_NONE} if none.
     */
    protected int mXmlIdAttrIndex;

    /*
    ///////////////////////////////////////////////////////////
    // Attribute (and ns) value builders
    ///////////////////////////////////////////////////////////
     */

    /**
     * TextBuilder into which values of all attributes are appended
     * to, including default valued ones (defaults are added after
     * explicit ones).
     * Constructed lazily, if and when needed (not needed
     * for short attribute-less docs)
     */
    protected TextBuilder mValueBuilder = null;

    /**
     * TextBuilder into which values of namespace URIs are added (including
     * URI for the default namespace, if one defined).
     */
    private final TextBuilder mNamespaceBuilder = new TextBuilder(EXP_NS_COUNT);

    /*
    //////////////////////////////////////////////////////////////
    // Information that defines "Map-like" data structure used for
    // quick access to attribute values by fully-qualified name
    //////////////////////////////////////////////////////////////
     */

    /**
     * Encoding of a data structure that contains mapping from
     * attribute names to attribute index in main attribute name arrays.
     *<p>
     * Data structure contains two separate areas; main hash area (with
     * size <code>mAttrHashSize</code>), and remaining spillover area
     * that follows hash area up until (but not including)
     * <code>mAttrSpillEnd</code> index.
     * Main hash area only contains indexes (index+1; 0 signifying empty slot)
     * to actual attributes; spillover area has both hash and index for
     * any spilled entry. Spilled entries are simply stored in order
     * added, and need to be searched using linear search. In case of both
     * primary hash hits and spills, eventual comparison with the local
     * name needs to be done with actual name array.
     */
    protected int[] mAttrMap = null;

    /**
     * Size of hash area in <code>mAttrMap</code>; generally at least 20%
     * more than number of attributes (<code>mAttrCount</code>).
     */
    protected int mAttrHashSize;

    /**
     * Pointer to int slot right after last spill entr, in
     * <code>mAttrMap</code> array.
     */
    protected int mAttrSpillEnd;
    
    protected int mMaxAttributesPerElement;
    protected int mMaxAttributeSize;

    /*
    ///////////////////////////////////////////////
    // Life-cycle:
    ///////////////////////////////////////////////
     */

    protected AttributeCollector(ReaderConfig cfg, boolean nsAware)
    {
        mXmlIdAttrIndex = cfg.willDoXmlIdTyping() ? XMLID_IX_NONE : XMLID_IX_DISABLED;
        if (nsAware) {
            mXmlIdPrefix = "xml";
            mXmlIdLocalName = "id";
        } else {
            mXmlIdPrefix = null;
            mXmlIdLocalName = "xml:id";
        }
        mMaxAttributesPerElement = cfg.getMaxAttributesPerElement();
        mMaxAttributeSize = cfg.getMaxAttributeSize();
    }

    /**
     * Method called to allow reusing of collector, usually right before
     * starting collecting attributes for a new start tag.
     */
    /**
     * Method called to allow reusing of collector, usually right before
     * starting collecting attributes for a new start tag.
     *<p>
     * Note: public only so that it can be called by unit tests.
     */
    public void reset()
    {
        if (mNsCount > 0) {
            mNamespaceBuilder.reset();
            mDefaultNsDeclared = false;
            mNsCount = 0;
        }

        /* No need to clear attr name, or NS prefix Strings; they are
         * canonicalized and will be referenced by symbol table in any
         * case... so we can save trouble of cleaning them up. This Object
         * will get GC'ed soon enough, after parser itself gets disposed of.
         */
        if (mAttrCount > 0) {
            mValueBuilder.reset();
            mAttrCount = 0;
            if (mXmlIdAttrIndex >= 0) {
                mXmlIdAttrIndex = XMLID_IX_NONE;
            }
        }
        /* Note: attribute values will be cleared later on, when validating
         * namespaces. This so that we know how much to clean up; and
         * occasionally can also just avoid clean up (when resizing)
         */
    }

    /**
     * Method that can be called to force space normalization (remove
     * leading/trailing spaces, replace non-spaces white space with
     * spaces, collapse spaces to one) on specified attribute.
     * Currently called by {@link InputElementStack} to force
     * normalization of Xml:id attribute
     */
    public void normalizeSpacesInValue(int index)
    {
        // StringUtil has a method, but it works on char arrays...
        char[] attrCB = mValueBuilder.getCharBuffer();
        String normValue = StringUtil.normalizeSpaces
            (attrCB, getValueStartOffset(index), getValueStartOffset(index+1));
        if (normValue != null) {
            mAttributes[index].setValue(normValue);
        }
    }

    /*
    ///////////////////////////////////////////////
    // Public accesors (for stream reader)
    ///////////////////////////////////////////////
     */

    /**
     * @return Number of namespace declarations collected, including
     *   possible default namespace declaration
     */
    protected int getNsCount() {
        return mNsCount;
    }

    public boolean hasDefaultNs() {
        return mDefaultNsDeclared;
    }

    // // // Direct access to attribute/NS prefixes/localnames/URI

    public final int getCount() {
        return mAttrCount;
    }

    /**
     * @return Number of attributes that were explicitly specified; may
     *  be less than the total count due to attributes created using
     *  attribute default values
     */
    public int getSpecifiedCount() {
        return mNonDefCount;
    }

    public String getNsPrefix(int index) {
        if (index < 0 || index >= mNsCount) {
            throwIndex(index);
        }
        // for NS decls, local name is stored in prefix
        return mNamespaces[index].mLocalName;
    }

    public String getNsURI(int index) {
        if (index < 0 || index >= mNsCount) {
            throwIndex(index);
        }
        return mNamespaces[index].mNamespaceURI;
    }

    // // // Direct access to attribute/NS prefixes/localnames/URI

    public String getPrefix(int index) {
        if (index < 0 || index >= mAttrCount) {
            throwIndex(index);
        }
        return mAttributes[index].mPrefix;
    }

    public String getLocalName(int index) {
        if (index < 0 || index >= mAttrCount) {
            throwIndex(index);
        }
        return mAttributes[index].mLocalName;
    }

    public String getURI(int index) {
        if (index < 0 || index >= mAttrCount) {
            throwIndex(index);
        }
        return mAttributes[index].mNamespaceURI;
    }

    public QName getQName(int index) {
        if (index < 0 || index >= mAttrCount) {
            throwIndex(index);
        }
        return mAttributes[index].getQName();
    }

    /**
     *<p>
     * Note: the main reason this method is defined at this level, and
     * made final, is performance. JIT may be able to fully inline this
     * method, even when reference is via this base class. This is important
     * since this is likely to be the most often called method of the
     * collector instances.
     */
    public final String getValue(int index)
    {
        if (index < 0 || index >= mAttrCount) {
            throwIndex(index);
        }
        String full = mValueBuilder.getAllValues();
        Attribute attr = mAttributes[index];
        ++index;
        if (index < mAttrCount) { // not last
            int endOffset = mAttributes[index].mValueStartOffset;
            return attr.getValue(full, endOffset);
        }
        // last can be optimized bit more:
        return attr.getValue(full);
    }

    public String getValue(String nsURI, String localName)
    {
        // Primary hit?
        int hashSize = mAttrHashSize;
        if (hashSize == 0) { // sanity check, for 'no attributes'
            return null;
        }
        int hash = localName.hashCode();
        if (nsURI != null) {
            if (nsURI.length() == 0) {
                nsURI = null;
            } else {
                hash ^= nsURI.hashCode();
            }
        }
        int ix = mAttrMap[hash & (hashSize-1)];
        if (ix == 0) { // nothing in here; no spills either
            return null;
        }
        --ix;

        // Is primary candidate match?
        if (mAttributes[ix].hasQName(nsURI, localName)) {
            return getValue(ix);
        }

        // Nope, need to traverse spill list, which has 2 entries for
        // each spilled attribute id; first for hash value, second index.
        for (int i = hashSize, len = mAttrSpillEnd; i < len; i += 2) {
            if (mAttrMap[i] != hash) {
                continue;
            }
            // Note: spill indexes are not off-by-one, since there's no need
            // to mask 0
            ix = mAttrMap[i+1];
            if (mAttributes[ix].hasQName(nsURI, localName)) {
                return getValue(ix);
            }
        }

        return null;
    }

    /**
     * Specialized version in which namespace information is completely ignored.
     *
     * @since 5.2
     */
    public String getValueByLocalName(String localName)
    {
        // NOTE: can't use hashing, must do linear scan
        
        switch (mAttrCount) {
        case 4:
            if (mAttributes[0].hasLocalName(localName)) return getValue(0);
            if (mAttributes[1].hasLocalName(localName)) return getValue(1);
            if (mAttributes[2].hasLocalName(localName)) return getValue(2);
            if (mAttributes[3].hasLocalName(localName)) return getValue(3);
            return null;
        case 3:
            if (mAttributes[0].hasLocalName(localName)) return getValue(0);
            if (mAttributes[1].hasLocalName(localName)) return getValue(1);
            if (mAttributes[2].hasLocalName(localName)) return getValue(2);
            return null;
        case 2:
            if (mAttributes[0].hasLocalName(localName)) return getValue(0);
            if (mAttributes[1].hasLocalName(localName)) return getValue(1);
            return null;
        case 1:
            if (mAttributes[0].hasLocalName(localName)) return getValue(0);
            return null;
        case 0:
            return null;
        default:
            for (int i = 0, end = mAttrCount; i < end; ++i) {
                if (mAttributes[i].hasLocalName(localName)) {
                    return getValue(i);
                }
            }
            return null;
        }
    }
    
    public int getMaxAttributesPerElement() {
        return mMaxAttributesPerElement;
    }

    public void setMaxAttributesPerElement(int maxAttributesPerElement) {
        this.mMaxAttributesPerElement = maxAttributesPerElement;
    }

    public int findIndex(String localName) {
        return findIndex(null, localName);
    }

    public int findIndex(String nsURI, String localName)
    {
        /* Note: most of the code is from getValue().. could refactor
         * code, performance is bit of concern (one more method call
         * if index access was separate).
         * See comments on that method, for logics.
         */

        // Primary hit?
        int hashSize = mAttrHashSize;
        if (hashSize == 0) { // sanity check, for 'no attributes'
            return -1;
        }
        int hash = localName.hashCode();
        if (nsURI != null) {
            if (nsURI.length() == 0) {
                nsURI = null;
            } else {
                hash ^= nsURI.hashCode();
            }
        }
        int ix = mAttrMap[hash & (hashSize-1)];
        if (ix == 0) { // nothing in here; no spills either
            return -1;
        }
        --ix;

        // Is primary candidate match?
        if (mAttributes[ix].hasQName(nsURI, localName)) {
            return ix;
        }

        /* Nope, need to traverse spill list, which has 2 entries for
         * each spilled attribute id; first for hash value, second index.
         */
        for (int i = hashSize, len = mAttrSpillEnd; i < len; i += 2) {
            if (mAttrMap[i] != hash) {
                continue;
            }
            /* Note: spill indexes are not off-by-one, since there's no need
             * to mask 0
             */
            ix = mAttrMap[i+1];
            if (mAttributes[ix].hasQName(nsURI, localName)) {
                return ix;
            }
        }
        return -1;
    }

    public final boolean isSpecified(int index) {
        return (index < mNonDefCount);
    }

    public final int getXmlIdAttrIndex() {
        return mXmlIdAttrIndex;
    }

    /*
    //////////////////////////////////////////////////////
    // Type-safe accessors to support TypedXMLStreamReader
    //////////////////////////////////////////////////////
     */

    /**
     * Method called to decode the whole attribute value as a single
     * typed value.
     * Decoding is done using the decoder provided.
     */
    public final void decodeValue(int index, TypedValueDecoder tvd)
        throws IllegalArgumentException
    {
        if (index < 0 || index >= mAttrCount) {
            throwIndex(index);
        }
        /* Should be faster to pass the char array even if we might
         * have a String
         */
        // Either way, need to trim before passing:
        char[] buf = mValueBuilder.getCharBuffer();
        int start = mAttributes[index].mValueStartOffset;
        int end = getValueStartOffset(index+1);

        while (true) {
            if (start >= end) {
                tvd.handleEmptyValue();
                return;
            }
            if (!StringUtil.isSpace(buf[start])) {
                break;
            }
            ++start;
        }
        // Trailing space?
        while (--end > start && StringUtil.isSpace(buf[end])) { }
        tvd.decode(buf, start, end+1);
    }

    /**
     * Method called to decode the attribute value that consists of
     * zero or more space-separated tokens.
     * Decoding is done using the decoder provided.
     * @return Number of tokens decoded
     */
    public final int decodeValues(int index, TypedArrayDecoder tad,
                                  InputProblemReporter rep)
        throws XMLStreamException
    {
        if (index < 0 || index >= mAttrCount) {
            throwIndex(index);
        }
        // Char[] faster than String... and no need to trim here:
        return decodeValues(tad, rep,
                            mValueBuilder.getCharBuffer(),
                            mAttributes[index].mValueStartOffset,
                            getValueStartOffset(index+1));
    }

    public final byte[] decodeBinary(int index, Base64Variant v, CharArrayBase64Decoder dec,
                                     InputProblemReporter rep)
        throws XMLStreamException
    {
        if (index < 0 || index >= mAttrCount) {
            throwIndex(index);
        }
        /* No point in trying to use String representation, even if one
         * available, faster to process from char[]
         */
        Attribute attr = mAttributes[index];
        char[] cbuf = mValueBuilder.getCharBuffer();
        int start = attr.mValueStartOffset;
        int end = getValueStartOffset(index+1);
        int len = end-start;
        dec.init(v, true, cbuf, start, len, null);
        try {
            return dec.decodeCompletely();
        } catch (IllegalArgumentException iae) {
            // Need to convert to a checked stream exception
            String lexical = new String(cbuf, start, len);
            throw new TypedXMLStreamException(lexical, iae.getMessage(), rep.getLocation(), iae);
        }
    }

    private final static int decodeValues(TypedArrayDecoder tad,
                                          InputProblemReporter rep,
                                          final char[] buf, int ptr, final int end)
        throws XMLStreamException
    {
        int start = ptr;
        int count = 0;

        try {
            decode_loop:
            while (ptr < end) {
                // First, any space to skip?
                while (buf[ptr] <= INT_SPACE) {
                    if (++ptr >= end) {
                        break decode_loop;
                    }
                }
                // Then let's figure out non-space char (token)
                start = ptr;
                ++ptr;
                while (ptr < end && buf[ptr] > INT_SPACE) {
                    ++ptr;
                }
                int tokenEnd = ptr;
                ++ptr; // to skip trailing space (or, beyond end)
                // Ok, decode... any more room?
                ++count;
                if (tad.decodeValue(buf, start, tokenEnd)) {
                    if (!checkExpand(tad)) {
                        break;
                    }
                }
            }
        } catch (IllegalArgumentException iae) {
            // Need to convert to a checked stream exception
            Location loc = rep.getLocation();
            String lexical = new String(buf, start, (ptr-start));
            throw new TypedXMLStreamException(lexical, iae.getMessage(), loc, iae);
        }
        return count;
    }

    /**
     * Internal method used to see if we can expand the buffer that
     * the array decoder has. Bit messy, but simpler than having
     * separately typed instances; and called rarely so that performance
     * downside of instanceof is irrelevant.
     */
    private final static boolean checkExpand(TypedArrayDecoder tad)
    {
        if (tad instanceof ValueDecoderFactory.BaseArrayDecoder) {
            ((ValueDecoderFactory.BaseArrayDecoder) tad).expand();
            return true;
        }
        return false;
    }

    /*
    ///////////////////////////////////////////////
    // Accessors for accessing helper objects
    ///////////////////////////////////////////////
     */

    /**
     * Method for getting start pointer within shared value buffer
     * for given attribute. It is also the same as end pointer
     * for preceding attribute, if any.
     */
    protected int getValueStartOffset(int index)
    {
        if (index < mAttrCount) {
            return mAttributes[index].mValueStartOffset;
        }
        return mValueBuilder.getCharSize();
    }

    protected char[] getSharedValueBuffer()
    {
        return mValueBuilder.getCharBuffer();
    }

    /**
     * Method called to resolve and initialize specified collected
     * namespace declaration
     *
     * @return Attribute that contains specified namespace declaration
     */
    protected Attribute resolveNamespaceDecl(int index, boolean internURI)
    {
        Attribute ns = mNamespaces[index];
        String full = mNamespaceBuilder.getAllValues();
        String uri;

        if (mNsCount == 0) {
            uri = full;
        } else {
            ++index;
            if (index < mNsCount) { // not last
                int endOffset = mNamespaces[index].mValueStartOffset;
                uri = ns.getValue(full, endOffset);
            } else { // is last
                uri = ns.getValue(full);
            }
        }
        if (internURI && uri.length() > 0) {
            uri = sInternCache.intern(uri);
        }
        ns.mNamespaceURI = uri;
        return ns;
    }

    /**
     * Method needed by event creating code, to build a non-transient
     * attribute container, to use with XMLEvent objects (specifically
     * implementation of StartElement event).
     */
    public ElemAttrs buildAttrOb()
    {
        int count = mAttrCount;
        if (count == 0) {
            return null;
        }
        // If we have actual attributes, let's first just create the
        // raw array that has all attribute information:
        String[] raw = new String[count << 2];
        for (int i = 0; i < count; ++i) {
            Attribute attr = mAttributes[i];
            int ix = (i << 2);
            raw[ix] = attr.mLocalName;
            raw[ix+1] = attr.mNamespaceURI;
            raw[ix+2] = attr.mPrefix;
            raw[ix+3] = getValue(i);
        }

        // Do we have a "short" list?
        if (count < LONG_ATTR_LIST_LEN) {
            return new ElemAttrs(raw, mNonDefCount);
        }

        // Ok, nope; we need to also pass the Map information...
        /* 02-Feb-2009, TSa: Must make a copy of the Map array now,
         *   otherwise could get overwritten.
         */
        int amapLen = mAttrMap.length;
        int[] amap = new int[amapLen];
        // TODO: JDK 1.6 has Arrays.copyOf(), should use with Woodstox 6
        System.arraycopy(mAttrMap, 0, amap, 0, amapLen);
        return new ElemAttrs(raw, mNonDefCount,
                             amap, mAttrHashSize, mAttrSpillEnd);
    }

    protected void validateAttribute(int index, XMLValidator vld)
        throws XMLStreamException
    {
        Attribute attr = mAttributes[index];
        String normValue = vld.validateAttribute
            (attr.mLocalName, attr.mNamespaceURI, attr.mPrefix,
             mValueBuilder.getCharBuffer(),
             getValueStartOffset(index),
             getValueStartOffset(index+1));

        if (normValue != null) {
            attr.setValue(normValue);
        }
    }

    /*
    ///////////////////////////////////////////////
    // Attribute, namespace decl building
    ///////////////////////////////////////////////
     */

    /**
     * Low-level accessor method that attribute validation code may call
     * for certain types of attributes; generally only for id and idref/idrefs
     * attributes. It returns the underlying 'raw' attribute value buffer
     * for direct access.
     */
    public final TextBuilder getAttrBuilder(String attrPrefix, String attrLocalName) throws XMLStreamException
    {
        /* Ok: we have parsed prefixed-name of a regular
         * attribute. So let's initialize the instance...
         */
        if (mAttrCount == 0) {
            if (mAttributes == null) {
                allocBuffers();
            }
            mAttributes[0] = new Attribute(attrPrefix, attrLocalName, 0);
        } else {
            int valueStart = mValueBuilder.getCharSize();
            if (mAttrCount >= mAttributes.length) {
                if ((mAttrCount + mNsCount) >= mMaxAttributesPerElement) {
                    throw new XMLStreamException("Attribute limit ("+mMaxAttributesPerElement+") exceeded");
                }
                mAttributes = (Attribute[]) DataUtil.growArrayBy50Pct(mAttributes);
            }
            Attribute curr = mAttributes[mAttrCount];
            if (curr == null) {
                mAttributes[mAttrCount] = new Attribute(attrPrefix, attrLocalName, valueStart);
            } else {
                curr.reset(attrPrefix, attrLocalName, valueStart);
            }
        }
        ++mAttrCount;
        // 25-Sep-2006, TSa: Need to keep track of xml:id attribute?
        if (attrLocalName == mXmlIdLocalName) {
            if (attrPrefix == mXmlIdPrefix) {
                if (mXmlIdAttrIndex != XMLID_IX_DISABLED) {
                    mXmlIdAttrIndex = mAttrCount - 1;
                }
            }
        }
        /* Can't yet create attribute map by name, since we only know
         * name prefix, not necessarily matching URI.
         */ 
        return mValueBuilder;
    }

    /**
     * Method called by validator to insert an attribute that has a default
     * value and wasn't yet included in collector's attribute set.
     *
     * @return Index of the newly added attribute, if added; -1 to indicate
     *    this was a duplicate
     */
    public int addDefaultAttribute(String localName, String uri, String prefix,
                                   String value) throws XMLStreamException
    {
        int attrIndex = mAttrCount;
        if (attrIndex < 1) {
            /* had no explicit attributes... better initialize now, then.
             * Let's just use hash area of 4, and 
             */
            initHashArea();
        }

        /* Ok, first, since we do want to verify that we can not accidentally
         * add duplicates, let's first try to add entry to Map, since that
         * will catch dups.
         */
        int hash = localName.hashCode();
        if (uri != null && uri.length() > 0) {
            hash ^= uri.hashCode();
        }
        int index = hash & (mAttrHashSize - 1);
        int[] map = mAttrMap;
        if (map[index] == 0) { // whoa, have room...
            map[index] = attrIndex+1; // add 1 to get 1-based index (0 is empty marker)
        } else { // nah, collision...
            int currIndex = map[index]-1; // Index of primary collision entry
            int spillIndex = mAttrSpillEnd;
            map = spillAttr(uri, localName, map, currIndex, spillIndex,
                            hash, mAttrHashSize);
            if (map == null) { // dup!
                return -1; // could return negation (-(index+1)) of the prev index?
            }
            map[++spillIndex] = attrIndex; // no need to specifically avoid 0
            mAttrMap = map;
            mAttrSpillEnd = ++spillIndex;
        }

        /* Can reuse code; while we don't really need the builder,
         * we need to add/reset attribute
         */
        getAttrBuilder(prefix, localName);
        Attribute attr = mAttributes[mAttrCount-1];
        attr.mNamespaceURI = uri;
        attr.setValue(value);
        // attribute count has been updated; index is one less than count
        return (mAttrCount-1);
    }

    /**
     * Low-level mutator method that attribute validation code may call
     * for certain types of attributes, when it wants to handle the whole
     * validation and normalization process by itself. It is generally
     * only called for id and idref/idrefs attributes, as those values
     * are usually normalized.
     */
    public final void setNormalizedValue(int index, String value)
    {
        mAttributes[index].setValue(value);
    }

    /**
     * @return null if the default namespace URI has been already declared
     *   for the current element; TextBuilder to add URI to if not.
     */
    public TextBuilder getDefaultNsBuilder() throws XMLStreamException
    {
        if (mDefaultNsDeclared) {
            return null;
        }
        mDefaultNsDeclared = true;
        return getNsBuilder(null);
    }

    /**
     * @return null if prefix has been already declared; TextBuilder to
     *   add value to if not.
     */
    public TextBuilder getNsBuilder(String prefix) throws XMLStreamException
    {
        // first: must verify that it's not a dup
        if (mNsCount == 0) {
            if (mNamespaces == null) {
                mNamespaces = new Attribute[EXP_NS_COUNT];
            }
            mNamespaces[0] = new Attribute(null, prefix, 0);
        } else {
            int len = mNsCount;
            /* Ok: must ensure that there are no duplicate namespace
             * declarations (ie. decls with same prefix being bound)
             */
            if (prefix != null) { // null == default ns
                for (int i = 0; i < len; ++i) {
                    // note: for ns decls, bound prefix is in 'local name'
                    if (prefix == mNamespaces[i].mLocalName) {
                        return null;
                    }
                }
            }
            if (len >= mNamespaces.length) {
                if ((mAttrCount + mNsCount) >= mMaxAttributesPerElement) {
                    throw new XMLStreamException("Attribute limit ("+mMaxAttributesPerElement+") exceeded");
                }
                mNamespaces = (Attribute[]) DataUtil.growArrayBy50Pct(mNamespaces);
            }
            int uriStart = mNamespaceBuilder.getCharSize();
            Attribute curr = mNamespaces[len];
            if (curr == null) {
                mNamespaces[len] = new Attribute(null, prefix, uriStart);
            } else {
                curr.reset(null, prefix, uriStart);
            }
        }
        ++mNsCount;
        return mNamespaceBuilder;
    }

    /**
     * Method called to resolve namespace URIs from attribute prefixes.
     *<p>
     * Note: public only so that it can be called by unit tests.
     *
     * @param rep Reporter to use for reporting well-formedness problems
     * @param ns Namespace prefix/URI mappings active for this element
     *
     * @return Index of xml:id attribute, if any, -1 if not
     */
    public int resolveNamespaces(InputProblemReporter rep, StringVector ns)
        throws XMLStreamException
    {
        int attrCount = mAttrCount;

        /* Let's now set number of 'real' attributes, to allow figuring
         * out number of attributes created via default value expansion
         */
        mNonDefCount = attrCount;

        if (attrCount < 1) {
            // Checked if doing access by FQN:
            mAttrHashSize = mAttrSpillEnd = 0;
            // And let's just bail out, too...
            return mXmlIdAttrIndex;
        }
        for (int i = 0; i < attrCount; ++i) {
            Attribute attr = mAttributes[i];
            String prefix = attr.mPrefix;
            // Attributes' ns URI is null after reset, so can skip setting "no namespace"
            if (prefix != null) {
                if (prefix == "xml") {
                    attr.mNamespaceURI = XMLConstants.XML_NS_URI;
                } else {
                    String uri = ns.findLastFromMap(prefix);
                    if (uri == null) {
                        rep.throwParseError(ErrorConsts.ERR_NS_UNDECLARED_FOR_ATTR, 
                                            prefix, attr.mLocalName);
                    }
                    attr.mNamespaceURI = uri;
                }
            }
        }

        /* Ok, finally, let's create attribute map, to allow efficient
         * access by prefix+localname combination. Could do it on-demand,
         * but this way we can check for duplicates right away.
         */
        int[] map = mAttrMap;

        /* What's minimum size to contain at most 80% full hash area,
         * plus 1/8 spill area (12.5% spilled entries, two ints each)?
         */
        int hashCount = 4;
        {
            int min = attrCount + (attrCount >> 2); // == 80% fill rate
            /* Need to get 2^N size that can contain all elements, with
             * 80% fill rate
             */
            while (hashCount < min) {
                hashCount += hashCount; // 2x
            }
            // And then add the spill area
            mAttrHashSize = hashCount;
            min = hashCount + (hashCount >> 4); // 12.5 x 2 ints
            if (map == null || map.length < min) {
                map = new int[min];
            } else {
                /* Need to clear old hash entries (if any). But note that
                 * spilled entries we can leave alone -- they are just ints,
                 * and get overwritten if and as needed
                 */
                Arrays.fill(map, 0, hashCount, 0);
            }
        }

        {
            int mask = hashCount-1;
            int spillIndex = hashCount;

            // Ok, array's fine, let's hash 'em in!
            for (int i = 0; i < attrCount; ++i) {
                Attribute attr = mAttributes[i];
                String name = attr.mLocalName;
                int hash = name.hashCode();
                String uri = attr.mNamespaceURI;
                if (uri != null) {
                    hash ^= uri.hashCode();
                }
                int index = hash & mask;
                // Hash slot available?
                if (map[index] == 0) {
                    map[index] = i+1; // since 0 is marker
                } else {
                    int currIndex = map[index]-1;
                    /* nope, need to spill; let's extract most of that code to
                     * a separate method for clarity (and maybe it'll be
                     * easier to inline by JVM too)
                     */
                    map = spillAttr(uri, name, map, currIndex, spillIndex,
                                    hash, hashCount);
                    if (map == null) {
                        throwDupAttr(rep, currIndex);
                        // never returns here...
                    } else { // let's use else to keep FindBugs happy
                        map[++spillIndex] = i; // no need to specifically avoid 0
                        ++spillIndex;
                    }
                }
            }
            mAttrSpillEnd = spillIndex;
        }
        mAttrMap = map;
        return mXmlIdAttrIndex;
    }

    /*
    ///////////////////////////////////////////////
    // Package/core methods:
    ///////////////////////////////////////////////
     */

    protected void throwIndex(int index) {
        throw new IllegalArgumentException("Invalid index "+index+"; current element has only "+getCount()+" attributes");
    }

    /**
     * @deprecated Since 5.0.3
     */
    @Deprecated
    public void writeAttribute(int index, XmlWriter xw) throws IOException, XMLStreamException {
        writeAttribute(index, xw, null);
    }
    
    /**
     * Method that basically serializes the specified (read-in) attribute
     * using Writers provided. Serialization is done by
     * writing out (fully-qualified) name
     * of the attribute, followed by the equals sign and quoted value.
     */
    public void writeAttribute(int index, XmlWriter xw, XMLValidator validator)
            throws IOException, XMLStreamException
    {
        // Note: here we assume index checks have been done by caller
        Attribute attr = mAttributes[index];
        String ln = attr.mLocalName;
        String prefix = attr.mPrefix;
        final String value = getValue(index);
        if (prefix == null || prefix.length() == 0) {
            xw.writeAttribute(ln, value);
        } else {
            xw.writeAttribute(prefix, ln, value);
        }
        if (validator != null) {
            validator.validateAttribute(ln, attr.mNamespaceURI, prefix, value);
        }
    }

    /**
     * Method called to initialize buffers that need not be immediately
     * initialized
     */
    protected final void allocBuffers()
    {
        if (mAttributes == null) {
            mAttributes = new Attribute[8];
        }
        if (mValueBuilder == null) {
            mValueBuilder = new TextBuilder(EXP_ATTR_COUNT);
        }
    }

    /*
    ///////////////////////////////////////////////
    // Internal methods:
    ///////////////////////////////////////////////
     */

    /**
     * @return Null, if attribute is a duplicate (to indicate error);
     *    map itself, or resized version, otherwise.
     */
    private int[] spillAttr(String uri, String name,
                            int[] map, int currIndex, int spillIndex,
                            int hash, int hashCount)
    {
        // Do we have a dup with primary entry?
        /* Can do equality comp for local name, as they
         * are always canonicalized:
         */
        Attribute oldAttr = mAttributes[currIndex];
        if (oldAttr.mLocalName == name) {
            // URIs may or may not be interned though:
            String currURI = oldAttr.mNamespaceURI;
            if (currURI == uri || (currURI != null && currURI.equals(uri))) {
                return null;
            }
        }

        /* Is there room to spill into? (need to 2 int spaces; one for hash,
         * the other for index)
         */
        if ((spillIndex + 1)>= map.length) {
            // Let's just add room for 4 spills...
            map = DataUtil.growArrayBy(map, 8);
        }
        // Let's first ensure we aren't adding a dup:
        for (int j = hashCount; j < spillIndex; j += 2) {
            if (map[j] == hash) {
                currIndex = map[j+1];
                Attribute attr = mAttributes[currIndex];
                if (attr.mLocalName == name) {
                    String currURI = attr.mNamespaceURI;
                    if (currURI == uri || (currURI != null && currURI.equals(uri))) {
                        return null;
                    }
                }
            }
        }
        map[spillIndex] = hash;
        return map;
    }

    /**
     * Method called to ensure hash area will be properly set up in
     * cases where initially no room was needed, but default attribute(s)
     * is being added.
     */
    private void initHashArea()
    {
        /* Let's use small hash area of size 4, and one spill; don't
         * want too big (need to clear up room), nor too small (only
         * collisions)
         */
        mAttrHashSize = mAttrSpillEnd = 4;
        if (mAttrMap == null || mAttrMap.length < mAttrHashSize) {
            mAttrMap = new int[mAttrHashSize+1];
        }
        mAttrMap[0] =  mAttrMap[1] = mAttrMap[2] = mAttrMap[3] = 0;
        allocBuffers();
    }

    /**
     * Method that can be used to get the specified attribute value,
     * by getting it written using Writer passed in. Can potentially
     * save one String allocation, since no (temporary) Strings need
     * to be created.
     */
    /*
    protected final void writeValue(int index, Writer w)
        throws IOException
    {
        mValueBuilder.getEntry(index, w);
    }
    */

    protected void throwDupAttr(InputProblemReporter rep, int index)
        throws XMLStreamException
    {
        rep.throwParseError("Duplicate attribute '"+getQName(index)+"'.");
    }
}
