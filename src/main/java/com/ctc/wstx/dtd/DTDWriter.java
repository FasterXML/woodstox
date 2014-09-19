package com.ctc.wstx.dtd;

import java.io.IOException;
import java.io.Writer;

import javax.xml.stream.XMLStreamException;

import com.ctc.wstx.exc.WstxIOException;

/**
 * Simple utility class used by {@link DTDReader} when writing out
 * flattened external DTD subset file. Writing functionality encapsulated
 * here since it's specific to one mode of operation (flattening).
 *<p>
 * Note, too, that underlying {@link IOException}s are generally wrapped
 * as {@link XMLStreamException}s. This is needed to reduce amount of
 * work caller has to do for wrapping. It will still be possible to
 * unwrap these exceptions further up the call stack if need be.
 */
final class DTDWriter
{
    /*
    //////////////////////////////////////////////////
    // Configuration
    //////////////////////////////////////////////////
     */

    final Writer mWriter;

    final boolean mIncludeComments;

    final boolean mIncludeConditionals;

    final boolean mIncludePEs;

    /*
    //////////////////////////////////////////////////
    // Output status
    //////////////////////////////////////////////////
     */

    /**
     * Counter that indicates whether flattened output should be written to
     * (non-null) mWriter; values above zero indicate output is enabled,
     * zero and below that output is disabled.
     * Only enabled if mWriter is not
     * null; will be temporarily disabled during processing of content
     * that is not to be included (PE reference; or comments / conditional
     * sections if comment/cs output is suppressed)
     */
    int mIsFlattening = 0;

    /**
     * Pointer to first character in the current input buffer that
     * has not yet been written to flatten writer.
     */
    int mFlattenStart = 0;

    /*
    //////////////////////////////////////////////////
    // Life-cycle
    //////////////////////////////////////////////////
     */

    public DTDWriter(Writer out, boolean inclComments, boolean inclCond,
                     boolean inclPEs)
    {
        mWriter = out;
        mIncludeComments = inclComments;
        mIncludeConditionals = inclCond;
        mIncludePEs = inclPEs;

        mIsFlattening = 1; // starts enabled
    }

    /*
    //////////////////////////////////////////////////
    // Public API, accessors, state change
    //////////////////////////////////////////////////
     */

    public boolean includeComments() {
        return mIncludeComments;
    }

    public boolean includeConditionals() {
        return mIncludeConditionals;
    }

    public boolean includeParamEntities() {
        return mIncludePEs;
    }

    public void disableOutput()
    {
        --mIsFlattening;
    }

    public void enableOutput(int newStart)
    {
        ++mIsFlattening;
        mFlattenStart = newStart;
    }

    public void setFlattenStart(int ptr) {
        mFlattenStart = ptr;
    }

    public int getFlattenStart() {
        return mFlattenStart;
    }
    
    /*
    //////////////////////////////////////////////////
    // Public API, output methods:
    //////////////////////////////////////////////////
     */

    public void flush(char[] buf, int upUntil)
        throws XMLStreamException
    {
        if (mFlattenStart < upUntil) {
            if (mIsFlattening > 0) {
                try {
                    mWriter.write(buf, mFlattenStart, upUntil - mFlattenStart);
                } catch (IOException ioe) {
                    throw new WstxIOException(ioe);
                }
            }
            mFlattenStart = upUntil;
        }
    }

    /**
     * Method called when explicit output has to be done for flatten output:
     * this is usually done when there's need to do speculative checks
     * before it's known if some chars are output (when suppressing comments
     * or conditional sections)
     */
    public void output(String output)
        throws XMLStreamException
    {
        if (mIsFlattening > 0) {
            try {
                mWriter.write(output);
            } catch (IOException ioe) {
                throw new WstxIOException(ioe);
            }
        }
    }

    public void output(char c)
        throws XMLStreamException
    {
        if (mIsFlattening > 0) {
            try {
                mWriter.write(c);
            } catch (IOException ioe) {
                throw new WstxIOException(ioe);
            }
        }
    }
}

