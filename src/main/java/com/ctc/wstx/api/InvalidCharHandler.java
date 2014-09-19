package com.ctc.wstx.api;

import java.io.IOException;

/**
 * Simple converter interface designed to be used with stream writer property
 * {@link WstxOutputProperties#P_OUTPUT_INVALID_CHAR_HANDLER}.
 * The idea is that it should be easy to have a way to convert invalid
 * characters such as Ascii control characters into something that
 * is legal to include in XML content. This only allows for simple
 * char-by-char replacements, instead of something more advanced such
 * as escaping. If escaping is needed, check out
 * {@link org.codehaus.stax2.XMLOutputFactory2#P_TEXT_ESCAPER} instead.
 *<p>
 * Note about exceptions: choice of only allowing throwing of
 * {@link IOException}s is due to the way Woodstox stream writer
 * backend works; <code>XmlWriter</code> can only throw IOExceptions.
 */
public interface InvalidCharHandler
{
    public char convertInvalidChar(int invalidChar) throws IOException;

    /**
     * This handler implementation just throws an exception for
     * all invalid characters encountered. It is the default handler
     * used if nothing else has been specified.
     */
    public static class FailingHandler
        implements InvalidCharHandler
    {
        public final static int SURR1_FIRST = 0xD800;
        public final static int SURR1_LAST = 0xDBFF;
        public final static int SURR2_FIRST = 0xDC00;
        public final static int SURR2_LAST = 0xDFFF;

        private final static FailingHandler sInstance = new FailingHandler();

        protected FailingHandler() { }

        public static FailingHandler getInstance() { return sInstance; }
        
        public char convertInvalidChar(int c) throws IOException
        {
            /* 17-May-2006, TSa: Would really be useful if we could throw
             *   XMLStreamExceptions; esp. to indicate actual output location.
             *   However, this causes problem with methods that call us and
             *   can only throw IOExceptions (when invoked via Writer proxy).
             *   Need to figure out how to resolve this.
             */
            if (c == 0) {
                throw new IOException("Invalid null character in text to output");
            }
            if (c < ' ' || (c >= 0x7F && c <= 0x9F)) {
                String msg = "Invalid white space character (0x"+Integer.toHexString(c)+") in text to output (in xml 1.1, could output as a character entity)";
                throw new IOException(msg);
            }
            if (c > 0x10FFFF) {
                throw new IOException("Illegal unicode character point (0x"+Integer.toHexString(c)+") to output; max is 0x10FFFF as per RFC 3629");
            }
            /* Surrogate pair in non-quotable (not text or attribute value)
             * content, and non-unicode encoding (ISO-8859-x, Ascii)?
             */
            if (c >= SURR1_FIRST && c <= SURR2_LAST) {
                throw new IOException("Illegal surrogate pair -- can only be output via character entities, which are not allowed in this content");
            }
            throw new IOException("Invalid XML character (0x"+Integer.toHexString(c)+") in text to output");
        }
    }

    /**
     * Alternative to the default handler, this handler converts all invalid
     * characters to the specified output character. That character will
     * not be further verified or modified by the stream writer.
     */
    public static class ReplacingHandler
        implements InvalidCharHandler
    {
        final char mReplacementChar;

        public ReplacingHandler(char c)
        {
            mReplacementChar = c;
        }

        public char convertInvalidChar(int c) throws IOException
        {
            return mReplacementChar;
        }
    }
}

