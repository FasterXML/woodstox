package com.ctc.wstx.dtd;

import com.ctc.wstx.util.PrefixedName;

/**
 * Simple content model validator that accepts no elements, ever; this
 * is true for pure #PCDATA content model as well as EMPTY content model.
 * Can be used as a singleton, since all info needed for diagnostics
 * is passed via methods.
 */
public class EmptyValidator
    extends StructValidator
{
    final static EmptyValidator sPcdataInstance = new EmptyValidator("No elements allowed in pure #PCDATA content model");

    final static EmptyValidator sEmptyInstance = new EmptyValidator("No elements allowed in EMPTY content model");

    final String mErrorMsg;

    private EmptyValidator(String errorMsg) {
        mErrorMsg = errorMsg;
    }

    public static EmptyValidator getPcdataInstance() { return sPcdataInstance; }
    public static EmptyValidator getEmptyInstance() { return sPcdataInstance; }

    /**
     * Simple; can always (re)use instance itself; no state information
     * is kept.
     */
    public StructValidator newInstance() {
        return this;
    }

    public String tryToValidate(PrefixedName elemName)
    {
        return mErrorMsg;
    }

    /**
     * If we ever get as far as element closing, things are all good;
     * can just return null.
     */
    public String fullyValid()
    {
        return null;
    }
}
