/* Woodstox XML processor
 *
 * Copyright (c) 2004- Tatu Saloranta, tatu.saloranta@iki.fi
 *
 * Licensed under the License specified in file LICENSE, included with
 * the source code.
 * You may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ctc.wstx.dtd;

import java.util.*;

import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.validation.*;

import com.ctc.wstx.ent.EntityDecl;
import com.ctc.wstx.util.DataUtil;
import com.ctc.wstx.util.ElementIdMap;
import com.ctc.wstx.util.ExceptionUtil;
import com.ctc.wstx.util.PrefixedName;

/**
 * This class is a "non-validating validator"; a validator-like object
 * that handles DTD-based non-validation functionality: determining type
 * information and default values. This instance does NOT implement any
 * actual DTD-validation, and is to be used in DTD-aware non-validating
 * mode.
 */
public class DTDTypingNonValidator
    extends DTDValidatorBase
{
	/*
    ///////////////////////////////////////////
    // Element def/spec/validator stack, state
    ///////////////////////////////////////////
    */

    /**
     * Flag that indicates if current element has any attributes that
     * have default values.
     */
    protected boolean mHasAttrDefaults = false;

    /**
     * Bitset used for keeping track of defaulted attributes for which values
     * have been found. Only non-null when current element does have such
     * attributes
     */
    protected BitSet mCurrDefaultAttrs = null;

    /**
     * Flag that indicates whether any of the attributes is potentially
     * normalizable, and we are in attribute-normalizing mode.
     */
    protected boolean mHasNormalizableAttrs = false;

    /*
    ///////////////////////////////////////
    // Temporary helper objects
    ///////////////////////////////////////
    */

    /**
     * Reusable lazily instantiated BitSet; needed to keep track of
     * 'missing' attributes with default values (normal default, #FIXED).
     */
    BitSet mTmpDefaultAttrs;

    /*
    ///////////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////////
    */

    public DTDTypingNonValidator(DTDSubset schema, ValidationContext ctxt, boolean hasNsDefaults,
                                 Map<PrefixedName,DTDElement> elemSpecs, Map<String,EntityDecl> genEntities)
    {
        super(schema, ctxt, hasNsDefaults, elemSpecs, genEntities);
    }

    /**
     * @return False, since this is not a real validator
     */
    @Override
    public final boolean reallyValidating() { return false; }

    /*
    ///////////////////////////////////////
    // Configuration
    ///////////////////////////////////////
    */

    /**
     * This 'validator' will not normalize any attributes,
     * so let's implement this as no-op.
     */
    @Override
    public void setAttrValueNormalization(boolean state) {
        // nop
    }

    /*
    ///////////////////////////////////////
    // XMLValidator implementation
    ///////////////////////////////////////
    */

    //public XMLValidationSchema getSchema()

    @Override
    public void validateElementStart(String localName, String uri, String prefix)
        throws XMLStreamException
    {
        // Ok, can we find the element definition?
        mTmpKey.reset(prefix, localName);
        DTDElement elem = mElemSpecs.get(mTmpKey);
        // whether it's found or not, let's add a stack frame:
        int elemCount = mElemCount++;
        if (elemCount >= mElems.length) {
            mElems = (DTDElement[]) DataUtil.growArrayBy50Pct(mElems);
        }

        mElems[elemCount] = mCurrElem = elem;
        mAttrCount = 0;
        mIdAttrIndex = -2; // -2 as a "don't know yet" marker

        /* but if not found, can not obtain any type information. Not
         * a validation problem though, since we are doing none...
         * Oh, also, unlike with real validation, not having actual element
         * information is ok; can still have attributes!
         */
        if (elem == null) { // || !elem.isDefined())
            mCurrAttrDefs = NO_ATTRS;
            mHasAttrDefaults = false;
            mCurrDefaultAttrs = null;
            mHasNormalizableAttrs = false;
            return;
        }

        // If element found, does it have any attributes?
        mCurrAttrDefs = elem.getAttributes();
        if (mCurrAttrDefs == null) {
            mCurrAttrDefs = NO_ATTRS;
            mHasAttrDefaults = false;
            mCurrDefaultAttrs = null;
            mHasNormalizableAttrs = false;
            return;
        }

        // Any normalization needed?
        mHasNormalizableAttrs = mNormAttrs || elem.attrsNeedValidation();

        // Any default values?
        mHasAttrDefaults = elem.hasAttrDefaultValues();
        if (mHasAttrDefaults) {
            /* Special count also contains ones with #REQUIRED value, but
             * that's a minor sub-optimality...
             */
            int specCount = elem.getSpecialCount();
            BitSet bs = mTmpDefaultAttrs;
            if (bs == null) {
                mTmpDefaultAttrs = bs = new BitSet(specCount);
            } else {
                bs.clear();
            }
            mCurrDefaultAttrs = bs;
        } else {
            mCurrDefaultAttrs = null;
        }
    }

    @Override
    public String validateAttribute(String localName, String uri,
                                    String prefix, String value)
        throws XMLStreamException
    {
        /* no need to do any validation; however, need to do following:
         *
         * (a) Figure out type info, if any (to get data type, id index etc);
         *     if yes, do:
         *   (1) If attribute has default value, note down it's not needed due
         *     to explicit definition
         *   (2) If attribute is normalizable, normalize it without validation
         */
        DTDAttribute attr = mCurrAttrDefs.get(mTmpKey.reset(prefix, localName));
        int index = mAttrCount++;
        if (index >= mAttrSpecs.length) {
            mAttrSpecs = (DTDAttribute[]) DataUtil.growArrayBy50Pct(mAttrSpecs);
        }
        mAttrSpecs[index] = attr;

        /* Although undeclared attribute would be a validation error,
         * we don't care here... just need to skip it
         */
        if (attr != null) {
            if (mHasAttrDefaults) {
                /* Once again, let's use more generic 'special' index,
                 * even though it also includes #REQUIRED values
                 */
                int specIndex = attr.getSpecialIndex();
                if (specIndex >= 0) {
                    mCurrDefaultAttrs.set(specIndex);
                }
            }
            if (mHasNormalizableAttrs) {
                // !!! TBI
            }
        }
        return null; // fine as is
    }

    @Override
    public String validateAttribute(String localName, String uri,
            String prefix,
            char[] valueChars, int valueStart,
            int valueEnd)
        throws XMLStreamException
    {
        // note: cut'n pasted from above...
        DTDAttribute attr = mCurrAttrDefs.get(mTmpKey.reset(prefix, localName));
        int index = mAttrCount++;
        if (index >= mAttrSpecs.length) {
            mAttrSpecs = (DTDAttribute[]) DataUtil.growArrayBy50Pct(mAttrSpecs);
        }
        mAttrSpecs[index] = attr;
        if (attr != null) {
            if (mHasAttrDefaults) {
                int specIndex = attr.getSpecialIndex();
                if (specIndex >= 0) {
                    mCurrDefaultAttrs.set(specIndex);
                }
            }
            if (mHasNormalizableAttrs) { // may get normalized, after all
                return attr.normalize(this, valueChars, valueStart, valueEnd);
            }
        }
        return null; // fine as is
    }

    @Override
    public int validateElementAndAttributes()
        throws XMLStreamException
    {
        /* Ok; since we are not really validating, we just need to add possible
         * attribute default values, and return "anything goes"
         * as the allowable content:
         */
        DTDElement elem = mCurrElem;
        if (mHasAttrDefaults) {
            BitSet specBits = mCurrDefaultAttrs;
            int specCount = elem.getSpecialCount();
            int ix = specBits.nextClearBit(0);
            while (ix < specCount) { // something amiss!
                List<DTDAttribute> specAttrs = elem.getSpecialAttrs();
                DTDAttribute attr = specAttrs.get(ix);
                if (attr.hasDefaultValue()) { // no default for #REQUIRED...
                    doAddDefaultValue(attr);
                }
                ix = specBits.nextClearBit(ix+1);
            }
        }
        /* However: we should indicate cases where PCDATA is not supposed
         * to occur -- although it won't be considered an error, when not
         * validating, info is needed to determine type of SPACE instead
         * of CHARACTERS. Other validation types are not to be returned,
         * however, since caller doesn't know how to deal with such
         * cases.
         */
        return (elem == null) ? XMLValidator.CONTENT_ALLOW_ANY_TEXT :
            elem.getAllowedContentIfSpace();
    }

    @Override
    public int validateElementEnd(String localName, String uri, String prefix)
        throws XMLStreamException
    {
        /* Since we are not really validating, only need to maintain
         * the element stack, and return "anything goes" as allowable content:
         */
        int ix = --mElemCount;
        mElems[ix] = null;
        if (ix < 1) {
            return XMLValidator.CONTENT_ALLOW_ANY_TEXT;
        }
        DTDElement elem = mElems[ix-1];
        return (elem == null) ? XMLValidator.CONTENT_ALLOW_ANY_TEXT :
            mElems[ix-1].getAllowedContentIfSpace();
    }

    // base class implements these ok:
    //public void validateText(String text, boolean lastTextSegment)
    //public void validateText(char[] cbuf, int textStart, int textEnd, boolean lastTextSegment)

    @Override
    public void validationCompleted(boolean eod)
        //throws XMLStreamException
    {
        // fine, great, nothing to do...
    }

    /*
    ///////////////////////////////////////
    // Package methods, accessors
    ///////////////////////////////////////
    */

    @Override
    protected ElementIdMap getIdMap()
    {
        /* should never be called; for now let's throw an exception, if it
         * turns out it does get called can/should return an empty immutable
         * map or something
         */
        ExceptionUtil.throwGenericInternal();
        return null; // never gets here
    }

}
