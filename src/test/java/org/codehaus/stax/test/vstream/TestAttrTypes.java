package org.codehaus.stax.test.vstream;

import java.util.HashMap;

import javax.xml.stream.*;

/**
 * Unit test suite that tests that attribute type information returned
 * for all recognized types is as expected
 */
public class TestAttrTypes
    extends BaseVStreamTest
{
    public void testAttrTypes()
        throws XMLStreamException
    {
        // Let's verify we get default value
        String XML = "<!DOCTYPE root [\n"
            +"<!ELEMENT root EMPTY>\n"
            +"<!ATTLIST root \n"
            +"attrCData CDATA #IMPLIED\n"
            +"attrId ID #IMPLIED\n"
            +"attrIdref IDREF #IMPLIED\n"
            +"attrIdrefs IDREFS #IMPLIED\n"
            +"attrEnum (val1| val2) #IMPLIED\n"
            +"attrEnt ENTITY #IMPLIED\n"
            +"attrEnts ENTITIES #IMPLIED\n"
            +"attrName NMTOKEN #IMPLIED\n"
            +"attrNames NMTOKENS #IMPLIED\n"
            +">\n"
            +"]>"
            +"<root "
            +"attrCData='1' "
            +"attrId='id' "
            +"attrIdref=\"id\" "
            +"attrIdrefs='id' "
            +"attrEnum='val1' "
            +"attrName='name' "
            +"attrNames='name1 name2' "
            +"/>";
        XMLStreamReader sr = getValidatingReader(XML, true);
        assertTokenType(DTD, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        int count = sr.getAttributeCount();
        assertEquals(7, count);

        HashMap<String,String> seen = new HashMap<String,String>();
        for (int i = 0; i < count; ++i) {
            String name = sr.getAttributeLocalName(i);
            String value = sr.getAttributeValue(i);
            String old = (String) seen.put(name, value);
            if (old != null) {
                fail("Duplicate attribute '"+name+"': previous value: '"+value+"'");
            }
            String type = sr.getAttributeType(i);
            if (name.equals("attrCData")) {
                assertEquals("CDATA", type);
            } else if (name.equals("attrId")) {
                assertEquals("ID", type);
            } else if (name.equals("attrIdref")) {
                assertEquals("IDREF", type);
            } else if (name.equals("attrIdrefs")) {
                assertEquals("IDREFS", type);
            } else if (name.equals("attrEnum")) {
                /* 25-Apr-2005, TSa: Not quite sure what would be the
                 *   "official" name for the enumerated type?
                 */
                assertEquals("ENUMERATED", type);
            } else if (name.equals("attrName")) {
                assertEquals("NMTOKEN", type);
            } else if (name.equals("attrNames")) {
                assertEquals("NMTOKENS", type);
            } else {
                fail("Unexpected attribute '"+name+"'");
            }
        }
    }
}
