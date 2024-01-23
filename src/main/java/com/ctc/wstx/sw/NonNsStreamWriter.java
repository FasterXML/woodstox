/* Woodstox XML processor
 *
 * Copyright (c) 2004- Tatu Saloranta, tatu.saloranta@iki.fi
 *
 * Licensed under the License specified in the file LICENSE which is
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
import java.util.*;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;

import org.codehaus.stax2.ri.typed.AsciiValueEncoder;
import org.codehaus.stax2.validation.XMLValidationException;
import org.codehaus.stax2.validation.XMLValidationSchema;
import org.codehaus.stax2.validation.XMLValidator;

import com.ctc.wstx.api.WriterConfig;
import com.ctc.wstx.api.WstxInputProperties;
import com.ctc.wstx.cfg.ErrorConsts;
import com.ctc.wstx.cfg.XmlConsts;
import com.ctc.wstx.sr.AttributeCollector;
import com.ctc.wstx.sr.InputElementStack;
import com.ctc.wstx.util.EmptyNamespaceContext;
import com.ctc.wstx.util.StringVector;

/**
 * Implementation of {@link XMLStreamWriter} used when namespace support
 * is not enabled. This means that only local names are used for elements
 * and attributes; and if rudimentary namespace declarations need to be
 * output, they are output using attribute writing methods.
 */
public class NonNsStreamWriter
    extends TypedStreamWriter
{
    /*
    ////////////////////////////////////////////////////
    // State information
    ////////////////////////////////////////////////////
     */

    /**
     * Stack of currently open start elements; only local names
     * are included.
     */
    final StringVector mElements;

    /**
     * Attributes of the current element; used
     * if uniqueness of attribute names is to be enforced
     * or if a validator is set.
     */
    private HashMap<String, Attribute> mAttrMap;
    private ArrayList<Attribute> mAttrList;
    private AttrCollector mAttrCollector;

    /*
    ////////////////////////////////////////////////////
    // Life-cycle (ctors)
    ////////////////////////////////////////////////////
     */

    public NonNsStreamWriter(XmlWriter xw, String enc, WriterConfig cfg)
    {
        super(xw, enc, cfg);
        mElements = new StringVector(32);
    }

    /*
    ////////////////////////////////////////////////////
    // XMLStreamWriter API
    ////////////////////////////////////////////////////
     */

    @Override
    public NamespaceContext getNamespaceContext() {
        return EmptyNamespaceContext.getInstance();
    }

    @Override
    public String getPrefix(String uri) {
        return null;
    }

    @Override
    public void setDefaultNamespace(String uri)
        throws XMLStreamException
    {
        reportIllegalArg("Can not set default namespace for non-namespace writer.");
    }

    @Override
    public void setNamespaceContext(NamespaceContext context) {
        reportIllegalArg("Can not set NamespaceContext for non-namespace writer.");
    }

    @Override
    public void setPrefix(String prefix, String uri) throws XMLStreamException
    {
        reportIllegalArg("Can not set namespace prefix for non-namespace writer.");
    }

    @Override
    public void writeAttribute(String localName, String value)
        throws XMLStreamException
    {
        // No need to set mAnyOutput, nor close the element
        if (!mStartElementOpen && mCheckStructure) {
            reportNwfStructure(ErrorConsts.WERR_ATTR_NO_ELEM);
        }
        if (mCheckAttrs) {
            /* 11-Dec-2005, TSa: Should use a more efficient Set/Map value
             *   for this in future.
             */
            addAttribute(localName, value);
        }

        try {
            mWriter.writeAttribute(localName, value);
        } catch (IOException ioe) {
            throwFromIOE(ioe);
        }
    }

    @Override
    public void writeAttribute(String nsURI, String localName, String value)
        throws XMLStreamException
    {
        writeAttribute(localName, value);
    }

    @Override
    public void writeAttribute(String prefix, String nsURI,
            String localName, String value)
        throws XMLStreamException
    {
        writeAttribute(localName, value);
    }

    @Override
    public void writeDefaultNamespace(String nsURI) throws XMLStreamException
    {
        reportIllegalMethod("Can not call writeDefaultNamespace namespaces with non-namespace writer.");
    }

    @Override
    public void writeEmptyElement(String localName) throws XMLStreamException
    {
        doWriteStartElement(localName);
        mEmptyElement = true;
    }

    @Override
    public void writeEmptyElement(String nsURI, String localName) throws XMLStreamException
    {
        writeEmptyElement(localName);
    }

    @Override
    public void writeEmptyElement(String prefix, String localName, String nsURI) throws XMLStreamException
    {
        writeEmptyElement(localName);
    }

    @Override
    public void writeEndElement() throws XMLStreamException {
        doWriteEndTag(null, mCfgAutomaticEmptyElems);
    }

    @Override
    public void writeNamespace(String prefix, String nsURI) throws XMLStreamException
    {
        reportIllegalMethod("Can not set write namespaces with non-namespace writer.");
    }

    @Override
    public void writeStartElement(String localName) throws XMLStreamException
    {
        doWriteStartElement(localName);
        mEmptyElement = false;
    }

    @Override
    public void writeStartElement(String nsURI, String localName) throws XMLStreamException {
        writeStartElement(localName);
    }

    @Override
    public void writeStartElement(String prefix, String localName, String nsURI)
        throws XMLStreamException
    {
        writeStartElement(localName);
    }

    /*
    ////////////////////////////////////////////////////
    // Remaining XMLStreamWriter2 methods (StAX2)
    ////////////////////////////////////////////////////
     */

    /**
     * Similar to {@link #writeEndElement}, but never allows implicit
     * creation of empty elements.
     */
    @Override
    public void writeFullEndElement() throws XMLStreamException {
        doWriteEndTag(null, false);
    }
    
    /*
    ////////////////////////////////////////////////////
    // Remaining ValidationContext methods (StAX2)
    ////////////////////////////////////////////////////
     */

    @Override
    public QName getCurrentElementName() {
        if (mElements.isEmpty()) {
            return null;
        }
        return new QName(mElements.getLastString());
    }

    @Override
    public String getNamespaceURI(String prefix) {
        return null;
    }

    /*
    ////////////////////////////////////////////////////
    // Package methods:
    ////////////////////////////////////////////////////
     */

    @Override
	public void writeStartElement(StartElement elem)
        throws XMLStreamException
    {
        QName name = elem.getName();
        writeStartElement(name.getLocalPart());
        @SuppressWarnings("unchecked")
        Iterator<javax.xml.stream.events.Attribute> it = elem.getAttributes();
        while (it.hasNext()) {
            javax.xml.stream.events.Attribute attr = it.next();
            name = attr.getName();
            writeAttribute(name.getLocalPart(), attr.getValue());
        }
    }

    /**
     * Method called by {@link javax.xml.stream.XMLEventWriter} implementation
     * (instead of the version
     * that takes no argument), so that we can verify it does match the
     * start element, if necessary
     */
    @Override
    public void writeEndElement(QName name) throws XMLStreamException
    {
        doWriteEndTag(mCheckStructure ? name.getLocalPart() : null,
                      mCfgAutomaticEmptyElems);
    }

    @Override
    protected void writeTypedAttribute(String prefix, String nsURI, String localName,
                                       AsciiValueEncoder enc)
        throws XMLStreamException
    {
        // note: mostly copied from the other writeAttribute() method..
        if (!mStartElementOpen && mCheckStructure) {
            reportNwfStructure(ErrorConsts.WERR_ATTR_NO_ELEM);
        }

        try {
            if (!mCheckAttrs) {
                mWriter.writeTypedAttribute(localName, enc);
            } else {
                if (mAttrCollector == null) {
                    mAttrCollector = new AttrCollector();
                }
                // mAttributes just checks the uniqueness of the attribute name and stores the name and value.
                // We will pass it to the real validator later
                mWriter.writeTypedAttribute(null, localName, null, enc, mAttrCollector, getCopyBuffer());
            }
        } catch (IOException ioe) {
            throwFromIOE(ioe);
        }
    }

    /**
     * Method called to close an open start element, when another
     * main-level element (not namespace declaration or
     * attribute) is being output; except for end element which is
     * handled differently.
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
            throwFromIOE(ioe);
        }

        if (mValidator != null) {
            String localName = mElements.getLastString();
            try {
                mVldContent = validateElementStartAndAttributes(localName);
                if (emptyElem) {
                    mVldContent = mValidator.validateElementEnd(localName, XmlConsts.ELEM_NO_NS_URI, XmlConsts.ELEM_NO_PREFIX);
                }
            } catch (XMLValidationException e) {
                mVldException = e;
                throw e;
            }
        } else if (mCheckAttrs) {
            mAttrMap = null;
            mAttrList = null;
        }

        // Need bit more special handling for empty elements...
        if (emptyElem) {
            mElements.removeLast();
            if (mElements.isEmpty()) {
                mState = STATE_EPILOG;
            }
        }
    }

    /**
     * Element copier method implementation suitable to be used with
     * non-namespace-aware writers. The only special thing here is that
     * the copier can convert namespace declarations to equivalent
     * attribute writes.
     */
    @Override
    public void copyStartElement(InputElementStack elemStack,
                                 AttributeCollector attrCollector)
        throws IOException, XMLStreamException
    {
        String ln = elemStack.getLocalName();
        boolean nsAware = elemStack.isNamespaceAware();
        
        /* First, since we are not to output namespace stuff as is,
         * we just need to copy the element:
         */
        if (nsAware) { // but reader is ns-aware? Need to add prefix?
            String prefix = elemStack.getPrefix();
            if (prefix != null && prefix.length() > 0) { // yup
                ln = prefix + ":" + ln;
            }
        }
        writeStartElement(ln);
        
        /* However, if there are any namespace declarations, we probably
         * better output them just as 'normal' attributes:
         */
        if (nsAware) {
            int nsCount = elemStack.getCurrentNsCount();
            if (nsCount > 0) {
                for (int i = 0; i < nsCount; ++i) {
                    String prefix = elemStack.getLocalNsPrefix(i);
                    if (prefix == null || prefix.length() == 0) { // default NS decl
                        prefix = XMLConstants.XML_NS_PREFIX;
                    } else {
                        prefix = "xmlns:"+prefix;
                    }
                    writeAttribute(prefix, elemStack.getLocalNsURI(i));
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
                if (mCheckAttrs) {
                    if (mAttrCollector == null) {
                        mAttrCollector = new AttrCollector();
                    }
                    attrCollector.writeAttribute(i, mWriter, mAttrCollector);
                } else {
                    attrCollector.writeAttribute(i, mWriter, null);
                }
            }
        }
    }

    @Override
    protected String getTopElementDesc() {
        return mElements.isEmpty() ? "#root" : mElements.getLastString();
    }

    @Override
    public String validateQNamePrefix(QName name) {
        // Can either strip prefix out, or return as is
        return name.getPrefix();
    }

    // // // Attribute access

    @Override
    public int getAttributeCount() {
        return mAttrList == null ? 0 : mAttrList.size();
    }

    @Override
    public String getAttributeLocalName(int index) {
        return mAttrList == null ? null : mAttrList.get(index).mLocalName;
    }

    @Override
    public String getAttributeNamespace(int index)
    {
        return XmlConsts.ATTR_NO_NS_URI;
    }

    @Override
    public String getAttributePrefix(int index)
    {
        return XmlConsts.ATTR_NO_PREFIX;
    }

    @Override
    public String getAttributeValue(int index) {
        return mAttrList == null ? null : mAttrList.get(index).mValue;
    }

    @Override
    public String getAttributeValue(String nsURI, String localName) {
        return mAttrMap == null ? null : mAttrMap.get(localName).mValue;
    }

    @Override
    public int findAttributeIndex(String nsURI, String localName) {
        return mAttrMap == null ? null : mAttrMap.get(localName).mIndex;
    }

    @Override
    public String getAttributeType(int index) {
        return (mValidator == null) ? WstxInputProperties.UNKNOWN_ATTR_TYPE :
            mValidator.getAttributeType(index);
    }

    /*
    ////////////////////////////////////////////////////
    // Internal methods
    ////////////////////////////////////////////////////
     */

    private void doWriteStartElement(String localName)
        throws XMLStreamException
    {
        if (mVldException != null) {
            throw new XMLStreamException("Cannot start an element after a validation error", mVldException);
        }
        mAnyOutput = true;
        // Need to finish an open start element?
        if (mStartElementOpen) {
            closeStartElement(mEmptyElement);
        } else if (mState == STATE_PROLOG) {
            // 20-Dec-2005, TSa: Does this match DOCTYPE declaration?
            verifyRootElement(localName, null);
        } else if (mState == STATE_EPILOG) {
            if (mCheckStructure) {
                reportNwfStructure(ErrorConsts.WERR_PROLOG_SECOND_ROOT, localName);
            }
            // Outputting fragment? Better reset to tree, then...
            mState = STATE_TREE;
        }

        /* Note: need not check for CONTENT_ALLOW_NONE here, since the
         * validator should handle this particular case...
         */
        /*if (mVldContent == XMLValidator.CONTENT_ALLOW_NONE) { // EMPTY content
            reportInvalidContent(START_ELEMENT);
            }*/

        mStartElementOpen = true;
        mElements.addString(localName);
        try {
            mWriter.writeStartTagStart(localName);
        } catch (IOException ioe) {
            throwFromIOE(ioe);
        }
    }

    /**
     *<p>
     * Note: Caller has to do actual removal of the element from element
     * stack, before calling this method.
     *
     * @param expName Name that the closing element should have; null
     *   if whatever is in stack should be used
     * @param allowEmpty If true, is allowed to create the empty element
     *   if the closing element was truly empty; if false, has to write
     *   the full empty element no matter what
     */
    private void doWriteEndTag(String expName, boolean allowEmpty)
        throws XMLStreamException
    {
        if (mVldException != null) {
            throw new XMLStreamException("Cannot start an element after a validation error", mVldException);
        }
        /* First of all, do we need to close up an earlier empty element?
         * (open start element that was not created via call to
         * writeEmptyElement gets handled later on)
         */
        if (mStartElementOpen && mEmptyElement) {
            mEmptyElement = false;
            // note: this method guarantees proper updates to validation
            closeStartElement(true);
        }

        // Better have something to close... (to figure out what to close)
        if (mState != STATE_TREE) {
            // Have to throw an exception always, don't know elem name
            reportNwfStructure("No open start element, when trying to write end element");
        }

        /* Now, do we have an unfinished start element (created via
         * writeStartElement() earlier)?
         */
        String localName = mElements.removeLast();
        if (mCheckStructure) {
            if (expName != null && !localName.equals(expName)) {
                /* Only gets called when trying to output an XMLEvent... in
                 * which case names can actually be compared
                 */
                reportNwfStructure("Mismatching close element name, '"+localName+"'; expected '"+expName+"'.");
            }
        }

        /* Can't yet validate, since we have two paths; one for empty
         * elements, another for non-empty...
         */

        // Got a half output start element to close?
        if (mStartElementOpen) {
            /* Can't/shouldn't call closeStartElement, but need to do same
             * processing. Thus, this is almost identical to closeStartElement:
             */
            mStartElementOpen = false;
            try {
                // We could write an empty element, implicitly?
                if (allowEmpty) {
                    mWriter.writeStartTagEmptyEnd();
                    if (mElements.isEmpty()) {
                        mState = STATE_EPILOG;
                    }
                    if (mValidator != null) {
                        try {
                            /* Note: return value is not of much use, since the
                             * element will be closed right away...
                             */
                            mVldContent = validateElementStartAndAttributes(localName);
                            mVldContent = mValidator.validateElementEnd(localName, XmlConsts.ELEM_NO_NS_URI, XmlConsts.ELEM_NO_PREFIX);
                        } catch (XMLValidationException e) {
                            mVldException = e;
                            throw e;
                        }
                    }
                    return;
                }
                if (mValidator != null) {
                    try {
                        /* Note: return value is not of much use, since the
                         * element will be closed right away...
                         */
                        mVldContent = validateElementStartAndAttributes(localName);
                    } catch (XMLValidationException e) {
                        mVldException = e;
                        throw e;
                    }
                } else if (mCheckAttrs) {
                    mAttrMap = null;
                    mAttrList = null;
                }
                // Nah, need to close open elem, and then output close elem
                mWriter.writeStartTagEnd();
            } catch (IOException ioe) {
                throwFromIOE(ioe);
            }
        }

        try {
            mWriter.writeEndTag(localName);
        } catch (IOException ioe) {
            throwFromIOE(ioe);
        }

        if (mElements.isEmpty()) {
            mState = STATE_EPILOG;
        }

        // Ok, time to validate...
        if (mValidator != null) {
            try {
                mVldContent = mValidator.validateElementEnd(localName, XmlConsts.ELEM_NO_NS_URI, XmlConsts.ELEM_NO_PREFIX);
            } catch (XMLValidationException e) {
                mVldException = e;
                throw e;
            }
        }
    }

    private int validateElementStartAndAttributes(String localName) throws XMLStreamException {
        final XMLValidator vld = mValidator;
        vld.validateElementStart(localName, XmlConsts.ELEM_NO_NS_URI, XmlConsts.ELEM_NO_PREFIX);
        ArrayList<Attribute> attrList = mAttrList;
        if (attrList != null)  {
            mAttrMap = null;
            mAttrList = null;
            if (!attrList.isEmpty()) {
                for (Attribute attr : attrList) {
                    vld.validateAttribute(attr.mLocalName, XmlConsts.ATTR_NO_NS_URI, XmlConsts.ATTR_NO_PREFIX, attr.mValue);
                }
            }
        }
        return vld.validateElementAndAttributes();
    }

    private void addAttribute(String localName, String value) throws XMLStreamException {
        if (mAttrMap == null) {
            mAttrMap = new HashMap<String, Attribute>();
            mAttrList = new ArrayList<Attribute>();
        }
        Attribute attr = new Attribute(mAttrMap.size(), localName, value);
        if (mAttrMap.put(localName, attr) != null) {
            reportNwfAttr("Trying to write attribute '"+localName+"' twice");
        } else {
            mAttrList.add(attr);
        }
    }

    final class AttrCollector extends XMLValidator {

        AttrCollector() {
            super();
        }
        
        @Override
        public String getSchemaType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public XMLValidationSchema getSchema() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void validateElementStart(String localName, String uri, String prefix) throws XMLStreamException {
            throw new UnsupportedOperationException();
        }

        @Override
        public String validateAttribute(String localName, String uri, String prefix, String value) throws XMLStreamException {
            addAttribute(localName, value);
            return value;
        }

        @Override
        public String validateAttribute(String localName, String uri, String prefix, char[] valueChars, int valueStart,
                int valueEnd) throws XMLStreamException {
            final String value = new String(valueChars, valueStart, valueEnd - valueStart);
            addAttribute(localName, value);
            return value;
        }

        @Override
        public int validateElementAndAttributes() throws XMLStreamException {
            throw new UnsupportedOperationException();
        }

        @Override
        public int validateElementEnd(String localName, String uri, String prefix) throws XMLStreamException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void validateText(String text, boolean lastTextSegment) throws XMLStreamException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void validateText(char[] cbuf, int textStart, int textEnd, boolean lastTextSegment) throws XMLStreamException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void validationCompleted(boolean eod) throws XMLStreamException {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getAttributeType(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getIdAttrIndex() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getNotationAttrIndex() {
            throw new UnsupportedOperationException();
        }

    }

    static final class Attribute {
        private final int mIndex;
        private final String mLocalName;
        private final String mValue;
        public Attribute(int mIndex, String mLocalName, String mValue) {
            super();
            this.mIndex = mIndex;
            this.mLocalName = mLocalName;
            this.mValue = mValue;
        }
    }

}
