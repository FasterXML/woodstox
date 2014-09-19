package com.ctc.wstx.dtd;

import java.util.*;

/**
 * Content specification class that represents an optional specification.
 * Optional specifications are generally a result of '?' arity marker,
 * and are created when {@link ContentSpec#rewrite} is called
 * on a specification with '?' arity modifier.
 */
public class OptionalModel
    extends ModelNode
{
    ModelNode mModel;

    /*
    ///////////////////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////////////////
     */

    public OptionalModel(ModelNode model) {
        mModel = model;
    }

    /*
    ///////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////
     */

    /**
     * Method that has to create a deep copy of the model, without
     * sharing any of existing Objects.
     */
    public ModelNode cloneModel() {
        return new OptionalModel(mModel.cloneModel());
    }

    public boolean isNullable() {
        return true;
    }

    public void indexTokens(List<TokenModel> tokens) {
        mModel.indexTokens(tokens);
    }

    public void addFirstPos(BitSet pos) {
        mModel.addFirstPos(pos);
    }
    
    public void addLastPos(BitSet pos) {
        mModel.addLastPos(pos);
    }

    public void calcFollowPos(BitSet[] followPosSets)
    {
        // Let's let sub-model do its stuff
        mModel.calcFollowPos(followPosSets);
    }

    public String toString() {
        return mModel + "[?]";
    }
}
