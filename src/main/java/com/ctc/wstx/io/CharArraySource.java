package com.ctc.wstx.io;

import javax.xml.stream.Location;

/**
 * Input source that reads input from a static char array, usually used
 * when expanding internal entities. It can also be used if the input
 * given is a raw character array.
 */
public final class CharArraySource
    extends BaseInputSource
{
    int mOffset;

    // // // Plus, location offset info:

    final Location mContentStart;
        
    protected CharArraySource(WstxInputSource parent, String fromEntity,
                    char[] chars, int offset, int len,
                    Location loc, SystemId sysId)
    {
        super(parent, fromEntity, loc.getPublicId(), sysId);
        //loc.getSystemId());
        mBuffer = chars;
        mOffset = offset;
        mInputLast = offset + len;
        mContentStart = loc;
    }

    /**
     * This is a hard-coded assumption, but yes, for now this source is
     * only created from internal entities.
     */
    public boolean fromInternalEntity() {
        return true;
    }

    /**
     * Unlike with reader source, we won't start from beginning of a file,
     * but usually from somewhere in the middle...
     */
    protected void doInitInputLocation(WstxInputData reader)
    {
        reader.mCurrInputProcessed = mContentStart.getCharacterOffset();
        reader.mCurrInputRow = mContentStart.getLineNumber();
        /* 13-Apr-2005, TSa: Since column offsets reported by Location
         *   objects are 1-based, but internally we use 0-based counts,
         *   need to offset this start by 1 to begin with.
         */
        reader.mCurrInputRowStart = -mContentStart.getColumnNumber() + 1;
    }

    public int readInto(WstxInputData reader)
    {
        /* Shouldn't really try to read after closing, but it may be easier
         * for caller not to have to keep track of open/close state...
         */
        if (mBuffer == null) {
            return -1;
        }

        /* In general, there are only 2 states; either this has been
         * read or not. Offset is used as the marker; plus, in case
         * somehow we get a dummy char source (length of 0), it'll
         * also prevent any reading.
         */
        int len = mInputLast - mOffset;
        if (len < 1) {
            return -1;
        }
        reader.mInputBuffer = mBuffer;
        reader.mInputPtr = mOffset;
        reader.mInputEnd = mInputLast;
        // Also, need to note the fact we're done
        mOffset = mInputLast;
        return len;
    }

    public boolean readMore(WstxInputData reader, int minAmount)
    {
        /* Only case where this may work is if we haven't yet been
         * read from at all. And that should mean caller also has no
         * existing input...
         */
        if (reader.mInputPtr >= reader.mInputEnd) {
            int len = mInputLast - mOffset;
            if (len >= minAmount) {
                return (readInto(reader) > 0);
            }
        }
        return false;
    }

    public void close()
    {
        /* Let's help GC a bit, in case there might be back references
         * to this Object from somewhere...
         */
        mBuffer = null;
    }

    public void closeCompletely() {
        close();
    }
}
