package wstxtest.stream;

import java.util.Iterator;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.ctc.wstx.util.BijectiveNsMap;
import org.junit.jupiter.api.Timeout;

// note: does NOT extend base class (if it did, timeout wouldn't work for some reason)
//
// Contributed by I. Marcu (see [woodstox-core#74])
public class TestNamespaceImpl
{
    static class BadNDContext implements NamespaceContext {
        @Override
        public String getNamespaceURI(String prefix) {
            return XMLConstants.NULL_NS_URI;
        }

        @Override
        public String getPrefix(String namespaceURI) {
            return null;
        }

        @SuppressWarnings("rawtypes")
        @Override
        public Iterator getPrefixes(String namespaceURI) {
            return null;
        }
    }

    // [woodstox-core#74]
    @Test
    @Timeout(value = 2000, unit = java.util.concurrent.TimeUnit.MILLISECONDS)
    public void testAddGeneratedMappingWithDefaultNsPrefix() {

        BijectiveNsMap map = BijectiveNsMap.createEmpty();

        // it should not get into an infinite loop
        String prefix = map.addGeneratedMapping("prefix", new BadNDContext(), "http://some.url", new int[1]);
        Assertions.assertEquals("prefix0", prefix);
    }
}
