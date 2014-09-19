package com.ctc.wstx.dtd;

import java.util.*;

/**
 * Model class that represents any number of repetitions of its submodel
 * (including no repetitions).
 */
public class StarModel
    extends ModelNode
{
    ModelNode mModel;

    /*
    ///////////////////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////////////////
     */

    public StarModel(ModelNode model) {
        super();
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
        return new StarModel(mModel.cloneModel());
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
        // First, let's let sub-model do its stuff
        mModel.calcFollowPos(followPosSets);

        /* And then add the closure for the model (since sub-model
         * can 'follow itself' as many times as it needs to)
         */

        BitSet foll = new BitSet();
        mModel.addFirstPos(foll);

        BitSet toAddTo = new BitSet();
        mModel.addLastPos(toAddTo);

        int ix = 0; // need to/can skip the null entry (index 0) 
        while ((ix = toAddTo.nextSetBit(ix+1)) >= 0) {
            /* Ok; so token at this index needs to have follow positions
             * added...
             */
            followPosSets[ix].or(foll);
        }
    }

    public String toString() {
        return mModel.toString() + "*";
    }
}

