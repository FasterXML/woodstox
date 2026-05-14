package wstxtest;

import org.junit.jupiter.api.Assertions;

/**
 * Base class providing JUnit 4-style static assertion API (with the
 * {@code String message} parameter first) on top of JUnit 5 {@code Assertions}.
 *<p>
 * Test base classes inherit from this so the bulk of existing test code can
 * keep its existing assertion call sites without flipping argument order.
 *<p>
 * Note: file name starts with {@code Base} which is excluded by Surefire,
 * so this class is not picked up as a test itself.
 */
public abstract class BaseJUnit4Test
{
    // // // fail

    public static void fail() {
        Assertions.fail();
    }

    public static void fail(String message) {
        Assertions.fail(message);
    }

    public static void fail(String message, Throwable t) {
        Assertions.fail(message, t);
    }

    // // // assertTrue / assertFalse

    public static void assertTrue(boolean condition) {
        Assertions.assertTrue(condition);
    }

    public static void assertTrue(String message, boolean condition) {
        Assertions.assertTrue(condition, message);
    }

    public static void assertFalse(boolean condition) {
        Assertions.assertFalse(condition);
    }

    public static void assertFalse(String message, boolean condition) {
        Assertions.assertFalse(condition, message);
    }

    // // // assertNull / assertNotNull

    public static void assertNull(Object obj) {
        Assertions.assertNull(obj);
    }

    public static void assertNull(String message, Object obj) {
        Assertions.assertNull(obj, message);
    }

    public static void assertNotNull(Object obj) {
        Assertions.assertNotNull(obj);
    }

    public static void assertNotNull(String message, Object obj) {
        Assertions.assertNotNull(obj, message);
    }

    // // // assertSame / assertNotSame

    public static void assertSame(Object expected, Object actual) {
        Assertions.assertSame(expected, actual);
    }

    public static void assertSame(String message, Object expected, Object actual) {
        Assertions.assertSame(expected, actual, message);
    }

    public static void assertNotSame(Object expected, Object actual) {
        Assertions.assertNotSame(expected, actual);
    }

    public static void assertNotSame(String message, Object expected, Object actual) {
        Assertions.assertNotSame(expected, actual, message);
    }

    // // // assertEquals - Object

    public static void assertEquals(Object expected, Object actual) {
        Assertions.assertEquals(expected, actual);
    }

    public static void assertEquals(String message, Object expected, Object actual) {
        Assertions.assertEquals(expected, actual, message);
    }

    // // // assertEquals - long (covers int, short, byte via widening)

    public static void assertEquals(long expected, long actual) {
        Assertions.assertEquals(expected, actual);
    }

    public static void assertEquals(String message, long expected, long actual) {
        Assertions.assertEquals(expected, actual, message);
    }

    // // // assertEquals - double / float (with delta)

    public static void assertEquals(double expected, double actual, double delta) {
        Assertions.assertEquals(expected, actual, delta);
    }

    public static void assertEquals(String message, double expected, double actual, double delta) {
        Assertions.assertEquals(expected, actual, delta, message);
    }

    public static void assertEquals(float expected, float actual, float delta) {
        Assertions.assertEquals(expected, actual, delta);
    }

    public static void assertEquals(String message, float expected, float actual, float delta) {
        Assertions.assertEquals(expected, actual, delta, message);
    }

    // // // assertEquals - char

    public static void assertEquals(char expected, char actual) {
        Assertions.assertEquals(expected, actual);
    }

    public static void assertEquals(String message, char expected, char actual) {
        Assertions.assertEquals(expected, actual, message);
    }

    // // // assertEquals - boolean

    public static void assertEquals(boolean expected, boolean actual) {
        Assertions.assertEquals(expected, actual);
    }

    public static void assertEquals(String message, boolean expected, boolean actual) {
        Assertions.assertEquals(expected, actual, message);
    }

    // // // assertArrayEquals

    public static void assertArrayEquals(Object[] expected, Object[] actual) {
        Assertions.assertArrayEquals(expected, actual);
    }

    public static void assertArrayEquals(String message, Object[] expected, Object[] actual) {
        Assertions.assertArrayEquals(expected, actual, message);
    }

    public static void assertArrayEquals(byte[] expected, byte[] actual) {
        Assertions.assertArrayEquals(expected, actual);
    }

    public static void assertArrayEquals(String message, byte[] expected, byte[] actual) {
        Assertions.assertArrayEquals(expected, actual, message);
    }

    public static void assertArrayEquals(int[] expected, int[] actual) {
        Assertions.assertArrayEquals(expected, actual);
    }

    public static void assertArrayEquals(String message, int[] expected, int[] actual) {
        Assertions.assertArrayEquals(expected, actual, message);
    }

    public static void assertArrayEquals(char[] expected, char[] actual) {
        Assertions.assertArrayEquals(expected, actual);
    }

    public static void assertArrayEquals(String message, char[] expected, char[] actual) {
        Assertions.assertArrayEquals(expected, actual, message);
    }
}
