/* Woodstox XML processor
 *
 * Copyright (c) 2004- Tatu Saloranta, tatu.saloranta@iki.fi
 *
 * Licensed under the License specified in file LICENSE, included with
 * the source code.
 * You may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ctc.wstx.sw;

import java.io.*;

/**
 * This is a simple wrapper class, which decorates an {@link XmlWriter}
 * to look like a Writer. This is necessary to implement a (legacy)
 * character quoting system introduced for Woodstox 2.0, which relies
 * on having a Writer to use for outputting.
 */
public abstract class XmlWriterWrapper
    extends Writer
{
    protected final XmlWriter mWriter;

    private char[] mBuffer = null;

    public static XmlWriterWrapper wrapWriteRaw(XmlWriter xw)
    {
        return new RawWrapper(xw);
    }

    public static XmlWriterWrapper wrapWriteCharacters(XmlWriter xw)
    {
        return new TextWrapper(xw);
    }

    protected XmlWriterWrapper(XmlWriter writer)
    {
        mWriter = writer;
    }
    
    public final void close()
        throws IOException
    {
        mWriter.close(false);
    }

    public final void flush()
        throws IOException
    {
        mWriter.flush();
    }

    /* !!! 30-Nov-2006, TSa: Due to co-variance between Appendable and
     *    Writer, this would not compile with javac 1.5, in 1.4 mode
     *    (source and target set to "1.4". Not a huge deal, but since
     *    the base impl is just fine, no point in overriding it.
     */
    /*
    public final Writer append(char c)
        throws IOException
    {
        if (mBuffer == null) {
            mBuffer = new char[1];
        }
        mBuffer[0] = (char) c;
        write(mBuffer, 0, 1);
        return this;
    }
    */

    public final void write(char[] cbuf)
        throws IOException
    {
        write(cbuf, 0, cbuf.length);
    }

    public abstract void write(char[] cbuf, int off, int len)
        throws IOException;

    public final void write(int c)
        throws IOException
    {
        if (mBuffer == null) {
            mBuffer = new char[1];
        }
        mBuffer[0] = (char) c;
        write(mBuffer, 0, 1);
    }

    public abstract void write(String str)
        throws IOException;

    public abstract void write(String str, int off, int len) 
        throws IOException;

    /*
    //////////////////////////////////////////////////
    // Implementation classes
    //////////////////////////////////////////////////
     */

    /**
     * This wrapper directs calls to <code>writeRaw</code> methods. Thus,
     * it is a "vanilla" writer, and no escaping is done.
     */
    private final static class RawWrapper
        extends XmlWriterWrapper
    {
        protected RawWrapper(XmlWriter writer)
        {
            super(writer);
        }

        public void write(char[] cbuf, int off, int len)
            throws IOException
        {
            mWriter.writeRaw(cbuf, off, len);
        }

        public void write(String str, int off, int len) 
            throws IOException
        {
            mWriter.writeRaw(str, off, len);
        }

        public final void write(String str)
            throws IOException
        {
            mWriter.writeRaw(str, 0, str.length());
        }
    }

    /**
     * This wrapper directs calls to <code>writeCharacters</code> methods.
     * This means that text content escaping (and, possibly, validation)
     * is done, using default or custom escaping code.
     */
    private static class TextWrapper
        extends XmlWriterWrapper
    {
        protected TextWrapper(XmlWriter writer)
        {
            super(writer);
        }

        public void write(char[] cbuf, int off, int len)
            throws IOException
        {
            mWriter.writeCharacters(cbuf, off, len);
        }

        public void write(String str)
            throws IOException
        {
            mWriter.writeCharacters(str);
        }

        public void write(String str, int off, int len) 
            throws IOException
        {
            mWriter.writeCharacters(str.substring(off, off+len));
        }
    }
}

