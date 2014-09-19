package stax2.typed;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Random;
import java.util.StringTokenizer;

import javax.xml.namespace.QName;
import javax.xml.stream.*;

import org.codehaus.stax2.*;
import org.codehaus.stax2.typed.*;

import stax2.BaseStax2Test;

/**
 * Base class that contains set of simple unit tests to verify implementation
 * of {@link TypedXMLStreamWriter}. Concrete sub-classes are used to
 * test both native and wrapped Stax2 implementations.
 *
 * @author Tatu Saloranta
 */
public abstract class WriterTestBase
    extends BaseStax2Test
{
    final static int[] ARRAY_TEST_LENGTHS = new int[] {
        3, 8, 25, 120, 16, 99, 253, 1099, 2866, 37242
    };

    /*
    ////////////////////////////////////////
    // Tests for numeric/enum types
    ////////////////////////////////////////
     */

    public void testSimpleBooleanElem()
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 w = getTypedWriter(bos);
        writeBooleanElem(w, true);
	byte[] data = closeWriter(w, bos);
        checkBooleanElem(data, true);

        w = getTypedWriter(bos);
        writeBooleanElem(w, false);
	data = closeWriter(w, bos);
        checkBooleanElem(data, false);
    }

    public void testSimpleBooleanAttr()
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 w = getTypedWriter(bos);
        writeBooleanAttr(w, true);
	byte[] data = closeWriter(w, bos);
        checkBooleanAttr(data, true);

        w = getTypedWriter(bos);
        writeBooleanAttr(w, false);
	data = closeWriter(w, bos);
        checkBooleanAttr(data, false);
    }

    public void testMultipleBooleanAttr()
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 w = getTypedWriter(bos);

        w.writeStartDocument();
        w.writeStartElement("root");

        w.writeBooleanAttribute(null, null, "a1", true);
        w.writeBooleanAttribute(null, null, "xyz", false);
        w.writeBooleanAttribute(null, null, "_attr3", true);

        w.writeEndElement();
        w.writeEndDocument();

	byte[] data = closeWriter(w, bos);
        XMLStreamReader2 sr = getRootReader(data);
        assertEquals(3, sr.getAttributeCount());
        int ix1 = sr.getAttributeIndex("", "a1");
        int ix2 = sr.getAttributeIndex("", "xyz");
        int ix3 = sr.getAttributeIndex("", "_attr3");
        if (ix1 < 0 || ix2 < 0 || ix3 < 0) {
            fail("Couldn't find indexes of attributes: a1="+ix1+", xyz="+ix2+", _attr3="+ix3);
        }
        assertTrue(sr.getAttributeAsBoolean(ix1));
        assertFalse(sr.getAttributeAsBoolean(ix2));
        assertTrue(sr.getAttributeAsBoolean(ix3));

        sr.close();
    }

    public void testSimpleIntElem()
        throws XMLStreamException
    {
        int[] values = new int[] {
            0, 3, -9, 999, -77, 1000000000, -1000000000,
            Integer.MIN_VALUE, Integer.MAX_VALUE
        };
        for (int i = 0; i < values.length; ++i) {
            int value = values[i];
            assertXML("<root>"+value+"</root>", writeIntElemDoc("root", value));
        }
    }

    public void testSimpleIntAttr()
        throws XMLStreamException
    {
        int[] values = new int[] {
            0, 3, -7, 123, -102, 1000000, -999999,
            Integer.MIN_VALUE, Integer.MAX_VALUE
        };
        for (int i = 0; i < values.length; ++i) {
            int value = values[i];
            assertXML("<a attr='"+value+"'></a>", writeIntAttrDoc("a", "attr", value));
        }
    }

    public void testMultipleIntAttr()
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 w = getTypedWriter(bos);

        w.writeStartDocument();
        w.writeStartElement("root");

        w.writeIntAttribute(null, null, "a", 0);
        w.writeIntAttribute(null, null, "bz", Integer.MAX_VALUE);
        w.writeIntAttribute(null, null, "___", -1200300400);

        w.writeEndElement();
        w.writeEndDocument();

	byte[] data = closeWriter(w, bos);
        XMLStreamReader2 sr = getRootReader(data);
        assertEquals(3, sr.getAttributeCount());
        int ix1 = sr.getAttributeIndex("", "a");
        int ix2 = sr.getAttributeIndex("", "bz");
        int ix3 = sr.getAttributeIndex("", "___");
        if (ix1 < 0 || ix2 < 0 || ix3 < 0) {
            fail("Couldn't find indexes of attributes: a="+ix1+", bz="+ix2+", ___="+ix3);
        }
        assertEquals(0, sr.getAttributeAsInt(ix1));
        assertEquals(Integer.MAX_VALUE, sr.getAttributeAsInt(ix2));
        assertEquals(-1200300400, sr.getAttributeAsInt(ix3));

        sr.close();
    }

    public void testSimpleLongElem()
        throws XMLStreamException
    {
        long[] values = new long[] {
            0, 3, -9, 999, -77, 1000000000, -1000000000,
            123456789012345678L, 
            -987654321098765423L,
            Long.MIN_VALUE, Long.MAX_VALUE
        };
        for (int i = 0; i < values.length; ++i) {
            long value = values[i];
            assertXML("<root>"+value+"</root>", writeLongElemDoc("root", value));
        }
    }

    public void testSimpleLongAttr()
        throws XMLStreamException
    {
        long[] values = new long[] {
            0, 3, -9, 999, -77, 1000000002, -2000000004,
            123456789012345678L, 
            -987654321098765423L,
            Long.MIN_VALUE, Long.MAX_VALUE
        };
        for (int i = 0; i < values.length; ++i) {
            long value = values[i];
            assertXML("<a attr='"+value+"'></a>", writeLongAttrDoc("a", "attr", value));
        }
    }

    public void testMultipleLongAttr()
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 w = getTypedWriter(bos);

        w.writeStartDocument();
        w.writeStartElement("root");

        w.writeLongAttribute(null, null, "a", 0L);
        w.writeLongAttribute(null, null, "bz", Long.MAX_VALUE);
        w.writeLongAttribute(null, null, "___", -1200300400L);

        w.writeEndElement();
        w.writeEndDocument();

	byte[] data = closeWriter(w, bos);
        XMLStreamReader2 sr = getRootReader(data);
        assertEquals(3, sr.getAttributeCount());
        int ix1 = sr.getAttributeIndex("", "a");
        int ix2 = sr.getAttributeIndex("", "bz");
        int ix3 = sr.getAttributeIndex("", "___");
        if (ix1 < 0 || ix2 < 0 || ix3 < 0) {
            fail("Couldn't find indexes of attributes: a="+ix1+", bz="+ix2+", ___="+ix3);
        }
        assertEquals(0L, sr.getAttributeAsLong(ix1));
        assertEquals(Long.MAX_VALUE, sr.getAttributeAsLong(ix2));
        assertEquals(-1200300400L, sr.getAttributeAsLong(ix3));

        sr.close();
    }

    public void testSimpleFloatElem()
        throws XMLStreamException
    {
        float[] values = new float[] {
            0.0f,  10.47f, (float) (1.0 / 3.0), -0.25f,
            Float.MIN_VALUE, Float.MAX_VALUE,
            Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY
        };
        for (int i = 0; i < values.length; ++i) {
            float value = values[i];
            assertXML("<root>"+value+"</root>", writeFloatElemDoc("root", value));
        }
    }

    public void testSimpleFloatAttr()
        throws XMLStreamException
    {
        float[] values = new float[] {
            0.0f,  10.47f, (float) (1.0 / 3.0), -0.25f,
            Float.MIN_VALUE, Float.MAX_VALUE,
            Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY
        };
        for (int i = 0; i < values.length; ++i) {
            float value = values[i];
            assertXML("<a attr='"+value+"'></a>", writeFloatAttrDoc("a", "attr", value));
        }
    }

    public void testSimpleDoubleElem()
        throws XMLStreamException
    {
        double[] values = new double[] {
            0.0f,  10.47f, (1.0 / 3.0), -0.25f,
            Double.MIN_VALUE, Double.MAX_VALUE,
            Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY
        };
        for (int i = 0; i < values.length; ++i) {
            double value = values[i];
            assertXML("<root>"+value+"</root>", writeDoubleElemDoc("root", value));
        }
    }

    public void testSimpleDoubleAttr()
        throws XMLStreamException
    {
        double[] values = new double[] {
            0.0f,  10.47f, (1.0 / 3.0), -0.25f,
            Double.MIN_VALUE, Double.MAX_VALUE,
            Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY
        };
        for (int i = 0; i < values.length; ++i) {
            double value = values[i];
            assertXML("<a attr='"+value+"'></a>", writeDoubleAttrDoc("a", "attr", value));
        }
    }

    public void testBigInteger()
        throws XMLStreamException
    {
        BigInteger I = BigInteger.valueOf(3);
        Random rnd = new Random(2);
        for (int i = 1; i < 200; ++i) {
            assertXML("<root>"+I+"</root>", writeIntegerElemDoc("root", I));
            assertXML("<a attr='"+I+"'></a>", writeIntegerAttrDoc("a", "attr", I));
            I = I.multiply(BigInteger.valueOf(10)).add(BigInteger.valueOf(rnd.nextInt() & 0xF));
        }
    }

    public void testBigDecimal()
        throws XMLStreamException
    {
        BigDecimal D = BigDecimal.valueOf(1L);
        Random rnd = new Random(9);
        for (int i = 1; i < 200; ++i) {
            assertXML("<root>"+D+"</root>", writeDecimalElemDoc("root", D));
            assertXML("<a attr='"+D+"'></a>", writeDecimalAttrDoc("a", "attr", D));
            // Ok, then, add a small integer, divide by 10 to generate digits
            D = D.add(BigDecimal.valueOf(rnd.nextInt() & 0xF)).divide(BigDecimal.valueOf(10L));
        }
    }

    public void testQNameNonRepairing()
        throws XMLStreamException
    {
        doTestQName(false);
    }

    public void testQNameRepairing()
        throws XMLStreamException
    {
        doTestQName(true);
    }

    private void doTestQName(boolean repairing)
        throws XMLStreamException
    {
        final String URI = "http://my.uri";
        QName n = new QName(URI, "elem", "ns");

        assertXML("<root xmlns:ns='"+URI+"'>ns:elem</root>", writeQNameElemDoc("root", n, repairing));
        assertXML("<root xmlns:ns='"+URI+"' attr='ns:elem'></root>",
                     writeQNameAttrDoc("root", "attr", n, repairing));
    }

    public void testIntArraysElem()
        throws XMLStreamException
    {
        doTestIntArrays(false);
    }

    public void testIntArraysAttr()
        throws XMLStreamException
    {
        doTestIntArrays(true);
    }

    private void doTestIntArrays(boolean testAttr)
        throws XMLStreamException
    {
        for (int i = 0; i <= ARRAY_TEST_LENGTHS.length; ++i) {
            int[] data;
            if (i == 0) {
                data = new int[] {
                    0, -139, 29, Integer.MAX_VALUE, 1, Integer.MIN_VALUE };
            } else {
                Random rnd = new Random(9);
                int len = ARRAY_TEST_LENGTHS[i-1];
                data = new int[len];
                for (int ix = 0; ix < len; ++ix) {
                    data[ix] = rnd.nextInt();
                }
            }
            String contents;
            if (testAttr) {
                contents = getAttributeContent(writeIntArrayAttrDoc("root", "attr", data));
            } else {
                contents = getElementContent(writeIntArrayElemDoc("root", data));
            }
            StringTokenizer st = new StringTokenizer(contents);
            int count = 0;
            while (st.hasMoreTokens()) {
                String exp = String.valueOf(data[count]);
                String act = st.nextToken();

                if (!exp.equals(act)) {
                    fail("Incorrect entry #"+count+"/"+data.length+": act = '"+act+"' (exp '"+exp+"')");
                }
                ++count;
            }
            assertEquals(data.length, count);
        }
    }

    public void testLongArraysElem()
        throws XMLStreamException
    {
        doTestLongArrays(false);
    }

    public void testLongArraysAttr()
        throws XMLStreamException
    {
        doTestLongArrays(true);
    }

    private void doTestLongArrays(boolean testAttr)
        throws XMLStreamException
    {
        for (int i = 0; i <= ARRAY_TEST_LENGTHS.length; ++i) {
            long[] data;
            if (i == 0) {
                data = new long[] {
                    0, -139, 29, Long.MAX_VALUE, 1, Long.MIN_VALUE };
            } else {
                Random rnd = new Random(9);
                int len = ARRAY_TEST_LENGTHS[i-1];
                data = new long[len];
                for (int ix = 0; ix < len; ++ix) {
                    data[ix] = rnd.nextLong();
                }
            }
            String contents;
            if (testAttr) {
                contents = getAttributeContent(writeLongArrayAttrDoc("root", "attr", data));
            } else {
                contents = getElementContent(writeLongArrayElemDoc("root", data));
            }
            StringTokenizer st = new StringTokenizer(contents);
            int count = 0;
            while (st.hasMoreTokens()) {
                String exp = String.valueOf(data[count]);
                String act = st.nextToken();

                if (!exp.equals(act)) {
                    fail("Incorrect entry #"+count+"/"+data.length+": act = '"+act+"' (exp '"+exp+"')");
                }
                ++count;
            }
            assertEquals(data.length, count);
        }
    }

    public void testFloatArraysElem()
        throws XMLStreamException
    {
        doTestFloatArrays(false);
    }

    public void testFloatArraysAttr()
        throws XMLStreamException
    {
        doTestFloatArrays(true);
    }

    @SuppressWarnings("cast")
	private void doTestFloatArrays(boolean testAttr)
        throws XMLStreamException
    {
        for (int i = 0; i <= ARRAY_TEST_LENGTHS.length; ++i) {
            float[] data;
            if (i == 0) {
                data = new float[] {
                    0, -139, 29, Float.MAX_VALUE, 1, Float.MIN_VALUE };
            } else {
                Random rnd = new Random(9);
                int len = ARRAY_TEST_LENGTHS[i-1];
                data = new float[len];
                for (int ix = 0; ix < len; ++ix) {
                    // Need to scale: nextFloat is [0.0, 1.0[
                    float value = rnd.nextFloat();
                    if (rnd.nextBoolean()) {
                        value = (float) (value * rnd.nextInt());
                    }
                    if (rnd.nextBoolean()) {
                        value = -value;
                    }
                    data[ix] = value;
                }
            }
            String contents;
            if (testAttr) {
                contents = getAttributeContent(writeFloatArrayAttrDoc("root", "attr", data));
            } else {
                contents = getElementContent(writeFloatArrayElemDoc("root", data));
            }
            StringTokenizer st = new StringTokenizer(contents);
            int count = 0;
            while (st.hasMoreTokens()) {
                String exp = String.valueOf(data[count]);
                String act = st.nextToken();

                if (!exp.equals(act)) {
                    fail("Incorrect entry #"+count+"/"+data.length+": act = '"+act+"' (exp '"+exp+"')");
                }
                ++count;
            }
            assertEquals(data.length, count);
        }
    }

    public void testDoubleArraysElem()
        throws XMLStreamException
    {
        doTestDoubleArrays(false);
    }

    public void testDoubleArraysAttr()
        throws XMLStreamException
    {
        doTestDoubleArrays(true);
    }

    private void doTestDoubleArrays(boolean testAttr)
        throws XMLStreamException
    {
        for (int i = 0; i <= ARRAY_TEST_LENGTHS.length; ++i) {
            double[] data;
            if (i == 0) {
                data = new double[] {
                    0, -139, 29, Double.MAX_VALUE, 1, Double.MIN_VALUE };
            } else {
                Random rnd = new Random(9);
                int len = ARRAY_TEST_LENGTHS[i-1];
                data = new double[len];
                for (int ix = 0; ix < len; ++ix) {
                    // Need to scale: nextDouble is [0.0, 1.0[
                    double value = rnd.nextDouble();
                    if (rnd.nextBoolean()) {
                        value = (value * rnd.nextLong());
                    }
                    if (rnd.nextBoolean()) {
                        value = -value;
                    }
                    data[ix] = value;
                }
            }
            String contents;
            if (testAttr) {
                contents = getAttributeContent(writeDoubleArrayAttrDoc("root", "attr", data));
            } else {
                contents = getElementContent(writeDoubleArrayElemDoc("root", data));
            }
            StringTokenizer st = new StringTokenizer(contents);
            int count = 0;
            while (st.hasMoreTokens()) {
                String exp = String.valueOf(data[count]);
                String act = st.nextToken();

                if (!exp.equals(act)) {
                    fail("Incorrect entry #"+count+"/"+data.length+": act = '"+act+"' (exp '"+exp+"')");
                }
                ++count;
            }
            assertEquals(data.length, count);
        }
    }

    /*
    ////////////////////////////////////////
    // Private methods, checking typed doc
    ////////////////////////////////////////
     */

    private void checkBooleanElem(byte[] data, boolean expState)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getRootReader(data);
        assertEquals(expState, sr.getElementAsBoolean());
        sr.close();
    }

    private void checkBooleanAttr(byte[] data, boolean expState)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getRootReader(data);
        assertEquals(expState, sr.getAttributeAsBoolean(0));
        sr.close();
    }

    private void writeBooleanElem(TypedXMLStreamWriter sw, boolean b)
        throws XMLStreamException
    {
        sw.writeStartDocument();
        sw.writeStartElement("root");
        sw.writeBoolean(b);
        sw.writeEndElement();
        sw.writeEndDocument();
    }

    private void writeBooleanAttr(TypedXMLStreamWriter sw, boolean b)
        throws XMLStreamException
    {
        sw.writeStartDocument();
        sw.writeStartElement("root");
        sw.writeBooleanAttribute(null, null, "attr", b);
        sw.writeEndElement();
        sw.writeEndDocument();
    }

    private String writeIntElemDoc(String elem, int value)
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 sw = getTypedWriter(bos);
        // Let's not write start doc, to avoid getting xml declaration
        //sw.writeStartDocument();
        sw.writeStartElement(elem);
        sw.writeInt(value);
        sw.writeEndElement();
        sw.writeEndDocument();
        return getUTF8(sw, bos);
    }

    private String writeIntAttrDoc(String elem, String attr, int value)
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 sw = getTypedWriter(bos);
        // Let's not write start doc, to avoid getting xml declaration
        //sw.writeStartDocument();
        sw.writeStartElement(elem);
        sw.writeIntAttribute(null, null, attr, value);
        sw.writeCharacters(""); // to avoid empty elem
        sw.writeEndElement();
        sw.writeEndDocument();
        String str = getUTF8(sw, bos);
        // One twist: need to ensure quotes are single-quotes (for the test)
        return str.replace('"', '\'');
    }

    private String writeLongElemDoc(String elem, long value)
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 sw = getTypedWriter(bos);
        // Let's not write start doc, to avoid getting xml declaration
        //sw.writeStartDocument();
        sw.writeStartElement(elem);
        sw.writeLong(value);
        sw.writeEndElement();
        sw.writeEndDocument();
        return getUTF8(sw, bos);
    }

    private String writeLongAttrDoc(String elem, String attr, long value)
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 sw = getTypedWriter(bos);
        // Let's not write start doc, to avoid getting xml declaration
        //sw.writeStartDocument();
        sw.writeStartElement(elem);
        sw.writeLongAttribute(null, null, attr, value);
        sw.writeCharacters(""); // to avoid empty elem
        sw.writeEndElement();
        sw.writeEndDocument();
        String str = getUTF8(sw, bos);
        // One twist: need to ensure quotes are single-quotes (for the test)
        return str.replace('"', '\'');
    }

    private String writeFloatElemDoc(String elem, float value)
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 sw = getTypedWriter(bos);
        // Let's not write start doc, to avoid getting xml declaration
        //sw.writeStartDocument();
        sw.writeStartElement(elem);
        sw.writeFloat(value);
        sw.writeEndElement();
        sw.writeEndDocument();
        return getUTF8(sw, bos);
    }

    private String writeFloatAttrDoc(String elem, String attr, float value)
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 sw = getTypedWriter(bos);
        // Let's not write start doc, to avoid getting xml declaration
        //sw.writeStartDocument();
        sw.writeStartElement(elem);
        sw.writeFloatAttribute(null, null, attr, value);
        sw.writeCharacters(""); // to avoid empty elem
        sw.writeEndElement();
        sw.writeEndDocument();
        String str = getUTF8(sw, bos);
        // One twist: need to ensure quotes are single-quotes (for the test)
        return str.replace('"', '\'');
    }

    private String writeDoubleElemDoc(String elem, double value)
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 sw = getTypedWriter(bos);
        sw.writeStartElement(elem);
        sw.writeDouble(value);
        sw.writeEndElement();
        sw.writeEndDocument();
        return getUTF8(sw, bos);
    }

    private String writeDoubleAttrDoc(String elem, String attr, double value)
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 sw = getTypedWriter(bos);
        sw.writeStartElement(elem);
        sw.writeDoubleAttribute(null, null, attr, value);
        sw.writeCharacters(""); // to avoid empty elem
        sw.writeEndElement();
        sw.writeEndDocument();
        String str = getUTF8(sw, bos);
        // One twist: need to ensure quotes are single-quotes (for the test)
        return str.replace('"', '\'');
    }

    private String writeIntegerElemDoc(String elem, BigInteger value)
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 sw = getTypedWriter(bos);
        sw.writeStartElement(elem);
        sw.writeInteger(value);
        sw.writeEndElement();
        sw.writeEndDocument();
        return getUTF8(sw, bos);
    }

    private String writeIntegerAttrDoc(String elem, String attr, BigInteger value)
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 sw = getTypedWriter(bos);
        sw.writeStartElement(elem);
        sw.writeIntegerAttribute(null, null, attr, value);
        sw.writeCharacters(""); // to avoid empty elem
        sw.writeEndElement();
        sw.writeEndDocument();
        String str = getUTF8(sw, bos);
        // One twist: need to ensure quotes are single-quotes (for the test)
        return str.replace('"', '\'');
    }

    private String writeDecimalElemDoc(String elem, BigDecimal value)
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 sw = getTypedWriter(bos);
        sw.writeStartElement(elem);
        sw.writeDecimal(value);
        sw.writeEndElement();
        sw.writeEndDocument();
        return getUTF8(sw, bos);
    }

    private String writeDecimalAttrDoc(String elem, String attr, BigDecimal value)
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 sw = getTypedWriter(bos);
        sw.writeStartElement(elem);
        sw.writeDecimalAttribute(null, null, attr, value);
        sw.writeCharacters(""); // to avoid empty elem
        sw.writeEndElement();
        sw.writeEndDocument();
        String str = getUTF8(sw, bos);
        // One twist: need to ensure quotes are single-quotes (for the test)
        return str.replace('"', '\'');
    }

    private String writeQNameElemDoc(String elem, QName n, boolean repairing)
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 sw = getTypedWriter(bos, repairing);
        sw.writeStartElement(elem);
        if (!repairing) {
            sw.writeNamespace(n.getPrefix(), n.getNamespaceURI());
        }
        sw.writeQName(n);
        sw.writeEndElement();
        sw.writeEndDocument();
        String str = getUTF8(sw, bos);
        return str.replace('"', '\'');
    }

    private String writeQNameAttrDoc(String elem, String attr, QName n, boolean repairing)
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 sw = getTypedWriter(bos, repairing);
        sw.writeStartElement(elem);
        if (!repairing) {
            sw.writeNamespace(n.getPrefix(), n.getNamespaceURI());
        }
        sw.writeQNameAttribute(null, null, attr, n);
        sw.writeCharacters(""); // to avoid empty elem
        sw.writeEndElement();
        sw.writeEndDocument();
        String str = getUTF8(sw, bos);
        return str.replace('"', '\'');
    }

    private byte[] writeIntArrayElemDoc(String elem, int[] values)
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 sw = getTypedWriter(bos);
        sw.writeStartElement(elem);
        if ((values.length % 2) == 1) { // odd -> single write
            sw.writeIntArray(values, 0, values.length);
        } else { // even -> split in halves
            int offset = values.length / 2;
            sw.writeIntArray(values, 0, offset);
            sw.writeIntArray(values, offset, values.length - offset);
        }
        sw.writeEndElement();
        sw.writeEndDocument();
	return closeWriter(sw, bos);
    }

    private byte[] writeIntArrayAttrDoc(String elem, String attr, int[] values)
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 sw = getTypedWriter(bos);
        sw.writeStartElement(elem);
        sw.writeIntArrayAttribute(null, null, attr, values);
        sw.writeEndElement();
        sw.writeEndDocument();
	return closeWriter(sw, bos);
    }

    private byte[] writeLongArrayElemDoc(String elem, long[] values)
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 sw = getTypedWriter(bos);
        sw.writeStartElement(elem);
        if ((values.length % 2) == 1) { // odd -> single write
            sw.writeLongArray(values, 0, values.length);
        } else { // even -> split in halves
            int offset = values.length / 2;
            sw.writeLongArray(values, 0, offset);
            sw.writeLongArray(values, offset, values.length - offset);
        }
        sw.writeEndElement();
        sw.writeEndDocument();
	return closeWriter(sw, bos);
    }

    private byte[] writeLongArrayAttrDoc(String elem, String attr, long[] values)
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 sw = getTypedWriter(bos);
        sw.writeStartElement(elem);
        sw.writeLongArrayAttribute(null, null, attr, values);
        sw.writeEndElement();
        sw.writeEndDocument();
	return closeWriter(sw, bos);
    }

    private byte[] writeFloatArrayElemDoc(String elem, float[] values)
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 sw = getTypedWriter(bos);
        sw.writeStartElement(elem);
        if ((values.length % 2) == 1) { // odd -> single write
            sw.writeFloatArray(values, 0, values.length);
        } else { // even -> split in halves
            int offset = values.length / 2;
            sw.writeFloatArray(values, 0, offset);
            sw.writeFloatArray(values, offset, values.length - offset);
        }
        sw.writeEndElement();
        sw.writeEndDocument();
	return closeWriter(sw, bos);
    }

    private byte[] writeFloatArrayAttrDoc(String elem, String attr, float[] values)
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 sw = getTypedWriter(bos);
        sw.writeStartElement(elem);
        sw.writeFloatArrayAttribute(null, null, attr, values);
        sw.writeEndElement();
        sw.writeEndDocument();
	return closeWriter(sw, bos);
    }

    private byte[] writeDoubleArrayElemDoc(String elem, double[] values)
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 sw = getTypedWriter(bos);
        sw.writeStartElement(elem);
        if ((values.length % 2) == 1) { // odd -> single write
            sw.writeDoubleArray(values, 0, values.length);
        } else { // even -> split in halves
            int offset = values.length / 2;
            sw.writeDoubleArray(values, 0, offset);
            sw.writeDoubleArray(values, offset, values.length - offset);
        }
        sw.writeEndElement();
        sw.writeEndDocument();
	return closeWriter(sw, bos);
    }

    private byte[] writeDoubleArrayAttrDoc(String elem, String attr, double[] values)
        throws XMLStreamException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        XMLStreamWriter2 sw = getTypedWriter(bos);
        sw.writeStartElement(elem);
        sw.writeDoubleArrayAttribute(null, null, attr, values);
        sw.writeEndElement();
        sw.writeEndDocument();
	return closeWriter(sw, bos);
    }

    /*
    ////////////////////////////////////////
    // Abstract methods
    ////////////////////////////////////////
     */

    protected abstract XMLStreamWriter2 getTypedWriter(ByteArrayOutputStream out,
                                                       boolean repairing)
        throws XMLStreamException;

    protected abstract byte[] closeWriter(XMLStreamWriter sw, ByteArrayOutputStream out)
        throws XMLStreamException;

    /*
    ////////////////////////////////////////
    // Private methods, constructing writers
    ////////////////////////////////////////
     */

    private XMLStreamWriter2 getTypedWriter(ByteArrayOutputStream out)
        throws XMLStreamException
    {
        return getTypedWriter(out, false);
    }

    // XMLStreamReader2 extends TypedXMLStreamReader
    private XMLStreamReader2 getRootReader(byte[] data)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getReader(data);
        assertTokenType(START_DOCUMENT, sr.getEventType());
        while (sr.next() != START_ELEMENT) { }
        assertTokenType(START_ELEMENT, sr.getEventType());
        return sr;
    }

    private XMLStreamReader2 getReader(byte[] data)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        setCoalescing(f, false); // shouldn't really matter
        setNamespaceAware(f, true);
        return (XMLStreamReader2) f.createXMLStreamReader(new ByteArrayInputStream(data));
    }

    private String getUTF8(XMLStreamWriter sw, ByteArrayOutputStream bos)
	throws XMLStreamException
    {
	byte[] data = closeWriter(sw, bos);
        try {
	    return new String(data, "UTF-8");
        } catch (IOException ioe) {
            throw new IllegalArgumentException(ioe);
        }
    }

    private String getElementContent(byte[] data)
        throws XMLStreamException
    {
        XMLStreamReader sr = getReader(data);
        assertTokenType(START_DOCUMENT, sr.getEventType());
        while (sr.next() != START_ELEMENT) { }
        assertTokenType(START_ELEMENT, sr.getEventType());
        String content = sr.getElementText();
        sr.close();
        return content;
    }

    private String getAttributeContent(byte[] data)
        throws XMLStreamException
    {
        XMLStreamReader sr = getReader(data);
        assertTokenType(START_DOCUMENT, sr.getEventType());
        while (sr.next() != START_ELEMENT) { }
        assertTokenType(START_ELEMENT, sr.getEventType());
        assertEquals(1, sr.getAttributeCount());
        String content = sr.getAttributeValue(0);
        sr.close();
        return content;
    }

    /**
     * Helper method to contain modifications we may need to
     * reliably compare equivalency of result xml to expected
     * results.
     */
    void assertXML(String exp, String act)
    {
	// First: let's trim out xml decl, if any
	act = act.trim();
	if (act.startsWith("<?xml")) {
	    int ix = act.indexOf("?>");
	    act = act.substring(ix+2);
	}
	// Usually this is enough:
	if (exp.equals(act)) {
	    return;
	}
	/* If not, let's see if we can still find them equal;
	 * given simplicity of docs, we only need to be concerned
	 * with '<a></a>' -> '<a/>' change it seems. Expected
	 * results seem to only differ by that much, for now...
	 */
	int ix = act.indexOf("/>");
	if (ix > 0) {
	    // ugh. this is ugly, yes... if it breaks, should just rewrite completely
	    String prefix = act.substring(0, ix).trim();
	    String suffix = act.substring(ix+2); // probably empty tho

	    ix = prefix.lastIndexOf("<");
	    int ix2 = prefix.indexOf(' ', ix);
	    // If it was just name it'd be simpler, but there may be attr(s)
	    String name = (ix2 < 0) ? prefix.substring(ix+1) : prefix.substring(ix+1, ix2);

	    act = prefix + "></" + name + ">"+suffix;
	}
	assertEquals(exp, act);
    }
}
