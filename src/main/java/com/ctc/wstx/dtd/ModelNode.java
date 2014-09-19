package com.ctc.wstx.dtd;

import java.util.BitSet;
import java.util.List;

/**
 * Abstract base class for classes constructed from {@link ContentSpec}
 * objects, when they get rewritten (when their {@link ContentSpec#rewrite}
 * gets called). These nodes are then used for constructing complete DFA
 * states for validation.
 */
public abstract class ModelNode
{
    /*
    ///////////////////////////////////////////////////
    // Methods needed for DFA construction
    ///////////////////////////////////////////////////
     */

    /**
     * Method that has to create a deep copy of the model, without
     * sharing any of existing Objects.
     */
    public abstract ModelNode cloneModel();

    public abstract boolean isNullable();

    public abstract void indexTokens(List<TokenModel> tokens);

    public abstract void addFirstPos(BitSet firstPos);

    public abstract void addLastPos(BitSet firstPos);

    public abstract void calcFollowPos(BitSet[] followPosSets);
}
