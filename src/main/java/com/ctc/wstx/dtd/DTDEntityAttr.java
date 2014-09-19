package com.ctc.wstx.dtd;

import javax.xml.stream.XMLStreamException;

import com.ctc.wstx.ent.EntityDecl;
import com.ctc.wstx.io.WstxInputData;
import com.ctc.wstx.sr.InputProblemReporter;
import com.ctc.wstx.util.PrefixedName;

/**
 * Specific attribute class for attributes that contain (unique)
 * identifiers.
 */
public final class DTDEntityAttr
    extends DTDAttribute
{
    /*
    ///////////////////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////////////////
     */

    /**
     * Main constructor. Note that id attributes can never have
     * default values.
     */
    public DTDEntityAttr(PrefixedName name, DefaultAttrValue defValue, int specIndex,
                     boolean nsAware, boolean xml11)
    {
        super(name, defValue, specIndex, nsAware, xml11);
    }

    public DTDAttribute cloneWith(int specIndex)
    {
        return new DTDEntityAttr(mName, mDefValue, specIndex, mCfgNsAware, mCfgXml11);
    }

    /*
    ///////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////
     */

    public int getValueType() {
        return TYPE_ENTITY;
    }

    /*
    ///////////////////////////////////////////////////
    // Public API, validation
    ///////////////////////////////////////////////////
     */

    /**
     * Method called by the {@link DTDValidatorBase}
     * to let the attribute do necessary normalization and/or validation
     * for the value.
     */
    public String validate(DTDValidatorBase v, char[] cbuf, int start, int end, boolean normalize)
        throws XMLStreamException
    {
        while (start < end && WstxInputData.isSpaceChar(cbuf[start])) {
            ++start;
        }

        // Empty value?
        if (start >= end) {
            return reportValidationProblem(v, "Empty ENTITY value");
        }
        --end; // so that it now points to the last char
        while (end > start  && WstxInputData.isSpaceChar(cbuf[end])) {
            --end;
        }

        // Ok, need to check char validity, and also calc hash code:
        char c = cbuf[start];
        if (!WstxInputData.isNameStartChar(c, mCfgNsAware, mCfgXml11) && c != ':') {
            return reportInvalidChar(v, c, "not valid as the first ID character");
        }
        for (int i = start+1; i <= end; ++i) {
            c = cbuf[i];
            if (!WstxInputData.isNameChar(c, mCfgNsAware, mCfgXml11)) {
                return reportInvalidChar(v, c, "not valid as an ID character");
            }
        }

        EntityDecl ent = findEntityDecl(v, cbuf, start, (end - start + 1));
        // only returns if it succeeded...

        return normalize ? ent.getName() : null;
    }

    /**
     * Method called by the validator object
     * to ask attribute to verify that the default it has (if any) is
     * valid for such type.
     */
    public void validateDefault(InputProblemReporter rep, boolean normalize)
        throws XMLStreamException
    {
        String normStr = validateDefaultName(rep, normalize);
        if (normalize) {
            mDefValue.setValue(normStr);
        }

        // Ok, but was it declared?

        /* 03-Dec-2004, TSa: This is rather ugly -- need to know we
         *   actually really get a DTD reader, and DTD reader needs
         *   to expose a special method... but it gets things done.
         */
        EntityDecl ent = ((MinimalDTDReader) rep).findEntity(normStr);
        checkEntity(rep, normStr, ent);
    }
}
