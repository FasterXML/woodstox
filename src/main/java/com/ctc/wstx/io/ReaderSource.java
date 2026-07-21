package com.ctc.wstx.io;

import java.io.CharConversionException;
import java.io.IOException;
import java.io.Reader;

import javax.xml.stream.XMLStreamException;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.cfg.XmlConsts;
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

    /**
     * Whether characters read from {@link #mReader} need to be checked for
     * XML 1.1 "restricted characters" (the C1 control range U+007F&ndash;U+009F,
     * except U+0085 NEL) that are only legal when expressed via character
     * references.
     *<p>
     * This is only enabled when (a) the document is XML 1.1 and (b) the
     * underlying Reader is an application- (or JDK-) supplied one rather than
     * one of Woodstox's own decoding Readers ({@link BaseReader}), which already
     * perform this check while decoding bytes. Without this, the same logical
     * document is accepted or rejected depending solely on whether it was handed
     * to the parser as an {@code InputStream} or as a {@code Reader}.
     */
    boolean mCheckXml11Chars = false;

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
     * Method called to indicate the XML version this source is being read as.
     * For XML 1.1 we need to verify the "restricted characters" of the C1
     * control range unless the underlying Reader already does so while decoding
     * bytes (i.e. is one of Woodstox's own {@link BaseReader}s).
     */
    public void setXmlCompliancy(int xmlVersion)
    {
        mCheckXml11Chars = (xmlVersion == XmlConsts.XML_V_11)
            && !(mReader instanceof BaseReader);
    }

    /**
     * Input location is easy to set, as we'll start from the beginning
     * of a File.
     */
    @Override
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
    @Override
    public boolean fromInternalEntity() {
        return false;
    }

    @Override
    public int readInto(WstxInputData reader)
        throws IOException, XMLStreamException
    {
        // Shouldn't really try to read after closing, but it may be easier
        // for caller not to have to keep track of closure...
        if (mBuffer == null) {
            return -1;
        }
        int count = mReader.read(mBuffer, 0, mBuffer.length);
        if (count < 1) {
            // Let's prevent caller from accidentally being able to access
            // data, first.
            mInputLast = 0;
            reader.mInputPtr = 0;
            reader.mInputEnd = 0;
            if (count == 0) {
                // Sanity check; should never happen with correctly written
                // Readers:
                throw new WstxException("Reader (of type "+mReader.getClass().getName()+") returned 0 characters, even when asked to read up to "+mBuffer.length, getLocation());
            }
            return -1;
        }
        if (mCheckXml11Chars) {
            verifyXml11Chars(mBuffer, 0, count, reader.mCurrInputProcessed);
        }
        reader.mInputBuffer = mBuffer;
        reader.mInputPtr = 0;
        mInputLast = count;
        reader.mInputEnd = count;

        return count;
    }

    @Override
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
            if (mCheckXml11Chars) {
                verifyXml11Chars(mBuffer, currAmount, currAmount + actual, reader.mCurrInputProcessed);
            }
            currAmount += actual;
            minAmount -= actual;
        }
        reader.mInputEnd = mInputLast = currAmount;
        return true;
    }

    /**
     * Verifies that the given character range does not contain XML 1.1
     * "restricted characters" from the C1 control range (U+007F&ndash;U+009F,
     * excluding U+0085 NEL) that are only legal when written as character
     * references. The C0 control range (below U+0020) is left to the tokenizer,
     * which already rejects those regardless of input source.
     *<p>
     * Mirrors the check performed by {@link UTF8Reader} (and the other
     * {@link BaseReader} subclasses) for {@code InputStream}-based input, so
     * that {@code Reader}-based input is validated identically.
     *
     * @param baseProcessed Number of characters processed before
     *   {@code buf[0]} (i.e. {@code reader.mCurrInputProcessed}); used so the
     *   reported character position is the absolute document offset rather than
     *   a buffer-local one.
     */
    private void verifyXml11Chars(char[] buf, int start, int end, long baseProcessed)
        throws IOException
    {
        for (int i = start; i < end; ++i) {
            char c = buf[i];
            if (c >= 0x7F && c <= 0x9F && c != 0x85) {
                reportInvalidXml11(c, baseProcessed + i);
            }
        }
    }

    private void reportInvalidXml11(int value, long charPos)
        throws IOException
    {
        // Matches BaseReader.reportInvalidXml11 so behavior is the same whether
        // the document was supplied as an InputStream or as a Reader.
        throw new CharConversionException("Invalid character 0x"
                +Integer.toHexString(value)
                +", can only be included in xml 1.1 using character entities (at char #"+charPos+")");
    }

    @Override
    public void close() throws IOException
    {
        /* Buffer gets nullified by call to close() or closeCompletely(),
         * no need to call second time
         */
        if (mBuffer != null) { // so that it's ok to call multiple times
            closeAndRecycle(mDoRealClose);
        }
    }

    @Override
    public void closeCompletely() throws IOException
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

