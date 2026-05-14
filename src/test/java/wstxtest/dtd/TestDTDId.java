package wstxtest.dtd;

import java.net.URI;
import java.net.URISyntaxException;

import com.ctc.wstx.dtd.DTDId;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DTDId}, the cache key used for parsed external DTDs.
 */
public class TestDTDId extends wstxtest.BaseWstxTest
{
    // ---------- construction: public-id only ----------

    @Test
    public void testConstructFromPublicId()
    {
        DTDId id = DTDId.constructFromPublicId("-//W3C//DTD HTML 4.01//EN", 0, false);
        assertNotNull(id);
        // toString must mention the public id and flag state
        String s = id.toString();
        assertTrue("toString should contain public id, was: " + s,
                s.contains("-//W3C//DTD HTML 4.01//EN"));
        assertTrue("toString should contain config flags, was: " + s,
                s.contains("config flags: 0x0"));
        assertTrue("toString should contain xml11 flag, was: " + s,
                s.contains("xml11: false"));
    }

    @Test
    public void testConstructFromPublicIdRejectsNull()
    {
        try {
            DTDId.constructFromPublicId(null, 0, false);
            fail("Expected IllegalArgumentException for null public id");
        } catch (IllegalArgumentException e) {
            verifyException(e, "public id");
        }
    }

    @Test
    public void testConstructFromPublicIdRejectsEmpty()
    {
        try {
            DTDId.constructFromPublicId("", 0, false);
            fail("Expected IllegalArgumentException for empty public id");
        } catch (IllegalArgumentException e) {
            verifyException(e, "public id");
        }
    }

    // ---------- construction: system-id only ----------

    @Test
    public void testConstructFromSystemId() throws URISyntaxException
    {
        URI sys = new URI("http://example.com/x.dtd");
        DTDId id = DTDId.constructFromSystemId(sys, 7, true);
        assertNotNull(id);
        String s = id.toString();
        assertTrue("toString should contain system id, was: " + s,
                s.contains("http://example.com/x.dtd"));
        assertTrue("toString should contain xml11=true, was: " + s,
                s.contains("xml11: true"));
        assertTrue("toString should show config flags as hex, was: " + s,
                s.contains("config flags: 0x7"));
    }

    @Test
    public void testConstructFromSystemIdRejectsNull()
    {
        try {
            DTDId.constructFromSystemId(null, 0, false);
            fail("Expected IllegalArgumentException for null system id");
        } catch (IllegalArgumentException e) {
            verifyException(e, "system id");
        }
    }

    // ---------- construct(publicId, systemId, ...) ----------

    @Test
    public void testConstructPrefersPublicIdWhenBothGiven() throws URISyntaxException
    {
        URI sys = new URI("http://example.com/x.dtd");
        DTDId id = DTDId.construct("PUBLIC-ID", sys, 0, false);
        // toString shows null system-id since public id won
        assertTrue(id.toString().contains("Public-id: PUBLIC-ID"));
        assertTrue(id.toString().contains("system-id: null"));
    }

    @Test
    public void testConstructFallsBackToSystemId() throws URISyntaxException
    {
        URI sys = new URI("file:/tmp/x.dtd");
        DTDId id = DTDId.construct(null, sys, 0, false);
        assertTrue(id.toString().contains("Public-id: null"));
        assertTrue(id.toString().contains("file:/tmp/x.dtd"));

        // Empty string for publicId is treated same as null
        id = DTDId.construct("", sys, 0, false);
        assertTrue(id.toString().contains("Public-id: null"));
        assertTrue(id.toString().contains("file:/tmp/x.dtd"));
    }

    @Test
    public void testConstructRejectsAllNullOrEmpty()
    {
        try {
            DTDId.construct(null, null, 0, false);
            fail("Expected IllegalArgumentException when both ids missing");
        } catch (IllegalArgumentException e) {
            verifyException(e, "null");
        }
        try {
            DTDId.construct("", null, 0, false);
            fail("Expected IllegalArgumentException when both ids missing");
        } catch (IllegalArgumentException e) {
            verifyException(e, "null");
        }
    }

    // ---------- equals / hashCode ----------

    @Test
    public void testEqualsByPublicId()
    {
        DTDId a = DTDId.constructFromPublicId("PUB", 0, false);
        DTDId b = DTDId.constructFromPublicId("PUB", 0, false);
        DTDId c = DTDId.constructFromPublicId("OTHER", 0, false);

        assertEquals(a, a);                  // reflexive
        assertEquals(a, b);                  // same public id + flags
        assertEquals(a.hashCode(), b.hashCode());
        assertFalse(a.equals(c));            // different public id
    }

    @Test
    public void testEqualsBySystemId() throws URISyntaxException
    {
        URI u1 = new URI("http://example.com/a.dtd");
        URI u2 = new URI("http://example.com/a.dtd");
        URI u3 = new URI("http://example.com/b.dtd");

        DTDId a = DTDId.constructFromSystemId(u1, 0, false);
        DTDId b = DTDId.constructFromSystemId(u2, 0, false);
        DTDId c = DTDId.constructFromSystemId(u3, 0, false);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertFalse(a.equals(c));
    }

    @Test
    public void testEqualsRespectsConfigFlagsAndXml11()
    {
        DTDId a = DTDId.constructFromPublicId("PUB", 0, false);
        DTDId differentFlags = DTDId.constructFromPublicId("PUB", 1, false);
        DTDId differentXml11 = DTDId.constructFromPublicId("PUB", 0, true);

        assertFalse("config flags must differentiate", a.equals(differentFlags));
        assertFalse("xml11 must differentiate", a.equals(differentXml11));
        // xml11 also flips the hash code
        assertFalse(a.hashCode() == differentXml11.hashCode());
    }

    @Test
    public void testEqualsRejectsOtherTypes()
    {
        DTDId a = DTDId.constructFromPublicId("PUB", 0, false);
        assertFalse(a.equals(null));
        assertFalse(a.equals("PUB"));
    }

    @Test
    public void testHashCodeIsCached()
    {
        DTDId a = DTDId.constructFromPublicId("PUB", 7, true);
        int h1 = a.hashCode();
        int h2 = a.hashCode();
        assertEquals("hashCode must be stable across calls", h1, h2);
        // And non-zero (the lazy-init sentinel is 0; PUB + flags + xml11 won't be 0)
        assertTrue("expected non-zero hash for non-empty inputs", h1 != 0);
    }
}
