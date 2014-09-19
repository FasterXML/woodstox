package com.ctc.wstx.dtd;

import java.util.*;

import com.ctc.wstx.util.PrefixedName;

/**
 * Content specification that defines model that has sequence of one or more
 * elements that have to come in the specified order.
 */
public class SeqContentSpec
    extends ContentSpec
{
    final boolean mNsAware;

    final ContentSpec[] mContentSpecs;

    /*
    ///////////////////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////////////////
     */

    public SeqContentSpec(boolean nsAware, char arity, ContentSpec[] subSpecs)
    {
        super(arity);
        mNsAware = nsAware;
        mContentSpecs = subSpecs;
    }

    public static SeqContentSpec construct(boolean nsAware, char arity, Collection<ContentSpec> subSpecs)
    {
        ContentSpec[] specs = new ContentSpec[subSpecs.size()];
        subSpecs.toArray(specs);
        return new SeqContentSpec(nsAware, arity, specs);
    }

    /*
    ///////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////
     */

    public StructValidator getSimpleValidator()
    {
        /* Can we create a simple validator? Yes, if the sub-specs are
         * all simple (leaves == element tokens with no arity modifier)
         */
        ContentSpec[] specs = mContentSpecs;
        int i = 0;
        int len = specs.length;

        for (; i < len; ++i) {
            if (!specs[i].isLeaf()) {
                break;
            }
        }

        if (i == len) { // all leaves, kewl
            PrefixedName[] set = new PrefixedName[len];
            for (i = 0; i < len; ++i) {
                TokenContentSpec ss = (TokenContentSpec) specs[i];
                set[i] = ss.getName();
            }
            return new Validator(mArity, set);
        }

        // Nope, need a DFA:
        return null;
    }

    public ModelNode rewrite()
    {
        /* First, need to create a tree of sub-models, consisting of
         * binary concat nodes (as opposed to n-ary list). Can do that
         * recursively (note that we'll always have at least 2 child
         * nodes!)
         */
        ModelNode model = rewrite(mContentSpecs, 0, mContentSpecs.length);

        // and then resolve arity modifiers, if necessary:
        if (mArity == '*') {
            return new StarModel(model);
        }
        if (mArity == '?') {
            return new OptionalModel(model);
        }
        if (mArity == '+') {
            return new ConcatModel(model,
                                   new StarModel(model.cloneModel()));
        }
        return model;
    }

    private ModelNode rewrite(ContentSpec[] specs, int first, int last)
    {
        // 3 or less, can convert and create; 4 or more, need to recurse:
        int count = last - first;
        if (count > 3) {
            int mid = (last + first + 1) >> 1;
            return new ConcatModel(rewrite(specs, first, mid),
                                   rewrite(specs, mid, last));
        }
        ConcatModel model = new ConcatModel(specs[first].rewrite(),
                                            specs[first+1].rewrite());
        if (count == 3) {
            model = new ConcatModel(model, specs[first+2].rewrite());
        }
        return model;
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append('(');

        for (int i = 0; i < mContentSpecs.length; ++i) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(mContentSpecs[i].toString());
        }
        sb.append(')');

        if (mArity != ' ') {
            sb.append(mArity);
        }
        return sb.toString();
    }

    /*
    ///////////////////////////////////////////////////
    // Validator class that can be used for simple
    // choices (including mixed content)
    ///////////////////////////////////////////////////
     */

    /**
     * Simple validator that can be used if all components of a sequence
     * are leaf nodes, ie. elements with no explicit arity modifiers.
     */
    final static class Validator
        extends StructValidator
    {
        final char mArity;
        final PrefixedName[] mNames;

        /**
         * Number of full repetitions done over the sequence
         */
        int mRounds = 0;

        /**
         * Expected next element in the sequence
         */
        int mStep = 0;

        public Validator(char arity, PrefixedName[] names)
        {
            mArity = arity;
            mNames = names;
        }


        /**
         * Sequence content specification is always stateful; can not
         * use a shared instance... so let's create new instance:
         */
        public StructValidator newInstance() {
            return new Validator(mArity, mNames);
        }

        public String tryToValidate(PrefixedName elemName)
        {
            // First; have we already done that max. 1 sequence?
            if (mStep == 0 && mRounds == 1) {
                if (mArity == '?' || mArity == ' ') {
                    return "was not expecting any more elements in the sequence ("
                        +concatNames(mNames)+")";
                }
            }

            PrefixedName next = mNames[mStep];
            if (!elemName.equals(next)) {
                return expElem(mStep);
            }
            if (++mStep == mNames.length) {
                ++mRounds;
                mStep = 0;
            }
            return null;
        }
        
        public String fullyValid()
        {
            if (mStep != 0) {
                return expElem(mStep)+"; got end element";
            }

            switch (mArity) {
            case '*':
            case '?':
                return null;
            case '+': // need at least one (and multiples checked earlier)
            case ' ':
                if (mRounds > 0) {
                    return null;
                }
                return "Expected sequence ("+concatNames(mNames)+"); got end element";
            }
            // should never happen:
            throw new IllegalStateException("Internal error");
        }

        private String expElem(int step)
        {
            return "expected element <"+mNames[step]+"> in sequence ("
                +concatNames(mNames)+")";
        }

        final static String concatNames(PrefixedName[] names)
        {
            StringBuilder sb = new StringBuilder();
            for (int i = 0, len = names.length; i < len; ++i) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(names[i].toString());
            }
            return sb.toString();
        }
    }
}
