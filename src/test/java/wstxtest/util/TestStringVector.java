package wstxtest.util;

import junit.framework.TestCase;

import com.ctc.wstx.util.StringVector;

/**
 * Simple unit tests for testing {@link StringVector}.
 */
public class TestStringVector
    extends TestCase
{
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
}
