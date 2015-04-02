package com.ctc.wstx.dtd;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import com.ctc.wstx.io.WstxInputData;
import com.ctc.wstx.sr.InputProblemReporter;
import com.ctc.wstx.util.ElementId;
import com.ctc.wstx.util.ElementIdMap;
import com.ctc.wstx.util.PrefixedName;

/**
 * Attribute class for attributes that contain references
 * to elements that have matching identifier specified.
 */
public final class DTDIdRefAttr
    extends DTDAttribute
{
    /*
    ///////////////////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////////////////
     */

    /**
     * Main constructor.
     */
    public DTDIdRefAttr(PrefixedName name, DefaultAttrValue defValue, int specIndex,
                        boolean nsAware, boolean xml11)
    {
        super(name, defValue, specIndex, nsAware, xml11);
    }

    @Override
    public DTDAttribute cloneWith(int specIndex) {
        return new DTDIdRefAttr(mName, mDefValue, specIndex, mCfgNsAware, mCfgXml11);
    }

    /*
    ///////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////
     */

    @Override
    public int getValueType() {
        return TYPE_IDREF;
    }

    /*
    ///////////////////////////////////////////////////
    // Public API, validation
    ///////////////////////////////////////////////////
     */

    /**
     * Method called by the validator
     * to let the attribute do necessary normalization and/or validation
     * for the value.
     */
    @SuppressWarnings("cast")
    @Override
    public String validate(DTDValidatorBase v, char[] cbuf, int start, int end, boolean normalize)
        throws XMLStreamException
    {
        /* Let's skip leading/trailing white space, even if we are not
         * to normalize visible attribute value. This allows for better
         * round-trip handling, but still allow validation.
         */
        while (start < end && WstxInputData.isSpaceChar(cbuf[start])) {
            ++start;
        }

        if (start >= end) { // empty (all white space) value?
            return reportValidationProblem(v, "Empty IDREF value");
        }

        --end; // so that it now points to the last char
        while (end > start && WstxInputData.isSpaceChar(cbuf[end])) {
            --end;
        }

        // Ok, need to check char validity, and also calc hash code:
        char c = cbuf[start];
        if (!WstxInputData.isNameStartChar(c, mCfgNsAware, mCfgXml11)) {
            return reportInvalidChar(v, c, "not valid as the first IDREF character");
        }
        int hash = (int) c;
        for (int i = start+1; i <= end; ++i) {
            c = cbuf[i];
            if (!WstxInputData.isNameChar(c, mCfgNsAware, mCfgXml11)) {
                return reportInvalidChar(v, c, "not valid as an IDREF character");
            }
            hash = (hash * 31) + (int) c;
        }

        // Ok, let's check and update id ref list...
        ElementIdMap m = v.getIdMap();
        Location loc = v.getLocation();
        ElementId id = m.addReferenced(cbuf, start, (end - start + 1), hash,
                                       loc, v.getElemName(), mName);
        // and that's all; no more checks needed here
        return normalize ? id.getId() : null;
    }

    /**
     * Method called by the validator
     * to ask attribute to verify that the default it has (if any) is
     * valid for such type.
     */
    @Override
    public void validateDefault(InputProblemReporter rep, boolean normalize)
        throws XMLStreamException
    {
        String def = validateDefaultName(rep, normalize);
        if (normalize) {
            mDefValue.setValue(def);
        }
    }
}
