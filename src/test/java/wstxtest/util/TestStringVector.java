package wstxtest.util;


import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.ctc.wstx.util.StringVector;
import org.junit.jupiter.api.Test;

/**
 * Simple unit tests for testing {@link StringVector}.
 */
public class TestStringVector
    extends wstxtest.BaseJUnit4Test
{
    @Test
    public void testConstructor()
    {
        try {
            new StringVector(0);
            fail("Should have thrown IllegalArgumentException for StringVector with initial count of zero");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            new StringVector(-1);
            fail("Should have thrown IllegalArgumentException for StringVector with negative initial count");
        } catch (IllegalArgumentException e) {
            // expected
        }

        StringVector sv = new StringVector(1);
        assertEquals(1, sv.getInternalArray().length);
        assertEquals(0, sv.size());
    }

    @Test
    public void testBasic()
    {
        StringVector sv = new StringVector(2);

        sv.addString("foo");
        sv.addString("xyz");
        assertEquals(2, sv.size());
        sv.addStrings("bar", "foo2");
        assertEquals(4, sv.size());
        sv.setString(3, "foo3");
        assertEquals(4, sv.size());
        assertEquals("foo3", sv.getString(3));

        sv.addString(new String("foo")); // so as to be different from entry 0
        sv.addString(new String("bar"));
        assertEquals("foo", sv.getString(4));
        // this uses identity
        assertEquals("xyz", sv.findLastFromMap("foo"));
        // and this equality
        assertEquals("bar", sv.findLastNonInterned("foo"));

        sv.clear(true);
        assertEquals(0, sv.size());
    }

    @Test
    public void testGetString()
    {
        StringVector sv = new StringVector(2);

        try {
            sv.getString(-1);
            fail("Should have thrown IllegalArgumentException for negative index");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            sv.getString(0);
            fail("Should have thrown IllegalArgumentException for index 0 in empty vector");
        } catch (IllegalArgumentException e) {
            // expected
        }

        sv.addString("foo");
        assertEquals("foo", sv.getString(0));

        try {
            sv.getString(1);
            fail("Should have thrown IllegalArgumentException for index 1 in vector with size 1");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testGetLastString()
    {
        StringVector sv = new StringVector(2);

        try {
            sv.getLastString();
            fail("Should have thrown IllegalStateException for empty vector");
        } catch (IllegalStateException e) {
            // expected
        }

        sv.addString("foo");
        assertEquals("foo", sv.getLastString());

        sv.addString("bar");
        assertEquals("bar", sv.getLastString());
    }

    @Test
    public void testGrowArray()
    {
        try {
            new StringVector(0);
            fail("Should have thrown IllegalArgumentException for StringVector with internal length of zero");
        } catch (IllegalArgumentException e) {
            // expected
        }

        StringVector sv = new StringVector(2);

        // Initial size is 2, so we can add two elements without growing
        sv.addString("foo");
        sv.addString("bar");
        assertEquals(2, sv.getInternalArray().length);

        // Adding a third element triples the array size
        sv.addString("baz");
        assertEquals(6, sv.getInternalArray().length);
        assertEquals("baz", sv.getString(2));

        // Adding more elements should continue to work without growing the array
        sv.addString("qux");
        assertEquals(6, sv.getInternalArray().length);
        assertEquals("qux", sv.getString(3));
    }

    // Map accessors switch to an index for big vectors: verify they still
    // match a plain scan as pairs are pushed and popped, as namespace
    // bindings are by the input element stack
    @Test
    public void testMapAccessWithManyPairs()
    {
        Random rnd = new Random(123);
        String[] keys = new String[50];
        for (int i = 0; i < keys.length; ++i) {
            keys[i] = ("k"+i).intern();
        }
        StringVector sv = new StringVector(4);
        List<String> ref = new ArrayList<String>();

        for (int round = 0; round < 20000; ++round) {
            if (ref.size() < 2 || rnd.nextInt(4) != 0) {
                String key = keys[rnd.nextInt(keys.length)];
                String value = (rnd.nextInt(10) == 0) ? null : ("v"+round);
                sv.addStrings(key, value);
                ref.add(key);
                ref.add(value);
            } else {
                int count = 2 * (1 + rnd.nextInt(Math.min(ref.size() / 2, 4)));
                sv.removeLast(count);
                ref.subList(ref.size() - count, ref.size()).clear();
            }
            assertEquals(ref.size(), sv.size());
            String key = keys[rnd.nextInt(keys.length)];
            String exp = findLast(ref, key);
            assertEquals(exp, sv.findLastFromMap(key));
            assertEquals(exp, sv.findLastNonInterned(new String(key)));
        }
    }

    @Test
    public void testMapAccessIdentityWithManyPairs()
    {
        StringVector sv = new StringVector(4);
        String key = "foo".intern();
        sv.addStrings(key, "a");
        for (int i = 0; i < 100; ++i) {
            sv.addStrings(("x"+i).intern(), "x");
        }
        sv.addStrings(new String("foo"), "b");
        // identity match skips the later, non-canonical key
        assertEquals("a", sv.findLastFromMap(key));
        // equality match does not
        assertEquals("b", sv.findLastNonInterned(key));
        // nor after the vector is used as a plain list
        sv.addString("y");
        sv.removeLast();
        assertEquals("a", sv.findLastFromMap(key));
        assertEquals("b", sv.findLastNonInterned(key));
    }

    private static String findLast(List<String> ref, String key) {
        for (int i = ref.size() - 2; i >= 0; i -= 2) {
            if (key.equals(ref.get(i))) {
                return ref.get(i+1);
            }
        }
        return null;
    }
}
