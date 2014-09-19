package stax2.typed;

import java.lang.reflect.Array;
import java.util.Random;

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
public abstract class ReaderArrayTestBase
    extends BaseStax2Test
{
    // Let's test variable length arrays
    final static int[] COUNTS_ELEM = new int[] {
        7, 39, 116, 900, 5003
    };
    final static int[] COUNTS_ATTR = new int[] {
        5, 17, 59, 357, 1920
    };

    /*
    ////////////////////////////////////////
    // Abstract methods
    ////////////////////////////////////////
     */

    protected abstract XMLStreamReader2 getReader(String contents)
        throws XMLStreamException;

    /*
    ////////////////////////////////////////
    // Test methods, elem, valid
    ////////////////////////////////////////
     */

    public void testSimpleIntArrayElem() throws XMLStreamException
    {
        _testSimpleIntArrayElem(false);
    }
    public void testSimpleIntArrayElemWithNoise() throws XMLStreamException
    {
        _testSimpleIntArrayElem(true);
    }

    private void _testSimpleIntArrayElem(boolean withNoise)
        throws XMLStreamException
    {
        for (int i = 0; i < COUNTS_ELEM.length; ++i) {
            int len = COUNTS_ELEM[i];
            int[] data = intArray(len);
            String XML = buildDoc(data, withNoise);

            // First, full read
            verifyInts(XML, data, len);
            // Then one by one
            verifyInts(XML, data, 1);
            // And finally, random
            verifyInts(XML, data, -1);
        }
    }

    public void testSimpleLongArrayElem()
        throws XMLStreamException
    {
        _testSimpleLongArrayElem(false);
    }
    public void testSimpleLongArrayElemWithNoise()
        throws XMLStreamException
    {
        _testSimpleLongArrayElem(true);
    }

    private void _testSimpleLongArrayElem(boolean withNoise)
        throws XMLStreamException
    {
        for (int i = 0; i < COUNTS_ELEM.length; ++i) {
            int len = COUNTS_ELEM[i];
            long[] data = longArray(len);
            String XML = buildDoc(data, withNoise);

            // First, full read
            verifyLongs(XML, data, len);
            // Then one by one
            verifyLongs(XML, data, 1);
            // And finally, random
            verifyLongs(XML, data, -1);
        }
    }

    public void testSimpleFloatArrayElem()
        throws XMLStreamException
    {
        _testSimpleFloatArrayElem(false);
    }
    public void testSimpleFloatArrayElemWithNoise()
        throws XMLStreamException
    {
        _testSimpleFloatArrayElem(true);
    }

    private void _testSimpleFloatArrayElem(boolean withNoise)
        throws XMLStreamException
    {
        for (int i = 0; i < COUNTS_ELEM.length; ++i) {
            int len = COUNTS_ELEM[i];
            float[] data = floatArray(len);
            String XML = buildDoc(data, withNoise);

            // First, full read
            verifyFloats(XML, data, len);
            // Then one by one
            verifyFloats(XML, data, 1);
            // And finally, random
            verifyFloats(XML, data, -1);
        }
    }

    public void testSimpleDoubleArrayElem()
        throws XMLStreamException
    {
        _testSimpleDoubleArrayElem(false);
    }
    public void testSimpleDoubleArrayElemWithNoise()
        throws XMLStreamException
    {
        _testSimpleDoubleArrayElem(true);
    }

    private void _testSimpleDoubleArrayElem(boolean withNoise)
        throws XMLStreamException
    {
        for (int i = 0; i < COUNTS_ELEM.length; ++i) {
            int len = COUNTS_ELEM[i];
            double[] data = doubleArray(len);
            String XML = buildDoc(data, withNoise);

            // First, full read
            verifyDoubles(XML, data, len);
            // Then one by one
            verifyDoubles(XML, data, 1);
            // And finally, random
            verifyDoubles(XML, data, -1);
        }
    }

    public void testEmptyElems()
        throws XMLStreamException
    {
        // And then some edge cases too
        for (int i = 0; i < 4; ++i) {
            XMLStreamReader2 sr = getReader("<root />");
            assertTokenType(START_ELEMENT, sr.next());
            int count;

            switch (i) {
            case 0:
                count = sr.readElementAsIntArray(new int[1], 0, 1);
                break;
            case 1:
                count = sr.readElementAsLongArray(new long[1], 0, 1);
                break;
            case 2:
                count = sr.readElementAsFloatArray(new float[1], 0, 1);
                break;
            default:
                count = sr.readElementAsDoubleArray(new double[1], 0, 1);
                break;
            }
            sr.close();
            assertEquals(-1, count);
        }
    }

    /*
    ////////////////////////////////////////
    // Test methods, elem, invalid
    ////////////////////////////////////////
     */

    public void testInvalidIntArrayElem()
        throws XMLStreamException
    {
        XMLStreamReader2 sr;

        for (int i = 0; i < 4; ++i) {
            sr = getReader("<root>1 2</root>");
            // Can't call on START_DOCUMENT
            try {
                switch (i) {
                case 0:
                    sr.readElementAsIntArray(new int[3], 0, 1);
                    fail("Expected an exception when trying to read at START_DOCUMENT");
                case 1:
                    sr.readElementAsLongArray(new long[3], 0, 1);
                    fail("Expected an exception when trying to read at START_DOCUMENT");
                case 2:
                    sr.readElementAsFloatArray(new float[3], 0, 1);
                    fail("Expected an exception when trying to read at START_DOCUMENT");
                default:
                    sr.readElementAsDoubleArray(new double[3], 0, 1);
                    fail("Expected an exception when trying to read at START_DOCUMENT");
                }
            } catch (IllegalStateException ise) { }
            
            sr = getReader("<root><!-- comment --></root>");
            sr.next();
            assertTokenType(COMMENT, sr.next());

            /* Hmmh. Should it be illegal to call on COMMENT?
             * Let's assume it should
             */
            /*
            try {
                switch (i) {
                case 0:
                    sr.readElementAsIntArray(new int[3], 0, 1);
                    fail("Expected an exception when trying to read at COMMENT");
                case 1:
                    sr.readElementAsLongArray(new long[3], 0, 1);
                    fail("Expected an exception when trying to read at COMMENT");
                case 2:
                    sr.readElementAsFloatArray(new float[3], 0, 1);
                    fail("Expected an exception when trying to read at COMMENT");
                default:
                    sr.readElementAsDoubleArray(new double[3], 0, 1);
                    fail("Expected an exception when trying to read at COMMENT");
                }
            } catch (IllegalStateException ise) { }
            */
        }
    }

    /*
    ////////////////////////////////////////
    // Test methods, attr, valid
    ////////////////////////////////////////
     */

    public void testSimpleIntArrayAttr()
        throws XMLStreamException
    {
        for (int i = 0; i < COUNTS_ATTR.length; ++i) {
            int len = COUNTS_ATTR[i];
            int[] data = intArray(len);
            String XML = buildAttrDoc(data);
            verifyIntsAttr(XML, data);
        }
    }

    public void testSimpleLongArrayAttr()
        throws XMLStreamException
    {
        for (int i = 0; i < COUNTS_ATTR.length; ++i) {
            int len = COUNTS_ATTR[i];
            long[] data = longArray(len);
            String XML = buildAttrDoc(data);
            verifyLongsAttr(XML, data);
        }
    }

    public void testSimpleFloatArrayAttr()
        throws XMLStreamException
    {
        for (int i = 0; i < COUNTS_ATTR.length; ++i) {
            int len = COUNTS_ATTR[i];
            float[] data = floatArray(len);
            String XML = buildAttrDoc(data);
            verifyFloatsAttr(XML, data);
        }
    }

    public void testSimpleDoubleArrayAttr()
        throws XMLStreamException
    {
        for (int i = 0; i < COUNTS_ATTR.length; ++i) {
            int len = COUNTS_ATTR[i];
            double[] data = doubleArray(len);
            String XML = buildAttrDoc(data);
            verifyDoublesAttr(XML, data);
        }
    }

    /*
    ////////////////////////////////////////
    // Helper methods
    ////////////////////////////////////////
     */

    private int[] intArray(int count)
    {
        Random r = new Random(count);
        int[] result = new int[count];
        for (int i = 0; i < count; ++i) {
            int base = r.nextInt();
            int shift = (r.nextInt() % 24);
            result[i] = (base >> shift);
        }
        return result;
    }

    private long[] longArray(int count)
    {
        Random r = new Random(count);
        long[] result = new long[count];
        for (int i = 0; i < count; ++i) {
            long base = r.nextLong();
            int shift = (r.nextInt() % 56);
            result[i] = (base >> shift);
        }
        return result;
    }

    private float[] floatArray(int count)
    {
        Random r = new Random(count);
        float[] result = new float[count];
        for (int i = 0; i < count; ++i) {
            float f = r.nextFloat();
            result[i] = r.nextBoolean() ? -f : f;
        }
        return result;
    }

    private double[] doubleArray(int count)
    {
        Random r = new Random(count);
        double[] result = new double[count];
        for (int i = 0; i < count; ++i) {
            double d = r.nextDouble();
            result[i] = r.nextBoolean() ? -d : d;
        }
        return result;
    }

    private String buildDoc(Object dataArray, boolean addNoise)
    {
        int len = Array.getLength(dataArray);
        StringBuilder sb = new StringBuilder(len * 8);
        sb.append("<root>");
        Random r = new Random(Array.get(dataArray, 0).hashCode());
        for (int i = 0; i < len; ++i) {
            Object value = Array.get(dataArray, i).toString();
            sb.append(value);
            // Let's add 25% of time
            if (addNoise && r.nextBoolean() && r.nextBoolean()) {
                if (r.nextBoolean()) {
                    sb.append("<!-- comment: "+value+" -->");
                } else {
                    sb.append("<?pi "+value+"?>");
                }
            }
            sb.append(' ');
        }
        sb.append("</root>");
        return sb.toString();
    }

    private String buildAttrDoc(Object dataArray)
    {
        int len = Array.getLength(dataArray);
        StringBuilder sb = new StringBuilder(len * 8);
        sb.append("<root attr='");
        for (int i = 0; i < len; ++i) {
            Object value = Array.get(dataArray, i).toString();
            sb.append(value);
            sb.append(' ');
        }
        sb.append("' />");
        return sb.toString();
    }
    
    private void assertArraysEqual(Object expArray, Object actArray, int actLen)
    {
        int expLen = Array.getLength(expArray);
        if (expLen != actLen) {
            fail("Expected number of entries "+expLen+", got "+actLen);
        }
        for (int i = 0; i < expLen; ++i) {
            Object e1 = Array.get(expArray, i);
            Object e2 = Array.get(actArray, i);
            if (!e1.equals(e2)) {
                fail("Elements at #"+i+" (len "+expLen+") differ: expected "+e1+", got "+e2);
            }
        }
    }

    private void verifyInts(String doc, int[] data, int blockLen)
        throws XMLStreamException
    {
        Random r = new Random(blockLen);
        int[] buffer = new int[Math.max(blockLen, 256+16)];
        int[] result = new int[data.length];
        int entries = 0;

        XMLStreamReader2 sr = getReader(doc);
        sr.next();
        assertTokenType(START_ELEMENT, sr.getEventType());

        while (true) {
            int readLen = (1 + (r.nextInt() & 0xFF));
            int offset = (r.nextInt() & 0xF);
            int got;

            try {
                got = sr.readElementAsIntArray(buffer, offset, readLen);
                if (got < 0) { 
                    break;
                }
            } catch (XMLStreamException xse) {
                fail("Did not expect a failure (readLen "+readLen+", offset "+offset+", total exp elems "+data.length+"), problem: "+xse.getMessage());
                got = 0; // never gets here, but compiler doesn't know
            }
            if ((entries + got) > result.length) {
                // Is that all, or would we get more?
                int total = entries+got;
                int more = sr.readElementAsIntArray(buffer, 0, 256);

                if (more > 0) {
                    fail("Expected only "+result.length+" entries, total now "+total+", plus "+more+" more with next call");
                } else {
                    fail("Expected only "+result.length+" entries, got "+total+" (and that's all)");
                }
            }
            System.arraycopy(buffer, offset, result, entries, got);
            entries += got;
        }
        assertArraysEqual(data, result, entries);
        sr.close();
    }

    private void verifyLongs(String doc, long[] data, int blockLen)
        throws XMLStreamException
    {
        Random r = (blockLen < 0) ? new Random(blockLen) : null;
        long[] buffer = new long[Math.max(blockLen, 256)];
        long[] result = new long[data.length];
        int entries = 0;

        XMLStreamReader2 sr = getReader(doc);
        sr.next();
        assertTokenType(START_ELEMENT, sr.getEventType());

        while (true) {
            int readLen = (r == null) ? blockLen : (1 + r.nextInt() & 0xFF);
            int got = sr.readElementAsLongArray(buffer, 0, readLen);
            if (got < 0) { 
                break;
            }
            if ((entries + got) > result.length) {
                fail("Expected only "+result.length+" entries, already got "+(entries+got));
            }
            System.arraycopy(buffer, 0, result, entries, got);
            entries += got;
        }
        assertArraysEqual(data, result, entries);
        sr.close();
    }

    private void verifyFloats(String doc, float[] data, int blockLen)
        throws XMLStreamException
    {
        Random r = (blockLen < 0) ? new Random(blockLen) : null;
        float[] buffer = new float[Math.max(blockLen, 256)];
        float[] result = new float[data.length];
        int entries = 0;

        XMLStreamReader2 sr = getReader(doc);
        sr.next();
        assertTokenType(START_ELEMENT, sr.getEventType());

        while (true) {
            int readLen = (r == null) ? blockLen : (1 + r.nextInt() & 0xFF);
            int got = sr.readElementAsFloatArray(buffer, 0, readLen);
            if (got < 0) { 
                break;
            }
            if ((entries + got) > result.length) {
                fail("Expected only "+result.length+" entries, already got "+(entries+got));
            }
            System.arraycopy(buffer, 0, result, entries, got);
            entries += got;
        }
        assertArraysEqual(data, result, entries);
        sr.close();
    }

    private void verifyDoubles(String doc, double[] data, int blockLen)
        throws XMLStreamException
    {
        Random r = (blockLen < 0) ? new Random(blockLen) : null;
        double[] buffer = new double[Math.max(blockLen, 256)];
        double[] result = new double[data.length];
        int entries = 0;

        XMLStreamReader2 sr = getReader(doc);
        sr.next();
        assertTokenType(START_ELEMENT, sr.getEventType());

        while (true) {
            int readLen = (r == null) ? blockLen : (1 + r.nextInt() & 0xFF);
            int got = sr.readElementAsDoubleArray(buffer, 0, readLen);
            if (got < 0) { 
                break;
            }
            if ((entries + got) > result.length) {
                fail("Expected only "+result.length+" entries, already got "+(entries+got));
            }
            System.arraycopy(buffer, 0, result, entries, got);
            entries += got;
        }
        assertArraysEqual(data, result, entries);
        sr.close();
    }

    private void verifyIntsAttr(String doc, int[] data)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getReader(doc);
        sr.next();
        assertTokenType(START_ELEMENT, sr.getEventType());
        int[] result = sr.getAttributeAsIntArray(0);
        assertArraysEqual(data, result, result.length);
        sr.close();
    }

    private void verifyLongsAttr(String doc, long[] data)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getReader(doc);
        sr.next();
        assertTokenType(START_ELEMENT, sr.getEventType());
        long[] result = sr.getAttributeAsLongArray(0);
        assertArraysEqual(data, result, result.length);
        sr.close();
    }

    private void verifyFloatsAttr(String doc, float[] data)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getReader(doc);
        sr.next();
        assertTokenType(START_ELEMENT, sr.getEventType());
        float[] result = sr.getAttributeAsFloatArray(0);
        assertArraysEqual(data, result, result.length);
        sr.close();
    }

    private void verifyDoublesAttr(String doc, double[] data)
        throws XMLStreamException
    {
        XMLStreamReader2 sr = getReader(doc);
        sr.next();
        assertTokenType(START_ELEMENT, sr.getEventType());
        double[] result = sr.getAttributeAsDoubleArray(0);
        assertArraysEqual(data, result, result.length);
        sr.close();
    }
}

