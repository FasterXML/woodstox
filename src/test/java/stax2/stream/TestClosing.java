package stax2.stream;

import java.io.*;
import javax.xml.stream.*;
import javax.xml.transform.stream.StreamSource;

import org.codehaus.stax2.*;
import org.codehaus.stax2.io.Stax2StringSource;

import stax2.BaseStax2Test;

/**
 * This unit test suite verifies that the auto-closing feature works
 * as expected (both explicitly, and via Source object being passed).
 *
 * @author Tatu Saloranta
 *
 * @since 3.0
 */
public class TestClosing
    extends BaseStax2Test
{
    /**
     * This unit test checks the default behaviour; with no auto-close, no
     * automatic closing should occur, nor explicit one unless specific
     * forcing method is used.
     */
    public void testNoAutoCloseReader()
        throws XMLStreamException
    {
        final String XML = "<root>...</root>";

        XMLInputFactory2 f = getFactory(false);
        MyReader input = new MyReader(XML);
        XMLStreamReader2 sr = (XMLStreamReader2) f.createXMLStreamReader(input);
        // shouldn't be closed to begin with...
        assertFalse(input.isClosed());
        assertTokenType(START_ELEMENT, sr.next());
        assertFalse(input.isClosed());

        // nor closed half-way through with basic close()
        sr.close();
        assertFalse(input.isClosed());

        // ok, let's finish it up:
        streamThrough(sr);
        // still not closed
        assertFalse(input.isClosed());

        // except when forced to:
        sr.closeCompletely();
        assertTrue(input.isClosed());

        // ... and should be ok to call it multiple times:
        sr.closeCompletely();
        sr.closeCompletely();
        assertTrue(input.isClosed());
    }

    public void testNoAutoCloseStream()
        throws XMLStreamException, IOException
    {
        final String XML = "<root>...</root>";

        XMLInputFactory2 f = getFactory(false);
        MyStream input = new MyStream(XML.getBytes("UTF-8"));
        XMLStreamReader2 sr = (XMLStreamReader2) f.createXMLStreamReader(input);
        // shouldn't be closed to begin with...
        assertFalse(input.isClosed());
        assertTokenType(START_ELEMENT, sr.next());
        assertFalse(input.isClosed());

        // nor closed half-way through with basic close()
        sr.close();
        assertFalse(input.isClosed());

        // ok, let's finish it up:
        streamThrough(sr);
        // still not closed
        assertFalse(input.isClosed());

        // except when forced to:
        sr.closeCompletely();
        assertTrue(input.isClosed());

        // ... and should be ok to call it multiple times:
        sr.closeCompletely();
        assertTrue(input.isClosed());
    }

    /**
     * This unit test checks that when auto-closing option is set, the
     * passed in input stream does get properly closed both when EOF
     * is hit, and when we call close() prior to EOF.
     */
    public void testAutoCloseEnabled()
        throws XMLStreamException
    {
        final String XML = "<root>...</root>";

        // First, explicit close:
        XMLInputFactory2 f = getFactory(true);
        MyReader input = new MyReader(XML);
        XMLStreamReader2 sr = (XMLStreamReader2) f.createXMLStreamReader(input);
        assertFalse(input.isClosed());
        assertTokenType(START_ELEMENT, sr.next());
        assertFalse(input.isClosed());
        sr.close();
        assertTrue(input.isClosed());
        // also, let's verify we can call more than once:
        sr.close();
        sr.close();
        assertTrue(input.isClosed());

        // Then implicit close (real auto-close):
        input = new MyReader(XML);
        sr = (XMLStreamReader2) f.createXMLStreamReader(input);
        assertFalse(input.isClosed());
        streamThrough(sr);
        assertTrue(input.isClosed());

        // And then similarly for Source abstraction for streams
        MySource src = MySource.createFor(XML);
        sr = (XMLStreamReader2) f.createXMLStreamReader(src);
        assertFalse(src.isClosed());
        assertTokenType(START_ELEMENT, sr.next());
        streamThrough(sr);
        assertTrue(input.isClosed());
    }

    /**
     * This unit test checks what happens when we use Result abstraction
     * for passing in result stream/writer. Their handling differs depending
     * on whether caller is considered to have access to the underlying
     * physical object or not.
     */
    public void testAutoCloseImplicit()
        throws XMLStreamException
    {
        final String XML = "<root>...</root>";

        // Factory with auto-close disabled:
        XMLInputFactory2 f = getFactory(false);

        /* Ok, first: with regular (InputStream, Reader) streams no auto-closing
         * because caller does have access: StreamSource retains given
         * stream/reader as is.
         */
        MySource input = MySource.createFor(XML);
        XMLStreamReader2 sr = (XMLStreamReader2) f.createXMLStreamReader(input);

        assertFalse(input.isClosed());
        assertTokenType(START_ELEMENT, sr.next());
        assertFalse(input.isClosed());
        sr.close();
        assertFalse(input.isClosed());
        // also, let's verify we can call more than once:
        sr.close();
        sr.close();
        assertFalse(input.isClosed());

        /* And then more interesting case; verifying that Stax2Source
         * sub-classes are implicitly auto-closed: they need to be, because
         * they do not (necessarily) expose underlying physical stream.
         * We can test this by using any Stax2Source impl.
         */
        MyStringSource src = new MyStringSource(XML);
        sr = (XMLStreamReader2) f.createXMLStreamReader(src);

        assertFalse(src.isClosed());
        assertTokenType(START_ELEMENT, sr.next());
        assertFalse(src.isClosed());
        streamThrough(sr);
        assertTrue(src.isClosed());

        // similarly, let's verify plain old close would do it
        src = new MyStringSource(XML);
        sr = (XMLStreamReader2) f.createXMLStreamReader(src);
        assertFalse(src.isClosed());
        assertTokenType(START_ELEMENT, sr.next());
        assertFalse(src.isClosed());
        sr.close();
        assertTrue(src.isClosed());
    }
    
    /*
    ////////////////////////////////////////
    // Non-test methods
    ////////////////////////////////////////
     */

    XMLInputFactory2 getFactory(boolean autoClose)
    {
        XMLInputFactory2 f = getInputFactory();
        f.setProperty(XMLInputFactory2.P_AUTO_CLOSE_INPUT,
                      Boolean.valueOf(autoClose));
        return f;
    }

    /*
    ////////////////////////////////////////
    // Helper mock classes
    ////////////////////////////////////////
     */

    final static class MyReader extends StringReader
    {
        boolean mIsClosed = false;

        public MyReader(String contents) {
            super(contents);
        }

        public void close() {
            mIsClosed = true;
            super.close();
        }

        public boolean isClosed() { return mIsClosed; }
    }

    final static class MyStream extends ByteArrayInputStream
    {
        boolean mIsClosed = false;

        public MyStream(byte[] data) {
            super(data);
        }

        public void close() throws IOException {
            mIsClosed = true;
            super.close();
        }

        public boolean isClosed() { return mIsClosed; }
    }

    final static class MySource
        extends StreamSource
    {
        final MyReader mReader;

        private MySource(MyReader reader) {
            super(reader);
            mReader = reader;
        }

        public static MySource createFor(String content) {
            MyReader r = new MyReader(content);
            return new MySource(r);
        }

        public boolean isClosed() {
            return mReader.isClosed();
        }

        public Reader getReader() {
            return mReader;
        }
    }

    private final static class MyStringSource
        extends Stax2StringSource
    {
        MyReader mReader;

        public MyStringSource(String s) { super(s); }

        public Reader constructReader() {
            mReader = new MyReader(getText());
            return mReader;
        }

        public boolean isClosed() { return mReader.isClosed(); }
    }
}
