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
            /*boolean b =*/ ArgUtil.convertToBoolean("test", new Integer(0));
            fail("Expected an IllegalArgumentException");
        } catch (IllegalArgumentException iae) { }

        try {
            /*boolean b =*/ ArgUtil.convertToBoolean("test", "foobar");
            fail("Expected an IllegalArgumentException");
        } catch (IllegalArgumentException iae) { }
    }

    public void testInt()
    {
        assertEquals(14, ArgUtil.convertToInt("test", "14", 0));
        assertEquals(14, ArgUtil.convertToInt("test", new Integer(14), 0));
        assertEquals(14, ArgUtil.convertToInt("test", new Long(14L), 0));
        assertEquals(14, ArgUtil.convertToInt("test", new Short((short) 14), 0));
        assertEquals(14, ArgUtil.convertToInt("test", new Byte((byte) 14), 0));

        // and then errors:
        try {
            /*int x =*/ ArgUtil.convertToInt("test", new HashMap<Object,Object>(), 0);
            fail("Expected an IllegalArgumentException");
        } catch (IllegalArgumentException iae) { }

        try {
            /*int x =*/ ArgUtil.convertToInt("test", "foobar", 0);
            fail("Expected an IllegalArgumentException");
        } catch (IllegalArgumentException iae) { }
    }
}
