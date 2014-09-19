package com.ctc.wstx.sr;

import javax.xml.namespace.QName;

/**
 * Container class that is constructed with enough raw attribute information,
 * to be able to lazily construct full attribute objects, to be accessed
 * via Iterator, or fully-qualified name.
 *<p>
 * Implementation note: code for using Map-like structure is unfortunately
 * cut'n pasted from {@link AttributeCollector}. Problem
 * with refactoring is that it's 90% the same code, but not 100%.
 *<p>
 * Although instances of this class are constructed by stream readers,
 * it is actually used by element event objects.
 */
public final class ElemAttrs
{
    //private final static int OFFSET_LOCAL_NAME = 0;
    private final static int OFFSET_NS_URI = 1;
    //private final static int OFFSET_NS_PREFIX = 2;
    //private final static int OFFSET_VALUE = 3;
    
    /**
     * Array that contains 4 Strings for each attribute;
     * localName, URI, prefix, value. Can be used to lazily construct
     * structure(s) needed to return Iterator for accessing all
     * attributes.
     */
    private final String[] mRawAttrs;

    /**
     * Raw offset (in <code>mRawAttrs</code>) of the first attribute
     * instance that was created through default value expansion.
     */
    private final int mDefaultOffset;

    /*
    //////////////////////////////////////////////////////////////
    // Information that defines "Map-like" data structure used for
    // quick access to attribute values by fully-qualified name
    // (only used for "long" lists)
    //////////////////////////////////////////////////////////////
     */

    // // // For full explanation, see source for AttributeCollector

    private final int[] mAttrMap;

    private final int mAttrHashSize;

    private final int mAttrSpillEnd;

    /**
     * Method called to create "short" attribute list; list that has
     * only few entries, and can thus be searched for attributes using
     * linear search, without using any kind of Map structure.
     *<p>
     * Currently the limit is 4 attributes; 1, 2 or 3 attribute lists are
     * considered short, 4 or more 'long'.
     *
     * @param rawAttrs Array that contains 4 Strings for each attribute;
     *    localName, URI, prefix, value. Can be used to lazily construct
     *    structure(s) needed to return Iterator for accessing all
     *    attributes.
     * @param defOffset Index of the first default attribute, if any;
     *    number of all attributes if none
     */
    public ElemAttrs(String[] rawAttrs, int defOffset)
    {
        mRawAttrs = rawAttrs;
        mAttrMap = null;
        mAttrHashSize = 0;
        mAttrSpillEnd = 0;
        mDefaultOffset = (defOffset << 2);
    }

    /**
     * Method called to create "long" attribute list; list that has
     * a few entries, and efficient access by fully-qualified name should
     * not be done by linear search.
     *
     * @param rawAttrs Array that contains 4 Strings for each attribute;
     *    localName, URI, prefix, value. Can be used to lazily construct
     *    structure(s) needed to return Iterator for accessing all
     *    attributes.
     */
    public ElemAttrs(String[] rawAttrs, int defOffset,
                     int[] attrMap, int hashSize, int spillEnd)
    {
        mRawAttrs = rawAttrs;
        mDefaultOffset = (defOffset << 2);
        mAttrMap = attrMap;
        mAttrHashSize = hashSize;
        mAttrSpillEnd = spillEnd;
    }

    /*
    ////////////////////////////////////////////////////
    // Public API
    ////////////////////////////////////////////////////
     */

    public String[] getRawAttrs() {
        return mRawAttrs;
    }

    public int findIndex(QName name)
    {
        // Do we have a Map to do lookup against?
        if (mAttrMap != null) { // yup
            return findMapIndex(name.getNamespaceURI(), name.getLocalPart());
        }

        // Nope, linear search:
        String ln = name.getLocalPart();
        String uri = name.getNamespaceURI();
        boolean defaultNs = (uri == null || uri.length() == 0);
        String[] raw = mRawAttrs;
        
        for (int i = 0, len = raw.length; i < len; i += 4) {
            if (!ln.equals(raw[i])) {
                continue;
            }
            String thisUri = raw[i+OFFSET_NS_URI];
            if (defaultNs) {
                if (thisUri == null || thisUri.length() == 0) {
                        return i;
                }
            } else { // non-default NS
                if (thisUri != null &&
                    (thisUri == uri || thisUri.equals(uri))) {
                    return i;
                }
            }
        }
        return -1;
    }

    public int getFirstDefaultOffset() {
        return mDefaultOffset;
    }

    public boolean isDefault(int ix) {
        return (ix >= mDefaultOffset);
    }

    /*
    ////////////////////////////////////////////////////
    // Internal methods
    ////////////////////////////////////////////////////
     */

    /**
     *<p>
     * Note: this method is very similar to
     * {@link com.ctc.wstx.sr.AttributeCollector#getAttrValue}; basically
     * most of it was cut'n pasted. Would be nice to refactor, but it's
     * bit hard to do that since data structures are not 100% identical
     * (mostly attribute storage, not Map structure itself).
     */
    private final int findMapIndex(String nsURI, String localName)
    {
        // Primary hit?
        int hash = localName.hashCode();
        if (nsURI == null) {
            nsURI = ""; // just to simplify comparisons -- array contains nulls
        } else if (nsURI.length() > 0) {
            hash ^= nsURI.hashCode();
        }
        int ix = mAttrMap[hash & (mAttrHashSize - 1)];
        if (ix == 0) { // nothing in here; no spills either
            return -1;
        }
        // Index is "one off" (since 0 indicates 'null), 4 Strings per attr
        ix = (ix - 1) << 2;

        // Is primary candidate match?
        String[] raw = mRawAttrs;
        String thisName = raw[ix];
        /* Equality first, since although equals() checks that too, it's
         * very likely to match (if interning Strings), and we can save
         * a method call.
         */
        if (thisName == localName || thisName.equals(localName)) {
            String thisURI = raw[ix+OFFSET_NS_URI];
            if (thisURI == nsURI) {
                return ix;
            }
            if (thisURI == null) {
                if (nsURI.length() == 0) {
                    return ix;
                }
            } else if (thisURI.equals(nsURI)) {            
                return ix;
            }
        }

        /* Nope, need to traverse spill list, which has 2 entries for
         * each spilled attribute id; first for hash value, second index.
         */
        for (int i = mAttrHashSize, len = mAttrSpillEnd; i < len; i += 2) {
            if (mAttrMap[i] != hash) {
                continue;
            }
            /* Note: spill indexes are not off-by-one, since there's no need
             * to mask 0
             */
            ix = mAttrMap[i+1] << 2; // ... but there are 4 Strings for each attr
            thisName = raw[ix];
            if (thisName == localName || thisName.equals(localName)) {
                String thisURI = raw[ix+1];
                if (thisURI == nsURI) {
                    return ix;
                }
                if (thisURI == null) {
                    if (nsURI.length() == 0) {
                        return ix;
                    }
                } else if (thisURI.equals(nsURI)) {            
                    return ix;
                }
            }
        }

        return -1;
    }
}
