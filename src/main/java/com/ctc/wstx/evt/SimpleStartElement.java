package com.ctc.wstx.evt;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.StartElement;

import com.ctc.wstx.io.TextEscaper;
import com.ctc.wstx.util.BaseNsContext;
import com.ctc.wstx.util.DataUtil;

/**
 * Wstx {@link StartElement} implementation used when event is constructed
 * from already objectified data, for example when constructed by the event
 * factory.
 */
public class SimpleStartElement
    extends BaseStartElement
{
    final Map<QName,Attribute> mAttrs;

    /*
    /////////////////////////////////////////////
    // Life cycle
    /////////////////////////////////////////////
     */

    protected SimpleStartElement(Location loc, QName name, BaseNsContext nsCtxt,
                                 Map<QName,Attribute> attr)
    {
        super(loc, name, nsCtxt);
        mAttrs = attr;
    }

    /**
     * Factory method called when a start element needs to be constructed
     * from an external source (most likely, non-woodstox stream reader).
     */
    public static SimpleStartElement construct(Location loc, QName name,
            Map<QName,Attribute> attrs, List<Namespace> ns,
            NamespaceContext nsCtxt)
    {
        BaseNsContext myCtxt = MergedNsContext.construct(nsCtxt, ns);
        return new SimpleStartElement(loc, name, myCtxt, attrs);
    }

    public static SimpleStartElement construct(Location loc, QName name,
            Iterator<Attribute> attrs, Iterator<Namespace> ns,
            NamespaceContext nsCtxt)
    {
        Map<QName,Attribute> attrMap;
        if (attrs == null || !attrs.hasNext()) {
            attrMap = null;
        } else {
            attrMap = new LinkedHashMap<QName,Attribute>();
            do {
                Attribute attr = attrs.next();
                attrMap.put(attr.getName(), attr);
            } while (attrs.hasNext());
        }

        BaseNsContext myCtxt;
        if (ns != null && ns.hasNext()) {
            ArrayList<Namespace> l = new ArrayList<Namespace>();
            do {
                l.add(ns.next()); // cast to catch type problems early
            } while (ns.hasNext());
            myCtxt = MergedNsContext.construct(nsCtxt, l);
        } else {
            /* Doh. Need specificially 'our' namespace context, to get them
             * output properly...
             */
            if (nsCtxt == null) {
                myCtxt = null;
            } else if (nsCtxt instanceof BaseNsContext) {
                myCtxt = (BaseNsContext) nsCtxt;
            } else {
                myCtxt = MergedNsContext.construct(nsCtxt, null);
            }
        }
        return new SimpleStartElement(loc, name, myCtxt, attrMap);
    }

    /*
    /////////////////////////////////////////////
    // Public API
    /////////////////////////////////////////////
     */

    @Override
    public Attribute getAttributeByName(QName name)
    {
        if (mAttrs == null) {
            return null;
        }
        return mAttrs.get(name);
    }

    @Override
    public Iterator<Attribute> getAttributes()
    {
        if (mAttrs == null) {
            return DataUtil.emptyIterator();
        }
        return mAttrs.values().iterator();
    }

    @Override
    protected void outputNsAndAttr(Writer w) throws IOException
    {
        // First namespace declarations, if any:
        if (mNsCtxt != null) {
            mNsCtxt.outputNamespaceDeclarations(w);
        }
        // Then attributes, if any:
        if (mAttrs != null && mAttrs.size() > 0) {
        	for (Attribute attr : mAttrs.values()) {
                // Let's only output explicit attribute values:
                if (!attr.isSpecified()) {
                    continue;
                }

                w.write(' ');
                QName name = attr.getName();
                String prefix = name.getPrefix();
                if (prefix != null && prefix.length() > 0) {
                    w.write(prefix);
                    w.write(':');
                }
                w.write(name.getLocalPart());
                w.write("=\"");
                String val =  attr.getValue();
                if (val != null && val.length() > 0) {
                    TextEscaper.writeEscapedAttrValue(w, val);
                }
                w.write('"');
            }
        }
    }

    @Override
    protected void outputNsAndAttr(XMLStreamWriter w) throws XMLStreamException
    {
        // First namespace declarations, if any:
        if (mNsCtxt != null) {
            mNsCtxt.outputNamespaceDeclarations(w);
        }
        // Then attributes, if any:
        if (mAttrs != null && mAttrs.size() > 0) {
            for (Attribute attr : mAttrs.values()) {
                // Let's only output explicit attribute values:
                if (!attr.isSpecified()) {
                    continue;
                }
                QName name = attr.getName();
                String prefix = name.getPrefix();
                String ln = name.getLocalPart();
                String nsURI = name.getNamespaceURI();
                w.writeAttribute(prefix, nsURI, ln, attr.getValue());
            }
        }
    }
}
