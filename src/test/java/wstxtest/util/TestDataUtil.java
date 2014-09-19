package wstxtest.util;

import java.util.*;

import junit.framework.TestCase;

import com.ctc.wstx.util.DataUtil;

/**
 * Simple unit tests for testing methods in {@link DataUtil}.
 */
public class TestDataUtil
    extends TestCase
{
    public void testBasic()
    {
        char[] empty = DataUtil.getEmptyCharArray();
        assertEquals(0, empty.length);
    }

    public void testContainment()
    {
        // First, no match:

        Collection<Object> c1 = new HashSet<Object>();
        c1.add("foo");
        c1.add(new String("bar"));
        Collection<Object> c2 = new ArrayList<Object>();
        c2.add("foobar");
        c2.add(new Integer(3));

        assertFalse(DataUtil.anyValuesInCommon(c1, c2));

        // Then a match
        c1.add(new Integer(3));
        assertTrue(DataUtil.anyValuesInCommon(c1, c2));

        // And another one:
        c2.clear();
        c2.add("bar");
        assertTrue(DataUtil.anyValuesInCommon(c1, c2));
    }

    public void testExpansion()
    {
        final int MAGIC_INDEX = 1;

        final int MAGIC_INT = 732;
        final String MAGIC_STRING = "yeehaw";

        int[] ia = new int[6];
        // let's also add a marker to test
        ia[MAGIC_INDEX] = MAGIC_INT;
        int[] ia2 = (int[]) DataUtil.growArrayBy50Pct(ia);
        assertEquals(9, ia2.length);
        assertEquals(MAGIC_INT, ia2[MAGIC_INDEX]);
        ia2 = (int[]) DataUtil.growArrayToAtLeast(ia, 7);
        if (ia2.length < 7) {
            fail("Expected array to grow to at least 7, was "+ia.length);
        }
        assertEquals(MAGIC_INT, ia2[MAGIC_INDEX]);
        ia2 = DataUtil.growArrayBy(ia, 2);
        assertEquals(8, ia2.length);
        assertEquals(MAGIC_INT, ia2[MAGIC_INDEX]);
        ia2 = DataUtil.growArrayBy((int[])null, 4);
        assertEquals(4, ia2.length);
        // no magic value, should just have 0
        assertEquals(0, ia2[MAGIC_INDEX]);

        String[] s1 = new String[10];
        s1[MAGIC_INDEX] = MAGIC_STRING;
        String[] s2 = (String[]) DataUtil.growArrayBy50Pct(s1);
        assertEquals(15, s2.length);
        assertEquals(MAGIC_STRING, s2[MAGIC_INDEX]);
        s2 = (String[]) DataUtil.growArrayToAtLeast(s1, 19);
        if (s2.length < 19) {
            fail("Expected array to grow to at least 19, was "+s2.length);
        }
        s2 = DataUtil.growArrayBy(s1, 3);
        assertEquals(13, s2.length);
        assertEquals(MAGIC_STRING, s2[MAGIC_INDEX]);
        s2 = DataUtil.growArrayBy((String[])null, 3);
        assertEquals(3, s2.length);
        // nothing to copy from
        assertNull(s2[MAGIC_INDEX]);

        // And then exceptions...
        try {
            s2 = (String[]) DataUtil.growArrayBy50Pct((String[])null);
            fail("Expected an IllegalArgumentException when passing null");
        } catch (IllegalArgumentException ie) {
            ; // good
        }
        try {
            s2 = (String[]) DataUtil.growArrayToAtLeast((String[])null, 5);
            fail("Expected an IllegalArgumentException when passing null");
        } catch (IllegalArgumentException ie) {
            ; // good
        }
    }
}
