package com.ctc.wstx.dtd;

import javax.xml.stream.XMLStreamException;

import com.ctc.wstx.io.WstxInputData;
import com.ctc.wstx.sr.InputProblemReporter;
import com.ctc.wstx.util.PrefixedName;

/**
 * Specific attribute class for attributes that contain (unique)
 * identifiers.
 */
public final class DTDNmTokensAttr
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
    public DTDNmTokensAttr(PrefixedName name, DefaultAttrValue defValue, int specIndex,
                           boolean nsAware, boolean xml11)
    {
        super(name, defValue, specIndex, nsAware, xml11);
    }

    @Override
    public DTDAttribute cloneWith(int specIndex) {
        return new DTDNmTokensAttr(mName, mDefValue, specIndex, mCfgNsAware, mCfgXml11);
    }

    /*
    ///////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////
     */

    @Override
    public int getValueType() {
        return TYPE_NMTOKENS;
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
        //int origStart = start;

        /* First things first; let's ensure value is not empty (all
         * white space)...
         */
        while (start < end && WstxInputData.isSpaceChar(cbuf[start])) {
            ++start;
        }
        // Empty value?
        if (start >= end) {
            return reportValidationProblem(v, "Empty NMTOKENS value");
        }

        /* Then, let's have separate handling for normalizing and
         * non-normalizing case, since latter is trivially easy case:
         */
        if (!normalize) {
            for (; start < end; ++start) {
                char c = cbuf[start];
                if (!WstxInputData.isSpaceChar(c) 
                    && !WstxInputData.isNameChar(c, mCfgNsAware, mCfgXml11)) {
                    return reportInvalidChar(v, c, "not valid as NMTOKENS character");
                }
            }
            return null; // ok, all good
        }

        //boolean trimmed = (origStart != start);
        //origStart = start;

        --end; // so that it now points to the last char
        // Wouldn't absolutely have to trim trailing... but is easy to do
        while (end > start && WstxInputData.isSpaceChar(cbuf[end])) {
            --end;
            //trimmed = true;
        }

        /* Ok, now, need to check we only have valid chars, and maybe
         * also coalesce multiple spaces, if any.
         */
        StringBuilder sb = null;

        while (start <= end) {
            int i = start;
            for (; i <= end; ++i) {
                char c = cbuf[i];
                if (WstxInputData.isSpaceChar(c)) {
                    break;
                }
                if (!WstxInputData.isNameChar(c, mCfgNsAware, mCfgXml11)) {
                    return reportInvalidChar(v, c, "not valid as an NMTOKENS character");
                }
            }

            if (sb == null) {
                sb = new StringBuilder(end - start + 1);
            } else {
                sb.append(' ');
            }
            sb.append(cbuf, start, (i - start));

            start = i + 1;
            // Ok, any white space to skip?
            while (start <= end && WstxInputData.isSpaceChar(cbuf[start])) {
                ++start;
            }
        }

        /* 27-Nov-2005, TSa: Could actually optimize trimming, and often
         *   avoid using StringBuilder... but let's only do it if it turns
         *   out dealing with NMTOKENS normalization shows up on profiling...
         */
        return sb.toString();
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
        String defValue = mDefValue.getValue();
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

            int i = start+1;

            do {
                if (++i >= len) {
                    break;
                }
                c = defValue.charAt(i);
            } while (!WstxInputData.isSpaceChar(c));
            ++count;
            String token = defValue.substring(start, i);
            int illegalIx = WstxInputData.findIllegalNmtokenChar(token, mCfgNsAware, mCfgXml11);
            if (illegalIx >= 0) {
                reportValidationProblem(rep, "Invalid default value '"+defValue
                                        +"'; character #"+illegalIx+" ("
                                        +WstxInputData.getCharDesc(defValue.charAt(illegalIx))
                                        +") not a valid NMTOKENS character");
            }

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
                                    +"'; empty String is not a valid NMTOKENS value");
            return;
        }

        if (normalize) {
            mDefValue.setValue(sb.toString());
        }
    }
}
