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
}
