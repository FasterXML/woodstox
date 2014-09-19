package wstxtest.util;

import java.util.*;

import junit.framework.TestCase;

import com.ctc.wstx.util.WordResolver;

/**
 * Simple unit tests for testing {@link WordResolver}.
 */
public class TestWordResolver
    extends TestCase
{
    public void testNormal()
    {
        checkResolver(new String[] {
            "word", "123", "len", "length",
            "leno", "1", "foobar",
        }, new String[] {
            "foo", "21", "__", "12", "lengt",
        });
    }

    /**
     * This unit test was created as a regression test, to check for
     * a bug that was found during development.
     */
    public void testSingle()
    {
        // this caused an arrayindexoutofbounds exception
        checkResolver(new String[] { "CDATA" },
                      new String[] { "value", "aaa", "ZZZ", "CDAT" });

        checkResolver(new String[] { "somethingelse" },
                      new String[] { "value", "aaa", "ZZZ", "CDAT" });
    }

    /**
     * This unit test tries to verify that things work ok with even bigger
     * word sets
     */
    public void testLarge()
    {
        // this caused an arrayindexoutofbounds exception
        checkResolver(new String[] {
            "a", "a1", "a2", "a4", "a5", "a6", "ab", "az", "a9", "aa", "ax",
            "c", "ca", "caa", "caaa", "caad", "caaa",
        }, new String[] {
            "a3", "aA", "a0", "b"
        });
    }

    /*
    ///////////////////////////////////////////////////////
    // Private methods:
    ///////////////////////////////////////////////////////
     */

    private void checkResolver(String[] words, String[] missingWords)
    {
        TreeSet<String> set = new TreeSet<String>();
        for (int i = 0, len = words.length; i < len; ++i) {
            set.add(words[i]);
        }

        WordResolver wr = WordResolver.constructInstance(set);

        assertEquals(wr.size(), set.size());

        Iterator<String> it = set.iterator();

        // Let's first check if words that should be there, are:
        while (it.hasNext()) {
            String str = it.next();

            assertEquals(str, wr.find(str));
            // And then, let's make sure intern()ing isn't needed:
            assertEquals(str, wr.find(""+str));

            char[] strArr = str.toCharArray();
            char[] strArr2 = new char[strArr.length + 4];
            System.arraycopy(strArr, 0, strArr2, 3, strArr.length);
            assertEquals(str, wr.find(strArr, 0, str.length()));
            assertEquals(str, wr.find(strArr2, 3, str.length() + 3));
        }

        // And then that ones shouldn't be there aren't:
        for (int i = 0, len = missingWords.length; i < len; ++i) {
            checkNotFind(wr, missingWords[i]);
        }
    }

    private void checkNotFind(WordResolver wr, String str)
    {
        char[] strArr = str.toCharArray();
        char[] strArr2 = new char[strArr.length + 4];
        System.arraycopy(strArr, 0, strArr2, 1, strArr.length);

        assertNull(wr.find(str));
        assertNull(wr.find(strArr, 0, strArr.length));
        assertNull(wr.find(strArr2, 1, strArr.length + 1));
    }

}

