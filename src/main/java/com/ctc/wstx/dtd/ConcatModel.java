package com.ctc.wstx.dtd;

import java.util.*;

/**
 * Model class that represents sequence of 2 sub-models, needed to be
 * matched in the order.
 */
public class ConcatModel
    extends ModelNode
{
    ModelNode mLeftModel;
    ModelNode mRightModel;

    final boolean mNullable;

    BitSet mFirstPos, mLastPos;

    /*
    ///////////////////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////////////////
     */

    public ConcatModel(ModelNode left, ModelNode right)
    {
        super();
        mLeftModel = left;
        mRightModel = right;
        mNullable = mLeftModel.isNullable() && mRightModel.isNullable();
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
    @Override
    public ModelNode cloneModel() {
        return new ConcatModel(mLeftModel.cloneModel(), mRightModel.cloneModel());
    }

    @Override
    public boolean isNullable() {
        return mNullable;
    }

    @Override
    public void indexTokens(List<TokenModel> tokens)
    {
        mLeftModel.indexTokens(tokens);
        mRightModel.indexTokens(tokens);
    }

    @Override
    public void addFirstPos(BitSet pos) {
        if (mFirstPos == null) {
            mFirstPos = new BitSet();
            mLeftModel.addFirstPos(mFirstPos);
            if (mLeftModel.isNullable()) {
                mRightModel.addFirstPos(mFirstPos);
            }
        }
        pos.or(mFirstPos);
    }
    
    @Override
    public void addLastPos(BitSet pos) {
        if (mLastPos == null) {
            mLastPos = new BitSet();
            mRightModel.addLastPos(mLastPos);
            if (mRightModel.isNullable()) {
                mLeftModel.addLastPos(mLastPos);
            }
        }
        pos.or(mLastPos);
    }

    @Override
    public void calcFollowPos(BitSet[] followPosSets)
    {
        // Let's let sub-models do what they need to do
        mLeftModel.calcFollowPos(followPosSets);
        mRightModel.calcFollowPos(followPosSets);

        /* And then we can calculate follower sets between left and
         * right sub models; so that left model's last position entries
         * have right model's first position entries included
         */
        BitSet foll = new BitSet();
        mRightModel.addFirstPos(foll);

        BitSet toAddTo = new BitSet();
        mLeftModel.addLastPos(toAddTo);

        int ix = 0; // need to/can skip the null entry (index 0) 
        while ((ix = toAddTo.nextSetBit(ix+1)) >= 0) {
            // Ok; so token at this index needs to have follow positions added...
            followPosSets[ix].or(foll);
        }
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        sb.append(mLeftModel.toString());
        sb.append(", ");
        sb.append(mRightModel.toString());
        sb.append(')');
        return sb.toString();
    }
}
