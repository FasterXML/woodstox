package com.ctc.wstx.dtd;

import javax.xml.stream.XMLStreamException;

import com.ctc.wstx.io.WstxInputData;
import com.ctc.wstx.sr.InputProblemReporter;
import com.ctc.wstx.util.PrefixedName;

/**
 * Specific attribute class for attributes that contain (unique)
 * identifiers.
 */
public final class DTDNmTokenAttr
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
    public DTDNmTokenAttr(PrefixedName name, DefaultAttrValue defValue, int specIndex,
                          boolean nsAware, boolean xml11)
    {
        super(name, defValue, specIndex, nsAware, xml11);
    }

    @Override
    public DTDAttribute cloneWith(int specIndex)
    {
        return new DTDNmTokenAttr(mName, mDefValue, specIndex, mCfgNsAware, mCfgXml11);
    }

    /*
    ///////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////
     */

    @Override
    public int getValueType() {
        return TYPE_NMTOKEN;
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
    @Override
    public String validate(DTDValidatorBase v, char[] cbuf, int start, int end, boolean normalize)
        throws XMLStreamException
    {
        int origLen = end-start;

        // Let's trim leading white space first...
        while (start < end && WstxInputData.isSpaceChar(cbuf[start])) {
            ++start;
        }

        // Empty value?
        if (start >= end) {
            return reportValidationProblem(v, "Empty NMTOKEN value");
        }

        --end; // so that it now points to the last char
        while (end > start && WstxInputData.isSpaceChar(cbuf[end])) {
            --end;
        }

        // Ok, need to check char validity
        for (int i = start; i <= end; ++i) {
            char c = cbuf[i];
            if (!WstxInputData.isNameChar(c, mCfgNsAware, mCfgXml11)) {
                return reportInvalidChar(v, c, "not valid NMTOKEN character");
            }
        }

        if (normalize) {
            // Let's only create the String if we trimmed something
            int len = (end - start)+1;
            if (len != origLen) {
                return new String(cbuf, start, len);
            }
        }
        return null;
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
        String def = validateDefaultNmToken(rep, normalize);
        if (normalize) {
            mDefValue.setValue(def);
        }
    }
}
