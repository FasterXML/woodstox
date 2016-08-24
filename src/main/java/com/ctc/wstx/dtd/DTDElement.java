/* Woodstox XML processor
 *
 * Copyright (c) 2004- Tatu Saloranta, tatu.saloranta@iki.fi
 *
 * Licensed under the License specified in the file LICENSE which is
 * included with the source code.
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

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.validation.XMLValidator;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.cfg.ErrorConsts;
import com.ctc.wstx.sr.InputProblemReporter;
import com.ctc.wstx.util.ExceptionUtil;
import com.ctc.wstx.util.PrefixedName;
import com.ctc.wstx.util.WordResolver;

/**
 * Class that contains element definitions from DTD.
 *<p>
 * Notes about thread-safety: this class is not thread-safe, since it does
 * not have to be, in general case. That is, the only instances that can
 * be shared are external subset instances, and those are used in read-only
 * manner (with the exception of temporary arrays constructed on-demand).
 */
public final class DTDElement
{

    /*
    ///////////////////////////////////////////////////
    // Information about the element itself
    ///////////////////////////////////////////////////
     */

    final PrefixedName mName;

    /**
     * Location of the (real) definition of the element; may be null for
     * placeholder elements created to hold ATTLIST definitions
     */
    final Location mLocation;

    /**
     * Base validator object for validating content model of this element;
     * may be null for some simple content models (ANY, EMPTY).
     */
    StructValidator mValidator;

    int mAllowedContent;

    /**
     * True if the DTD was parsed (and is to be used) in namespace-aware
     * mode.
     * Affects (name) validation amongst other things.
     */
    final boolean mNsAware;

    /**
     * True if the DTD was parsed in xml1.1 compliant mode (referenced to
     * from an xml 1.1 document).
     * Affects (name) validation amongst other things.
     */
    final boolean mXml11;

    /*
    ///////////////////////////////////////////////////
    // Attribute info
    ///////////////////////////////////////////////////
     */

    HashMap<PrefixedName,DTDAttribute> mAttrMap = null;

    /**
     * Ordered list of attributes that have 'special' properties (attribute
     * is required, has a default value [regular or fixed]); these attributes
     * have to be specifically checked after actual values have been resolved.
     */
    ArrayList<DTDAttribute> mSpecAttrList = null;

    boolean mAnyFixed = false;

    /**
     * Flag set to true if there are any attributes that have either
     * basic default value, or #FIXED default value.
     */
    boolean mAnyDefaults = false;

    /**
     * Flag that is set to true if there is at least one attribute that
     * has type that requires normalization and/or validation; that is,
     * is of some other type than CDATA.
     */
    boolean mValidateAttrs = false;

    /**
     * Id attribute instance, if one already declared for this element;
     * can only have up to one such attribute per element.
     */
    DTDAttribute mIdAttr;

    /**
     * Notation attribute instance, if one already declared for this element;
     * can only have up to one such attribute per element.
     */
    DTDAttribute mNotationAttr;

    // // // !! If you add new attributes, make sure they get copied
    // // // in #define() method !!

    /*
    ///////////////////////////////////////////////////
    // Namespace declaration defaulting...
    ///////////////////////////////////////////////////
     */

    /**
     * Set of namespace declarations with default values, if any
     * (regular ns pseudo-attr declarations are just ignored)
     */
    HashMap<String,DTDAttribute> mNsDefaults = null;

    /*
    ///////////////////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////////////////
     */

    private DTDElement(Location loc, PrefixedName name,
                       StructValidator val, int allowedContent,
                       boolean nsAware, boolean xml11)
    {
        mName = name;
        mLocation = loc;
        mValidator = val;
        mAllowedContent = allowedContent;
        mNsAware = nsAware;
        mXml11 = xml11;
    }

    /**
     * Method called to create an actual element definition, matching
     * an ELEMENT directive in a DTD subset.
     */
    public static DTDElement createDefined(ReaderConfig cfg, Location loc, PrefixedName name,
                                           StructValidator val, int allowedContent)
    {
        if (allowedContent == XMLValidator.CONTENT_ALLOW_UNDEFINED) { // sanity check
            ExceptionUtil.throwInternal("trying to use XMLValidator.CONTENT_ALLOW_UNDEFINED via createDefined()");
        }
        return new DTDElement(loc, name, val, allowedContent,
                              cfg.willSupportNamespaces(), cfg.isXml11());
    }

    /**
     * Method called to create a "placeholder" element definition, needed to
     * contain attribute definitions.
     */
    public static DTDElement createPlaceholder(ReaderConfig cfg, Location loc, PrefixedName name)
    {
        return new DTDElement(loc, name, null, XMLValidator.CONTENT_ALLOW_UNDEFINED,
                              cfg.willSupportNamespaces(), cfg.isXml11());
    }
        
    /**
     * Method called on placeholder element, to create a real instance that
     * has all attribute definitions placeholder had (it'll always have at
     * least one -- otherwise no placeholder was needed).
     */
    public DTDElement define(Location loc, StructValidator val,
                             int allowedContent)
    {
        verifyUndefined();
        if (allowedContent == XMLValidator.CONTENT_ALLOW_UNDEFINED) { // sanity check
            ExceptionUtil.throwInternal("trying to use CONTENT_ALLOW_UNDEFINED via define()");
        }

        DTDElement elem = new DTDElement(loc, mName, val, allowedContent,
                                         mNsAware, mXml11);

        // Ok, need to copy state collected so far:
        elem.mAttrMap = mAttrMap;
        elem.mSpecAttrList = mSpecAttrList;
        elem.mAnyFixed = mAnyFixed;
        elem.mValidateAttrs = mValidateAttrs;
        elem.mAnyDefaults = mAnyDefaults;
        elem.mIdAttr = mIdAttr;
        elem.mNotationAttr = mNotationAttr;
        elem.mNsDefaults = mNsDefaults;

        return elem;
    }

    /**
     * Method called to "upgrade" a placeholder using a defined element,
     * including adding attributes.
     */
    public void defineFrom(InputProblemReporter rep, DTDElement definedElem,
                           boolean fullyValidate)
        throws XMLStreamException
    {
        if (fullyValidate) {
            verifyUndefined();
        }
        mValidator = definedElem.mValidator;
        mAllowedContent = definedElem.mAllowedContent;
        mergeMissingAttributesFrom(rep, definedElem, fullyValidate);
    }

    private void verifyUndefined()
    {
        if (mAllowedContent != XMLValidator.CONTENT_ALLOW_UNDEFINED) { // sanity check
            ExceptionUtil.throwInternal("redefining defined element spec");
        }
    }

    /**
     * Method called by DTD parser when it has read information about
     * an attribute that belong to this element
     *
     * @return Newly created attribute Object if the attribute definition was
     *   added (hadn't been declared yet); null if it's a duplicate, in which
     *   case original definition sticks.
     */
    public DTDAttribute addAttribute(InputProblemReporter rep,
                                     PrefixedName attrName, int valueType,
                                     DefaultAttrValue defValue, WordResolver enumValues,
                                     boolean fullyValidate)
        throws XMLStreamException
    {
        HashMap<PrefixedName,DTDAttribute> m = mAttrMap;
        if (m == null) {
            mAttrMap = m = new HashMap<PrefixedName,DTDAttribute>();
        }

        List<DTDAttribute> specList = defValue.isSpecial() ? getSpecialList() : null;

        DTDAttribute attr;
        int specIndex = (specList == null) ? -1 : specList.size();

        switch (valueType) {
        case DTDAttribute.TYPE_CDATA:
            attr = new DTDCdataAttr(attrName, defValue, specIndex, mNsAware, mXml11);
            break;

        case DTDAttribute.TYPE_ENUMERATED:
            attr = new DTDEnumAttr(attrName, defValue, specIndex, mNsAware, mXml11, enumValues);
            break;

        case DTDAttribute.TYPE_ID:
            /* note: although ID attributes are not to have default value,
             * this is 'only' a validity constraint, and in dtd-aware-but-
             * not-validating mode it is apparently 'legal' to add default
             * values. Bleech.
             */
            attr = new DTDIdAttr(attrName, defValue, specIndex, mNsAware, mXml11);
            break;

        case DTDAttribute.TYPE_IDREF:
            attr = new DTDIdRefAttr(attrName, defValue, specIndex, mNsAware, mXml11);
            break;

        case DTDAttribute.TYPE_IDREFS:
            attr = new DTDIdRefsAttr(attrName, defValue, specIndex, mNsAware, mXml11);
            break;

        case DTDAttribute.TYPE_ENTITY:
            attr = new DTDEntityAttr(attrName, defValue, specIndex, mNsAware, mXml11);
            break;

        case DTDAttribute.TYPE_ENTITIES:
            attr = new DTDEntitiesAttr(attrName, defValue, specIndex, mNsAware, mXml11);
            break;

        case DTDAttribute.TYPE_NOTATION:
            attr = new DTDNotationAttr(attrName, defValue, specIndex, mNsAware, mXml11, enumValues);
            break;
        
        case DTDAttribute.TYPE_NMTOKEN:
            attr = new DTDNmTokenAttr(attrName, defValue, specIndex, mNsAware, mXml11);
            break;

        case DTDAttribute.TYPE_NMTOKENS:
            attr = new DTDNmTokensAttr(attrName, defValue, specIndex, mNsAware, mXml11);
            break;

        default:
            // 18-Jan-2006, TSa: should never get here...
            ExceptionUtil.throwGenericInternal();
            attr = null; // unreachable, but compiler wants it
        }

        DTDAttribute old = doAddAttribute(m, rep, attr, specList, fullyValidate);
        return (old == null) ? attr : null;
    }

    /**
     * Method called to add a definition of a namespace-declaration
     * pseudo-attribute with a default value.
     *
     * @param rep Reporter to use to report non-fatal problems
     * @param fullyValidate Whether this is being invoked for actual DTD validation,
     *   or just the "typing non-validator"
     *
     * @return Attribute that acts as the placeholder, if the declaration
     *   was added; null to indicate it
     *   was a dup (there was an earlier declaration)
     */
    public DTDAttribute addNsDefault
        (InputProblemReporter rep, PrefixedName attrName, int valueType,
         DefaultAttrValue defValue, boolean fullyValidate)
        throws XMLStreamException
    {
        /* Let's simplify handling a bit: although theoretically all
         * combinations of value can be used, let's really only differentiate
         * between CDATA and 'other' (for which let's use NMTOKEN)
         */
        DTDAttribute nsAttr;

        switch (valueType) {
        case DTDAttribute.TYPE_CDATA:
            nsAttr = new DTDCdataAttr(attrName, defValue, -1, mNsAware, mXml11);
            break;
        default: // something else, default to NMTOKEN then
            nsAttr = new DTDNmTokenAttr(attrName, defValue, -1, mNsAware, mXml11);
            break;
        }

        // Ok. So which prefix are we to bind? Need to access by prefix...
        String prefix = attrName.getPrefix();
        if (prefix == null || prefix.length() == 0) { // defult NS -> ""
            prefix = "";
        } else { // non-default, use the local name
            prefix = attrName.getLocalName();
        }

        if (mNsDefaults == null) {
            mNsDefaults = new HashMap<String,DTDAttribute>();
        } else {
            if (mNsDefaults.containsKey(prefix)) {
                return null;
            }
        }
        mNsDefaults.put(prefix, nsAttr);
        return nsAttr;
    }

    public void mergeMissingAttributesFrom(InputProblemReporter rep, DTDElement other,
                                           boolean fullyValidate)
        throws XMLStreamException
    {
        Map<PrefixedName,DTDAttribute> otherMap = other.getAttributes();
        HashMap<PrefixedName,DTDAttribute> m = mAttrMap;
        if (m == null) {
            mAttrMap = m = new HashMap<PrefixedName,DTDAttribute>();
        }

        //boolean anyAdded = false;
        
        if (otherMap != null && otherMap.size() > 0) {
        	for (Map.Entry<PrefixedName,DTDAttribute> me : otherMap.entrySet()) {
                PrefixedName key = me.getKey();
                // Should only add if no such attribute exists...
                if (!m.containsKey(key)) {
                    // can only use as is, if it's not a special attr
                    DTDAttribute newAttr = me.getValue();
                    List<DTDAttribute> specList;
                    // otherwise need to clone
                    if (newAttr.isSpecial()) {
                        specList = getSpecialList();
                        newAttr = newAttr.cloneWith(specList.size());
                    } else {
                        specList = null;
                    }
                    doAddAttribute(m, rep, newAttr, specList, fullyValidate);
                }
            }
        }

        HashMap<String,DTDAttribute> otherNs = other.mNsDefaults;
        if (otherNs != null) {
            if (mNsDefaults == null) {
                mNsDefaults = new HashMap<String,DTDAttribute>();
            }
            for (Map.Entry<String, DTDAttribute> en : otherNs.entrySet()) {
                String prefix = en.getKey();
                // Should only add if no such attribute exists...
                if (!mNsDefaults.containsKey(prefix)) {
                    mNsDefaults.put(prefix, en.getValue());
                }
            }
        }
    }

    /**
     * @return Earlier declaration of the attribute, if any; null if
     *    this was a new attribute
     */
    private DTDAttribute doAddAttribute(Map<PrefixedName,DTDAttribute> attrMap, InputProblemReporter rep,
                                        DTDAttribute attr, List<DTDAttribute> specList,
                                        boolean fullyValidate)
        throws XMLStreamException
    {
        PrefixedName attrName = attr.getName();

        // Maybe we already have it? If so, need to ignore
        DTDAttribute old = attrMap.get(attrName);
        if (old != null) {
            rep.reportProblem(null, ErrorConsts.WT_ATTR_DECL, ErrorConsts.W_DTD_DUP_ATTR,
                              attrName, mName);
            return old;
        }

        switch (attr.getValueType()) {
        case DTDAttribute.TYPE_ID:
            // Only one such attribute per element (Specs, 1.0#3.3.1)
            if (fullyValidate && mIdAttr != null) {
                rep.throwParseError("Invalid id attribute \"{0}\" for element <{1}>: already had id attribute \""+mIdAttr.getName()+"\"", attrName, mName);
            }
            mIdAttr = attr;
            break;

        case DTDAttribute.TYPE_NOTATION:
            // Only one such attribute per element (Specs, 1.0#3.3.1)
            if (fullyValidate && mNotationAttr != null) {
                rep.throwParseError("Invalid notation attribute '"+attrName+"' for element <"+mName+">: already had notation attribute '"+mNotationAttr.getName()+"'");
            }
            mNotationAttr = attr;
            break;
        }

        attrMap.put(attrName, attr);
        if (specList != null) {
            specList.add(attr);
        }
        if (!mAnyFixed) {
            mAnyFixed = attr.isFixed();
        }
        if (!mValidateAttrs) {
            mValidateAttrs = attr.needsValidation();
        }
        if (!mAnyDefaults) {
            mAnyDefaults = attr.hasDefaultValue();
        }

        return null;
    }

    /*
    ///////////////////////////////////////////////////
    // Public API, accessors:
    ///////////////////////////////////////////////////
     */

    public PrefixedName getName() { return mName; }

    @Override
    public String toString() {
        return mName.toString();
    }

    public String getDisplayName() {
        return mName.toString();
    }

    public Location getLocation() { return mLocation; }

    public boolean isDefined() {
        return (mAllowedContent != XMLValidator.CONTENT_ALLOW_UNDEFINED);
    }

    /**
     * @return Constant that identifies what kind of nodes are in general
     *    allowed inside this element.
     */
    public int getAllowedContent() {
        return mAllowedContent;
    }

    /**
     * Specialized accessor used by non-validating but typing 'validator':
     * essentially, used to figure out whether #PCDATA is allowed or not;
     * and based on that, return one of 2 allowable text values (only
     * space, or anything). This is the relevant subset in non-validating
     * modes, needed to properly type resulting character events.
     */
    public int getAllowedContentIfSpace()
    {
        int vld = mAllowedContent;
        return (vld <= XMLValidator.CONTENT_ALLOW_WS) ?
            XMLValidator.CONTENT_ALLOW_WS_NONSTRICT :
            XMLValidator.CONTENT_ALLOW_ANY_TEXT;
    }

    public HashMap<PrefixedName,DTDAttribute> getAttributes() {
        return mAttrMap;
    }

    public int getSpecialCount() {
        return (mSpecAttrList == null) ? 0 : mSpecAttrList.size();
    }

    public List<DTDAttribute> getSpecialAttrs() {
        return mSpecAttrList;
    }

    /**
     * @return True if at least one of the attributes has type other than
     *   CDATA; false if not
     */
    public boolean attrsNeedValidation() {
        return mValidateAttrs;
    }

    public boolean hasFixedAttrs() {
        return mAnyFixed;
    }

    public boolean hasAttrDefaultValues() {
        return mAnyDefaults;
    }

    public DTDAttribute getIdAttribute() {
        return mIdAttr;
    }

    public DTDAttribute getNotationAttribute() {
        return mNotationAttr;
    }

    public boolean hasNsDefaults() {
        return (mNsDefaults != null);
    }

    /*
    ///////////////////////////////////////////////////
    // Public API, factory methods:
    ///////////////////////////////////////////////////
     */

    public StructValidator getValidator()
    {
        return (mValidator == null) ? null : mValidator.newInstance();
    }

    protected HashMap<String,DTDAttribute> getNsDefaults() {
        return mNsDefaults;
    }

    /*
    ///////////////////////////////////////////////////
    // Internal methods
    ///////////////////////////////////////////////////
     */

    private List<DTDAttribute> getSpecialList()
    {
        ArrayList<DTDAttribute> l = mSpecAttrList;
        if (l == null) {
            mSpecAttrList = l = new ArrayList<DTDAttribute>();
        }
        return l;
    }
}
