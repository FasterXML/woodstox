package com.ctc.wstx.exc;

import java.io.IOException;

/**
 * Simple wrapper for {@link IOException}s; needed when StAX does not expose
 * underlying I/O exceptions via its methods.
 */
@SuppressWarnings("serial")
public class WstxIOException
    extends WstxException
{
    public WstxIOException(IOException ie) {
        super(ie);
    }

    public WstxIOException(String msg) {
        super(msg);
    }
}
