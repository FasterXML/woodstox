package com.ctc.wstx.dtd;

import javax.xml.stream.XMLStreamException;

import com.ctc.wstx.sr.InputProblemReporter;
import com.ctc.wstx.util.PrefixedName;
import com.ctc.wstx.util.WordResolver;

/**
 * Specific attribute class for attributes that have enumerated values.
 */
public final class DTDEnumAttr
    extends DTDAttribute
{
    final WordResolver mEnumValues;

    /*
    ///////////////////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////////////////
     */

    public DTDEnumAttr(PrefixedName name, DefaultAttrValue defValue,
                       int specIndex, boolean nsAware, boolean xml11,
                       WordResolver enumValues)
    {
        super(name, defValue, specIndex, nsAware, xml11);
        mEnumValues = enumValues;
    }

    @Override
    public DTDAttribute cloneWith(int specIndex)
    {
        return new DTDEnumAttr(mName, mDefValue, specIndex, mCfgNsAware,
                               mCfgXml11, mEnumValues);
    }

    /*
    ///////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////
     */

    @Override
    public int getValueType() {
        return TYPE_ENUMERATED;
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
        String ok = validateEnumValue(cbuf, start, end, normalize, mEnumValues);
        if (ok == null) {
            String val = new String(cbuf, start, (end-start));
            return reportValidationProblem(v, "Invalid enumerated value '"+val+"': has to be one of ("
                                           +mEnumValues+")");
        }
        return ok;
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

        // And then that it's one of listed values:
        String shared = mEnumValues.find(def);
        if (shared == null) {
            reportValidationProblem(rep, "Invalid default value '"+def+"': has to be one of ("
                                    +mEnumValues+")");
            return;
        }

        // Ok, cool it's ok...
        if (normalize) {
            mDefValue.setValue(shared);
        }
    }
}
