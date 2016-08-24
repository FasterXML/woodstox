package org.codehaus.stax.test.vstream;

import java.io.StringReader;
import java.util.*;

import javax.xml.stream.*;

/**
 * Unit test suite that tests various aspects of parameter entity resolution
 * in the external DTD subset.
 *
 * @author Tatu Saloranta
 */
public class TestParamEntities
    extends BaseVStreamTest
{
    /**
     * Test similar to one in xmltest (valid/not-sa/003.xml, specifically)
     */
    public void testExternalParamDeclViaPE()
        throws XMLStreamException
    {
        HashMap<String,String> m = new HashMap<String,String>();
        m.put("ent1", "<!ELEMENT doc EMPTY>\n"
            +"<!ENTITY % e SYSTEM 'ent2'>\n"
              +"<!ATTLIST doc a1 CDATA %e; 'v1'>");
        m.put("ent2", "");

        // Following should be ok; notations have been declared ok
        String XML = "<!DOCTYPE doc SYSTEM 'ent1'><doc />";

        XMLInputFactory f = getValidatingFactory(true);
        setResolver(f, new MyResolver(m));
        streamThrough(getValidatingReader(XML));
    }

    final static class MyResolver
        implements XMLResolver
    {
        final Map<String, String> mEntities;

        public MyResolver(Map<String, String> entities) {
            mEntities = entities;
        }
        
        @Override
        public Object resolveEntity(String publicID, String systemID, String baseURI, String namespace)
        {
            String str = (String) mEntities.get(publicID);
            if (str == null) {
                str = (String) mEntities.get(systemID);
            }
            return (str == null) ? null : new StringReader(str); 
        }
    }
}
