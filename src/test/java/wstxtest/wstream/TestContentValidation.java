package wstxtest.wstream;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.stax2.*;

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
                StringBuffer sb = new StringBuffer();
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
                    StringBuffer sb = new StringBuffer();
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


    /*
////////////////////////////////////////////////////
// Internal methods
////////////////////////////////////////////////////
*/

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

