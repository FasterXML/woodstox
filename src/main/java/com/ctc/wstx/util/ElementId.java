package com.ctc.wstx.util;

import javax.xml.stream.Location;

import com.ctc.wstx.cfg.ErrorConsts;

/**
 * Simple container Object used to store information about id attribute
 * values, and references to such (as of yet undefined) values.
 *<p>
 * Instances can be in one of 2 modes: either in fully defined mode,
 * in which case information refers to location where value was defined
 * (ie. we had id as a value of ID type attribute); or in undefined mode,
 * in which case information refers to the first reference.
 *<p>
 * Note: this class is designed to be used with {@link ElementIdMap},
 * and as a result has some information specifically needed by the
 * map implementation (such as collision links).
 */
public final class ElementId
{
    /**
     * Flag that indicates whether this Object presents a defined id
     * value (value of an ID attribute) or just a reference to one.
     */
    private boolean mDefined;

    /*
    /////////////////////////////////////////////////
    // Information about id value or value reference,
    // depending on mDefined flag
    /////////////////////////////////////////////////
    */

    /**
     * Actual id value
     */
    private final String mIdValue;

    /**
     * Location of either definition (if {@link #mDefined} is true; or
     * first reference (otherwise). Used when reporting errors; either
     * a referenced id has not been defined, or there are multiple
     * definitions of same id.
     */
    private Location mLocation;

    /**
     * Name of element for which this id refers.
     */
    private PrefixedName mElemName;

    /**
     * Name of the attribute that contains this id value (often "id", 
     * but need not be)
     */
    private PrefixedName mAttrName;

    /*
    ////////////////////////////////////////////////////
    // Linking information, needed by the map to keep
    // track of collided ids, as well as undefined ids
    ////////////////////////////////////////////////////
    */

    private ElementId mNextUndefined;

    /**
     * Pointer to the next element within collision chain.
     */
    private ElementId mNextColl;

    /*
    /////////////////////////////////////////////////
    // Life cycle
    /////////////////////////////////////////////////
    */

    ElementId(String id, Location loc, boolean defined,
              PrefixedName elemName, PrefixedName attrName)
    {
        mIdValue = id;
        mLocation = loc;
        mDefined = defined;
        mElemName = elemName;
        mAttrName = attrName;
    }

    protected void linkUndefined(ElementId undefined)
    {
        if (mNextUndefined != null) {
            throw new IllegalStateException("ElementId '"+this+"' already had net undefined set ('"+mNextUndefined+"')");
        }
        mNextUndefined = undefined;
    }

    protected void setNextColliding(ElementId nextColl)
    {
        // May add/remove link, no point in checking
        mNextColl = nextColl;
    }

    /*
    /////////////////////////////////////////////////
    // Public API
    /////////////////////////////////////////////////
    */

    public String getId() { return mIdValue; }
    public Location getLocation() { return mLocation; }
    public PrefixedName getElemName() { return mElemName; }
    public PrefixedName getAttrName() { return mAttrName; }
    
    public boolean isDefined() { return mDefined; }

    public boolean idMatches(char[] buf, int start, int len)
    {
        if (mIdValue.length() != len) {
            return false;
        }
        // Assumes it's always at least one char long
        if (buf[start] != mIdValue.charAt(0)) {
            return false;
        }
        int i = 1;
        len += start;
        while (++start < len) {
            if (buf[start] != mIdValue.charAt(i)) {
                return false;
            }
            ++i;
        }
        return true;
    }

    public boolean idMatches(String idStr)
    {
        return mIdValue.equals(idStr);
    }

    public ElementId nextUndefined() { return mNextUndefined; }
    public ElementId nextColliding() { return mNextColl; }

    public void markDefined(Location defLoc) {
        if (mDefined) { // sanity check
            throw new IllegalStateException(ErrorConsts.ERR_INTERNAL);
        }
        mDefined = true;
        mLocation = defLoc;
    }

    /*
    /////////////////////////////////////////////////
    // Other methods
    /////////////////////////////////////////////////
    */

    public String toString() {
        return mIdValue;
    }
}

