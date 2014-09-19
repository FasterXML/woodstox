package com.ctc.wstx.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Singleton class that implements "fast intern" functionality, essentially
 * adding a layer that caches Strings that have been previously intern()ed,
 * but that probably shouldn't be added to symbol tables.
 * This is usually used by improving intern()ing of things like namespace
 * URIs.
 *<p>
 * Note: that this class extends {@link LinkedHashMap} is an implementation
 * detail -- no code should ever directly call Map methods.
 */
@SuppressWarnings("serial")
public final class InternCache extends LinkedHashMap<String,String>
{
    /**
     * Let's create cache big enough to usually have enough space for
     * all entries... (assuming NS URIs only)
     */
    private final static int DEFAULT_SIZE = 64;

    /**
     * Let's limit to hash area size of 1024.
     */
    private final static int MAX_SIZE = 660;

    private final static InternCache sInstance = new InternCache();

    private InternCache() {
        /* Let's also try to seriously minimize collisions... since
         * collisions are likely to be more costly here, with longer
         * Strings; so let's use 2/3 ratio (67%) instead of default
         * (75%)
         */
        super(DEFAULT_SIZE, 0.6666f, false);
    }

    public static InternCache getInstance() {
        return sInstance;
    }

    public String intern(String input)
    {
        String result;

        /* Let's split sync block to help in edge cases like
         * [WSTX-220]
         */
        synchronized (this) {
            result = get(input);
        }
        if (result == null) {
            result = input.intern();
            synchronized (this) {
                put(result, result);
            }
        }
        return result;
    }

    // We will force maximum size here (for [WSTX-237])
    @Override protected boolean removeEldestEntry(Map.Entry<String,String> eldest)
    {
        return size() > MAX_SIZE;
    }
}

