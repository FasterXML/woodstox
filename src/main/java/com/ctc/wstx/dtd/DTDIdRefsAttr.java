package com.ctc.wstx.dtd;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import com.ctc.wstx.io.WstxInputData;
import com.ctc.wstx.sr.InputProblemReporter;
import com.ctc.wstx.util.ElementId;
import com.ctc.wstx.util.ElementIdMap;
import com.ctc.wstx.util.PrefixedName;

/**
 * Attribute class for attributes that contain multiple references
 * to elements that have matching identifier specified.
 */
public final class DTDIdRefsAttr
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
    public DTDIdRefsAttr(PrefixedName name, DefaultAttrValue defValue, int specIndex,
                         boolean nsAware, boolean xml11)
    {
        super(name, defValue, specIndex, nsAware, xml11);
    }

    public DTDAttribute cloneWith(int specIndex)
    {
        return new DTDIdRefsAttr(mName, mDefValue, specIndex, mCfgNsAware, mCfgXml11);
    }

    /*
    ///////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////
     */

    public int getValueType() {
        return TYPE_IDREFS;
    }

    /*
    ///////////////////////////////////////////////////
    // Public API, validation
    ///////////////////////////////////////////////////
     */

    @SuppressWarnings("cast")
	public String validate(DTDValidatorBase v, char[] cbuf, int start, int end, boolean normalize)
        throws XMLStreamException
    {
        /* Let's skip leading/trailing white space, even if we are not
         * to normalize visible attribute value. This allows for better
         * round-trip handling (no changes for physical value caller
         * gets), but still allows succesful validation.
         */
        while (start < end && WstxInputData.isSpaceChar(cbuf[start])) {
            ++start;
        }

        // No id?
        if (start >= end) {
            return reportValidationProblem(v, "Empty IDREFS value");
        }

        --end; // so that it now points to the last char
        // We now the first char is not a space by now...
        while (end > start && WstxInputData.isSpaceChar(cbuf[end])) {
            --end;
        }

        // Ok; now start points to first, end to last char (both inclusive)
        ElementIdMap m = v.getIdMap();
        Location loc = v.getLocation();

        String idStr = null;
        StringBuilder sb = null;
        while (start <= end) {
            // Ok, need to check char validity, and also calc hash code:
            char c = cbuf[start];
            if (!WstxInputData.isNameStartChar(c, mCfgNsAware, mCfgXml11)) {
                return reportInvalidChar(v, c, "not valid as the first IDREFS character");
            }
            int hash = (int) c;
            int i = start+1;
            for (; i <= end; ++i) {
                c = cbuf[i];
                if (WstxInputData.isSpaceChar(c)) {
                    break;
                }
                if (!WstxInputData.isNameChar(c, mCfgNsAware, mCfgXml11)) {
                    return reportInvalidChar(v, c, "not valid as an IDREFS character");
                }
                hash = (hash * 31) + (int) c;
            }

            // Ok, got the next id ref...
            ElementId id = m.addReferenced(cbuf, start, i - start, hash,
                                           loc, v.getElemName(), mName);
            
            // Can skip the trailing space char (if there was one)
            start = i+1;

            /* When normalizing, we can possibly share id String, or
             * alternatively, compose normalized String if multiple
             */
            if (normalize) {
                if (idStr == null) { // first idref
                    idStr = id.getId();
                } else {
                    if (sb == null) {
                        sb = new StringBuilder(idStr);
                    }
                    idStr = id.getId();
                    sb.append(' ');
                    sb.append(idStr);
                }
            }

            // Ok, any white space to skip?
            while (start <= end && WstxInputData.isSpaceChar(cbuf[start])) {
                ++start;
            }
        }

        if (normalize) {
            if (sb != null) {
                idStr = sb.toString();
            }
            return idStr;
        }

        return null;
    }

    /**
     * Method called by the validator
     * to ask attribute to verify that the default it has (if any) is
     * valid for such type.
     *<p>
     * It's unlikely there will be default values... but just in case,
     * let's implement it properly.
     */
    public void validateDefault(InputProblemReporter rep, boolean normalize)
        throws XMLStreamException
    {
        String def = validateDefaultNames(rep, normalize);
        if (normalize) {
            mDefValue.setValue(def);
        }
    }
}
