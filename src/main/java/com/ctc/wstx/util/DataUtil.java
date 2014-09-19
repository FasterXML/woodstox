package com.ctc.wstx.util;

import java.lang.reflect.Array;
import java.util.*;

import org.codehaus.stax2.ri.EmptyIterator;
import org.codehaus.stax2.ri.SingletonIterator;

public final class DataUtil
{
    final static char[] EMPTY_CHAR_ARRAY = new char[0];

    /**
     * If baseline requirement was JDK 1.5, we wouldn't need to
     * cache Integer instances like this (since it has
     * Integer.valueOf() which does it); but until then, we
     * alas need our known canonicalization.
     */
    final static Integer[] INTS = new Integer[100];
    static {
        for (int i = 0; i < INTS.length; ++i) {
            INTS[i] = new Integer(i);
        }
    }
    final static Long MAX_LONG = new Long(Long.MAX_VALUE);

    private DataUtil() { }

    /*
    ////////////////////////////////////////////////////////////
    // Pooling for immutable objects
    ////////////////////////////////////////////////////////////
    */

    public static char[] getEmptyCharArray() {
        return EMPTY_CHAR_ARRAY;
    }

    public static Integer Integer(int i)
    {
        /* !!! 13-Sep-2008, TSa: JDK 1.5 can use Integer.valueOf(int)
         *   which does the same. When upgrading baseline, can get rid
         *   of this method.
         */
        if (i < 0 || i >= INTS.length) {
            return new Integer(i);
        }
        return INTS[i];
    }
    
    public static Long Long(long l)
    {
        if (l == Long.MAX_VALUE) {
            return MAX_LONG;
        }
        return new Long(l);
    }  

    /*
    ////////////////////////////////////////////////////////////
    // Empty/singleton thingies
    ////////////////////////////////////////////////////////////
    */

    @SuppressWarnings("unchecked")
    public static <T> Iterator<T> singletonIterator(T item) {
        return (Iterator<T>) new SingletonIterator(item);
    }

    @SuppressWarnings("unchecked")
    public static <T> Iterator<T> emptyIterator() {
        return (Iterator<T>) EmptyIterator.getInstance();
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
        String[] old = arr;
        int len = arr.length;
        arr = new String[len + more];
        System.arraycopy(old, 0, arr, 0, len);
        return arr;
    }

    public static int[] growArrayBy(int[] arr, int more)
    {
        if (arr == null) {
            return new int[more];
        }
        int[] old = arr;
        int len = arr.length;
        arr = new int[len + more];
        System.arraycopy(old, 0, arr, 0, len);
        return arr;
    }
}
 
