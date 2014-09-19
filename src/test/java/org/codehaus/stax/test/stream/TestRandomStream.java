package org.codehaus.stax.test.stream;

import java.io.*;
import java.util.Random;

import javax.xml.stream.*;

/**
 * Unit test suite that ensures that independent of combinations of settings
 * such as namespace-awareness, coalescing, automatic entity replacement,
 * parsing results remain the same when they should.
 */
public class TestRandomStream
    extends BaseStreamTest
{
    public void testCoalescingAutoEntity()
        throws Exception
    {
        doTest(false, true, true); // non-ns
        doTest(true, true, true); // ns-aware
    }

    public void testNonCoalescingAutoEntity()
        throws Exception
    {
        doTest(false, false, true); // non-ns
        doTest(true, false, true); // ns-aware
    }

    public void testCoalescingNonAutoEntity()
        throws Exception
    {
        doTest(false, true, false); // non-ns
        doTest(true, true, false); // ns-aware
    }

    public void testNonCoalescingNonAutoEntity()
        throws Exception
    {
        doTest(false, false, false); // non-ns
        doTest(true, false, false); // ns-aware
    }

    /*
    ////////////////////////////////////////
    // Private methods, common test code
    ////////////////////////////////////////
     */

    private void doTest(boolean ns, boolean coalescing, boolean autoEntity)
        throws Exception
    {
//System.err.println("Ns: "+ns+", coal "+coalescing+" ent "+autoEntity);
        // Let's generate seed from args so it's reproducible
        long seed = 123457;
        if (ns) {
            seed ^= "ns".hashCode();
        }
        if (coalescing) {
            seed ^= "coalescing".hashCode();
        }
        if (autoEntity) {
            seed ^= "autoEntity".hashCode();
        }
        Random r = new Random(seed);

        /* We can do multiple rounds, too, too get even wider coverage...
         */
        final int ROUNDS = 5;

        for (int i = 0; i < ROUNDS; ++i) {
            StringBuffer inputBuf = new StringBuffer(1000);
            StringBuffer expOutBuf = new StringBuffer(1000);
            generateData(r, inputBuf, expOutBuf, autoEntity);
            String input = inputBuf.toString();
            String expOutput = expOutBuf.toString();

            // Let's test different input methods too:
            for (int j = 0; j < 3; ++j) {
                XMLInputFactory f = getFactory(ns, coalescing, autoEntity);
                XMLStreamReader sr;

                switch (j) {
                case 0: // simple StringReader:
                    sr = constructStreamReader(f, input);
                    break;
                case 1: // via InputStream and auto-detection
                    /* It shouldn't really contain anything outside ISO-Latin;
                     * however, detection may be tricky.. so let's just
                     * test with UTF-8, for now?
                     */
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

                String actual = null;

                try {
                    actual = runTest(sr);
                } catch (Exception e) {
                    // For debugging uncomment:
                    /*
                    System.err.println("Error: "+e);
                    System.err.println("Ns: "+ns+", coalescing: "+coalescing+", auto-ent: "+autoEntity);
                    System.err.println("Input was '"+input+"'");
                    */

                    throw e;
                }
        
                // uncomment for debugging:
                /*
                if (!expOutput.equals(actual)) {
                    System.err.println("Input:  '"+input+"'");
                    System.err.println("Exp:    '"+expOutput+"'");
                    System.err.println("Actual: '"+actual+"'");
                }
                */
                assertEquals(expOutput, actual);
            }
        }
    }

    private String runTest(XMLStreamReader sr)
        throws Exception
    {
        assertEquals(DTD, sr.next());
        
        int type;
        
        while ((type = sr.next()) == SPACE) {
            ;
        }
        assertEquals(START_ELEMENT, type);
        
        StringBuffer act = new StringBuffer(1000);

        do {
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
                try { // note: entity expansion may fail...
                    act.append(getAndVerifyText(sr));
                } catch (XMLStreamException xse) {
                    fail("Expected succesful entity expansion, got: "+xse);
                } catch (RuntimeException rex) {
                    /* 28-Oct-2006, TSa: since getText() may not be able
                     *   to throw XMLStreamException, let's see if we got
                     *   a runtime excpetion with root cause of stream exc
                     */
                    if (rex.getCause() instanceof XMLStreamException) {
                        fail("Expected succesful entity expansion, got: "+rex);
                    } else {
                        throw rex;
                    }
                }
            } else if (type == COMMENT) {
                act.append("<!--");
                act.append(sr.getText());
                act.append("-->");
            } else if (type == ENTITY_REFERENCE) {
                act.append(sr.getText());
            } else {
                fail("Unexpected event type "+type);
            }
            try {
                type = sr.next();
            } catch (XMLStreamException xse) {
                fail("Parse problem: "+xse);
            }
        } while (type != END_DOCUMENT);
        
        return act.toString();
    }

    private void generateData(Random r, StringBuffer input,
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
            +"<c>*<d>*<e>*</e></d></c>"
            +"<c><d><e>*</e>*</d>*</c>"
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

    private void replaceEntity(StringBuffer input, StringBuffer output,
                               boolean autoEnt,
                               Random r, int index)
    {
        String in, out;
        
        switch (Math.abs(r.nextInt()) % 5) {
        case 0: // Let's use one of pre-def'd entities:
            switch (Math.abs(r.nextInt()) % 5) {
            case 0:
                in = "&amp;";
                out = "&";
                break;
            case 1:
                in = "&apos;";
                out = "'";
                break;
            case 2:
                in = "&lt;";
                out = "<";
                break;
            case 3:
                in = "&gt;";
                out = ">";
                break;
            case 4:
                in = "&quot;";
                out = "\"";
                break;
            default: throw new Error("Internal error!");
            }
            break;
        case 1: // How about some CDATA?
            switch (Math.abs(r.nextInt()) % 4) {
            case 0:
                in = "<![CDATA[]] >]]>";
                out = "]] >";
                break;
            case 1:
                in = "<![CDATA[xyz&abc]]>";
                out = "xyz&abc";
                break;
            case 2:
                in = "<!--comment-->";
                out = "<!--comment-->";
                break;
            case 3:
                in = "<![CDATA[ ]]>";
                out = " ";
                break;
            default: throw new Error("Internal error!");
            }
            break;
        case 2: // Char entities?
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
        case 3: // Full entities
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

        case 4: // Plain text, ISO-Latin chars:
            in = out = "(\u00A9)"; // copyright symbol
            break;

        default:
            throw new Error("Internal error!");
        }
        input.replace(index, index+1, in);
        output.replace(index, index+1, out);
    }

    /*
    ////////////////////////////////////////
    // Private methods, other
    ////////////////////////////////////////
     */

    private XMLInputFactory getFactory(boolean nsAware,
                                       boolean coalescing, boolean autoEntity)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setNamespaceAware(f, nsAware);
        setCoalescing(f, coalescing);
        setReplaceEntities(f, autoEntity);

        setSupportDTD(f, true);
        setValidating(f, false);
        return f;
    }
}
