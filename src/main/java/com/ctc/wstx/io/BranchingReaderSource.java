package com.ctc.wstx.io;

import java.io.IOException;
import java.io.Reader;

import javax.xml.stream.XMLStreamException;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.util.TextBuffer;

/**
 * Specialized input source that can "branch" input it reads; essentially
 * both giving out read data AND also writing it out to a Writer.
 *<p>
 * Currently this Reader is only used as the main-level Reader, to allow for
 * branching of internal DTD subset to a text buffer if necessary.
 */
public final class BranchingReaderSource
    extends ReaderSource
{

    // // // Branching information

    TextBuffer mBranchBuffer = null;

    int mBranchStartOffset = 0;

    boolean mConvertLFs = false;

    /**
     * Flag that indicates that last char from previous buffer was
     * '\r', and that following '\n' (if there is one) needs to be
     * ignored.
     */
    boolean mGotCR = false;

    public BranchingReaderSource(ReaderConfig cfg, String pubId, SystemId sysId,
    		Reader r, boolean realClose)
    {
        /* null -> no parent,
         * null -> not from explicit entity (no id/name)
         */
        super(cfg, null, null, pubId, sysId, r, realClose);
    }

    public int readInto(WstxInputData reader)
        throws IOException, XMLStreamException
    {
        // Need to flush out branched content?
        if (mBranchBuffer != null) {
            if (mInputLast > mBranchStartOffset) {
                appendBranched(mBranchStartOffset, mInputLast);
            }
            mBranchStartOffset = 0;
        }
        return super.readInto(reader);
    }

    public boolean readMore(WstxInputData reader, int minAmount)
        throws IOException, XMLStreamException
    {
        // Existing data to output to branch?
        if (mBranchBuffer != null) {
            int ptr = reader.mInputPtr;
            int currAmount = mInputLast - ptr;
            if (currAmount > 0) {
                if (ptr > mBranchStartOffset) {
                    appendBranched(mBranchStartOffset, ptr);
                }
                mBranchStartOffset = 0;
            }
        }
        return super.readMore(reader, minAmount);
    }

    /*
    //////////////////////////////////////////////////
    // Branching methods; used mostly to make a copy
    // of parsed internal subsets.
    //////////////////////////////////////////////////
    */

    public void startBranch(TextBuffer tb, int startOffset,
                            boolean convertLFs)
    {
        mBranchBuffer = tb;
        mBranchStartOffset = startOffset;
        mConvertLFs = convertLFs;
        mGotCR = false;
    }

    /**
     * Currently this input source does not implement branching
     */
    public void endBranch(int endOffset)
    {
        if (mBranchBuffer != null) {
            if (endOffset > mBranchStartOffset) {
                appendBranched(mBranchStartOffset, endOffset);
            }
            // Let's also make sure no branching is done from this point on:
            mBranchBuffer = null;
        }
    }
    
    /*
    //////////////////////////////////////////////////
    // Internal methods
    //////////////////////////////////////////////////
    */

    private void appendBranched(int startOffset, int pastEnd) {
        // Main tricky thing here is just replacing of linefeeds...
        if (mConvertLFs) {
            char[] inBuf = mBuffer;
            /* this will also unshare() and ensure there's room for at
             * least one more char
             */
            char[] outBuf = mBranchBuffer.getCurrentSegment();
            int outPtr = mBranchBuffer.getCurrentSegmentSize();

            // Pending \n to skip?
            if (mGotCR) {
                if (inBuf[startOffset] == '\n') {
                    ++startOffset;
                }
            }

            while (startOffset < pastEnd) {
                char c = inBuf[startOffset++];
                if (c == '\r') {
                    if (startOffset < pastEnd) {
                        if (inBuf[startOffset] == '\n') {
                            ++startOffset;
                        }
                    } else {
                        mGotCR = true;
                    }
                    c = '\n';
                }

                // Ok, let's add char to output:
                outBuf[outPtr++] = c;
                    
                // Need more room?
                if (outPtr >= outBuf.length) {
                    outBuf = mBranchBuffer.finishCurrentSegment();
                    outPtr = 0;
                }
            }

            mBranchBuffer.setCurrentLength(outPtr);
        } else {
            mBranchBuffer.append(mBuffer, startOffset, pastEnd-startOffset);
        }
    }
}
