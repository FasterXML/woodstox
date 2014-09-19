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

import java.text.MessageFormat;
import java.util.*;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.validation.*;

import com.ctc.wstx.api.WstxInputProperties;
import com.ctc.wstx.ent.EntityDecl;
import com.ctc.wstx.sr.NsDefaultProvider;
import com.ctc.wstx.sr.InputElementStack;
import com.ctc.wstx.util.DataUtil;
import com.ctc.wstx.util.ElementIdMap;
import com.ctc.wstx.util.ExceptionUtil;
import com.ctc.wstx.util.PrefixedName;

/**
 * Shared abstract base class for Woodstox implementations
 * of {@link XMLValidator} for DTD validation.
 * Since there are 2 sub-types -- full actual DTD validator, and a dummy
 * one that only adds type information and default values, with no actual
 * validation -- common functionality was refactored into this base
 * class.
 */
public abstract class DTDValidatorBase
    extends XMLValidator
    implements NsDefaultProvider // for namespace attr defaults
{
	protected final static HashMap<PrefixedName,DTDAttribute> NO_ATTRS = new HashMap<PrefixedName,DTDAttribute>();
	 
	/*
    /////////////////////////////////////////////////////
    // Constants
    /////////////////////////////////////////////////////
     */

    /**
     * Estimated maximum depth of typical documents; used to allocate
     * the array for element stack
     */
    final static int DEFAULT_STACK_SIZE = 16;

    /**
     * Estimated maximum number of attributes for a single element
     */
    final static int EXP_MAX_ATTRS = 16;

    /**
     * Let's actually just reuse a local Map...
     */
    protected final static HashMap<String,EntityDecl> EMPTY_MAP = new HashMap<String,EntityDecl>();

    /*
    ///////////////////////////////////////
    // Configuration
    ///////////////////////////////////////
    */

    /**
     * Flag that indicates whether any of the elements declared has default
     * attribute values for namespace declaration pseudo-attributes.
     */
    final boolean mHasNsDefaults;

    /**
     * DTD schema ({@link DTDSubsetImpl}) object that created this validator
     * instance.
     */
    final DTDSubset mSchema;

    /**
     * Validation context (owner) for this validator. Needed for adding
     * default attribute values, for example.
     */
    final ValidationContext mContext;

    /**
     * Map that contains element specifications from DTD; null if no
     * DOCTYPE declaration found.
     */
    final Map<PrefixedName,DTDElement> mElemSpecs;

    /**
     * General entities defined in DTD subsets; needed for validating
     * ENTITY/ENTITIES attributes.
     */
    final Map<String,EntityDecl> mGeneralEntities;

    /**
     * Flag that indicates whether parser wants the attribute values
     * to be normalized (according to XML specs) or not (which may be
     * more efficient, although not compliant with the specs)
     */
    protected boolean mNormAttrs;

    /*
    ///////////////////////////////////////////
    // Element def/spec/validator stack, state
    ///////////////////////////////////////////
    */

    /**
     * This is the element that is currently being validated; valid
     * during
     * <code>validateElementStart</code>,
     * <code>validateAttribute</code>,
     * <code>validateElementAndAttributes</code> calls.
     */
    protected DTDElement mCurrElem = null;

    /**
     * Stack of element definitions matching the current active element stack.
     * Instances are elements definitions read from DTD.
     */
    protected DTDElement[] mElems = null;

    /**
     * Number of elements in {@link #mElems}.
     */
    protected int mElemCount = 0;

    /**
     * Attribute definitions for attributes the current element may have
     */
    protected HashMap<PrefixedName,DTDAttribute> mCurrAttrDefs = null;

    /**
     * List of attribute declarations/specifications, one for each
     * attribute of the current element, for which there is a matching
     * value (either explicitly defined, or assigned via defaulting).
     */
    protected DTDAttribute[] mAttrSpecs = new DTDAttribute[EXP_MAX_ATTRS];

    /**
     * Number of attribute specification Objects in
     * {@link #mAttrSpecs}; needed to store in case type information
     * is requested later on.
     */
    protected int mAttrCount = 0;

    /**
     * Index of the attribute of type ID, within current element's
     * attribute list. Track of this is kept separate from other
     * attribute since id attributes often need to be used for resolving
     * cross-references.
     */
    protected int mIdAttrIndex = -1;

    /*
    ///////////////////////////////////////
    // Temporary helper objects
    ///////////////////////////////////////
    */

    protected final transient PrefixedName mTmpKey = new PrefixedName(null, null);

    /**
     * Temporary buffer attribute instances can share for validation
     * purposes
     */
    char[] mTmpAttrValueBuffer = null;

    /*
    ///////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////
    */

    public DTDValidatorBase(DTDSubset schema, ValidationContext ctxt, boolean hasNsDefaults,
                            Map<PrefixedName,DTDElement> elemSpecs, Map<String,EntityDecl> genEntities)
    {
        mSchema = schema;
        mContext = ctxt;
        mHasNsDefaults = hasNsDefaults;
        if (elemSpecs == null || elemSpecs.size() == 0) {
            mElemSpecs = Collections.emptyMap();
        } else {
            mElemSpecs = elemSpecs;
        }
        mGeneralEntities = genEntities;
        // By default, let's assume attrs are to be normalized (fully xml compliant)
        mNormAttrs = true;
        mElems = new DTDElement[DEFAULT_STACK_SIZE];
    }

    /*
    ///////////////////////////////////////
    // Configuration
    ///////////////////////////////////////
    */

    /**
     * Method that allows enabling/disabling attribute value normalization.
     * In general, readers by default enable normalization (to be fully xml
     * compliant),
     * whereas writers do not (since there is usually little to gain, if
     * anything -- it is even possible value may be written before validation
     * is called in some cases)
     */
    public void setAttrValueNormalization(boolean state) {
        mNormAttrs = state;
    }

    /**
     * @return True for validator object that actually do validate
     *   content; false for objects that only use DTD type information.
     */
    public abstract boolean reallyValidating();

    /*
    ///////////////////////////////////////
    // XMLValidator implementation
    ///////////////////////////////////////
    */

    public final XMLValidationSchema getSchema() {
        return mSchema;
    }

    /**
     * Method called to update information about the newly encountered (start)
     * element. At this point namespace information has been resolved, but
     * no DTD validation has been done. Validator is to do these validations,
     * including checking for attribute value (and existence) compatibility.
     */
    public abstract void validateElementStart(String localName, String uri, String prefix)
        throws XMLStreamException;

    public abstract String validateAttribute(String localName, String uri,
                                             String prefix, String value)
        throws XMLStreamException;

    public abstract String validateAttribute(String localName, String uri,
                                    String prefix,
                                    char[] valueChars, int valueStart,
                                    int valueEnd)
        throws XMLStreamException;
    
    public abstract int validateElementAndAttributes()
        throws XMLStreamException;

    /**
     * @return Validation state that should be effective for the parent
     *   element state
     */
    public abstract int validateElementEnd(String localName, String uri, String prefix)
        throws XMLStreamException;

    public void validateText(String text, boolean lastTextSegment)
        throws XMLStreamException
    {
        /* This method is a NOP, since basic DTD has no mechanism for
         * validating textual content.
         */
    }

    public void validateText(char[] cbuf, int textStart, int textEnd,
                             boolean lastTextSegment)
        throws XMLStreamException
    {
        /* This method is a NOP, since basic DTD has no mechanism for
         * validating textual content.
         */
    }

    public abstract void validationCompleted(boolean eod)
        throws XMLStreamException;

    /*
    ///////////////////////////////////////
    // Attribute info access
    ///////////////////////////////////////
    */

    // // // Access to type info

    public String getAttributeType(int index)
    {
        DTDAttribute attr = mAttrSpecs[index];
        return (attr == null) ? WstxInputProperties.UNKNOWN_ATTR_TYPE : 
            attr.getValueTypeString();
    }    

    /**
     * Method for finding out the index of the attribute (collected using
     * the attribute collector; having DTD-derived info in same order)
     * that is of type ID. DTD explicitly specifies that at most one
     * attribute can have this type for any element.
     * 
     * @return Index of the attribute with type ID, in the current
     *    element, if one exists: -1 otherwise
     */
    public int getIdAttrIndex()
    {
        // Let's figure out the index only when needed
        int ix = mIdAttrIndex;
        if (ix == -2) {
            ix = -1;
            if (mCurrElem != null) {
                DTDAttribute idAttr = mCurrElem.getIdAttribute();
                if (idAttr != null) {
                    DTDAttribute[] attrs = mAttrSpecs;
                    for (int i = 0, len = attrs.length; i < len; ++i) {
                        if (attrs[i] == idAttr) {
                            ix = i;
                            break;
                        }
                    }
                }
            }
            mIdAttrIndex = ix;
        }
        return ix;
    }

    /**
     * Method for finding out the index of the attribute (collected using
     * the attribute collector; having DTD-derived info in same order)
     * that is of type NOTATION. DTD explicitly specifies that at most one
     * attribute can have this type for any element.
     * 
     * @return Index of the attribute with type NOTATION, in the current
     *    element, if one exists: -1 otherwise
     */
    public int getNotationAttrIndex()
    {
        /* If necessary, we could find this index when resolving the
         * element, could avoid linear search. But who knows how often
         * it's really needed...
         */
        for (int i = 0, len = mAttrCount; i < len; ++i) {
            if (mAttrSpecs[i].typeIsNotation()) {
                return i;
            }
        }
        return -1;
    }

    /*
    /////////////////////////////////////////////////////
    // NsDefaultProvider interface
    /////////////////////////////////////////////////////
     */

    /**
     * Calling this method before {@link #checkNsDefaults} is necessary
     * to pass information regarding the current element; although
     * it will become available later on (via normal XMLValidator interface),
     * that's too late (after namespace binding and resolving).
     */
    public boolean mayHaveNsDefaults(String elemPrefix, String elemLN)
    {
        mTmpKey.reset(elemPrefix, elemLN);
        DTDElement elem = mElemSpecs.get(mTmpKey);
        mCurrElem = elem;
        return (elem != null) && elem.hasNsDefaults();
    }

    public void checkNsDefaults(InputElementStack nsStack)
        throws XMLStreamException
    {
        // We only get called if mCurrElem != null, and has defaults
        HashMap<String,DTDAttribute> m = mCurrElem.getNsDefaults();
        if (m != null) {
        	for (Map.Entry<String,DTDAttribute> me : m.entrySet()) {
                String prefix = me.getKey();
                if (!nsStack.isPrefixLocallyDeclared(prefix)) {
                    DTDAttribute attr = me.getValue();
                    String uri = attr.getDefaultValue(mContext, this);
                    nsStack.addNsBinding(prefix, uri);
                }
            }
        }
    }

    /*
    ///////////////////////////////////////
    // Package methods, accessors
    ///////////////////////////////////////
    */

    /**
     * Name of current element on the top of the element stack.
     */
    PrefixedName getElemName() {
        DTDElement elem = mElems[mElemCount-1];
        return elem.getName();
    }

    Location getLocation() {
        return mContext.getValidationLocation();
    }

    protected abstract ElementIdMap getIdMap();

    Map<String,EntityDecl> getEntityMap() {
        return mGeneralEntities;
    }

    char[] getTempAttrValueBuffer(int neededLength)
    {
        if (mTmpAttrValueBuffer == null
            || mTmpAttrValueBuffer.length < neededLength) {
            int size = (neededLength < 100) ? 100 : neededLength;
            mTmpAttrValueBuffer = new char[size];
        }
        return mTmpAttrValueBuffer;
    }

    public boolean hasNsDefaults() {
        return mHasNsDefaults;
    }

    /*
    ///////////////////////////////////////
    // Package methods, error handling
    ///////////////////////////////////////
    */

    /**
     * Method called to report validity problems; depending on mode, will
     * either throw an exception, or add a problem notification to the
     * list of problems.
     */
    void reportValidationProblem(String msg)
        throws XMLStreamException
    {
        doReportValidationProblem(msg, null);
    }

    void reportValidationProblem(String msg, Location loc)
        throws XMLStreamException
    {
        doReportValidationProblem(msg, loc);
    }

    void reportValidationProblem(String format, Object arg)
        throws XMLStreamException
    {
        doReportValidationProblem(MessageFormat.format(format, new Object[] { arg }),
                        null);
    }

    void reportValidationProblem(String format, Object arg1, Object arg2)
        throws XMLStreamException
    {
        doReportValidationProblem(MessageFormat.format(format, new Object[] { arg1, arg2 }),
                        null);
    }

    /*
    ///////////////////////////////////////
    // Private/sub-class methods
    ///////////////////////////////////////
    */

    protected void doReportValidationProblem(String msg, Location loc)
        throws XMLStreamException
    {
        if (loc == null) {
            loc = getLocation();
        }
        XMLValidationProblem prob = new XMLValidationProblem(loc, msg, XMLValidationProblem.SEVERITY_ERROR);
        prob.setReporter(this);
        mContext.reportProblem(prob);
    }

    protected void doAddDefaultValue(DTDAttribute attr)
        throws XMLStreamException
    {
        /* If we get here, we should have a non-null (possibly empty) default
         * value:
         */
        String def = attr.getDefaultValue(mContext, this);
        if (def == null) {
            ExceptionUtil.throwInternal("null default attribute value");
        }
        PrefixedName an = attr.getName();
        // Ok, do we need to find the URI?
        String prefix = an.getPrefix();
        String uri = "";
        if (prefix != null && prefix.length() > 0) {
            uri = mContext.getNamespaceURI(prefix);
            // Can not map to empty NS!
            if (uri == null || uri.length() == 0) {
                /* Hmmh. This is a weird case where we do have to
                 * throw a validity exception; even though it really
                 * is more a ns-well-formedness error...
                 */
                reportValidationProblem("Unbound namespace prefix \"{0}\" for default attribute \"{1}\"", prefix, attr);
                // May continue if we don't throw errors, just collect them to a list
                uri = "";
            }
        }
        int defIx = mContext.addDefaultAttribute(an.getLocalName(), uri, prefix, def);
        if (defIx < 0) {
            /* 13-Dec-2005, Tatus: Hmmh. For readers this is an error
             *   condition, but writers may just indicate they are not
             *   interested in defaults. So let's let context report
             *   problem(s) if it has any regarding the request.
             */
            // nop
        } else {
            while (defIx >= mAttrSpecs.length) {
                mAttrSpecs = (DTDAttribute[]) DataUtil.growArrayBy50Pct(mAttrSpecs);
            }
            /* Any intervening empty slots? (can happen if other
             * validators add default attributes...)
             */
            while (mAttrCount < defIx) {
                mAttrSpecs[mAttrCount++] = null;
            }
            mAttrSpecs[defIx] = attr;
            mAttrCount = defIx+1;
        }
    }
}
