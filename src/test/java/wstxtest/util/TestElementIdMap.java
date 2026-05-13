package wstxtest.util;

import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;

import com.ctc.wstx.io.WstxInputLocation;
import com.ctc.wstx.util.ElementId;
import com.ctc.wstx.util.ElementIdMap;
import com.ctc.wstx.util.PrefixedName;
import com.ctc.wstx.util.SymbolTable;

/**
 * Unit tests for {@link ElementIdMap}, mirroring {@link TestSymbolTable}
 * since both classes share the seeded-hashing scheme added for issue #12.
 */
public class TestElementIdMap
    extends TestCase
{
    private static final PrefixedName ELEM = new PrefixedName(null, "elem");
    private static final PrefixedName ATTR = new PrefixedName(null, "attr");

    private static WstxInputLocation loc() {
        return new WstxInputLocation(null, null, "test", -1L, -1, -1);
    }

    public void testSeedsDifferAcrossInstances()
    {
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 32; ++i) {
            seen.add(new ElementIdMap().getHashSeed());
        }
        assertTrue("Expected ElementIdMap seeds to vary across instances, got: " + seen,
                seen.size() > 1);
    }

    public void testSeededHashMatchesInlineComputation()
    {
        ElementIdMap map = new ElementIdMap();
        int seed = map.getHashSeed();
        String key = "id-42";

        // What DTDIdAttr/DTDIdRefAttr/DTDIdRefsAttr do inline:
        int inline = seed ^ key.charAt(0);
        for (int i = 1, len = key.length(); i < len; ++i) {
            inline = (inline * 31) + key.charAt(i);
        }
        inline = SymbolTable.finalizeHash(inline);

        char[] buf = key.toCharArray();
        int viaCharBuf = ElementIdMap.calcHash(buf, 0, buf.length, seed);
        int viaString = ElementIdMap.calcHash(key, seed);

        assertEquals("char[] calcHash must equal inline compute + finalizer",
                inline, viaCharBuf);
        assertEquals("String calcHash must equal inline compute + finalizer",
                inline, viaString);
    }

    public void testBucketIndexVariesWithSeed()
    {
        final String key = "id-stable";
        final int mask = 0xFF;
        Set<Integer> indexes = new HashSet<>();
        for (int i = 0; i < 64; ++i) {
            int seed = new ElementIdMap().getHashSeed();
            indexes.add(ElementIdMap.calcHash(key, seed) & mask);
        }
        assertTrue("Bucket index must depend on seed; got: " + indexes,
                indexes.size() > 1);
    }

    public void testLookupRoundTripCharBuf()
    {
        // Verify the inline-compute path the DTD validators use end-to-end:
        // calcHash with the map's seed, then addDefined/addReferenced with
        // that hash, must locate the same entry on a second pass.
        ElementIdMap map = new ElementIdMap();
        int seed = map.getHashSeed();
        String[] ids = { "alpha", "beta", "gamma", "Aa", "BB", "x", "longish-id" };

        ElementId[] defined = new ElementId[ids.length];
        for (int i = 0; i < ids.length; ++i) {
            char[] buf = ids[i].toCharArray();
            int hash = ElementIdMap.calcHash(buf, 0, buf.length, seed);
            defined[i] = map.addDefined(buf, 0, buf.length, hash, loc(), ELEM, ATTR);
            assertNotNull(defined[i]);
            assertEquals(ids[i], defined[i].getId());
        }

        // Re-lookup as a *reference*: must return the already-defined entry,
        // not a new one. Identity equality proves the bucket index matched.
        for (int i = 0; i < ids.length; ++i) {
            char[] buf = ids[i].toCharArray();
            int hash = ElementIdMap.calcHash(buf, 0, buf.length, seed);
            ElementId found = map.addReferenced(buf, 0, buf.length, hash, loc(), ELEM, ATTR);
            assertSame("Reference lookup must hit the defined entry for " + ids[i],
                    defined[i], found);
        }
    }

    public void testLookupRoundTripString()
    {
        // Mirror of the char[] test, exercising the String overload used by
        // MSV-based validators (GenericMsvValidator).
        ElementIdMap map = new ElementIdMap();
        String[] ids = { "one", "two", "three", "Aa", "BB" };

        ElementId[] defined = new ElementId[ids.length];
        for (int i = 0; i < ids.length; ++i) {
            defined[i] = map.addDefined(ids[i], loc(), ELEM, ATTR);
            assertEquals(ids[i], defined[i].getId());
        }
        for (int i = 0; i < ids.length; ++i) {
            ElementId found = map.addReferenced(ids[i], loc(), ELEM, ATTR);
            assertSame("Reference lookup must hit defined entry for " + ids[i],
                    defined[i], found);
        }
    }

    public void testRehashPreservesLookups()
    {
        // MIN_SIZE is 16, so an initial size of 16 with 500 entries forces
        // multiple rehash() rounds, each of which must recompute hashes with
        // the same seed.
        ElementIdMap map = new ElementIdMap(16);
        final int n = 500;
        String[] ids = new String[n];
        ElementId[] defined = new ElementId[n];
        for (int i = 0; i < n; ++i) {
            ids[i] = "id-" + i;
            defined[i] = map.addDefined(ids[i], loc(), ELEM, ATTR);
        }
        for (int i = 0; i < n; ++i) {
            assertSame("Entry " + ids[i] + " must survive rehash",
                    defined[i], map.addReferenced(ids[i], loc(), ELEM, ATTR));
        }
    }
}
