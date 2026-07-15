package wstxtest.wstream;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.stax2.*;
import org.junit.jupiter.api.Test;

/**
 * This unit test suite verifies that output-side content validation
 * works as expected, when enabled.
 */
public class TestContentValidation
    extends BaseWriterTest
{
    final String COMMENT_CONTENT_IN = "can not have -- in there";
    final String COMMENT_CONTENT_OUT = "can not have - - in there";

    final String CDATA_CONTENT_IN = "CData in: <![CDATA[text]]>";
    final String CDATA_CONTENT_OUT = "CData in: <![CDATA[text]]>";

    final String PI_CONTENT_IN = "this should end PI: ?> shouldn't it?";
    final String PI_CONTENT_OUT = "this should end PI: ?> shouldn't it?";

    /*
    ////////////////////////////////////////////////////
    // Main test methods
    ////////////////////////////////////////////////////
    */

    @Test
    public void testCommentChecking()
        throws XMLStreamException
    {
        for (int i = 0; i <= 2; ++i) {
            XMLOutputFactory2 f = getFactory(i, true, false);
            for (int enc = 0; enc < 3; ++enc) {
                XMLStreamWriter2 sw;
                
                if (enc == 0) {
                    StringWriter strw = new StringWriter();
                    sw = (XMLStreamWriter2) f.createXMLStreamWriter(strw);
                } else {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    String encStr = (enc == 1) ? "UTF-8" : "ISO-8859-1";
                    sw = (XMLStreamWriter2) f.createXMLStreamWriter(bos, encStr);
                }
                sw.writeStartDocument();
                sw.writeStartElement("root");
                try {
                    sw.writeComment(COMMENT_CONTENT_IN);
                    fail("Expected an XMLStreamException for illegal comment content (contains '--') in checking + non-fixing mode (type "+i+")");
                } catch (XMLStreamException sex) {
                    // good
                } catch (Throwable t) {
                    fail("Expected an XMLStreamException for illegal comment content (contains '--') in checking + non-fixing mode; got: "+t);
                }
            }
        }
    }

    @Test
    public void testCommentFixing()
        throws Exception
    {
        for (int i = 0; i <= 2; ++i) {
            XMLOutputFactory2 f = getFactory(i, true, true);
            
            /* 24-Aug-2006, TSa: Let's also test with output stream-based
             *    output... writers may use different code
             */
            for (int enc = 0; enc < 3; ++enc) {
                XMLStreamWriter2 sw;
                StringWriter strw = null;
                ByteArrayOutputStream bos = null;
                String encStr = null;
                
                if (enc == 0) {
                    strw = new StringWriter();
                    sw = (XMLStreamWriter2) f.createXMLStreamWriter(strw);
                } else {
                    bos = new ByteArrayOutputStream();
                    encStr = (enc == 1) ? "UTF-8" : "ISO-8859-1";
                        sw = (XMLStreamWriter2) f.createXMLStreamWriter(bos, encStr);
                }
                sw.writeStartDocument();
                sw.writeStartElement("root");
                /* now it should be ok, and result in one padded or
                 * 2 separate comments...
                 */
                sw.writeComment(COMMENT_CONTENT_IN);
                sw.writeEndElement();
                sw.writeEndDocument();
                sw.close();
                
                String output;
                if (strw != null) {
                    output = strw.toString();
                } else {
                    output = new String(bos.toByteArray(), encStr);
                }
                
                // so far so good; but let's ensure it also parses:
                XMLStreamReader sr = getReader(output);
                assertTokenType(START_ELEMENT, sr.next());
                assertTokenType(COMMENT, sr.next());
                StringBuilder sb = new StringBuilder();
                sb.append(getAndVerifyText(sr));
                
                // May get another one too...?
                int type;
                
                while ((type = sr.next()) == COMMENT) {
                    sb.append(getAndVerifyText(sr));
                }
                
                /* Ok... now, except for additional spaces, we should have
                 * about the same content:
                 */
                /* For now, since it's wstx-specific, let's just hard-code
                 * exactly what we are to get:
                 */
                String act = sb.toString();
                if (!COMMENT_CONTENT_OUT.equals(act)) {
                    failStrings("Failed to properly quote comment content (type "+i+")",
                                COMMENT_CONTENT_OUT, act);
                }
                assertTokenType(END_ELEMENT, type);
            }
        }
    }

    @Test
    public void testCDataChecking()
        throws Exception
    {
        for (int i = 0; i <= 2; ++i) {
            for (int itype = 0; itype < 2; ++itype) {
                XMLOutputFactory2 f = getFactory(i, true, false);
                
                /* 24-Aug-2006, TSa: Let's also test with output stream-based
                 *    output... writers may use different code
                 */
                for (int enc = 0; enc < 3; ++enc) {
                    XMLStreamWriter2 sw;
                    
                    if (enc == 0) {
                        StringWriter strw = new StringWriter();
                        sw = (XMLStreamWriter2) f.createXMLStreamWriter(strw);
                    } else {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        sw = (XMLStreamWriter2) f.createXMLStreamWriter
                            (bos, (enc == 1) ? "UTF-8" : "ISO-8859-1");
                    }
                    sw.writeStartDocument();
                    sw.writeStartElement("root");
                    try {
                        if (itype == 0) {
                            sw.writeCData(CDATA_CONTENT_IN);
                        } else {
                            char[] ch = CDATA_CONTENT_IN.toCharArray();
                            sw.writeCData(ch, 0, ch.length);
                        }
                        fail("Expected an XMLStreamException for illegal CDATA content (contains ']]>') in checking + non-fixing mode (type "+i+", itype "+itype+")");
                    } catch (XMLStreamException sex) {
                        // good
                    } catch (Exception t) {
                        fail("Expected an XMLStreamException for illegal CDATA content (contains ']]>') in checking + non-fixing mode; got: "+t);
                    }
                }
            }
        }
    }

    @Test
    public void testCDataFixing()
        throws Exception
    {
        for (int i = 0; i <= 2; ++i) {
            for (int itype = 0; itype < 2; ++itype) {
                XMLOutputFactory2 f = getFactory(i, true, true);
                
                /* 24-Aug-2006, TSa: Let's also test with output stream-based
                 *    output... writers may use different code
                 */
                for (int enc = 0; enc < 3; ++enc) {
                    XMLStreamWriter2 sw;
                    StringWriter strw = null;
                    ByteArrayOutputStream bos = null;
                    String encStr = null;
                    
                    if (enc == 0) {
                        strw = new StringWriter();
                        sw = (XMLStreamWriter2) f.createXMLStreamWriter(strw);
                    } else {
                        bos = new ByteArrayOutputStream();
                        encStr = (enc == 1) ? "UTF-8" : "ISO-8859-1";
                        sw = (XMLStreamWriter2) f.createXMLStreamWriter(bos, encStr);
                    }
                    
                    sw.writeStartDocument();
                    sw.writeStartElement("root");
                    /* now it should be ok, and result in two separate CDATA
                     * segments...
                     */
                    if (itype == 0) {
                        sw.writeCData(CDATA_CONTENT_IN);
                    } else {
                        char[] ch = CDATA_CONTENT_IN.toCharArray();
                        sw.writeCData(ch, 0, ch.length);
                    }
                    sw.writeEndElement();
                    sw.writeEndDocument();
                    sw.close();
                    
                    String output;
                    if (strw != null) {
                        output = strw.toString();
                    } else {
                        output = new String(bos.toByteArray(), encStr);
                    }
                    
                    // so far so good; but let's ensure it also parses:
                    XMLStreamReader sr = getReader(output);
                    assertTokenType(START_ELEMENT, sr.next());
                    int type = sr.next();
                    
                    assertTokenType(CDATA, type);
                    StringBuilder sb = new StringBuilder();
                    sb.append(getAndVerifyText(sr));
                    
                    // Should be getting one or more segments...
                    while ((type = sr.next()) == CDATA) {
                        sb.append(getAndVerifyText(sr));
                    }
                    
                    String act = sb.toString();
                    if (!CDATA_CONTENT_OUT.equals(act)) {
                        failStrings("Failed to properly quote CDATA content (type "+i+", itype "+itype+")",
                                    CDATA_CONTENT_OUT, act);
                    }
                    assertTokenType(END_ELEMENT, type);
                }
            }
        }
    }

    @Test
    public void testPIChecking()
        throws Exception
    {
        for (int i = 0; i <= 2; ++i) {
            XMLOutputFactory2 f = getFactory(i, true, false);
            
            /* 24-Aug-2006, TSa: Let's also test with output stream-based
             *    output... writers may use different code
             */
            for (int enc = 0; enc < 3; ++enc) {
                XMLStreamWriter2 sw;
                
                if (enc == 0) {
                    StringWriter strw = new StringWriter();
                    sw = (XMLStreamWriter2) f.createXMLStreamWriter(strw);
                } else {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    sw = (XMLStreamWriter2) f.createXMLStreamWriter
                        (bos, (enc == 1) ? "UTF-8" : "ISO-8859-1");
                }
                sw.writeStartDocument();
                sw.writeStartElement("root");
                try {
                    sw.writeProcessingInstruction("target", PI_CONTENT_IN);
                    fail("Expected an XMLStreamException for illegal PI content (contains '?>') in checking + non-fixing mode (type "+enc+")");
                } catch (XMLStreamException sex) {
                    // good
                } catch (Exception t) {
                    fail("Expected an XMLStreamException for illegal PI content (contains '?>') in checking + non-fixing mode; got: "+t);
                }
            }
        }
    }
    
    // // Note: no way (currently?) to fix PI content; thus, no test:

    final String COMMENT_TRAILING_HYPHEN_IN = "comment ends with-";
    final String COMMENT_TRAILING_HYPHEN_OUT = "comment ends with- ";

    /**
     * A comment must not end with a hyphen either: the trailing '-' merges
     * with the appended "-->" to form the illegal "--->" end marker. This is
     * checked for the StringWriter/UTF-8 paths, but the ISO-8859-1 and
     * US-ASCII paths use different writer classes.
     */
    @Test
    public void testCommentTrailingHyphenChecking()
        throws XMLStreamException
    {
        for (int i = 0; i <= 2; ++i) {
            XMLOutputFactory2 f = getFactory(i, true, false);
            for (int enc = 0; enc < 4; ++enc) {
                XMLStreamWriter2 sw = createWriter(f, enc, null);
                sw.writeStartDocument();
                sw.writeStartElement("root");
                try {
                    sw.writeComment(COMMENT_TRAILING_HYPHEN_IN);
                    fail("Expected an XMLStreamException for comment ending in '-' in checking + non-fixing mode (type "+i+", enc "+enc+")");
                } catch (XMLStreamException sex) {
                    // good
                } catch (Throwable t) {
                    fail("Expected an XMLStreamException for comment ending in '-' in checking + non-fixing mode; got: "+t);
                }
            }
        }
    }

    @Test
    public void testCommentTrailingHyphenFixing()
        throws Exception
    {
        for (int i = 0; i <= 2; ++i) {
            XMLOutputFactory2 f = getFactory(i, true, true);
            for (int enc = 0; enc < 4; ++enc) {
                ByteArrayOutputStream bos = (enc == 0) ? null : new ByteArrayOutputStream();
                String encStr = encodingFor(enc);
                StringWriter strw = (enc == 0) ? new StringWriter() : null;
                XMLStreamWriter2 sw = createWriter(f, enc, new Object[] { strw, bos });
                sw.writeStartDocument();
                sw.writeStartElement("root");
                sw.writeComment(COMMENT_TRAILING_HYPHEN_IN);
                sw.writeEndElement();
                sw.writeEndDocument();
                sw.close();

                String output = (strw != null) ? strw.toString()
                    : new String(bos.toByteArray(), encStr);

                // The fixed output must round-trip as a single comment:
                XMLStreamReader sr = getReader(output);
                assertTokenType(START_ELEMENT, sr.next());
                assertTokenType(COMMENT, sr.next());
                String act = getAndVerifyText(sr);
                if (!COMMENT_TRAILING_HYPHEN_OUT.equals(act)) {
                    failStrings("Failed to properly pad comment ending in '-' (type "+i+", enc "+enc+")",
                                COMMENT_TRAILING_HYPHEN_OUT, act);
                }
                assertTokenType(END_ELEMENT, sr.next());
            }
        }
    }

    /**
     * U+009F is a restricted character in XML 1.1 (like the rest of the C1
     * range 0x7F-0x9F, bar NEL 0x85). In comment/CDATA/PI content it can not
     * be escaped, so the ISO-8859-1 writer must reject it. The C1 rejection
     * range in ISOLatin1XmlWriter used '<' where the reader (ISOLatinReader)
     * and every sibling check use '<=', so 0x9F alone slipped through and was
     * emitted as a raw byte, which the reader then refuses.
     */
    @Test
    public void testXml11RestrictedC1InIsoLatin1()
        throws XMLStreamException
    {
        // 0x9F must be rejected; 0x9E is the adjacent already-rejected control
        for (int c1 = 0x9E; c1 <= 0x9F; ++c1) {
            for (int i = 0; i <= 2; ++i) {
                XMLOutputFactory2 f = getFactory(i, true, false);
                for (int kind = 0; kind < 4; ++kind) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    XMLStreamWriter2 sw = (XMLStreamWriter2) f.createXMLStreamWriter(bos, "ISO-8859-1");
                    sw.writeStartDocument("ISO-8859-1", "1.1");
                    sw.writeStartElement("root");
                    String content = "x" + ((char) c1) + "y";
                    try {
                        switch (kind) {
                        case 0:
                            sw.writeComment(content);
                            break;
                        case 1:
                            sw.writeCData(content);
                            break;
                        case 2:
                            char[] ch = content.toCharArray();
                            sw.writeCData(ch, 0, ch.length);
                            break;
                        default:
                            sw.writeProcessingInstruction("target", content);
                            break;
                        }
                        fail("Expected an XMLStreamException for restricted XML 1.1 char U+00"
                                +Integer.toHexString(c1).toUpperCase()
                                +" in unescapable ISO-8859-1 content (type "+i+", kind "+kind+")");
                    } catch (XMLStreamException sex) {
                        // good
                    }
                }
            }
        }
    }

    /**
     * The fix above must not touch valid ISO-8859-1 chars (0xA0-0xFF), which
     * remain legal literal XML 1.1 content.
     */
    @Test
    public void testXml11ValidHighLatin1Unaffected()
        throws Exception
    {
        for (int i = 0; i <= 2; ++i) {
            XMLOutputFactory2 f = getFactory(i, true, false);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            XMLStreamWriter2 sw = (XMLStreamWriter2) f.createXMLStreamWriter(bos, "ISO-8859-1");
            sw.writeStartDocument("ISO-8859-1", "1.1");
            sw.writeStartElement("root");
            String content = "x éÿy"; // NBSP, e-acute, y-diaeresis
            sw.writeCData(content);
            sw.writeEndElement();
            sw.writeEndDocument();
            sw.close();

            XMLStreamReader sr = getReader(new String(bos.toByteArray(), "ISO-8859-1"));
            assertTokenType(START_ELEMENT, sr.next());
            assertTokenType(CDATA, sr.next());
            if (!content.equals(getAndVerifyText(sr))) {
                fail("Valid high ISO-8859-1 CDATA content was altered (type "+i+")");
            }
        }
    }

    /*
////////////////////////////////////////////////////
// Internal methods
////////////////////////////////////////////////////
*/

    // enc: 0 -> StringWriter, 1 -> UTF-8, 2 -> ISO-8859-1, 3 -> US-ASCII
    private String encodingFor(int enc)
    {
        switch (enc) {
        case 1: return "UTF-8";
        case 2: return "ISO-8859-1";
        case 3: return "US-ASCII";
        default: return null;
        }
    }

    private XMLStreamWriter2 createWriter(XMLOutputFactory2 f, int enc, Object[] sinks)
        throws XMLStreamException
    {
        if (enc == 0) {
            StringWriter strw = (sinks == null) ? new StringWriter() : (StringWriter) sinks[0];
            return (XMLStreamWriter2) f.createXMLStreamWriter(strw);
        }
        ByteArrayOutputStream bos = (sinks == null) ? new ByteArrayOutputStream() : (ByteArrayOutputStream) sinks[1];
        return (XMLStreamWriter2) f.createXMLStreamWriter(bos, encodingFor(enc));
    }

    private XMLOutputFactory2 getFactory(int type, boolean checkAll, boolean fixAll)
        throws XMLStreamException
    {
        XMLOutputFactory2 f = getOutputFactory();
        // type 0 -> non-ns, 1 -> ns, non-repairing, 2 -> ns, repairing
        setNamespaceAware(f, type > 0); 
        setRepairing(f, type > 1); 
        setValidateAll(f, checkAll);
        setFixContent(f, fixAll);
        return f;
    }

    private XMLStreamReader getReader(String content)
        throws XMLStreamException
    {
        XMLInputFactory2 f = getInputFactory();
        setCoalescing(f, false);
        return f.createXMLStreamReader(new StringReader(content));
    }
}

