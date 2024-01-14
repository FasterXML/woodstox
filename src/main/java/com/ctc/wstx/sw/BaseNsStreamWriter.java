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

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;

import org.codehaus.stax2.ri.typed.AsciiValueEncoder;
import org.codehaus.stax2.validation.XMLValidationException;
import org.codehaus.stax2.validation.XMLValidationSchema;
import org.codehaus.stax2.validation.XMLValidator;

import com.ctc.wstx.api.EmptyElementHandler;
import com.ctc.wstx.api.WriterConfig;
import com.ctc.wstx.api.WstxInputProperties;
import com.ctc.wstx.cfg.ErrorConsts;
import com.ctc.wstx.exc.WstxIOException;
import com.ctc.wstx.util.DefaultXmlSymbolTable;

/**
 * Mid-level base class of namespace-aware stream writers. Contains
 * shared functionality between repairing and non-repairing implementations.
 */
public abstract class BaseNsStreamWriter
    extends TypedStreamWriter
{
    final protected static String sPrefixXml = DefaultXmlSymbolTable.getXmlSymbol();

    final protected static String sPrefixXmlns = DefaultXmlSymbolTable.getXmlnsSymbol();

    final protected static String ERR_NSDECL_WRONG_STATE =
        "Trying to write a namespace declaration when there is no open start element.";

    /*
    ///////////////////////////////////////////////////////////////////////
    // Configuration (options, features)
    ///////////////////////////////////////////////////////////////////////
     */

    // // // Additional specific config flags base class doesn't have

    /**
     * True, if writer needs to automatically output namespace declarations
     * (we are in repairing mode)
     */
    final protected boolean mAutomaticNS;
    
    final protected EmptyElementHandler mEmptyElementHandler;

    /*
    ///////////////////////////////////////////////////////////////////////
    // State information
    ///////////////////////////////////////////////////////////////////////
     */

    protected SimpleOutputElement mCurrElem = SimpleOutputElement.createRoot();

    /**
     * Optional "root" namespace context that application can set. If so,
     * it can be used to lookup namespace/prefix mappings
     */
    protected NamespaceContext mRootNsContext = null;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Pool for recycling SimpleOutputElement instances
    ///////////////////////////////////////////////////////////////////////
     */

    /* Note: although pooling of cheap objects like SimpleOutputElement
     * is usually not a good idea, here profiling showed it to be a
     * significant improvement. As long as instances are ONLY reused
     * within context of a single writer, they stay in cheap ("Eden")
     * GC area, and thus it should be a win.
     */
    protected SimpleOutputElement mOutputElemPool = null;

    /**
     * Although pooled objects are small, let's limit the pool size
     * nonetheless, to optimize memory usage for deeply nested
     * documents. In general, even just low number like 4 levels gets
     * decent return, but 8 should get 99% hit rate.
     */
    final static int MAX_POOL_SIZE = 8;

    protected int mPoolSize = 0;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Life-cycle (ctors)
    ///////////////////////////////////////////////////////////////////////
     */

    public BaseNsStreamWriter(XmlWriter xw, String enc, WriterConfig cfg,
                              boolean repairing)
    {
        super(xw, enc, cfg);
        mAutomaticNS = repairing;
        mEmptyElementHandler = cfg.getEmptyElementHandler();
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // XMLStreamWriter API
    ///////////////////////////////////////////////////////////////////////
     */

    @Override
    public NamespaceContext getNamespaceContext() {
        return mCurrElem;
    }

    @Override
    public String getPrefix(String uri) {
        return mCurrElem.getPrefix(uri);
    }

    @Override
    public abstract void setDefaultNamespace(String uri)
        throws XMLStreamException;

    /**
     *<p>
     * Note: Root namespace context works best if automatic prefix
     * creation ("namespace/prefix repairing" in StAX lingo) is enabled.
     */
    @Override
    public void setNamespaceContext(NamespaceContext ctxt)
        throws XMLStreamException
    {
        // This is only allowed before root element output:
        if (mState != STATE_PROLOG) {
            throwOutputError("Called setNamespaceContext() after having already output root element.");
        }

        mRootNsContext = ctxt;
        mCurrElem.setRootNsContext(ctxt);
    }

    @Override
    public void setPrefix(String prefix, String uri)
        throws XMLStreamException
    {
        if (prefix == null) {
            throw new NullPointerException("Can not pass null 'prefix' value");
        }
        // Are we actually trying to set the default namespace?
        if (prefix.length() == 0) {
            setDefaultNamespace(uri);
            return;
        }
        if (uri == null) {
            throw new NullPointerException("Can not pass null 'uri' value");
        }

        /* 25-Sep-2004, TSa: Let's check that "xml" and "xmlns" are not
         *     (re-)defined to any other value, nor that value they 
         *     are bound to are bound to other prefixes.
         */
        /* 01-Apr-2005, TSa: And let's not leave it optional: such
         *   bindings should never succeed.
         */
        // ... perhaps it really should be optional though?
        {
            if (prefix.equals(sPrefixXml)) { // prefix "xml"
                if (!uri.equals(XMLConstants.XML_NS_URI)) {
                    throwOutputError(ErrorConsts.ERR_NS_REDECL_XML, uri);
                }
            } else if (prefix.equals(sPrefixXmlns)) { // prefix "xmlns"
                if (!uri.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)) {
                    throwOutputError(ErrorConsts.ERR_NS_REDECL_XMLNS, uri);
                }
            } else {
                // Neither of prefixes.. but how about URIs?
                if (uri.equals(XMLConstants.XML_NS_URI)) {
                    throwOutputError(ErrorConsts.ERR_NS_REDECL_XML_URI, prefix);
                } else if (uri.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)) {
                    throwOutputError(ErrorConsts.ERR_NS_REDECL_XMLNS_URI, prefix);
                }
            }
            
            /* 05-Feb-2005, TSa: Also, as per namespace specs; the 'empty'
             *   namespace URI can not be bound as a non-default namespace
             *   (ie. for any actual prefix)
             */
            /* 04-Feb-2005, TSa: Namespaces 1.1 does allow this, though,
             *   so for xml 1.1 documents we need to allow it
             */
            if (!mXml11) {
                if (uri.length() == 0) {
                    throwOutputError(ErrorConsts.ERR_NS_EMPTY);
                }
            }
        }

        doSetPrefix(prefix, uri);
    }

    /**
     * It's assumed calling this method implies caller just wants to add
     * an attribute that does not belong to any namespace; as such no
     * namespace checking or prefix generation is needed.
     */
    @Override
    public void writeAttribute(String localName, String value)
        throws XMLStreamException
    {
        // No need to set mAnyOutput, nor close the element
        if (!mStartElementOpen && mCheckStructure) {
            reportNwfStructure(ErrorConsts.WERR_ATTR_NO_ELEM);
        }
        doWriteAttr(localName, null, null, value);
    }

    @Override
    public abstract void writeAttribute(String nsURI, String localName, String value)
        throws XMLStreamException;

    @Override
    public abstract void writeAttribute(String prefix, String nsURI,
            String localName, String value)
        throws XMLStreamException;

    /**
     *<p>
     * Note: It is assumed caller just wants the element to belong to whatever
     * is the current default namespace.
     */
    @Override
    public void writeEmptyElement(String localName)
        throws XMLStreamException
    {
        checkStartElement(localName, null);
        mEmptyElement = true;
        if (mOutputElemPool != null) {
            SimpleOutputElement newCurr = mOutputElemPool;
            mOutputElemPool = newCurr.reuseAsChild(mCurrElem, localName);
            --mPoolSize;
            mCurrElem = newCurr;
        } else {
            mCurrElem = mCurrElem.createChild(localName);
        }
        doWriteStartTag(localName);

    }

    @Override
    public void writeEmptyElement(String nsURI, String localName)
        throws XMLStreamException
    {
        writeStartOrEmpty(localName, nsURI);
        mEmptyElement = true;
    }

    @Override
    public void writeEmptyElement(String prefix, String localName, String nsURI)
        throws XMLStreamException
    {
        writeStartOrEmpty(prefix, localName, nsURI);
        mEmptyElement = true;
    }

    @Override
    public void writeEndElement()
        throws XMLStreamException
    {
        doWriteEndTag(null, mCfgAutomaticEmptyElems);
    }

    /**
     * This method is assumed to just use default namespace (if any),
     * and no further checks should be done.
     */
    @Override
    public void writeStartElement(String localName)
        throws XMLStreamException
    {
        checkStartElement(localName, null);
        mEmptyElement = false;
        if (mOutputElemPool != null) {
            SimpleOutputElement newCurr = mOutputElemPool;
            mOutputElemPool = newCurr.reuseAsChild(mCurrElem, localName);
            --mPoolSize;
            mCurrElem = newCurr;
        } else {
            mCurrElem = mCurrElem.createChild(localName);
        }

        doWriteStartTag(localName);
    }

    @Override
    public void writeStartElement(String nsURI, String localName)
        throws XMLStreamException
    {
        writeStartOrEmpty(localName, nsURI);
        mEmptyElement = false;
    }

    @Override
    public void writeStartElement(String prefix, String localName, String nsURI)
        throws XMLStreamException
    {
        writeStartOrEmpty(prefix, localName, nsURI);
        mEmptyElement = false;
    }

    @Override
    protected void writeTypedAttribute(String prefix, String nsURI, String localName,
                                       AsciiValueEncoder enc)
        throws XMLStreamException
    {
        if (!mStartElementOpen) {
            throwOutputError(ErrorConsts.WERR_ATTR_NO_ELEM);
        }
        try {
            if (mValidator == null) {
                 if (mCheckAttrs) { // still need to ensure no duplicate attrs?
                     mCurrElem.addAttribute(nsURI, localName, null, null);
                 }
                 if (prefix == null || prefix.length() == 0) {
                     mWriter.writeTypedAttribute(localName, enc);
                 } else {
                     mWriter.writeTypedAttribute(prefix, localName, enc);
                 }
            } else {
                mWriter.writeTypedAttribute
                    (prefix, localName, nsURI, enc, mCurrElem.getAttributeCollector(), getCopyBuffer());
            }
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Remaining XMLStreamWriter2 methods (StAX2)
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Similar to {@link #writeEndElement}, but never allows implicit
     * creation of empty elements.
     */
    @Override
    public void writeFullEndElement()
        throws XMLStreamException
    {
        doWriteEndTag(null, false);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Remaining ValidationContext methods (StAX2)
    ///////////////////////////////////////////////////////////////////////
     */

    @Override
    public QName getCurrentElementName() {
        return mCurrElem.getName();
    }

    @Override
    public String getNamespaceURI(String prefix) {
        return mCurrElem.getNamespaceURI(prefix);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Implementations for base-class defined abstract methods
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Method called by {@link javax.xml.stream.XMLEventWriter} implementation
     * (instead of the version
     * that takes no argument), so that we can verify it does match the
     * start element, if necessary
     */
    @Override
    public void writeEndElement(QName name)
        throws XMLStreamException
    {
        doWriteEndTag(mCheckStructure ? name : null, mCfgAutomaticEmptyElems);
    }

    /**
     * Method called to close an open start element, when another
     * main-level element (not namespace declaration or attribute)
     * is being output; except for end element which is handled differently.
     *
     * @param emptyElem If true, the element being closed is an empty
     *   element; if false, a separate stand-alone start element.
     */
    @Override
    protected void closeStartElement(boolean emptyElem)
        throws XMLStreamException
    {
        mStartElementOpen = false;

        try {
            if (emptyElem) {
                mWriter.writeStartTagEmptyEnd();
            } else {
                mWriter.writeStartTagEnd();
            }
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }

        if (mValidator != null) {
            try {
                mVldContent = mCurrElem.validateElementStartAndAttributes();
                if (emptyElem) {
                    mVldContent = mValidator.validateElementEnd
                            (mCurrElem.getLocalName(), mCurrElem.getNamespaceURI(), mCurrElem.getPrefix());
                }
            } catch (XMLValidationException e) {
                mVldException = e;
                throw e;
            }
        }

        // Need bit more special handling for empty elements...
        if (emptyElem) {
            SimpleOutputElement curr = mCurrElem;
            mCurrElem = curr.getParent();
            if (mCurrElem.isRoot()) { // Did we close the root? (isRoot() returns true for the virtual "document node")
                mState = STATE_EPILOG;
            }
            if (mPoolSize < MAX_POOL_SIZE) {
                curr.addToPool(mOutputElemPool);
                mOutputElemPool = curr;
                ++mPoolSize;
            }
        }
    }

    @Override
    protected String getTopElementDesc() {
        return mCurrElem.getNameDesc();
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Package methods sub-classes may also need
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Method that is called to ensure that we can start writing an
     * element, both from structural point of view, and from syntactic
     * (close previously open start element, if any).
     */
    protected void checkStartElement(String localName, String prefix)
        throws XMLStreamException
    {
        if (mVldException != null) {
            throw new XMLStreamException("Cannot start an element after a validation error", mVldException);
        }
        // Need to finish an open start element?
        if (mStartElementOpen) {
            closeStartElement(mEmptyElement);
        } else if (mState == STATE_PROLOG) {
            verifyRootElement(localName, prefix);
        } else if (mState == STATE_EPILOG) {
            if (mCheckStructure) {
                String name = (prefix == null || prefix.length() == 0) ?
                    localName : (prefix + ":" + localName);
                reportNwfStructure(ErrorConsts.WERR_PROLOG_SECOND_ROOT, name);
            }
            /* When outputting a fragment, need to reset this to the
             * tree. No point in trying to verify the root element?
             */
            mState = STATE_TREE;
        }
    }

    protected final void doWriteAttr(String localName, String nsURI, String prefix,
            String value)
        throws XMLStreamException
    {
        if (mCheckAttrs) {
            // ensure no duplicate attrs and possibly pass them to validator when closing the start element
            try {
                mCurrElem.addAttribute(nsURI, localName, prefix, value);
            } catch (XMLValidationException e) {
                mVldException = e;
                throw e;
            }
        }
        try {
            int vlen = value.length();
            // Worthwhile to make a local copy?
            if (vlen >= ATTR_MIN_ARRAYCOPY) {
                char[] buf = mCopyBuffer;
                if (buf == null) {
                    mCopyBuffer = buf = mConfig.allocMediumCBuffer(DEFAULT_COPYBUFFER_LEN);
                }
                /* Ok, and in unlikely case of attribute values longer than
                 * buffer... for now, let's just skip those case
                 */
                if (vlen <= buf.length) {
                    value.getChars(0, vlen, buf, 0);
                    if (prefix != null && prefix.length() > 0) {
                        mWriter.writeAttribute(prefix, localName, buf, 0, vlen);
                    } else {
                        mWriter.writeAttribute(localName, buf, 0, vlen);
                    }
                    return;
                }
            }
            if (prefix != null && prefix.length() > 0) {
                mWriter.writeAttribute(prefix, localName, value);
            } else {
                mWriter.writeAttribute(localName, value);
            }
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }
    }

    protected void doWriteNamespace(String prefix, String nsURI)
        throws XMLStreamException
    {
        try {
            int vlen = nsURI.length();
            // Worthwhile to make a local copy?
            if (vlen >= ATTR_MIN_ARRAYCOPY) {
                char[] buf = mCopyBuffer;
                if (buf == null) {
                    mCopyBuffer = buf = mConfig.allocMediumCBuffer(DEFAULT_COPYBUFFER_LEN);
                }
                // Let's not bother with too long, though
                if (vlen <= buf.length) {
                    nsURI.getChars(0, vlen, buf, 0);
                    mWriter.writeAttribute(XMLConstants.XMLNS_ATTRIBUTE, prefix, buf, 0, vlen);
                    return;
                }
            }
            mWriter.writeAttribute(XMLConstants.XMLNS_ATTRIBUTE, prefix, nsURI);
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }
    }

    protected void doWriteDefaultNs(String nsURI)
        throws XMLStreamException
    {
        try {
            int vlen = (nsURI == null) ? 0 : nsURI.length();
            // Worthwhile to make a local copy?
            if (vlen >= ATTR_MIN_ARRAYCOPY) {
                char[] buf = mCopyBuffer;
                if (buf == null) {
                    mCopyBuffer = buf = mConfig.allocMediumCBuffer(DEFAULT_COPYBUFFER_LEN);
                }
                // Let's not bother with too long, though
                if (vlen <= buf.length) {
                    nsURI.getChars(0, vlen, buf, 0);
                    mWriter.writeAttribute(XMLConstants.XMLNS_ATTRIBUTE, buf, 0, vlen);
                    return;
                }
            }
            mWriter.writeAttribute(XMLConstants.XMLNS_ATTRIBUTE, nsURI);
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }
    }

    protected final void doWriteStartTag(String localName)
        throws XMLStreamException
    {
        mAnyOutput = true;
        mStartElementOpen = true;
        try {
            mWriter.writeStartTagStart(localName);
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }
    }

    protected final void doWriteStartTag(String prefix, String localName)
        throws XMLStreamException
    {
        mAnyOutput = true;
        mStartElementOpen = true;
        try {
            boolean hasPrefix = (prefix != null && prefix.length() > 0);
            if (hasPrefix) {
                mWriter.writeStartTagStart(prefix, localName);
            } else {
                mWriter.writeStartTagStart(localName);
            }
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }
    }

    /**
     *
     * @param expName Name that the closing element should have; null
     *   if whatever is in stack should be used
     * @param allowEmpty If true, is allowed to create the empty element
     *   if the closing element was truly empty; if false, has to write
     *   the full empty element no matter what
     */
    protected void doWriteEndTag(QName expName, boolean allowEmpty)
        throws XMLStreamException
    {
        /* First of all, do we need to close up an earlier empty element?
         * (open start element that was not created via call to
         * writeEmptyElement gets handled later on)
         */
        if (mStartElementOpen && mEmptyElement) {
            mEmptyElement = false;
            closeStartElement(true);
        }

        // Better have something to close... (to figure out what to close)
        if (mVldException != null) {
            throw new XMLStreamException("Cannot start an element after a validation error", mVldException);
        } else if (mState != STATE_TREE) {
            // Have to always throw exception... don't necessarily know the name
            reportNwfStructure("No open start element, when trying to write end element");
        }

        if (mStartElementOpen) {
            if (mValidator != null) {
                // We need to validate here, before we move the mCurrElem 
                try {
                    /* Note: return value is not of much use, since the
                     * element will be closed right away...
                     */
                    mVldContent = mCurrElem.validateElementStartAndAttributes();
                } catch (XMLValidationException e) {
                    mVldException = e;
                    throw e;
                }
            }
        }

        SimpleOutputElement thisElem = mCurrElem;
        String prefix = thisElem.getPrefix();
        String localName = thisElem.getLocalName();
        String nsURI = thisElem.getNamespaceURI();

        // Ok, and then let's pop that element from the stack
        mCurrElem = thisElem.getParent();
        // Need to return the instance to pool?
        if (mPoolSize < MAX_POOL_SIZE) {
            thisElem.addToPool(mOutputElemPool);
            mOutputElemPool = thisElem;
            ++mPoolSize;
        }

        if (mCheckStructure) {
            if (expName != null) {
                // Let's only check the local name, for now...
                if (!localName.equals(expName.getLocalPart())) {
                    /* Only gets called when trying to output an XMLEvent... in
                     * which case names can actually be compared
                     */
                    reportNwfStructure("Mismatching close element local name, '"+localName+"'; expected '"+expName.getLocalPart()+"'.");
                }
            }
        }

        /* Now, do we have an unfinished start element (created via
         * writeStartElement() earlier)?
         */
        if (mStartElementOpen) {
            /* Can't/shouldn't call closeStartElement, but need to do same
             * processing. Thus, this is almost identical to closeStartElement:
             */
            mStartElementOpen = false;
            try {
                //If an EmptyElementHandler is provided use it to determine if allowEmpty is set
                if (mEmptyElementHandler != null) {
                    allowEmpty = mEmptyElementHandler.allowEmptyElement(prefix, localName, nsURI, allowEmpty);
                }
                // We could write an empty element, implicitly?
                if (allowEmpty) {
                    mWriter.writeStartTagEmptyEnd();
                    if (mCurrElem.isRoot()) {
                        mState = STATE_EPILOG;
                    }
                    if (mValidator != null) {
                        try {
                            mVldContent = mValidator.validateElementEnd(localName, nsURI, prefix);
                        } catch (XMLValidationException e) {
                            mVldException = e;
                            throw e;
                        }
                    }
                    return;
                }
                // Nah, need to close open elem, and then output close elem
                mWriter.writeStartTagEnd();
            } catch (IOException ioe) {
                throw new WstxIOException(ioe);
            }
        }

        try {
            mWriter.writeEndTag(prefix, localName);
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }

        if (mCurrElem.isRoot()) {
            mState = STATE_EPILOG;
        }

        // Ok, time to validate...
        if (mValidator != null) {
            try {
                mVldContent = mValidator.validateElementEnd(localName, nsURI, prefix);
            } catch (XMLValidationException e) {
                mVldException = e;
                throw e;
            }
        }
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // More abstract methods for sub-classes to implement
    ///////////////////////////////////////////////////////////////////////
     */

    public abstract void doSetPrefix(String prefix, String uri)
        throws XMLStreamException;

    @Override
    public abstract void writeDefaultNamespace(String nsURI)
        throws XMLStreamException;

    @Override
    public abstract void writeNamespace(String prefix, String nsURI)
        throws XMLStreamException;

    @Override
    public abstract void writeStartElement(StartElement elem)
        throws XMLStreamException;

    protected abstract void writeStartOrEmpty(String localName, String nsURI)
        throws XMLStreamException;

    protected abstract void writeStartOrEmpty(String prefix, String localName, String nsURI)
        throws XMLStreamException;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Attribute access
    ///////////////////////////////////////////////////////////////////////
     */

    @Override
    public int getAttributeCount()
    {
        return mCurrElem.getAttributeCount();
    }

    @Override
    public String getAttributeLocalName(int index)
    {
        return mCurrElem.getAttributeLocalName(index);
    }

    @Override
    public String getAttributeNamespace(int index)
    {
        return mCurrElem.getAttributeNamespace(index);
    }

    @Override
    public String getAttributePrefix(int index)
    {
        return mCurrElem.getAttributePrefix(index);
    }

    @Override
    public String getAttributeValue(int index)
    {
        return mCurrElem.getAttributeValue(index);
    }

    @Override
    public String getAttributeValue(String nsURI, String localName)
    {
        return mCurrElem.getAttributeValue(nsURI, localName);
    }

    @Override
    public String getAttributeType(int index) {
        return (mValidator == null) ? WstxInputProperties.UNKNOWN_ATTR_TYPE :
            mValidator.getAttributeType(index);
    }

    @Override
    public int findAttributeIndex(String nsURI, String localName)
    {
        return mCurrElem.findAttributeIndex(nsURI, localName);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Overrides to keep the validator up to date in SimpleOutputElement instances
    ///////////////////////////////////////////////////////////////////////
     */

    @Override
    public XMLValidator validateAgainst(XMLValidationSchema schema) throws XMLStreamException {
        final XMLValidator validateAgainst = super.validateAgainst(schema);
        mCurrElem.setValidator(mValidator);
        if (mOutputElemPool != null) {
            mOutputElemPool.setValidator(mValidator);
        }
        return validateAgainst;
    }

    @Override
    public XMLValidator stopValidatingAgainst(XMLValidationSchema schema) throws XMLStreamException {
        final XMLValidator result = super.stopValidatingAgainst(schema);
        mCurrElem.setValidator(mValidator);
        if (mOutputElemPool != null) {
            mOutputElemPool.setValidator(mValidator);
        }
        return result;
    }

    @Override
    public XMLValidator stopValidatingAgainst(XMLValidator validator) throws XMLStreamException {
        final XMLValidator result = super.stopValidatingAgainst(validator);
        mCurrElem.setValidator(mValidator);
        if (mOutputElemPool != null) {
            mOutputElemPool.setValidator(mValidator);
        }
        return result;
    }


}
