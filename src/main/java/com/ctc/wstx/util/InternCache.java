package com.ctc.wstx.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Singleton class that implements "fast intern" functionality, essentially
 * adding a layer that caches Strings that have been previously intern()ed,
 * but that probably shouldn't be added to symbol tables.
 * This is usually used by improving intern()ing of things like namespace
 * URIs.
 */
public final class InternCache
{
    /**
     * Let's create cache big enough to usually have enough space for
     * all entries... (assuming NS URIs only)
     */
    private final static int DEFAULT_SIZE = 64;

    /**
     * Let's limit size [WSTX-237]
     */
    private final static int MAX_SIZE = 660;

    private final static InternCache sInstance = new InternCache();

    private final ConcurrentHashMap<String,String> mCache;

    /**
     * Queue tracking insertion order for FIFO eviction when the
     * cache exceeds {@link #MAX_SIZE}.
     */
    private final ConcurrentLinkedQueue<String> mInsertionOrder;

    private InternCache() {
        /* Let's also try to seriously minimize collisions... since
         * collisions are likely to be more costly here, with longer
         * Strings; so let's use 2/3 ratio (67%) instead of default
         * (75%)
         */
        mCache = new ConcurrentHashMap<>(DEFAULT_SIZE, 0.6666f);
        mInsertionOrder = new ConcurrentLinkedQueue<>();
    }

    public static InternCache getInstance() {
        return sInstance;
    }

    public String intern(String input)
    {
        // Ideally, lock-free return from cache
        String result = mCache.get(input);
        if (result != null) {
            return result;
        }

        // If not found, intern the string (expensive) and attempt to add to cache
        result = input.intern();
        String prev = mCache.putIfAbsent(result, result);

        // If another thread added the same string in the meantime, use that one
        if (prev != null) {
            return prev;
        }

        // New entry added: track insertion order and evict oldest if needed
        mInsertionOrder.add(result);
        while (mCache.size() > MAX_SIZE) {
            String eldest = mInsertionOrder.poll();
            if (eldest == null) {
                // Queue is empty, should never happen in practice
                break;
            }
            mCache.remove(eldest);
        }
        return result;
    }
}
