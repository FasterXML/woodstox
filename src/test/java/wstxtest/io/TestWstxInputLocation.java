package wstxtest.io;

import com.ctc.wstx.io.WstxInputLocation;

/**
 * Unit tests for {@link WstxInputLocation}, the {@link javax.xml.stream.Location}
 * implementation used by Woodstox readers.
 */
public class TestWstxInputLocation extends wstxtest.BaseWstxTest
{
    // ---------- basic getters ----------

    public void testGetters()
    {
        WstxInputLocation loc = new WstxInputLocation(null,
                "pub-id", "sys-id", 42L, 3, 7);
        assertEquals("pub-id", loc.getPublicId());
        assertEquals("sys-id", loc.getSystemId());
        assertEquals(42, loc.getCharacterOffset());
        assertEquals(42L, loc.getCharacterOffsetLong());
        assertEquals(3, loc.getLineNumber());
        assertEquals(7, loc.getColumnNumber());
        assertNull("no context expected", loc.getContext());
    }

    public void testCharacterOffsetTruncatedToInt()
    {
        // Long offset that doesn't fit in int — getCharacterOffset() truncates,
        // but getCharacterOffsetLong() preserves the full value.
        long bigOffset = ((long) Integer.MAX_VALUE) + 100L;
        WstxInputLocation loc = new WstxInputLocation(null,
                "p", "s", bigOffset, 0, 0);
        assertEquals(bigOffset, loc.getCharacterOffsetLong());
        // Truncated int reflects the low 32 bits cast
        assertEquals((int) bigOffset, loc.getCharacterOffset());
    }

    // ---------- empty location singleton ----------

    public void testEmptyLocationSingleton()
    {
        WstxInputLocation empty = WstxInputLocation.getEmptyLocation();
        assertNotNull(empty);
        // Sentinel offsets/rows/cols are -1
        assertEquals(-1, empty.getCharacterOffset());
        assertEquals(-1, empty.getLineNumber());
        assertEquals(-1, empty.getColumnNumber());
        // Subsequent calls return the same instance
        assertSame(empty, WstxInputLocation.getEmptyLocation());
    }

    // ---------- equals / hashCode ----------

    public void testEqualsSameOffsetAndIds()
    {
        WstxInputLocation a = new WstxInputLocation(null, "p", "s", 100L, 1, 2);
        // Different row/col, same offset+ids -> equal (equals uses offset only
        // for the numeric component)
        WstxInputLocation b = new WstxInputLocation(null, "p", "s", 100L, 9, 9);
        assertEquals(a, b);
        // hashCode mixes offset/row/col; equality of hashes is NOT guaranteed
        // by the equals contract here since equals ignores row/col, so we only
        // assert equal hash for fully identical objects.
        WstxInputLocation c = new WstxInputLocation(null, "p", "s", 100L, 1, 2);
        assertEquals(a, c);
        assertEquals(a.hashCode(), c.hashCode());
    }

    public void testEqualsDifferentOffset()
    {
        WstxInputLocation a = new WstxInputLocation(null, "p", "s", 100L, 1, 2);
        WstxInputLocation b = new WstxInputLocation(null, "p", "s", 101L, 1, 2);
        assertFalse(a.equals(b));
    }

    public void testEqualsDifferentPublicOrSystemId()
    {
        WstxInputLocation base = new WstxInputLocation(null, "p", "s", 100L, 1, 2);
        WstxInputLocation diffPub = new WstxInputLocation(null, "OTHER", "s", 100L, 1, 2);
        WstxInputLocation diffSys = new WstxInputLocation(null, "p", "OTHER", 100L, 1, 2);
        assertFalse(base.equals(diffPub));
        assertFalse(base.equals(diffSys));
    }

    public void testEqualsHandlesNullIdsOnOtherInstance()
    {
        // equals() normalizes null public/system ids on the OTHER side to "",
        // so a location with null ids should equal an otherwise-identical one
        // whose ids are empty strings.
        WstxInputLocation empty = new WstxInputLocation(null, "", "", 0L, 0, 0);
        // Cast disambiguates from the (String, SystemId, ...) overload.
        WstxInputLocation nulls = new WstxInputLocation(null, null, (String) null, 0L, 0, 0);
        // Note: 'empty' has "" stored, while 'nulls' has null stored. The
        // implementation only re-maps null on the OTHER side, so the result
        // depends on direction:
        assertTrue("empty should equal nulls (nulls' getters normalized to '')",
                empty.equals(nulls));
    }

    public void testEqualsRejectsNonLocation()
    {
        WstxInputLocation a = new WstxInputLocation(null, "p", "s", 0L, 0, 0);
        assertFalse(a.equals(null));
        assertFalse(a.equals("not a location"));
    }

    // ---------- toString / context ----------

    public void testToStringIncludesRowColAndSystemId()
    {
        WstxInputLocation loc = new WstxInputLocation(null, "pub", "sys", 0L, 5, 12);
        String s = loc.toString();
        assertTrue("expected row/col in '" + s + "'", s.contains("[5,12"));
        assertTrue("expected system id in '" + s + "'", s.contains("\"sys\""));
        assertTrue("expected system-id label in '" + s + "'",
                s.contains("system-id"));

        // toString is memoized — second call returns the same value
        assertSame(s, loc.toString());
    }

    public void testToStringWithContextChain()
    {
        WstxInputLocation parent = new WstxInputLocation(null, "p1", "sys1", 0L, 1, 1);
        WstxInputLocation child = new WstxInputLocation(parent, "p2", "sys2", 10L, 2, 3);
        String s = child.toString();
        // Child should reference its own info AND the parent's via " from "
        assertTrue("expected child row/col in '" + s + "'", s.contains("[2,3"));
        assertTrue("expected ' from ' linkage in '" + s + "'", s.contains(" from "));
        assertTrue("expected parent system id in '" + s + "'", s.contains("\"sys1\""));
    }

    public void testGetContextReturnsParent()
    {
        WstxInputLocation parent = new WstxInputLocation(null, "p1", "sys1", 0L, 1, 1);
        WstxInputLocation child = new WstxInputLocation(parent, "p2", "sys2", 10L, 2, 3);
        assertSame(parent, child.getContext());
    }
}
