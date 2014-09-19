/**
 * $Id$
 * 
 * (c) 2006 acrolinx GmbH All rights reserved.
 * 
 * Created on 09.04.2010 Last changed: $Date$
 * 
 * @author schwarz, last changed by $Author$
 * @version $Revision$
 */

package wstxtest.stream;

import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;

import com.ctc.wstx.api.WstxInputProperties;
import com.ctc.wstx.ent.EntityDecl;
import com.ctc.wstx.sr.BasicStreamReader;

/**
 * @author schwarz
 *
 */
public class TestTreatCharRefAsEnts 
    extends BaseStreamTest
{
    protected static void setTreatCharRefsAsEnts(XMLInputFactory f, boolean state) 
        throws XMLStreamException
    {
        f.setProperty(WstxInputProperties.P_TREAT_CHAR_REFS_AS_ENTS, 
                      state ? Boolean.TRUE : Boolean.FALSE);
    }

    public void testReturnEntityForCharReference() throws Exception
    {
        
        String XML = "<root>text &amp; more</root>";
        
        BasicStreamReader sr = getReader(XML, true, true, 1);

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());

        assertTokenType(CHARACTERS, sr.next());
        
        assertEquals("text ", sr.getText());
        
        assertTokenType(ENTITY_REFERENCE, sr.next());
        assertEquals("amp", sr.getLocalName());
        EntityDecl ed = sr.getCurrentEntityDecl();
        assertNotNull(ed);
        assertEquals("amp", ed.getName());
        assertEquals("&", ed.getReplacementText());

        // The pure stax way:
        assertEquals("&", sr.getText());

        // Finally, let's see that location info is about right?
        Location loc = sr.getCurrentLocation();
        assertNotNull(loc);
        assertEquals(16, loc.getCharacterOffset());
    }
    
    public void testReturnCharsReference() throws Exception
    {
        String XML = "<root>text &amp; more</root>";
        
        // 64 is the default
        BasicStreamReader sr = getReader(XML, true, false, 1);
    
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());
    
        assertTokenType(CHARACTERS, sr.next());
        assertEquals("text ", sr.getText());
        
        assertTokenType(CHARACTERS, sr.next());
        assertEquals("& more", sr.getText());
    }

    public void testReturnCharsReferenceWithHighMinTextSegment() throws Exception
    {
        String XML = "<root>text &amp; more</root>";
        
        // 64 is the default
        BasicStreamReader sr = getReader(XML, true, true, 64);

        assertTokenType(START_ELEMENT, sr.next());
        assertEquals("root", sr.getLocalName());

        assertTokenType(CHARACTERS, sr.next());
        
        assertEquals("text & more", sr.getText());
    }
    
    private BasicStreamReader getReader(String contents, boolean replEntities,
                                        boolean treatCharRefsAsEnts, int minTextSegment)
        throws XMLStreamException
    {
        XMLInputFactory f = getConfiguredFactory(replEntities, treatCharRefsAsEnts, minTextSegment);
        return (BasicStreamReader) constructStreamReader(f, contents);
    }
    
    private XMLInputFactory getConfiguredFactory(boolean replEntities, boolean treatCharRefsAsEnts, 
                                                 int minTextSegment)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setValidating(f, false);
        setReplaceEntities(f, replEntities);
        setTreatCharRefsAsEnts(f, treatCharRefsAsEnts);
        setMinTextSegment(f, minTextSegment);
        return f;
    }

}
