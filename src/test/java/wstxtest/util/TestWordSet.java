package wstxtest.util;

import java.util.*;

import junit.framework.TestCase;

import com.ctc.wstx.util.WordSet;

/**
 * Simple unit tests for testing {@link WordSet}.
 */
public class TestWordSet
    extends TestCase
{
    public TestWordSet(String name) {
        super(name);
    }

    public void testNormal()
    {
        TreeSet<String> set = new TreeSet<String>();

        set.add("word");
        set.add("123");
        set.add("len");
        set.add("length");
        set.add("leno");
        set.add("1");
        set.add("foobar");

        WordSet ws = WordSet.constructSet(set);

        // Let's first check if words that should be there, are:
        for (String str : set) {
            assertTrue(ws.contains(str));
            // And then, let's make sure intern()ing isn't needed:
            assertTrue(ws.contains(""+str));

            char[] strArr = str.toCharArray();
            char[] strArr2 = new char[strArr.length + 4];
            System.arraycopy(strArr, 0, strArr2, 3, strArr.length);
            assertTrue(ws.contains(strArr, 0, str.length()));
            assertTrue(ws.contains(strArr2, 3, str.length() + 3));
        }

        // And then that ones shouldn't be there aren't:
        checkNotFind(ws, "foo");

    }

    /*
    ///////////////////////////////////////////////////////
    // Private methods:
    ///////////////////////////////////////////////////////
     */

    private void checkNotFind(WordSet ws, String str)
    {
        char[] strArr = str.toCharArray();
        char[] strArr2 = new char[strArr.length + 4];
        System.arraycopy(strArr, 0, strArr2, 1, strArr.length);

        assertFalse(ws.contains(str));
        assertFalse(ws.contains(strArr, 0, strArr.length));
        assertFalse(ws.contains(strArr2, 1, strArr.length + 1));
    }
}

