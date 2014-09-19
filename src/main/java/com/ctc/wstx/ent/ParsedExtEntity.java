package com.ctc.wstx.ent;

import java.io.IOException;
import java.io.Writer;
import java.net.URL;

import javax.xml.stream.Location;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.cfg.XmlConsts;
import com.ctc.wstx.io.DefaultInputResolver;
import com.ctc.wstx.io.WstxInputSource;

public class ParsedExtEntity
    extends ExtEntity
{
    public ParsedExtEntity(Location loc, String name, URL ctxt,
                           String pubId, String sysId)
    {
        super(loc, name, ctxt, pubId, sysId);
    }

    public String getNotationName() {
        return null;
    }

    /*
    ///////////////////////////////////////////
    // Implementation of abstract base methods
    ///////////////////////////////////////////
     */

    public void writeEnc(Writer w) throws IOException
    {
        w.write("<!ENTITY ");
        w.write(mName);
        String pubId = getPublicId();
        if (pubId != null) {
            w.write("PUBLIC \"");
            w.write(pubId);
            w.write("\" ");
        } else {
            w.write("SYSTEM ");
        }
        w.write('"');
        w.write(getSystemId());
        w.write("\">");
    }

    /*
    ///////////////////////////////////////////
    // Extended API for Wstx core
    ///////////////////////////////////////////
     */

    // // // Type information
    
    public boolean isParsed() { return true; }
    
    public WstxInputSource expand(WstxInputSource parent,
                                  XMLResolver res, ReaderConfig cfg,
                                  int xmlVersion)
        throws IOException, XMLStreamException
    {
        /* 05-Feb-2006, TSa: If xmlVersion not explicitly known, it defaults
         *    to 1.0
         */
        if (xmlVersion == XmlConsts.XML_V_UNKNOWN) {
            xmlVersion = XmlConsts.XML_V_10;
        }
        return DefaultInputResolver.resolveEntity
            (parent, mContext, mName, getPublicId(), getSystemId(), res, cfg, xmlVersion);
    }
}
