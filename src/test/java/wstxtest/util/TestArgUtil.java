package wstxtest.util;

import java.util.*;

import junit.framework.TestCase;

import com.ctc.wstx.util.ArgUtil;

/**
 * Simple unit tests for testing methods in {@link ArgUtil}.
 */
public class TestArgUtil
    extends TestCase
{
    public void testBoolean()
    {
        assertFalse(ArgUtil.convertToBoolean("test", "false"));
        assertFalse(ArgUtil.convertToBoolean("test", "False"));
        assertFalse(ArgUtil.convertToBoolean("test", "FALSE"));
        assertTrue(ArgUtil.convertToBoolean("test", "true"));
        assertTrue(ArgUtil.convertToBoolean("test", "True"));
        assertTrue(ArgUtil.convertToBoolean("test", "TRUE"));
        assertFalse(ArgUtil.convertToBoolean("test", null));

        // and then errors:
        try {
            /*boolean b =*/ ArgUtil.convertToBoolean("test", 0);
            fail("Expected an IllegalArgumentException");
        } catch (IllegalArgumentException iae) { }

        try {
            /*boolean b =*/ ArgUtil.convertToBoolean("test", "foobar");
            fail("Expected an IllegalArgumentException");
        } catch (IllegalArgumentException iae) { }
    }

    public void testBooleanFromBoolean()
    {
        assertTrue(ArgUtil.convertToBoolean("test", Boolean.TRUE));
        assertFalse(ArgUtil.convertToBoolean("test", Boolean.FALSE));
    }

    public void testInt()
    {
        assertEquals(14, ArgUtil.convertToInt("test", "14", 0));
        assertEquals(14, ArgUtil.convertToInt("test", 14, 0));
        assertEquals(14, ArgUtil.convertToInt("test", 14L, 0));
        assertEquals(14, ArgUtil.convertToInt("test", (short) 14, 0));
        assertEquals(14, ArgUtil.convertToInt("test", (byte) 14, 0));

        // null is converted to 0 (and 0 must be >= minValue)
        assertEquals(0, ArgUtil.convertToInt("test", null, 0));
        assertEquals(0, ArgUtil.convertToInt("test", null, -10));

        // and then errors:
        try {
            /*int x =*/ ArgUtil.convertToInt("test", new HashMap<>(), 0);
            fail("Expected an IllegalArgumentException");
        } catch (IllegalArgumentException iae) { }

        try {
            /*int x =*/ ArgUtil.convertToInt("test", "foobar", 0);
            fail("Expected an IllegalArgumentException");
        } catch (IllegalArgumentException iae) { }
    }

    public void testIntBelowMinimum()
    {
        // Value below minValue should throw IllegalArgumentException
        try {
            ArgUtil.convertToInt("test", 3, 10);
            fail("Expected an IllegalArgumentException");
        } catch (IllegalArgumentException iae) {
            verifyException(iae, "minimum is 10");
        }

        // Same for a String that parses to below-minimum
        try {
            ArgUtil.convertToInt("test", "3", 10);
            fail("Expected an IllegalArgumentException");
        } catch (IllegalArgumentException iae) {
            verifyException(iae, "minimum is 10");
        }
    }

    public void testLong()
    {
        assertEquals(99L, ArgUtil.convertToLong("test", "99", 0L));
        assertEquals(99L, ArgUtil.convertToLong("test", 99L, 0L));
        assertEquals(99L, ArgUtil.convertToLong("test", 99, 0L));
        assertEquals(99L, ArgUtil.convertToLong("test", (short) 99, 0L));
        assertEquals(99L, ArgUtil.convertToLong("test", (byte) 99, 0L));

        // null becomes 0
        assertEquals(0L, ArgUtil.convertToLong("test", null, 0L));
        assertEquals(0L, ArgUtil.convertToLong("test", null, -10L));

        // bad String:
        try {
            ArgUtil.convertToLong("test", "not-a-number", 0L);
            fail("Expected an IllegalArgumentException");
        } catch (IllegalArgumentException iae) {
            verifyException(iae, "Long");
        }

        // unsupported type:
        try {
            ArgUtil.convertToLong("test", new HashMap<>(), 0L);
            fail("Expected an IllegalArgumentException");
        } catch (IllegalArgumentException iae) {
            verifyException(iae, "expected Long");
        }
    }

    public void testLongBelowMinimum()
    {
        try {
            ArgUtil.convertToLong("test", 3L, 10L);
            fail("Expected an IllegalArgumentException");
        } catch (IllegalArgumentException iae) {
            verifyException(iae, "minimum is 10");
        }
    }

    private void verifyException(Throwable e, String substr)
    {
        String msg = e.getMessage();
        assertNotNull("Exception message should not be null", msg);
        if (msg.indexOf(substr) < 0) {
            fail("Expected exception message to contain '" + substr
                    + "', got: " + msg);
        }
    }
}
