package com.ctc.wstx.dom;

import java.util.Collections;

import javax.xml.stream.*;
import javax.xml.transform.dom.DOMSource;

import org.codehaus.stax2.ri.dom.DOMWrappingReader;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.exc.WstxParsingException;

public class WstxDOMWrappingReader
    extends DOMWrappingReader
{
    protected final ReaderConfig mConfig;

    /*
    ///////////////////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////////////////
     */

    protected WstxDOMWrappingReader(DOMSource src, ReaderConfig cfg)
        throws XMLStreamException
    {
        super(src, cfg.willSupportNamespaces(), cfg.willCoalesceText());
        mConfig = cfg;
        // [WSTX-162]: allow enabling/disabling name/ns intern()ing
        if (cfg.hasInternNamesBeenEnabled()) {
            setInternNames(true);
        }
        if (cfg.hasInternNsURIsBeenEnabled()) {
            setInternNsURIs(true);
        }
    }

    public static WstxDOMWrappingReader createFrom(DOMSource src, ReaderConfig cfg)
        throws XMLStreamException
    {
        return new WstxDOMWrappingReader(src, cfg);
    }

    /*
    ///////////////////////////////////////////////////
    // Defined/Overridden config methods
    ///////////////////////////////////////////////////
     */

    public boolean isPropertySupported(String name)
    {
        // !!! TBI: not all these properties are really supported
        return mConfig.isPropertySupported(name);
    }

    public Object getProperty(String name)
    {
        if (name.equals("javax.xml.stream.entities")) {
            // !!! TBI
            return Collections.EMPTY_LIST;
        }
        if (name.equals("javax.xml.stream.notations")) {
            // !!! TBI
            return Collections.EMPTY_LIST;
        }
        return mConfig.getProperty(name);
    }

    public boolean setProperty(String name, Object value)
    {
        // Basic config accessor works just fine...
        return mConfig.setProperty(name, value);
    }

    /*
    ///////////////////////////////////////////////////
    // Defined/Overridden error reporting
    ///////////////////////////////////////////////////
     */

    @Override
    protected void throwStreamException(String msg, Location loc)
        throws XMLStreamException
    {
        if (loc == null) {
            throw new WstxParsingException(msg);
        }
        throw new WstxParsingException(msg, loc);
    }
}
