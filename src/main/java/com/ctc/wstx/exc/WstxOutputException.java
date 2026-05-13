package com.ctc.wstx.exc;

/**
 * Exception class used for notifying about well-formedness errors that
 * writers would create. Such exceptions are thrown when strict output
 * validation is enabled.
 */
@SuppressWarnings("serial")
public class WstxOutputException
    extends WstxException
{
    public WstxOutputException(String msg) {
        super(msg);
    }

    public WstxOutputException(String msg, Throwable cause) {
        super(msg);
        initCause(cause);
    }
}
