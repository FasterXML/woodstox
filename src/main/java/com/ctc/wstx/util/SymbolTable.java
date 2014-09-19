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

/**
 * This class is a kind of specialized type-safe Map, from char array to
 * String value. Specialization means that in addition to type-safety
 * and specific access patterns (key char array, Value optionally interned
 * String; values added on access if necessary), and that instances are
 * meant to be used concurrently, but by using well-defined mechanisms
 * to obtain such concurrently usable instances. Main use for the class
 * is to store symbol table information for things like compilers and
 * parsers; especially when number of symbols (keywords) is limited.
 *<p>
 * For optimal performance, usage pattern should be one where matches
 * should be very common (esp. after "warm-up"), and as with most hash-based
 * maps/sets, that hash codes are uniformly distributed. Also, collisions
 * are slightly more expensive than with HashMap or HashSet, since hash codes
 * are not used in resolving collisions; that is, equals() comparison is
 * done with all symbols in same bucket index.<br />
 * Finally, rehashing is also more expensive, as hash codes are not
 * stored; rehashing requires all entries' hash codes to be recalculated.
 * Reason for not storing hash codes is reduced memory usage, hoping
 * for better memory locality.
 *<p>
 * Usual usage pattern is to create a single "master" instance, and either
 * use that instance in sequential fashion, or to create derived "child"
 * instances, which after use, are asked to return possible symbol additions
 * to master instance. In either case benefit is that symbol table gets
 * initialized so that further uses are more efficient, as eventually all
 * symbols needed will already be in symbol table. At that point no more
 * Symbol String allocations are needed, nor changes to symbol table itself.
 *<p>
 * Note that while individual SymbolTable instances are NOT thread-safe
 * (much like generic collection classes), concurrently used "child"
 * instances can be freely used without synchronization. However, using
 * master table concurrently with child instances can only be done if
 * access to master instance is read-only (ie. no modifications done).
 */

public class SymbolTable {

    /**
     * Default initial table size; no need to make it miniscule, due
     * to couple of things: first, overhead of array reallocation
     * is significant,
     * and second, overhead of rehashing is also non-negligible.
     *<p>
     * Let's use 128 as the default; it allows for up to 96 symbols,
     * and uses about 512 bytes on 32-bit machines.
     */
    protected static final int DEFAULT_TABLE_SIZE = 128;

    protected static final float DEFAULT_FILL_FACTOR = 0.75f;

    protected static final String EMPTY_STRING = "";

    /*
    ////////////////////////////////////////
    // Configuration:
    ////////////////////////////////////////
     */

    /**
     * Flag that determines whether Strings to be added need to be
     * interned before being added or not. Forcing intern()ing will add
     * some overhead when adding new Strings, but may be beneficial if such
     * Strings are generally used by other parts of system. Note that even
     * without interning, all returned String instances are guaranteed
     * to be comparable with equality (==) operator; it's just that such
     * guarantees are not made for Strings other classes return.
     */
    protected boolean mInternStrings;

    /*
    ////////////////////////////////////////
    // Actual symbol table data:
    ////////////////////////////////////////
     */

    /**
     * Primary matching symbols; it's expected most match occur from
     * here.
     */
    protected String[] mSymbols;

    /**
     * Overflow buckets; if primary doesn't match, lookup is done
     * from here.
     *<p>
     * Note: Number of buckets is half of number of symbol entries, on
     * assumption there's less need for buckets.
     */
    protected Bucket[] mBuckets;

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
    // Information about concurrency
    ////////////////////////////////////////
     */
    
    /**
     * Version of this table instance; used when deriving new concurrently
     * used versions from existing 'master' instance.
     */
    protected int mThisVersion;

    /**
     * Flag that indicates if any changes have been made to the data;
     * used to both determine if bucket array needs to be copied when
     * (first) change is made, and potentially if updated bucket list
     * is to be resync'ed back to master instance.
     */
    protected boolean mDirty;

    /*
    ////////////////////////////////////////
    // Life-cycle:
    ////////////////////////////////////////
     */

    /**
     * Method for constructing a master symbol table instance; this one
     * will create master instance with default size, and with interning
     * enabled.
     */
    public SymbolTable() {
        this(true);
    }

    /**
     * Method for constructing a master symbol table instance.
     */
    public SymbolTable(boolean internStrings) {
        this(internStrings, DEFAULT_TABLE_SIZE);
    }

    /**
     * Method for constructing a master symbol table instance.
     */
    public SymbolTable(boolean internStrings, int initialSize) {
        this(internStrings, initialSize, DEFAULT_FILL_FACTOR);
    }

    /**
     * Main method for constructing a master symbol table instance; will
     * be called by other public constructors.
     *
     * @param internStrings Whether Strings to add are intern()ed or not
     * @param initialSize Minimum initial size for bucket array; internally
     *   will always use a power of two equal to or bigger than this value.
     * @param fillFactor Maximum fill factor allowed for bucket table;
     *   when more entries are added, table will be expanded.
     */
    public SymbolTable(boolean internStrings, int initialSize,
                       float fillFactor)
    {
        mInternStrings = internStrings;
        // Let's start versions from 1
        mThisVersion = 1;
        // And we'll also set flags so no copying of buckets is needed:
        mDirty = true;

        // No point in requesting funny initial sizes...
        if (initialSize < 1) {
            throw new IllegalArgumentException("Can not use negative/zero initial size: "+initialSize);
        }
        /* Initial size has to be a power of two. Also, let's not honour
         * sizes that are ridiculously small...
         */
        {
            int currSize = 4;
            while (currSize < initialSize) {
                currSize += currSize;
            }
            initialSize = currSize;
        }

        mSymbols = new String[initialSize];
        mBuckets = new Bucket[initialSize >> 1];
        // Mask is easy to calc for powers of two.
        mIndexMask = initialSize - 1;
        mSize = 0;

        // Sanity check for fill factor:
        if (fillFactor < 0.01f) {
            throw new IllegalArgumentException("Fill factor can not be lower than 0.01.");
        }
        if (fillFactor > 10.0f) { // just to catch stupid values, ie. useless from performance perspective
            throw new IllegalArgumentException("Fill factor can not be higher than 10.0.");
        }
        mSizeThreshold = (int) (initialSize * fillFactor + 0.5);
    }

    /**
     * Internal constructor used when creating child instances.
     */
    private SymbolTable(boolean internStrings, String[] symbols,
                        Bucket[] buckets, int size, int sizeThreshold,
                        int indexMask, int version)
    {
        mInternStrings = internStrings;
        mSymbols = symbols;
        mBuckets = buckets;
        mSize = size;
        mSizeThreshold = sizeThreshold;
        mIndexMask = indexMask;
        mThisVersion = version;

        // Need to make copies of arrays, if/when adding new entries
        mDirty = false;
    }

    /**
     * "Factory" method; will create a new child instance of this symbol
     * table. It will be a copy-on-write instance, ie. it will only use
     * read-only copy of parent's data, but when changes are needed, a
     * copy will be created.
     *<p>
     * Note: while data access part of this method is synchronized, it is
     * generally not safe to both use makeChild/mergeChild, AND to use instance
     * actively. Instead, a separate 'root' instance should be used
     * on which only makeChild/mergeChild are called, but instance itself
     * is not used as a symbol table.
     */
    public SymbolTable makeChild()
    {
        final boolean internStrings;
        final String[] symbols;
        final Bucket[] buckets;
        final int size;
        final int sizeThreshold;
        final int indexMask;
        final int version;

        synchronized (this) {      
            internStrings = mInternStrings;
            symbols = mSymbols;
            buckets = mBuckets;
            size = mSize;
            sizeThreshold = mSizeThreshold;
            indexMask = mIndexMask;
            version = mThisVersion+1;
        }
        return new SymbolTable(internStrings, symbols, buckets,
                size, sizeThreshold, indexMask, version);
    }

    /**
     * Method that allows contents of child table to potentially be
     * "merged in" with contents of this symbol table.
     *<p>
     * Note that caller has to make sure symbol table passed in is
     * really a child or sibling of this symbol table.
     */
    public synchronized void mergeChild(SymbolTable child)
    {
        // Let's do a basic sanity check first:
        if (child.size() <= size()) { // nothing to add
            return;
        }

        // Okie dokie, let's get the data in!
        mSymbols = child.mSymbols;
        mBuckets = child.mBuckets;
        mSize = child.mSize;
        mSizeThreshold = child.mSizeThreshold;
        mIndexMask = child.mIndexMask;
        mThisVersion++; // to prevent other children from overriding

        // Dirty flag... well, let's just clear it, to force copying just
        // in case. Shouldn't really matter, for master tables.
        mDirty = false;

        /* However, we have to mark child as dirty, so that it will not
         * be modifying arrays we "took over" (since child may have
         * returned an updated table before it stopped fully using
         * the SymbolTable: for example, it may still use it for
         * parsing PI targets in epilog)
         */
        child.mDirty = false;
    }

    /*
    ////////////////////////////////////////////////////
    // Public API, configuration
    ////////////////////////////////////////////////////
     */

    public void setInternStrings(boolean state) {
        mInternStrings = state;
    }

    /*
    ////////////////////////////////////////////////////
    // Public API, generic accessors:
    ////////////////////////////////////////////////////
     */

    public int size() { return mSize; }

    public int version() { return mThisVersion; }

    public boolean isDirty() { return mDirty; }

    public boolean isDirectChildOf(SymbolTable t)
    {
        /* Actually, this doesn't really prove it is a child (would have to
         * use sequence number, or identityHash to really prove it), but
         * it's good enough if relationship is known to exist.
         */
        /* (for real check, one would need to child/descendant stuff; or
         * at least an identity hash... or maybe even just a _static_ global
         * counter for instances... maybe that would actually be worth
         * doing?)
         */
        if (mThisVersion == (t.mThisVersion + 1)) {
            return true;
        }
        return false;
    }

    /*
    ////////////////////////////////////////////////////
    // Public API, accessing symbols:
    ////////////////////////////////////////////////////
     */

    /**
     * Main access method; will check if actual symbol String exists;
     * if so, returns it; if not, will create, add and return it.
     *
     * @return The symbol matching String in input array
     */
    /*
    public String findSymbol(char[] buffer, int start, int len)
    {
        return findSymbol(buffer, start, len, calcHash(buffer, start, len));
    }
    */

    public String findSymbol(char[] buffer, int start, int len, int hash)
    {
        // Sanity check:
        if (len < 1) {
            return EMPTY_STRING;
        }

        hash &= mIndexMask;

        String sym = mSymbols[hash];

        // Optimal case; checking existing primary symbol for hash index:
        if (sym != null) {
            // Let's inline primary String equality checking:
            if (sym.length() == len) {
                int i = 0;
                do {
                    if (sym.charAt(i) != buffer[start+i]) {
                        break;
                    }
                } while (++i < len);
                // Optimal case; primary match found
                if (i == len) {
                    return sym;
                }
            }
            // How about collision bucket?
            Bucket b = mBuckets[hash >> 1];
            if (b != null) {
                sym = b.find(buffer, start, len);
                if (sym != null) {
                    return sym;
                }
            }
        }

        // Need to expand?
        if (mSize >= mSizeThreshold) {
            rehash();
            /* Need to recalc hash; rare occurence (index mask has been
             * recalculated as part of rehash)
             */
            hash = calcHash(buffer, start, len) & mIndexMask;
        } else if (!mDirty) {
            // Or perhaps we need to do copy-on-write?
            copyArrays();
            mDirty = true;
        }
        ++mSize;

        String newSymbol = new String(buffer, start, len);
        if (mInternStrings) {
            newSymbol = newSymbol.intern();
        }
        // Ok; do we need to add primary entry, or a bucket?
        if (mSymbols[hash] == null) {
            mSymbols[hash] = newSymbol;
        } else {
            int bix = hash >> 1;
            mBuckets[bix] = new Bucket(newSymbol, mBuckets[bix]);
        }

        return newSymbol;
    }

    /**
     * Similar to {link #findSymbol}, but will not add passed in symbol
     * if it is not in symbol table yet.
     */
    public String findSymbolIfExists(char[] buffer, int start, int len, int hash)
    {
        // Sanity check:
        if (len < 1) {
            return EMPTY_STRING;
        }
        hash &= mIndexMask;

        String sym = mSymbols[hash];
        // Optimal case; checking existing primary symbol for hash index:
        if (sym != null) {
            // Let's inline primary String equality checking:
            if (sym.length() == len) {
                int i = 0;
                do {
                    if (sym.charAt(i) != buffer[start+i]) {
                        break;
                    }
                } while (++i < len);
                // Optimal case; primary match found
                if (i == len) {
                    return sym;
                }
            }
            // How about collision bucket?
            Bucket b = mBuckets[hash >> 1];
            if (b != null) {
                sym = b.find(buffer, start, len);
                if (sym != null) {
                    return sym;
                }
            }
        }
        return null;
    }

    /**
     * Similar to to {@link #findSymbol(char[],int,int,int)}; used to either
     * do potentially cheap intern() (if table already has intern()ed version),
     * or to pre-populate symbol table with known values.
     */
    public String findSymbol(String str)
    {
        int len = str.length();
        // Sanity check:
        if (len < 1) {
            return EMPTY_STRING;
        }

        int index = calcHash(str) & mIndexMask;
        String sym = mSymbols[index];

        // Optimal case; checking existing primary symbol for hash index:
        if (sym != null) {
            // Let's inline primary String equality checking:
            if (sym.length() == len) {
                int i = 0;
                for (; i < len; ++i) {
                    if (sym.charAt(i) != str.charAt(i)) {
                        break;
                    }
                }
                // Optimal case; primary match found
                if (i == len) {
                    return sym;
                }
            }
            // How about collision bucket?
            Bucket b = mBuckets[index >> 1];
            if (b != null) {
                sym = b.find(str);
                if (sym != null) {
                    return sym;
                }
            }
        }

        // Need to expand?
        if (mSize >= mSizeThreshold) {
            rehash();
            /* Need to recalc hash; rare occurence (index mask has been
             * recalculated as part of rehash)
             */
            index = calcHash(str) & mIndexMask;
        } else if (!mDirty) {
            // Or perhaps we need to do copy-on-write?
            copyArrays();
            mDirty = true;
        }
        ++mSize;

        if (mInternStrings) {
            str = str.intern();
        }
        // Ok; do we need to add primary entry, or a bucket?
        if (mSymbols[index] == null) {
            mSymbols[index] = str;
        } else {
            int bix = index >> 1;
            mBuckets[bix] = new Bucket(str, mBuckets[bix]);
        }

        return str;
    }

    /**
     * Implementation of a hashing method for variable length
     * Strings. Most of the time intention is that this calculation
     * is done by caller during parsing, not here; however, sometimes
     * it needs to be done for parsed "String" too.
     *
     * @param len Length of String; has to be at least 1 (caller guarantees
     *   this pre-condition)
     */
    @SuppressWarnings("cast")
    public static int calcHash(char[] buffer, int start, int len) {
        int hash = (int) buffer[start];
        for (int i = 1; i < len; ++i) {
            hash = (hash * 31) + (int) buffer[start+i];
        }
        return hash;
    }

    @SuppressWarnings("cast")
    public static int calcHash(String key) {
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
     * Method called when copy-on-write is needed; generally when first
     * change is made to a derived symbol table.
     */
    private void copyArrays() {
        String[] oldSyms = mSymbols;
        int size = oldSyms.length;
        mSymbols = new String[size];
        System.arraycopy(oldSyms, 0, mSymbols, 0, size);
        Bucket[] oldBuckets = mBuckets;
        size = oldBuckets.length;
        mBuckets = new Bucket[size];
        System.arraycopy(oldBuckets, 0, mBuckets, 0, size);
    }

    /**
     * Method called when size (number of entries) of symbol table grows
     * so big that load factor is exceeded. Since size has to remain
     * power of two, arrays will then always be doubled. Main work
     * is really redistributing old entries into new String/Bucket
     * entries.
     */
    private void rehash()
    {
        int size = mSymbols.length;
        int newSize = size + size;
        String[] oldSyms = mSymbols;
        Bucket[] oldBuckets = mBuckets;
        mSymbols = new String[newSize];
        mBuckets = new Bucket[newSize >> 1];
        // Let's update index mask, threshold, now (needed for rehashing)
        mIndexMask = newSize - 1;
        mSizeThreshold += mSizeThreshold;
        
        int count = 0; // let's do sanity check

        /* Need to do two loops, unfortunately, since spillover area is
         * only half the size:
         */
        for (int i = 0; i < size; ++i) {
            String symbol = oldSyms[i];
            if (symbol != null) {
                ++count;
                int index = calcHash(symbol) & mIndexMask;
                if (mSymbols[index] == null) {
                    mSymbols[index] = symbol;
                } else {
                    int bix = index >> 1;
                    mBuckets[bix] = new Bucket(symbol, mBuckets[bix]);
                }
            }
        }

        size >>= 1;
        for (int i = 0; i < size; ++i) {
            Bucket b = oldBuckets[i];
            while (b != null) {
                ++count;
                String symbol = b.getSymbol();
                int index = calcHash(symbol) & mIndexMask;
                if (mSymbols[index] == null) {
                    mSymbols[index] = symbol;
                } else {
                    int bix = index >> 1;
                    mBuckets[bix] = new Bucket(symbol, mBuckets[bix]);
                }
                b = b.getNext();
            }
        }

        if (count != mSize) {
            throw new IllegalStateException("Internal error on SymbolTable.rehash(): had "+mSize+" entries; now have "+count+".");
        }
    }

    /*
    //////////////////////////////////////////////////////////
    // Test/debug support:
    //////////////////////////////////////////////////////////
     */

    public double calcAvgSeek() {
        int count = 0;

        for (int i = 0, len = mSymbols.length; i < len; ++i) {
            if (mSymbols[i] != null) {
                ++count;
            }
        }

        for (int i = 0, len = mBuckets.length; i < len; ++i) {
            Bucket b = mBuckets[i];
            int cost = 2;
            while (b != null) {
                count += cost;
                ++cost;
                b = b.getNext();
            }
        }

        return ((double) count) / ((double) mSize);
    }

    /*
    //////////////////////////////////////////////////////////
    // Bucket class
    //////////////////////////////////////////////////////////
     */

    /**
     * This class is a symbol table entry. Each entry acts as a node
     * in a linked list.
     */
    static final class Bucket {
        private final String mSymbol;
        private final Bucket mNext;

        public Bucket(String symbol, Bucket next) {
            mSymbol = symbol;
            mNext = next;
        }

        public String getSymbol() { return mSymbol; }
        public Bucket getNext() { return mNext; }

        public String find(char[] buf, int start, int len) {
            String sym = mSymbol;
            Bucket b = mNext;

            while (true) { // Inlined equality comparison:
                if (sym.length() == len) {
                    int i = 0;
                    do {
                        if (sym.charAt(i) != buf[start+i]) {
                            break;
                        }
                    } while (++i < len);
                    if (i == len) {
                        return sym;
                    }
                }
                if (b == null) {
                    break;
                }
                sym = b.getSymbol();
                b = b.getNext();
            }
            return null;
        }

        public String find(String str) {
            String sym = mSymbol;
            Bucket b = mNext;

            while (true) {
                if (sym.equals(str)) {
                    return sym;
                }
                if (b == null) {
                    break;
                }
                sym = b.getSymbol();
                b = b.getNext();
            }
            return null;
        }
    }
}
