package com.ctc.wstx.exc;

import javax.xml.stream.Location;

/**
 * Exception thrown during parsing, if an unexpected EOF is encountered.
 * Location usually signals starting position of current Node.
 */
@SuppressWarnings("serial")
public class WstxEOFException
    extends WstxParsingException
{
    public WstxEOFException(String msg, Location loc) {
        super(msg, loc);
    }
}
