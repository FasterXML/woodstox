package com.ctc.wstx.ent;

import com.ctc.wstx.api.ReaderConfig;

import java.io.IOException;
import java.io.Writer;
import java.net.URL;

import javax.xml.stream.Location;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;

import com.ctc.wstx.io.WstxInputSource;

public abstract class ExtEntity
    extends EntityDecl
{
    final String mPublicId;
    final String mSystemId;

    public ExtEntity(Location loc, String name, URL ctxt,
                     String pubId, String sysId)
    {
        super(loc, name, ctxt);
        mPublicId = pubId;
        mSystemId = sysId;
    }

    @Override
    public abstract String getNotationName();

    @Override
    public String getPublicId() {
        return mPublicId;
    }

    @Override
    public String getReplacementText() {
        return null;
    }

    @Override
    public int getReplacementText(Writer w)
        //throws IOException
    {
        return 0;
    }

    @Override
    public String getSystemId() {
        return mSystemId;
    }

    /*
    ///////////////////////////////////////////
    // Implementation of abstract base methods
    ///////////////////////////////////////////
     */

    @Override
    public abstract void writeEnc(Writer w) throws IOException;

    /*
    ///////////////////////////////////////////
    // Extended API for Wstx core
    ///////////////////////////////////////////
     */

    // // // Access to data

    @Override
    public char[] getReplacementChars() {
        return null;
    }

    // // // Type information
    
    @Override
    public boolean isExternal() { return true; }
    
    @Override
    public abstract boolean isParsed();

    @Override
    public abstract WstxInputSource expand(WstxInputSource parent,
                                           XMLResolver res, ReaderConfig cfg,
                                           int xmlVersion)
        throws IOException, XMLStreamException;
}
