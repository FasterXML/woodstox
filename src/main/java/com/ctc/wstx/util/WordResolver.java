package com.ctc.wstx.util;

import java.util.*;

/**
 * A specialized Map/Symbol table - like data structure that can be used
 * for both checking whether a word (passed in as a char array) exists
 * in certain set of words AND getting that word as a String.
 * It is reasonably efficient both time and speed-wise, at least for
 * certain use cases; specifically, if there is no existing key to use,
 * it is more efficient way to get to a shared copy of that String
 * The general usage pattern is expected
 * to be such that most checks are positive, ie. that the word indeed
 * is contained in the structure.
 *<p>
 * Although this is an efficient data struct for specific set of usage
 * patterns, one restriction is that the full set of words to include has to
 * be known before constructing the instnace. Also, the size of the set is
 * limited to total word content of about 20k characters.
 *<p>
 * TODO: Should document the internal data structure...
 */
public final class WordResolver
{
    /**
     * Maximum number of words (Strings) an instance can contain
     */
    public final static int MAX_WORDS = 0x2000;

    final static char CHAR_NULL = (char) 0;

    /**
     * Offset added to numbers to mark 'negative' numbers. Asymmetric,
     * since range of negative markers needed is smaller than positive
     * numbers...
     */
    final static int NEGATIVE_OFFSET = 0x10000 - MAX_WORDS;

    /**
     * This is actually just a guess; but in general linear search should
     * be faster for short sequences (definitely for 4 or less; maybe up
     * to 8 or less?)
     */
    final static int MIN_BINARY_SEARCH = 7;

    /**
     * Compressed presentation of the word set.
     */
    final char[] mData;

    /**
     * Array of actual words returned resolved for matches.
     */
    final String[] mWords;

    /*
    ////////////////////////////////////////////////
    // Life-cycle
    ////////////////////////////////////////////////
     */

    private WordResolver(String[] words, char[] index) {
        mWords = words;
        mData = index;
    }

    /**
     * Tries to construct an instance given ordered set of words.
     *<p>
     * Note: currently maximum number of words that can be contained
     * is limited to {@link #MAX_WORDS}; additionally, maximum length
     * of all such words can not exceed roughly 28000 characters.
     *
     * @return WordResolver constructed for given set of words, if
     *   the word set size is not too big; null to indicate "too big"
     *   instance.
     */
    public static WordResolver constructInstance(TreeSet<String> wordSet)
    {
        if (wordSet.size() > MAX_WORDS) {
            return null;
        }
        return new Builder(wordSet).construct();
    }

    /*
    ////////////////////////////////////////////////
    // Public API
    ////////////////////////////////////////////////
     */

    /**
     * @return Number of words contained
     */
    public int size() {
        return mWords.length;
    }

    /*
    public int indexSize() {
        return mData.length;
    }
    */

    /**
     * @param str Character array that contains the word to find
     * @param start Index of the first character of the word
     * @param end Index following the last character of the word,
     *   so that <code>end - start</code> equals word length (similar
     *   to the way <code>String.substring()</code> has).
     *
     * @return (Shared) string instance of the word, if it exists in
     *   the word set; null if not.
     */
    @SuppressWarnings("cast")
	public String find(char[] str, final int start, final int end)
    {
        char[] data = mData;

        // 03-Jan-2006, TSa: Special case; one entry
        if (data == null) {
            return findFromOne(str, start, end);
        }

        int ptr = 0; // pointer to compressed set data
        int offset = start;

        while (true) {
            // End of input String? Need to match the runt entry!
            if (offset == end) {
                if (data[ptr+1] == CHAR_NULL) {
                    return mWords[data[ptr+2] - NEGATIVE_OFFSET];
                }
                return null;
            }

            int count = data[ptr++];
            // Need to find the branch to follow, if any
            char c = str[offset++];

            inner_block:
            do { // dummy loop, need to have break
                // Linear or binary search?
                if (count < MIN_BINARY_SEARCH) {
                    // always at least two branches; never less
                    if (data[ptr] == c) {
                        ptr = (int) data[ptr+1];
                        break inner_block;
                    }
                    if (data[ptr+2] == c) {
                        ptr = (int) data[ptr+3];
                        break inner_block;
                    }
                    int branchEnd = ptr + (count << 1);
                    // Starts from entry #3, if such exists
                    for (ptr += 4; ptr < branchEnd; ptr += 2) {
                        if (data[ptr] == c) {
                            ptr = (int) data[ptr+1];
                            break inner_block;
                        }
                    }
                    return null; // No match!
                } else { // Ok, binary search:
                    int low = 0;
                    int high = count-1;
                    int mid;
                    
                    while (low <= high) {
                        mid = (low + high) >> 1;
                        int ix = ptr + (mid << 1);
                        int diff = data[ix] - c;
                        if (diff > 0) { // char was 'higher', need to go down
                            high = mid-1;
                        } else if (diff < 0) { // lower, need to go up
                            low = mid+1;
                        } else { // match (so far)
                            ptr = (int) data[ix+1];
                            break inner_block;
                        }
                    }
                    return null; // No match!
                }
            } while (false);

            // Ok; now, is it the end?
            if (ptr >= NEGATIVE_OFFSET) {
                String word = mWords[ptr - NEGATIVE_OFFSET];
                int expLen = (end - start);
                if (word.length() != expLen) {
                    return null;
                }
                for (int i = offset - start; offset < end; ++i, ++offset) {
                    if (word.charAt(i) != str[offset]) {
                        return null;
                    }
                }
                return word;
            }
        }
        // never gets here
    }

    private String findFromOne(char[] str, final int start, final int end)
    {
        String word = mWords[0];
        int len = end-start;
        if (word.length() != len) {
            return null;
        }
        for (int i = 0; i < len; ++i) {
            if (word.charAt(i) != str[start+i]) {
                return null;
            }
        }
        return word;
    }

    /**
     * @return (Shared) string instance of the word, if it exists in
     *   the word set; null if not.
     */
    @SuppressWarnings("cast")
	public String find(String str)
    {
        char[] data = mData;

        // 03-Jan-2006, TSa: Special case; one entry
        if (data == null) {
            String word = mWords[0];
            return word.equals(str) ? word : null;
        }

        int ptr = 0; // pointer to compressed set data
        int offset = 0;
        int end = str.length();

        while (true) {
            // End of input String? Need to match the runt entry!
            if (offset == end) {
                if (data[ptr+1] == CHAR_NULL) {
                    return mWords[data[ptr+2] - NEGATIVE_OFFSET];
                }
                return null;
            }

            int count = data[ptr++];
            // Need to find the branch to follow, if any
            char c = str.charAt(offset++);

            inner_block:
            do { // dummy loop, need to have break
                // Linear or binary search?
                if (count < MIN_BINARY_SEARCH) {
                    // always at least two branches; never less
                    if (data[ptr] == c) {
                        ptr = (int) data[ptr+1];
                        break inner_block;
                    }
                    if (data[ptr+2] == c) {
                        ptr = (int) data[ptr+3];
                        break inner_block;
                    }
                    int branchEnd = ptr + (count << 1);
                    // Starts from entry #3, if such exists
                    for (ptr += 4; ptr < branchEnd; ptr += 2) {
                        if (data[ptr] == c) {
                            ptr = (int) data[ptr+1];
                            break inner_block;
                        }
                    }
                    return null; // No match!
                } else { // Ok, binary search:
                    int low = 0;
                    int high = count-1;
                    int mid;
                    
                    while (low <= high) {
                        mid = (low + high) >> 1;
                        int ix = ptr + (mid << 1);
                        int diff = data[ix] - c;
                        if (diff > 0) { // char was 'higher', need to go down
                            high = mid-1;
                        } else if (diff < 0) { // lower, need to go up
                            low = mid+1;
                        } else { // match (so far)
                            ptr = (int) data[ix+1];
                            break inner_block;
                        }
                    }
                    return null; // No match!
                }
            } while (false);

            // Ok; now, is it the end?
            if (ptr >= NEGATIVE_OFFSET) {
                String word = mWords[ptr - NEGATIVE_OFFSET];
                if (word.length() != str.length()) {
                    return null;
                }
                for (; offset < end; ++offset) {
                    if (word.charAt(offset) != str.charAt(offset)) {
                        return null;
                    }
                }
                return word;
            }
        }
        // never gets here
    }

    /*
    ////////////////////////////////////////////////
    // Re-defined public methods
    ////////////////////////////////////////////////
     */

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder(16 + (mWords.length << 3));
        for (int i = 0, len = mWords.length; i < len; ++i) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(mWords[i]);
        }
        return sb.toString();
    }

    /*
    ////////////////////////////////////////////////
    // Helper classes
    ////////////////////////////////////////////////
     */

    private final static class Builder
    {
        final String[] mWords;

        char[] mData;

        /**
         * Number of characters currently used from mData
         */
        int mSize;

        public Builder(TreeSet<String> wordSet)
        {
            int wordCount = wordSet.size();

            mWords = new String[wordCount];
            wordSet.toArray(mWords);

            /* 03-Jan-2006, TSa: Special case: just one entry; if so,
             *   let's leave char array null, and just have the String
             *   array with one entry.
             */
            if (wordCount < 2) {
                if (wordCount == 0) {
                    throw new IllegalArgumentException(); // not legal
                }
                mData = null;
            } else {
                /* Let's guess approximate size we should need, assuming
                 * average word length of 6 characters, overhead matching
                 * compression (ie. about 1-to-1 ratio overall)
                 */
                int size = wordCount * 6;
                if (size < 256) {
                    size = 256;
                }
                mData = new char[size];
            }
        }

        /**
         * @return Raw character data that contains compressed structure
         *   of the word set
         */
        public WordResolver construct() 
        {
            char[] result;

            /* 03-Jan-2006, TSa: Special case: just one entry; if so,
             *   let's leave char array null, and just have the String
             *   array with one entry.
             */
            if (mData == null) {
                result = null;
            } else {
                constructBranch(0, 0, mWords.length);
                
                // Too big?
                if (mSize > NEGATIVE_OFFSET) {
                    return null;
                }
                
                result = new char[mSize];
                System.arraycopy(mData, 0, result, 0, mSize);
            }

            return new WordResolver(mWords, result);
        }

        /**
         * Method that is called recursively to build the data
         * representation for a branch, ie. part of word set tree
         * that still has more than one ending
         *
         * @param charIndex Index of the character in words to consider
         *   for this round
         * @param start Index of the first word to be processed
         * @param end Index of the word after last word to be processed
         *   (so that number of words is <code>end - start - 1</code>
         */
        @SuppressWarnings("cast")
		private void constructBranch(int charIndex, int start, int end)
        {
            // If more than one entry, need to divide into groups

            // First, need to add placeholder for branch count:
            if (mSize >= mData.length) {
                expand(1);
            }
            mData[mSize++] = 0; // placeholder!
            /* structStart will point to second char of first entry
             * (which will temporarily have entry count, eventually 'link'
             * to continuation)
             */
            int structStart = mSize + 1;
            int groupCount = 0;
            int groupStart = start;
            String[] words = mWords;
            boolean gotRunt;

            /* First thing we need to do is a special check for the
             * first entry -- it may be "runt" word, one that has no
             * more chars but also has a longer version ("id" vs.
             * "identifier"). If so, it needs to be marked; this is done
             * by adding a special entry before other entries (since such
             * entry would always be ordered first alphabetically)
             */
            if (words[groupStart].length() == charIndex) { // yup, got one:
                if ((mSize + 2) > mData.length) {
                    expand(2);
                }
                /* First null marks the "missing" char (or, end-of-word);
                 * and then we need the index
                 */
                mData[mSize++] = CHAR_NULL;
                mData[mSize++] = (char) (NEGATIVE_OFFSET + groupStart);

                // Ok, let's then ignore that entry
                ++groupStart;
                ++groupCount;
                gotRunt = true;
            } else {
                gotRunt = false;
            }

            // Ok, then, let's find the ('real') groupings:
            while (groupStart < end) {
                // Inner loop, let's find the group:
                char c = words[groupStart].charAt(charIndex);
                int j = groupStart+1;
                while (j < end && words[j].charAt(charIndex) == c) {
                    ++j;
                }
                /* Ok, let's store the char in there, along with count;
                 * count will be needed in second, and will then get
                 * overwritten with actual data later on
                 */
                if ((mSize + 2) > mData.length) {
                    expand(2);
                }
                mData[mSize++] = c;
                mData[mSize++] = (char) (j - groupStart); // entries in group
                groupStart = j;
                ++groupCount;
            }

            /* Ok, groups found; need to loop through them, recursively
             * calling branch and/or leaf methods
             */
            // first let's output the header, ie. group count:
            mData[structStart-2] = (char) groupCount;
            groupStart = start;

            // Do we have the "runt" to skip?
            if (gotRunt) {
                structStart += 2;
                ++groupStart;
            }

            int structEnd = mSize;
            ++charIndex;
            for (; structStart < structEnd; structStart += 2) {
                groupCount = (int) mData[structStart]; // no sign expansion, is ok
                /* Ok, count gotten, can either create a branch (if more than
                 * one entry) or leaf (just one entry)
                 */
                if (groupCount == 1) {
                    mData[structStart] = (char) (NEGATIVE_OFFSET + groupStart);
                } else {
                    mData[structStart] = (char) mSize;
                    constructBranch(charIndex, groupStart,
                                    groupStart + groupCount);
                }
                groupStart += groupCount;
            }

            // done!
        }

        private char[] expand(int needSpace)
        {
            char[] old = mData;
            int len = old.length;
            int newSize = len + ((len < 4096) ? len : (len >> 1));

            /* Let's verify we get enough; should always be true but
             * better safe than sorry
             */
            if (newSize < (mSize + needSpace)) {
                newSize = mSize + needSpace + 64;
            }
            mData = new char[newSize];
            System.arraycopy(old, 0, mData, 0, len);
            return mData;
        }
    }

    /*
    ////////////////////////////////////////////////////
    // Simple test driver, useful for debugging
    // (uncomment if needed -- commented out so it won't
    // affect coverage testing)
    ////////////////////////////////////////////////////
     */

    /*
    public static void main(String[] args)
    {
        if (args.length < 2) {
            System.err.println("Usage: "+WordResolver.class+" word1 [word2] ... [wordN] keyword");
            System.exit(1);
        }
        String key = args[args.length-1];
        TreeSet words = new TreeSet();
        for (int i = 0; i < args.length-1; ++i) {
            words.add(args[i]);
        }

        WordResolver set = WordResolver.constructInstance(words);

//outputData(set.mData);

        // Ok, and then the test!
        char[] keyA = new char[key.length() + 4];
        key.getChars(0, key.length(), keyA, 2);
        //System.out.println("Word '"+key+"' found via array search: "+WordResolver.find(data, keyA, 2, key.length() + 2));
        System.out.println("Word '"+key+"' found via array search: "+set.find(keyA, 2, key.length() + 2));
    }

    static void outputData(char[] data)
    {
        for (int i = 0; i < data.length; ++i) {
            char c = data[i];
            System.out.print(Integer.toHexString(i)+" ["+Integer.toHexString(c)+"]");
            if (c > 32 && c <= 127) { // printable char (letter)
                System.out.println(" -> '"+c+"'");
            } else {
                System.out.println();
            }
        }
    }
    */
}
