package stax2.typed;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Random;

import javax.xml.namespace.QName;
import javax.xml.stream.*;

import org.codehaus.stax2.*;
import org.codehaus.stax2.typed.*;

import stax2.BaseStax2Test;

/**
 * Base class that contains set of simple unit tests to verify implementation
 * of {@link TypedXMLStreamReader}. Concrete sub-classes are used to
 * test both native and wrapped Stax2 implementations.
 *
 * @author Tatu Saloranta
 */
public abstract class ReaderTestBase
    extends BaseStax2Test
{
    final static long TOO_BIG_FOR_INT = Integer.MAX_VALUE+1L;
    final static long TOO_SMALL_FOR_INT = Integer.MIN_VALUE-1L;

    final static BigInteger TOO_BIG_FOR_LONG = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(123));
    final static BigInteger TOO_SMALL_FOR_LONG = BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.valueOf(123));

    /*
    ////////////////////////////////////////
    // Tests for boolean, integral numbers
    ////////////////////////////////////////
     */

    public void testSimpleBooleanElem()
        throws Exception
    {
        // simple boolean
        checkBooleanElem("<root>true</root>", true);
        // with white space normalization
        checkBooleanElem("<root>\tfalse\n\r</root>", false);
        // Then non9-canonical alternatives
        checkBooleanElem("<root>0   \t</root>", false);
        checkBooleanElem("<root>\r1</root>", true);

        // And finally invalid ones
        checkBooleanElemException("<root>yes</root>");
        /* Although "01" would be valid integer equal to "1",
         * it's not a legal boolean nonetheless (as per my reading
         * of W3C Schema specs)
         */
        checkBooleanElemException("<root>01</root>");
    }

    public void testSimpleBooleanAttr()
        throws Exception
    {
        checkBooleanAttr("<root attr='true' />", true);
        checkBooleanAttr("<root attr=\"\tfalse\n\r\" />", false);
        checkBooleanAttr("<root attr='0   \t' />", false);
        checkBooleanAttr("<root attr=\"\r1\" />", true);

        checkBooleanAttrException("<root attr=\"yes\" />");
        checkBooleanAttrException("<root attr='01' />");
    }

    public void testMultipleBooleanAttr()
        throws Exception
    {
        XMLStreamReader2 sr = getRootReader("<root a1='true' b=\"false\" third='0' />");
        assertEquals(3, sr.getAttributeCount());
        int ix1 = sr.getAttributeIndex("", "a1");
        int ix2 = sr.getAttributeIndex("", "b");
        int ix3 = sr.getAttributeIndex("", "third");
        if (ix1 < 0 || ix2 < 0 || ix3 < 0) {
            fail("Couldn't find indexes of attributes: a1="+ix1+", b="+ix2+", third="+ix3);
        }
        assertTrue(sr.getAttributeAsBoolean(ix1));
        assertFalse(sr.getAttributeAsBoolean(ix2));
        assertFalse(sr.getAttributeAsBoolean(ix3));

        sr.close();
    }

    public void testSimpleIntElem()
        throws Exception
    {
        checkIntElem("<root>000000000000000000000000012</root>", 12);


        checkIntElem("<root>0</root>", 0);
        // with white space normalization
        checkIntElem("<root>291\t</root>", 291);
        checkIntElem("<root>   \t1</root>", 1);
        checkIntElem("<root>3 </root>", 3);
        checkIntElem("<root>  -7 </root>", -7);
        // with signs, spacing etc
        checkIntElem("<root>-1234</root>", -1234);
        checkIntElem("<root>+3</root>", 3);
        checkIntElem("<root>-0</root>", 0);
        checkIntElem("<root>-0000</root>", 0);
        checkIntElem("<root>-001</root>", -1);
        checkIntElem("<root>+0</root>", 0);
        checkIntElem("<root>+0  </root>", 0);
        checkIntElem("<root>+00</root>", 0);
        checkIntElem("<root>000000000000000000000000012</root>", 12);
        checkIntElem("<root>-00000000</root>", 0);
        int v = 1200300400;
        checkIntElem("<root>   \r\n+"+v+"</root>", v);
        checkIntElem("<root> "+Integer.MAX_VALUE+"</root>", Integer.MAX_VALUE);
        checkIntElem("<root> "+Integer.MIN_VALUE+"</root>", Integer.MIN_VALUE);

        // And finally invalid ones
        checkIntElemException("<root>12a3</root>");
        checkIntElemException("<root>5000100200</root>"); // overflow
        checkIntElemException("<root>3100200300</root>"); // overflow
        checkIntElemException("<root>-4100200300</root>"); // underflow
        checkIntElemException("<root>"+TOO_BIG_FOR_INT+"</root>"); // overflow as well
        checkIntElemException("<root>"+TOO_SMALL_FOR_INT+"</root>"); // underflow as well
        checkIntElemException("<root>-  </root>");
        checkIntElemException("<root>+</root>");
        checkIntElemException("<root> -</root>");
    }

    public void testSimpleIntAttr()
        throws Exception
    {
        checkIntAttr("<root attr='+0   \t' />", 0);
        checkIntAttr("<root attr='13' />", 13);
        checkIntAttr("<root attr='123' />", 123);
        checkIntAttr("<root attr=\"\t-12\n\r\" />", -12);
        checkIntAttr("<root attr='+0   \t' />", 0);
        checkIntAttr("<root attr=\"\r-00\" />", 0);
        checkIntAttr("<root attr='-000000000000012345' />", -12345);
        checkIntAttr("<root attr='"+Integer.MAX_VALUE+"  ' />", Integer.MAX_VALUE);
        checkIntAttr("<root attr='"+Integer.MIN_VALUE+"'  />", Integer.MIN_VALUE);

        checkIntAttrException("<root attr=\"abc\" />");
        checkIntAttrException("<root attr='1c' />");
        checkIntAttrException("<root attr='\n"+TOO_BIG_FOR_INT+"' />");
        checkIntAttrException("<root attr=\""+TOO_SMALL_FOR_INT+"   \" />");
        checkIntAttrException("<root attr='-' />");
        checkIntAttrException("<root attr='  + ' />");
    }

    public void testMultipleIntAttr()
        throws Exception
    {
        XMLStreamReader2 sr = getRootReader("<root a1='123456789' b=\"-123456789\" third='0' />");
        assertEquals(3, sr.getAttributeCount());
        int ix1 = sr.getAttributeIndex("", "a1");
        int ix2 = sr.getAttributeIndex("", "b");
        int ix3 = sr.getAttributeIndex("", "third");
        if (ix1 < 0 || ix2 < 0 || ix3 < 0) {
            fail("Couldn't find indexes of attributes: a1="+ix1+", b="+ix2+", third="+ix3);
        }
        assertEquals(123456789, sr.getAttributeAsInt(ix1));
        assertEquals(-123456789, sr.getAttributeAsInt(ix2));
        assertEquals(0, sr.getAttributeAsInt(ix3));

        sr.close();
    }

    public void testSimpleLongElem()
        throws Exception
    {
        checkLongElem("<root>000000000000000000000000012</root>", 12);


        checkLongElem("<root>0</root>", 0);
        // with white space normalization
        checkLongElem("<root>10091\t</root>", 10091);
        checkLongElem("<root>   \t-1</root>", -1);
        checkLongElem("<root>39876 </root>", 39876);
        checkLongElem("<root>  0701 </root>", 701);
        // with signs, spacing etc
        checkLongElem("<root>-1234</root>", -1234);
        checkLongElem("<root>+3</root>", 3);
        checkLongElem("<root>-0</root>", 0);
        checkLongElem("<root>-001</root>", -1);
        checkLongElem("<root>+0</root>", 0);
        checkLongElem("<root>0000000000000001234567890</root>", 1234567890L);
        checkLongElem("<root>-00000000</root>", 0);
        long v = 1200300400500600L;
        checkLongElem("<root>   \r"+v+"</root>", v);
        checkLongElem("<root>   \r\n+"+v+"</root>", v);
        v = -1234567890123456789L;
        checkLongElem("<root>   \r\n"+v+"</root>", v);
        checkLongElem("<root> "+Long.MAX_VALUE+"</root>", Long.MAX_VALUE);
        checkLongElem("<root> "+Long.MIN_VALUE+"</root>", Long.MIN_VALUE);

        // And finally invalid ones
        checkLongElemException("<root>12a3</root>");
        checkLongElemException("<root>"+TOO_BIG_FOR_LONG+"</root>"); // overflow as well
        checkLongElemException("<root>"+TOO_SMALL_FOR_LONG+"</root>"); // underflow as well
        checkLongElemException("<root>-  </root>");
        checkLongElemException("<root>+</root>");
        checkLongElemException("<root> -</root>");
    }

    public void testSimpleLongAttr()
        throws Exception
    {
        checkLongAttr("<root attr='+0   \t' />", 0);
        checkLongAttr("<root attr='13' />", 13);
        checkLongAttr("<root attr='123' />", 123);
        checkLongAttr("<root attr=\"\t-12\n\r\" />", -12);
        checkLongAttr("<root attr='+0   \t' />", 0);
        checkLongAttr("<root attr=\"\r-00\" />", 0);
        checkLongAttr("<root attr='-000000000000012345' />", -12345);
        checkLongAttr("<root attr='"+Long.MAX_VALUE+"  ' />", Long.MAX_VALUE);
        checkLongAttr("<root attr='"+Long.MIN_VALUE+"'  />", Long.MIN_VALUE);

        checkLongAttrException("<root attr=\"abc\" />");
        checkLongAttrException("<root attr='1c' />");
        checkLongAttrException("<root attr='\n"+TOO_BIG_FOR_LONG+"' />");
        checkLongAttrException("<root attr=\""+TOO_SMALL_FOR_LONG+"   \" />");
        checkLongAttrException("<root attr='-' />");
        checkLongAttrException("<root attr='  + ' />");
    }

    public void testMultipleLongAttr()
        throws Exception
    {
        XMLStreamReader2 sr = getRootReader("<root a1='12345678900' b=\"-12345678900\" third='0' />");
        assertEquals(3, sr.getAttributeCount());
        int ix1 = sr.getAttributeIndex("", "a1");
        int ix2 = sr.getAttributeIndex("", "b");
        int ix3 = sr.getAttributeIndex("", "third");
        if (ix1 < 0 || ix2 < 0 || ix3 < 0) {
            fail("Couldn't find indexes of attributes: a1="+ix1+", b="+ix2+", third="+ix3);
        }
        assertEquals(12345678900L, sr.getAttributeAsLong(ix1));
        assertEquals(-12345678900L, sr.getAttributeAsLong(ix2));
        assertEquals(0L, sr.getAttributeAsLong(ix3));

        sr.close();
    }

    /*
    ////////////////////////////////////////
    // Tests for floating point numbers
    ////////////////////////////////////////
     */

    public void testSimpleFloatElem()
        throws Exception
    {
        checkFloatElem("<root>0.0</root>", 0.0f);
        checkFloatElem("<root>0</root>", 0.0f);
        // with white space normalization
        checkFloatElem("<root>1.0\t</root>", 1.0f);
        checkFloatElem("<root>   \t-0.1</root>", -0.1f);
        checkFloatElem("<root>+.001 </root>", 0.001f);
        checkFloatElem("<root>  -3.1415 </root>", -3.1415f);
        checkFloatElem("<root>27.3E-01</root>", 2.73e-01f);
        checkFloatElem("<root> "+Float.MAX_VALUE+"</root>", Float.MAX_VALUE);
        checkFloatElem("<root> "+Float.MIN_VALUE+"</root>", Float.MIN_VALUE);
        checkFloatElem("<root> NaN</root>", Float.NaN);
        checkFloatElem("<root>INF  </root>", Float.POSITIVE_INFINITY);
        checkFloatElem("<root>\t-INF\t</root>", Float.NEGATIVE_INFINITY);

        // And finally invalid ones
        checkFloatElemException("<root>abcd</root>");
        checkFloatElemException("<root>-  </root>");
        checkFloatElemException("<root>+</root>");
        checkFloatElemException("<root> -</root>");
        checkFloatElemException("<root>1e</root>");
    }

    public void testSimpleFloatAttr()
        throws Exception
    {
        checkFloatAttr("<root attr='+0.1   \t' />", 0.1f);
        checkFloatAttr("<root attr='13.23' />", 13.23f);
        checkFloatAttr("<root attr='0.123' />", 0.123f);
        checkFloatAttr("<root attr=\"\t-12.03\n\r\" />", -12.03f);
        checkFloatAttr("<root attr='+0   \t' />", 0.0f);
        checkFloatAttr("<root attr=\"\r-00\" />", 0.0f);
        checkFloatAttr("<root attr='-000000000000012345' />", -12345f);
        checkFloatAttr("<root attr='"+Float.MAX_VALUE+"  ' />", Float.MAX_VALUE);
        checkFloatAttr("<root attr='"+Float.MIN_VALUE+"'  />", Float.MIN_VALUE);
        checkFloatAttr("<root attr='NaN'/>", Float.NaN);
        checkFloatAttr("<root attr=' INF' />", Float.POSITIVE_INFINITY);
        checkFloatAttr("<root attr='-INF  ' />", Float.NEGATIVE_INFINITY);

        checkFloatAttrException("<root attr=\"abc\" />");
        checkFloatAttrException("<root attr='1c' />");
        checkFloatAttrException("<root attr='-' />");
        checkFloatAttrException("<root attr='  + ' />");
    }

    public void testMultipleFloatAttr()
        throws Exception
    {
        XMLStreamReader2 sr = getRootReader("<root a1='-123.456' b=\"0.003\" third='-0.0' />");
        assertEquals(3, sr.getAttributeCount());
        int ix1 = sr.getAttributeIndex("", "a1");
        int ix2 = sr.getAttributeIndex("", "b");
        int ix3 = sr.getAttributeIndex("", "third");
        if (ix1 < 0 || ix2 < 0 || ix3 < 0) {
            fail("Couldn't find indexes of attributes: a1="+ix1+", b="+ix2+", third="+ix3);
        }
        assertEquals(-123.456f, sr.getAttributeAsFloat(ix1));
        assertEquals(0.003f, sr.getAttributeAsFloat(ix2));
        assertEquals(-0.0f, sr.getAttributeAsFloat(ix3));

        sr.close();
    }

    public void testSimpleDoubleElem()
        throws Exception
    {
        checkDoubleElem("<root>0.0</root>", 0.0f);
        checkDoubleElem("<root>0</root>", 0.0f);
        // with white space normalization
        checkDoubleElem("<root>1.0\t</root>", 1.0f);
        checkDoubleElem("<root>   \t-0.1</root>", -0.1f);
        checkDoubleElem("<root>+.001 </root>", 0.001f);
        checkDoubleElem("<root>  -3.1415 </root>", -3.1415f);
        checkDoubleElem("<root>27.3E-01</root>", 2.73e-01f);
        checkDoubleElem("<root> "+Double.MAX_VALUE+"</root>", Double.MAX_VALUE);
        checkDoubleElem("<root> "+Double.MIN_VALUE+"</root>", Double.MIN_VALUE);
        checkDoubleElem("<root> NaN</root>", Double.NaN);
        checkDoubleElem("<root>INF  </root>", Double.POSITIVE_INFINITY);
        checkDoubleElem("<root>\t-INF\t</root>", Double.NEGATIVE_INFINITY);

        // And finally invalid ones
        checkDoubleElemException("<root>abcd</root>");
        checkDoubleElemException("<root>-  </root>");
        checkDoubleElemException("<root>+</root>");
        checkDoubleElemException("<root> -</root>");
        checkDoubleElemException("<root>1e</root>");
    }

    public void testSimpleDoubleAttr()
        throws Exception
    {
        checkDoubleAttr("<root attr='+0.1   \t' />", 0.1f);
        checkDoubleAttr("<root attr='13.23' />", 13.23f);
        checkDoubleAttr("<root attr='0.123' />", 0.123f);
        checkDoubleAttr("<root attr=\"\t-12.03\n\r\" />", -12.03f);
        checkDoubleAttr("<root attr='+0   \t' />", 0.0f);
        checkDoubleAttr("<root attr=\"\r-00\" />", 0.0f);
        checkDoubleAttr("<root attr='-000000000000012345' />", -12345f);
        checkDoubleAttr("<root attr='"+Double.MAX_VALUE+"  ' />", Double.MAX_VALUE);
        checkDoubleAttr("<root attr='"+Double.MIN_VALUE+"'  />", Double.MIN_VALUE);
        checkDoubleAttr("<root attr='NaN'/>", Double.NaN);
        checkDoubleAttr("<root attr=' INF' />", Double.POSITIVE_INFINITY);
        checkDoubleAttr("<root attr='-INF  ' />", Double.NEGATIVE_INFINITY);

        checkDoubleAttrException("<root attr=\"abc\" />");
        checkDoubleAttrException("<root attr='1c' />");
        checkDoubleAttrException("<root attr='-' />");
        checkDoubleAttrException("<root attr='  + ' />");
    }

    public void testMultipleDoubleAttr()
        throws Exception
    {
        XMLStreamReader2 sr = getRootReader("<root a1='-123.456' b=\"0.003\" third='-0.0' />");
        assertEquals(3, sr.getAttributeCount());
        int ix1 = sr.getAttributeIndex("", "a1");
        int ix2 = sr.getAttributeIndex("", "b");
        int ix3 = sr.getAttributeIndex("", "third");
        if (ix1 < 0 || ix2 < 0 || ix3 < 0) {
            fail("Couldn't find indexes of attributes: a1="+ix1+", b="+ix2+", third="+ix3);
        }
        assertEquals(-123.456f, sr.getAttributeAsDouble(ix1));
        assertEquals(0.003f, sr.getAttributeAsDouble(ix2));
        assertEquals(-0.0f, sr.getAttributeAsDouble(ix3));

        sr.close();
    }

    /*
    ////////////////////////////////////////
    // Tests for "big" numbers
    ////////////////////////////////////////
     */

    /**
     * With unlimited length BigInteger, it's easier to just generate
     * very big (long) numbers, and test variability that way.
     */
    public void testBigInteger()
        throws Exception
    {
        /* Let's just generate reasonably big (up to 200 digits)
         * numbers, and test some variations
         */
        BigInteger I = BigInteger.valueOf(3);
        Random rnd = new Random(1);
        for (int i = 1; i < 200; ++i) {
            // First, regular elem content
            String doc;
            String istr = I.toString();

            switch (i % 4) { // some white space variations
            case 0:
                istr = " \t "+istr;
                break;
            case 1:
                istr = istr+"\r";
                break;
            case 2:
                istr = "\n"+istr+" ";
                break;
            }
            XMLStreamReader2 sr = getRootReader("<root>"+istr+"</root>");
            assertEquals(I, sr.getElementAsInteger());
            sr.close();
            // Then attribute
            doc = "<root attr='"+istr+"' />";
            sr = getRootReader(doc);
            assertEquals(I, sr.getAttributeAsInteger(0));
            sr.close();

            // And finally, invalid
            istr = I.toString();

            switch (i % 3) {
            case 0:
                istr = "ab"+istr;
                break;
            case 1:
                istr = istr+"!";
                break;
            case 2:
                istr = istr+".0";
                break;
            }

            sr = getRootReader("<root>"+istr+"</root>");
            try {
                sr.getElementAsInteger();
                fail("Expected exception for invalid input ["+doc+"]");
            } catch (TypedXMLStreamException xse) { ; // good
            }
            sr.close();

            sr = getRootReader("<root attr='"+istr+" '/>");
            try {
                sr.getAttributeAsInteger(0);
                fail("Expected exception for invalid input ["+doc+"]");
            } catch (TypedXMLStreamException xse) { ; // good
            }
            sr.close();

            // And then, let's just multiply by 10, add a new digit
            I = I.multiply(BigInteger.valueOf(10)).add(BigInteger.valueOf(rnd.nextInt() & 0xF));

            // Plus switch sign every now and then
            if ((i % 3) == 0) {
                I = I.negate();
            }
        }
    }

    /**
     * As with BigInteger, we better use number generation with
     * BigDecimal.
     */
    public void testBigDecimal()
        throws Exception
    {
        BigDecimal D = BigDecimal.valueOf(1L);
        Random rnd = new Random(6);
        // 200 digits seems ok here too
        for (int i = 1; i < 200; ++i) {
            // First, regular elem content
            String doc;
            String istr = D.toString();

            switch (i % 4) { // some white space variations
            case 0:
                istr = "\t"+istr;
                break;
            case 1:
                istr = istr+"  ";
                break;
            case 2:
                istr = " "+istr+"\r";
                break;
            }
            XMLStreamReader2 sr = getRootReader("<root>"+istr+"</root>");
            assertEquals(D, sr.getElementAsDecimal());
            sr.close();
            // Then attribute
            doc = "<root attr='"+istr+"' />";
            sr = getRootReader(doc);
            assertEquals(D, sr.getAttributeAsDecimal(0));
            sr.close();

            // And finally, invalid
            istr = D.toString();

            switch (i % 3) {
            case 0:
                istr = "_x"+istr;
                break;
            case 1:
                istr = istr+"?";
                break;
            case 2:
                istr = istr+"e";
                break;
            }

            sr = getRootReader("<root>"+istr+"</root>");
            try {
                sr.getElementAsDecimal();
                fail("Expected exception for invalid input ["+doc+"]");
            } catch (TypedXMLStreamException xse) { ; // good
            }
            sr.close();

            sr = getRootReader("<root attr='"+istr+" '/>");
            try {
                sr.getAttributeAsDecimal(0);
                fail("Expected exception for invalid input ["+doc+"]");
            } catch (TypedXMLStreamException xse) { ; // good
            }
            sr.close();
            
            // Ok, then, add a small integer, divide by 10 to generate digits
            D = D.add(BigDecimal.valueOf(rnd.nextInt() & 0xF)).divide(BigDecimal.valueOf(10L));
            
            // Plus switch sign every now and then
            if ((i % 3) == 0) {
                D = D.negate();
            }
        }
    }

    /*
    ////////////////////////////////////////
    // Tests for name type(s)
    ////////////////////////////////////////
     */

    public void testValidQNameElem()
        throws Exception
    {
        String URI = "http://test.org/";
        String XML = "<root xmlns:ns='"+URI+"'>ns:name  </root>";
        XMLStreamReader2 sr = getRootReader(XML);
        QName n = sr.getElementAsQName();
        assertNotNull(n);
        assertEquals("name", n.getLocalPart());
        assertEquals("ns", n.getPrefix());
        assertEquals(URI, n.getNamespaceURI());
        sr.close();
    }

    public void testInvalidQNameElemUnbound()
        throws Exception
    {
        XMLStreamReader2 sr = getRootReader("<root>ns:name  </root>");
        // First, unbound namespace prefix
        try {
            /*QName n =*/ sr.getElementAsQName();
            fail("Expected an exception for unbound QName prefix");
        } catch (TypedXMLStreamException tex) { }
        sr.close();
    }

    public void testInvalidQNameElemBadChars()
        throws Exception
    {
        XMLStreamReader2 sr = getRootReader("<root xmlns:ns='http://foo'>ns:na?me</root>");
        try {
            /* QName n =*/ sr.getElementAsQName();
            fail("Expected an exception for invalid QName (non-xml-name char in the middle)");
        } catch (TypedXMLStreamException tex) { }
        sr.close();
    }

    public void testValidQNameAttr()
        throws Exception
    {
        String URI = "http://test.org/";
        String XML = "<root xmlns:abc='"+URI+"' attr='   abc:x1\n' />";
        XMLStreamReader2 sr = getRootReader(XML);
        QName n = sr.getAttributeAsQName(0);
        assertNotNull(n);
        assertEquals("x1", n.getLocalPart());
        assertEquals("abc", n.getPrefix());
        assertEquals(URI, n.getNamespaceURI());
        sr.close();
    }

    public void testInvalidQNameAttrUnbound()
        throws Exception
    {
        XMLStreamReader2 sr = getRootReader("<root attr='ns:name  ' />");
        // First, unbound namespace prefix
        try {
            /* QName n =*/ sr.getAttributeAsQName(0);
            fail("Expected an exception for unbound QName prefix");
        } catch (TypedXMLStreamException tex) { }
        sr.close();
    }

    public void testInvalidQNameAttrBadChars()
        throws Exception
    {
        XMLStreamReader2 sr = getRootReader("<root xmlns:ns='http://foo' attr='ns:name:too' />");
        try {
            /* QName n =*/ sr.getAttributeAsQName(0);
            fail("Expected an exception for invalid QName (non-xml-name char in the middle)");
        } catch (TypedXMLStreamException tex) { }
        sr.close();
    }

    /*
    ////////////////////////////////////////
    // Private methods, second-level tests
    ////////////////////////////////////////
     */

    private void checkBooleanElem(String doc, boolean expState)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getRootReader(doc);
        assertEquals(expState, sr.getElementAsBoolean());
        sr.close();
    }

    private void checkBooleanAttr(String doc, boolean expState)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getRootReader(doc);
        // Assumption is that there's just one attribute...
        boolean actState = sr.getAttributeAsBoolean(0);
        assertEquals(expState, actState);
        sr.close();
    }

    private void checkBooleanElemException(String doc)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getRootReader(doc);
        try {
            /*boolean b =*/ sr.getElementAsBoolean();
            fail("Expected exception for invalid input ["+doc+"]");
        } catch (TypedXMLStreamException xse) {
            ; // good
        }
    }

    private void checkBooleanAttrException(String doc)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getRootReader(doc);
        try {
            /*boolean b =*/ sr.getAttributeAsBoolean(0);
            fail("Expected exception for invalid input ["+doc+"]");
        } catch (TypedXMLStreamException xse) {
            ; // good
        }
    }

    private void checkIntElem(String doc, int expState)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getRootReader(doc);
        assertEquals(expState, sr.getElementAsInt());
        sr.close();
    }

    private void checkIntAttr(String doc, int expState)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getRootReader(doc);
        // Assumption is that there's just one attribute...
        int actState = sr.getAttributeAsInt(0);
        assertEquals(expState, actState);
        sr.close();
    }

    private void checkIntElemException(String doc)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getRootReader(doc);
        try {
            /*int b =*/ sr.getElementAsInt();
            fail("Expected exception for invalid input ["+doc+"]");
        } catch (TypedXMLStreamException xse) {
            ; // good
        }
    }

    private void checkIntAttrException(String doc)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getRootReader(doc);
        try {
            /*int b =*/ sr.getAttributeAsInt(0);
            fail("Expected exception for invalid input ["+doc+"]");
        } catch (TypedXMLStreamException xse) {
            ; // good
        }
    }

    private void checkLongElem(String doc, long expState)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getRootReader(doc);
        assertEquals(expState, sr.getElementAsLong());
        sr.close();
    }

    private void checkLongAttr(String doc, long expState)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getRootReader(doc);
        // Assumption is that there's just one attribute...
        long actState = sr.getAttributeAsLong(0);
        assertEquals(expState, actState);
        sr.close();
    }

    private void checkLongElemException(String doc)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getRootReader(doc);
        try {
            /*long b =*/ sr.getElementAsLong();
            fail("Expected exception for invalid input ["+doc+"]");
        } catch (TypedXMLStreamException xse) {
            ; // good
        }
    }

    private void checkLongAttrException(String doc)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getRootReader(doc);
        try {
            /*long b =*/ sr.getAttributeAsLong(0);
            fail("Expected exception for invalid input ["+doc+"]");
        } catch (TypedXMLStreamException xse) {
            ; // good
        }
    }

    private void checkFloatElem(String doc, float expState)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getRootReader(doc);
        assertEquals(expState, sr.getElementAsFloat());
        sr.close();
    }

    private void checkFloatAttr(String doc, float expState)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getRootReader(doc);
        // Assumption is that there's just one attribute...
        float actState = sr.getAttributeAsFloat(0);
        assertEquals(expState, actState);
        sr.close();
    }

    private void checkFloatElemException(String doc)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getRootReader(doc);
        try {
            /*float b =*/ sr.getElementAsFloat();
            fail("Expected exception for invalid input ["+doc+"]");
        } catch (TypedXMLStreamException xse) {
            ; // good
        }
    }

    private void checkFloatAttrException(String doc)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getRootReader(doc);
        try {
            /*float b =*/ sr.getAttributeAsFloat(0);
            fail("Expected exception for invalid input ["+doc+"]");
        } catch (TypedXMLStreamException xse) {
            ; // good
        }
    }

    private void checkDoubleElem(String doc, double expState)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getRootReader(doc);
        assertEquals(expState, sr.getElementAsDouble());
        sr.close();
    }

    private void checkDoubleAttr(String doc, double expState)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getRootReader(doc);
        // Assumption is that there's just one attribute...
        double actState = sr.getAttributeAsDouble(0);
        assertEquals(expState, actState);
        sr.close();
    }

    private void checkDoubleElemException(String doc)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getRootReader(doc);
        try {
            /*double b =*/ sr.getElementAsDouble();
            fail("Expected exception for invalid input ["+doc+"]");
        } catch (TypedXMLStreamException xse) {
            ; // good
        }
    }

    private void checkDoubleAttrException(String doc)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getRootReader(doc);
        try {
            /*double b =*/ sr.getAttributeAsDouble(0);
            fail("Expected exception for invalid input ["+doc+"]");
        } catch (TypedXMLStreamException xse) {
            ; // good
        }
    }

    /*
    ////////////////////////////////////////
    // Abstract methods
    ////////////////////////////////////////
     */

    protected abstract XMLStreamReader2 getReader(String contents)
        throws Exception;

    /*
    ////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////
     */

    private void assertEquals(float a, float b)
    {
        if (Float.isNaN(a)) {
            assertTrue(Float.isNaN(b));
        } else if (a != b) {
            assertEquals(a, b, 1000.0f); // just to make it fail
        }
    }

    private void assertEquals(double a, double b)
    {
        if (Double.isNaN(a)) {
            assertTrue(Double.isNaN(b));
        } else if (a != b) {
            assertEquals(a, b, 1000.0f); // just to make it fail
        }
    }

    // XMLStreamReader2 extends TypedXMLStreamReader
    protected XMLStreamReader2 getRootReader(String str)
        throws XMLStreamException
    {
        XMLStreamReader2 sr;
        try {
            sr = getReader(str);
        } catch (XMLStreamException xse) {
            throw xse;
        } catch (Exception e) {
            throw new XMLStreamException(e);
        }
        assertTokenType(START_DOCUMENT, sr.getEventType());
        while (sr.next() != START_ELEMENT) { }
        assertTokenType(START_ELEMENT, sr.getEventType());
        return sr;
    }
}
