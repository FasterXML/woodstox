package com.ctc.wstx.exc;

import javax.xml.stream.Location;

/**
 * Intermediate base class for reporting actual Wstx parsing problems.
 */
@SuppressWarnings("serial")
public class WstxParsingException
    extends WstxException
{
    public WstxParsingException(String msg, Location loc) {
        super(msg, loc);
    }

    // !!! 13-Sep-2008, tatus: Only needed for DOMWrapping reader, for now
    public WstxParsingException(String msg) { super(msg); }
}
