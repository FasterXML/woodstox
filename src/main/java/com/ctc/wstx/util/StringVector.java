package com.ctc.wstx.util;

/**
 * Data container similar {@link java.util.List} (from storage perspective),
 * but that can be used in multiple ways. For some uses it acts more like
 * type-safe String list/vector; for others as order associative list of
 * String-to-String mappings.
 */
public final class StringVector
{
    private String[] mStrings;

    private int mSize;

    /*
    ///////////////////////////////////////////////////////
    // Life-cycle:
    ///////////////////////////////////////////////////////
     */

    public StringVector(int initialCount) {
        mStrings = new String[initialCount];
    }

    /*
    ///////////////////////////////////////////////////////
    // Basic accessors
    ///////////////////////////////////////////////////////
     */

    public int size() { return mSize; }

    public boolean isEmpty() { return mSize == 0; }

    public String getString(int index) {
        if (index < 0 || index >= mSize) {
            throw new IllegalArgumentException("Index "+index+" out of valid range; current size: "+mSize+".");
        }
        return mStrings[index];
    }

    public String getLastString() {
        if (mSize < 1) {
            throw new IllegalStateException("getLastString() called on empty StringVector.");
        }
        return mStrings[mSize-1];
    }

    public String[] getInternalArray() {
        return mStrings;
    }

    public String[] asArray() {
        String[] strs = new String[mSize];
        System.arraycopy(mStrings, 0, strs, 0, mSize);
        return strs;
    }

    public boolean containsInterned(String value) {
        String[] str = mStrings;
        for (int i = 0, len = mSize; i < len; ++i) {
            if (str[i] == value) {
                return true;
            }
        }
        return false;
    }

    /*
    ///////////////////////////////////////////////////////
    // Mutators:
    ///////////////////////////////////////////////////////
     */

    public void addString(String str) {
        if (mSize == mStrings.length) {
            String[] old = mStrings;
            int oldSize = old.length;
            mStrings = new String[oldSize + (oldSize << 1)];
            System.arraycopy(old, 0, mStrings, 0, oldSize);
        }
        mStrings[mSize++] = str;
    }

    public void addStrings(String str1, String str2) {
        if ((mSize + 2) > mStrings.length) {
            String[] old = mStrings;
            int oldSize = old.length;
            mStrings = new String[oldSize + (oldSize << 1)];
            System.arraycopy(old, 0, mStrings, 0, oldSize);
        }
        mStrings[mSize] = str1;
        mStrings[mSize+1] = str2;
        mSize += 2;
    }

    public void setString(int index, String str) {
        mStrings[index] = str;
    }

    public void clear(boolean removeRefs) {
        if (removeRefs) {
            for (int i = 0, len = mSize; i < len; ++i) {
                mStrings[i] = null;
            }
        }
        mSize = 0;
    }

    public String removeLast() {
        String result = mStrings[--mSize];
        mStrings[mSize] = null;
        return result;
    }

    public void removeLast(int count) {
        while (--count >= 0) {
            mStrings[--mSize] = null;
        }
    }

    /*
    ///////////////////////////////////////////////////////
    // Specialized "map accessors":
    ///////////////////////////////////////////////////////
     */

    /**
     * Specialized access method; treats vector as a Map, with 2 Strings
     * per entry; first one being key, second value. Further, keys are
     * assumed to be canonicalized with passed in key (ie. either intern()ed,
     * or resolved from symbol table).
     * Starting from the
     * end (assuming even number of entries), tries to find an entry with
     * matching key, and if so, returns value.
     */
    public String findLastFromMap(String key) {
        int index = mSize;
        while ((index -= 2) >= 0) {
            if (mStrings[index] == key) {
                return mStrings[index+1];
            }
        }
        return null;
    }

    public String findLastNonInterned(String key)
    {
        int index = mSize;
        while ((index -= 2) >= 0) {
            String curr = mStrings[index];
            if (curr == key || (curr != null && curr.equals(key))) {
                return mStrings[index+1];
            }
        }
        return null;
    }

    public int findLastIndexNonInterned(String key) {
        int index = mSize;
        while ((index -= 2) >= 0) {
            String curr = mStrings[index];
            if (curr == key || (curr != null && curr.equals(key))) {
                return index;
            }
        }
        return -1;
    }

    public String findLastByValueNonInterned(String value) {
        for (int index = mSize-1; index > 0; index -= 2) {
            String currVal = mStrings[index];
            if (currVal == value || (currVal != null && currVal.equals(value))) {
                return mStrings[index-1];
            }
        }
        return null;
    }

    public int findLastIndexByValueNonInterned(String value) {
        for (int index = mSize-1; index > 0; index -= 2) {
            String currVal = mStrings[index];
            if (currVal == value || (currVal != null && currVal.equals(value))) {
                return index-1;
            }
        }
        return -1;
    }

    /*
      // Not needed any more
    public Iterator findAllByValueNonInterned(String value) {
        String first = null;
        ArrayList all = null;
        for (int index = mSize-1; index > 0; index -= 2) {
            String currVal = mStrings[index];
            if (currVal == value || (currVal != null && currVal.equals(value))) {
                if (first == null) {
                    first = mStrings[index-1];
                } else {
                    if (all == null) {
                        all = new ArrayList();
                        all.add(first);
                    }
                    all.add(mStrings[index-1]);
                }
            }
        }
        if (all != null) {
            return all.iterator();
        }
        if (first != null) {
            return new SingletonIterator(first);
        }
        return DataUtil.emptyIterator();
    }
    */

    /*
    ///////////////////////////////////////////////////////
    // Other methods
    ///////////////////////////////////////////////////////
     */

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(mSize * 16);
        sb.append("[(size = ");
        sb.append(mSize);
        sb.append(" ) ");
        for (int i = 0; i < mSize; ++i) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('"');
            sb.append(mStrings[i]);
            sb.append('"');

            sb.append(" == ");
            sb.append(Integer.toHexString(System.identityHashCode(mStrings[i])));
        }
        sb.append(']');
        return sb.toString();
    }
}
