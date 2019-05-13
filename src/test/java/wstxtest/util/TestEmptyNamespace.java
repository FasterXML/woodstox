package wstxtest.util;

import com.ctc.wstx.util.BijectiveNsMap;
import org.junit.Test;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import java.util.Iterator;

/**
 * @author I. Marcu
 */
public class TestEmptyNamespace {

    /**
     * Tests that {@link BijectiveNsMap#addGeneratedMapping(String, NamespaceContext, String, int[])} does not cause
     * an infinite loop when {@link NamespaceContext#getNamespaceURI(String)} returns {@link XMLConstants#NULL_NS_URI}
     * (which is a valid return value for an unbound prefix).
     */
    @Test(timeout = 5 * 1000)
    public void testAddGeneratedMappingWithNullNsUri() {
        NamespaceContext namespaceContext = new NamespaceContext() {

            public String getNamespaceURI(String prefix) {
                return XMLConstants.NULL_NS_URI;
            }

            public String getPrefix(String namespaceURI) {
                return null;
            }

            public Iterator getPrefixes(String namespaceURI) {
                return null;
            }
        };

        BijectiveNsMap map = BijectiveNsMap.createEmpty();

        // it should not get into an infinite loop
        map.addGeneratedMapping("prefix", namespaceContext, "http://some.url", new int[1]);
    }
}
