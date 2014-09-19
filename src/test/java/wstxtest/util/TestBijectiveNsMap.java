package wstxtest.util;

import java.util.*;

import junit.framework.TestCase;

import com.ctc.wstx.util.BijectiveNsMap;

/**
 * Unit test to verify problem [WSTX-202].
 */
public class TestBijectiveNsMap
    extends TestCase
{
    public void testMaskingForFindPrefix() throws Exception
    {
        BijectiveNsMap nsMap = BijectiveNsMap.createEmpty();
        nsMap.addMapping("ns", "abc");
        assertEquals("ns", nsMap.findPrefixByUri("abc"));
        // and then let's mask it
        nsMap = nsMap.createChild();
        nsMap.addMapping("ns", "xyz");
        String uri = nsMap.findPrefixByUri("abc");
        if (uri != null) {
            fail("Expected null for masked prefix, got '"+uri+"'");
        }
    }

    public void testMaskingForGetBoundPrefixes() throws Exception
    {
        BijectiveNsMap nsMap = BijectiveNsMap.createEmpty();
        nsMap.addMapping("ns", "abc");
        List<?> l = nsMap.getPrefixesBoundToUri("abc", null);
        assertEquals(1, l.size());
        assertEquals("ns", l.iterator().next());

        // and then let's mask it
        nsMap = nsMap.createChild();
        nsMap.addMapping("ns", "xyz");
        assertEquals(0, nsMap.getPrefixesBoundToUri("abc", new ArrayList<String>()).size());

        // and finally, let's re-bind it
        nsMap = nsMap.createChild();
        nsMap.addMapping("ns", "abc");
        assertEquals(1, nsMap.getPrefixesBoundToUri("abc", null).size());

        // and add another similar binding
        nsMap.addMapping("ns2", "abc");
        assertEquals(2, nsMap.getPrefixesBoundToUri("abc", null).size());
    }
}
