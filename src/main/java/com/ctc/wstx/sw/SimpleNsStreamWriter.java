/* Woodstox XML processor
 *
 * Copyright (c) 2004- Tatu Saloranta, tatu.saloranta@iki.fi
 *
 * Licensed under the License specified in the file LICENSE,
 * included with the source code.
 * You may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ctc.wstx.sw;

import java.io.IOException;
import java.util.Iterator;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.StartElement;

import com.ctc.wstx.api.WriterConfig;
import com.ctc.wstx.cfg.ErrorConsts;
import com.ctc.wstx.sr.AttributeCollector;
import com.ctc.wstx.sr.InputElementStack;

/**
 * Namespace-aware implementation of {@link XMLStreamWriter}, that does
 * not do namespace repairing, ie doesn't try to resolve possible
 * conflicts between prefixes and namespace URIs, or automatically
 * create namespace bindings.
 */
public class SimpleNsStreamWriter
    extends BaseNsStreamWriter
{
    /*
    ////////////////////////////////////////////////////
    // Life-cycle (ctors)
    ////////////////////////////////////////////////////
     */

    public SimpleNsStreamWriter(XmlWriter xw, String enc, WriterConfig cfg)
    {
        super(xw, enc, cfg, false);
    }

    /*
    ////////////////////////////////////////////////////
    // XMLStreamWriter API
    ////////////////////////////////////////////////////
     */

    //public NamespaceContext getNamespaceContext()
    //public void setNamespaceContext(NamespaceContext context)
    //public String getPrefix(String uri)
    //public void setPrefix(String prefix, String uri)

    //public void writeAttribute(String localName, String value)

    @Override
    public void writeAttribute(String nsURI, String localName, String value)
        throws XMLStreamException
    {
        // No need to set mAnyOutput, nor close the element
        if (!mStartElementOpen) {
            throwOutputError(ErrorConsts.WERR_ATTR_NO_ELEM);
        }
        String prefix = mCurrElem.getExplicitPrefix(nsURI);
        if (!mReturnNullForDefaultNamespace && prefix == null) {
            throwOutputError("Unbound namespace URI '" + nsURI + "'");
        }
        doWriteAttr(localName, nsURI, prefix, value);
    }

    @Override
    public void writeAttribute(String prefix, String nsURI,
                               String localName, String value)
        throws XMLStreamException
    {
        if (!mStartElementOpen) {
            throwOutputError(ErrorConsts.WERR_ATTR_NO_ELEM);
        }
        doWriteAttr(localName, nsURI, prefix, value);
    }

    //public void writeEmptyElement(String localName) throws XMLStreamException
    //public void writeEmptyElement(String nsURI, String localName) throws XMLStreamException
    //public void writeEmptyElement(String prefix, String localName, String nsURI) throws XMLStreamException

    //public void writeEndElement() throws XMLStreamException

    @Override
    public void writeDefaultNamespace(String nsURI)
        throws XMLStreamException
    {
        if (!mStartElementOpen) {
            throwOutputError(ERR_NSDECL_WRONG_STATE);
        }
        // 27-Mar-2007, TSa: Apparently TCK expects a binding to be added
        setDefaultNamespace(nsURI);
        doWriteDefaultNs(nsURI);
    }

    @Override
    public void writeNamespace(String prefix, String nsURI)
        throws XMLStreamException
    {
        if (prefix == null || prefix.length() == 0
            || prefix.equals(XMLConstants.XMLNS_ATTRIBUTE)) {
            writeDefaultNamespace(nsURI);
            return;
        }

        // No need to set mAnyOutput, and shouldn't close the element.
        // But element needs to be open, obviously.
        if (!mStartElementOpen) {
            throwOutputError(ERR_NSDECL_WRONG_STATE);
        }
        /* 05-Feb-2005, TSa: Also, as per namespace specs; the 'empty'
         *   namespace URI can not be bound as a non-default namespace
         *   (ie. for any actual prefix)
         */
        /* 04-Feb-2005, TSa: Namespaces 1.1 does allow this, though,
         *   so for xml 1.1 documents we need to allow it
         */
        if (!mXml11) {
            if (nsURI.length() == 0) {
                throwOutputError(ErrorConsts.ERR_NS_EMPTY);
            }
            // 01-Apr-2005, TSa: Can we (and do we want to) verify NS consistency?
        }
        // 27-Mar-2007, TSa: Apparently TCK expects a binding to be added
        setPrefix(prefix, nsURI);
        doWriteNamespace(prefix, nsURI);
    }

    /*
    ////////////////////////////////////////////////////
    // Package methods:
    ////////////////////////////////////////////////////
     */

    @Override
    public void setDefaultNamespace(String uri) throws XMLStreamException
    {
        mCurrElem.setDefaultNsUri(uri);
    }

    @Override
    public void doSetPrefix(String prefix, String uri) throws XMLStreamException
    {
        mCurrElem.addPrefix(prefix, uri);
    }

    @Override
    public void writeStartElement(StartElement elem) throws XMLStreamException
    {
        QName name = elem.getName();
        @SuppressWarnings("unchecked")
        Iterator<Namespace> it = elem.getNamespaces();

        while (it.hasNext()) {
            Namespace ns = it.next();
            // First need to 'declare' namespace:
            String prefix = ns.getPrefix();
            if (prefix == null || prefix.length() == 0) {
                setDefaultNamespace(ns.getNamespaceURI());
            } else {
                setPrefix(prefix, ns.getNamespaceURI());
            }
        }

        /* Outputting element itself is fairly easy. The main question
         * is whether namespaces match. Let's use simple heuristics:
         * if writer is to do automatic prefix matching, let's only
         * pass explicit prefix (not default one); otherwise we'll
         * pass all parameters as is.
         */
        /* Quick check first though: if URI part of QName is null, it's
         * assumed element will just use whatever is current default
         * namespace....
         */
        String nsURI = name.getNamespaceURI();
        if (nsURI == null) {
            writeStartElement(name.getLocalPart());
        } else {
            String prefix = name.getPrefix();
            writeStartElement(prefix, name.getLocalPart(), nsURI);
        }

        // And now we need to output namespaces (including default), if any:
        @SuppressWarnings("unchecked")
        Iterator<Namespace> it2 = elem.getNamespaces();
        while (it2.hasNext()) {
            Namespace ns = it2.next();
            String prefix = ns.getPrefix();
            if (prefix == null || prefix.length() == 0) {
                writeDefaultNamespace(ns.getNamespaceURI());
            } else {
                writeNamespace(prefix, ns.getNamespaceURI());
            }
        }


        // And finally, need to output attributes as well:
        @SuppressWarnings("unchecked")
        Iterator<Attribute> ait = elem.getAttributes();
        while (ait.hasNext()) {
            Attribute attr = ait.next();
            name = attr.getName();
            nsURI = name.getNamespaceURI();
            // In non-default/empty namespace?
            if (nsURI != null && nsURI.length() > 0) {
                writeAttribute(name.getPrefix(), nsURI,
                               name.getLocalPart(), attr.getValue());
            } else {
                writeAttribute(name.getLocalPart(), attr.getValue());
            }
        }
    }

    //public void writeEndElement(QName name) throws XMLStreamException

    @Override
    protected void writeStartOrEmpty(String localName, String nsURI)
        throws XMLStreamException
    {
        // Need a prefix...
        String prefix = mCurrElem.getPrefix(nsURI);
        if (prefix == null) {
            throw new XMLStreamException("Unbound namespace URI '"+nsURI+"'");
        }
        checkStartElement(localName, prefix);
        if (mValidator != null) {
            mValidator.validateElementStart(localName, nsURI, prefix);
        }

        if (mOutputElemPool != null) {
            SimpleOutputElement newCurr = mOutputElemPool;
            mOutputElemPool = newCurr.reuseAsChild(mCurrElem, prefix, localName, nsURI);
            --mPoolSize;
            mCurrElem = newCurr;
        } else {
            mCurrElem = mCurrElem.createChild(prefix, localName, nsURI);
        }
        doWriteStartTag(prefix, localName);
    }

    @Override
    protected void writeStartOrEmpty(String prefix, String localName, String nsURI)
        throws XMLStreamException
    {
        checkStartElement(localName, prefix);
        if (mValidator != null) {
            mValidator.validateElementStart(localName, nsURI, prefix);
        }

        if (mOutputElemPool != null) {
            SimpleOutputElement newCurr = mOutputElemPool;
            mOutputElemPool = newCurr.reuseAsChild(mCurrElem, prefix, localName, nsURI);
            --mPoolSize;
            mCurrElem = newCurr;
        } else {
            mCurrElem = mCurrElem.createChild(prefix, localName, nsURI);
        }
        doWriteStartTag(prefix, localName);
    }

    /**
     * Element copier method implementation suitable to be used with
     * namespace-aware writers in non-repairing (explicit namespaces) mode.
     * The trickiest thing is having to properly
     * order calls to <code>setPrefix</code>, <code>writeNamespace</code>
     * and <code>writeStartElement</code>; the order writers expect is
     * bit different from the order in which element information is
     * passed in.
     */
    @Override
    public final void copyStartElement(InputElementStack elemStack,
            AttributeCollector attrCollector)
        throws IOException, XMLStreamException
    {
        // Any namespace declarations/bindings?
        int nsCount = elemStack.getCurrentNsCount();
        if (nsCount > 0) { // yup, got some...
            /* First, need to (or at least, should?) add prefix bindings:
             * (may not be 100% required, but probably a good thing to do,
             * just so that app code has access to prefixes then)
             */
            for (int i = 0; i < nsCount; ++i) {
                String prefix = elemStack.getLocalNsPrefix(i);
                String uri = elemStack.getLocalNsURI(i);
                if (prefix == null || prefix.length() == 0) { // default NS
                    setDefaultNamespace(uri);
                } else {
                    setPrefix(prefix, uri);
                }
            }
        }

        writeStartElement(elemStack.getPrefix(),
                elemStack.getLocalName(),
                elemStack.getNsURI());

        if (nsCount > 0) {
            // And then output actual namespace declarations:
            for (int i = 0; i < nsCount; ++i) {
                String prefix = elemStack.getLocalNsPrefix(i);
                String uri = elemStack.getLocalNsURI(i);

                if (prefix == null || prefix.length() == 0) { // default NS
                    writeDefaultNamespace(uri);
                } else {
                    writeNamespace(prefix, uri);
                }
            }
        }

        /* And then let's just output attributes, if any (whether to copy
         * implicit, aka "default" attributes, is configurable)
         */
        int attrCount = mCfgCopyDefaultAttrs ?
            attrCollector.getCount() :
            attrCollector.getSpecifiedCount();

        if (attrCount > 0) {
            for (int i = 0; i < attrCount; ++i) {
                attrCollector.writeAttribute(i, mWriter, mValidator);
            }
        }
    }

    @Override
    public String validateQNamePrefix(QName name)
    {
        // Good as is, let's not complicate things
        return name.getPrefix();
    }
}
