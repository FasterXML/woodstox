/* Woodstox XML processor
 *
 * Copyright (c) 2004- Tatu Saloranta, tatu.saloranta@iki.fi
 *
 * Licensed under the License specified in the file LICENSE which is
 * included with the source code.
 * You may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ctc.wstx.util;

import javax.xml.stream.Location;


/**
 * This class is a specialized type-safe linked hash map used for
 * storing {@link ElementId} instances. {@link ElementId} instances
 * represent both id definitions (values of element attributes that
 * have type ID in DTD), and references (values of element attributes
 * of type IDREF and IDREFS). These definitions and references are
 * stored for the purpose of verifying
 * that all referenced id values are defined, and that none are defined
 * more than once.
 *<p>
 * Note: there are 2 somewhat distinct usage modes, by DTDValidator and
 * by MSV-based validators. 
 * DTDs pass raw character arrays, whereas
 * MSV-based validators operate on Strings. This is the main reason
 * for 2 distinct sets of methods.
 */

public final class ElementIdMap
{
    /**
     * Default initial table size; set so that usually it need not
     * be expanded.
     */
    protected static final int DEFAULT_SIZE = 128;

    protected static final int MIN_SIZE = 16;

    /**
     * Let's use 80% fill factor...
     */
    protected static final int FILL_PCT = 80;

    /*
    ////////////////////////////////////////
    // Actual hash table structure
    ////////////////////////////////////////
     */

    /**
     * Actual hash table area
     */
    protected ElementId[] mTable;

    /**
     * Current size (number of entries); needed to know if and when
     * rehash.
     */
    protected int mSize;

    /**
     * Limit that indicates maximum size this instance can hold before
     * it needs to be expanded and rehashed. Calculated using fill
     * factor passed in to constructor.
     */
    protected int mSizeThreshold;

    /**
     * Mask used to get index from hash values; equal to
     * <code>mBuckets.length - 1</code>, when mBuckets.length is
     * a power of two.
     */
    protected int mIndexMask;

    /*
    ////////////////////////////////////////
    // Linked list info
    ////////////////////////////////////////
     */

    protected ElementId mHead;

    protected ElementId mTail;

    /*
    ////////////////////////////////////////
    // Life-cycle:
    ////////////////////////////////////////
     */

    public ElementIdMap()
    {
        this(DEFAULT_SIZE);
    }

    /**
     * This constructor is mainly used for testing, as it can be sized
     * appropriately to test rehashing etc.
     */
    public ElementIdMap(int initialSize)
    {
        int actual = MIN_SIZE;
        while (actual < initialSize) {
            actual += actual;
        }
        mTable = new ElementId[actual];
        // Mask is easy to calc for powers of two.
        mIndexMask = actual - 1;
        mSize = 0;
        mSizeThreshold = (actual * FILL_PCT) / 100;
        mHead = mTail = null;
    }

    /*
    ////////////////////////////////////////////////////
    // Public API
    ////////////////////////////////////////////////////
     */

    public ElementId getFirstUndefined()
    {
        /* Since the linked list is pruned to always start with
         * the first (in doc order) undefined id, we can just
         * return head:
         */
        return mHead;
    }

    /**
     * Method called when a reference to id is encountered. If so, need
     * to check if specified id entry (ref or definiton) exists; and if not,
     * to add a reference marker.
     */
    public ElementId addReferenced(char[] buffer, int start, int len, int hash,
                                   Location loc, PrefixedName elemName, PrefixedName attrName)
    {
        int index = (hash & mIndexMask);
        ElementId id = mTable[index];

        while (id != null) {
            if (id.idMatches(buffer, start, len)) { // found existing one
                return id;
            }
            id = id.nextColliding();
        }

        // Not found, need to create a placeholder...

        // But first, do we need more room?
        if (mSize >= mSizeThreshold) {
            rehash();
            // Index changes, for the new entr:
            index = (hash & mIndexMask);
        }
        ++mSize;

        // Ok, then, let's create the entry
        String idStr = new String(buffer, start, len);
        id = new ElementId(idStr, loc, false, elemName, attrName);

        // First, let's link it to Map; all ids have to be connected
        id.setNextColliding(mTable[index]);
        mTable[index] = id;

        // And then add the undefined entry at the end of list
        if (mHead == null) {
            mHead = mTail = id;
        } else {
            mTail.linkUndefined(id);
            mTail = id;
        }
        return id;
    }

    public ElementId addReferenced(String idStr,
                                   Location loc, PrefixedName elemName, PrefixedName attrName)
    {
        int hash = calcHash(idStr);
        int index = (hash & mIndexMask);
        ElementId id = mTable[index];

        while (id != null) {
            if (id.idMatches(idStr)) { // found existing one
                return id;
            }
            id = id.nextColliding();
        }

        // Not found, need to create a placeholder...

        // But first, do we need more room?
        if (mSize >= mSizeThreshold) {
            rehash();
            // Index changes, for the new entr:
            index = (hash & mIndexMask);
        }
        ++mSize;

        // Ok, then, let's create the entry
        id = new ElementId(idStr, loc, false, elemName, attrName);

        // First, let's link it to Map; all ids have to be connected
        id.setNextColliding(mTable[index]);
        mTable[index] = id;

        // And then add the undefined entry at the end of list
        if (mHead == null) {
            mHead = mTail = id;
        } else {
            mTail.linkUndefined(id);
            mTail = id;
        }
        return id;
    }

    /**
     * Method called when an id definition is encountered. If so, need
     * to check if specified id entry (ref or definiton) exists. If not,
     * need to add the definition marker. If it does exist, need to
     * 'upgrade it', if it was a reference marker; otherwise need to
     * just return the old entry, and expect caller to check for dups
     * and report the error.
     */
    public ElementId addDefined(char[] buffer, int start, int len, int hash,
                                Location loc, PrefixedName elemName, PrefixedName attrName)
    {
        int index = (hash & mIndexMask);
        ElementId id = mTable[index];

        while (id != null) {
            if (id.idMatches(buffer, start, len)) {
                break;
            }
            id = id.nextColliding();
        }

        /* Not found, can just add it to the Map; no need to add to the
         * linked list as it's not undefined
         */
        if (id == null) {
            // First, do we need more room?
            if (mSize >= mSizeThreshold) {
                rehash();
                index = (hash & mIndexMask);
            }
            ++mSize;
            String idStr = new String(buffer, start, len);
            id = new ElementId(idStr, loc, true, elemName, attrName);
            id.setNextColliding(mTable[index]);
            mTable[index] = id;
        } else {
            /* If already defined, nothing additional to do (we could
             * signal an error here, though... for now, we'll let caller
             * do that
             */
            if (id.isDefined()) {
                ;
            } else {
                /* Not defined, just need to upgrade, and possibly remove from
                 * the linked list.
                 */
                id.markDefined(loc);
                
                /* Ok; if it was the first undefined, need to unlink it, as
                 * well as potentially next items.
                 */
                if (id == mHead) {
                    do {
                        mHead = mHead.nextUndefined();
                    } while (mHead != null && mHead.isDefined());
                    
                    // Did we clear up all undefined ids?
                    if (mHead == null) {
                        mTail = null;
                    }
                }
            }
        }

        return id;
    }

    public ElementId addDefined(String idStr,
                                Location loc, PrefixedName elemName, PrefixedName attrName)
    {
        int hash = calcHash(idStr);
        int index = (hash & mIndexMask);
        ElementId id = mTable[index];

        while (id != null) {
            if (id.idMatches(idStr)) {
                break;
            }
            id = id.nextColliding();
        }

        /* Not found, can just add it to the Map; no need to add to the
         * linked list as it's not undefined
         */
        if (id == null) {
            if (mSize >= mSizeThreshold) { // need more room
                rehash();
                index = (hash & mIndexMask);
            }
            ++mSize;
            id = new ElementId(idStr, loc, true, elemName, attrName);
            id.setNextColliding(mTable[index]);
            mTable[index] = id;
        } else {
            /* If already defined, nothing additional to do (we could
             * signal an error here, though... for now, we'll let caller
             * do that
             */
            if (id.isDefined()) {
                ;
            } else {
                /* Not defined, just need to upgrade, and possibly remove from
                 * the linked list.
                 */
                id.markDefined(loc);
                
                /* Ok; if it was the first undefined, need to unlink it, as
                 * well as potentially next items.
                 */
                if (id == mHead) {
                    do {
                        mHead = mHead.nextUndefined();
                    } while (mHead != null && mHead.isDefined());
                    if (mHead == null) { // cleared up all undefined ids?
                        mTail = null;
                    }
                }
            }
        }

        return id;
    }

    /**
     * Implementation of a hashing method for variable length
     * Strings. Most of the time intention is that this calculation
     * is done by caller during parsing, not here; however, sometimes
     * it needs to be done for parsed "String" too.
     *<p>
     * Note: identical to {@link com.ctc.wstx.util.SymbolTable#calcHash},
     * although not required to be.
     *
     * @param len Length of String; has to be at least 1 (caller guarantees
     *   this pre-condition)
     */
    @SuppressWarnings("cast")
	public static int calcHash(char[] buffer, int start, int len)
    {
        int hash = (int) buffer[start];
        for (int i = 1; i < len; ++i) {
            hash = (hash * 31) + (int) buffer[start+i];
        }
        return hash;
    }

    @SuppressWarnings("cast")
	public static int calcHash(String key)
    {
        int hash = (int) key.charAt(0);
        for (int i = 1, len = key.length(); i < len; ++i) {
            hash = (hash * 31) + (int) key.charAt(i);

        }
        return hash;
    }

    /*
    //////////////////////////////////////////////////////////
    // Internal methods
    //////////////////////////////////////////////////////////
     */

    /**
     * Method called when size (number of entries) of symbol table grows
     * so big that load factor is exceeded. Since size has to remain
     * power of two, arrays will then always be doubled. Main work
     * is really redistributing old entries into new String/Bucket
     * entries.
     */
    private void rehash()
    {
        int size = mTable.length;
        /* Let's grow aggressively; this should minimize number of
         * resizes, while adding to mem usage. But since these Maps
         * are never long-lived (only during parsing and validation of
         * a single doc), that shouldn't greatly matter.
         */
        int newSize = (size << 2);
        ElementId[] oldSyms = mTable;
        mTable = new ElementId[newSize];

        // Let's update index mask, threshold, now (needed for rehashing)
        mIndexMask = newSize - 1;
        mSizeThreshold <<= 2;
        
        int count = 0; // let's do sanity check

        for (int i = 0; i < size; ++i) {
            for (ElementId id = oldSyms[i]; id != null; ) {
                ++count;
                int index = calcHash(id.getId()) & mIndexMask;
                ElementId nextIn = id.nextColliding();
                id.setNextColliding(mTable[index]);
                mTable[index] = id;
                id = nextIn;
            }
        }

        if (count != mSize) {
            ExceptionUtil.throwInternal("on rehash(): had "+mSize+" entries; now have "+count+".");
        }
    }
}
