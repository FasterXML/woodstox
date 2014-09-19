package com.ctc.wstx.dtd;

import org.codehaus.stax2.validation.XMLValidationException;

import com.ctc.wstx.sr.InputProblemReporter;
import com.ctc.wstx.util.PrefixedName;

/**
 * Simple {@link DTDAttribute} sub-class used for plain vanilla CDATA
 * valued attributes. Although base class implements most of the methods,
 * it's better designwise to keep that base class abstract and have
 * separate CDATA type as well.
 */
public final class DTDCdataAttr
    extends DTDAttribute
{
    public DTDCdataAttr(PrefixedName name, DefaultAttrValue defValue, int specIndex,
                        boolean nsAware, boolean xml11)
    {
        super(name, defValue, specIndex, nsAware, xml11);
    }

    public DTDAttribute cloneWith(int specIndex)
    {
        return new DTDCdataAttr(mName, mDefValue, specIndex, mCfgNsAware, mCfgXml11);
    }

    /*
    ///////////////////////////////////////////////////
    // Public API, validation
    ///////////////////////////////////////////////////
     */

    @Override
    public String validate(DTDValidatorBase v, char[] cbuf, int start, int end, boolean normalize)
        throws XMLValidationException
    {
        // Nothing to do for pure CDATA attributes...
        return null;
    }

    @Override
    public void validateDefault(InputProblemReporter rep, boolean normalize)
        throws javax.xml.stream.XMLStreamException
    {
        // Nothing to do for CDATA; all values are fine
    }

    @Override
    public String normalize(DTDValidatorBase v, char[] cbuf, int start, int end)
    {
        // Nothing to do for pure CDATA attributes...
        return null;
    }

    @Override
    public void normalizeDefault()
    {
        // Nothing to do for pure CDATA attributes...
    }
}
