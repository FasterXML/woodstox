package com.ctc.wstx.evt;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;

import org.codehaus.stax2.ri.evt.AttributeEventImpl;

import com.ctc.wstx.io.TextEscaper;
import com.ctc.wstx.sr.ElemAttrs;
import com.ctc.wstx.util.BaseNsContext;
import com.ctc.wstx.util.DataUtil;

/**
 * Wstx {@link StartElement} implementation used when directly creating
 * events from a stream reader.
 */
public class CompactStartElement
    extends BaseStartElement
{
    // Need to be in sync with ones from ElemAttrs
    //private final static int OFFSET_LOCAL_NAME = 0;
    private final static int OFFSET_NS_URI = 1;
    private final static int OFFSET_NS_PREFIX = 2;
    private final static int OFFSET_VALUE = 3;

    /*
    ////////////////////////////////////////////////////////////
    // Attribute information
    ////////////////////////////////////////////////////////////
     */

    /**
     * Container object that has enough information about attributes to
     * be able to implement attribute accessor methods of this class.
     */
    final ElemAttrs mAttrs;

    /**
     * Array needed for accessing actual String components of the attributes
     */
    final String[] mRawAttrs;

    /**
     * Lazily created List that contains Attribute instances contained
     * in this list. Created only if there are at least 2 attributes.
     */
    private ArrayList<Attribute> mAttrList = null;

    /*
    ////////////////////////////////////////////////////////////
    // Life cycle
    ////////////////////////////////////////////////////////////
     */

    protected CompactStartElement(Location loc, QName name, BaseNsContext nsCtxt,
                                  ElemAttrs attrs)
    {
        super(loc, name, nsCtxt);
        mAttrs = attrs;
        mRawAttrs = (attrs == null) ? null : attrs.getRawAttrs();
    }

    /*
    ////////////////////////////////////////////////////////////
    // StartElement implementation
    ////////////////////////////////////////////////////////////
     */

    @Override
    public Attribute getAttributeByName(QName name)
    {
        if (mAttrs == null) {
            return null;
        }
        int ix = mAttrs.findIndex(name);
        if (ix < 0) {
            return null;
        }
        return constructAttr(mRawAttrs, ix, mAttrs.isDefault(ix));
    }

    @Override
    public Iterator<Attribute> getAttributes()
    {
        if (mAttrList == null) { // List is lazily constructed as needed
            if (mAttrs == null) {
                return DataUtil.emptyIterator();
            }
            String[] rawAttrs = mRawAttrs;
            int rawLen = rawAttrs.length;
            int defOffset = mAttrs.getFirstDefaultOffset();
            if (rawLen == 4) {
                return DataUtil.singletonIterator(constructAttr(rawAttrs, 0, (defOffset == 0)));
            }
            ArrayList<Attribute> l = new ArrayList<Attribute>(rawLen >> 2);
            for (int i = 0; i < rawLen; i += 4) {
                l.add(constructAttr(rawAttrs, i, (i >= defOffset)));
            }
            mAttrList = l;
        }
        return mAttrList.iterator();
    }

    @Override
    protected void outputNsAndAttr(Writer w) throws IOException
    {
        if (mNsCtxt != null) {
            mNsCtxt.outputNamespaceDeclarations(w);
        }

        String[] raw = mRawAttrs;
        if (raw != null) {
            for (int i = 0, len = raw.length; i < len; i += 4) {
                w.write(' ');
                String prefix = raw[i + OFFSET_NS_PREFIX];
                if (prefix != null && prefix.length() > 0) {
                    w.write(prefix);
                    w.write(':');
                }
                w.write(raw[i]); // local name
                w.write("=\"");
                TextEscaper.writeEscapedAttrValue(w, raw[i + OFFSET_VALUE]);
                w.write('"');
            }
        }
    }

    @Override
    protected void outputNsAndAttr(XMLStreamWriter w) throws XMLStreamException
    {
        if (mNsCtxt != null) {
            mNsCtxt.outputNamespaceDeclarations(w);
        }
        String[] raw = mRawAttrs;
        if (raw != null) {
            for (int i = 0, len = raw.length; i < len; i += 4) {
                String ln = raw[i];
                String prefix = raw[i + OFFSET_NS_PREFIX];
                String nsURI = raw[i + OFFSET_NS_URI];
                w.writeAttribute(prefix, nsURI, ln, raw[i + OFFSET_VALUE]);
            }
        }
    }

    /*
    ////////////////////////////////////////////////////////////
    // Internal methods
    ////////////////////////////////////////////////////////////
     */

    protected Attribute constructAttr(String[] raw, int rawIndex, boolean isDef)
    {
        return new AttributeEventImpl(getLocation(), raw[rawIndex], raw[rawIndex+1],
                raw[rawIndex+2], raw[rawIndex+3], !isDef);
    }
}
