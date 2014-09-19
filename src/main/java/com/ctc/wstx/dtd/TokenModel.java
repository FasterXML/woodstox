package com.ctc.wstx.dtd;

import java.util.BitSet;
import java.util.List;

import com.ctc.wstx.util.PrefixedName;

/**
 * Model class that encapsulates a single (obligatory) token instance.
 */
public final class TokenModel
    extends ModelNode
{
    final static TokenModel NULL_TOKEN = new TokenModel(null);
    static { // null token needs to have 0 as its index...
        NULL_TOKEN.mTokenIndex = 0;
    }

    final PrefixedName mElemName;

    int mTokenIndex = -1; // to catch errors...

    /*
    ///////////////////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////////////////
     */

    public TokenModel(PrefixedName elemName) {
        mElemName = elemName;
    }

    public static TokenModel getNullToken() {
        return NULL_TOKEN;
    }

    /*
    ///////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////
     */

    public PrefixedName getName() { return mElemName; }

    /**
     * Method that has to create a deep copy of the model, without
     * sharing any of existing Objects.
     */
    public ModelNode cloneModel() {
        return new TokenModel(mElemName);
    }

    public boolean isNullable() {
        return false;
    }

    public void indexTokens(List<TokenModel> tokens)
    {
        /* Doh. This is not clean... but need to make sure the null
         * token never gets reindexed or explicitly added:
         */
        if (this != NULL_TOKEN) {
            int index = tokens.size();
            mTokenIndex = index;
            tokens.add(this);
        }
    }

    public void addFirstPos(BitSet firstPos) {
        firstPos.set(mTokenIndex);
    }

    public void addLastPos(BitSet lastPos) {
        lastPos.set(mTokenIndex);
    }

    public void calcFollowPos(BitSet[] followPosSets) {
        // nothing to do, for tokens...
    }

    public String toString() {
        return (mElemName == null) ? "[null]" : mElemName.toString();
    }

    /*
    ///////////////////////////////////////////////////
    // Internal methods
    ///////////////////////////////////////////////////
     */
}
