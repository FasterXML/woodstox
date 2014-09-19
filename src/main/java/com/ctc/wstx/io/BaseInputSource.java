package com.ctc.wstx.io;

import java.io.IOException;
import java.net.URL;

import javax.xml.stream.XMLStreamException;

/**
 * Abstract base class that implements shared functionality that all current
 * {@link WstxInputSource} implementations Woodstox includes need.
 */
public abstract class BaseInputSource
    extends WstxInputSource
{
    final String mPublicId;

    /**
     * URL for/from systemId points to original source of input, if known; null if not
     * known (source constructed with just a stream or reader). Used for
     * resolving references from the input that's read from this source.
     * Can be overridden by reader; done if P_BASE_URL is changed on
     * stream reader for which this input source is the currently
     * active input source.
     */
    SystemId mSystemId;
    
    /**
     * Input buffer this input source uses, if any.
     */
    protected char[] mBuffer;

    /**
     * Length of the buffer, if buffer used
     */
    protected int mInputLast;

    /*
    ////////////////////////////////////////////////////////////////
    // Saved location information; active information is directly
    // handled by Reader, and then saved to input source when switching
    // to a nested input source.
    ////////////////////////////////////////////////////////////////
    */

    /**
     * Number of characters read from the current input source prior to
     * the current buffer
     */
    long mSavedInputProcessed = 0;

    int mSavedInputRow = 1;
    int mSavedInputRowStart = 0;

    int mSavedInputPtr = 0;

    /*
    ////////////////////////////////////////////////////////////////
    // Some simple lazy-loading/reusing support...
    ////////////////////////////////////////////////////////////////
    */

    transient WstxInputLocation mParentLocation = null;

    /*
    ////////////////////////////////////////////////////////////////
    // Life-cycle
    ////////////////////////////////////////////////////////////////
    */
    protected BaseInputSource(WstxInputSource parent, String fromEntity,
                              String publicId, SystemId systemId)
    {
        super(parent, fromEntity);
        mSystemId = systemId;
        mPublicId = publicId;
    }

    @Override
    public void overrideSource(URL src)
    {
        //19-May-2014, tatu: I assume this should also override observed systemId...
        mSystemId = SystemId.construct(src);
    }

    @Override
    public abstract boolean fromInternalEntity();

    @Override
    public URL getSource() throws IOException {
        return (mSystemId == null) ? null : mSystemId.asURL();
    }

    @Override
    public String getPublicId() {
      return mPublicId;
    }

    @Override
    public String getSystemId() {
    	return (mSystemId == null) ? null : mSystemId.toString();
    }

    @Override
    protected abstract void doInitInputLocation(WstxInputData reader);

    @Override
    public abstract int readInto(WstxInputData reader)
        throws IOException, XMLStreamException;
    
    @Override
    public abstract boolean readMore(WstxInputData reader, int minAmount)
        throws IOException, XMLStreamException;

    @Override
    public void saveContext(WstxInputData reader)
    {
        // First actual input data
        mSavedInputPtr = reader.mInputPtr;

        // then location
        mSavedInputProcessed = reader.mCurrInputProcessed;
        mSavedInputRow = reader.mCurrInputRow;
        mSavedInputRowStart = reader.mCurrInputRowStart;
    }

    @Override
    public void restoreContext(WstxInputData reader)
    {
        reader.mInputBuffer = mBuffer;
        reader.mInputEnd = mInputLast;
        reader.mInputPtr = mSavedInputPtr;

        // then location
        reader.mCurrInputProcessed = mSavedInputProcessed;
        reader.mCurrInputRow = mSavedInputRow;
        reader.mCurrInputRowStart = mSavedInputRowStart;
    }

    @Override
    public abstract void close() throws IOException;

    /*
    //////////////////////////////////////////////////////////
    // Methods for accessing location information
    //////////////////////////////////////////////////////////
     */

    /**
     * This method only gets called by the 'child' input source (for example,
     * contents of an expanded entity), to get the enclosing context location.
     */
    @Override
    protected final WstxInputLocation getLocation()
    {
        // Note: columns are 1-based, need to add 1.
        return getLocation(mSavedInputProcessed + mSavedInputPtr - 1L,
                           mSavedInputRow,
                           mSavedInputPtr - mSavedInputRowStart + 1);
    }

    @Override
    public final WstxInputLocation getLocation(long total, int row, int col)
    {
        WstxInputLocation pl;

        if (mParent == null) {
            pl = null;
        } else {
            /* 13-Apr-2005, TSa: We can actually reuse parent location, since
             *   it can not change during lifetime of this child context...
             */
            pl = mParentLocation;
            if (pl == null) {
                mParentLocation = pl = mParent.getLocation();
            }
            pl = mParent.getLocation();
        }
        /* !!! 15-Apr-2005, TSa: This will cause overflow for total count,
         *   but since StAX 1.0 API doesn't have any way to deal with that,
         *   let's just let that be...
         */
        return new WstxInputLocation(pl, getPublicId(), getSystemId(),
                                     total, row, col);
    }
}
