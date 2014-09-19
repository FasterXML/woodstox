package com.ctc.wstx.dtd;

import com.ctc.wstx.cfg.ErrorConsts;
import com.ctc.wstx.util.PrefixedName;

/**
 * Content specification that defines content model consisting of just
 * one allowed element. In addition to the allowed element, spec can have
 * optional arity ("*", "+", "?") marker.
 */
public class TokenContentSpec
    extends ContentSpec
{
    final static TokenContentSpec sDummy = new TokenContentSpec
        (' ', new PrefixedName("*", "*"));

    final PrefixedName mElemName;

    /*
    ///////////////////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////////////////
     */

    public TokenContentSpec(char arity, PrefixedName elemName)
    {
        super(arity);
        mElemName = elemName;
    }

    public static TokenContentSpec construct(char arity, PrefixedName elemName)
    {
        return new TokenContentSpec(arity, elemName);
    }

    public static TokenContentSpec getDummySpec() {
        return sDummy;
    }
    
    /*
    ///////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////
     */

    public boolean isLeaf() {
        return mArity == ' ';
    }

    public PrefixedName getName() {
        return mElemName;
    }

    public StructValidator getSimpleValidator() {
        return new Validator(mArity, mElemName);
    }

    public ModelNode rewrite() {
        TokenModel model = new TokenModel(mElemName);
        if (mArity == '*') {
            return new StarModel(model);
        }
        if (mArity == '?') {
            return new OptionalModel(model);
        }
        if (mArity == '+') {
            return new ConcatModel(model,
                                   new StarModel(new TokenModel(mElemName)));
        }
        return model;
    }

    public String toString() {
        return (mArity == ' ') ? mElemName.toString()
            : (mElemName.toString() + mArity);
    }

    /*
    ///////////////////////////////////////////////////
    // Validator class:
    ///////////////////////////////////////////////////
     */

    final static class Validator
        extends StructValidator
    {
        final char mArity;
        final PrefixedName mElemName;

        int mCount = 0;

        public Validator(char arity, PrefixedName elemName)
        {
            mArity = arity;
            mElemName = elemName;
        }

        /**
         * Rules for reuse are simple: if we can have any number of
         * repetitions, we can just use a shared root instance. Although
         * its count variable will get updated this doesn't really
         * matter as it won't be used. Otherwise a new instance has to
         * be created always, to keep track of instance counts.
         */
        public StructValidator newInstance() {
            return (mArity == '*') ? this : new Validator(mArity, mElemName);
        }

        public String tryToValidate(PrefixedName elemName)
        {
            if (!elemName.equals(mElemName)) {
                return "Expected element <"+mElemName+">";
            }
            if (++mCount > 1 && (mArity == '?' || mArity == ' ')) {
                return "More than one instance of element <"+mElemName+">";
            }
            return null;
        }
        
        public String fullyValid()
        {
            switch (mArity) {
            case '*':
            case '?':
                return null;
            case '+': // need at least one (and multiples checked earlier)
            case ' ':
                if (mCount > 0) {
                    return null;
                }
                return "Expected "+(mArity == '+' ? "at least one" : "")
                    +" element <"+mElemName+">";
            }
            // should never happen:
            throw new IllegalStateException(ErrorConsts.ERR_INTERNAL);
        }
    }
}
