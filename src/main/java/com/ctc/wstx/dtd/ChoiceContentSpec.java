package com.ctc.wstx.dtd;

import java.util.*;

import com.ctc.wstx.util.ExceptionUtil;
import com.ctc.wstx.util.PrefixedName;

/**
 * Content specification that defines content model that has
 * multiple alternative elements; including mixed content model.
 */
public class ChoiceContentSpec
    extends ContentSpec
{
    final boolean mNsAware;

    /**
     * Whether this is a mixed content model; mostly affects String
     * representation
     */
    final boolean mHasMixed;

    final ContentSpec[] mContentSpecs;

    /*
    ///////////////////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////////////////
     */

    private ChoiceContentSpec(boolean nsAware, char arity, boolean mixed,
                              ContentSpec[] specs)
    {
        super(arity);
        mNsAware = nsAware;
        mHasMixed = mixed;
        mContentSpecs = specs;
    }

    private ChoiceContentSpec(boolean nsAware, char arity, boolean mixed, Collection<ContentSpec> specs)
    {
        super(arity);
        mNsAware = nsAware;
        mHasMixed = mixed;
        mContentSpecs = new ContentSpec[specs.size()];
        specs.toArray(mContentSpecs);
    }

    public static ChoiceContentSpec constructChoice(boolean nsAware, char arity,
                                                    Collection<ContentSpec> specs)
    {
        return new ChoiceContentSpec(nsAware, arity, false, specs);
    }

    public static ChoiceContentSpec constructMixed(boolean nsAware, Collection<ContentSpec> specs)
    {
        return new ChoiceContentSpec(nsAware, '*', true, specs);
    }

    /*
    ///////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////
     */

    public StructValidator getSimpleValidator()
    {
        /* Can we create a simple validator? Yes, if the sub-specs are
         * all simple (leaves == element tokens with no arity modifier);
         * this is always true for mixed.
         */
        ContentSpec[] specs = mContentSpecs;
        int len = specs.length;
        int i;

        if (mHasMixed) {
            i = len;
        } else {
            i = 0;
            for (; i < len; ++i) {
                if (!specs[i].isLeaf()) {
                    break;
                }
            }
        }

        if (i == len) { // all leaves, kewl
            PrefixedNameSet keyset = namesetFromSpecs(mNsAware, specs);
            return new Validator(mArity, keyset);
        }

        // Nah, need a DFA...
        return null;
    }

    public ModelNode rewrite()
    {
        // First, need to convert sub-specs:
        ContentSpec[] specs = mContentSpecs;
        int len = specs.length;
        ModelNode[] models = new ModelNode[len];
        for (int i = 0; i < len; ++i) {
            models[i] = specs[i].rewrite();
        }
        ChoiceModel model = new ChoiceModel(models);

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

    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        if (mHasMixed) {
            sb.append("(#PCDATA | ");
        } else {
            sb.append('(');
        }
        for (int i = 0; i < mContentSpecs.length; ++i) {
            if (i > 0) {
                sb.append(" | ");
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
    // Internal methods
    ///////////////////////////////////////////////////
     */

    /*
    ///////////////////////////////////////////////////
    // Package methods
    ///////////////////////////////////////////////////
     */

    protected static PrefixedNameSet namesetFromSpecs(boolean nsAware, ContentSpec[] specs)
    {
        int len = specs.length;
        PrefixedName[] nameArray = new PrefixedName[len];
        for (int i = 0; i < len; ++i) {
            nameArray[i] = ((TokenContentSpec)specs[i]).getName();
        }

        if (len < 5) { // 4 or fewer elements -> small
            return new SmallPrefixedNameSet(nsAware, nameArray);
        }
        return new LargePrefixedNameSet(nsAware, nameArray);
    }

    /*
    ///////////////////////////////////////////////////
    // Validator class that can be used for simple
    // choices (including mixed content)
    ///////////////////////////////////////////////////
     */

    final static class Validator
        extends StructValidator
    {
        final char mArity;
        final PrefixedNameSet mNames;

        int mCount = 0;

        public Validator(char arity, PrefixedNameSet names)
        {
            mArity = arity;
            mNames = names;
        }

        /**
         * Rules for reuse are simple: if we can have any number of
         * repetitions, we can just use a shared root instance. Although
         * its count variable will get updated this doesn't really
         * matter as it won't be used. Otherwise a new instance has to
         * be created always, to keep track of instance counts.
         */
        public StructValidator newInstance() {
            return (mArity == '*') ? this : new Validator(mArity, mNames);
        }

        public String tryToValidate(PrefixedName elemName)
        {
            if (!mNames.contains(elemName)) {
                if (mNames.hasMultiple()) {
                    return "Expected one of ("+mNames.toString(" | ")+")";
                }
                return "Expected <"+mNames.toString("")+">";
            }
            if (++mCount > 1 && (mArity == '?' || mArity == ' ')) {
                if (mNames.hasMultiple()) {
                    return "Expected $END (already had one of ["
                        +mNames.toString(" | ")+"]";
                }
                return "Expected $END (already had one <"
                    +mNames.toString("")+">]";
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
                return "Expected "+(mArity == '+' ? "at least" : "")
                    +" one of elements ("+mNames+")";
            }
            // should never happen:
            ExceptionUtil.throwGenericInternal();
            return null;
        }
    }
}
