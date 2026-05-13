package wstxtest.util;

import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;

import com.ctc.wstx.util.SymbolTable;

/**
 * Unit tests for {@link SymbolTable}, focused on the seeded hashing
 * introduced for woodstox issue #12 (hash-flooding defense).
 */
public class TestSymbolTable
    extends TestCase
{
    public void testSeedsDifferAcrossInstances()
    {
        // 32-bit random seeds should be effectively unique across a small
        // number of fresh instances; if many of these collide, ThreadLocalRandom
        // is broken.
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 32; ++i) {
            seen.add(new SymbolTable().getHashSeed());
        }
        assertTrue("Expected SymbolTable seeds to vary across instances, got: " + seen,
                seen.size() > 1);
    }

    public void testChildInheritsParentSeed()
    {
        SymbolTable master = new SymbolTable();
        SymbolTable child = master.makeChild();
        assertEquals("Child must reuse parent's seed so it can read parent's buckets",
                master.getHashSeed(), child.getHashSeed());
    }

    public void testSeededHashMatchesInlineComputation()
    {
        SymbolTable table = new SymbolTable();
        int seed = table.getHashSeed();
        String key = "elementName";

        // Recreate what StreamScanner does inline while parsing a name:
        int inline = seed ^ key.charAt(0);
        for (int i = 1, len = key.length(); i < len; ++i) {
            inline = (inline * 31) + key.charAt(i);
        }
        inline = SymbolTable.finalizeHash(inline);

        char[] buf = key.toCharArray();
        int viaCharBuf = SymbolTable.calcHash(buf, 0, buf.length, seed);
        int viaString = SymbolTable.calcHash(key, seed);

        assertEquals("char[] calcHash must equal inline compute + finalizer",
                inline, viaCharBuf);
        assertEquals("String calcHash must equal inline compute + finalizer",
                inline, viaString);
    }

    public void testBucketIndexVariesWithSeed()
    {
        // Hash-flooding defense rests on the attacker not being able to predict
        // which bucket a given key lands in. Verify that for one fixed key, the
        // low-bit "bucket index" reached across many independently-seeded
        // tables actually varies — i.e. the seed is genuinely folded into the
        // bits an attacker would target.
        final String key = "elementName";
        final int mask = 0xFF;
        Set<Integer> indexes = new HashSet<>();
        for (int i = 0; i < 64; ++i) {
            int seed = new SymbolTable().getHashSeed();
            indexes.add(SymbolTable.calcHash(key, seed) & mask);
        }
        assertTrue("Bucket index must depend on seed; got: " + indexes,
                indexes.size() > 1);
    }

    public void testSeedSeparatesLegacyCollisionPair()
    {
        // "Aa" and "BB" share the legacy multiply-31 hash. The XOR-with-seed
        // scheme can't break the collision for every seed (low-bit collapses
        // survive), but it must break it for the *majority* of seeds —
        // empirically ~75%. Verify the rate stays above a safe floor.
        final int trials = 2000;
        int separated = 0;
        java.util.Random rng = new java.util.Random(42);
        for (int i = 0; i < trials; ++i) {
            int seed = rng.nextInt();
            if (SymbolTable.calcHash("Aa", seed) != SymbolTable.calcHash("BB", seed)) {
                ++separated;
            }
        }
        // Expected rate is 3/4 (1500); leave generous headroom for variance.
        assertTrue("Seed should break the Aa/BB collision for most seeds, but only "
                + separated + " of " + trials + " did", separated > 1200);
    }

    public void testLookupRoundTripWithRandomSeed()
    {
        SymbolTable table = new SymbolTable(false);
        int seed = table.getHashSeed();
        String[] keys = {
            "alpha", "beta", "gamma", "delta", "epsilon",
            "Aa", "BB", "x", "longer-element-name", "ns:prefixed"
        };
        String[] interned = new String[keys.length];
        for (int i = 0; i < keys.length; ++i) {
            char[] buf = keys[i].toCharArray();
            int hash = SymbolTable.calcHash(buf, 0, buf.length, seed);
            interned[i] = table.findSymbol(buf, 0, buf.length, hash);
            assertEquals(keys[i], interned[i]);
        }
        // Second pass: same hash + chars must return identity-equal String.
        for (int i = 0; i < keys.length; ++i) {
            char[] buf = keys[i].toCharArray();
            int hash = SymbolTable.calcHash(buf, 0, buf.length, seed);
            assertSame("Second lookup must return the interned instance for " + keys[i],
                    interned[i], table.findSymbol(buf, 0, buf.length, hash));
        }
    }

    public void testRehashPreservesLookups()
    {
        // Force well past the default fill threshold (96 entries) so rehash runs.
        SymbolTable table = new SymbolTable(false, 16);
        final int n = 500;
        String[] keys = new String[n];
        for (int i = 0; i < n; ++i) {
            keys[i] = "name-" + i;
            table.findSymbol(keys[i]);
        }
        // All entries must still be retrievable after multiple rehashes.
        for (int i = 0; i < n; ++i) {
            assertEquals(keys[i], table.findSymbol(keys[i]));
        }
        assertEquals(n, table.size());
    }
}
