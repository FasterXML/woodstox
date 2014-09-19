package com.ctc.wstx.sr;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.validation.XMLValidationProblem;

/**
 * Interface implemented by input reader, and used by other components to
 * report problem that are related to current input position.
 */
public interface InputProblemReporter
{
    /*
    ////////////////////////////////////////////////////
    // Methods for reporting "hard" errors:
    ////////////////////////////////////////////////////
     */

    public void throwParseError(String msg) throws XMLStreamException;
    public void throwParseError(String msg, Object arg, Object arg2)
        throws XMLStreamException;

    /*
    ///////////////////////////////////////////////////////
    // Reporting validation problems
    ///////////////////////////////////////////////////////
     */

    public void reportValidationProblem(XMLValidationProblem prob)
        throws XMLStreamException;
    public void reportValidationProblem(String msg)
        throws XMLStreamException;
    public void reportValidationProblem(String msg, Object arg, Object arg2)
        throws XMLStreamException;

    /*
    ///////////////////////////////////////////////////////
    // Methods for reporting other "soft" (recoverable) problems
    ///////////////////////////////////////////////////////
     */

    public void reportProblem(Location loc, String probType, String format, Object arg, Object arg2)
        throws XMLStreamException;

    /*
    ////////////////////////////////////////////////////
    // Supporting methods needed by reporting
    ////////////////////////////////////////////////////
     */

    public Location getLocation();
}
