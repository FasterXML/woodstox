package com.ctc.wstx.evt;

import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.Location;
import javax.xml.stream.events.EntityDeclaration;
import javax.xml.stream.events.NotationDeclaration;

import org.codehaus.stax2.ri.evt.DTDEventImpl;

import com.ctc.wstx.dtd.DTDSubset;

/**
 * Event that contains all StAX accessible information read from internal
 * and external DTD subsets.
 */
public class WDTD
    extends DTDEventImpl
{
    /**
     * Internal DTD Object that contains combined information from internal
     * and external subsets.
     */
    final DTDSubset mSubset;

    /*
    /////////////////////////////////////////////////////
    // Lazily constructed objects
    /////////////////////////////////////////////////////
     */

    List<EntityDeclaration> mEntities = null;

    List<NotationDeclaration> mNotations = null;

    /*
    /////////////////////////////////////////////////////
    // Constuctors
    /////////////////////////////////////////////////////
     */

    public WDTD(Location loc, String rootName,
                String sysId, String pubId, String intSubset,
                DTDSubset dtdSubset)
    {
        super(loc, rootName, sysId, pubId, intSubset, dtdSubset);
        mSubset = dtdSubset;
    }

    public WDTD(Location loc, String rootName,
                String sysId, String pubId, String intSubset)
    {
        this(loc, rootName, sysId, pubId, intSubset, null);
    }

    /**
     * Constructor used when only partial information is available...
     */
    public WDTD(Location loc, String rootName, String intSubset)
    {
        this(loc, rootName, null, null, intSubset, null);
    }

    public WDTD(Location loc, String fullText)
    {
        super(loc, fullText);
        mSubset = null;
    }

    /*
    /////////////////////////////////////////////////////
    // Accessors
    /////////////////////////////////////////////////////
     */

    @Override
    public List<EntityDeclaration> getEntities()
    {
        if (mEntities == null && (mSubset != null)) {
            /* Better make a copy, so that caller can not modify list
             * DTD has, which may be shared (since DTD subset instances
             * are cached and reused)
             */
            mEntities = new ArrayList<EntityDeclaration>(mSubset.getGeneralEntityList());
        }
        return mEntities;
    }

    @Override
    public List<NotationDeclaration> getNotations() {
        if (mNotations == null && (mSubset != null)) {
            mNotations = new ArrayList<NotationDeclaration>(mSubset.getNotationList());
        }
        return mNotations;
    }
}
