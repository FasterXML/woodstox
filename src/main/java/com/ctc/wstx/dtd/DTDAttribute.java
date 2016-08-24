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

import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.validation.ValidationContext;
import org.codehaus.stax2.validation.XMLValidator;

import com.ctc.wstx.ent.EntityDecl;
import com.ctc.wstx.io.WstxInputData;
import com.ctc.wstx.sr.InputProblemReporter;
import com.ctc.wstx.util.PrefixedName;
import com.ctc.wstx.util.StringUtil;
import com.ctc.wstx.util.WordResolver;

/**
 * Base class for objects that contain attribute definitions from DTD.
 * Sub-classes exists for specific typed attributes (enumeration-valued,
 * non-CDATA ones); base class itself is used for attributes of type
 * CDATA.
 */
public abstract class DTDAttribute
{
    final static char CHAR_SPACE = (char) 0x0020;

    /*
    ///////////////////////////////////////////////////
    // Type constants
    ///////////////////////////////////////////////////
     */

    // // // Value types

    public final static int TYPE_CDATA = 0; // default...
    public final static int TYPE_ENUMERATED = 1;

    public final static int TYPE_ID = 2;
    public final static int TYPE_IDREF = 3;
    public final static int TYPE_IDREFS = 4;

    public final static int TYPE_ENTITY = 5;
    public final static int TYPE_ENTITIES = 6;

    public final static int TYPE_NOTATION = 7;
    public final static int TYPE_NMTOKEN = 8;
    public final static int TYPE_NMTOKENS = 9;

    /**
     * Array that has String constants matching above mentioned
     * value types
     */
    final static String[] sTypes = new String[] {
        "CDATA",
        /* 05-Feb-2006, TSa: Hmmh. Apparently SAX specs indicate that
         *   enumerated type should be listed as "NMTOKEN"... but most
         *   SAX parsers use ENUMERATED, plus this way application can
         *   distinguish real NMTOKEN from enumerated type.
         */
        /* 26-Nov-2006, TSa: Either way, we can change type to SAX
         *   compatible within SAX classes, not here.
         */
        //"NMTOKEN"
        "ENUMERATED",
        "ID",
        "IDREF",
        "IDREFS",
        "ENTITY",
        "ENTITIES",
        "NOTATION",
        "NMTOKEN",
        "NMTOKENS",
    };

    /*
    ///////////////////////////////////////////////////
    // Information about the attribute itself
    ///////////////////////////////////////////////////
     */

    protected final PrefixedName mName;

    /**
     * Index number amongst "special" attributes (required ones, attributes
     * that have default values), if attribute is one: -1 if not.
     */
    protected final int mSpecialIndex;

    protected final DefaultAttrValue mDefValue;

    protected final boolean mCfgNsAware;
    protected final boolean mCfgXml11;

    /*
    ///////////////////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////////////////
     */

    public DTDAttribute(PrefixedName name, DefaultAttrValue defValue, int specIndex,
                        boolean nsAware, boolean xml11)
    {
        mName = name;
        mDefValue = defValue;
        mSpecialIndex = specIndex;
        mCfgNsAware = nsAware;
        mCfgXml11 = xml11;
    }

    public abstract DTDAttribute cloneWith(int specIndex);

    /*
    ///////////////////////////////////////////////////
    // Public API, accessors
    ///////////////////////////////////////////////////
     */

    public final PrefixedName getName() { return mName; }

    @Override
    public final String toString() {
        return mName.toString();
    }

    public final String getDefaultValue(ValidationContext ctxt, XMLValidator dtd)
        throws XMLStreamException
    {
        String val = mDefValue.getValueIfOk();
        if (val == null) {
            mDefValue.reportUndeclared(ctxt, dtd);
            /* should never get here, but just to be safe, let's use
             * the 'raw' value (one that does not have undeclared entities
             * included, most likely)
             */
            val = mDefValue.getValue();
        }
        return val;
    }

    public final int getSpecialIndex() {
        return mSpecialIndex;
    }

    public final boolean needsValidation() {
        return (getValueType() != TYPE_CDATA);
    }

    public final boolean isFixed() {
        return mDefValue.isFixed();
    }

    public final boolean isRequired() {
        return mDefValue.isRequired();
    }

    /**
     * Method used by the element to figure out if attribute needs "special"
     * checking; basically if it's required, and/or has a default value.
     * In both cases missing the attribute has specific consequences, either
     * exception or addition of a default value.
     */
    public final boolean isSpecial() {
        return mDefValue.isSpecial();
    }

    public final boolean hasDefaultValue() {
        return mDefValue.hasDefaultValue();
    }

    /**
     * Returns the value type of this attribute as an enumerated int
     * to match type (CDATA, ...)
     *<p>
     * Note: 
     */
    public int getValueType() {
        return TYPE_CDATA;
    }

    public String getValueTypeString()
    {
        return sTypes[getValueType()];
    }

    public boolean typeIsId() {
        return false;
    }

    public boolean typeIsNotation() {
        return false;
    }

    /*
    ///////////////////////////////////////////////////
    // Public API, validation
    ///////////////////////////////////////////////////
     */

    public abstract String validate(DTDValidatorBase v, char[] cbuf, int start, int end, boolean normalize)
        throws XMLStreamException;

    /**
     *<p>
     * Note: the default implementation is not optimized, as it does
     * a potentially unnecessary copy of the contents. It is expected that
     * this method is seldom called (Woodstox never directly calls it; it
     * only gets called for chained validators when one validator normalizes
     * the value, and then following validators are passed a String, not
     * char array)
     */
    public String validate(DTDValidatorBase v, String value, boolean normalize)
        throws XMLStreamException
    {
        int len = value.length();
        /* Temporary buffer has to come from the validator itself, since
         * attribute objects are stateless and shared...
         */
        char[] cbuf = v.getTempAttrValueBuffer(value.length());
        if (len > 0) {
            value.getChars(0, len, cbuf, 0);
        }
        return validate(v, cbuf, 0, len, normalize);
    }

    /**
     * Method called by the {@link DTDValidator}
     * to ask attribute to verify that the default it has (if any) is
     * valid for such type.
     */
    public abstract void validateDefault(InputProblemReporter rep, boolean normalize)
        throws XMLStreamException;

    /**
     * Method called when no validation is to be done, but value is still
     * to be normalized as much as it can. What this usually means is that
     * all regular space (parser earlier on converts other white space to
     * spaces, except for specific character entities; and these special
     * cases are NOT to be normalized).
     *<p>
     * The only exception is that CDATA will not do any normalization. But
     * for now, let's implement basic functionality that CDTA instance will
     * override
     * 
     * @param v Validator that invoked normalization
     *
     * @return Normalized value as a String, if any changes were done; 
     *  null if input was normalized
     */
    public String normalize(DTDValidatorBase v, char[] cbuf, int start, int end)
    {
        return StringUtil.normalizeSpaces(cbuf, start, end);
    }

    /**
     * Method called to do initial normalization of the default attribute
     * value, without trying to verify its validity. Thus, it's
     * called independent of whether we are fully validating the document.
     */
    public void normalizeDefault()
    {
        String val = mDefValue.getValue();
        if (val.length() > 0) {
            char[] cbuf = val.toCharArray();
            String str = StringUtil.normalizeSpaces(cbuf, 0, cbuf.length);
            if (str != null) {
                mDefValue.setValue(str);
            }
        }
    }

    /*
    ///////////////////////////////////////////////////
    // Package methods, validation helper methods
    ///////////////////////////////////////////////////
     */

    protected String validateDefaultName(InputProblemReporter rep, boolean normalize)
        throws XMLStreamException
    {
        String origDefValue = mDefValue.getValue();
        String defValue = origDefValue.trim();

        if (defValue.length() == 0) {
            reportValidationProblem(rep, "Invalid default value '"+defValue
                             +"'; empty String is not a valid name");
        }

        // Ok, needs to be a valid XML name:
        int illegalIx = WstxInputData.findIllegalNameChar(defValue, mCfgNsAware, mCfgXml11);
        if (illegalIx >= 0) {
            if (illegalIx == 0) {
                reportValidationProblem(rep, "Invalid default value '"+defValue+"'; character "
                                        +WstxInputData.getCharDesc(defValue.charAt(0))
                                        +") not valid first character of a name");
            } else {
                reportValidationProblem(rep, "Invalid default value '"+defValue+"'; character #"+illegalIx+" ("
                                        +WstxInputData.getCharDesc(defValue.charAt(illegalIx))
                                        +") not valid name character");
            }
        }

        // Ok, cool it's ok...
        return normalize ? defValue : origDefValue;
    }

    protected String validateDefaultNames(InputProblemReporter rep, boolean normalize)
        throws XMLStreamException
    {
        String defValue = mDefValue.getValue().trim();
        int len = defValue.length();

        // Then code similar to actual value validation:
        StringBuilder sb = null;
        int count = 0;
        int start = 0;

        main_loop:
        while (start < len) {
            char c = defValue.charAt(start);

            // Ok, any white space to skip?
            while (true) {
                if (!WstxInputData.isSpaceChar(c)) {
                    break;
                }
                if (++start >= len) {
                    break main_loop;
                }
                c = defValue.charAt(start);
            }

            // Then need to find the token itself:
            int i = start+1;

            for (; i < len; ++i) {
                if (WstxInputData.isSpaceChar(defValue.charAt(i))) {
                    break;
                }
            }
            String token = defValue.substring(start, i);
            int illegalIx = WstxInputData.findIllegalNameChar(token, mCfgNsAware, mCfgXml11);
            if (illegalIx >= 0) {
                if (illegalIx == 0) {
                    reportValidationProblem(rep, "Invalid default value '"+defValue
                                            +"'; character "
                                            +WstxInputData.getCharDesc(defValue.charAt(start))
                                            +") not valid first character of a name token");
                } else {
                    reportValidationProblem(rep, "Invalid default value '"+defValue
                                            +"'; character "
                                            +WstxInputData.getCharDesc(c)
                                            +") not a valid name character");
                }
            }
            ++count;
            if (normalize) {
                if (sb == null) {
                    sb = new StringBuilder(i - start + 32);
                } else {
                    sb.append(' ');
                }
                sb.append(token);
            }
            start = i+1;
        }

        if (count == 0) {
            reportValidationProblem(rep, "Invalid default value '"+defValue
                             +"'; empty String is not a valid name value");
        }

        return normalize ? sb.toString() : defValue;
    }

    protected String validateDefaultNmToken(InputProblemReporter rep, boolean normalize)
        throws XMLStreamException
    {
        String origDefValue = mDefValue.getValue();
        String defValue = origDefValue.trim();

        if (defValue.length() == 0) {
            reportValidationProblem(rep, "Invalid default value '"+defValue+"'; empty String is not a valid NMTOKEN");
        }
        int illegalIx = WstxInputData.findIllegalNmtokenChar(defValue, mCfgNsAware, mCfgXml11);
        if (illegalIx >= 0) {
            reportValidationProblem(rep, "Invalid default value '"+defValue
                                    +"'; character #"+illegalIx+" ("
                                    +WstxInputData.getCharDesc(defValue.charAt(illegalIx))
                                    +") not valid NMTOKEN character");
        }
        // Ok, cool it's ok...
        return normalize ? defValue : origDefValue;
    }

    /**
     * Method called by validation/normalization code for enumeration-valued
     * attributes, to trim
     * specified attribute value (full normalization not needed -- called
     * for values that CAN NOT have spaces inside; such values can not
     * be legal), and then check whether it is included
     * in set of words (tokens) passed in. If actual value was included,
     * will return the normalized word (as well as store shared String
     * locally); otherwise will return null.
     */
    public String validateEnumValue(char[] cbuf, int start, int end,
                                    boolean normalize,
                                    WordResolver res)
    {
        /* Better NOT to build temporary Strings quite yet; can resolve
         * matches via resolver more efficiently.
         */
        // Note: at this point, should only have real spaces...
        if (normalize) {
            while (start < end && cbuf[start] <= CHAR_SPACE) {
                ++start;
            }
            while (--end > start && cbuf[end] <= CHAR_SPACE) {
                ;
            }
            ++end; // so it'll point to the first char (or beyond end of buffer)
        }

        // Empty String is never legal for enums:
        if (start >= end) {
            return null;
        }
        return res.find(cbuf, start, end);
    }

    protected EntityDecl findEntityDecl(DTDValidatorBase v,
                                        char[] ch, int start, int len /*, int hash*/)
        throws XMLStreamException
    {
        Map<String,EntityDecl> entMap = v.getEntityMap();
        /* !!! 13-Nov-2005, TSa: If this was to become a bottle-neck, we
         *   could use/share a symbol table. Or at least reuse Strings...
         */
        String id = new String(ch, start, len);
        EntityDecl ent = entMap.get(id);

        if (ent == null) {
            reportValidationProblem(v, "Referenced entity '"+id+"' not defined");
        } else if (ent.isParsed()) {
            reportValidationProblem(v, "Referenced entity '"+id+"' is not an unparsed entity");
        }
        return ent;
    }

    /* Too bad this method can not be combined with previous segment --
     * the reason is that DTDValidator does not implement
     * InputProblemReporter...
     */

    protected void checkEntity(InputProblemReporter rep, String id, EntityDecl ent)
        throws XMLStreamException
    {
        if (ent == null) {
            rep.reportValidationProblem("Referenced entity '"+id+"' not defined");
        } else if (ent.isParsed()) {
            rep.reportValidationProblem("Referenced entity '"+id+"' is not an unparsed entity");
        }
    }

    /*
    ///////////////////////////////////////////////////
    // Package methods, error reporting
    ///////////////////////////////////////////////////
     */

    protected String reportInvalidChar(DTDValidatorBase v, char c, String msg)
        throws XMLStreamException
    {
        reportValidationProblem(v, "Invalid character "+WstxInputData.getCharDesc(c)+": "+msg);
        return null;
    }

    protected String reportValidationProblem(DTDValidatorBase v, String msg)
        throws XMLStreamException
    {
        v.reportValidationProblem("Attribute '"+mName+"': "+msg);
        return null;
    }

    /**
     * Method called during parsing of DTD schema, to report a problem.
     * Note that unlike during actual validation, we have no option of
     * just gracefully listing problems and ignoring them; an exception
     * is always thrown.
     */
    protected String reportValidationProblem(InputProblemReporter rep, String msg)
        throws XMLStreamException
    {
        rep.reportValidationProblem("Attribute definition '"+mName+"': "+msg);
        return null;
    }
}
