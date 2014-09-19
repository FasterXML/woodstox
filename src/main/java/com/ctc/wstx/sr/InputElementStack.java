/* Woodstox XML processor
 *
 * Copyright (c) 2004- Tatu Saloranta, tatu.saloranta@iki.fi
 *
 * Licensed under the License specified in file LICENSE, included with
 * the source code.
 * You may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ctc.wstx.sr;

import java.util.*;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.AttributeInfo;
import org.codehaus.stax2.validation.ValidationContext;
import org.codehaus.stax2.validation.XMLValidator;
import org.codehaus.stax2.validation.XMLValidationProblem;
import org.codehaus.stax2.validation.XMLValidationSchema;
import org.codehaus.stax2.validation.ValidatorPair;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.api.WstxInputProperties;
import com.ctc.wstx.cfg.ErrorConsts;
import com.ctc.wstx.cfg.XmlConsts;
import com.ctc.wstx.compat.QNameCreator;
import com.ctc.wstx.dtd.DTDValidatorBase; // unfortunate dependency
import com.ctc.wstx.util.*;

/**
 * Shared base class that defines API stream reader uses to communicate
 * with the element stack implementation, independent of whether it's
 * operating in namespace-aware or non-namespace modes.
 * Element stack class is used for storing nesting information about open
 * elements, and for namespace-aware mode, also information about
 * namespaces active (including default namespace), during parsing of
 * XML input.
 *<p>
 * This class also implements {@link NamespaceContext}, since it has all
 * the information necessary, so parser can just return element stack
 * instance as necesary.
 */
public final class InputElementStack
    implements AttributeInfo, NamespaceContext, ValidationContext
{
    final static int ID_ATTR_NONE = -1;

    /*
    ///////////////////////////////////////////////////////////
    // Configuration
    ///////////////////////////////////////////////////////////
    */

    protected final boolean mNsAware;

    protected final AttributeCollector mAttrCollector;

    protected final ReaderConfig mConfig;

    protected InputProblemReporter mReporter = null;

    /**
     * Object that will need to be consulted about namespace bindings,
     * since it has some knowledge about default namespace declarations
     * (has default attribute value expansion).
     */
    protected NsDefaultProvider mNsDefaultProvider;

    /*
    ///////////////////////////////////////////////////////////
    // Element, namespace information
    ///////////////////////////////////////////////////////////
    */

    protected int mDepth = 0;
    protected long mTotalElements = 0;

    /**
     * Vector that contains all currently active namespaces; one String for
     * prefix, another for matching URI. Does also include default name
     * spaces (at most one per level).
     */
    protected final StringVector mNamespaces = new StringVector(64);

    /**
     * Currently open element, if any; null outside root element.
     */
    protected Element mCurrElement;

    protected boolean mMayHaveNsDefaults = false;

    /*
    ///////////////////////////////////////////////////////////
    // Element validation (optional), attribute typing
    ///////////////////////////////////////////////////////////
    */

    /**
     * Optional validator object that will get called if set,
     * and that can validate xml content. Note that it is possible
     * that this is set to a proxy object that calls multiple
     * validators in sequence.
     */
    protected XMLValidator mValidator = null;

    /**
     * Index of the attribute with type of ID, if known (most likely
     * due to Xml:id support); -1 if not available, or no ID attribute
     * for current element.
     */
    protected int mIdAttrIndex = ID_ATTR_NONE;

    /*
    ///////////////////////////////////////////////////////////
    // Simple 1-slot QName cache; used for improving
    // efficiency of code that uses QNames extensively
    // (like StAX Event API implementation)
    ///////////////////////////////////////////////////////////
     */

    protected String mLastLocalName = null;
    protected String mLastPrefix = null;
    protected String mLastNsURI = null;

    protected QName mLastName = null;

    /*
    ///////////////////////////////////////////////////////////
    // Other simple caching
    ///////////////////////////////////////////////////////////
     */

    // Non-transient NamespaceContext caching; mostly for event API

    /**
     * Last potentially shareable NamespaceContext created by
     * this stack. This reference is cleared each time bindings
     * change (either due to a start element with new bindings, or due
     * to the matching end element that closes scope of such binding(s)).
     */
    protected BaseNsContext mLastNsContext = null;

    // Chain of reusable Element instances

    protected Element mFreeElement = null;

    /*
    ///////////////////////////////////////////////////////////
    // Life-cycle (create, update state)
    ///////////////////////////////////////////////////////////
     */

    protected InputElementStack(ReaderConfig cfg, boolean nsAware)
    {
        mConfig = cfg;
        mNsAware = nsAware;
        mAttrCollector = new AttributeCollector(cfg, nsAware);
    }

    protected void connectReporter(InputProblemReporter rep)
    {
        mReporter = rep;
    }

    protected XMLValidator addValidator(XMLValidator vld)
    {
        if (mValidator == null) {
            mValidator = vld;
        } else {
            mValidator = new ValidatorPair(mValidator, vld);
        }
        return vld;
    }

    /**
     * Method called to connect the automatically handled DTD validator
     * (one detected from DOCTYPE, loaded and completely handled by
     * the stream reader without application calling validation methods).
     * Handled separately, since its behaviour is potentially different
     * from that of explicitly added validators.
     */
    protected void setAutomaticDTDValidator(XMLValidator validator, NsDefaultProvider nsDefs)
    {
        mNsDefaultProvider = nsDefs;
        addValidator(validator);
    }

    /*
    ///////////////////////////////////////////////////////////
    // Start/stop validation
    ///////////////////////////////////////////////////////////
     */

    public XMLValidator validateAgainst(XMLValidationSchema schema)
        throws XMLStreamException
    {
        /* Should we first check if we maybe already have a validator
         * for the schema?
         */
        return addValidator(schema.createValidator(this));
    }



    public XMLValidator stopValidatingAgainst(XMLValidationSchema schema)
        throws XMLStreamException
    {
        XMLValidator[] results = new XMLValidator[2];
        if (ValidatorPair.removeValidator(mValidator, schema, results)) { // found
            XMLValidator found = results[0];
            mValidator = results[1];
            found.validationCompleted(false);
            return found;
        }
        return null;
    }

    public XMLValidator stopValidatingAgainst(XMLValidator validator)
        throws XMLStreamException
    {
        XMLValidator[] results = new XMLValidator[2];
        if (ValidatorPair.removeValidator(mValidator, validator, results)) { // found
            XMLValidator found = results[0];
            mValidator = results[1];
            found.validationCompleted(false);
            return found;
        }
        return null;
    }

    /*
    ///////////////////////////////////////////////////////////
    // Accessors:
    ///////////////////////////////////////////////////////////
     */

    /**
     * This is a method called by the reader to ensure that we have at
     * least one 'real' validator. This is only needed to ensure that
     * validation problems that the reader can detect (illegal textual
     * content) can be reported as validity errors. Since the validator
     * API does not have a good way to cleanly deal with such a possibility,
     * the check is rather fragile, but should work for now: essentially
     * we need at least one validator object that either is not a sub-class
     * of <code>DTDValidatorBase</code> or returns true for
     * <code>reallyValidating</code>.
     *<p>
     * !!! TODO: remove need for this method (and method itself) with
     * Woodstox 4.0, by adding necessary support in Stax2 XMLValidator
     * interface.
     */
    protected boolean reallyValidating()
    {
        if (mValidator == null) { // no validators, no validation
            // (although, should never get called if no validators)
            return false;
        }
        if (!(mValidator instanceof DTDValidatorBase)) {
            // note: happens for validator pair, for one
            return true;
        }
        return ((DTDValidatorBase) mValidator).reallyValidating();
    }

    /**
     * Method called by {@link BasicStreamReader}, to retrieve the
     * attribute collector it needs for some direct access.
     */
    public final AttributeCollector getAttrCollector() {
        return mAttrCollector;
    }

    /**
     * Method called to construct a non-transient NamespaceContext instance;
     * generally needed when creating events to return from event-based
     * iterators.
     */
    public BaseNsContext createNonTransientNsContext(Location loc)
    {
        // Have an instance we can reuse? Great!
        if (mLastNsContext != null) {
            return mLastNsContext;
        }

        // No namespaces declared at this point? Easy, as well:
        int totalNsSize = mNamespaces.size();
        if (totalNsSize < 1) {
            return (mLastNsContext = EmptyNamespaceContext.getInstance());
        }

        // Otherwise, we need to create a new non-empty context:
        int localCount = getCurrentNsCount() << 1;
        BaseNsContext nsCtxt = new CompactNsContext
            (loc, /*getDefaultNsURI(),*/
             mNamespaces.asArray(), totalNsSize,
             totalNsSize - localCount);
        /* And it can be shared if there are no new ('local', ie. included
         * within this start element) bindings -- if there are, underlying
         * array might be shareable, but offsets wouldn't be)
         */
        if (localCount == 0) {
            mLastNsContext = nsCtxt;
        }
        return nsCtxt;
}

    /**
     * Method called by the stream reader to add new (start) element
     * into the stack in namespace-aware mode; called when a start element
     * is encountered during parsing, but only in ns-aware mode.
     * @throws XMLStreamException 
     */
    public final void push(String prefix, String localName) throws XMLStreamException
    {
        if (++mDepth > mConfig.getMaxElementDepth()) {
            throw new XMLStreamException("Maximum Element Depth limit ("+mConfig.getMaxElementDepth()+") Exceeded");
        }
        if (++mTotalElements > mConfig.getMaxElementCount()) {
            throw new XMLStreamException("Maximum Element Count limit ("+mConfig.getMaxElementCount()+") Exceeded");
        }
        String defaultNs = (mCurrElement == null) ?
            XmlConsts.DEFAULT_NAMESPACE_URI : mCurrElement.mDefaultNsURI;
        if (mCurrElement != null) {
            ++mCurrElement.mChildCount;
            final int max = mConfig.getMaxChildrenPerElement();
            if (max > 0 && mCurrElement.mChildCount > max) {
                throw new XMLStreamException("Maximum Number of Child Elements limit ("+max+") Exceeded");
            }
        }

        if (mFreeElement == null) {
            mCurrElement = new Element(mCurrElement, mNamespaces.size(), prefix, localName);
        } else {
            Element newElem = mFreeElement;
            mFreeElement = newElem.mParent;
            newElem.reset(mCurrElement, mNamespaces.size(), prefix, localName);
            mCurrElement = newElem;
        }
        mCurrElement.mDefaultNsURI = defaultNs;
        mAttrCollector.reset();

        /* 20-Feb-2006, TSa: Hmmh. Namespace default provider unfortunately
         *   needs an advance warning...
         */
        if (mNsDefaultProvider != null) {
            mMayHaveNsDefaults = mNsDefaultProvider.mayHaveNsDefaults(prefix, localName);
        }
    }

    /**
     * Method called by the stream reader to remove the topmost (start)
     * element from the stack;
     * called when an end element is encountered during parsing.
     *
     * @return True if stack has more elements; false if not (that is,
     *    root element closed)
     */
    public final boolean pop() throws XMLStreamException
    {
        if (mCurrElement == null) {
            throw new IllegalStateException("Popping from empty stack");
        }
        --mDepth;

        Element child = mCurrElement;
        Element parent = child.mParent;
        mCurrElement = parent;

        // Let's do simple recycling of Element instances...
        child.relink(mFreeElement);
        mFreeElement = child;
            
        // Need to purge namespaces?
        int nsCount = mNamespaces.size() - child.mNsOffset;
        if (nsCount > 0) { // 2 entries for each NS mapping:
            mLastNsContext = null; // let's invalidate ns ctxt too, if we had one
            mNamespaces.removeLast(nsCount);
        }
        return (parent != null);
    }

    /**
     * Method called to resolve element and attribute namespaces (in
     * namespace-aware mode), and do optional validation using pluggable
     * validator object.
     *
     * @return Text content validation state that should be effective
     *   for the fully resolved element context
     */
    public int resolveAndValidateElement()
        throws XMLStreamException
    {
        if (mDepth == 0) { // just a simple sanity check
            throw new IllegalStateException("Calling validate() on empty stack.");
        }
        AttributeCollector ac = mAttrCollector;

        // Any namespace declarations?
        {
            int nsCount = ac.getNsCount();
            if (nsCount > 0) {
                /* let's first invalidate old (possibly) shared ns ctxt too,
                 * if we had one; new one can be created at a later point
                 */
                mLastNsContext = null;

                boolean internNsUris = mConfig.willInternNsURIs();
                for (int i = 0; i < nsCount; ++i) {
                    Attribute ns = ac.resolveNamespaceDecl(i, internNsUris);
                    String nsUri = ns.mNamespaceURI;
                    // note: for namespaces, prefix is stored as local name
                    String prefix = ns.mLocalName;

                    /* 18-Jul-2004, TSa: Need to check that 'xml' and 'xmlns'
                     *   prefixes are not re-defined (and 'xmlns' not even
                     *   defined to its correct ns).
                     */
                    if (prefix == "xmlns") {
                        // xmlns can never be declared, even to its correct URI
                        mReporter.throwParseError(ErrorConsts.ERR_NS_REDECL_XMLNS);
                    } else if (prefix == "xml") {
                        // whereas xml is ok, as long as it's same URI:
                        if (!nsUri.equals(XMLConstants.XML_NS_URI)) {
                            mReporter.throwParseError(ErrorConsts.ERR_NS_REDECL_XML,
                                                      nsUri, null);
                        }
                        /* 09-Feb-2006, TSa: Hmmh. Now, should this explicit
                         *   xml declaration be visible to the app? SAX API
                         *   seem to ignore it.
                         */
                        //mNamespaces.addStrings(prefix, nsUri);
                    } else { // ok, valid prefix, so far
                        /* 17-Mar-2006, TSa: Unbinding default NS needs to
                         *    result in null being added:
                         */
                        if (nsUri == null || nsUri.length() == 0) {
                            nsUri = XmlConsts.DEFAULT_NAMESPACE_URI;
                        }
                        // The default ns binding needs special handling:
                        if (prefix == null) {
                            mCurrElement.mDefaultNsURI = nsUri;
                        }

                        /* But then let's ensure that URIs matching xml
                         * and xmlns are not being bound to anything else
                         */
                        if (internNsUris) { // identity comparison is ok:
                            if (nsUri == XMLConstants.XML_NS_URI) {
                                mReporter.throwParseError(ErrorConsts.ERR_NS_REDECL_XML_URI, prefix, null);
                            } else if (nsUri == XMLConstants.XMLNS_ATTRIBUTE_NS_URI) {
                                mReporter.throwParseError(ErrorConsts.ERR_NS_REDECL_XMLNS_URI);
                            }
                        } else { // need to check equals()
                            if (nsUri.equals(XMLConstants.XML_NS_URI)) {
                                mReporter.throwParseError(ErrorConsts.ERR_NS_REDECL_XML_URI, prefix, null);
                            } else if (nsUri.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)) {
                                mReporter.throwParseError(ErrorConsts.ERR_NS_REDECL_XMLNS_URI);
                            }
                        }
                        /* and at any rate, binding needs to be added, to
                         * be visible to the app (including def ns):
                         */
                        mNamespaces.addStrings(prefix, nsUri);
                    }
                }
            }
        }

        /* 20-Feb-2006, TSa: Any attribute defaults for namespace declaration
         *   pseudo-attributes?
         */
        if (mMayHaveNsDefaults) {
            mNsDefaultProvider.checkNsDefaults(this);
        }

        // Then, let's set element's namespace, if any:
        String prefix = mCurrElement.mPrefix;
        String ns;

        if (prefix == null) { // use default NS, if any
            ns = mCurrElement.mDefaultNsURI;
        } else if (prefix == "xml") {
            ns = XMLConstants.XML_NS_URI;
        } else {
            // Need to find namespace with the prefix:
            ns = mNamespaces.findLastFromMap(prefix);
            /* 07-Sep-2007, TSa: "no namespace" should now be indicated
             *   by an empty string, however, due to historical reasons
             *   let's be bit defensive and allow nulls for the same too
             */
            if (ns == null || ns.length() == 0) {
                mReporter.throwParseError(ErrorConsts.ERR_NS_UNDECLARED, prefix, null);
            }
        }
        mCurrElement.mNamespaceURI = ns;

        // And finally, resolve attributes' namespaces too:
        int xmlidIx = ac.resolveNamespaces(mReporter, mNamespaces);
        mIdAttrIndex = xmlidIx;

        XMLValidator vld = mValidator;
        /* If we have no validator(s), nothing more to do,
         * except perhaps little bit of Xml:id handling:
         */
        if (vld == null) { // no validator in use
            if (xmlidIx >= 0) { // need to normalize xml:id, still?
                ac.normalizeSpacesInValue(xmlidIx);
            }
            return XMLValidator.CONTENT_ALLOW_ANY_TEXT;
        }

        // Otherwise need to call relevant validation methods.

        /* First, a call to check if the element itself may be acceptable
         * within structure:
         */
        vld.validateElementStart
            (mCurrElement.mLocalName, mCurrElement.mNamespaceURI, mCurrElement.mPrefix);

        // Then attributes, if any:
        int attrLen = ac.getCount();
        if (attrLen > 0) {
            for (int i = 0; i < attrLen; ++i) {
                ac.validateAttribute(i, mValidator);
            }
        }

        /* And finally let's wrap things up to see what textual content
         * is allowed as child content, if any:
         */
        return mValidator.validateElementAndAttributes();
    }

    /**
     * Method called after parsing (but before returning) end element,
     * to allow for pluggable validators to verify correctness of
     * the content model for the closing element.
     *
     * @return Validation state that should be effective for the parent
     *   element state
     */
    public int validateEndElement()
        throws XMLStreamException
    {
        if (mValidator == null) { // should never be null if we get here
            return XMLValidator.CONTENT_ALLOW_ANY_TEXT;
        }
        int result =  mValidator.validateElementEnd
            (mCurrElement.mLocalName, mCurrElement.mNamespaceURI, mCurrElement.mPrefix);
        if (mDepth == 1) { // root closing
            mValidator.validationCompleted(true);
        }
        return result;
    }

    /*
    ///////////////////////////////////////////////////////////
    // AttributeInfo methods (StAX2)
    ///////////////////////////////////////////////////////////
     */

    @Override
    public final int getAttributeCount() {
        return mAttrCollector.getCount();
    }

    @Override
    public final int findAttributeIndex(String nsURI, String localName) {
        return mAttrCollector.findIndex(nsURI, localName);
    }

    /**
     * Default implementation just indicates it does not know of such
     * attributes; this because that requires DTD information that only
     * some implementations have.
     */
    @Override
    public final int getIdAttributeIndex()
    {
        if (mIdAttrIndex >= 0) {
            return mIdAttrIndex;
        }
        return (mValidator == null) ? -1 : mValidator.getIdAttrIndex();
    }

    /**
     * Default implementation just indicates it does not know of such
     * attributes; this because that requires DTD information that only
     * some implementations have.
     */
    @Override
    public final int getNotationAttributeIndex()
    {
        return (mValidator == null) ? -1 :
            mValidator.getNotationAttrIndex();
    }

    /*
    ///////////////////////////////////////////////////////////
    // Implementation of NamespaceContext:
    ///////////////////////////////////////////////////////////
     */

    @Override
    public final String getNamespaceURI(String prefix)
    {
        if (prefix == null) {
            throw new IllegalArgumentException(ErrorConsts.ERR_NULL_ARG);
        }
        if (prefix.length() == 0) {
            if (mDepth == 0) { // unexpected... but let's not err at this point
                /* 07-Sep-2007, TSa: Default/"no namespace" does map to
                 *    "URI" of empty String.
                 */
                return XmlConsts.DEFAULT_NAMESPACE_URI;
            }
            return mCurrElement.mDefaultNsURI;
        }
        if (prefix.equals(XMLConstants.XML_NS_PREFIX)) {
            return XMLConstants.XML_NS_URI;
        }
        if (prefix.equals(XMLConstants.XMLNS_ATTRIBUTE)) {
            return XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
        }
        /* Ok, need to find the match, if any; starting from end of the
         * list of active namespaces. Note that we can not count on prefix
         * being interned/canonicalized.
         */
        return mNamespaces.findLastNonInterned(prefix);
    }

    @Override
    public final String getPrefix(String nsURI)
    {
        if (nsURI == null || nsURI.length() == 0) {
            throw new IllegalArgumentException("Illegal to pass null/empty prefix as argument.");
        }
        if (nsURI.equals(XMLConstants.XML_NS_URI)) {
            return XMLConstants.XML_NS_PREFIX;
        }
        if (nsURI.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)) {
            return XMLConstants.XMLNS_ATTRIBUTE;
        }
        /* Ok, need to find the match, if any; starting from end of the
         * list of active namespaces. Note that we can not count on prefix
         * being interned/canonicalized.
         */
        String prefix = null;

        // 29-Sep-2004, TSa: Need to check for namespace masking, too...
        String[] strs = mNamespaces.getInternalArray();
        int len = mNamespaces.size();

        main_loop:
        for (int index = len-1; index > 0; index -= 2) {
            if (nsURI.equals(strs[index])) {
                // Ok, is prefix masked?
                prefix = strs[index-1];
                for (int j = index+1; j < len; j += 2) {
                    if (strs[j] == prefix) { // masked!
                        prefix = null;
                        continue main_loop;
                    }
                }
                // nah, it's good
                // 17-Mar-2006, TSa: ... but default NS has prefix null...
                if (prefix == null) {
                    prefix = "";
                }
                break main_loop;
            }
        }

        return prefix;
    }

    @Override
    public final Iterator<String> getPrefixes(String nsURI)
    {
        if (nsURI == null || nsURI.length() == 0) {
            throw new IllegalArgumentException("Illegal to pass null/empty prefix as argument.");
        }
        if (nsURI.equals(XMLConstants.XML_NS_URI)) {
            return DataUtil.singletonIterator(XMLConstants.XML_NS_PREFIX);
        }
        if (nsURI.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)) {
            return DataUtil.singletonIterator(XMLConstants.XMLNS_ATTRIBUTE);
        }

        // 29-Sep-2004, TSa: Need to check for namespace masking, too...
        String[] strs = mNamespaces.getInternalArray();
        int len = mNamespaces.size();
        ArrayList<String> l = null;

        main_loop:
        for (int index = len-1; index > 0; index -= 2) {
            if (nsURI.equals(strs[index])) {
                // Ok, is prefix masked?
                String prefix = strs[index-1];
                for (int j = index+1; j < len; j += 2) {
                    if (strs[j] == prefix) { // masked!
                        continue main_loop;
                    }
                }
                // nah, it's good!
                if (l == null) {
                    l = new ArrayList<String>();
                }
                l.add(prefix);
            }
        }

        if (l == null) {
            return DataUtil.emptyIterator();
        }
        return l.iterator();
    }

    /*
    ///////////////////////////////////////////////////////////
    // ValidationContext
    ///////////////////////////////////////////////////////////
     */

    @Override
    public final String getXmlVersion()
    {
        return mConfig.isXml11() ? XmlConsts.XML_V_11_STR : XmlConsts.XML_V_10_STR;
    }

    // Part of Stax2, see above:
    //public int getAttributeCount();

    @Override
    public String getAttributeLocalName(int index) {
        return getAttrCollector().getLocalName(index);
    }

    @Override
    public String getAttributeNamespace(int index) {
        return getAttrCollector().getURI(index);
    }

    @Override
    public String getAttributePrefix(int index) {
        return getAttrCollector().getPrefix(index);
    }

    @Override
    public String getAttributeValue(int index) {
        return getAttrCollector().getValue(index);
    }

    @Override
    public String getAttributeValue(String nsURI, String localName)
    {
        int ix = findAttributeIndex(nsURI, localName);
        return (ix < 0) ? null : getAttributeValue(ix);
    }

    // Part of Stax2, see above:
    //public int findAttributeIndex(String nsURI, String localName);

    @Override
    public boolean isNotationDeclared(String name)
    {
        // !!! TBI
        return false;
    }

    @Override
    public boolean isUnparsedEntityDeclared(String name)
    {
        // !!! TBI
        return false;
    }

    @Override
    public String getBaseUri()
    {
        // !!! TBI
        return null;
    }

    @Override
    public final QName getCurrentElementName()
    {
        if (mDepth == 0) {
            return null;
        }
        String prefix = mCurrElement.mPrefix;
        /* 17-Mar-2006, TSa: We only map prefix to empty String because
         *   some QName impls barf on nulls. Otherwise we will always
         *   use null to indicate missing prefixes.
         */
        if (prefix == null) {
            prefix = "";
        }
        /* 03-Dec-2004, TSa: Maybe we can just reuse the last QName
         *    object created, if we have same data? (happens if
         *    state hasn't changed, or we got end element for a leaf
         *    element, or repeating leaf elements)
         */
        String nsURI = mCurrElement.mNamespaceURI;
        String ln = mCurrElement.mLocalName;

        /* Since we generally intern most Strings, can do identity
         * comparisons here:
         */
        if (ln != mLastLocalName) {
            mLastLocalName = ln;
            mLastPrefix = prefix;
            mLastNsURI = nsURI;
        } else if (prefix != mLastPrefix) {
            mLastPrefix = prefix;
            mLastNsURI = nsURI;
        } else if (nsURI != mLastNsURI) {
            mLastNsURI = nsURI;
        } else {
            return mLastName;
        }
        QName n = QNameCreator.create(nsURI, ln, prefix);
        mLastName = n;
        return n;
    }

    // This was defined above for NamespaceContext
    //public String getNamespaceURI(String prefix);

    @Override
    public Location getValidationLocation() {
        return mReporter.getLocation();
    }

    @Override
    public void reportProblem(XMLValidationProblem problem)
        throws XMLStreamException
    {
        mReporter.reportValidationProblem(problem);
    }

    /**
     * Method called by actual validator instances when attributes with
     * default values have no explicit values for the element; if so,
     * default value needs to be added as if it was parsed from the
     * element.
     */
    @Override
    public int addDefaultAttribute(String localName, String uri, String prefix,
                                   String value) throws XMLStreamException
    {
        return mAttrCollector.addDefaultAttribute(localName, uri, prefix, value);
    }

    /*
    ///////////////////////////////////////////////////////////
    // Support for NsDefaultProvider
    ///////////////////////////////////////////////////////////
     */

    public boolean isPrefixLocallyDeclared(String internedPrefix)
    {
        if (internedPrefix != null && internedPrefix.length() == 0) { // default ns
            internedPrefix = null;
        }

        int offset = mCurrElement.mNsOffset;
        for (int len = mNamespaces.size(); offset < len; offset += 2) {
            // both interned, can use identity comparison
            String thisPrefix = mNamespaces.getString(offset);
            if (thisPrefix == internedPrefix) {
                return true;
            }
        }
        return false;
    }

    /**
     * Callback method called by the namespace default provider. At
     * this point we can trust it to only call this method with somewhat
     * valid arguments (no dups etc).
     */
    public void addNsBinding(String prefix, String uri)
    {
        // Unbind? (xml 1.1...)
        if ((uri == null) || (uri.length() == 0)) {
            uri = null;
        }

        // Default ns declaration?
        if ((prefix == null) || (prefix.length() == 0)) {
            prefix = null;
            mCurrElement.mDefaultNsURI = uri;
        }
        mNamespaces.addStrings(prefix, uri);
    }

    /*
    ///////////////////////////////////////////////////////////
    // Support for validation:
    ///////////////////////////////////////////////////////////
     */

    public final void validateText(TextBuffer tb, boolean lastTextSegment)
        throws XMLStreamException
    {
        tb.validateText(mValidator, lastTextSegment);
    }

    public final void validateText(String contents, boolean lastTextSegment)
        throws XMLStreamException
    {
        mValidator.validateText(contents, lastTextSegment);
    }

    /*
    ///////////////////////////////////////////////////////////
    // Accessors:
    ///////////////////////////////////////////////////////////
     */

    // // // Generic stack information:

    public final boolean isNamespaceAware() {
        return mNsAware;
    }

    // // // Generic stack information:

    public final boolean isEmpty() {
        return mDepth == 0;
    }

    /**
     * @return Number of open elements in the stack; 0 when parser is in
     *  prolog/epilog, 1 inside root element and so on.
     */
    public final int getDepth() { return mDepth; }

    // // // Information about element at top of stack:

    public final String getDefaultNsURI() {
        if (mDepth == 0) {
            throw new IllegalStateException("Illegal access, empty stack.");
        }
        return mCurrElement.mDefaultNsURI;
    }

    public final String getNsURI() {
        if (mDepth == 0) {
            throw new IllegalStateException("Illegal access, empty stack.");
        }
        return mCurrElement.mNamespaceURI;
    }

    public final String getPrefix() {
        if (mDepth == 0) {
            throw new IllegalStateException("Illegal access, empty stack.");
        }
        return mCurrElement.mPrefix;
    }

    public final String getLocalName() {
        if (mDepth == 0) {
            throw new IllegalStateException("Illegal access, empty stack.");
        }
        return mCurrElement.mLocalName;
    }

    public final boolean matches(String prefix, String localName)
    {
        if (mDepth == 0) {
            throw new IllegalStateException("Illegal access, empty stack.");
        }
        String thisPrefix = mCurrElement.mPrefix;
        if (prefix == null || prefix.length() == 0) { // no name space
            if (thisPrefix != null && thisPrefix.length() > 0) {
                return false;
            }
        } else {
            if (thisPrefix != prefix && !thisPrefix.equals(prefix)) {
                return false;
            }
        }

        String thisName = mCurrElement.mLocalName;
        return (thisName == localName) || thisName.equals(localName);
    }

    public final String getTopElementDesc()
    {
        if (mDepth == 0) {
            throw new IllegalStateException("Illegal access, empty stack.");
        }
        String name = mCurrElement.mLocalName;
        String prefix = mCurrElement.mPrefix;
        if (prefix == null) { // no name space
            return name;
        }
        return prefix + ":" + name;
    }

    // // // Namespace information:

    /**
     * @return Number of active prefix/namespace mappings for current scope,
     *   including mappings from enclosing elements.
     */
    public final int getTotalNsCount() {
        return mNamespaces.size() >> 1;
    }

    /**
     * @return Number of active prefix/namespace mappings for current scope,
     *   NOT including mappings from enclosing elements.
     */
    public final int getCurrentNsCount()
    {
        // Need not check for empty stack; should return 0 properly
        return (mNamespaces.size() - mCurrElement.mNsOffset) >> 1;
    }

    public final String getLocalNsPrefix(int index)
    {
        int offset = mCurrElement.mNsOffset;
        int localCount = (mNamespaces.size() - offset);
        index <<= 1; // 2 entries, prefix/URI for each NS
        if (index < 0 || index >= localCount) {
            throwIllegalIndex(index >> 1, localCount >> 1);
        }
        return mNamespaces.getString(offset + index);
    }

    public final String getLocalNsURI(int index)
    {
        int offset = mCurrElement.mNsOffset;
        int localCount = (mNamespaces.size() - offset);
        index <<= 1; // 2 entries, prefix/URI for each NS
        if (index < 0 || index >= localCount) {
            throwIllegalIndex(index >> 1, localCount >> 1);
        }
        return mNamespaces.getString(offset + index + 1);
    }

    private void throwIllegalIndex(int index, int localCount)
    {
        throw new IllegalArgumentException("Illegal namespace index "
                +(index >> 1)+"; current scope only has "
                +(localCount >> 1)+" namespace declarations.");
    }

    // // // DTD-derived attribute information:

    /**
     * @return Schema (DTD, RNG, W3C Schema) based type of the attribute
     *   in specified index
     */
    @Override
    public final String getAttributeType(int index)
    {
        if (index == mIdAttrIndex && index >= 0) { // second check to ensure -1 is not passed
            return "ID";
        }
        return (mValidator == null) ? WstxInputProperties.UNKNOWN_ATTR_TYPE : 
            mValidator.getAttributeType(index);
    }
}
