package com.ctc.wstx.ent;

import java.io.IOException;
import java.io.Writer;
import java.net.URL;

import javax.xml.stream.Location;
import javax.xml.stream.XMLResolver;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.io.InputSourceFactory;
import com.ctc.wstx.io.TextEscaper;
import com.ctc.wstx.io.WstxInputLocation;
import com.ctc.wstx.io.WstxInputSource;

public class IntEntity
    extends EntityDecl
{
    /**
     * Location where entity content definition started;
     * points to the starting/opening quote for internal
     * entities.
     */
    protected final Location mContentLocation;

    /**
     * Replacement text of the entity; full array contents.
     */
    final char[] mRepl;

    String mReplText = null;

    public IntEntity(Location loc, String name, URL ctxt,
                     char[] repl, Location defLoc)
    {
        super(loc, name, ctxt);
        mRepl = repl;
        mContentLocation = defLoc;
    }

    public static IntEntity create(String id, String repl)
    {
        return create(id, repl.toCharArray());
    }

    public static IntEntity create(String id, char[] val)
    {
        WstxInputLocation loc = WstxInputLocation.getEmptyLocation();
        return new IntEntity(loc, id, null, val, loc);
    }

    @Override
    public String getNotationName() {
        return null;
    }

    @Override
    public String getPublicId() {
        return null;
    }

    @Override
    public String getReplacementText()
    {
        String repl = mReplText;
        if (repl == null) {
            repl = (mRepl.length == 0) ? "" : new String(mRepl);
            mReplText = repl;
        }
        return mReplText;
    }

    @Override
    public int getReplacementText(Writer w) throws IOException
    {
        w.write(mRepl);
        return mRepl.length;
    }

    @Override
    public String getSystemId() {
        return null;
    }

    /*
    ///////////////////////////////////////////
    // Implementation of abstract base methods
    ///////////////////////////////////////////
     */

    @Override
    public void writeEnc(Writer w) throws IOException
    {
        w.write("<!ENTITY ");
        w.write(mName);
        w.write(" \"");
        TextEscaper.outputDTDText(w, mRepl, 0, mRepl.length);
        w.write("\">");
    }

    /*
    ///////////////////////////////////////////
    // Extended API for Wstx core
    ///////////////////////////////////////////
     */

    // // // Access to data

    /**
     * Gives raw access to replacement text data...
     *<p>
     * Note: this is not really safe, as caller can modify the array, but
     * since this method is thought to provide fast access, let's avoid making
     * copy here.
     */
    @Override
    public char[] getReplacementChars() {
        return mRepl;
    }

    // // // Type information
    
    @Override
    public boolean isExternal() { return false; }
    
    @Override
    public boolean isParsed() { return true; }
    
    @Override
    public WstxInputSource expand(WstxInputSource parent,
                                  XMLResolver res, ReaderConfig cfg,
                                  int xmlVersion)
    {
        /* 26-Dec-2006, TSa: Better leave source as null, since internal
         *   entity declaration context should never be used: when expanding,
         *   reference context is to be used.
         */
        return InputSourceFactory.constructCharArraySource
            //(parent, mName, mRepl, 0, mRepl.length, mContentLocation, getSource());
            (parent, mName, mRepl, 0, mRepl.length, mContentLocation, null);
    }
}

