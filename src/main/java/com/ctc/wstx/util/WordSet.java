package com.ctc.wstx.util;

import java.util.*;

/**
 * An efficient (both memory and time) implementation of a Set used to
 * verify that a given
 * word is contained within the set. The general usage pattern is expected
 * to be such that most checks are positive, ie. that the word indeed
 * is contained in the set.
 *<p>
 * Performance of the set is comparable to that of {@link java.util.TreeSet}
 * for Strings, ie. 2-3x slower than {@link java.util.HashSet} when
 * using pre-constructed Strings. This is generally result of algorithmic
 * complexity of structures; Word and Tree sets are roughly logarithmic
 * to the whole data, whereas Hash set is linear to the length of key.
 * However:
 * <ul>
 *  <li>WordSet can use char arrays as keys, without constructing Strings.
 *     In cases where there is no (need for) Strings, WordSet seems to be
 *     about twice as fast, even without considering additional GC caused
 *     by temporary String instances.
 *   </li>
 *  <li>WordSet is more compact in its memory presentation; if Strings are
 *    shared its size is comparable to optimally filled HashSet, and if
 *    no such Strings exists, its much more compact (relatively speaking)
 *   </li>
 * </ul>
 *<p>
 * Although this is an efficient set for specific set of usage patterns,
 * one restriction is that the full set of words to include has to be
 * known before constructing the set. Also, the size of the set is
 * limited to total word content of about 20k characters; factory method
 * does verify the limit and indicates if an instance can not be created.
 */
public final class WordSet
{
    final static char CHAR_NULL = (char) 0;

    /**
     * Offset added to numbers to mark 'negative' numbers. Asymmetric,
     * since range of negative markers needed is smaller than positive
     * numbers...
     */
    final static int NEGATIVE_OFFSET = 0xC000;

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

    /*
    ////////////////////////////////////////////////
    // Life-cycle
    ////////////////////////////////////////////////
     */

    private WordSet(char[] data) {
        mData = data;
    }

    public static WordSet constructSet(TreeSet<String> wordSet)
    {
        return new WordSet(new Builder(wordSet).construct());
    }

    public static char[] constructRaw(TreeSet<String> wordSet)
    {
        return new Builder(wordSet).construct();
    }

    /*
    ////////////////////////////////////////////////
    // Public API
    ////////////////////////////////////////////////
     */

    public boolean contains(char[] buf, int start, int end) {
        return contains(mData, buf, start, end);
    }

    @SuppressWarnings("cast")
	public static boolean contains(char[] data, char[] str, int start, int end)
    {
        int ptr = 0; // pointer to compressed set data

        main_loop:
        do {
            int left = end-start;

            // End of input String? Need to have the run entry:
            if (left == 0) {
                return (data[ptr+1] == CHAR_NULL);
            }

            int count = data[ptr++];

            // Nope, but do we have an end marker?
            if (count >= NEGATIVE_OFFSET) {
                // How many chars do we need to have left to match?
                int expCount = count - NEGATIVE_OFFSET;
                if (left != expCount) {
                    return false;
                }
                while (start < end) {
                    if (data[ptr] != str[start]) {
                        return false;
                    }
                    ++ptr;
                    ++start;
                }
                return true;
            }

            // No, need to find the branch to follow, if any
            char c = str[start++];

            // Linear or binary search?
            if (count < MIN_BINARY_SEARCH) {
                // always at least two branches; never less
                if (data[ptr] == c) {
                    ptr = (int) data[ptr+1];
                    continue main_loop;
                }
                if (data[ptr+2] == c) {
                    ptr = (int) data[ptr+3];
                    continue main_loop;
                }
                int branchEnd = ptr + (count << 1);
                // Starts from entry #3, if such exists
                for (ptr += 4; ptr < branchEnd; ptr += 2) {
                    if (data[ptr] == c) {
                        ptr = (int) data[ptr+1];
                        continue main_loop;
                    }
                }
                return false; // No match!
            }

            { // Ok, binary search:
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
                    } else { // match
                        ptr = (int) data[ix+1];
                        continue main_loop;
                    }
                }
            }

            // If we fall here, no match!
            return false;

        } while (ptr != 0);

        // If we reached an end state, must match the length
        return (start == end);
    }

    public boolean contains(String str) {
        return contains(mData, str);
    }

    @SuppressWarnings("cast")
	public static boolean contains(char[] data, String str)
    {
        // Let's use same vars as array-based code, to allow cut'n pasting
        int ptr = 0; // pointer to compressed set data
        int start = 0;
        int end = str.length();

        main_loop:
        do {
            int left = end-start;

            // End of input String? Need to have the run entry:
            if (left == 0) {
                return (data[ptr+1] == CHAR_NULL);
            }

            int count = data[ptr++];

            // Nope, but do we have an end marker?
            if (count >= NEGATIVE_OFFSET) {
                // How many chars do we need to have left to match?
                int expCount = count - NEGATIVE_OFFSET;
                if (left != expCount) {
                    return false;
                }
                while (start < end) {
                    if (data[ptr] != str.charAt(start)) {
                        return false;
                    }
                    ++ptr;
                    ++start;
                }
                return true;
            }

            // No, need to find the branch to follow, if any
            char c = str.charAt(start++);

            // Linear or binary search?
            if (count < MIN_BINARY_SEARCH) {
                // always at least two branches; never less
                if (data[ptr] == c) {
                    ptr = (int) data[ptr+1];
                    continue main_loop;
                }
                if (data[ptr+2] == c) {
                    ptr = (int) data[ptr+3];
                    continue main_loop;
                }
                int branchEnd = ptr + (count << 1);
                // Starts from entry #3, if such exists
                for (ptr += 4; ptr < branchEnd; ptr += 2) {
                    if (data[ptr] == c) {
                        ptr = (int) data[ptr+1];
                        continue main_loop;
                    }
                }
                return false; // No match!
            }

            { // Ok, binary search:
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
                    } else { // match
                        ptr = (int) data[ix+1];
                        continue main_loop;
                    }
                }
            }

            // If we fall here, no match!
            return false;

        } while (ptr != 0);

        // If we reached an end state, must match the length
        return (start == end);
    }

    /*
    ////////////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////////////
     */

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

        public Builder(TreeSet<String> wordSet) {
            int wordCount = wordSet.size();
            mWords = new String[wordCount];
            wordSet.toArray(mWords);

            /* Let's guess approximate size we should need, assuming
             * average word length of 6 characters, and 100% overhead
             * in structure:
             */
            int size = wordCount * 12;
            if (size < 256) {
                size = 256;
            }
            mData = new char[size];
        }

        /**
         * @return Raw character data that contains compressed structure
         *   of the word set
         */
        public char[] construct() 
        {
// Uncomment if you need to debug array-out-of-bound probs
//try {
            // Let's check degenerate case of 1 word:
            if (mWords.length == 1) {
                constructLeaf(0, 0);
            } else {
                constructBranch(0, 0, mWords.length);
            }
//} catch (Throwable t) { System.err.println("Error: "+t); }

            char[] result = new char[mSize];
            System.arraycopy(mData, 0, result, 0, mSize);
            return result;
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

            /* First thing we need to do is a special check for the
             * first entry -- it may be "runt" word, one that has no
             * more chars but also has a longer version ("id" vs.
             * "identifier"). If there is such a word, it'll always
             * be first in alphabetic ordering:
             */
            if (words[groupStart].length() == charIndex) { // yup, got one:
                if ((mSize + 2) > mData.length) {
                    expand(2);
                }
                /* Nulls mark both imaginary branching null char and
                 * "missing link" to the rest
                 */
                mData[mSize++] = CHAR_NULL;
                mData[mSize++] = CHAR_NULL;

                // Ok, let's then ignore that entry
                ++groupStart;
                ++groupCount;
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
            if (mData[structStart] == CHAR_NULL) {
                structStart += 2;
                ++groupStart;
            }

            int structEnd = mSize;
            ++charIndex;
            for (; structStart < structEnd; structStart += 2) {
                groupCount = (int) mData[structStart]; // no sign expansion, is ok
                // Ok, count gotten, can now put the 'link' (pointer) in there
                mData[structStart] = (char) mSize;
                if (groupCount == 1) {
                    /* One optimization; if it'd lead to a single runt
                     * entry, we can just add 'null' link:
                     */
                    String word = words[groupStart];
                    if (word.length() == charIndex) {
                        mData[structStart] = CHAR_NULL;
                    } else { // otherwise, let's just create end state:
                        constructLeaf(charIndex, groupStart);
                    }
                } else {
                    constructBranch(charIndex, groupStart,
                                    groupStart + groupCount);
                }
                groupStart += groupCount;
            }

            // done!
        }

        /**
         * Method called to add leaf entry to word set; basically
         * "here is the rest of the only matching word"
         */
        private void constructLeaf(int charIndex, int wordIndex)
        {
            String word = mWords[wordIndex];
            int len = word.length();
            char[] data = mData;

            // need room for 1 header char, rest of the word
            if ((mSize + len + 1) >= data.length) {
                data = expand(len+1);
            }

            data[mSize++] = (char) (NEGATIVE_OFFSET + (len - charIndex));
            for (; charIndex < len; ++charIndex) {
                data[mSize++] = word.charAt(charIndex);
            }
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
}
