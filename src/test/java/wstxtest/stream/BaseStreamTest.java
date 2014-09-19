package wstxtest.stream;

import java.io.*;
import java.util.Random;

import javax.xml.stream.*;

import org.codehaus.stax2.XMLStreamReader2;

import wstxtest.BaseWstxTest;
import wstxtest.cfg.*;

public abstract class BaseStreamTest
    extends BaseWstxTest
{
    protected BaseStreamTest() { super(); } 

    /*
    ///////////////////////////////////////////////////////////
    // "Special" accessors
    ///////////////////////////////////////////////////////////
     */

    /**
     * Method that not only gets currently available text from the 
     * reader, but also checks that its consistenly accessible using
     * different StAX methods.
     */
    protected static String getAndVerifyText(XMLStreamReader sr)
        throws XMLStreamException
    {
        int expLen = sr.getTextLength();
        // Hmmh. It's only ok to return empty text for DTD event
        if (sr.getEventType() != DTD) {
            assertTrue("Stream reader should never return empty Strings.",  (expLen > 0));
        }
        String text = sr.getText();
        assertNotNull("getText() should never return null.", text);
        assertEquals(expLen, text.length());
        char[] textChars = sr.getTextCharacters();
        int start = sr.getTextStart();
        String text2 = new String(textChars, start, expLen);
        assertEquals(text, text2);
        return text;
    }

    protected static String getStreamingText(XMLStreamReader sr)
        throws IOException, XMLStreamException
    {
        StringWriter sw = new StringWriter();
        ((XMLStreamReader2) sr).getText(sw, false);
        return sw.toString();
    }

    /*
    ///////////////////////////////////////////////////////////
    // Higher-level test methods
    ///////////////////////////////////////////////////////////
     */

    protected int streamAndCheck(XMLInputFactory f, InputConfigIterator it,
                                 String input, String expOutput,
				 boolean reallyStreaming)
        throws IOException, XMLStreamException, UnsupportedEncodingException
    {
        int count = 0;

        // Let's loop couple of input methods
        for (int m = 0; m < 3; ++m) {
            XMLStreamReader sr;
            
            /* Contents shouldn't really contain anything
             * outside ISO-Latin; however, detection may
             * be tricky.. so let's just test with UTF-8,
             * for now?
             */

            switch (m) {
            case 0: // simple StringReader:
                sr = constructStreamReader(f, input);
                break;
            case 1: // via InputStream and auto-detection
                {
                    ByteArrayInputStream bin = new ByteArrayInputStream
                        (input.getBytes("UTF-8"));
                    sr = f.createXMLStreamReader(bin);
                }
                break;
            case 2: // explicit UTF-8 stream
                {
                    ByteArrayInputStream bin = new ByteArrayInputStream
                        (input.getBytes("UTF-8"));
                    Reader br = new InputStreamReader(bin, "UTF-8");
                    sr = f.createXMLStreamReader(br);
                }
                break;
            default: throw new Error("Internal error");
            }

            count += streamAndCheck(sr, it, input, expOutput,
				    reallyStreaming);
        }
        return count;
    }

    protected int streamAndCheck(XMLStreamReader sr, InputConfigIterator it,
				 String input, String expOutput,
				 boolean reallyStreaming)
        throws IOException, XMLStreamException
    {
        int type;

        /* Let's ignore leading white space and DTD; and stop on encountering
         * something else
         */
        do {
            type = sr.next();
        } while ((type == SPACE) || (type == DTD));
        
        StringBuffer act = new StringBuffer(1000);
        int count = 0;

        do {
            count += type;
            if (type == START_ELEMENT || type == END_ELEMENT) {
                act.append('<');
                if (type == END_ELEMENT) {
                    act.append('/');
                }
                String prefix = sr.getPrefix();
                if (prefix != null && prefix.length() > 0) {
                    act.append(prefix);
                    act.append(':');
                }
                act.append(sr.getLocalName());
                act.append('>');
            } else if (type == CHARACTERS || type == SPACE || type == CDATA) {
                // No quoting, doesn't have to result in legal XML
		if (reallyStreaming) {
		    StringWriter sw = new StringWriter();
		    // important: false to indicate 'don't preserve contents'
		    int gotLen = ((XMLStreamReader2)sr).getText(sw, false);
                    String text = sw.toString();
                    int textLen = text.length();
                    if (textLen != gotLen) {
                        if (text.length() > 60) {
                            text = text.substring(0, 30) + "<...>" + text.substring(textLen-30);
                        }
                        assertEquals("Incorrect return value from streaming getText() for "+
                                     tokenTypeDesc(type)+" [string '"+text+"']", textLen, gotLen);
                    }
		    act.append(text);
		} else {
		    act.append(sr.getText());
		}
            } else if (type == COMMENT) {
                act.append("<!--");
		if (reallyStreaming) {
		    StringWriter sw = new StringWriter();
		    // important: false to indicate 'don't preserve contents'
		    int gotLen = ((XMLStreamReader2)sr).getText(sw, false);
                    String text = sw.toString();
                    int textLen = text.length();
                    if (textLen != gotLen) {
                        if (text.length() > 60) {
                            text = text.substring(0, 30) + "<...>" + text.substring(textLen-30);
                        }
                        assertEquals("Incorrect return value from streaming getText() for "+
                                     tokenTypeDesc(type)+" [string '"+text+"']", textLen, gotLen);
                    }
		    act.append(text);
		} else {
		    act.append(sr.getText());
		}
                act.append("-->");
            } else if (type == PROCESSING_INSTRUCTION) {
                act.append("<!?");
                act.append(sr.getPITarget());
                String data = sr.getPIData();
                if (data != null) {
                    act.append(' ');
                    act.append(data.trim());
                }
                act.append("?>");
            } else if (type == ENTITY_REFERENCE) {
                act.append(sr.getText());
            } else {
                fail("Unexpected event type "+tokenTypeDesc(type));
            }
        } while ((type = sr.next()) != END_DOCUMENT);

        String result = act.toString();
        if (!result.equals(expOutput)) {
            String desc = it.toString();
            int round = it.getIndex();

        // uncomment for debugging:

            /*
        System.err.println("FAIL: round "+round+" ["+desc+"]");
        System.err.println("Input:  '"+input.toString()+"'");
        System.err.println("Exp:    '"+expOutput.toString()+"'");
        System.err.println("Actual: '"+act.toString()+"'");
            */

            fail("Failure with '"+desc+"' (round #"+round+"):\n<br />"
                 +"Input : {"+printableWithSpaces(input)+"}\n<br />"
                 +"Output: {"+printableWithSpaces(result)+"}\n<br />"
                 +"Exp.  : {"+printableWithSpaces(expOutput)+"}\n<br />");
        }

        return count;
    }

    protected int streamAndSkip(XMLInputFactory f, InputConfigIterator it,
                                String input)
        throws XMLStreamException, UnsupportedEncodingException
    {
        int count = 0;

        // Let's loop couple of input methods
        for (int m = 0; m < 3; ++m) {
            XMLStreamReader sr;

            switch (m) {
            case 0: // simple StringReader:
                sr = constructStreamReader(f, input);
                break;
            case 1: // via InputStream and auto-detection
                {
                    ByteArrayInputStream bin = new ByteArrayInputStream
                        (input.getBytes("UTF-8"));
                    sr = f.createXMLStreamReader(bin);
                }
                break;
            case 2: // explicit UTF-8 stream
                {
                    ByteArrayInputStream bin = new ByteArrayInputStream
                        (input.getBytes("UTF-8"));
                    Reader br = new InputStreamReader(bin, "UTF-8");
                    sr = f.createXMLStreamReader(br);
                }
                break;
            default: throw new Error("Internal error");
            }

            count += streamAndSkip(sr);
        }
        return count;
    }

    protected int streamAndSkip(XMLStreamReader sr)
        throws XMLStreamException
    {
        int count = 0;

        while (sr.hasNext()) {
            count += sr.next();
        }
        return count;
    }

    protected void generateData(Random r, StringBuffer input,
				StringBuffer output, boolean autoEnt)
    {
        final String PREAMBLE =
            "<?xml version='1.0' encoding='UTF-8'?>"
            +"<!DOCTYPE root [\n"
            +" <!ENTITY ent1 'ent1Value'>\n"
            +" <!ENTITY x 'Y'>\n"
            +" <!ENTITY both '&ent1;&x;'>\n"
            +"]>";

        /* Ok; template will use '*' chars as placeholders, to be replaced
         * by pseudo-randomly selected choices.
         */
        final String TEMPLATE =
            "<root>"

            // Short one for trouble shooting:
            /*
            +" * Text ****<empty></empty>\n</root>"
            */

            // Real one for regression testing:
            +" * Text ****<empty></empty>\n"
            +"<empty>*</empty>*  * xx<empty></empty>\n"
            +"<tag>Text ******</tag>\n"
            +"<a>*...</a><b>...*</b><c>*</c>"
            +"<c>*</c><c>*</c><c>*</c><c>*</c><c>*</c><c>*</c>"
            +"<c>*<d>** *<e>*</e>**</d></c>"
            +"<c><d><e>*</e> **</d>*</c>"
            +"a*b*c*d*e*f*g*h*i*j*k"
            +"</root>"
            ;

        input.append(TEMPLATE);
        output.append(TEMPLATE);

        for (int i = TEMPLATE.length(); --i >= 0; ) {
            char c = TEMPLATE.charAt(i);

            if (c == '*') {
                replaceEntity(input, output, autoEnt, r, i);
            }
        }

        // Let's also insert preamble into input now
        input.insert(0, PREAMBLE);
    }

    protected void replaceEntity(StringBuffer input, StringBuffer output,
				 boolean autoEnt,
				 Random r, int index)
    {
        String in, out;
        
        switch (Math.abs(r.nextInt()) % 6) {
        case 0: // Let's use one of pre-def'd entities:
            switch (Math.abs(r.nextInt()) % 5) {
            case 0:
                in = "&amp;"; out = "&";
                break;
            case 1:
                in = "&apos;"; out = "'";
                break;
            case 2:
                in = "&lt;"; out = "<";
                break;
            case 3:
                in = "&gt;"; out = ">";
                break;
            case 4:
                in = "&quot;"; out = "\"";
                break;
            default: throw new Error("Internal error!");
            }
            break;
        case 1: // How about some CDATA?
            switch (Math.abs(r.nextInt()) % 5) {
            case 0:
                in = "<![CDATA[]] >]]>";
                out = "]] >";
                break;
            case 1:
                in = "<![CDATA[xyz&abc]]>";
                out = "xyz&abc";
                break;
            case 2:
                in = "<![CDATA[ ]]>";
                out = " ";
                break;
            case 3:
                in = "<![CDATA[]]>";
                out = "";
                break;
            case 4:
                in = "<![CDATA[   \nxyz]]>";
                out = "   \nxyz";
                break;
            default: throw new Error("Internal error!");
            }

        case 2: // and COMMENTS
            switch (Math.abs(r.nextInt()) % 5) {
            case 0:
                in = "<!--comment-->";
                out = "<!--comment-->";
                break;
            case 1:
                in = out = "<!---->";
                break;
            case 2:
                in = out = "<!--   \n-->";
                break;
            case 3:
                //in = out = "<!--a\nb  \r\n   \rhah\r \n-->";
                in = out = "<!-- \r -->";
                break;
            case 4:
                in = out = "<!-- a<>B -->";
                break;
            default: throw new Error("Internal error!");
            }
            break;
        case 3: // Char entities?
            switch (Math.abs(r.nextInt()) % 4) {
            case 0:
                in = "&#35;";
                out = "#";
                break;
            case 1:
                in = "&#x24;";
                out = "$";
                break;
            case 2:
                in = "&#169;"; // above US-Ascii, copyright symbol
                out = "\u00A9";
                break;
            case 3:
                in = "&#xc4;"; // Upper-case a with umlauts
                out = "\u00C4";
                break;
            default: throw new Error("Internal error!");
            }
            break;
        case 4: // Full entities
            switch (Math.abs(r.nextInt()) % 3) {
            case 0:
                in = "&ent1;";
                out = "ent1Value";
                break;
            case 1:
                in = "&x;";
                out = "Y";
                break;
            case 2:
                in = "&both;";
                out = autoEnt ? "ent1ValueY" : "&ent1;&x;";
                break;
            default: throw new Error("Internal error!");
            }
            break;

        case 5: // Plain text, ISO-Latin chars:
            in = out = "(\u00A9)"; // copyright symbol
            break;

        case 6: // Proc. instr?
            switch (Math.abs(r.nextInt()) % 5) {
            case 0:
                in = out = "<?myTarget?>";
                break;
            case 1:
                in = out = "<?my data?>";
                break;
            case 2:
                in = out = "<?a -ha!?>";
                break;
            case 3:
                in = out = "<?xy_z ? ? <>? ?>";
                break;
            case 4:
                in = out = "<?proc instr\nwith a\r\nlinefeed or <b>two</b> \r\r\r";
                break;
            default: throw new Error("Internal error!");
            }

        default:
            throw new Error("Internal error!");
        }
        input.replace(index, index+1, in);
        output.replace(index, index+1, out);
    }

    /**
     * Method that will normalize all unnormalized LFs (\r, \r\n) into
     * normalized one (\n).
     */
    protected void normalizeLFs(StringBuffer input)
    {
        int len = input.length();
        for (int i = len; --i >= 0; ) {
            char c = input.charAt(i);
            if (c == '\r') {
                if (i < (len-1) && input.charAt(i+1) == '\n') {
                    input.deleteCharAt(i);
                } else {
                    input.setCharAt(i, '\n');
                }
            }
        }
    }
}
