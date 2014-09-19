package com.ctc.wstx.io;

import java.io.IOException;
import java.io.Reader;

import javax.xml.stream.XMLStreamException;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.exc.WstxException;

/**
 * Input source that reads input via a Reader.
 */
public class ReaderSource
    extends BaseInputSource
{
    final ReaderConfig mConfig;

    /**
     * Underlying Reader to read character data from
     */
    protected Reader mReader;

    /**
     * If true, will close the underlying Reader when this source is closed;
     * if false will leave it open.
     */
    final boolean mDoRealClose;

    int mInputProcessed = 0;
    int mInputRow = 1;
    int mInputRowStart = 0;

    public ReaderSource(ReaderConfig cfg, WstxInputSource parent, String fromEntity,
                        String pubId, SystemId sysId,
                        Reader r, boolean realClose)
    {
        super(parent, fromEntity, pubId, sysId);
        mConfig = cfg;
        mReader = r;
        mDoRealClose = realClose;
        int bufSize = cfg.getInputBufferLength();
        mBuffer = cfg.allocFullCBuffer(bufSize);
    }

    /**
     * Method called to change the default offsets this source has. Generally
     * done when the underlying Reader had been partially read earlier (like
     * reading the xml declaration before starting real parsing).
     */
    public void setInputOffsets(int proc, int row, int rowStart)
    {
        mInputProcessed = proc;
        mInputRow = row;
        mInputRowStart = rowStart;
    }

    /**
     * Input location is easy to set, as we'll start from the beginning
     * of a File.
     */
    protected void doInitInputLocation(WstxInputData reader)
    {
        reader.mCurrInputProcessed = mInputProcessed;
        reader.mCurrInputRow = mInputRow;
        reader.mCurrInputRowStart = mInputRowStart;
    }

    /**
     * This is a hard-coded assumption, for now this source is
     * only created from external entities
     */
    public boolean fromInternalEntity() {
        return false;
    }

    public int readInto(WstxInputData reader)
        throws IOException, XMLStreamException
    {
        /* Shouldn't really try to read after closing, but it may be easier
         * for caller not to have to keep track of closure...
         */
        if (mBuffer == null) {
            return -1;
        }
        int count = mReader.read(mBuffer, 0, mBuffer.length);
        if (count < 1) {
            /* Let's prevent caller from accidentally being able to access
             * data, first.
             */
            mInputLast = 0;
            reader.mInputPtr = 0;
            reader.mInputEnd = 0;
            if (count == 0) {
                /* Sanity check; should never happen with correctly written
                 * Readers:
                 */
                throw new WstxException("Reader (of type "+mReader.getClass().getName()+") returned 0 characters, even when asked to read up to "+mBuffer.length, getLocation());
            }
            return -1;
        }
        reader.mInputBuffer = mBuffer;
        reader.mInputPtr = 0;
        mInputLast = count;
        reader.mInputEnd = count;

        return count;
    }

    public boolean readMore(WstxInputData reader, int minAmount)
        throws IOException, XMLStreamException
    {
        /* Shouldn't really try to read after closing, but it may be easier
         * for caller not to have to keep track of closure...
         */
        if (mBuffer == null) {
            return false;
        }

        int ptr = reader.mInputPtr;
        int currAmount = mInputLast - ptr;

        // Let's first adjust caller's data appropriately:
        /* Since we are essentially removing 'ptr' chars that we
         * have used already, they count as past chars. Also, since
         * offsets are reduced by 'ptr', need to adjust linefeed offset
         * marker as well.
         */
        reader.mCurrInputProcessed += ptr;
        reader.mCurrInputRowStart -= ptr;

        // Existing data to move?
        if (currAmount > 0) {
            System.arraycopy(mBuffer, ptr, mBuffer, 0, currAmount);
            minAmount -= currAmount;
        }
        reader.mInputBuffer = mBuffer;
        reader.mInputPtr = 0;
        mInputLast = currAmount;

        while (minAmount > 0) {
            int amount = mBuffer.length - currAmount;
            int actual = mReader.read(mBuffer, currAmount, amount);
            if (actual < 1) {
                if (actual == 0) { // sanity check:
		    throw new WstxException("Reader (of type "+mReader.getClass().getName()+") returned 0 characters, even when asked to read up to "+amount, getLocation());
                }
                reader.mInputEnd = mInputLast = currAmount;
                return false;
            }
            currAmount += actual;
            minAmount -= actual;
        }
        reader.mInputEnd = mInputLast = currAmount;
        return true;
    }

    public void close()
        throws IOException
    {
        /* Buffer gets nullified by call to close() or closeCompletely(),
         * no need to call second time
         */
        if (mBuffer != null) { // so that it's ok to call multiple times
            closeAndRecycle(mDoRealClose);
        }
    }

    public void closeCompletely()
        throws IOException
    {
        /* Only need to call if the Reader is not yet null... since
         * buffer may have been cleaned by a call to close()
         */
        if (mReader != null) { // so that it's ok to call multiple times
            closeAndRecycle(true);
        }
    }

    private void closeAndRecycle(boolean fullClose)
        throws IOException
    {
        char[] buf = mBuffer;

        // Can we recycle buffers?
        if (buf != null) {
            mBuffer = null;
            mConfig.freeFullCBuffer(buf);
        }

        // How about Reader; close and/or recycle its buffers?
        if (mReader != null) {
            if (mReader instanceof BaseReader) {
                ((BaseReader) mReader).freeBuffers();
            }
            if (fullClose) {
                Reader r = mReader;
                mReader = null;
                r.close();
            }
        }
    }
}

