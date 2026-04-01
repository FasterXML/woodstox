package wstxtest.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import com.ctc.wstx.util.InternCache;

/**
 * Unit tests for {@link InternCache}.
 */
public class TestInternCache
    extends TestCase
{
    public void testBasicIntern()
    {
        InternCache cache = InternCache.getInstance();
        // Use new String() to ensure we're not passing an already-interned literal
        String input = new String("http://example.com/test-ns");
        String result = cache.intern(input);

        // Must return the interned version
        assertSame(input.intern(), result);

        // Calling again with a different String instance of same value must return same reference
        String input2 = new String("http://example.com/test-ns");
        assertSame(result, cache.intern(input2));
    }

    public void testConcurrentIntern() throws Exception
    {
        final InternCache cache = InternCache.getInstance();
        final int threadCount = 16;
        final int stringsPerThread = 100;

        // Create a shared set of strings that all threads will intern
        final String[] sharedStrings = new String[stringsPerThread];
        for (int i = 0; i < stringsPerThread; i++) {
            sharedStrings[i] = "urn:concurrent-test:ns:" + i;
        }

        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(threadCount);
        final String[][] results = new String[threadCount][stringsPerThread];
        final List<Throwable> errors = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadIdx = t;
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        startLatch.await();
                        for (int i = 0; i < stringsPerThread; i++) {
                            // Use new String() so each thread has its own instance
                            results[threadIdx][i] = cache.intern(new String(sharedStrings[i]));
                        }
                    } catch (Throwable e) {
                        synchronized (errors) {
                            errors.add(e);
                        }
                    } finally {
                        doneLatch.countDown();
                    }
                }
            });
            thread.setDaemon(true);
            thread.start();
        }

        // Release all threads at once
        startLatch.countDown();
        assertTrue("Threads did not complete in time", doneLatch.await(30, TimeUnit.SECONDS));
        assertTrue("Unexpected errors: " + errors, errors.isEmpty());

        // All threads must have gotten the exact same (identity-equal) interned String
        for (int i = 0; i < stringsPerThread; i++) {
            String expected = results[0][i];
            assertSame("Result must be interned", sharedStrings[i].intern(), expected);
            for (int t = 1; t < threadCount; t++) {
                assertSame("Thread " + t + " got different instance for string " + i,
                        expected, results[t][i]);
            }
        }
    }
}
