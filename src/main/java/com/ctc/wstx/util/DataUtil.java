package com.ctc.wstx.util;

import java.lang.reflect.Array;
import java.util.*;

import org.codehaus.stax2.ri.SingletonIterator;

public final class DataUtil
{
    final static char[] EMPTY_CHAR_ARRAY = new char[0];

    final static Long MAX_LONG = new Long(Long.MAX_VALUE);

    // Replace with Java 7 `Collections.emptyIterator()` once we can use it
    private final static class EI implements Iterator<Object>
    {
        public final static Iterator<?> sInstance = new EI();

        @SuppressWarnings("unchecked")
        public static <T> Iterator<T> getInstance() { return (Iterator<T>) sInstance; }

        @Override
        public boolean hasNext() { return false; }

        @Override
        public Object next() {
            throw new java.util.NoSuchElementException();
        }

        @Override
        public void remove() {
            throw new IllegalStateException();
        }
    }

    private DataUtil() { }

    /*
    ////////////////////////////////////////////////////////////
    // Pooling for immutable objects
    ////////////////////////////////////////////////////////////
    */

    public static char[] getEmptyCharArray() {
        return EMPTY_CHAR_ARRAY;
    }

    // TODO: deprecate, not really needed post-JDK-1.4
    public static Integer Integer(int i) {
        return Integer.valueOf(i);
    }

    /*
    ////////////////////////////////////////////////////////////
    // Empty/singleton thingies
    ////////////////////////////////////////////////////////////
    */

    public static <T> Iterator<T> singletonIterator(T item) {
        // TODO: with JDK 1.7, can use method from Collections
        // TODO: alternatively, with Woodstox 5.1, can fix deprecation marker
        return SingletonIterator.create(item);
    }

    public static <T> Iterator<T> emptyIterator() {
        // TODO: with JDK 1.7, can use method from Collections
        return EI.getInstance();
    }

    /*
    ////////////////////////////////////////////////////////////
    // Methods for common operations on std data structs
    ////////////////////////////////////////////////////////////
    */

    /**
     * Method that can be used to efficiently check if 2 collections
     * share at least one common element.
     *
     * @return True if there is at least one element that's common
     *   to both Collections, ie. that is contained in both of them.
     */
    public static <T> boolean anyValuesInCommon(Collection<T> c1, Collection<T> c2)
    {
        // Let's always iterate over smaller collection:
        if (c1.size() > c2.size()) {
            Collection<T> tmp = c1;
            c1 = c2;
            c2 = tmp;
        }
        Iterator<T> it = c1.iterator();
        while (it.hasNext()) {
            if (c2.contains(it.next())) {
                return true;
            }
        }
        return false;
    }

    final static String NO_TYPE = "Illegal to pass null; can not determine component type";

    public static Object growArrayBy50Pct(Object arr)
    {
        if (arr == null) {
            throw new IllegalArgumentException(NO_TYPE);
        }
        Object old = arr;
        int len = Array.getLength(arr);
        arr = Array.newInstance(arr.getClass().getComponentType(), len + (len >> 1));
        System.arraycopy(old, 0, arr, 0, len);
        return arr;
    }

    /**
     * Method similar to {@link #growArrayBy50Pct}, but it also ensures that
     * the new size is at least as big as the specified minimum size.
     */
    public static Object growArrayToAtLeast(Object arr, int minLen)
    {
        if (arr == null) {
            throw new IllegalArgumentException(NO_TYPE);
        }
        Object old = arr;
        int oldLen = Array.getLength(arr);
        int newLen = oldLen + ((oldLen + 1) >> 1);
        if (newLen < minLen) {
            newLen = minLen;
        }
        arr = Array.newInstance(arr.getClass().getComponentType(), newLen);
        System.arraycopy(old, 0, arr, 0, oldLen);
        return arr;
    }

    public static String[] growArrayBy(String[] arr, int more)
    {
        if (arr == null) {
            return new String[more];
        }
        return Arrays.copyOf(arr, arr.length + more);
    }

    public static int[] growArrayBy(int[] arr, int more)
    {
        if (arr == null) {
            return new int[more];
        }
        return Arrays.copyOf(arr, arr.length + more);
    }
}
