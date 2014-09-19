package com.ctc.wstx.exc;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.validation.XMLValidationException;
import org.codehaus.stax2.validation.XMLValidationProblem;

import com.ctc.wstx.util.StringUtil;

/**
 * Specific exception thrown when document has validation (DTD, W3C Schema)
 * errors; things that are not well-formedness problems.
 *<p>
 * The current implementation does not add much beyond basic
 * {@link XMLValidationException}, except for fixing some problems that
 * underlying {@link XMLStreamException} has.
 *<p>
 * Note that some of the code is shared with {@link WstxException}. Unfortunately
 * it is not possible to extend it, however, since it extends basic
 * {@link XMLStreamException}, not {@link XMLValidationException}.
 *<p>
 * One more thing to note: unlike some other exception classes, these
 * exceptions do not have chained root causes. That's why no special
 * handling is necessary for setting the root cause in backwards compatible
 * way.
 */
@SuppressWarnings("serial")
public class WstxValidationException
    extends XMLValidationException
{
    protected WstxValidationException(XMLValidationProblem cause, String msg)
    {
        super(cause, msg);
    }

    protected WstxValidationException(XMLValidationProblem cause, String msg,
                                      Location loc)
    {
        super(cause, msg, loc);
    }

    public static WstxValidationException create(XMLValidationProblem cause)
    {
        // Should always get a message
        Location loc = cause.getLocation();
        if (loc == null) {
            return new WstxValidationException(cause, cause.getMessage());
        }
        return new WstxValidationException(cause, cause.getMessage(), loc);
    }

    /*
    /////////////////////////////////////////////////////////
    // Overridden methods from XMLStreamException
    /////////////////////////////////////////////////////////
     */

    /**
     * Method is overridden for two main reasons: first, default method
     * does not display public/system id information, even if it exists, and
     * second, default implementation can not handle nested Location
     * information.
     */
    public String getMessage()
    {
        String locMsg = getLocationDesc();
        /* Better not use super's message if we do have location information,
         * since parent's message contains (part of) Location
         * info; something we can regenerate better...
         */
        if (locMsg == null) {
            return super.getMessage();
        }
        String msg = getValidationProblem().getMessage();
        StringBuilder sb = new StringBuilder(msg.length() + locMsg.length() + 20);
        sb.append(msg);
        StringUtil.appendLF(sb);
        sb.append(" at ");
        sb.append(locMsg);
        return sb.toString();
    }

    public String toString()
    {
        return getClass().getName()+": "+getMessage();
    }

    /*
    ////////////////////////////////////////////////////////
    // Internal methods:
    ////////////////////////////////////////////////////////
     */

    protected String getLocationDesc()
    {
        Location loc = getLocation();
        return (loc == null) ? null : loc.toString();
    }
}
