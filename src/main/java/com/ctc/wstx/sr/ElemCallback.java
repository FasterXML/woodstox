package com.ctc.wstx.sr;

import javax.xml.stream.Location;
import javax.xml.namespace.QName;

import com.ctc.wstx.util.BaseNsContext;

/**
 * Abstract base class that defines set of simple callbacks to be
 * called by the stream reader, passing information about element
 * that the stream currently points to, if any.
 */
public abstract class ElemCallback
{
    public abstract Object withStartElement(Location loc, QName name,
                                            BaseNsContext nsCtxt, ElemAttrs attrs,
                                            boolean wasEmpty);
}
