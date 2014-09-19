package com.ctc.wstx.util;

import java.io.*;
import java.util.ArrayList;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

import org.codehaus.stax2.typed.Base64Variant;
import org.codehaus.stax2.typed.TypedArrayDecoder;
import org.codehaus.stax2.typed.TypedValueDecoder;
import org.codehaus.stax2.typed.TypedXMLStreamException;
import org.codehaus.stax2.validation.XMLValidator;

import org.codehaus.stax2.ri.typed.CharArrayBase64Decoder;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.dtd.DTDEventListener;
import com.ctc.wstx.sr.InputProblemReporter;
import com.ctc.wstx.util.StringUtil;

/**
 * TextBuffer is a class similar to {@link StringBuilder}, with
 * following differences:
 *<ul>
 *  <li>TextBuffer uses segments character arrays, to avoid having
 *     to do additional array copies when array is not big enough. This
 *     means that only reallocating that is necessary is done only once --
 *     if and when caller
 *     wants to access contents in a linear array (char[], String).
 *    </li>
 *  <li>TextBuffer is not synchronized.
 *    </li>
 * </ul>
 *<p>
 * Over time more and more cruft has accumulated here, mostly to
 * support efficient access to collected text. Since access is
 * easiest to do efficiently using callbacks, this class now needs
 * to known interfaces of SAX classes and validators.
 *<p>
 * Notes about usage: for debugging purposes, it's suggested to use 
 * {@link #toString} method, as opposed to
 * {@link #contentsAsArray} or {@link #contentsAsString}. Internally
 * resulting code paths may or may not be different, WRT caching.
 *
 * @author Tatu Saloranta
 */
public final class TextBuffer
{
    /* 23-Mar-2006, TSa: Memory buffer clearing is a significant overhead
     *    for small documents, no need to use huge buffer -- it will expand
     *    as necessary for larger docs, but commonly text segments just
     *    aren't that long.
     */
    /**
     * Size of the first text segment buffer to allocate; need not contain
     * the biggest segment, since new ones will get allocated as needed.
     * However, it's sensible to use something that often is big enough
     * to contain segments.
     */
    final static int DEF_INITIAL_BUFFER_SIZE = 500; // 1k

    /**
     * We will also restrict maximum length of individual segments
     * to allocate (not including cases where we must return a single
     * segment). Value is somewhat arbitrary, let's use it so that
     * memory used is no more than 1/2 megabytes.
     */
    final static int MAX_SEGMENT_LENGTH = 256 * 1024;

    final static int INT_SPACE = 0x0020;

    // // // Configuration:

    private final ReaderConfig mConfig;

    // // // Shared read-only input buffer:

    /**
     * Shared input buffer; stored here in case some input can be returned
     * as is, without being copied to collector's own buffers. Note that
     * this is read-only for this Objet.
     */
    private char[] mInputBuffer;

    /**
     * Character offset of first char in input buffer; -1 to indicate
     * that input buffer currently does not contain any useful char data
     */
    private int mInputStart;

    /**
     * When using shared buffer, offset after the last character in
     * shared buffer
     */
    private int mInputLen;

    // // // Internal non-shared collector buffers:

    private boolean mHasSegments = false;

    /**
     * List of segments prior to currently active segment.
     */
    private ArrayList<char[]> mSegments;


    // // // Currently used segment; not (yet) contained in mSegments

    /**
     * Amount of characters in segments in {@link mSegments}
     */
    private int mSegmentSize;

    private char[] mCurrentSegment;

    /**
     * Number of characters in currently active (last) segment
     */
    private int mCurrentSize;

    // // // Temporary caching for Objects to return

    /**
     * String that will be constructed when the whole contents are
     * needed; will be temporarily stored in case asked for again.
     */
    private String mResultString;

    private char[] mResultArray;

    // // // Canonical indentation objects (up to 32 spaces, 8 tabs)

    public final static int MAX_INDENT_SPACES = 32;
    public final static int MAX_INDENT_TABS = 8;

    // Let's add one more space at the end, for safety...
    private final static String sIndSpaces =
        // 123456789012345678901234567890123
        "\n                                 ";
    private final static char[] sIndSpacesArray = sIndSpaces.toCharArray();
    private final static String[] sIndSpacesStrings = new String[sIndSpacesArray.length];

    private final static String sIndTabs =
        // 1 2 3 4 5 6 7 8 9
        "\n\t\t\t\t\t\t\t\t\t";
    private final static char[] sIndTabsArray = sIndTabs.toCharArray();
    private final static String[] sIndTabsStrings = new String[sIndTabsArray.length];

    /*
    //////////////////////////////////////////////
    // Life-cycle
    //////////////////////////////////////////////
     */

    private TextBuffer(ReaderConfig cfg)
    {
        mConfig = cfg;
    }

    public static TextBuffer createRecyclableBuffer(ReaderConfig cfg)
    {
        return new TextBuffer(cfg);
    }

    public static TextBuffer createTemporaryBuffer()
    {
        return new TextBuffer(null);
    }

    /**
     * Method called to indicate that the underlying buffers should now
     * be recycled if they haven't yet been recycled. Although caller
     * can still use this text buffer, it is not advisable to call this
     * method if that is likely, since next time a buffer is needed,
     * buffers need to reallocated.
     * Note: calling this method automatically also clears contents
     * of the buffer.
     */
    public void recycle(boolean force)
    {
        if (mConfig != null && mCurrentSegment != null) {
            if (force) {
                /* If we are allowed to wipe out all existing data, it's
                 * quite easy; we'll just wipe out contents, and return
                 * biggest buffer:
                 */
                resetWithEmpty();
            } else {
                /* But if there's non-shared data (ie. buffer is still
                 * in use), can't return it yet:
                 */
                if (mInputStart < 0 && (mSegmentSize + mCurrentSize) > 0) {
                    return;
                }
                // If no data (or only shared data), can continue
                if (mSegments != null && mSegments.size() > 0) {
                    // No need to use anything from list, curr segment not null
                    mSegments.clear();
                    mSegmentSize = 0;
                }
            }

            char[] buf = mCurrentSegment;
            mCurrentSegment = null;
            mConfig.freeMediumCBuffer(buf);
        }
    }

    /**
     * Method called to clear out any content text buffer may have, and
     * initializes buffer to use non-shared data.
     */
    public void resetWithEmpty()
    {
        mInputBuffer = null;
        mInputStart = -1; // indicates shared buffer not used
        mInputLen = 0;

        mResultString = null;
        mResultArray = null;

        // And then reset internal input buffers, if necessary:
        if (mHasSegments) {
            clearSegments();
        }
        mCurrentSize = 0;
    }

    /**
     * Similar to {@link #resetWithEmpty}, but actively marks current
     * text content to be empty string (whereas former method leaves
     * content as undefined).
     */
    public void resetWithEmptyString()
    {
        mInputBuffer = null;
        mInputStart = -1; // indicates shared buffer not used
        mInputLen = 0;
        mResultString = "";
        mResultArray = null;
        if (mHasSegments) {
            clearSegments();
        }
        mCurrentSize = 0;
    }

    /**
     * Method called to initialize the buffer with a shared copy of data;
     * this means that buffer will just have pointers to actual data. It
     * also means that if anything is to be appended to the buffer, it
     * will first have to unshare it (make a local copy).
     */
    public void resetWithShared(char[] buf, int start, int len)
    {
        // Let's first mark things we need about input buffer
        mInputBuffer = buf;
        mInputStart = start;
        mInputLen = len;

        // Then clear intermediate values, if any:
        mResultString = null;
        mResultArray = null;

        // And then reset internal input buffers, if necessary:
        if (mHasSegments) {
            clearSegments();
        }
    }

    public void resetWithCopy(char[] buf, int start, int len)
    {
        mInputBuffer = null;
        mInputStart = -1; // indicates shared buffer not used
        mInputLen = 0;

        mResultString = null;
        mResultArray = null;

        // And then reset internal input buffers, if necessary:
        if (mHasSegments) {
            clearSegments();
        } else {
            if (mCurrentSegment == null) {
                mCurrentSegment = allocBuffer(len);
            }
            mCurrentSize = mSegmentSize = 0;
        }
        append(buf, start, len);
    }

    /**
     * Method called to make sure there is a non-shared segment to use, without
     * appending any content yet.
     */
    public void resetInitialized()
    {
        resetWithEmpty();
        if (mCurrentSegment == null) {
            mCurrentSegment = allocBuffer(0);
        }
    }

    private final char[] allocBuffer(int needed)
    {
        int size = Math.max(needed, DEF_INITIAL_BUFFER_SIZE);
        char[] buf = null;
        if (mConfig != null) {
            buf = mConfig.allocMediumCBuffer(size);
            if (buf != null) {
                return buf;
            }
        }
        return new char[size];
    }

    private final void clearSegments()
    {
        mHasSegments = false;
        /* Since the current segment should be the biggest one
         * (as we allocate 50% bigger each time), let's retain it,
         * and clear others
         */
        mSegments.clear();
        mCurrentSize = mSegmentSize = 0;
    }

    public void resetWithIndentation(int indCharCount, char indChar)
    {
        mInputStart = 0;
        mInputLen = indCharCount+1;
        String text;
        if (indChar == '\t') { // tabs?
            mInputBuffer = sIndTabsArray;
            text = sIndTabsStrings[indCharCount];
            if (text == null) {
                sIndTabsStrings[indCharCount] = text = sIndTabs.substring(0, mInputLen);
            }
        } else { // nope, spaces (should assert indChar?)
            mInputBuffer = sIndSpacesArray;
            text = sIndSpacesStrings[indCharCount];
            if (text == null) {
                sIndSpacesStrings[indCharCount] = text = sIndSpaces.substring(0, mInputLen);
            }
        }
        mResultString = text;

        /* Should not need the explicit non-shared array; no point in
         * pre-populating it (can be changed if this is not true)
         */
        mResultArray = null;

        // And then reset internal input buffers, if necessary:
        if (mSegments != null && mSegments.size() > 0) {
            mSegments.clear();
            mCurrentSize = mSegmentSize = 0;
        }
    }

    /*
    //////////////////////////////////////////////
    // Accessors for implementing StAX interface:
    //////////////////////////////////////////////
     */

    /**
     * @return Number of characters currently stored by this collector
     */
    public int size() {
        if (mInputStart >= 0) { // shared copy from input buf
            return mInputLen;
        }
        // local segmented buffers
        return mSegmentSize + mCurrentSize;
    }

    public int getTextStart()
    {
        /* Only shared input buffer can have non-zero offset; buffer
         * segments start at 0, and if we have to create a combo buffer,
         * that too will start from beginning of the buffer
         */
        return (mInputStart >= 0) ? mInputStart : 0;
    }

    public char[] getTextBuffer()
    {
        // Are we just using shared input buffer?
        if (mInputStart >= 0) {
            return mInputBuffer;
        }
        // Nope; but does it fit in just one segment?
        if (mSegments == null || mSegments.size() == 0) {
            return mCurrentSegment;
        }
        // Nope, need to have/create a non-segmented array and return it
        return contentsAsArray();
    }

    /*
    /////////////////////////////////////////////////
    // Accessors for implementing StAX2 Typed access
    /////////////////////////////////////////////////
     */

    /**
     * Generic pass-through method which call given decoder
     * with accumulated data
     */
    public void decode(TypedValueDecoder tvd)
        throws IllegalArgumentException
    {
        char[] buf;
        int start, end;

        if (mInputStart >= 0) { // shared buffer, common case
            buf = mInputBuffer;
            start = mInputStart;
            end = start + mInputLen;
        } else {
            buf = getTextBuffer();
            start = 0;
            end = mSegmentSize + mCurrentSize;
        }

        // Need to trim first
        while (true) {
            if (start >= end) {
                tvd.handleEmptyValue();
                return;
            }
            if (!StringUtil.isSpace(buf[start])) {
                break;
            }
            ++start;
        }
        // Trailing space?
        while (--end > start && StringUtil.isSpace(buf[end])) { }
        tvd.decode(buf, start, end+1);
    }

    /**
     * Pass-through decode method called to find find the next token,
     * decode it, and repeat the process as long as there are more
     * tokens and the array decoder accepts more entries.
     * All tokens processed will be "consumed", such that they will
     * not be visible via buffer.
     *
     * @return Number of tokens decoded; 0 means that no (more) tokens
     *    were found from this buffer.
     */
    public int decodeElements(TypedArrayDecoder tad, InputProblemReporter rep)
        throws TypedXMLStreamException
    {
        int count = 0;

        /* First: for simplicity, we require a single flat buffer to
         * decode from. Second: to be able to update start location
         * (to keep track of what's available), we need to fake that
         * we are using a shared buffer (since that has offset)
         */
        if (mInputStart < 0) {
            if (mHasSegments) {
                mInputBuffer = buildResultArray();
                mInputLen = mInputBuffer.length;
                // let's also clear segments since they are not needed any more
                clearSegments();
            } else {
                // just current buffer, easier to fake
                mInputBuffer = mCurrentSegment;
                mInputLen = mCurrentSize;
            }
            mInputStart = 0;
        }

        // And then let's decode
        int ptr = mInputStart;
        final int end = ptr + mInputLen;
        final char[] buf = mInputBuffer;
        int start = ptr;

        try {
            decode_loop:
            while (ptr < end) {
                // First, any space to skip?
                while (buf[ptr] <= INT_SPACE) {
                    if (++ptr >= end) {
                        break decode_loop;
                    }
                }
                // Then let's figure out non-space char (token)
                start = ptr;
                ++ptr;
                while (ptr < end && buf[ptr] > INT_SPACE) {
                    ++ptr;
                }
                ++count;
                int tokenEnd = ptr;
                ++ptr; // to skip trailing space (or, beyond end)
                // And there we have it
                if (tad.decodeValue(buf, start, tokenEnd)) {
                    break;
                }
            }
        } catch (IllegalArgumentException iae) {
            // Need to convert to a checked stream exception

            /* Hmmh. This is probably not an accurate location... but
             * we can't do much better as content we have has been
             * normalized already.
             */
            Location loc = rep.getLocation();
            // -1 to move it back after being advanced earlier (to skip trailing space)
            String lexical = new String(buf, start, (ptr-start-1));
            throw new TypedXMLStreamException(lexical, iae.getMessage(), loc, iae);
        } finally {
            mInputStart = ptr;
            mInputLen = end-ptr;
        }
        return count;
    }

    /**
     * Method that needs to be called to configure given base64 decoder
     * with textual contents collected by this buffer.
     *
     * @param dec Decoder that will need data
     * @param firstChunk Whether this is the first segment fed or not;
     *    if it is, state needs to be fullt reset; if not, only partially.
     */
    public void initBinaryChunks(Base64Variant v, CharArrayBase64Decoder dec, boolean firstChunk)
    {
        if (mInputStart < 0) { // non-shared
            dec.init(v, firstChunk, mCurrentSegment, 0, mCurrentSize, mSegments);
        } else { // shared
            dec.init(v, firstChunk, mInputBuffer, mInputStart, mInputLen, null);
        }
    }

    /*
    //////////////////////////////////////////////
    // Accessors:
    //////////////////////////////////////////////
     */

    public String contentsAsString()
    {
        if (mResultString == null) {
            // Has array been requested? Can make a shortcut, if so:
            if (mResultArray != null) {
                mResultString = new String(mResultArray);
            } else {
                // Do we use shared array?
                if (mInputStart >= 0) {
                    if (mInputLen < 1) {
                        return (mResultString = "");
                    }
                    mResultString = new String(mInputBuffer, mInputStart, mInputLen);
                } else { // nope... need to copy
                    // But first, let's see if we have just one buffer
                    int segLen = mSegmentSize;
                    int currLen = mCurrentSize;
                    
                    if (segLen == 0) { // yup
                        mResultString = (currLen == 0) ? "" : new String(mCurrentSegment, 0, currLen);
                    } else { // no, need to combine
                        StringBuilder sb = new StringBuilder(segLen + currLen);
                        // First stored segments
                        if (mSegments != null) {
                            for (int i = 0, len = mSegments.size(); i < len; ++i) {
                                char[] curr = mSegments.get(i);
                                sb.append(curr, 0, curr.length);
                            }
                        }
                        // And finally, current segment:
                        sb.append(mCurrentSegment, 0, mCurrentSize);
                        mResultString = sb.toString();
                    }
                }
            }
        }
        return mResultString;
    }

    /**
     * Similar to {@link #contentsAsString}, but constructs a StringBuilder
     * for further appends.
     *
     * @param extraSpace Number of extra characters to preserve in StringBuilder
     *   beyond space immediately needed to hold the contents
     */
    public StringBuilder contentsAsStringBuilder(int extraSpace)
    {
        if (mResultString != null) {
            return new StringBuilder(mResultString);
        }
        if (mResultArray != null) {
            StringBuilder sb = new StringBuilder(mResultArray.length + extraSpace);
            sb.append(mResultArray, 0, mResultArray.length);
            return sb;
        }
        if (mInputStart >= 0) { // shared array
            if (mInputLen < 1) {
                return new StringBuilder();
            }
            StringBuilder sb = new StringBuilder(mInputLen + extraSpace);
            sb.append(mInputBuffer, mInputStart, mInputLen);
            return sb;
        }
        int segLen = mSegmentSize;
        int currLen = mCurrentSize;

        StringBuilder sb = new StringBuilder(segLen + currLen + extraSpace);
        // First stored segments
        if (mSegments != null) {
            for (int i = 0, len = mSegments.size(); i < len; ++i) {
                char[] curr = mSegments.get(i);
                sb.append(curr, 0, curr.length);
            }
        }
        // And finally, current segment:
        sb.append(mCurrentSegment, 0, currLen);
        return sb;
    }

    public void contentsToStringBuilder(StringBuilder sb)
    {
        if (mResultString != null) {
            sb.append(mResultString);
        } else if (mResultArray != null) {
            sb.append(mResultArray);
        } else if (mInputStart >= 0) { // shared array
            if (mInputLen > 0) {
                sb.append(mInputBuffer, mInputStart, mInputLen);
            }
        } else {
            // First stored segments
            if (mSegments != null) {
                for (int i = 0, len = mSegments.size(); i < len; ++i) {
                    char[] curr = mSegments.get(i);
                    sb.append(curr, 0, curr.length);
                }
            }
            // And finally, current segment:
            sb.append(mCurrentSegment, 0, mCurrentSize);
        }
    }
 
    public char[] contentsAsArray()
    {
        char[] result = mResultArray;
        if (result == null) {
            mResultArray = result = buildResultArray();
        }
        return result;
    }

    public int contentsToArray(int srcStart, char[] dst, int dstStart, int len) {
        // Easy to copy from shared buffer:
        if (mInputStart >= 0) {
            int amount = mInputLen - srcStart;
            if (amount > len) {
                amount = len;
            } else if (amount < 0) {
                amount = 0;
            }
            if (amount > 0) {
                System.arraycopy(mInputBuffer, mInputStart+srcStart,
                                 dst, dstStart, amount);
            }
            return amount;
        }

        /* Could also check if we have array, but that'd only help with
         * braindead clients that get full array first, then segments...
         * which hopefully aren't that common
         */
        // Copying from segmented array is bit more involved:
        int totalAmount = 0;
        if (mSegments != null) {
            for (int i = 0, segc = mSegments.size(); i < segc; ++i) {
                char[] segment = mSegments.get(i);
                int segLen = segment.length;
                int amount = segLen - srcStart;
                if (amount < 1) { // nothing from this segment?
                    srcStart -= segLen;
                    continue;
                }
                if (amount >= len) { // can get rest from this segment?
                    System.arraycopy(segment, srcStart, dst, dstStart, len);
                    return (totalAmount + len);
                }
                // Can get some from this segment, offset becomes zero:
                System.arraycopy(segment, srcStart, dst, dstStart, amount);
                totalAmount += amount;
                dstStart += amount;
                len -= amount;
                srcStart = 0;
            }
        }

        // Need to copy anything from last segment?
        if (len > 0) {
            int maxAmount = mCurrentSize - srcStart;
            if (len > maxAmount) {
                len = maxAmount;
            }
            if (len > 0) { // should always be true
                System.arraycopy(mCurrentSegment, srcStart, dst, dstStart, len);
                totalAmount += len;
            }
        }

        return totalAmount;
    }

    /**
     * Method that will stream contents of this buffer into specified
     * Writer.
     */
    public int rawContentsTo(Writer w)
        throws IOException
    {
        // Let's first see if we have created helper objects:
        if (mResultArray != null) {
            w.write(mResultArray);
            return mResultArray.length;
        }
        if (mResultString != null) {
            w.write(mResultString);
            return mResultString.length();
        }

        // Do we use shared array?
        if (mInputStart >= 0) {
            if (mInputLen > 0) {
                w.write(mInputBuffer, mInputStart, mInputLen);
            }
            return mInputLen;
        }
        // Nope, need to do full segmented output
        int rlen = 0;
        if (mSegments != null) {
            for (int i = 0, len = mSegments.size(); i < len; ++i) {
                char[] ch = mSegments.get(i);
                w.write(ch);
                rlen += ch.length;
            }
        }
        if (mCurrentSize > 0) {
            w.write(mCurrentSegment, 0, mCurrentSize);
            rlen += mCurrentSize;
        }
        return rlen;
    }

    public Reader rawContentsViaReader()
        throws IOException
    {
        // Let's first see if we have created helper objects:
        if (mResultArray != null) {
	    return new CharArrayReader(mResultArray);
        }
        if (mResultString != null) {
            return new StringReader(mResultString);
        }

        // Do we use shared array?
        if (mInputStart >= 0) {
            if (mInputLen > 0) {
                return new CharArrayReader(mInputBuffer, mInputStart, mInputLen);
            }
            return new StringReader("");
        }
	// or maybe it's all in the current segment
	if (mSegments == null || mSegments.size() == 0) {
	    return new CharArrayReader(mCurrentSegment, 0, mCurrentSize);
	}
        // Nope, need to do full segmented output
	return new BufferReader(mSegments, mCurrentSegment, mCurrentSize);
    }

    public boolean isAllWhitespace()
    {
        if (mInputStart >= 0) { // using single shared buffer?
            char[] buf = mInputBuffer;
            int i = mInputStart;
            int last = i + mInputLen;
            for (; i < last; ++i) {
                if (buf[i] > INT_SPACE) {
                    return false;
                }
            }
            return true;
        }

        // Nope, need to do full segmented output
        if (mSegments != null) {
            for (int i = 0, len = mSegments.size(); i < len; ++i) {
                char[] buf = mSegments.get(i);
                for (int j = 0, len2 = buf.length; j < len2; ++j) {
                    if (buf[j] > INT_SPACE) {
                        return false;
                    }
                }
            }
        }
        
        char[] buf = mCurrentSegment;
        for (int i = 0, len = mCurrentSize; i < len; ++i) {
            if (buf[i] > INT_SPACE) {
                return false;
            }
        }
        return true;
    }

    /**
     * Method that can be used to check if the contents of the buffer end
     * in specified String.
     *
     * @return True if the textual content buffer contains ends with the
     *   specified String; false otherwise
     */
    public boolean endsWith(String str)
    {
        /* Let's just play this safe; should seldom if ever happen...
         * and because of that, can be sub-optimal, performancewise, to
         * alternatives.
         */
        if (mInputStart >= 0) {
            unshare(16);
        }

        int segIndex = (mSegments == null) ? 0 : mSegments.size();
        int inIndex = str.length() - 1;
        char[] buf = mCurrentSegment;
        int bufIndex = mCurrentSize-1;

        while (inIndex >= 0) {
            if (str.charAt(inIndex) != buf[bufIndex]) {
                return false;
            }
            if (--inIndex == 0) {
                break;
            }
            if (--bufIndex < 0) {
                if (--segIndex < 0) { // no more data?
                    return false;
                }
                buf = mSegments.get(segIndex);
                bufIndex = buf.length-1;
            }
        }

        return true;
    }

    /**
     * Note: it is assumed that this method is not used often enough to
     * be a bottleneck, or for long segments. Based on this, it is optimized
     * for common simple cases where there is only one single character
     * segment to use; fallback for other cases is to create such segment.
     */
    public boolean equalsString(String str)
    {
        int expLen = str.length();
        
        // First the easy check; if we have a shared buf:
        if (mInputStart >= 0) {
            if (mInputLen != expLen) {
                return false;
            }
            for (int i = 0; i < expLen; ++i) {
                if (str.charAt(i) != mInputBuffer[mInputStart+i]) {
                    return false;
                }
            }
            return true;
        }
        
        // Otherwise, segments:
        if (expLen != size()) {
            return false;
        }
        char[] seg;
        if (mSegments == null || mSegments.size() == 0) {
            // just one segment, still easy
            seg = mCurrentSegment;
        } else {
            /* Ok; this is the sub-optimal case. Could obviously juggle through
             * segments, but probably not worth the hassle, we seldom if ever
             * get here...
             */
            seg = contentsAsArray();
        }
        
        for (int i = 0; i < expLen; ++i) {
            if (seg[i] != str.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /*
    //////////////////////////////////////////////
    // Access using SAX handlers:
    //////////////////////////////////////////////
     */

    public void fireSaxCharacterEvents(ContentHandler h)
        throws SAXException
    {
        if (mResultArray != null) { // already have single array?
            h.characters(mResultArray, 0, mResultArray.length);
        } else if (mInputStart >= 0) { // sharing input buffer?
            h.characters(mInputBuffer, mInputStart, mInputLen);
        } else {
            if (mSegments != null) {
                for (int i = 0, len = mSegments.size(); i < len; ++i) {
                    char[] ch = mSegments.get(i);
                    h.characters(ch, 0, ch.length);
                }
            }
            if (mCurrentSize > 0) {
                h.characters(mCurrentSegment, 0, mCurrentSize);
            }
        }
    }

    public void fireSaxSpaceEvents(ContentHandler h)
        throws SAXException
    {
        if (mResultArray != null) { // only happens for indentation
            h.ignorableWhitespace(mResultArray, 0, mResultArray.length);
        } else if (mInputStart >= 0) { // sharing input buffer?
            h.ignorableWhitespace(mInputBuffer, mInputStart, mInputLen);
        } else {
            if (mSegments != null) {
                for (int i = 0, len = mSegments.size(); i < len; ++i) {
                    char[] ch = mSegments.get(i);
                    h.ignorableWhitespace(ch, 0, ch.length);
                }
            }
            if (mCurrentSize > 0) {
                h.ignorableWhitespace(mCurrentSegment, 0, mCurrentSize);
            }
        }
    }

    public void fireSaxCommentEvent(LexicalHandler h)
        throws SAXException
    {
        // Comment can not be split, so may need to combine the array
        if (mResultArray != null) { // only happens for indentation
            h.comment(mResultArray, 0, mResultArray.length);
        } else if (mInputStart >= 0) { // sharing input buffer?
            h.comment(mInputBuffer, mInputStart, mInputLen);
        } else if (mSegments != null && mSegments.size() > 0) {
            char[] ch = contentsAsArray();
            h.comment(ch, 0, ch.length);
        } else {
            h.comment(mCurrentSegment, 0, mCurrentSize);
        }
    }

    public void fireDtdCommentEvent(DTDEventListener l)
    {
        // Comment can not be split, so may need to combine the array
        if (mResultArray != null) { // only happens for indentation
            l.dtdComment(mResultArray, 0, mResultArray.length);
        } else if (mInputStart >= 0) { // sharing input buffer?
            l.dtdComment(mInputBuffer, mInputStart, mInputLen);
        } else if (mSegments != null && mSegments.size() > 0) {
            char[] ch = contentsAsArray();
            l.dtdComment(ch, 0, ch.length);
        } else {
            l.dtdComment(mCurrentSegment, 0, mCurrentSize);
        }
    }

    /*
    //////////////////////////////////////////////
    // Support for validation
    //////////////////////////////////////////////
     */

    public void validateText(XMLValidator vld, boolean lastSegment)
        throws XMLStreamException
    {
        // Shared buffer? Let's just pass that
        if (mInputStart >= 0) {
            vld.validateText(mInputBuffer, mInputStart, mInputStart + mInputLen, lastSegment);
        } else {
            /* Otherwise, can either create a combine buffer, or construct
             * a String. While former could be more efficient, let's do latter
             * for now since current validator implementations work better
             * with Strings.
             */
            vld.validateText(contentsAsString(), lastSegment);
        }
    }

    /*
    //////////////////////////////////////////////
    // Public mutators:
    //////////////////////////////////////////////
     */

    /**
     * Method called to make sure that buffer is not using shared input
     * buffer; if it is, it will copy such contents to private buffer.
     */
    public void ensureNotShared() {
        if (mInputStart >= 0) {
            unshare(16);
        }
    }

    public void append(char c) {
        // Using shared buffer so far?
        if (mInputStart >= 0) {
            unshare(16);
        }
        mResultString = null;
        mResultArray = null;
        // Room in current segment?
        char[] curr = mCurrentSegment;
        if (mCurrentSize >= curr.length) {
            expand(1);
            curr = mCurrentSegment;
        }
        curr[mCurrentSize++] = c;
    }

    public void append(char[] c, int start, int len)
    {
        // Can't append to shared buf (sanity check)
        if (mInputStart >= 0) {
            unshare(len);
        }
        mResultString = null;
        mResultArray = null;

        // Room in current segment?
        char[] curr = mCurrentSegment;
        int max = curr.length - mCurrentSize;
            
        if (max >= len) {
            System.arraycopy(c, start, curr, mCurrentSize, len);
            mCurrentSize += len;
        } else {
            // No room for all, need to copy part(s):
            if (max > 0) {
                System.arraycopy(c, start, curr, mCurrentSize, max);
                start += max;
                len -= max;
            }
            /* And then allocate new segment; we are guaranteed to now
             * have enough room in segment.
             */
            expand(len); // note: curr != mCurrentSegment after this
            System.arraycopy(c, start, mCurrentSegment, 0, len);
            mCurrentSize = len;
        }
    }

    public void append(String str)
    {
        // Can't append to shared buf (sanity check)
        int len = str.length();
        if (mInputStart >= 0) {
            unshare(len);
        }
        mResultString = null;
        mResultArray = null;

        // Room in current segment?
        char[] curr = mCurrentSegment;
        int max = curr.length - mCurrentSize;
        if (max >= len) {
            str.getChars(0, len, curr, mCurrentSize);
            mCurrentSize += len;
        } else {
            // No room for all, need to copy part(s):
            if (max > 0) {
                str.getChars(0, max, curr, mCurrentSize);
                len -= max;
            }
            /* And then allocate new segment; we are guaranteed to now
             * have enough room in segment.
             */
            expand(len);
            str.getChars(max, max+len, mCurrentSegment, 0);
            mCurrentSize = len;
        }
    }

    /*
    //////////////////////////////////////////////
    // Raw access, for high-performance use:
    //////////////////////////////////////////////
     */

    public char[] getCurrentSegment()
    {
        /* Since the intention of the caller is to directly add stuff into
         * buffers, we should NOT have anything in shared buffer... ie. may
         * need to unshare contents.
         */
        if (mInputStart >= 0) {
            unshare(1);
        } else {
            char[] curr = mCurrentSegment;
            if (curr == null) {
                mCurrentSegment = allocBuffer(0);
            } else if (mCurrentSize >= curr.length) {
                // Plus, we better have room for at least one more char
                expand(1);
            }
        }
        return mCurrentSegment;
    }

    public int getCurrentSegmentSize() {
        return mCurrentSize;
    }

    public void setCurrentLength(int len) {
        mCurrentSize = len;
    }

    public char[] finishCurrentSegment()
    {
        if (mSegments == null) {
            mSegments = new ArrayList<char[]>();
        }
        mHasSegments = true;
        mSegments.add(mCurrentSegment);
        int oldLen = mCurrentSegment.length;
        mSegmentSize += oldLen;
        char[] curr = new char[calcNewSize(oldLen)];
        mCurrentSize = 0;
        mCurrentSegment = curr;
        return curr;
    }
    
    /**
     * Method used to determine size of the next segment to
     * allocate to contain textual content.
     */
    private int calcNewSize(int latestSize)
    {
        // Let's grow segments by 50%, when over 8k
        int incr = (latestSize < 8000) ? latestSize : (latestSize >> 1);
        int size = latestSize + incr;
        // but let's not create too big chunks
        return Math.min(size, MAX_SEGMENT_LENGTH);
    }

    /*
    //////////////////////////////////////////////
    // Standard methods:
    //////////////////////////////////////////////
     */

    /**
     * Note: calling this method may not be as efficient as calling
     * {@link #contentsAsString}, since it's not guaranteed that resulting
     * String is cached.
     */
    public String toString() {
         return contentsAsString();
    }

    /*
    //////////////////////////////////////////////
    // Internal methods:
    //////////////////////////////////////////////
     */

    /**
     * Method called if/when we need to append content when we have been
     * initialized to use shared buffer.
     */
    public void unshare(int needExtra)
    {
        int len = mInputLen;
        mInputLen = 0;
        char[] inputBuf = mInputBuffer;
        mInputBuffer = null;
        int start = mInputStart;
        mInputStart = -1;

        // Is buffer big enough, or do we need to reallocate?
        int needed = len+needExtra;
        if (mCurrentSegment == null || needed > mCurrentSegment.length) {
            mCurrentSegment = allocBuffer(needed);
        }
        if (len > 0) {
            System.arraycopy(inputBuf, start, mCurrentSegment, 0, len);
        }
        mSegmentSize = 0;
        mCurrentSize = len;
    }

    /**
     * Method called when current segment is full, to allocate new
     * segment.
     *
     * @param roomNeeded Number of characters that the resulting
     *   new buffer must have
     */
    private void expand(int roomNeeded)
    {
        // First, let's move current segment to segment list:
        if (mSegments == null) {
            mSegments = new ArrayList<char[]>();
        }
        char[] curr = mCurrentSegment;
        mHasSegments = true;
        mSegments.add(curr);
        int oldLen = curr.length;
        mSegmentSize += oldLen;
        int newSize = Math.max(roomNeeded, calcNewSize(oldLen));
        curr = new char[newSize];
        mCurrentSize = 0;
        mCurrentSegment = curr;
    }

    private char[] buildResultArray()
    {
        if (mResultString != null) { // Can take a shortcut...
            return mResultString.toCharArray();
        }
        char[] result;
        
        // Do we use shared array?
        if (mInputStart >= 0) {
            if (mInputLen < 1) {
                return DataUtil.getEmptyCharArray();
            }
            result = new char[mInputLen];
            System.arraycopy(mInputBuffer, mInputStart, result, 0,
                             mInputLen);
        } else { // nope 
            int size = size();
            if (size < 1) {
                return DataUtil.getEmptyCharArray();
            }
            int offset = 0;
            result = new char[size];
            if (mSegments != null) {
                for (int i = 0, len = mSegments.size(); i < len; ++i) {
                    char[] curr = mSegments.get(i);
                    int currLen = curr.length;
                    System.arraycopy(curr, 0, result, offset, currLen);
                    offset += currLen;
                }
            }
            System.arraycopy(mCurrentSegment, 0, result, offset, mCurrentSize);
        }
        return result;
    }

    private final static class BufferReader
        extends Reader
    {
        ArrayList<char[]> _segments;
        char[] _currentSegment;
        final int _currentLength;
        
        int _segmentIndex;
        int _segmentOffset;
        int _currentOffset;
        
        public BufferReader(ArrayList<char[]> segs, char[] currSeg, int currSegLen)
        {
            _segments = segs;
            _currentSegment = currSeg;
            _currentLength = currSegLen;
            
            _segmentIndex = 0;
            _segmentOffset = _currentOffset = 0;
        }
        
        public void close() {
            _segments = null;
            _currentSegment = null;
        }
        
        public void mark(int x)
            throws IOException
        {      
            throw new IOException("mark() not supported");
        }
        
        public boolean markSupported() {
            return false;
        }

        public int read(char[] cbuf, int offset, int len)
        {
            if (len < 1) {
                return 0;
            }
            
            int origOffset = offset;
            // First need to copy stuff from previous segments
            while (_segments != null) {
                char[] curr = _segments.get(_segmentIndex);
                int max = curr.length - _segmentOffset;
                if (len <= max) { // this is enough
                    System.arraycopy(curr, _segmentOffset, cbuf, offset, len);
                    _segmentOffset += len;
                    offset += len;
                    return (offset - origOffset);
                }
                // Not enough, but helps...
                if (max > 0) {
                    System.arraycopy(curr, _segmentOffset, cbuf, offset, max);
                    offset += max;
                }
                if (++_segmentIndex >= _segments.size()) { // last one
                    _segments = null;
                } else {
                    _segmentOffset = 0;
                }
            }
            
            // ok, anything to copy from the active segment?
            if (len > 0 && _currentSegment != null) {
                int max = _currentLength - _currentOffset;
                if (len >= max) { // reading it all
                    len = max;
                    System.arraycopy(_currentSegment, _currentOffset,
                                     cbuf, offset, len);
                    _currentSegment = null;
                } else {
                    System.arraycopy(_currentSegment, _currentOffset,
                                     cbuf, offset, len);
                    _currentOffset += len;
                }
                offset += len;
            }

            return (origOffset == offset) ? -1 : (offset - origOffset);
        }
        
        public boolean ready() {
            return true;
        }
        
        public void reset()
            throws IOException
        {	
            throw new IOException("reset() not supported");
        }
        
        public long skip(long amount)
        {
            /* Note: implementation is almost identical to that of read();
             * difference being that no data is copied.
             */
            if (amount < 0) {
                return 0L;
            }
            
            long origAmount= amount;
            
            while (_segments != null) {
                char[] curr = _segments.get(_segmentIndex);
                int max = curr.length - _segmentOffset;
                if (max >= amount) { // this is enough
                    _segmentOffset += (int) amount;
                    return origAmount;
                }
		// Not enough, but helps...
                amount -= max;
                if (++_segmentIndex >= _segments.size()) { // last one
                    _segments = null;
                } else {
                    _segmentOffset = 0;
                }
            }
            
            // ok, anything left in the active segment?
            if (amount > 0 && _currentSegment != null) {
                int max = _currentLength - _currentOffset;
                if (amount >= max) { // reading it all
                    amount -= max;
                    _currentSegment = null;
                } else {
                    amount = 0L;
                    _currentOffset += (int) amount;
                }
            }
            
            return (amount == origAmount) ? -1L : (origAmount - amount);
        }
    }
}
