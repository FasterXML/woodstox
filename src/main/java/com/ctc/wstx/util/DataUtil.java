package com.ctc.wstx.util;

import java.lang.reflect.Array;
import java.util.*;

import org.codehaus.stax2.ri.SingletonIterator;

public final class DataUtil
{
    final static char[] EMPTY_CHAR_ARRAY = new char[0];

    final static Long MAX_LONG = new Long(Long.MAX_VALUE);

    /**
     * Due to [woodstox#10], we will need to use a work-around which (for now)
     * includes a local copy of this iterator class.
     *<p>
     * TODO: Once we get to Java 7 / JDK 1.7, replace with one from Collections
     * 
     * @since 5.0.1
     */
    private final static class EmptyIterator implements Iterator<Object>
    {
        public final static Iterator<?> INSTANCE = new EmptyIterator();

        @Override
        public boolean hasNext() { return false; }
        @Override
        public Object next() { throw new java.util.NoSuchElementException(); }
        @Override
        public void remove() { throw new IllegalStateException(); }
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

    @Deprecated // since 5.0.1
    public static Long Long(long l)
    {
        if (l == Long.MAX_VALUE) {
            return MAX_LONG;
        }
        return Long.valueOf(l);
    }

    /*
    ////////////////////////////////////////////////////////////
    // Empty/singleton thingies
    ////////////////////////////////////////////////////////////
    */

    @SuppressWarnings("unchecked")
    public static <T> Iterator<T> singletonIterator(T item) {
        // TODO: with JDK 1.7, can use method from Collections
        // TODO: alternatively, with Woodstox 5.1, can fix deprecation marker
        return (Iterator<T>) new SingletonIterator(item);
    }

    @SuppressWarnings("unchecked")
    public static <T> Iterator<T> emptyIterator() {
        // TODO: with JDK 1.7, can use method from Collections
        return (Iterator<T>) EmptyIterator.INSTANCE;
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
