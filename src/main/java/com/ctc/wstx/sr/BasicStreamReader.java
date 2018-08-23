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

import java.io.*;
import java.text.MessageFormat;
import java.util.Map;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.AttributeInfo;
import org.codehaus.stax2.DTDInfo;
import org.codehaus.stax2.LocationInfo;
import org.codehaus.stax2.XMLStreamLocation2;
import org.codehaus.stax2.XMLStreamProperties;
import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.typed.TypedXMLStreamException;
import org.codehaus.stax2.validation.*;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.api.WstxInputProperties;
import com.ctc.wstx.cfg.ErrorConsts;
import com.ctc.wstx.cfg.XmlConsts;
import com.ctc.wstx.dtd.MinimalDTDReader;
import com.ctc.wstx.ent.EntityDecl;
import com.ctc.wstx.exc.WstxException;
import com.ctc.wstx.io.*;
import com.ctc.wstx.util.DefaultXmlSymbolTable;
import com.ctc.wstx.util.TextBuffer;
import com.ctc.wstx.util.TextBuilder;

/**
 * Partial implementation of {@link XMLStreamReader2} consisting of
 * all functionality other than DTD-validation-specific parts, and
 * Typed Access API (Stax2 v3.0), which are implemented at
 * sub-classes.
 *
 * @author Tatu Saloranta
 */
public abstract class BasicStreamReader
    extends StreamScanner
    implements StreamReaderImpl, DTDInfo, LocationInfo
{
    /*
    ///////////////////////////////////////////////////////////////////////
    // Constants
    ///////////////////////////////////////////////////////////////////////
     */

    // // // Standalone values:

    final static int DOC_STANDALONE_UNKNOWN = 0;
    final static int DOC_STANDALONE_YES = 1;
    final static int DOC_STANDALONE_NO = 2;

    // // // Main state consts:

    final static int STATE_PROLOG = 0; // Before root element
    final static int STATE_TREE = 1; // Parsing actual XML tree
    final static int STATE_EPILOG = 2; // After root element has been closed
    final static int STATE_MULTIDOC_HACK = 3; // State "between" multiple documents (in multi-doc mode)
    final static int STATE_CLOSED = 4; // After reader has been closed

    // // // Tokenization state consts:

    // no idea as to what comes next (unknown type):
    final static int TOKEN_NOT_STARTED = 0;

    // token type figured out, but not long enough:
    final static int TOKEN_STARTED = 1;

    /* minimum token length returnable achieved; only used for
     * CHARACTERS event which allow fragments to be returned (and for
     * CDATA in some limited cases)
     */
    final static int TOKEN_PARTIAL_SINGLE = 2;

    /* a single physical event has been successfully tokenized; as with
     * partial, only used with CDATA and CHARACTERS (meaningless for others,
     * which should only use TOKEN_FULL_COALESCED, TOKEN_NOT_STARTED or
     * TOKEN_STARTED.
     */
    final static int TOKEN_FULL_SINGLE = 3;

    /* all adjacent (text) events have been tokenized and coalesced (for
     * CDATA and CHARACTERS), or that the full event has been parsed (for
     * others)
     */
    final static int TOKEN_FULL_COALESCED = 4;

    // // // Bit masks used for quick type comparisons

    /**
     * This mask covers all types for which basic {@link #getText} method
     * can be called.
     */
    final protected static int MASK_GET_TEXT = 
        (1 << CHARACTERS) | (1 << CDATA) | (1 << SPACE)
        | (1 << COMMENT) | (1 << DTD) | (1 << ENTITY_REFERENCE);

    /**
     * This mask covers all types for which extends <code>getTextXxx</code>
     * methods can be called; which is less than those for which 
     * {@link #getText} can be called. Specifically, <code>DTD</code> and
     * <code>ENTITY_REFERENCE</code> types do not support these extended
     */
    final protected static int MASK_GET_TEXT_XXX =
        (1 << CHARACTERS) | (1 << CDATA) | (1 << SPACE) | (1 << COMMENT);

    /**
     * This mask is used with Stax2 getText() method (one that takes
     * Writer as an argument): accepts even wider range of event types.
     */
    final protected static int MASK_GET_TEXT_WITH_WRITER = 
        (1 << CHARACTERS) | (1 << CDATA) | (1 << SPACE)
        | (1 << COMMENT) | (1 << DTD) | (1 << ENTITY_REFERENCE)
        | (1 << PROCESSING_INSTRUCTION);

    final protected static int MASK_GET_ELEMENT_TEXT = 
        (1 << CHARACTERS) | (1 << CDATA) | (1 << SPACE)
        | (1 << ENTITY_REFERENCE);


    // // // Indicator of type of text in text event (WRT white space)

    final static int ALL_WS_UNKNOWN = 0x0000;
    final static int ALL_WS_YES = 0x0001;
    final static int ALL_WS_NO = 0x0002;

    /* 2 magic constants used for enabling/disabling indentation checks:
     * (to minimize negative impact for both small docs, and large
     * docs with non-regular white space)
     */

    private final static int INDENT_CHECK_START = 16;

    private final static int INDENT_CHECK_MAX = 40;

    // // // Shared namespace symbols

    final protected static String sPrefixXml = DefaultXmlSymbolTable.getXmlSymbol();

    final protected static String sPrefixXmlns = DefaultXmlSymbolTable.getXmlnsSymbol();

    /*
    ///////////////////////////////////////////////////////////////////////
    // Configuration
    ///////////////////////////////////////////////////////////////////////
     */

    // note: mConfig defined in base class

    /**
     * Set of locally stored configuration flags
     */
    protected final int mConfigFlags;

    // // // Various extracted settings:

    protected final boolean mCfgCoalesceText;

    protected final boolean mCfgReportTextAsChars;
    protected final boolean mCfgLazyParsing;

    /**
     * Minimum number of characters parser can return as partial text
     * segment, IF it's not required to coalesce adjacent text
     * segments.
     */
    protected final int mShortestTextSegment;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Symbol handling
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Object to notify about shared stuff, such as symbol tables, as well
     * as to query for additional config settings if necessary.
     */
    final protected ReaderCreator mOwner;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Additional XML document information, in addition to what StreamScanner has
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Status about "stand-aloneness" of document; set to 'yes'/'no'/'unknown'
     * based on whether there was xml declaration, and if so, whether
     * it had standalone attribute.
     */
    protected int mDocStandalone = DOC_STANDALONE_UNKNOWN;

    /*
    ///////////////////////////////////////////////////////////////////////
    // DOCTYPE information from document type declaration (if any found)
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Prefix of root element, as dictated by DOCTYPE declaration; null
     * if no DOCTYPE declaration, or no root prefix
     */
    protected String mRootPrefix;

    /**
     * Local name of root element, as dictated by DOCTYPE declaration; null
     * if no DOCTYPE declaration.
     */
    protected String mRootLName;

    /**
     * Public id of the DTD, if one exists and has been parsed.
     */
    protected String mDtdPublicId;

    /**
     * System id of the DTD, if one exists and has been parsed.
     */
    protected String mDtdSystemId;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Information about currently open subtree, content
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * TextBuffer mostly used to collect non-element textual content
     * (text, CDATA, comment content, pi data)
     */
    final protected TextBuffer mTextBuffer;

    /**
     * Currently open element tree
     */
    final protected InputElementStack mElementStack;

    /**
     * Object that stores information about currently accessible attributes.
     */
    final protected AttributeCollector mAttrCollector;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Tokenization state
    ///////////////////////////////////////////////////////////////////////
     */

    /// Flag set when DOCTYPE declaration has been parsed
    protected boolean mStDoctypeFound = false;

    /**
     * State of the current token; one of M_ - constants from above.
     *<p>
     * Initially set to fully tokenized, since it's the virtual
     * START_DOCUMENT event that we fully know by now (parsed by
     * bootstrapper)
     */
    protected int mTokenState = TOKEN_FULL_COALESCED;

    /**
     * Threshold value that defines tokenization state that needs to be
     * achieved to "finish" current <b>logical</b> text segment (which
     * may consist of adjacent CDATA and text segments; or be a complete
     * physical segment; or just even a fragment of such a segment)
     */
    protected final int mStTextThreshold;

    /**
     * Sized of currentTextLength for CDATA, CHARACTERS, WHITESPACE.
     * When segmenting, this records to size of all the segments
     * so we can track if the text length has exceeded limits.
     */
    protected int mCurrTextLength;

    /// Flag that indicates current start element is an empty element
    protected boolean mStEmptyElem = false;

    /**
     * Main parsing/tokenization state (STATE_xxx)
     */
    protected int mParseState;

    /**
     * Current state of the stream, ie token value returned by
     * {@link #getEventType}. Needs to be initialized to START_DOCUMENT,
     * since that's the state it starts in.
     */
    protected int mCurrToken = START_DOCUMENT;

    /**
     * Additional information sometimes stored (when generating dummy
     * events in multi-doc mode, for example) temporarily when
     * {@link #mCurrToken} is already populated.
     */
    protected int mSecondaryToken = START_DOCUMENT;

    /**
     * Status of current (text) token's "whitespaceness", that is,
     * whether it is or is not all white space.
     */
    protected int mWsStatus;

    /**
     * Flag that indicates that textual content (CDATA, CHARACTERS) is to
     * be validated within current element's scope. Enabled if one of
     * validators returns {@link XMLValidator#CONTENT_ALLOW_VALIDATABLE_TEXT},
     * and will prevent lazy parsing of text.
     */
    protected boolean mValidateText = false;

    /**
     * Counter used for determining whether we are to try to heuristically
     * "intern" white space that seems to be used for indentation purposes
     */
    protected int mCheckIndentation;

    /**
     * Due to the way Stax API does not allow throwing stream exceptions
     * from many methods for which Woodstox would need to throw one
     * (especially <code>getText</code> and its variations), we may need
     * to delay throwing an exception until {@link #next} is called next
     * time. If so, this variable holds the pending stream exception.
     */
    protected XMLStreamException mPendingException = null;

    /*
    ///////////////////////////////////////////////////////////////////////
    // DTD information (entities, content spec stub)
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Entities parsed from internal/external DTD subsets. Although it
     * will remain null for this class, extended classes make use of it,
     * plus, to be able to share some of entity resolution code, instance
     * is left here even though it semantically belongs to the sub-class.
     */
    protected Map<String, EntityDecl> mGeneralEntities = null;

    /**
     * Mode information needed at this level; mostly to check what kind
     * of textual content (if any) is allowed in current element
     * context. Constants come from
     * {@link XMLValidator},
     * (like {@link XMLValidator#CONTENT_ALLOW_VALIDATABLE_TEXT}).
     * Only used inside tree; ignored for prolog/epilog (which
     * have straight-forward static rules).
     */
    protected int mVldContent = XMLValidator.CONTENT_ALLOW_ANY_TEXT;
    
    /**
     * Configuration from {@link XMLStreamProperties.RETURN_NULL_FOR_DEFAULT_NAMESPACE}
     * 
     * @since 4.1.2
     */
    protected boolean mReturnNullForDefaultNamespace;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Instance construction, initialization
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * @param elemStack Input element stack to use; if null, will create
     *   instance locally.
     * @param forER Override indicator; if true, this stream reader will be
     *   used by an event reader, and should modify some of the base config
     *   settings appropriately. If false, configuration settings are to
     *   be used as is.
     */
    protected BasicStreamReader(InputBootstrapper bs,
                                BranchingReaderSource input, ReaderCreator owner,
                                ReaderConfig cfg, InputElementStack elemStack,
                                boolean forER)
        throws XMLStreamException
    {
        super(input, cfg, cfg.getEntityResolver());

        mOwner = owner;

        mTextBuffer = TextBuffer.createRecyclableBuffer(cfg);

        // // // First, configuration settings:

        mConfigFlags = cfg.getConfigFlags();
        mCfgCoalesceText = (mConfigFlags & CFG_COALESCE_TEXT) != 0;
        mCfgReportTextAsChars = (mConfigFlags & CFG_REPORT_CDATA) == 0;
        mXml11 = cfg.isXml11();

        // Can only use canonical white space if we are normalizing lfs
        mCheckIndentation = mNormalizeLFs ? 16 : 0;

        /* 30-Sep-2005, TSa: Let's not do lazy parsing when access is via
         *   Event API. Reason is that there will be no performance benefit
         *   (event objects always access full info right after traversal),
         *   but the wrapping of stream exceptions within runtime exception
         *   wrappers would happen, which is inconvenient (loss of stack trace,
         *   not catching all exceptions as expected)
         */
        mCfgLazyParsing = !forER && ((mConfigFlags & CFG_LAZY_PARSING) != 0);

        /* There are a few derived settings used during tokenization that
         * need to be initialized now...
         */
        if (mCfgCoalesceText) {
            mStTextThreshold =  TOKEN_FULL_COALESCED;
            mShortestTextSegment = Integer.MAX_VALUE;
        } else {
            mStTextThreshold =  TOKEN_PARTIAL_SINGLE;
            if (forER) {
                /* 30-Sep-2005, TSa: No point in returning runt segments for event readers
                 *   (due to event object overhead, less convenient); let's just force
                 *   returning of full length segments.
                 */
                mShortestTextSegment = Integer.MAX_VALUE;
            } else {
                mShortestTextSegment = cfg.getShortestReportedTextSegment();
            }
        }

        // // // Then handling of xml declaration data:

        mDocXmlVersion = bs.getDeclaredVersion();
        mDocInputEncoding = bs.getInputEncoding();
        mDocXmlEncoding = bs.getDeclaredEncoding();

        String sa = bs.getStandalone();
        if (sa == null) {
            mDocStandalone = DOC_STANDALONE_UNKNOWN;
        } else {
            if (XmlConsts.XML_SA_YES.equals(sa)) {
                mDocStandalone = DOC_STANDALONE_YES;
            } else {
                mDocStandalone = DOC_STANDALONE_NO;
            }
        }

        /* Ok; either we got declaration or not, but in either case we can
         * now initialize prolog parsing settings, without having to really
         * parse anything more.
         */
        /* 07-Oct-2005, TSa: Except, if we are in fragment mode, in which
         *   case we are kind of "in tree" mode...
         */
        mParseState = mConfig.inputParsingModeFragment() ?
            STATE_TREE : STATE_PROLOG;

        // // // And then connecting element stack and attribute collector

        mElementStack = elemStack;
        mAttrCollector = elemStack.getAttrCollector();

        // And finally, location information may have offsets:
        input.initInputLocation(this, mCurrDepth, 0);

        elemStack.connectReporter(this);
        mReturnNullForDefaultNamespace = mConfig.returnNullForDefaultNamespace();
    }

    protected static InputElementStack createElementStack(ReaderConfig cfg)
    {
        return new InputElementStack(cfg, cfg.willSupportNamespaces());
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // XMLStreamReader, document info
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * As per Stax (1.0) specs, needs to return whatever xml declaration
     * claimed encoding is, if any; or null if no xml declaration found.
     *<p>
     * Note: method name is rather confusing (compare to {@link #getEncoding}).
     */
    @Override
    public String getCharacterEncodingScheme() {
        return mDocXmlEncoding;
    }

    /**
     * As per Stax (1.0) specs, needs to return whatever parser determined
     * the encoding was, if it was able to figure it out. If not (there are
     * cases where this can not be found; specifically when being passed a
     * {@link Reader}), it should return null.
     */
    @Override
    public String getEncoding() {
        return mDocInputEncoding;
    }

    @Override
    public String getVersion()
    {
        if (mDocXmlVersion == XmlConsts.XML_V_10) {
            return XmlConsts.XML_V_10_STR;
        }
        if (mDocXmlVersion == XmlConsts.XML_V_11) {
            return XmlConsts.XML_V_11_STR;
        }
        return null; // unknown
    }

    @Override
    public boolean isStandalone() {
        return mDocStandalone == DOC_STANDALONE_YES;
    }

    @Override
    public boolean standaloneSet() {
        return mDocStandalone != DOC_STANDALONE_UNKNOWN;
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Public API, configuration
    ///////////////////////////////////////////////////////////////////////
     */

    @Override
    public Object getProperty(String name)
    {
        /* 18-Nov-2008, TSa: As per [WSTX-50], should report the
         *   actual Base URL. It can be overridden by matching
         *   setProperty, but if not, is set to actual source
         *   of content being parsed.
         */
        if (WstxInputProperties.P_BASE_URL.equals(name)) {
        	try {
        		return mInput.getSource();
        	} catch (IOException e) { // not optimal but...
        		throw new IllegalStateException(e);
        	}
        }
        /* 23-Apr-2008, TSa: Let's NOT throw IllegalArgumentException
         *   for unknown property; JavaDocs do not suggest it needs
         *   to be done (different from that of XMLInputFactory
         *   and XMLStreamWriter specification)
         */
        return mConfig.safeGetProperty(name);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // XMLStreamReader, current state
    ///////////////////////////////////////////////////////////////////////
     */

    // // // Attribute access:

    @Override
    public int getAttributeCount() {
        if (mCurrToken != START_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        return mAttrCollector.getCount();
    }

    @Override
    public String getAttributeLocalName(int index) {
        if (mCurrToken != START_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        return mAttrCollector.getLocalName(index);
    }

    @Override
    public QName getAttributeName(int index) {
        if (mCurrToken != START_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        return mAttrCollector.getQName(index);
    }

    @Override
    public String getAttributeNamespace(int index) {
        if (mCurrToken != START_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        // Internally it's marked as null, externally need to see ""
        String uri = mAttrCollector.getURI(index);
        return (uri == null) ? XmlConsts.ATTR_NO_NS_URI : uri;
    }

    @Override
    public String getAttributePrefix(int index) {
        if (mCurrToken != START_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        // Internally it's marked as null, externally need to see ""
        String p = mAttrCollector.getPrefix(index);
        return (p == null) ? XmlConsts.ATTR_NO_PREFIX : p;
    }

    @Override
    public String getAttributeType(int index) {
        if (mCurrToken != START_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        // Attr. collector doesn't know it, elem stack does:
        return mElementStack.getAttributeType(index);
    }

    @Override
    public String getAttributeValue(int index) {
        if (mCurrToken != START_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        return mAttrCollector.getValue(index);
    }

    @Override
    public String getAttributeValue(String nsURI, String localName) {
        if (mCurrToken != START_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        // 22-Aug-2018, tatu: As per [woodstox-core#53], need different logic
        //   for `null` namespace URI argument
        if (nsURI == null) {
            return mAttrCollector.getValueByLocalName(localName);
        }
        return mAttrCollector.getValue(nsURI, localName);
    }

    /**
     * From StAX specs:
     *<blockquote>
     * Reads the content of a text-only element, an exception is thrown if
     * this is not a text-only element.
     * Regardless of value of javax.xml.stream.isCoalescing this method always
     * returns coalesced content.
     *<br/>Precondition: the current event is START_ELEMENT.
     *<br/>Postcondition: the current event is the corresponding END_ELEMENT. 
     *</blockquote>
     */
    @Override
    public String getElementText()
        throws XMLStreamException
    {
        if (mCurrToken != START_ELEMENT) {
            throwParseError(ErrorConsts.ERR_STATE_NOT_STELEM, null, null);
        }
        /* Ok, now: with START_ELEMENT we know that it's not partially
         * processed; that we are in-tree (not prolog or epilog).
         * The only possible complication would be:
         */
        if (mStEmptyElem) {
            /* And if so, we'll then get 'virtual' close tag; things
             * are simple as location info was set when dealing with
             * empty start element; and likewise, validation (if any)
             * has been taken care of
             */
            mStEmptyElem = false;
            mCurrToken = END_ELEMENT;
            return "";
        }

        // First need to find a textual event
        while (true) {
            int type = next();
            if (type == END_ELEMENT) {
                return "";
            }
            if (type == COMMENT || type == PROCESSING_INSTRUCTION) {
                continue;
            }
            if (((1 << type) & MASK_GET_ELEMENT_TEXT) == 0) {
                throw _constructUnexpectedInTyped(type);
            }
            break;
        }

        if (mTokenState < TOKEN_FULL_COALESCED) {
            readCoalescedText(mCurrToken, false);
        }

        /* Ok: then a quick check; if it looks like we are directly
         * followed by the end tag, we need not construct String
         * quite yet.
         */
        if ((mInputPtr + 1) < mInputEnd &&
            mInputBuffer[mInputPtr] == '<' && mInputBuffer[mInputPtr+1] == '/') {
            // Note: next() has validated text, no need for more validation
            mInputPtr += 2;
            mCurrToken = END_ELEMENT;
            // must first get text, as call to readEndElem may break it:
            String result = mTextBuffer.contentsAsString();
            // Can by-pass next(), nextFromTree(), in this case:
            readEndElem();
            // and then return results
            return result;
        }

        // Otherwise, we'll need to do slower processing
        int extra = 1 + (mTextBuffer.size() >> 1); // let's add 50% space
        StringBuilder sb = mTextBuffer.contentsAsStringBuilder(extra);
        int type;

        while ((type = next()) != END_ELEMENT) {
            if (((1 << type) & MASK_GET_ELEMENT_TEXT) != 0) {
                if (mTokenState < mStTextThreshold) {
                    finishToken(false);
                }
                verifyLimit("Text size", mConfig.getMaxTextLength(), sb.length());
                mTextBuffer.contentsToStringBuilder(sb);
                continue;
            }
            if (type != COMMENT && type != PROCESSING_INSTRUCTION) {
                throw _constructUnexpectedInTyped(type);
            }
        }
        // Note: calls next() have validated text, no need for more validation
        return sb.toString();
    }

    /**
     * Returns type of the last event returned; or START_DOCUMENT before
     * any events has been explicitly returned.
     */
    @Override
    public int getEventType()
    {
        /* Only complication -- multi-part coalesced text is to be reported
         * as CHARACTERS always, never as CDATA (StAX specs).
         */
        if (mCurrToken == CDATA) {
            if (mCfgCoalesceText || mCfgReportTextAsChars) {
                return CHARACTERS;
            }
        }
        return mCurrToken;
    }

    @Override
    public String getLocalName()
    {
        // Note: for this we need not (yet) finish reading element
        if (mCurrToken == START_ELEMENT || mCurrToken == END_ELEMENT) {
            return mElementStack.getLocalName();
        }
        if (mCurrToken == ENTITY_REFERENCE) {
            /* 30-Sep-2005, TSa: Entity will be null in non-expanding mode
             *   if no definition was found:
             */
            return (mCurrEntity == null) ? mCurrName: mCurrEntity.getName();
        }
        throw new IllegalStateException("Current state not START_ELEMENT, END_ELEMENT or ENTITY_REFERENCE");
    }

    // // // getLocation() defined in StreamScanner

    @Override
    public QName getName()
    {
        if (mCurrToken != START_ELEMENT && mCurrToken != END_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_ELEM);
        }
        return mElementStack.getCurrentElementName();
    }

    // // // Namespace access

    @Override
    public NamespaceContext getNamespaceContext() {
        /* Unlike other getNamespaceXxx methods, this is available
         * for all events.
         * Note that the context is "live", ie. remains active (but not
         * static) even through calls to next(). StAX compliant apps
         * should not count on this behaviour, however.         
         */
        return mElementStack;
    }

    @Override
    public int getNamespaceCount() {
        if (mCurrToken != START_ELEMENT && mCurrToken != END_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_ELEM);
        }
        return mElementStack.getCurrentNsCount();
    }

    @Override
    public String getNamespacePrefix(int index) {
        if (mCurrToken != START_ELEMENT && mCurrToken != END_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_ELEM);
        }
        // Internally it's marked as null, externally need to see "" or null, depending
        String p = mElementStack.getLocalNsPrefix(index);
        if (p == null) {
            return mReturnNullForDefaultNamespace ? null : XmlConsts.ATTR_NO_PREFIX;
        }
        return p;
    }

    @Override
    public String getNamespaceURI() {
        if (mCurrToken != START_ELEMENT && mCurrToken != END_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_ELEM);
        }
        // Internally it's marked as null, externally need to see ""
        String uri = mElementStack.getNsURI();
        return (uri == null) ? XmlConsts.ELEM_NO_NS_URI : uri;
    }

    @Override
    public String getNamespaceURI(int index)
    {
        if (mCurrToken != START_ELEMENT && mCurrToken != END_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_ELEM);
        }
        // Internally it's marked as null, externally need to see ""
        String uri = mElementStack.getLocalNsURI(index);
        return (uri == null) ? XmlConsts.ATTR_NO_NS_URI : uri;
    }

    @Override
    public String getNamespaceURI(String prefix)
    {
        if (mCurrToken != START_ELEMENT && mCurrToken != END_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_ELEM);
        }
        /* Note: this will need to return null if no URI found for
         * the prefix, so we can't mask it.
         */
        return mElementStack.getNamespaceURI(prefix);
    }

    @Override
    public String getPIData() {
        if (mCurrToken != PROCESSING_INSTRUCTION) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_PI);
        }
        if (mTokenState <= TOKEN_STARTED) {
            safeFinishToken();
        }
        return mTextBuffer.contentsAsString();
    }

    @Override
    public String getPITarget() {
        if (mCurrToken != PROCESSING_INSTRUCTION) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_PI);
        }
        // Target is always parsed automatically, not lazily...
        return mCurrName;
    }

    @Override
    public String getPrefix() {
        if (mCurrToken != START_ELEMENT && mCurrToken != END_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_ELEM);
        }
        // Internally it's marked as null, externally need to see ""
        String p = mElementStack.getPrefix();
        return (p == null) ? XmlConsts.ELEM_NO_PREFIX : p;
    }

    @Override
    public String getText()
    {
        final int currToken = mCurrToken;
        if (((1 << currToken) & MASK_GET_TEXT) == 0) {
            throwNotTextual(currToken);
        }
        if (mTokenState < mStTextThreshold) {
            safeFinishToken();
        }
        if (currToken == ENTITY_REFERENCE) {
            return (mCurrEntity == null) ? null : mCurrEntity.getReplacementText();
        }
        if (currToken == DTD) {
            // 16-Aug-2004, TSa: Hmmh. Specs are bit ambiguous on whether this
            //   should return just the internal subset, or the whole thing...
            return getDTDInternalSubset();
        }
        return mTextBuffer.contentsAsString();
    }

    @Override
    public char[] getTextCharacters()
    {
        final int currToken = mCurrToken;
        if (((1 << currToken) & MASK_GET_TEXT_XXX) == 0) {
            throwNotTextXxx(currToken);
        }
        if (mTokenState < mStTextThreshold) {
            safeFinishToken();
        }
        if (currToken == ENTITY_REFERENCE) {
            return mCurrEntity.getReplacementChars();
        }
        if (currToken == DTD) {
            return getDTDInternalSubsetArray();
        }
        return mTextBuffer.getTextBuffer();
    }

    @Override
    public int getTextCharacters(int sourceStart, char[] target, int targetStart, int len)
    {
        final int currToken = mCurrToken;
        if (((1 << currToken) & MASK_GET_TEXT_XXX) == 0) {
            throwNotTextXxx(currToken);
        }
        if (mTokenState < mStTextThreshold) {
            safeFinishToken();
        }
        return mTextBuffer.contentsToArray(sourceStart, target, targetStart, len);
    }

    @Override
    public int getTextLength()
    {
        final int currToken = mCurrToken;
        if (((1 << currToken) & MASK_GET_TEXT_XXX) == 0) {
            throwNotTextXxx(currToken);
        }
        if (mTokenState < mStTextThreshold) {
            safeFinishToken();
        }
        return mTextBuffer.size();
    }

    @Override
    public int getTextStart()
    {
        final int currToken = mCurrToken;
        if (((1 << currToken) & MASK_GET_TEXT_XXX) == 0) {
            throwNotTextXxx(currToken);
        }
        if (mTokenState < mStTextThreshold) {
            safeFinishToken();
        }
        return mTextBuffer.getTextStart();
    }

    @Override
    public boolean hasName() {
        return (mCurrToken == START_ELEMENT) || (mCurrToken == END_ELEMENT);
    }

    @Override
    public boolean hasNext() {
        // 08-Oct-2005, TSa: In multi-doc mode, we have different criteria...
        return (mCurrToken != END_DOCUMENT)
            || (mParseState == STATE_MULTIDOC_HACK);
    }

    @Override
    public boolean hasText() {
        return (((1 << mCurrToken) & MASK_GET_TEXT) != 0);
    }

    @Override
    public boolean isAttributeSpecified(int index)
    {
        /* No need to check for ATTRIBUTE since we never return that...
         */
        if (mCurrToken != START_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        return mAttrCollector.isSpecified(index);
    }

    @Override
    public boolean isCharacters()
    {
        /* 21-Dec-2005, TSa: Changed for 3.0 to work the same way as stax
         *    ref impl.
         */
        //return (mCurrToken == CHARACTERS || mCurrToken == CDATA || mCurrToken == SPACE);
        /* 21-Apr-2009, TSa: As per [WSTX-201], should be consistent with
         *   what getEventType() returns (affects CDATA, SPACE, in
         *   coalescing mode or when explicitly asked to return CDATA
         *   as CHARACTERS)
         */
        return (CHARACTERS == getEventType());
    }

    @Override
    public boolean isEndElement() {
        return (mCurrToken == END_ELEMENT);
    }

    @Override
    public boolean isStartElement() {
        return (mCurrToken == START_ELEMENT);
    }

    /**
     *<p>
     * 05-Apr-2004, TSa: Could try to determine status when text is actually
     *   read. That'd prevent double reads... but would it slow down that
     *   one reading so that net effect would be negative?
     */
    @Override
    public boolean isWhiteSpace()
    {
        final int currToken = mCurrToken;
        if (currToken == CHARACTERS || currToken == CDATA) {
            if (mTokenState < mStTextThreshold) {
                safeFinishToken();
            }
            if (mWsStatus == ALL_WS_UNKNOWN) {
                mWsStatus = mTextBuffer.isAllWhitespace() ?
                    ALL_WS_YES : ALL_WS_NO;
            }
            return mWsStatus == ALL_WS_YES;
        }
        return (currToken == SPACE);
    }

    @Override
    public void require(int type, String nsUri, String localName)
        throws XMLStreamException
    {
        int curr = mCurrToken;

        /* There are some special cases; specifically, CDATA
         * is sometimes reported as CHARACTERS. Let's be lenient by
         * allowing both 'real' and reported types, for now.
         */
        if (curr != type) {
            if (curr == CDATA) {
                if (mCfgCoalesceText || mCfgReportTextAsChars) {
                    curr = CHARACTERS;
                }
            } else if (curr == SPACE) {
                // Hmmh. Should we require it to be empty or something?
                //curr = CHARACTERS;
                // For now, let's not change the check
            }
        }

        if (type != curr) {
            throwParseError("Expected type "+tokenTypeDesc(type)
                            +", current type "
                            +tokenTypeDesc(curr));
        }

        if (localName != null) {
            if (curr != START_ELEMENT && curr != END_ELEMENT
                && curr != ENTITY_REFERENCE) {
                throwParseError("Expected non-null local name, but current token not a START_ELEMENT, END_ELEMENT or ENTITY_REFERENCE (was "+tokenTypeDesc(mCurrToken)+")");
            }
            String n = getLocalName();
            if (n != localName && !n.equals(localName)) {
                throwParseError("Expected local name '"+localName+"'; current local name '"+n+"'.");
            }
        }
        if (nsUri != null) {
            if (curr != START_ELEMENT && curr != END_ELEMENT) {
                throwParseError("Expected non-null NS URI, but current token not a START_ELEMENT or END_ELEMENT (was "+tokenTypeDesc(curr)+")");
            }
            String uri = mElementStack.getNsURI();
            // No namespace?
            if (nsUri.length() == 0) {
                if (uri != null && uri.length() > 0) {
                    throwParseError("Expected empty namespace, instead have '"+uri+"'.");
                }
            } else {
                if ((nsUri != uri) && !nsUri.equals(uri)) {
                    throwParseError("Expected namespace '"+nsUri+"'; have '"
                                    +uri+"'.");
                }
            }
        }
        // Ok, fine, all's good
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // XMLStreamReader, iterating
    ///////////////////////////////////////////////////////////////////////
     */

    @Override
    public final int next() throws XMLStreamException
    {
        /* 24-Sep-2006, TSa: We may have deferred an exception that occurred
         *   during parsing of the previous event. If so, now it needs to
         *   be thrown.
         */
        if (mPendingException != null) {
            XMLStreamException strEx = mPendingException;
            mPendingException = null;
            throw strEx;
        }

        /* Note: can not yet accurately record the location, since the
         * previous event might not yet be completely finished...
         */
        if (mParseState == STATE_TREE) {
            int type = nextFromTree();
            mCurrToken = type;
            if (mTokenState < mStTextThreshold) { // incomplete?
                /* Can remain incomplete if lazy parsing is enabled,
                 * and this is not a validatable text segment; otherwise
                 * must finish
                 */
                if (!mCfgLazyParsing ||
                    (mValidateText && (type == CHARACTERS || type == CDATA))) {
                    finishToken(false);
                }
            }

            /* Special cases -- sometimes (when coalescing text, or
             * when specifically configured to do so), CDATA and SPACE are
             * to be reported as CHARACTERS, although we still will
             * internally keep track of the real type.
             */
            if (type == CDATA) {
                if (mValidateText) {
                    mElementStack.validateText(mTextBuffer, false);
                }
                if (mCfgCoalesceText || mCfgReportTextAsChars) {
                    return CHARACTERS;
                }
                /*
                  } else if (type == SPACE) {
                  //if (mValidateText) { throw new IllegalStateException("Internal error: trying to validate SPACE event"); }
                  */
                mCurrTextLength += mTextBuffer.size();
                verifyLimit("Text size", mConfig.getMaxTextLength(), mCurrTextLength);
            } else if (type == CHARACTERS) {
                if (mValidateText) {
                    /* We may be able to determine that there will be
                     * no more text coming for this element: but only
                     * seeing the end tag marker ("</") is certain
                     * (PIs and comments won't do, nor CDATA; start
                     * element possibly... but that indicates mixed
                     * content that's generally non-validatable)
                         */
                    if ((mInputPtr+1) < mInputEnd
                        && mInputBuffer[mInputPtr] == '<'
                        && mInputBuffer[mInputPtr+1] == '/') {
                        // yup, it's all there is
                        mElementStack.validateText(mTextBuffer, true);
                    } else {
                        mElementStack.validateText(mTextBuffer, false);
                    }
                }
                mCurrTextLength += mTextBuffer.size();
                verifyLimit("Text size", mConfig.getMaxTextLength(), mCurrTextLength);
            } else if (type == START_ELEMENT || type == END_ELEMENT) {
                this.mCurrTextLength = 0;
            }
            return type;
        }

        if (mParseState == STATE_PROLOG) {
            nextFromProlog(true);
        } else if (mParseState == STATE_EPILOG) {
            if (nextFromProlog(false)) {
                // We'll return END_DOCUMENT, need to mark it 'as consumed'
                mSecondaryToken = 0;
                
            }
        } else if (mParseState == STATE_MULTIDOC_HACK) {
            mCurrToken = nextFromMultiDocState();
        } else { // == STATE_CLOSED
            if (mSecondaryToken == END_DOCUMENT) { // marker
                mSecondaryToken = 0; // mark end doc as consumed
                return END_DOCUMENT;
            }
            throw new java.util.NoSuchElementException();
        }
        return mCurrToken;
    }

    @Override
    public int nextTag() throws XMLStreamException
    {
        while (true) {
            int next = next();

            switch (next) {
            case SPACE:
            case COMMENT:
            case PROCESSING_INSTRUCTION:
                continue;
            case CDATA:
            case CHARACTERS:
                // inlined version of "isWhiteSpace()", so that exceptions can be passed as-is
                // without suppression
                if (mTokenState < mStTextThreshold) {
                    finishToken(false);
                }
                if (mWsStatus == ALL_WS_UNKNOWN) {
                    mWsStatus = mTextBuffer.isAllWhitespace() ? ALL_WS_YES : ALL_WS_NO;
                }
                if (mWsStatus == ALL_WS_YES) {
                    continue;
                }
                throwParseError("Received non-all-whitespace CHARACTERS or CDATA event in nextTag().");
                break; // never gets here, but jikes complains without
            case START_ELEMENT:
            case END_ELEMENT:
                return next;
            }
            throwParseError("Received event "+ErrorConsts.tokenTypeDesc(next)
                            +", instead of START_ELEMENT or END_ELEMENT.");
        }
    }

    /**
     *<p>
     * Note: as per StAX 1.0 specs, this method does NOT close the underlying
     * input reader. That is, unless the new StAX2 property
     * {@link org.codehaus.stax2.XMLInputFactory2#P_AUTO_CLOSE_INPUT} is
     * set to true.
     */
    @Override
    public void close() throws XMLStreamException
    {
        if (mParseState != STATE_CLOSED) {
            mParseState = STATE_CLOSED;
            /* Let's see if we should notify factory that symbol table
             * has new entries, and may want to reuse this symbol table
             * instead of current root.
             */
            if (mCurrToken != END_DOCUMENT) {
                mCurrToken = mSecondaryToken = END_DOCUMENT;
                if (mSymbols.isDirty()) {
                    mOwner.updateSymbolTable(mSymbols);
                }
            }
            /* Hmmh. Actually, we need to close all the dependant input
             * sources, first, and then also call close() 
             * on the root input source object; it
             * will only do real close if that was enabled earlier.
             * The root input source also prevents multiple close() calls
             * for the underlying source, so we need not check that here.
             */
            closeAllInput(false);
            // And finally, can now recycle low-level (text) buffers
            mTextBuffer.recycle(true);
        }
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // XMLStreamReader2 (StAX2) implementation
    ///////////////////////////////////////////////////////////////////////
     */

    @Override
    @Deprecated
    public Object getFeature(String name)  {
        throw new IllegalArgumentException(MessageFormat.format(ErrorConsts.ERR_UNKNOWN_FEATURE, new Object[] { name })); 
    }

    @Override
    @Deprecated
    public void setFeature(String name, Object value) {
        throw new IllegalArgumentException(MessageFormat.format(ErrorConsts.ERR_UNKNOWN_FEATURE, new Object[] { name })); 
    }

    // NOTE: getProperty() defined in Stax 1.0 interface

    @Override
    public boolean isPropertySupported(String name) {
        // !!! TBI: not all these properties are really supported
        return mConfig.isPropertySupported(name);
    }

    /**
     * @param name Name of the property to set
     * @param value Value to set property to.
     *
     * @return True, if the specified property was <b>succesfully</b>
     *    set to specified value; false if its value was not changed
     */
    @Override
    public boolean setProperty(String name, Object value)
    {
        boolean ok = mConfig.setProperty(name, value);
        /* To make [WSTX-50] work fully dynamically (i.e. allow
         * setting BASE_URL after stream reader has been constructed)
         * need to force
         */
        if (ok && WstxInputProperties.P_BASE_URL.equals(name)) {
            // Easiest to just access from config: may come in as a String etc
            mInput.overrideSource(mConfig.getBaseURL());
        }
        return ok;
    }

    // // // StAX2, additional traversal methods

    @Override
    public void skipElement() throws XMLStreamException
    {
        if (mCurrToken != START_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        int nesting = 1; // need one more end elements than start elements

        while (true) {
            int type = next();
            if (type == START_ELEMENT) {
                ++nesting;
            } else if (type == END_ELEMENT) {
                if (--nesting == 0) {
                    break;
                }
            }
        }
    }

    // // // StAX2, additional attribute access

    @Override
    public AttributeInfo getAttributeInfo() throws XMLStreamException
    {
        if (mCurrToken != START_ELEMENT) {
            throw new IllegalStateException(ErrorConsts.ERR_STATE_NOT_STELEM);
        }
        /* Although attribute collector knows about specific parsed
         * information, the element stack has DTD-derived information (if
         * any)... and knows how to call attribute collector when necessary.
         */
        return mElementStack;
    }

    // // // StAX2, Additional DTD access

    /**
     * Since this class implements {@link DTDInfo}, method can just
     * return <code>this</code>.
     */
    @Override
    public DTDInfo getDTDInfo() throws XMLStreamException
    {
        /* Let's not allow it to be accessed during other events -- that
         * way callers won't count on it being available afterwards.
         */
        if (mCurrToken != DTD) {
            return null;
        }
        if (mTokenState < TOKEN_FULL_SINGLE) { // need to fully read it in now
            finishToken(false);
        }
        return this;
    }

    // // // StAX2, Additional location information

    /**
     * Location information is always accessible, for this reader.
     */
    @Override
    public final LocationInfo getLocationInfo() {
        return this;
    }

    // // // StAX2, Pass-through text accessors


    /**
     * Method similar to {@link #getText()}, except
     * that it just uses provided Writer to write all textual content.
     * For further optimization, it may also be allowed to do true
     * pass-through, thus possibly avoiding one temporary copy of the
     * data.
     *<p>
     * TODO: try to optimize to allow completely streaming pass-through:
     * currently will still read all data in memory buffers before
     * outputting
     * 
     * @param w Writer to use for writing textual contents
     * @param preserveContents If true, reader has to preserve contents
     *   so that further calls to <code>getText</code> will return
     *   proper conntets. If false, reader is allowed to skip creation
     *   of such copies: this can improve performance, but it also means
     *   that further calls to <code>getText</code> is not guaranteed to
     *   return meaningful data.
     *
     * @return Number of characters written to the reader
     */
    @Override
    public int getText(Writer w, boolean preserveContents)
        throws IOException, XMLStreamException
    {
        final int currToken = mCurrToken;
        if (((1 << currToken) & MASK_GET_TEXT_WITH_WRITER) == 0) {
            throwNotTextual(currToken);
        }
        /* May need to be able to do fully streaming... but only for
         * text events that have not yet been fully read; for other
         * types there's less benefit, and for fully read ones, we
         * already have everything ready.
         */
        if (!preserveContents) {
            if (currToken == CHARACTERS) {
                int count = mTextBuffer.rawContentsTo(w);
                /* Let's also clear whatever was collected (as allowed by
                 * method contract) previously, to both save memory, and
                 * to ensure caller doesn't accidentally try to access it
                 * (and get otherwise 'random' results).
                 */
                mTextBuffer.resetWithEmpty();
                if (mTokenState < TOKEN_FULL_SINGLE) {
                    count += readAndWriteText(w);
                }
                if (mCfgCoalesceText &&
                    (mTokenState < TOKEN_FULL_COALESCED)) {
                    if (mCfgCoalesceText) {
                        count += readAndWriteCoalesced(w, false);
                    }
                }
                return count;
            } else if (currToken == CDATA) {
                int count = mTextBuffer.rawContentsTo(w);
                mTextBuffer.resetWithEmpty(); // same as with CHARACTERS
                if (mTokenState < TOKEN_FULL_SINGLE) {
                    count += readAndWriteCData(w);
                }
                if (mCfgCoalesceText &&
                    (mTokenState < TOKEN_FULL_COALESCED)) {
                    if (mCfgCoalesceText) {
                        count += readAndWriteCoalesced(w, true);
                    }
                }
                return count;
            }
        }
        if (mTokenState < mStTextThreshold) {
            /* Otherwise, let's just finish the token; and due to guarantee
             * by streaming method, let's try ensure we get it all.
             */
            finishToken(false); // false -> shouldn't defer errors
        }
        if (currToken == ENTITY_REFERENCE) {
            return mCurrEntity.getReplacementText(w);
        }
        if (currToken == DTD) {
            char[] ch = getDTDInternalSubsetArray();
            if (ch != null) {
                w.write(ch);
                return ch.length;
            }
            return 0;
        }
        return mTextBuffer.rawContentsTo(w);
    }

    // // // StAX 2, Other accessors

    /**
     * @return Number of open elements in the stack; 0 when parser is in
     *  prolog/epilog, 1 inside root element and so on.
     */
    @Override
    public int getDepth() {
        /* Note: we can not necessarily use mCurrDepth, since it is
         * directly synchronized to the input (to catch unbalanced entity
         * expansion WRT element nesting), and not to actual token values
         * returned.
         */
        return mElementStack.getDepth();
    }

    /**
     * @return True, if cursor points to a start or end element that is
     *    constructed from 'empty' element (ends with '/>');
     *    false otherwise.
     */
    @Override
    public boolean isEmptyElement() throws XMLStreamException {
        return (mCurrToken == START_ELEMENT) ? mStEmptyElem : false;
    }

    @Override
    public NamespaceContext getNonTransientNamespaceContext() {
        // null -> no Location info, not needed with basic API
        return mElementStack.createNonTransientNsContext(null);
    }

    @Override
    public String getPrefixedName()
    {
        switch (mCurrToken) {
        case START_ELEMENT:
        case END_ELEMENT:
            {
                String prefix = mElementStack.getPrefix();
                String ln = mElementStack.getLocalName();

                if (prefix == null) {
                    return ln;
                }
                StringBuilder sb = new StringBuilder(ln.length() + 1 + prefix.length());
                sb.append(prefix);
                sb.append(':');
                sb.append(ln);
                return sb.toString();
            }
        case ENTITY_REFERENCE:
            return getLocalName();
        case PROCESSING_INSTRUCTION:
            return getPITarget();
        case DTD:
            return getDTDRootName();

        }
        throw new IllegalStateException("Current state not START_ELEMENT, END_ELEMENT, ENTITY_REFERENCE, PROCESSING_INSTRUCTION or DTD");
    }

    @Override
    public void closeCompletely() throws XMLStreamException {
        closeAllInput(true);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // DTDInfo implementation (StAX 2)
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     *<p>
     * Note: DTD-handling sub-classes need to override this method.
     */
    @Override
    public Object getProcessedDTD() {
        return null;
    }

    @Override
    public String getDTDRootName() {
        if (mRootPrefix == null) {
            return mRootLName;
        }
        return mRootPrefix + ":" + mRootLName;
    }

    @Override
    public String getDTDPublicId() {
        return mDtdPublicId;
    }

    @Override
    public String getDTDSystemId() {
        return mDtdSystemId;
    }

    /**
     * @return Internal subset portion of the DOCTYPE declaration, if any;
     *   empty String if none
     */
    @Override
    public String getDTDInternalSubset() {
        if (mCurrToken != DTD) {
            return null;
        }
        return mTextBuffer.contentsAsString();
    }

    /**
     * Internal method used by implementation
     */
    private char[] getDTDInternalSubsetArray() {
        /* Note: no checks for current state, but only because it's
         * an internal method and callers are known to ensure it's ok
         * to call this
         */
        return mTextBuffer.contentsAsArray();
    }

    // // StAX2, v2.0

    /**
     * Sub-class will override this method
     */
    @Override
    public DTDValidationSchema getProcessedDTDSchema() {
        return null;
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // LocationInfo implementation (StAX 2)
    ///////////////////////////////////////////////////////////////////////
     */

    // // // First, the "raw" offset accessors:

    @Override
    public long getStartingByteOffset() {
        /* 15-Apr-2005, TSa: No way to reliably keep track of byte offsets,
         *   at least for variable-length encodings... so let's just
         *   return -1 for now
         */
        return -1L;
    }

    @Override
    public long getStartingCharOffset() {
        return mTokenInputTotal;
    }

    @Override
    public long getEndingByteOffset() throws XMLStreamException
    {
        /* 15-Apr-2005, TSa: No way to reliably keep track of byte offsets,
         *   at least for variable-length encodings... so let's just
         *   return -1 for now
         */
        return -1;
    }

    @Override
    public long getEndingCharOffset() throws XMLStreamException
    {
        // Need to get to the end of the token, if not there yet
        if (mTokenState < mStTextThreshold) {
            finishToken(false);
        }
        return mCurrInputProcessed + mInputPtr;
    }

    // // // and then the object-based access methods:

    @Override
    public final Location getLocation() {
        return getStartLocation();
    }

    // public XMLStreamLocation2 getStartLocation() // from base class
    // public XMLStreamLocation2 getCurrentLocation() // - "" -

    @Override
    public final XMLStreamLocation2 getEndLocation()
        throws XMLStreamException
    {
        // Need to get to the end of the token, if not there yet
        if (mTokenState < mStTextThreshold) {
            finishToken(false);
        }
        // And then we just need the current location!
        return getCurrentLocation();
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Stax2 validation
    ///////////////////////////////////////////////////////////////////////
     */

    @Override
    public XMLValidator validateAgainst(XMLValidationSchema schema)
        throws XMLStreamException
    {
        // Not implemented by the basic reader:
        return null;
    }

    @Override
    public XMLValidator stopValidatingAgainst(XMLValidationSchema schema)
        throws XMLStreamException
    {
        // Not implemented by the basic reader:
        return null;
    }

    @Override
    public XMLValidator stopValidatingAgainst(XMLValidator validator)
        throws XMLStreamException
    {
        // Not implemented by the basic reader:
        return null;
    }

    @Override
    public ValidationProblemHandler setValidationProblemHandler(ValidationProblemHandler h)
    {
        // Not implemented by the basic reader:
        return null;
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // StreamReaderImpl implementation
    ///////////////////////////////////////////////////////////////////////
     */

    @Override
    public EntityDecl getCurrentEntityDecl() {
        return mCurrEntity;
    }

    /**
     * Method called by {@link com.ctc.wstx.evt.DefaultEventAllocator}
     * to get double-indirection necessary for constructing start element
     * events.
     *
     * @return Null, if stream does not point to start element; whatever
     *    callback returns otherwise.
     */
    @Override
    public Object withStartElement(ElemCallback cb, Location loc)
    {
        if (mCurrToken != START_ELEMENT) {
            return null;
        }
        return cb.withStartElement(loc, getName(), 
                mElementStack.createNonTransientNsContext(loc),
                mAttrCollector.buildAttrOb(),
                mStEmptyElem);
    }

    @Override
    public boolean isNamespaceAware() {
        return mCfgNsEnabled;
    }

    /**
     * Method needed by classes (like stream writer implementations)
     * that want to have efficient direct access to element stack
     * implementation
     */
    @Override
    public InputElementStack getInputElementStack() {
        return mElementStack;
    }

    /**
     * Method needed by classes (like stream writer implementations)
     * that want to have efficient direct access to attribute collector
     * Object, for optimal attribute name and value access.
     */
    @Override
    public AttributeCollector getAttributeCollector() {
        return mAttrCollector;
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Support for SAX XMLReader implementation
    ///////////////////////////////////////////////////////////////////////
     */

    public void fireSaxStartElement(ContentHandler h, Attributes attrs)
        throws SAXException
    {
        if (h != null) {
            // First; any ns declarations?
            int nsCount = mElementStack.getCurrentNsCount();
            for (int i = 0; i < nsCount; ++i) {
                String prefix = mElementStack.getLocalNsPrefix(i);
                String uri = mElementStack.getLocalNsURI(i);
                h.startPrefixMapping((prefix == null) ? "" : prefix, uri);
            }

            // Then start-elem event itself:
            String uri = mElementStack.getNsURI();
            // Sax requires "" (not null) for ns uris...
            h.startElement((uri == null) ? "" : uri,
                           mElementStack.getLocalName(), getPrefixedName(), attrs);
        }
    }

    public void fireSaxEndElement(ContentHandler h)
        throws SAXException
    {
        if (h != null) {
            /* Order of events is reversed (wrt. start-element): first
             * the end tag event, then unbound prefixes
             */
            String uri = mElementStack.getNsURI();
            // Sax requires "" (not null) for ns uris...
            h.endElement((uri == null) ? "" : uri,
                         mElementStack.getLocalName(), getPrefixedName());
            // Any expiring ns declarations?
            int nsCount = mElementStack.getCurrentNsCount();
            for (int i = 0; i < nsCount; ++i) {
                String prefix = mElementStack.getLocalNsPrefix(i);
                //String nsUri = mElementStack.getLocalNsURI(i);
                h.endPrefixMapping((prefix == null) ? "" : prefix);
            }
        }
    }

    public void fireSaxCharacterEvents(ContentHandler h)
        throws XMLStreamException, SAXException
    {
        if (h != null) {
            if (mPendingException != null) {
                XMLStreamException sex = mPendingException;
                mPendingException = null;
                throw sex;
            }
            /* Let's not defer errors; SAXTest implies
             * it's expected errors are thrown right away
             */
            if (mTokenState < mStTextThreshold) {
                finishToken(false);
            }
            mTextBuffer.fireSaxCharacterEvents(h);
        }
    }

    public void fireSaxSpaceEvents(ContentHandler h)
        throws XMLStreamException, SAXException
    {
        if (h != null) {
            if (mTokenState < mStTextThreshold) {
                finishToken(false); // no error deferring
            }
            mTextBuffer.fireSaxSpaceEvents(h);
        }
    }

    public void fireSaxCommentEvent(LexicalHandler h)
        throws XMLStreamException, SAXException
    {
        if (h != null) {
            if (mTokenState < mStTextThreshold) {
                finishToken(false); // no error deferring
            }
            mTextBuffer.fireSaxCommentEvent(h);
        }
    }

    public void fireSaxPIEvent(ContentHandler h)
        throws XMLStreamException, SAXException
    {
        if (h != null) {
            if (mTokenState < mStTextThreshold) {
                finishToken(false); // no error deferring
            }
            h.processingInstruction(mCurrName, mTextBuffer.contentsAsString());
        }
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Internal methods, config access
    ///////////////////////////////////////////////////////////////////////
     */

    protected final boolean hasConfigFlags(int flags) {
        return (mConfigFlags & flags) == flags;
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Internal methods, parsing helper methods
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * @return Null, if keyword matches ok; String that contains erroneous
     *   keyword if not.
     */
    protected String checkKeyword(char c, String expected)
        throws XMLStreamException
    {
      int ptr = 0;
      int len = expected.length();

      while (expected.charAt(ptr) == c && ++ptr < len) {
          if (mInputPtr < mInputEnd) {
              c = mInputBuffer[mInputPtr++];
          } else {
              int ci = getNext();
              if (ci < 0) { // EOF
                  break;
              }
              c = (char) ci;
          }
      }
      
      if (ptr == len) {
          // Probable match... but let's make sure keyword is finished:
          int i = peekNext();
          if (i < 0 || (!isNameChar((char) i) && i != ':')) {
              return null;
          }
          // Nope, continues, need to find the rest:
      }
      
      StringBuilder sb = new StringBuilder(expected.length() + 16);
      sb.append(expected.substring(0, ptr));
      if (ptr < len) {
          sb.append(c);
      }

      while (true) {
          if (mInputPtr < mInputEnd) {
              c = mInputBuffer[mInputPtr++];
          } else {
              int ci = getNext();
              if (ci < 0) { // EOF
                  break;
              }
              c = (char) ci;
          }
          if (!isNameChar(c)) {
              // Let's push it back then
              --mInputPtr;
              break;
          }
          sb.append(c);
      }

      return sb.toString();
    }

    protected void checkCData() throws XMLStreamException
    {
        String wrong = checkKeyword(getNextCharFromCurrent(SUFFIX_IN_CDATA), "CDATA");
        if (wrong != null) {
            throwParseError("Unrecognized XML directive '"+wrong+"'; expected 'CDATA'.");
        }
        // Plus, need the bracket too:
        char c = getNextCharFromCurrent(SUFFIX_IN_CDATA);
        if (c != '[') {
            throwUnexpectedChar(c, "excepted '[' after '<![CDATA'");
        }
        // Cool, that's it!
    }

    /**
     * Method that will parse an attribute value enclosed in quotes, using
     * an {@link TextBuilder} instance. Will normalize white space inside
     * attribute value using default XML rules (change linefeeds to spaces
     * etc.; but won't use DTD information for further coalescing).
     *
     * @param openingQuote Quote character (single or double quote) for
     *   this attribute value
     * @param tb TextBuilder into which attribute value will be added
     */
    private final void parseAttrValue(char openingQuote, TextBuilder tb)
        throws XMLStreamException
    {
        char[] outBuf = tb.getCharBuffer();
        int outPtr = tb.getCharSize();
        int outLen = outBuf.length;
        WstxInputSource currScope = mInput;

        while (true) {
            char c = (mInputPtr < mInputEnd) ? mInputBuffer[mInputPtr++]
                : getNextChar(SUFFIX_IN_ATTR_VALUE);
            // Let's do a quick for most attribute content chars:
            if (c <= '\'') {
                if (c < CHAR_SPACE) {
                    if (c == '\n') {
                        markLF();
                    } else if (c == '\r') {
                        /* 04-Mar-2006, TSa: Linefeed normalization only
                         *   done if enabled -- specifically, 2-char lfs
                         *   from int. entities are not coalesced. Now...
                         *   whether to try to count them as one or not...
                         *   easier not to; esp. since we may not be able to
                         *   distinguish char entity originated ones from
                         *   real ones.
                         */
                        if (mNormalizeLFs) {
                            c = getNextChar(SUFFIX_IN_ATTR_VALUE);
                            if (c != '\n') { // nope, not 2-char lf (Mac?)
                                --mInputPtr;
                            }
                        }
                        markLF();
                    } else if (c != '\t') {
                        throwInvalidSpace(c);
                    }
                    // Whatever it was, it'll be 'normal' space now.
                    c = CHAR_SPACE;
                } else if (c == openingQuote) {
                    /* 06-Aug-2004, TSa: Can get these via entities; only "real"
                     *    end quotes in same scope count. Note, too, that since
                     *    this will only be done at root level, there's no need
                     *    to check for "runaway" values; they'll hit EOF
                     */
                    if (mInput == currScope) {
                        break;
                    }
                } else if (c == '&') { // an entity of some sort...
                    int ch;
                    if (inputInBuffer() >= 3
                        && (ch = resolveSimpleEntity(true)) != 0) {
                        // Ok, fine, c is whatever it is
                        ;
                    } else { // full entity just changes buffer...
                        ch = fullyResolveEntity(false);
                        if (ch == 0) {
                            // need to skip output, thusly (expanded to new input source)
                            continue;
                        }
                    }
                    if (ch <= 0xFFFF) {
                        c = (char) ch;
                    } else {
                        ch -= 0x10000;
                        if (outPtr >= outLen) {
                            outBuf = tb.bufferFull(1);
                            outLen = outBuf.length;
                        }
                        outBuf[outPtr++] = (char) ((ch >> 10)  + 0xD800);
                        c = (char) ((ch & 0x3FF)  + 0xDC00);
                    }
                }
            } else if (c == '<') {
                throwUnexpectedChar(c, SUFFIX_IN_ATTR_VALUE);
            }

            // Ok, let's just add char in, whatever it was
            if (outPtr >= outLen) {
                verifyLimit("Maximum attribute size", mConfig.getMaxAttributeSize(), tb.getCharSize());
                outBuf = tb.bufferFull(1);
                outLen = outBuf.length;
            }
            outBuf[outPtr++] = c;
        }

        // Fine; let's tell TextBuild we're done:
        tb.setBufferSize(outPtr);
    }
    
    /*
    ///////////////////////////////////////////////////////////////////////
    // Internal methods, parsing prolog (before root) and epilog
    ///////////////////////////////////////////////////////////////////////
     */
    
    /**
     * Method called to find type of next token in prolog; either reading
     * just enough information to know the type (lazy parsing), or the
     * full contents (non-lazy)
     *
     * @return True if we hit EOI, false otherwise
     */
    private boolean nextFromProlog(boolean isProlog)
        throws XMLStreamException
    {
        int i;

        // First, do we need to finish currently open token?
        if (mTokenState < mStTextThreshold) {
            mTokenState = TOKEN_FULL_COALESCED;
            i = skipToken();
            // note: skipToken() updates the start location
        } else {
            // Need to update the start location...
            mTokenInputTotal = mCurrInputProcessed + mInputPtr;
            mTokenInputRow = mCurrInputRow;
            mTokenInputCol = mInputPtr - mCurrInputRowStart;
            i = getNext();
        }

        // Any white space to parse or skip?
        if (i <= CHAR_SPACE && i >= 0) {
            // Need to return as an event?
            if (hasConfigFlags(CFG_REPORT_PROLOG_WS)) {
                mCurrToken = SPACE;
                if (readSpacePrimary((char) i, true)) {
                    /* no need to worry about coalescing, since CDATA is not
                     * allowed at this level...
                     */
                    mTokenState = TOKEN_FULL_COALESCED;
                } else {
                    if (mCfgLazyParsing) {
                        /* Let's not even bother checking if it's
                         * "long enough"; shouldn't usually matter, but few
                         * apps care to get multiple adjacent SPACE events...
                         */
                        mTokenState = TOKEN_STARTED;
                    } else {
                        readSpaceSecondary(true);
                        mTokenState = TOKEN_FULL_COALESCED;
                    }
                }
                return false;
            }
            // If not, can skip it right away
            --mInputPtr; // to handle linefeeds gracefully
            i = getNextAfterWS();
            if (i >= 0) {
                // ... after which location has to be reset properly:
                /* 11-Apr-2005, TSa: But note that we need to "move back"
                 *   column and total offset values by one, to compensate
                 *   for the char that was read (row can not have changed,
                 *   since it's non-WS, and thus non-lf/cr char)
                 */
                mTokenInputTotal = mCurrInputProcessed + mInputPtr - 1;
                mTokenInputRow = mCurrInputRow;
                mTokenInputCol = mInputPtr - mCurrInputRowStart - 1;
            }
        }

        // Did we hit EOI?
        if (i < 0) {
            handleEOF(isProlog);
            mParseState = STATE_CLOSED;
            return true;
        }

        // Now we better have a lt...
        if (i != '<') {
            throwUnexpectedChar(i, (isProlog ? SUFFIX_IN_PROLOG : SUFFIX_IN_EPILOG)
                                +"; expected '<'");
        }

        // And then it should be easy to figure out type:
        char c = getNextChar(isProlog ? SUFFIX_IN_PROLOG : SUFFIX_IN_EPILOG);

        if (c == '?') { // proc. inst
            mCurrToken = readPIPrimary();
        } else  if (c == '!') { // DOCTYPE or comment (or CDATA, but not legal here)
            // Need to figure out bit more first...
            nextFromPrologBang(isProlog);
        } else if (c == '/') { // end tag not allowed...
            if (isProlog) {
                throwParseError("Unexpected character combination '</' in prolog.");
            }
            throwParseError("Unexpected character combination '</' in epilog (extra close tag?).");
        } else if (c == ':' || isNameStartChar(c)) {
            // Root element, only allowed after prolog
            if (!isProlog) {
                /* This call will throw an exception if there's a problem;
                 * otherwise set up everything properly
                 */
                mCurrToken = handleExtraRoot(c); // will check input parsing mode...
                return false;
            }
            handleRootElem(c);
            mCurrToken = START_ELEMENT;
        } else {
            throwUnexpectedChar(c, (isProlog ? SUFFIX_IN_PROLOG : SUFFIX_IN_EPILOG)
                                +", after '<'.");
        }

        // Ok; final twist, maybe we do NOT want lazy parsing?
        if (!mCfgLazyParsing && mTokenState < mStTextThreshold) {
            finishToken(false);
        }

        return false;
    }

    protected void handleRootElem(char c)
        throws XMLStreamException
    {
        mParseState = STATE_TREE;
        initValidation();
        handleStartElem(c);
        // Does name match with DOCTYPE declaration (if any)?
        // 20-Jan-2006, TSa: Only check this is we are (DTD) validating...
        if (mRootLName != null) {
            if (hasConfigFlags(CFG_VALIDATE_AGAINST_DTD)) {
                if (!mElementStack.matches(mRootPrefix, mRootLName)) {
                    String actual = (mRootPrefix == null) ? mRootLName
                        : (mRootPrefix + ":" + mRootLName);
                    reportValidationProblem(ErrorConsts.ERR_VLD_WRONG_ROOT, actual, mRootLName);
                }
            }
        }
    }

    /**
     * Method called right before the document root element is handled.
     * The default implementation is empty; validating stream readers
     * should override the method and do whatever initialization is
     * necessary
     */
    protected void initValidation()
        throws XMLStreamException
    {
        ; // nothing to do here
    }

    protected int handleEOF(boolean isProlog)
        throws XMLStreamException
    {
        /* 19-Aug-2006, TSa: mSecondaryToken needs to be initialized to
         *   END_DOCUMENT so we'll know it hasn't been yet accessed.
         */
        mCurrToken = mSecondaryToken = END_DOCUMENT;

        /* Although buffers have most likely already been recycled,
         * let's call this again just in case. At this point we can
         * safely discard any contents
         */
        mTextBuffer.recycle(true); // true -> clean'n recycle
        // It's ok to get EOF from epilog but not from prolog
        if (isProlog) {
            throwUnexpectedEOF(SUFFIX_IN_PROLOG);
        }
        return mCurrToken;
    }

    /**
     * Method called if a root-level element is found after the main
     * root element was closed. This is legal in multi-doc parsing
     * mode (and in fragment mode), but not in the default single-doc
     * mode. 
     * @param c Character passed in (not currently used)
     *
     * @return Token to return
     */
    private int handleExtraRoot(char c)
        throws XMLStreamException
    {
        if (!mConfig.inputParsingModeDocuments()) {
            /* Has to be single-doc mode, since fragment mode
             * should never get here (since fragment mode never has epilog
             * or prolog modes)
             */
            throwParseError("Illegal to have multiple roots (start tag in epilog?).");
        }
        // Need to push back the char, since it is the first char of elem name
        --mInputPtr;
        return handleMultiDocStart(START_ELEMENT);
    }

    /**
     * Method called when an event was encountered that indicates document
     * boundary in multi-doc mode. Needs to trigger dummy
     * END_DOCUMENT/START_DOCUMENT event combination, followed by the
     * handling of the original event.
     *
     * @return Event type to return
     */
    protected int handleMultiDocStart(int nextEvent)
    {
        mParseState = STATE_MULTIDOC_HACK;
        mTokenState = TOKEN_FULL_COALESCED; // this is a virtual event after all...
        mSecondaryToken = nextEvent;
        return END_DOCUMENT;
    }

    /**
     * Method called to get the next event when we are "multi-doc hack" mode,
     * during which extra END_DOCUMENT/START_DOCUMENT events need to be
     * returned.
     */
    private int nextFromMultiDocState()
        throws XMLStreamException
    {
        if (mCurrToken == END_DOCUMENT) {
            /* Ok; this is the initial step; need to advance: need to parse
             * xml declaration if that was the cause, otherwise just clear
             * up values.
             */
            if (mSecondaryToken == START_DOCUMENT) {
                handleMultiDocXmlDecl();
            } else { // Nah, DOCTYPE or start element... just need to clear decl info:
                mDocXmlEncoding = null;
                mDocXmlVersion = XmlConsts.XML_V_UNKNOWN;
                mDocStandalone = DOC_STANDALONE_UNKNOWN;
            }
            return START_DOCUMENT;
        }
        if (mCurrToken == START_DOCUMENT) {
            mParseState = STATE_PROLOG; // yup, we are now officially in prolog again...

            // Had an xml decl (ie. "real" START_DOCUMENT event)
            if (mSecondaryToken == START_DOCUMENT) { // was a real xml decl
                nextFromProlog(true);
                return mCurrToken;
            }
            // Nah, start elem or DOCTYPE
            if (mSecondaryToken == START_ELEMENT) {
                handleRootElem(getNextChar(SUFFIX_IN_ELEMENT));
                return START_ELEMENT;
            }
            if (mSecondaryToken == DTD) {
                mStDoctypeFound = true;
                startDTD();
                return DTD;
            }
        }
        throw new IllegalStateException("Internal error: unexpected state; current event "
                                        +tokenTypeDesc(mCurrToken)+", sec. state: "+tokenTypeDesc(mSecondaryToken));
    }

    protected void handleMultiDocXmlDecl()
        throws XMLStreamException
    {
        // Let's default these first
        mDocStandalone = DOC_STANDALONE_UNKNOWN;
        mDocXmlEncoding = null;

        char c = getNextInCurrAfterWS(SUFFIX_IN_XML_DECL);
        String wrong = checkKeyword(c, XmlConsts.XML_DECL_KW_VERSION);
        if (wrong != null) {
            throwParseError(ErrorConsts.ERR_UNEXP_KEYWORD, wrong, XmlConsts.XML_DECL_KW_VERSION);
        }
        c = skipEquals(XmlConsts.XML_DECL_KW_VERSION, SUFFIX_IN_XML_DECL);
        TextBuffer tb = mTextBuffer;
        tb.resetInitialized();
        parseQuoted(XmlConsts.XML_DECL_KW_VERSION, c, tb);
        
        if (tb.equalsString(XmlConsts.XML_V_10_STR)) {
            mDocXmlVersion = XmlConsts.XML_V_10;
            mXml11 = false;
        } else if (tb.equalsString(XmlConsts.XML_V_11_STR)) {
            mDocXmlVersion = XmlConsts.XML_V_11;
            mXml11 = true;
        } else {
            mDocXmlVersion = XmlConsts.XML_V_UNKNOWN;
            mXml11 = false;
            throwParseError("Unexpected xml version '"+tb.toString()+"'; expected '"+XmlConsts.XML_V_10_STR+"' or '"+XmlConsts.XML_V_11_STR+"'");
        }
        
        c = getNextInCurrAfterWS(SUFFIX_IN_XML_DECL);
        
        if (c != '?') { // '?' signals end...
            if (c == 'e') { // encoding
                wrong = checkKeyword(c, XmlConsts.XML_DECL_KW_ENCODING);
                if (wrong != null) {
                    throwParseError(ErrorConsts.ERR_UNEXP_KEYWORD, wrong, XmlConsts.XML_DECL_KW_ENCODING);
                }
                c = skipEquals(XmlConsts.XML_DECL_KW_ENCODING, SUFFIX_IN_XML_DECL);
                tb.resetWithEmpty();
                parseQuoted(XmlConsts.XML_DECL_KW_ENCODING, c, tb);
                mDocXmlEncoding = tb.toString();
                /* should we verify encoding at this point? let's not, for now;
                 * since it's for information only, first declaration from
                 * bootstrapper is used for the whole stream.
                 */
                c = getNextInCurrAfterWS(SUFFIX_IN_XML_DECL);
            } else if (c != 's') {
                throwUnexpectedChar(c, " in xml declaration; expected either 'encoding' or 'standalone' pseudo-attribute");
            }
            
            // Standalone?
            if (c == 's') {
                wrong = checkKeyword(c, XmlConsts.XML_DECL_KW_STANDALONE);
                if (wrong != null) {
                    throwParseError(ErrorConsts.ERR_UNEXP_KEYWORD, wrong, XmlConsts.XML_DECL_KW_STANDALONE);
                }
                c = skipEquals(XmlConsts.XML_DECL_KW_STANDALONE, SUFFIX_IN_XML_DECL);
                tb.resetWithEmpty();
                parseQuoted(XmlConsts.XML_DECL_KW_STANDALONE, c, tb);
                if (tb.equalsString(XmlConsts.XML_SA_YES)) {
                    mDocStandalone = DOC_STANDALONE_YES;
                } else if (tb.equalsString(XmlConsts.XML_SA_NO)) {
                    mDocStandalone = DOC_STANDALONE_NO;
                } else {
                    throwParseError("Unexpected xml '"+XmlConsts.XML_DECL_KW_STANDALONE+"' pseudo-attribute value '"
                                    +tb.toString()+"'; expected '"+XmlConsts.XML_SA_YES+"' or '"+
                                    XmlConsts.XML_SA_NO+"'");
               }
                c = getNextInCurrAfterWS(SUFFIX_IN_XML_DECL);
            }
        }
        
        if (c != '?') {
            throwUnexpectedChar(c, " in xml declaration; expected '?>' as the end marker");
        }
        c = getNextCharFromCurrent(SUFFIX_IN_XML_DECL);
        if (c != '>') {
            throwUnexpectedChar(c, " in xml declaration; expected '>' to close the declaration");
        }
    }

    /**
     * Method that checks that input following is of form
     * '[S]* '=' [S]*' (as per XML specs, production #25).
     * Will push back non-white space characters as necessary, in
     * case no equals char is encountered.
     */
    protected final char skipEquals(String name, String eofMsg)
        throws XMLStreamException
    {
        char c = getNextInCurrAfterWS(eofMsg);
        if (c != '=') {
            throwUnexpectedChar(c, " in xml declaration; expected '=' to follow pseudo-attribute '"+name+"'");
        }
        // trailing space?
        return getNextInCurrAfterWS(eofMsg);
    }

    /**
     * Method called to parse quoted xml declaration pseudo-attribute values.
     * Works similar to attribute value parsing, except no entities can be
     * included, and in general need not be as picky (since caller is to
     * verify contents). One exception is that we do check for linefeeds
     * and lt chars, since they generally would indicate problems and
     * are useful to catch early on (can happen if a quote is missed etc)
     *<p>
     * Note: since it'll be called at most 3 times per document, this method
     * is not optimized too much.
     */
    protected final void parseQuoted(String name, char quoteChar, TextBuffer tbuf)
        throws XMLStreamException
    {
        if (quoteChar != '"' && quoteChar != '\'') {
            throwUnexpectedChar(quoteChar, " in xml declaration; waited ' or \" to start a value for pseudo-attribute '"+name+"'");
        }
        char[] outBuf = tbuf.getCurrentSegment();
        int outPtr = 0;

        while (true) {
            char c = (mInputPtr < mInputEnd) ? mInputBuffer[mInputPtr++]
                : getNextChar(SUFFIX_IN_XML_DECL);
            
            if (c == quoteChar) {
                break;
            }
            if (c < CHAR_SPACE || c == '<') {
                throwUnexpectedChar(c, SUFFIX_IN_XML_DECL);
            } else if (c == CHAR_NULL) {
                throwNullChar();
            }
            if (outPtr >= outBuf.length) {
                outBuf = tbuf.finishCurrentSegment();
                outPtr = 0;
            }
            outBuf[outPtr++] = c;
        }
        tbuf.setCurrentLength(outPtr);
    }

    /**
     * Called after character sequence '&lt;!' has been found; expectation is
     * that it'll either be DOCTYPE declaration (if we are in prolog and
     * haven't yet seen one), or a comment. CDATA is not legal here;
     * it would start same way otherwise.
     */
    private void nextFromPrologBang(boolean isProlog)
        throws XMLStreamException
    {
        int i = getNext();
        if (i < 0) {
            throwUnexpectedEOF(SUFFIX_IN_PROLOG);
        }
        if (i == 'D') { // Doctype declaration?
            String keyw = checkKeyword('D', "DOCTYPE");
            if (keyw != null) {
                throwParseError("Unrecognized XML directive '<!"+keyw+"' (misspelled DOCTYPE?).");
            }
            
            if (!isProlog) {
                // Still possibly ok in multidoc mode...
                if (mConfig.inputParsingModeDocuments()) {
                    if (!mStDoctypeFound) {
                        mCurrToken = handleMultiDocStart(DTD);
                        return;
                    }
                } else {
                    throwParseError(ErrorConsts.ERR_DTD_IN_EPILOG);
                }
            }
            if (mStDoctypeFound) {
                throwParseError(ErrorConsts.ERR_DTD_DUP);
            }
            mStDoctypeFound = true;
            // Ok; let's read main input (all but internal subset)
            mCurrToken = DTD;
            startDTD();
            return;
        } else if (i == '-') { // comment
            char c = getNextChar(isProlog ? SUFFIX_IN_PROLOG : SUFFIX_IN_EPILOG);
            if (c != '-') {
                throwUnexpectedChar(i, " (malformed comment?)");
            }
            // Likewise, let's delay actual parsing/skipping.
            mTokenState = TOKEN_STARTED;
            mCurrToken = COMMENT;
            return;
        } else if (i == '[') { // erroneous CDATA?
            i = peekNext();
            // Let's just add bit of heuristics, to get better error msg
            if (i == 'C') {
                throwUnexpectedChar(i, ErrorConsts.ERR_CDATA_IN_EPILOG);
            }
        }

        throwUnexpectedChar(i, " after '<!' (malformed comment?)");
    }

    /**
     * Method called to parse through most of DOCTYPE declaration; excluding
     * optional internal subset.
     */
    private void startDTD()
        throws XMLStreamException
    {
        /* 21-Nov-2004, TSa: Let's make sure that the buffer gets cleared
         *   at this point. Need not start branching yet, however, since
         *   DTD event is often skipped.
         */
        mTextBuffer.resetInitialized();

        /* So, what we need is:<code>
         *  <!DOCTYPE' S Name (S ExternalID)? S? ('[' intSubset ']' S?)? '>
         *</code>. And we have already read the DOCTYPE token.
         */

        char c = getNextInCurrAfterWS(SUFFIX_IN_DTD);
        if (mCfgNsEnabled) {
            String str = parseLocalName(c);
            c = getNextChar(SUFFIX_IN_DTD);
            if (c == ':') { // Ok, got namespace and local name
                mRootPrefix = str;
                mRootLName = parseLocalName(getNextChar(SUFFIX_EOF_EXP_NAME));
            } else if (c <= CHAR_SPACE || c == '[' || c == '>') {
                // ok to get white space or '[', or closing '>'
                --mInputPtr; // pushback
                mRootPrefix = null;
                mRootLName = str;
            } else {
                throwUnexpectedChar(c, " in DOCTYPE declaration; expected '[' or white space.");
            }
        } else {
            mRootLName = parseFullName(c);
            mRootPrefix = null;
        }

        // Ok, fine, what next?
        c = getNextInCurrAfterWS(SUFFIX_IN_DTD);
        if (c != '[' && c != '>') {
            String keyw = null;
            
            if (c == 'P') {
                keyw = checkKeyword(getNextChar(SUFFIX_IN_DTD), "UBLIC");
                if (keyw != null) {
                    keyw = "P" + keyw;
                } else {
                    if (!skipWS(getNextChar(SUFFIX_IN_DTD))) {
                        throwUnexpectedChar(c, SUFFIX_IN_DTD+"; expected a space between PUBLIC keyword and public id");
                    }
                    c = getNextCharFromCurrent(SUFFIX_IN_DTD);
                    if (c != '"' && c != '\'') {
                        throwUnexpectedChar(c, SUFFIX_IN_DTD+"; expected a public identifier.");
                    }
                    mDtdPublicId = parsePublicId(c, SUFFIX_IN_DTD);
                    if (mDtdPublicId.length() == 0) {
                        // According to XML specs, this isn't illegal?
                        // however, better report it as empty, not null.
                        //mDtdPublicId = null;
                    }
                    if (!skipWS(getNextChar(SUFFIX_IN_DTD))) {
                        throwUnexpectedChar(c, SUFFIX_IN_DTD+"; expected a space between public and system identifiers");
                    }
                    c = getNextCharFromCurrent(SUFFIX_IN_DTD);
                    if (c != '"' && c != '\'') {
                        throwParseError(SUFFIX_IN_DTD+"; expected a system identifier.");
                    }
                    mDtdSystemId = parseSystemId(c, mNormalizeLFs, SUFFIX_IN_DTD);
                    if (mDtdSystemId.length() == 0) {
                        // According to XML specs, this isn't illegal?
                        // however, better report it as empty, not null.
                        //mDtdSystemId = null;
                    }
                }
            } else if (c == 'S') {
                mDtdPublicId = null;
                keyw = checkKeyword(getNextChar(SUFFIX_IN_DTD), "YSTEM");
                if (keyw != null) {
                    keyw = "S" + keyw;
                } else {
                    c = getNextInCurrAfterWS(SUFFIX_IN_DTD);
                    if (c != '"' && c != '\'') {
                        throwUnexpectedChar(c, SUFFIX_IN_DTD+"; expected a system identifier.");
                    }
                    mDtdSystemId = parseSystemId(c, mNormalizeLFs, SUFFIX_IN_DTD);
                    if (mDtdSystemId.length() == 0) {
                        // According to XML specs, this isn't illegal?
                        mDtdSystemId = null;
                    }
                }
            } else {
                if (!isNameStartChar(c)) {
                    throwUnexpectedChar(c, SUFFIX_IN_DTD+"; expected keywords 'PUBLIC' or 'SYSTEM'.");
                } else {
                    --mInputPtr;
                    keyw = checkKeyword(c, "SYSTEM"); // keyword passed in doesn't matter
                }
            }
            
            if (keyw != null) { // error:
                throwParseError("Unexpected keyword '"+keyw+"'; expected 'PUBLIC' or 'SYSTEM'");
            }
            
            // Ok, should be done with external DTD identifier:
            c = getNextInCurrAfterWS(SUFFIX_IN_DTD);
        }
        
        if (c == '[') { // internal subset
            ;
        } else {
            if (c != '>') {
                throwUnexpectedChar(c, SUFFIX_IN_DTD+"; expected closing '>'.");
            }
        }
        
        /* Actually, let's just push whatever char it is, back; this way
         * we can lazily initialize text buffer with DOCTYPE declaration
         * if/as necessary, even if there's no internal subset.
         */
        --mInputPtr; // pushback
        mTokenState = TOKEN_STARTED;
    }

    /**
     * This method gets called to handle remainder of DOCTYPE declaration,
     * essentially the optional internal subset. This class implements the
     * basic "ignore it" functionality, but can optionally still store copy
     * of the contents to the read buffer.
     *<p>
     * NOTE: Since this default implementation will be overridden by
     * some sub-classes, make sure you do NOT change the method signature.
     *
     * @param copyContents If true, will copy contents of the internal
     *   subset of DOCTYPE declaration
     *   in the text buffer; if false, will just completely ignore the
     *   subset (if one found).
     */
    protected void finishDTD(boolean copyContents)
        throws XMLStreamException
    {
        /* We know there are no spaces, as this char was read and pushed
         * back earlier...
         */
        char c = getNextChar(SUFFIX_IN_DTD);
        if (c == '[') {
            // Do we need to get contents as text too?
            if (copyContents) {
                ((BranchingReaderSource) mInput).startBranch(mTextBuffer, mInputPtr, mNormalizeLFs);
            }

            try {
                MinimalDTDReader.skipInternalSubset(this, mInput, mConfig);
            } finally {
                /* Let's close branching in any and every case (may allow
                 * graceful recovery in error cases in future
                 */
                if (copyContents) {
                    /* Need to "push back" ']' got in the succesful case
                     * (that's -1 part below);
                     * in error case it'll just be whatever last char was.
                     */
                    ((BranchingReaderSource) mInput).endBranch(mInputPtr-1);
                }
            }

            // And then we need closing '>'
            c = getNextCharAfterWS(SUFFIX_IN_DTD_INTERNAL);
        }

        if (c != '>') {
            throwUnexpectedChar(c, "; expected '>' to finish DOCTYPE declaration.");
        }
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Internal methods, main parsing (inside root)
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Method called to parse beginning of the next event within
     * document tree, and return its type.
     */
    private final int nextFromTree()
        throws XMLStreamException
    {
        int i;

        // First, do we need to finish currently open token?
        if (mTokenState < mStTextThreshold) {
            // No need to update state... will get taken care of
            /* 03-Mar-2006, TSa: Let's add a sanity check here, temporarily,
             *   to ensure we never skip any textual content when it is
             *   to be validated
             */
            if (mVldContent == XMLValidator.CONTENT_ALLOW_VALIDATABLE_TEXT) {
                if (mCurrToken == CHARACTERS || mCurrToken == CDATA) { // should never happen
                    throwParseError("Internal error: skipping validatable text");
                }
            }
            i = skipToken();
            // note: skipToken() updates the start location
        } else {
            // Start/end elements are never unfinished (ie. are always
            // completely read in)
            if (mCurrToken == START_ELEMENT) {
                // Start tag may be an empty tag:
                if (mStEmptyElem) {
                    // and if so, we'll then get 'virtual' close tag:
                    mStEmptyElem = false;
                    // ... and location info is correct already
                    // 27-Feb-2009, TSa: but we do have to handle validation of the end tag now
                    int vld = mElementStack.validateEndElement();
                    mVldContent = vld;
                    mValidateText = (vld == XMLValidator.CONTENT_ALLOW_VALIDATABLE_TEXT);
                    return END_ELEMENT;
                }
            } else if (mCurrToken == END_ELEMENT) {
                // Close tag removes current element from stack
                if (!mElementStack.pop()) { // false if root closed
                    // if so, we'll get to epilog, unless in fragment mode
                    if (!mConfig.inputParsingModeFragment()) {
                        return closeContentTree();
                    }
                    // in fragment mode, fine, we'll just continue
                }
            } else if (mCurrToken == CDATA && mTokenState <= TOKEN_PARTIAL_SINGLE) {
                /* Just returned a partial CDATA... that's ok, just need to
                 * know we won't get opening marker etc.
                 * The tricky part here is just to ensure there's at least
                 * one character; if not, need to just discard the empty
                 * 'event' (note that it is possible to have an initial
                 * empty CDATA event for truly empty CDATA block; but not
                 * partial ones!). Let's just read it like a new
                 * CData section first:
                 */
                // First, need to update the start location...
                mTokenInputTotal = mCurrInputProcessed + mInputPtr;
                mTokenInputRow = mCurrInputRow;
                mTokenInputCol = mInputPtr - mCurrInputRowStart;
                char c = (mInputPtr < mInputEnd) ? mInputBuffer[mInputPtr++]
                    : getNextChar(SUFFIX_IN_CDATA);
                if (readCDataPrimary(c)) { // got it all!
                    // note: can not be in coalescing mode at this point;
                    // as we can never have partial cdata without unfinished token
                    // ... still need to have gotten at least 1 char though:
                    if (mTextBuffer.size() > 0) {
                        return CDATA;
                    }
                    // otherwise need to continue and parse the next event
                } else {
                    // Hmmh. Have to verify we get at least one char from
                    // CData section; if so, we are good to go for now;
                    // if not, need to get that damn char first:
                    if (mTextBuffer.size() == 0
                            && readCDataSecondary(mCfgLazyParsing
                                    ? 1 : mShortestTextSegment)) {
                        // Ok, all of it read
                        if (mTextBuffer.size() > 0) {
                            // And had some contents
                            mTokenState = TOKEN_FULL_SINGLE;
                            return CDATA;
                        }
                        // if nothing read, we'll just fall back (see below)
                    } else { // good enough!
                        mTokenState = TOKEN_PARTIAL_SINGLE;
                        return CDATA;
                    }
                }
                
                /* If we get here, it was the end of the section, without
                 * any more text inside CDATA, so let's just continue
                 */
            }
            // Once again, need to update the start location info:
            mTokenInputTotal = mCurrInputProcessed + mInputPtr;
            mTokenInputRow = mCurrInputRow;
            mTokenInputCol = mInputPtr - mCurrInputRowStart;
            i = getNext();
        }

        if (i < 0) {
            // 07-Oct-2005, TSa: May be ok in fragment mode (not otherwise),
            //  but we can just check if element stack has anything, as that handles all cases
            if (!mElementStack.isEmpty()) {
                throwUnexpectedEOF();
            }
            return handleEOF(false);
        }

        /* 26-Aug-2004, TSa: We have to deal with entities, usually, if
         *   they are the next thing; even in non-expanding mode there
         *   are entities and then there are entities... :-)
         *   Let's start with char entities; they can be expanded right away.
         */
        while (i == '&') {
            mWsStatus = ALL_WS_UNKNOWN;

            /* 30-Aug-2004, TSa: In some contexts entities are not
             *    allowed in any way, shape or form:
             */
            if (mVldContent == XMLValidator.CONTENT_ALLOW_NONE) {
                /* May be char entity, general entity; whatever it is it's
                 * invalid!
                 */
                reportInvalidContent(ENTITY_REFERENCE);
            }

            /* Need to call different methods based on whether we can do
             * automatic entity expansion or not:
             */
            int ch = mCfgReplaceEntities ?
                fullyResolveEntity(true) : resolveCharOnlyEntity(true);

            if (ch != 0) {
                /* Char-entity... need to initialize text output buffer, then;
                 * independent of whether it'll be needed or not.
                 */
                /* 30-Aug-2004, TSa: In some contexts only white space is
                 *   accepted...
                 */
                if (mVldContent <= XMLValidator.CONTENT_ALLOW_WS) {
                    // As per xml specs, only straight white space is legal
                    if (ch > CHAR_SPACE) {
                        /* 21-Sep-2008, TSa: Used to also require a call to
                         *   'mElementStack.reallyValidating', if only ws
                         *   allowed, to cover the case where non-typing-dtd
                         *   was only used to discover SPACE type. But
                         *   now that we have CONTENT_ALLOW_WS_NONSTRICT,
                         *   shouldn't be needed.
                         */
                        //if (mVldContent < XMLValidator.CONTENT_ALLOW_WS || mElementStack.reallyValidating()) {
                        reportInvalidContent(CHARACTERS);
                    }
                }
                TextBuffer tb = mTextBuffer;
                tb.resetInitialized();
                if (ch <= 0xFFFF) {
                    tb.append((char) ch);
                } else {
                    ch -= 0x10000;
                    tb.append((char) ((ch >> 10)  + 0xD800));
                    tb.append((char) ((ch & 0x3FF)  + 0xDC00));
                }
                mTokenState = TOKEN_STARTED;
                return CHARACTERS;
            }

            /* Nope; was a general entity... in auto-mode, it's now been
             * expanded; in non-auto, need to figure out entity itself.
             */
            if (!mCfgReplaceEntities|| mCfgTreatCharRefsAsEntities) {
                if (!mCfgTreatCharRefsAsEntities) {
                    final EntityDecl ed = resolveNonCharEntity();
                    // Note: ed may still be null at this point
                    mCurrEntity = ed;
                }
                // Note: ed may still be null at this point
                mTokenState = TOKEN_FULL_COALESCED;
                /*
                // let's not worry about non-parsed entities, since this is unexpanded mode
                // ... although it'd be an error either way? Should we report it?
                if (ed != null && !ed.isParsed()) {
                    throwParseError("Reference to unparsed entity '"+ed.getName()+"' from content not allowed.");
                }
                */
                return ENTITY_REFERENCE;
            }

            // Otherwise automatic expansion fine; just need the next char:
            i = getNextChar(SUFFIX_IN_DOC);
        }

        if (i == '<') { // Markup
            // And then it should be easy to figure out type:
            char c = getNextChar(SUFFIX_IN_ELEMENT);
            if (c == '?') { // proc. inst
                // 30-Aug-2004, TSa: Not legal for EMPTY elements
                if (mVldContent == XMLValidator.CONTENT_ALLOW_NONE) {
                    reportInvalidContent(PROCESSING_INSTRUCTION);
                }
                return readPIPrimary();
            }
            
            if (c == '!') { // CDATA or comment
                // Need to figure out bit more first...
                int type = nextFromTreeCommentOrCData();
                // 30-Aug-2004, TSa: Not legal for EMPTY elements
                if (mVldContent == XMLValidator.CONTENT_ALLOW_NONE) {
                    reportInvalidContent(type);
                }
                return type;
            }
            if (c == '/') { // always legal (if name matches etc)
                readEndElem();
                return END_ELEMENT;
            }

            if (c == ':' || isNameStartChar(c)) {
                /* Note: checking for EMPTY content type is done by the
                 * validator, no need to check here
                 */
                handleStartElem(c);
                return START_ELEMENT;
            }
            if (c == '[') {
                throwUnexpectedChar(c, " in content after '<' (malformed <![CDATA[]] directive?)");
            }
            throwUnexpectedChar(c, " in content after '<' (malformed start element?).");
        }

        /* Text... ok; better parse the 'easy' (consequtive) portions right
         * away, since that's practically free (still need to scan those
         * characters no matter what, even if skipping).
         */
        /* But first, do we expect to get ignorable white space (only happens
         * in validating mode)? If so, needs bit different handling:
         */
        if (mVldContent <= XMLValidator.CONTENT_ALLOW_WS_NONSTRICT) {
            if (mVldContent == XMLValidator.CONTENT_ALLOW_NONE) {
                if (mElementStack.reallyValidating()) {
                    reportInvalidContent(CHARACTERS);
                }
            }
            if (i <= CHAR_SPACE) {
                /* Note: need not worry about coalescing, since non-whitespace
                 * text is illegal (ie. can not have CDATA)
                 */
                mTokenState = (readSpacePrimary((char) i, false)) ?
                    TOKEN_FULL_COALESCED : TOKEN_STARTED;
                return SPACE;
            }
            // Problem if we are really validating; otherwise not
            if (mElementStack.reallyValidating()) {
                reportInvalidContent(CHARACTERS);
            }
            /* otherwise, we know it's supposed to contain just space (or
             * be empty), but as we are not validating it's not an error
             * for this not to be true. Type should be changed to
             * CHARACTERS tho.
             */
        }

        // Further, when coalescing, can not be sure if we REALLY got it all
        if (readTextPrimary((char) i)) { // reached following markup
            mTokenState = TOKEN_FULL_SINGLE;
        } else {
            // If not coalescing, this may be enough for current event
            if (!mCfgCoalesceText
                && mTextBuffer.size() >= mShortestTextSegment) {
                mTokenState = TOKEN_PARTIAL_SINGLE;
            } else {
                mTokenState = TOKEN_STARTED;
            }
        }
        return CHARACTERS;
    }

    /**
     * Method called when advacing stream past the end tag that closes
     * the root element of the open document.
     * Document can be either the singular one, in regular mode, or one of
     * possibly multiple, in multi-doc mode: this method is never called
     * in fragment mode. Method needs to update state properly and
     * parse following epilog event (if any).
     *
     * @return Event following end tag of the root elemennt, if any;
     *   END_DOCUMENT otherwis.e
     */
    private int closeContentTree()
        throws XMLStreamException
    {
        mParseState = STATE_EPILOG;
        // this call will update the location too...
        if (nextFromProlog(false)) {
            mSecondaryToken = 0;
        }
        /* 10-Apr-2006, TSa: Let's actually try to update
         *   SymbolTable here (after main xml tree); caller
         *   may not continue parsing after this.
         */
        if (mSymbols.isDirty()) {
            mOwner.updateSymbolTable(mSymbols);
        }
        /* May be able to recycle, but not certain; and
         * definitely can not just clean contents (may
         * contain space(s) read)
         */
        mTextBuffer.recycle(false);
        return mCurrToken;
    }

    /**
     * Method that takes care of parsing of start elements; including
     * full parsing of namespace declarations and attributes, as well as
     * namespace resolution.
     */
    private final void handleStartElem(char c)
        throws XMLStreamException
    {
        mTokenState = TOKEN_FULL_COALESCED;
        boolean empty;

        if (mCfgNsEnabled) {
            String str = parseLocalName(c);
            c = (mInputPtr < mInputEnd) ?
                mInputBuffer[mInputPtr++] : getNextCharFromCurrent(SUFFIX_EOF_EXP_NAME);
            if (c == ':') { // Ok, got namespace and local name
                c = (mInputPtr < mInputEnd) ?
                    mInputBuffer[mInputPtr++] : getNextCharFromCurrent(SUFFIX_EOF_EXP_NAME);
                mElementStack.push(str, parseLocalName(c));
                c = (mInputPtr < mInputEnd) ?
                    mInputBuffer[mInputPtr++] : getNextCharFromCurrent(SUFFIX_IN_ELEMENT);
            } else {
                mElementStack.push(null, str);
                // c is fine as
            }
            /* Enough about element name itself; let's then parse attributes
             * and namespace declarations. Split into another method for clarity,
             * and so that maybe JIT has easier time to optimize it separately.
             */
             /* 04-Jul-2005, TSa: But hold up: we can easily check for a fairly
              *   common case of no attributes showing up, and us getting the
              *   closing '>' right away. Let's do that, since it can save
              *   a call to a rather long method.
              */
            empty = (c == '>') ? false : handleNsAttrs(c);
        } else { // Namespace handling not enabled:
            mElementStack.push(null, parseFullName(c));
            c = (mInputPtr < mInputEnd) ?
                mInputBuffer[mInputPtr++] : getNextCharFromCurrent(SUFFIX_IN_ELEMENT);
            empty = (c == '>') ? false : handleNonNsAttrs(c);
        }
        if (!empty) {
            ++mCurrDepth; // needed to match nesting with entity expansion
        }
        mStEmptyElem = empty;

        /* 27-Feb-2009, TSa: [WSTX-191]: We used to validate virtual
         *   end element here for empty elements, but it really should
         *   occur later on when actually returning that end element.
         */
        int vld = mElementStack.resolveAndValidateElement();
        mVldContent = vld;
        mValidateText = (vld == XMLValidator.CONTENT_ALLOW_VALIDATABLE_TEXT);
    }

    /**
     * @return True if this is an empty element; false if not
     */
    private final boolean handleNsAttrs(char c)
        throws XMLStreamException
    {
        AttributeCollector ac = mAttrCollector;

        while (true) {
            if (c <= CHAR_SPACE) {
                c = getNextInCurrAfterWS(SUFFIX_IN_ELEMENT, c);
            } else if (c != '/' && c != '>') {
                throwUnexpectedChar(c, " excepted space, or '>' or \"/>\"");
            }

            if (c == '/') {
                c = getNextCharFromCurrent(SUFFIX_IN_ELEMENT);
                if (c != '>') {
                    throwUnexpectedChar(c, " expected '>'");
                }
                return true;
            } else if (c == '>') {
                return false;
            } else if (c == '<') {
                throwParseError("Unexpected '<' character in element (missing closing '>'?)");
            }

            String prefix, localName;
            String str = parseLocalName(c);
            c = (mInputPtr < mInputEnd) ?
                mInputBuffer[mInputPtr++] : getNextCharFromCurrent(SUFFIX_EOF_EXP_NAME);
            if (c == ':') { // Ok, got namespace and local name
                prefix = str;
                c = (mInputPtr < mInputEnd) ?
                    mInputBuffer[mInputPtr++] : getNextCharFromCurrent(SUFFIX_EOF_EXP_NAME);
                localName = parseLocalName(c);
            } else {
                --mInputPtr; // pushback
                prefix = null;
                localName = str;
            }

            c = (mInputPtr < mInputEnd) ?
                mInputBuffer[mInputPtr++] : getNextCharFromCurrent(SUFFIX_IN_ELEMENT);
            if (c <= CHAR_SPACE) {
                c = getNextInCurrAfterWS(SUFFIX_IN_ELEMENT, c);
            }
            if (c != '=') {
                throwUnexpectedChar(c, " expected '='");
            }
            c = (mInputPtr < mInputEnd) ?
                mInputBuffer[mInputPtr++] : getNextCharFromCurrent(SUFFIX_IN_ELEMENT);
            if (c <= CHAR_SPACE) {
                c = getNextInCurrAfterWS(SUFFIX_IN_ELEMENT, c);
            }

            // And then a quote:
            if (c != '"' && c != '\'') {
                throwUnexpectedChar(c, SUFFIX_IN_ELEMENT+" Expected a quote");
            }

            // And then the actual value
            int startLen = -1;
            TextBuilder tb;

            if (prefix == sPrefixXmlns) { // non-default namespace declaration
                tb = ac.getNsBuilder(localName);
                // returns null if it's a dupe:
                if (null == tb) {
                    throwParseError("Duplicate declaration for namespace prefix '"+localName+"'.");
                }
                startLen = tb.getCharSize();
            } else if (localName == sPrefixXmlns && prefix == null) {
                tb = ac.getDefaultNsBuilder();
                // returns null if default ns was already declared
                if (null == tb) {
                    throwParseError("Duplicate default namespace declaration.");
                }
            } else {
                tb = ac.getAttrBuilder(prefix, localName);
            }
            parseAttrValue(c, tb);

            /* 19-Jul-2004, TSa: Need to check that non-default namespace
             *     URI is NOT empty, as per XML namespace specs, #2,
             *    ("...In such declarations, the namespace name may not
             *      be empty.")
             */
            /* (note: startLen is only set to first char position for
             * non-default NS declarations, see above...)
             */
            /* 04-Feb-2005, TSa: Namespaces 1.1 does allow this, though,
             *   so for xml 1.1 documents we need to allow it
             */
            if (!mXml11) {
                if (startLen >= 0 && tb.getCharSize() == startLen) { // is empty!
                    throwParseError(ErrorConsts.ERR_NS_EMPTY);
                }
            }

            // and then we need to iterate some more
            c = (mInputPtr < mInputEnd) ?
                mInputBuffer[mInputPtr++] : getNextCharFromCurrent(SUFFIX_IN_ELEMENT);
        }
        // never gets here
    }

    /**
     * @return True if this is an empty element; false if not
     */
    private final boolean handleNonNsAttrs(char c)
        throws XMLStreamException
    {
        AttributeCollector ac = mAttrCollector;

        while (true) {
            if (c <= CHAR_SPACE) {
                c = getNextInCurrAfterWS(SUFFIX_IN_ELEMENT, c);
            } else if (c != '/' && c != '>') {
                throwUnexpectedChar(c, " excepted space, or '>' or \"/>\"");
            }
            if (c == '/') {
                c = getNextCharFromCurrent(SUFFIX_IN_ELEMENT);
                if (c != '>') {
                    throwUnexpectedChar(c, " expected '>'");
                }
                return true;
            } else if (c == '>') {
                return false;
            } else if (c == '<') {
                throwParseError("Unexpected '<' character in element (missing closing '>'?)");
            }

            String name = parseFullName(c);
            TextBuilder tb = ac.getAttrBuilder(null, name);
            c = (mInputPtr < mInputEnd) ?
                mInputBuffer[mInputPtr++] : getNextCharFromCurrent(SUFFIX_IN_ELEMENT);
            if (c <= CHAR_SPACE) {
                c = getNextInCurrAfterWS(SUFFIX_IN_ELEMENT, c);
            }
            if (c != '=') {
                throwUnexpectedChar(c, " expected '='");
            }
            c = (mInputPtr < mInputEnd) ?
                mInputBuffer[mInputPtr++] : getNextCharFromCurrent(SUFFIX_IN_ELEMENT);
            if (c <= CHAR_SPACE) {
                c = getNextInCurrAfterWS(SUFFIX_IN_ELEMENT, c);
            }

            // And then a quote:
            if (c != '"' && c != '\'') {
                throwUnexpectedChar(c, SUFFIX_IN_ELEMENT+" Expected a quote");
            }

            // And then the actual value
            parseAttrValue(c, tb);
            // and then we need to iterate some more
            c = (mInputPtr < mInputEnd) ?
                mInputBuffer[mInputPtr++] : getNextCharFromCurrent(SUFFIX_IN_ELEMENT);
        }
        // never gets here
    }

    /**
     * Method called to completely read a close tag, and update element
     * stack appropriately (including checking that tag matches etc).
     */
    protected final void readEndElem()
        throws XMLStreamException
    {
        mTokenState = TOKEN_FULL_COALESCED; // will be read completely

        if (mElementStack.isEmpty()) {
            // Let's just offline this for clarity
            reportExtraEndElem();
            return; // never gets here
        }

        char c = (mInputPtr < mInputEnd) ? mInputBuffer[mInputPtr++]
            : getNextCharFromCurrent(SUFFIX_IN_CLOSE_ELEMENT);
        // Quick check first; missing name?
        if  (!isNameStartChar(c) && c != ':') {
            if (c <= CHAR_SPACE) { // space
                throwUnexpectedChar(c, "; missing element name?");
            }
            throwUnexpectedChar(c, "; expected an element name.");
        }

        /* Ok, now; good thing is we know exactly what to compare
         * against...
         */
        String expPrefix = mElementStack.getPrefix();
        String expLocalName = mElementStack.getLocalName();

        // Prefix to match?
        if (expPrefix != null && expPrefix.length() > 0) {
            int len = expPrefix.length();
            int i = 0;

            while (true){
                if (c != expPrefix.charAt(i)) {
                    reportWrongEndPrefix(expPrefix, expLocalName, i);
                    return; // never gets here
                }
                if (++i >= len) {
                    break;
                }
                c = (mInputPtr < mInputEnd) ? mInputBuffer[mInputPtr++]
                    : getNextCharFromCurrent(SUFFIX_IN_CLOSE_ELEMENT);
            }
            // And then we should get a colon
            c = (mInputPtr < mInputEnd) ? mInputBuffer[mInputPtr++]
                : getNextCharFromCurrent(SUFFIX_IN_CLOSE_ELEMENT);
            if (c != ':') {
                reportWrongEndPrefix(expPrefix, expLocalName, i);
                return;
            }
            c = (mInputPtr < mInputEnd) ? mInputBuffer[mInputPtr++]
                : getNextCharFromCurrent(SUFFIX_IN_CLOSE_ELEMENT);
        }

        // Ok, then, does the local name match?
        int len = expLocalName.length();
        int i = 0;
        
        while (true){
            if (c != expLocalName.charAt(i)) {
                // Not a match...
                reportWrongEndElem(expPrefix, expLocalName, i);
                return; // never gets here
            }
            if (++i >= len) {
                break;
            }
            c = (mInputPtr < mInputEnd) ? mInputBuffer[mInputPtr++]
                : getNextCharFromCurrent(SUFFIX_IN_CLOSE_ELEMENT);
        }

        // Let's see if end element still continues, however?
        c = (mInputPtr < mInputEnd) ? mInputBuffer[mInputPtr++]
            : getNextCharFromCurrent(SUFFIX_IN_CLOSE_ELEMENT);
        if (c <= CHAR_SPACE) {
            c = getNextInCurrAfterWS(SUFFIX_IN_CLOSE_ELEMENT, c);
        } else if (c == '>') {
            ;
        } else if (c == ':' || isNameChar(c)) {
            reportWrongEndElem(expPrefix, expLocalName, len);
        }

        // Ok, fine, match ok; now we just need the closing gt char.
        if (c != '>') {
            throwUnexpectedChar(c, SUFFIX_IN_CLOSE_ELEMENT+" Expected '>'.");
        }

        // Finally, let's let validator detect if things are ok
        int vld = mElementStack.validateEndElement();
        mVldContent = vld;
        mValidateText = (vld == XMLValidator.CONTENT_ALLOW_VALIDATABLE_TEXT);

        // Plus verify WFC that start and end tags came from same entity
        /* 13-Feb-2006, TSa: Are we about to close an element that
         *    started within a parent element?
         *    That's a GE/element nesting WFC violation...
         */
        if (mCurrDepth == mInputTopDepth) {
            handleGreedyEntityProblem(mInput);
        }
        --mCurrDepth;
    }

    private void reportExtraEndElem()
        throws XMLStreamException
    {
        String name = parseFNameForError();
        throwParseError("Unbalanced close tag </"+name+">; no open start tag.");
    }

    private void reportWrongEndPrefix(String prefix, String localName, int done)
        throws XMLStreamException
    {
        --mInputPtr; // pushback
        String fullName = prefix + ":" + localName;
        String rest = parseFNameForError();
        String actName = fullName.substring(0, done) + rest;
        throwParseError("Unexpected close tag </"+actName+">; expected </"
                        +fullName+">.");
    }

    private void reportWrongEndElem(String prefix, String localName, int done)
        throws XMLStreamException
    {
        --mInputPtr; // pushback
        String fullName;
        if (prefix != null && prefix.length() > 0) {
            fullName = prefix + ":" + localName;
            done += 1 + prefix.length();
        } else {
            fullName = localName;
        }
        String rest = parseFNameForError();
        String actName = fullName.substring(0, done) + rest;
        throwParseError("Unexpected close tag </"+actName+">; expected </"
                        +fullName+">.");
    }

    /**
     *<p>
     * Note: According to StAX 1.0, coalesced text events are always to be
     * returned as CHARACTERS, never as CDATA. And since at this point we
     * don't really know if there's anything to coalesce (but there may
     * be), let's convert CDATA if necessary.
     */
    private int nextFromTreeCommentOrCData()
        throws XMLStreamException
    {
        char c = getNextCharFromCurrent(SUFFIX_IN_DOC);
        if (c == '[') {
            checkCData();
            /* Good enough; it is a CDATA section... but let's just also
             * parse the easy ("free") stuff:
             */
            c = (mInputPtr < mInputEnd) ? mInputBuffer[mInputPtr++]
                : getNextCharFromCurrent(SUFFIX_IN_CDATA);
            readCDataPrimary(c); // sets token state appropriately...
            return CDATA;
        }
        if (c == '-' && getNextCharFromCurrent(SUFFIX_IN_DOC) == '-') {
            mTokenState = TOKEN_STARTED;
            return COMMENT;
        }
        throwParseError("Unrecognized XML directive; expected CDATA or comment ('<![CDATA[' or '<!--').");
        return 0; // never gets here, but compilers don't know it...
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Internal methods, skipping
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * Method called to skip last part of current token, when full token
     * has not been parsed. Generally happens when caller is not interested
     * in current token and just calls next() to iterate to next token.
     *<p>
     * Note: this method is to accurately update the location information
     * to reflect where the next event will start (or, in case of EOF, where
     * EOF was encountered, ie. where event would start, if there was one).
     *
     * @return Next character after node has been skipped, or -1 if EOF
     *    follows
     */
    private int skipToken()
        throws XMLStreamException
    {
        int result;

        main_switch:
        switch (mCurrToken) {
        case CDATA:
            {
                /* 30-Aug-2004, TSa: Need to be careful here: we may
                 *    actually have finished with CDATA, but are just
                 *    coalescing... if so, need to skip first part of
                 *    skipping
                 */
                if (mTokenState <= TOKEN_PARTIAL_SINGLE) {
                    // Skipping CDATA is easy; just need to spot closing ]]&gt;
                    skipCommentOrCData(SUFFIX_IN_CDATA, ']', false);
                }
                result = getNext();
                // ... except if coalescing, may need to skip more:
                if (mCfgCoalesceText) {
                    result = skipCoalescedText(result);
                }
            }
            break;
                
        case COMMENT:
            skipCommentOrCData(SUFFIX_IN_COMMENT, '-', true);
            result = 0;
            break;

        case CHARACTERS:
            {
                result = skipTokenText(getNext());
                // ... except if coalescing, need to skip more:
                if (mCfgCoalesceText) {
                    result = skipCoalescedText(result);
                }
            }
            break;

        case DTD:
            finishDTD(false);
            result = 0;
            break;

        case PROCESSING_INSTRUCTION:
            while (true) {
                char c = (mInputPtr < mInputEnd)
                    ? mInputBuffer[mInputPtr++] : getNextCharFromCurrent(SUFFIX_IN_PROC_INSTR);
                if (c == '?') {
                    do {
                        c = (mInputPtr < mInputEnd)
                            ? mInputBuffer[mInputPtr++] : getNextCharFromCurrent(SUFFIX_IN_PROC_INSTR);
                    } while (c == '?');
                    if (c == '>') {
                        result = 0;
                        break main_switch;
                    }
                }
                if (c < CHAR_SPACE) {
                    if (c == '\n' || c == '\r') {
                        skipCRLF(c);
                    } else if (c != '\t') {
                        throwInvalidSpace(c);
                    }
                }
            }
            // never gets in here

        case SPACE:

            while (true) {
                // Fairly easy to skip through white space...
                while (mInputPtr < mInputEnd) {
                    char c = mInputBuffer[mInputPtr++];
                    if (c > CHAR_SPACE) { // non-EOF non-WS?
                        result = c;
                        break main_switch;
                    }
                    if (c == '\n' || c == '\r') {
                        skipCRLF(c);
                    } else if (c != CHAR_SPACE && c != '\t') {
                        throwInvalidSpace(c);
                    }
                }
                if (!loadMore()) {
                    result = -1;
                    break main_switch;
                }
            }
            // never gets in here

        case ENTITY_REFERENCE: // these should never end up in here...
        case ENTITY_DECLARATION:
        case NOTATION_DECLARATION:
        case START_DOCUMENT:
        case END_DOCUMENT:
            // As are start/end document
            throw new IllegalStateException("skipToken() called when current token is "+tokenTypeDesc(mCurrToken));

        case ATTRIBUTE:
        case NAMESPACE:
            // These two are never returned by this class
        case START_ELEMENT:
        case END_ELEMENT:
            /* Never called for elements tokens; start token handled
             * differently, end token always completely read in the first place
             */

        default:
            throw new IllegalStateException("Internal error: unexpected token "+tokenTypeDesc(mCurrToken));

        }

        /* Ok; now we have 3 possibilities; result is:
         *
         * + 0 -> could reliably read the prev event, now need the
         *   following char/EOF
         * + -1 -> hit EOF; can return it
         * + something else -> this is the next char, return it.
         *
         * In first 2 cases, next event start offset is the current location;
         * in third case, it needs to be backtracked by one char
         */
        if (result < 1) {
            mTokenInputRow = mCurrInputRow;
            mTokenInputTotal = mCurrInputProcessed + mInputPtr;
            mTokenInputCol = mInputPtr - mCurrInputRowStart;
            return (result < 0) ? result : getNext();
        }

        // Ok, need to offset location, and return whatever we got:
        mTokenInputRow = mCurrInputRow;
        mTokenInputTotal = mCurrInputProcessed + mInputPtr - 1;
        mTokenInputCol = mInputPtr - mCurrInputRowStart - 1;
        return result;
    }

    private void skipCommentOrCData(String errorMsg, char endChar, boolean preventDoubles)
        throws XMLStreamException
    {
        /* Let's skip all chars except for double-ending chars in
         * question (hyphen for comments, right brack for cdata)
         */
        int count = 0;
        while (true) {
            char c;
            while (true) {
                if (mInputPtr >= mInputEnd) {
                    verifyLimit("Text size", mConfig.getMaxTextLength(), count);
                    c = getNextCharFromCurrent(errorMsg);
                } else {
                    c =  mInputBuffer[mInputPtr++];
                }
                if (c < CHAR_SPACE) {
                    if (c == '\n' || c == '\r') {
                        skipCRLF(c);
                    } else if (c != '\t') {
                        throwInvalidSpace(c);
                    }
                } else if (c == endChar) {
                    break;
                }
                ++count;
            }

            // Now, we may be getting end mark; first need second marker char:.
            c = getNextChar(errorMsg);
            if (c == endChar) { // Probably?
                // Now; we should be getting a '>', most likely.
                c = getNextChar(errorMsg);
                if (c == '>') {
                    break;
                }
                if (preventDoubles) { // if not, it may be a problem...
                    throwParseError("String '--' not allowed in comment (missing '>'?)");
                }
                // Otherwise, let's loop to see if there is end
                while (c == endChar) {
                    c = (mInputPtr < mInputEnd)
                        ? mInputBuffer[mInputPtr++] : getNextCharFromCurrent(errorMsg);
                }
                if (c == '>') {
                    break;
                }
            }

            // No match, did we get a linefeed?
            if (c < CHAR_SPACE) {
                if (c == '\n' || c == '\r') {
                    skipCRLF(c);
                } else if (c != '\t') {
                    throwInvalidSpace(c);
                }
            }
            // Let's continue from beginning, then
        }
    }

    /**
     * Method called to skip past all following text and CDATA segments,
     * until encountering something else (including a general entity,
     * which may in turn expand to text).
     *
     * @return Character following all the skipped text and CDATA segments,
     *   if any; or -1 to denote EOF
     */
    private int skipCoalescedText(int i)
        throws XMLStreamException
    {
        while (true) {
            // Ok, plain text or markup?
            if (i == '<') { // markup, maybe CDATA?
                // Need to distinguish "<![" from other tags/directives
                if (!ensureInput(3)) {
                    /* Most likely an error condition, but let's leave
                     * it up for other parts of code to complain.
                     */
                    return i;
                }
                if (mInputBuffer[mInputPtr] != '!'
                    || mInputBuffer[mInputPtr+1] != '[') {
                    // Nah, some other tag or directive
                    return i;
                }
                // Let's skip beginning parts, then:
                mInputPtr += 2;
                // And verify we get proper CDATA directive
                checkCData();
                skipCommentOrCData(SUFFIX_IN_CDATA, ']', false);
                i = getNext();
            } else if (i < 0) { // eof
                return i;
            } else { // nah, normal text, gotta skip
                i = skipTokenText(i);
                /* Did we hit an unexpandable entity? If so, need to
                 * return ampersand to the caller...
                 * (and same for EOF too)
                 */
                if (i == '&' || i < 0) {
                    return i;
                }
            }
        }
    }

    private int skipTokenText(int i)
        throws XMLStreamException
    {
        /* Fairly easy; except for potential to have entities
         * expand to some crap?
         */
        int count = 0;
        
        main_loop:
        while (true) {
            if (i == '<') {
                return i;
            }
            if (i == '&') {
                // Can entities be resolved automatically?
                if (mCfgReplaceEntities) {
                    // Let's first try quick resolution:
                    if ((mInputEnd - mInputPtr) >= 3
                        && resolveSimpleEntity(true) != 0) {
                        ;
                    } else {
                        i = fullyResolveEntity(true);
                        /* Either way, it's just fine; we don't care about
                         * returned single-char value.
                         */
                    }
                } else {
                    /* Can only skip character entities; others need to
                     * be returned separately.
                     */
                    if (resolveCharOnlyEntity(true) == 0) {
                        /* Now points to the char after ampersand, and we need
                         * to return the ampersand itself
                         */
                        return i;
                    }
                }
            } else if (i < CHAR_SPACE) {
                if (i == '\r' || i == '\n') {
                    skipCRLF((char) i);
                } else if (i < 0) { // EOF
                    return i;
                } else if (i != '\t') {
                    throwInvalidSpace(i);
                }

            }
            ++count;
            verifyLimit("Text size", mConfig.getMaxTextLength(), count);

            // Hmmh... let's do quick looping here:
            while (mInputPtr < mInputEnd) {
                char c = mInputBuffer[mInputPtr++];
                if (c < CHAR_FIRST_PURE_TEXT) { // need to check it
                    i = c;
                    continue main_loop;
                }
            }

            i = getNext();
        }
        // never gets here...
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Internal methods, parsing
    ///////////////////////////////////////////////////////////////////////
     */

    protected void ensureFinishToken() throws XMLStreamException
    {
        if (mTokenState < mStTextThreshold) {
            finishToken(false);
        }
    }

    protected void safeEnsureFinishToken()
    {
        if (mTokenState < mStTextThreshold) {
            safeFinishToken();
        }
    }

    protected void safeFinishToken()
    {
        try {
            /* 24-Sep-2006, TSa: Let's try to reduce number of unchecked
             *   (wrapped) exceptions we throw, and defer some. For now,
             *   this is only for CHARACTERS (since it's always legal to
             *   split CHARACTERS segment); could be expanded in future.
             */
            boolean deferErrors = (mCurrToken == CHARACTERS);
            finishToken(deferErrors);
        } catch (XMLStreamException strex) {
            throwLazyError(strex);
        }
    }

    /**
     * Method called to read in contents of the token completely, if not
     * yet read. Generally called when caller needs to access anything
     * other than basic token type (except for elements), text contents
     * or such.
     *
     * @param deferErrors Flag to enable storing an exception to a 
     *   variable, instead of immediately throwing it. If true, will
     *   just store the exception; if false, will not store, just throw.
     */
    protected void finishToken(boolean deferErrors)
        throws XMLStreamException
    {
        switch (mCurrToken) {
        case CDATA:
            if (mCfgCoalesceText) {
                readCoalescedText(mCurrToken, deferErrors);
            } else {
                if (readCDataSecondary(Integer.MAX_VALUE)) {
                    mTokenState = TOKEN_FULL_SINGLE;
                } else { // can this ever happen?
                    mTokenState = TOKEN_PARTIAL_SINGLE;
                }
            }
            return;

        case CHARACTERS:
            if (mCfgCoalesceText) {
                /* 21-Sep-2005, TSa: It is often possible to optimize
                 *   here: if we get '<' NOT followed by '!', it can not
                 *   be CDATA, and thus we are done.
                 */
                if (mTokenState == TOKEN_FULL_SINGLE
                    && (mInputPtr + 1) < mInputEnd
                    && mInputBuffer[mInputPtr+1] != '!') {
                    mTokenState = TOKEN_FULL_COALESCED;
                    return;
                }
                readCoalescedText(mCurrToken, deferErrors);
            } else {
                if (readTextSecondary(mShortestTextSegment, deferErrors)) {
                    mTokenState = TOKEN_FULL_SINGLE;
                } else {
                    mTokenState = TOKEN_PARTIAL_SINGLE;
                }
            }
            return;

        case SPACE:
            {
                /* Only need to ensure there's no non-whitespace text
                 * when parsing 'real' ignorable white space (in validating
                 * mode, but that's implicit here)
                 */
                boolean prolog = (mParseState != STATE_TREE);
                readSpaceSecondary(prolog);
                mTokenState = TOKEN_FULL_COALESCED;
            }
            return;

        case COMMENT:
            readComment();
            mTokenState = TOKEN_FULL_COALESCED;
            return;

        case DTD:

            /* 05-Jan-2006, TSa: Although we shouldn't have to use finally
             *   here, it's probably better to do that for robustness
             *   (specifically, in case of a parsing problem, we don't want
             *   to remain in 'DTD partially read' case -- it's better
             *   to get in panic mode and skip the rest)
             */
            try {
                finishDTD(true);
            } finally {
                mTokenState = TOKEN_FULL_COALESCED;
            }
            return;

        case PROCESSING_INSTRUCTION:
            readPI();
            mTokenState = TOKEN_FULL_COALESCED;
            return;

        case START_ELEMENT:
        case END_ELEMENT: // these 2 should never end up in here...
        case ENTITY_REFERENCE:
        case ENTITY_DECLARATION:
        case NOTATION_DECLARATION:
        case START_DOCUMENT:
        case END_DOCUMENT:
            throw new IllegalStateException("finishToken() called when current token is "+tokenTypeDesc(mCurrToken));

        case ATTRIBUTE:
        case NAMESPACE:
            // These two are never returned by this class
        default:
        }

        throw new IllegalStateException("Internal error: unexpected token "+tokenTypeDesc(mCurrToken));
    }

    private void readComment()
        throws XMLStreamException
    {
        char[] inputBuf = mInputBuffer;
        int inputLen = mInputEnd;
        int ptr = mInputPtr;
        int start = ptr;

        // Let's first see if we can just share input buffer:
        while (ptr < inputLen) {
            char c = inputBuf[ptr++];
            if (c > '-') {
                continue;
            }

            if (c < CHAR_SPACE) {
                if (c == '\n') {
                    markLF(ptr);
                } else if (c == '\r') {
                    if (!mNormalizeLFs && ptr < inputLen) {
                        if (inputBuf[ptr] == '\n') {
                            ++ptr;
                        }
                        markLF(ptr);
                    } else {
                        --ptr; // pushback
                        break;
                    }
                } else if (c != '\t') {
                    throwInvalidSpace(c);
                }
            } else if (c == '-') {
                // Ok; need to get '->', can not get '--'
                
                if ((ptr + 1) >= inputLen) {
                    /* Can't check next 2, let's push '-' back, for rest of
                     * code to take care of
                     */
                    --ptr;
                    break;
                }
                
                if (inputBuf[ptr] != '-') {
                    // Can't skip, might be LF/CR
                    continue;
                }
                // Ok; either get '>' or error:
                c = inputBuf[ptr+1];
                if (c != '>') {
                    throwParseError("String '--' not allowed in comment (missing '>'?)");
                }
                mTextBuffer.resetWithShared(inputBuf, start, ptr-start-1);
                mInputPtr = ptr + 2;
                return;
            }
        }

        mInputPtr = ptr;
        mTextBuffer.resetWithCopy(inputBuf, start, ptr-start);
        readComment2(mTextBuffer);
    }

    private void readComment2(TextBuffer tb)
        throws XMLStreamException
    {
        /* Output pointers; calls will also ensure that the buffer is
         * not shared, AND has room for at least one more char
         */
        char[] outBuf = tb.getCurrentSegment();
        int outPtr = tb.getCurrentSegmentSize();
        int outLen = outBuf.length;

        while (true) {
            char c = (mInputPtr < mInputEnd) ?
                mInputBuffer[mInputPtr++] : getNextCharFromCurrent(SUFFIX_IN_COMMENT);

            if (c < CHAR_SPACE) {
                if (c == '\n') {
                    markLF();
                } else if (c == '\r') {
                    if (skipCRLF(c)) { // got 2 char LF
                        if (!mNormalizeLFs) {
                            if (outPtr >= outLen) { // need more room?
                                outBuf = mTextBuffer.finishCurrentSegment();
                                outLen = outBuf.length;
                                outPtr = 0;
                            }
                            outBuf[outPtr++] = c;
                        }
                        // And let's let default output the 2nd char
                        c = '\n';
                    } else if (mNormalizeLFs) { // just \r, but need to convert
                        c = '\n'; // For Mac text
                    }
                } else if (c != '\t') {
                    throwInvalidSpace(c);
                }
            } else if (c == '-') { // Ok; need to get '->', can not get '--'
                c = getNextCharFromCurrent(SUFFIX_IN_COMMENT);
                if (c == '-') { // Ok, has to be end marker then:
                    // Either get '>' or error:
                    c = getNextCharFromCurrent(SUFFIX_IN_COMMENT);
                    if (c != '>') {
                        throwParseError(ErrorConsts.ERR_HYPHENS_IN_COMMENT);
                    }
                    break;
                }

                /* Not the end marker; let's just output the first hyphen,
                 * push the second char back , and let main
                 * code handle it.
                 */
                c = '-';
                --mInputPtr;
            }

            // Need more room?
            if (outPtr >= outLen) {
                outBuf = mTextBuffer.finishCurrentSegment();
                outLen = outBuf.length;
                outPtr = 0;
                verifyLimit("Text size", mConfig.getMaxTextLength(), mTextBuffer.size());
            }
            // Ok, let's add char to output:
            outBuf[outPtr++] = c;
        }

        // Ok, all done, then!
        mTextBuffer.setCurrentLength(outPtr);
    }

    /**
     * Method that reads the primary part of a PI, ie. target, and also
     * skips white space between target and data (if any data)
     *
     * @return Usually <code>PROCESSING_INSTRUCTION</code>; but may be
     *    different in multi-doc mode, if we actually hit a secondary
     *    xml declaration.
     */
    private final int readPIPrimary()
        throws XMLStreamException
    {
        // Ok, first we need the name:
        String target = parseFullName();
        mCurrName = target;

        if (target.length() == 0) {
            throwParseError(ErrorConsts.ERR_WF_PI_MISSING_TARGET);
        }

        // As per XML specs, #17, case-insensitive 'xml' is illegal:
        if (target.equalsIgnoreCase("xml")) {
            // 07-Oct-2005, TSa: Still legal in multi-doc mode...
            if (!mConfig.inputParsingModeDocuments()) {
                throwParseError(ErrorConsts.ERR_WF_PI_XML_TARGET, target, null);
            }
            // Ok, let's just verify we get space then
            char c = getNextCharFromCurrent(SUFFIX_IN_XML_DECL);
            if (!isSpaceChar(c)) {
                throwUnexpectedChar(c, "excepted a space in xml declaration after 'xml'");
            }
            return handleMultiDocStart(START_DOCUMENT);
        }

        // And then either white space before data, or end marker:
        char c = (mInputPtr < mInputEnd) ?
            mInputBuffer[mInputPtr++] : getNextCharFromCurrent(SUFFIX_IN_PROC_INSTR);
        if (isSpaceChar(c)) { // Ok, space to skip
            mTokenState = TOKEN_STARTED;
            // Need to skip the WS...
            skipWS(c);
        } else { // Nope; apparently finishes right away...
            mTokenState = TOKEN_FULL_COALESCED;
            mTextBuffer.resetWithEmpty();
            // or does it?
            if (c != '?' || getNextCharFromCurrent(SUFFIX_IN_PROC_INSTR) != '>') {
                throwUnexpectedChar(c, ErrorConsts.ERR_WF_PI_XML_MISSING_SPACE);
            }
        }

        return PROCESSING_INSTRUCTION;
    }

    /**
     * Method that parses a processing instruction's data portion; at this
     * point target has been parsed.
     */
    private void readPI()
        throws XMLStreamException
    {
        int ptr = mInputPtr;
        int start = ptr;
        char[] inputBuf = mInputBuffer;
        int inputLen = mInputEnd;

        outer_loop:
        while (ptr < inputLen) {
            char c = inputBuf[ptr++];
            if (c < CHAR_SPACE) {
                if (c == '\n') {
                    markLF(ptr);
                } else if (c == '\r') {
                    if (ptr < inputLen && !mNormalizeLFs) {
                        if (inputBuf[ptr] == '\n') {
                            ++ptr;
                        }
                        markLF(ptr);
                    } else {
                        --ptr; // pushback
                        break;
                    }
                } else if (c != '\t') {
                    throwInvalidSpace(c);
                }
            } else if (c == '?') {
                // K; now just need '>' after zero or more '?'s
                while (true) {
                    if (ptr >= inputLen) {
                        /* end of buffer; need to push back at least one of
                         * question marks (not all, since just one is needed
                         * to close the PI)
                         */
                        --ptr;
                        break outer_loop;
                    }
                    c = inputBuf[ptr++];
                    if (c == '>') {
                        mInputPtr = ptr;
                        // Need to discard trailing '?>'
                        mTextBuffer.resetWithShared(inputBuf, start, ptr-start-2);
                        return;
                    }
                    if (c != '?') {
                        // Not end, can continue, but need to push back last char, in case it's LF/CR
                        --ptr;
                        break;
                    }
                }
            }
        }
        
        mInputPtr = ptr;
        // No point in trying to share... let's just append
        mTextBuffer.resetWithCopy(inputBuf, start, ptr-start);
        readPI2(mTextBuffer);
    }

    private void readPI2(TextBuffer tb)
        throws XMLStreamException
    {
        char[] inputBuf = mInputBuffer;
        int inputLen = mInputEnd;
        int inputPtr = mInputPtr;

        /* Output pointers; calls will also ensure that the buffer is
         * not shared, AND has room for one more char
         */
        char[] outBuf = tb.getCurrentSegment();
        int outPtr = tb.getCurrentSegmentSize();

        main_loop:
        while (true) {
            // Let's first ensure we have some data in there...
            if (inputPtr >= inputLen) {
                loadMoreFromCurrent(SUFFIX_IN_PROC_INSTR);
                inputBuf = mInputBuffer;
                inputPtr = mInputPtr;
                inputLen = mInputEnd;
            }

            // And then do chunks
            char c = inputBuf[inputPtr++];
            if (c < CHAR_SPACE) {
                if (c == '\n') {
                    markLF(inputPtr);
                } else if (c == '\r') {
                    mInputPtr = inputPtr;
                    if (skipCRLF(c)) { // got 2 char LF
                        if (!mNormalizeLFs) {
                            // Special handling, to output 2 chars at a time:
                            if (outPtr >= outBuf.length) { // need more room?
                                outBuf = mTextBuffer.finishCurrentSegment();
                                outPtr = 0;
                            }
                            outBuf[outPtr++] = c;
                        }
                        // And let's let default output the 2nd char, either way
                        c = '\n';
                    } else if (mNormalizeLFs) { // just \r, but need to convert
                        c = '\n'; // For Mac text
                    }
                    /* Since skipCRLF() needs to peek(), buffer may have
                     * changed, even if there was no CR+LF.
                     */
                    inputPtr = mInputPtr;
                    inputBuf = mInputBuffer;
                    inputLen = mInputEnd;
                } else if (c != '\t') {
                    throwInvalidSpace(c);
                }
            } else if (c == '?') { // Ok, just need '>' after zero or more '?'s
                mInputPtr = inputPtr; // to allow us to call getNextChar

                qmLoop:
                while (true) {
                    c = (mInputPtr < mInputEnd) ? mInputBuffer[mInputPtr++]
                        : getNextCharFromCurrent(SUFFIX_IN_PROC_INSTR);
                    if (c == '>') { // got it!
                        break main_loop;
                    } else if (c == '?') {
                        if (outPtr >= outBuf.length) { // need more room?
                            outBuf = tb.finishCurrentSegment();
                            outPtr = 0;
                        }
                        outBuf[outPtr++] = c;
                    } else {
                        /* Hmmh. Wasn't end mark after all. Thus, need to
                         * fall back to normal processing, with one more
                         * question mark (first one matched that wasn't
                         * yet output),
                         * reset variables, and go back to main loop.
                         */
                        inputPtr = --mInputPtr; // push back last char
                        inputBuf = mInputBuffer;
                        inputLen = mInputEnd;
                        c = '?';
                        break qmLoop;
                    }
                }
            } // if (c == '?)

            // Need more room?
            if (outPtr >= outBuf.length) {
                outBuf = tb.finishCurrentSegment();
                outPtr = 0;
            }
            // Ok, let's add char to output:
            outBuf[outPtr++] = c;

        } // while (true)

        tb.setCurrentLength(outPtr);
    }

    /**
     * Method called to read the content of both current CDATA/CHARACTERS
     * events, and all following consequtive events into the text buffer.
     * At this point the current type is known, prefix (for CDATA) skipped,
     * and initial consequtive contents (if any) read in.
     *
     * @param deferErrors Flag to enable storing an exception to a 
     *   variable, instead of immediately throwing it. If true, will
     *   just store the exception; if false, will not store, just throw.
     */
    protected void readCoalescedText(int currType, boolean deferErrors)
        throws XMLStreamException
    {
        boolean wasCData;

        // Ok; so we may need to combine adjacent text/CDATA chunks.
        if (currType == CHARACTERS || currType == SPACE) {
            readTextSecondary(Integer.MAX_VALUE, deferErrors);
            wasCData = false;
        } else if (currType == CDATA) {
            /* We may have actually really finished it, but just left
             * the 'unfinished' flag due to need to coalesce...
             */
            if (mTokenState <= TOKEN_PARTIAL_SINGLE) {
                readCDataSecondary(Integer.MAX_VALUE);
            }
            wasCData = true;
        } else {
            throw new IllegalStateException("Internal error: unexpected token "+tokenTypeDesc(mCurrToken)+"; expected CHARACTERS, CDATA or SPACE.");
        }

        // But how about additional text?
        while (!deferErrors || (mPendingException == null)) {
            if (mInputPtr >= mInputEnd) {
                mTextBuffer.ensureNotShared();
                if (!loadMore()) {
                    // ??? Likely an error but let's just break
                    break;
                }
            }
            // Let's peek, ie. not advance it yet
            char c = mInputBuffer[mInputPtr];
            if (c == '<') { // CDATA, maybe?
                // Need to distinguish "<![" from other tags/directives
                // 26-Feb-2014, tatu: Wrt [WSTX-294], need to unshare buffer
                //   unless whole leading CDATA marker fits in buffer
                if ((mInputEnd - mInputPtr) < 9) { // 3 for "<![" and 6 more for "CDATA["
                    mTextBuffer.ensureNotShared();
                    if (!ensureInput(3)) {
                        break;
                    }
                }
                if (mInputBuffer[mInputPtr+1] != '!'
                    || mInputBuffer[mInputPtr+2] != '[') {
                    // Nah, some other tag or directive
                    break;
                }
                // Let's skip beginning parts, then:
                mInputPtr += 3;
                // And verify we get proper CDATA directive
                checkCData();
                /* No need to call the primary data; it's only useful if
                 * there's a chance for sharing buffers... so let's call
                 * the secondary loop straight on.
                 */
                readCDataSecondary(Integer.MAX_VALUE);
                wasCData = true;
            } else { // text
                /* Did we hit an 'unexpandable' entity? If so, need to
                 * just bail out.
                 */
                if (c == '&' && !wasCData) {
                    break;
                }
                // Likewise, can't share buffers, let's call secondary loop:
                readTextSecondary(Integer.MAX_VALUE, deferErrors);
                wasCData = false;
            }
        }

        mTokenState = TOKEN_FULL_COALESCED;
    }

    /**
     * Method called to read in consecutive beginning parts of a CDATA
     * segment, up to either end of the segment (]] and >) or until
     * first 'hole' in text (buffer end, 2-char lf to convert, entity).
     *<p>
     * When the method is called, it's expected that the first character
     * has been read as is in the current input buffer just before current
     * pointer
     *
     * @param c First character in the CDATA segment (possibly part of end
     *   marker for empty segments
     *
     * @return True if the whole CDATA segment was completely read; this
     *   happens only if lt-char is hit; false if it's possible that
     *   it wasn't read (ie. end-of-buffer or entity encountered).
     */
    private final boolean readCDataPrimary(char c)
        throws XMLStreamException
    {
        mWsStatus = (c <= CHAR_SPACE) ? ALL_WS_UNKNOWN : ALL_WS_NO;

        int ptr = mInputPtr;
        int inputLen = mInputEnd;
        char[] inputBuf = mInputBuffer;
        int start = ptr-1;

        while (true) {
            if (c < CHAR_SPACE) {
                if (c == '\n') {
                    markLF(ptr);
                } else if (c == '\r') {
                    if (ptr >= inputLen) { // can't peek?
                        --ptr;
                        break;
                    }
                    if (mNormalizeLFs) { // can we do in-place Mac replacement?
                        if (inputBuf[ptr] == '\n') { // nope, 2 char lf
                            --ptr;
                            break;
                        }
                        inputBuf[ptr-1] = '\n'; // yup
                    } else {
                        // No LF normalization... can we just skip it?
                        if (inputBuf[ptr] == '\n') {
                            ++ptr;
                        }
                    }
                    markLF(ptr);
                } else if (c != '\t') {
                    throwInvalidSpace(c);
                }
            } else if (c == ']') {
                // Ok; need to get one or more ']'s, then '>'
                if ((ptr + 1) >= inputLen) { // not enough room? need to push it back
                    --ptr;
                    break;
                }

                // Needs to be followed by another ']'...
                if (inputBuf[ptr] == ']') {
                    ++ptr;
                    inner_loop:
                    while (true) {
                        if (ptr >= inputLen) {
                            /* Need to push back last 2 right brackets; it may
                             * be end marker divided by input buffer boundary
                             */
                            ptr -= 2;
                            break inner_loop;
                        }
                        c = inputBuf[ptr++];
                        if (c == '>') { // Ok, got it!
                            mInputPtr = ptr;
                            ptr -= (start+3);
                            mTextBuffer.resetWithShared(inputBuf, start, ptr);
                            mTokenState = TOKEN_FULL_SINGLE;
                            return true;
                        }
                        if (c != ']') {
                            // Need to re-check this char (may be linefeed)
                            --ptr;
                            break inner_loop;
                        }
                        // Fall through to next round
                    }
                }
            }

            if (ptr >= inputLen) { // end-of-buffer?
                break;
            }
            c = inputBuf[ptr++];
        }

        mInputPtr = ptr;

        /* If we end up here, we either ran out of input, or hit something
         * which would leave 'holes' in buffer... fine, let's return then;
         * we can still update shared buffer copy: would be too early to
         * make a copy since caller may not even be interested in the
         * stuff.
         */
        int len = ptr - start;
        mTextBuffer.resetWithShared(inputBuf, start, len);
        if (mCfgCoalesceText ||
            (mTextBuffer.size() < mShortestTextSegment)) {
            mTokenState = TOKEN_STARTED;
        } else {
            mTokenState = TOKEN_PARTIAL_SINGLE;
        }
        return false;
    }

    /**
     * @return True if the whole CData section was completely read (we
     *   hit the end marker); false if a shorter segment was returned.
     */
    protected boolean readCDataSecondary(int shortestSegment)
        throws XMLStreamException
    {
        // Input pointers
        char[] inputBuf = mInputBuffer;
        int inputLen = mInputEnd;
        int inputPtr = mInputPtr;

        /* Output pointers; calls will also ensure that the buffer is
         * not shared, AND has room for one more char
         */
        char[] outBuf = mTextBuffer.getCurrentSegment();
        int outPtr = mTextBuffer.getCurrentSegmentSize();

        while (true) {
            if (inputPtr >= inputLen) {
                loadMore(SUFFIX_IN_CDATA);
                inputBuf = mInputBuffer;
                inputPtr = mInputPtr;
                inputLen = mInputEnd;
            }
            char c = inputBuf[inputPtr++];

            if (c < CHAR_SPACE) {
                if (c == '\n') {
                    markLF(inputPtr);
                } else if (c == '\r') {
                    mInputPtr = inputPtr;
                    if (skipCRLF(c)) { // got 2 char LF
                        if (!mNormalizeLFs) {
                            // Special handling, to output 2 chars at a time:
                            outBuf[outPtr++] = c;
                            if (outPtr >= outBuf.length) { // need more room?
                                outBuf = mTextBuffer.finishCurrentSegment();
                                outPtr = 0;
                            }
                        }
                        // And let's let default output the 2nd char, either way
                        c = '\n';
                    } else if (mNormalizeLFs) { // just \r, but need to convert
                        c = '\n'; // For Mac text
                    }
                    /* Since skipCRLF() needs to peek(), buffer may have
                     * changed, even if there was no CR+LF.
                     */
                    inputPtr = mInputPtr;
                    inputBuf = mInputBuffer;
                    inputLen = mInputEnd;
                } else if (c != '\t') {
                    throwInvalidSpace(c);
                }
            } else if (c == ']') {
                // Ok; need to get ']>'
                mInputPtr = inputPtr;
                if (checkCDataEnd(outBuf, outPtr)) {
                    return true;
                }
                inputPtr = mInputPtr;
                inputBuf = mInputBuffer;
                inputLen = mInputEnd;

                outBuf = mTextBuffer.getCurrentSegment();
                outPtr = mTextBuffer.getCurrentSegmentSize();
                continue; // need to re-process last (non-bracket) char
            }

            // Ok, let's add char to output:
            outBuf[outPtr++] = c;

            // Need more room?
            if (outPtr >= outBuf.length) {
                TextBuffer tb = mTextBuffer;
                // Perhaps we have now enough to return?
                if (!mCfgCoalesceText) {
                    tb.setCurrentLength(outBuf.length);
                    if (tb.size() >= shortestSegment) {
                        mInputPtr = inputPtr;
                        return false;
                    }
                }
                // If not, need more buffer space:
                outBuf = tb.finishCurrentSegment();
                outPtr = 0;
                // 17-Aug-2016, tatu: need to make sure to enforce size limits here too
                verifyLimit("Text size", mConfig.getMaxTextLength(), mTextBuffer.size());
            }
        }
        // never gets here
    }

    /**
     * Method that will check, given the starting ']', whether there is
     * ending ']]>' (including optional extra ']'s); if so, will updated
     * output buffer with extra ]s, if not, will make sure input and output
     * are positioned for further checking.
     * 
     * @return True, if we hit the end marker; false if not.
     */
    private boolean checkCDataEnd(char[] outBuf, int outPtr)
        throws XMLStreamException
    {
        int bracketCount = 0;
        char c;
        do {
            ++bracketCount;
            c = (mInputPtr < mInputEnd) ? mInputBuffer[mInputPtr++]
                : getNextCharFromCurrent(SUFFIX_IN_CDATA);
        } while (c == ']');

        boolean match = (bracketCount >= 2 && c == '>');
        if (match) {
            bracketCount -= 2;
        }
        while (bracketCount > 0) {
            --bracketCount;
            outBuf[outPtr++] = ']';
            if (outPtr >= outBuf.length) {
                /* Can't really easily return, even if we have enough
                 * stuff here, since we've more than one char...
                 */
                outBuf = mTextBuffer.finishCurrentSegment();
                outPtr = 0;
            }
        }
        mTextBuffer.setCurrentLength(outPtr);
        // Match? Can break, then:
        if (match) {
            return true;
        }
        // No match, need to push the last char back and admit defeat...
        --mInputPtr;
        return false;
    }

    /**
     * Method called to read in consecutive beginning parts of a text
     * segment, up to either end of the segment (lt char) or until
     * first 'hole' in text (buffer end, 2-char lf to convert, entity).
     *<p>
     * When the method is called, it's expected that the first character
     * has been read as is in the current input buffer just before current
     * pointer
     *
     * @param c First character of the text segment
     *
     * @return True if the whole text segment was completely read; this
     *   happens only if lt-char is hit; false if it's possible that
     *   it wasn't read (ie. end-of-buffer or entity encountered).
     */
    private final boolean readTextPrimary(char c) throws XMLStreamException
    {
        int ptr = mInputPtr;
        int start = ptr-1;

        // First: can we heuristically canonicalize ws used for indentation?
        if (c <= CHAR_SPACE) {
            int len = mInputEnd;
            /* Even without indentation removal, it's good idea to
             * 'convert' \r or \r\n into \n (by replacing or skipping first
             * char): this may allow reusing the buffer. 
             * But note that conversion MUST be enabled -- this is toggled
             * by code that includes internal entities, to prevent replacement
             * of CRs from int. general entities, as applicable.
             */
            do {
                // We'll need at least one char, no matter what:
                if (ptr < len && mNormalizeLFs) {
                    if (c == '\r') {
                        c = '\n';
                        if (mInputBuffer[ptr] == c) {
                            // Ok, whatever happens, can 'skip' \r, to point to following \n:
                            ++start;
                            // But if that's buffer end, can't skip that
                            if (++ptr >= len) {
                                break;
                            }
                        } else {
                            mInputBuffer[start] = c;
                        }
                    } else if (c != '\n') {
                        break;
                    }
                    markLF(ptr);
                    if (mCheckIndentation > 0) {
                        ptr = readIndentation(c, ptr);
                        if (ptr < 0) { // success!
                            return true;
                        }
                    }
                    // If we got this far, we skipped a lf, need to read next char
                    c = mInputBuffer[ptr++];
                }
            } while (false);

            // can we figure out indentation?
            mWsStatus = ALL_WS_UNKNOWN;
        } else {
            mWsStatus = ALL_WS_NO;
        }
        
        char[] inputBuf = mInputBuffer;
        int inputLen = mInputEnd;

        // Let's first see if we can just share input buffer:
        while (true) {
            if (c < CHAR_FIRST_PURE_TEXT) {
                if (c == '<') {
                    mInputPtr = --ptr;
                    mTextBuffer.resetWithShared(inputBuf, start, ptr-start);
                    return true;
                }
                if (c < CHAR_SPACE) {
                    if (c == '\n') {
                        markLF(ptr);
                    } else if (c == '\r') {
                        if (ptr >= inputLen) { // can't peek?
                            --ptr;
                            break;
                        }
                        if (mNormalizeLFs) { // can we do in-place Mac replacement?
                            if (inputBuf[ptr] == '\n') { // nope, 2 char lf
                                --ptr;
                                break;
                            }
                            /* This would otherwise be risky (may modify value of
                             * a shared entity value), but since DTDs are cached/accessed
                             * based on properties including lf-normalization there's no
                             * harm in 'fixing' it in place.
                             */
                            inputBuf[ptr-1] = '\n'; // yup
                        } else {
                            // No LF normalization... can we just skip it?
                            if (inputBuf[ptr] == '\n') {
                                ++ptr;
                            }
                        }
                        markLF(ptr);
                    } else if (c != '\t') {
                        // Should consume invalid char, but not include in result
                        mInputPtr = ptr;
                        mTextBuffer.resetWithShared(inputBuf, start, ptr-start-1);
                        /* Let's defer exception, provided we got at least
                         * one valid character (if not, better throw
                         * exception right away)
                         */
                        boolean deferErrors = (ptr - start) > 1;
                        mPendingException = throwInvalidSpace(c, deferErrors);
                        return true;
                    }
                } else if (c == '&') {
                    // Let's push it back and break
                    --ptr;
                   break;
                } else if (c == '>') {
                    // Let's see if we got ']]>'?
                    if ((ptr - start) >= 3) {
                        if (inputBuf[ptr-3] == ']' && inputBuf[ptr-2] == ']') {
                            /* Let's include ']]' in there, not '>' (since that
                             * makes it non-wellformed): but need to consume
                             * that char nonetheless
                             */
                            mInputPtr = ptr;
                            mTextBuffer.resetWithShared(inputBuf, start, ptr-start-1);
                            mPendingException = throwWfcException(ErrorConsts.ERR_BRACKET_IN_TEXT, true);
                            return true; // and we are fully done
                        }
                    }
                }
            } // if (char in lower code range)

            if (ptr >= inputLen) { // end-of-buffer?
                break;
            }
            c = inputBuf[ptr++];
        }
        mInputPtr = ptr;

        /* If we end up here, we either ran out of input, or hit something
         * which would leave 'holes' in buffer... fine, let's return then;
         * we can still update shared buffer copy: would be too early to
         * make a copy since caller may not even be interested in the
         * stuff.
         */
        mTextBuffer.resetWithShared(inputBuf, start, ptr - start);
        return false;
    }

    /**
     *
     * @param deferErrors Flag to enable storing an exception to a 
     *   variable, instead of immediately throwing it. If true, will
     *   just store the exception; if false, will not store, just throw.
     *
     * @return True if the text segment was completely read ('<' was hit,
     *   or in non-entity-expanding mode, a non-char entity); false if
     *   it may still continue
     */
    protected final boolean readTextSecondary(int shortestSegment, boolean deferErrors)
        throws XMLStreamException
    {
        /* Output pointers; calls will also ensure that the buffer is
         * not shared, AND has room for at least one more char
         */
        char[] outBuf = mTextBuffer.getCurrentSegment();
        int outPtr = mTextBuffer.getCurrentSegmentSize();
        int inputPtr = mInputPtr;
        char[] inputBuffer = mInputBuffer;
        int inputLen = mInputEnd;

        while (true) {
            if (inputPtr >= inputLen) {
                /* 07-Oct-2005, TSa: Let's not throw an exception for EOF from
                 *   here -- in fragment mode, it shouldn't be thrown, and in
                 *   other modes we might as well first return text, and only
                 *   then throw an exception: no need to do that yet.
                 */
                mInputPtr = inputPtr;
                if (!loadMore()) {
                    break;
                }
                inputPtr = mInputPtr;
                inputBuffer = mInputBuffer;
                inputLen = mInputEnd;
            }
            char c = inputBuffer[inputPtr++];

            // Most common case is we don't have special char, thus:
            if (c < CHAR_FIRST_PURE_TEXT) {
                if (c < CHAR_SPACE) {
                    if (c == '\n') {
                        markLF(inputPtr);
                    } else if (c == '\r') {
                        mInputPtr = inputPtr;
                        if (skipCRLF(c)) { // got 2 char LF
                            if (!mNormalizeLFs) {
                                // Special handling, to output 2 chars at a time:
                                outBuf[outPtr++] = c;
                                if (outPtr >= outBuf.length) { // need more room?
                                    outBuf = mTextBuffer.finishCurrentSegment();
                                    outPtr = 0;
                                }
                            }
                            // And let's let default output the 2nd char
                            c = '\n';
                        } else if (mNormalizeLFs) { // just \r, but need to convert
                            c = '\n'; // For Mac text
                        }
                        /* note: skipCRLF() may change ptr and len, but since
                         * it does not close input source, it won't change
                         * actual buffer object:
                         */
                        //inputBuffer = mInputBuffer;
                        inputLen = mInputEnd;
                        inputPtr = mInputPtr;
                    } else if (c != '\t') {
                        mTextBuffer.setCurrentLength(outPtr);
                        mInputPtr = inputPtr;
                        mPendingException = throwInvalidSpace(c, deferErrors);
                        break;
                    }
                } else if (c == '<') { // end is nigh!
                    mInputPtr = inputPtr-1;
                    break;
                } else if (c == '&') {
                    mInputPtr = inputPtr;
                    int ch;
                    if (mCfgReplaceEntities) { // can we expand all entities?
                        if ((inputLen - inputPtr) >= 3
                            && (ch = resolveSimpleEntity(true)) != 0) {
                            // Ok, it's fine then
                        } else {
                            ch = fullyResolveEntity(true);
                            if (ch == 0) {
                                // Input buffer changed, nothing to output quite yet:
                                inputBuffer = mInputBuffer;
                                inputLen = mInputEnd;
                                inputPtr = mInputPtr;
                                continue;
                            }
                            // otherwise char is now fine...
                        }
                    } else {
                        /* Nope, can only expand char entities; others need
                         * to be separately handled.
                         */
                        ch = resolveCharOnlyEntity(true);
                        if (ch == 0) { // some other entity...
                            /* can't expand; underlying pointer now points to
                             * char after ampersand, need to rewind
                             */
                            --mInputPtr;
                            break;
                        }
                        // .. otherwise we got char we needed
                    }
                    if (ch <= 0xFFFF) {
                        c = (char) ch;
                    } else {
                        ch -= 0x10000;
                        // need more room?
                        if (outPtr >= outBuf.length) {
                            outBuf = mTextBuffer.finishCurrentSegment();
                            outPtr = 0;
                        }
                        outBuf[outPtr++] = (char) ((ch >> 10)  + 0xD800);
                        if (outPtr >= outBuf.length) {
                            if ((outBuf = _expandOutputForText(inputPtr, outBuf, Integer.MAX_VALUE)) == null) { // got enough, leave
                                return false;
                            }
                            outPtr = 0;
                        }
                        c = (char) ((ch & 0x3FF)  + 0xDC00);
                    }
                    inputPtr = mInputPtr;
                    // not quite sure why this is needed... but it is:
                    inputLen = mInputEnd;
                } else if (c == '>') {
                    // Let's see if we got ']]>'?
                    /* 21-Apr-2005, TSa: But we can NOT check the output buffer
                     *  as it contains _expanded_ stuff... only input side.
                     *  For now, 98% accuracy has to do, as we may not be able
                     *  to access previous buffer's contents. But at least we
                     *  won't produce false positives from entity expansion
                     */
                    if (inputPtr > 2) { // can we do it here?
                        // Since mInputPtr has been advanced, -1 refers to '>'
                        if (inputBuffer[inputPtr-3] == ']'
                            && inputBuffer[inputPtr-2] == ']') {
                            mInputPtr = inputPtr;
                            /* We have already added ']]' into output buffer...
                             * should be ok, since only with '>' does it become
                             * non-wellformed.
                             */
                            mTextBuffer.setCurrentLength(outPtr);
                            mPendingException = throwWfcException(ErrorConsts.ERR_BRACKET_IN_TEXT, deferErrors);
                            break;
                        }
                    } else {
                        /* 21-Apr-2005, TSa: No good way to verify it,
                         *   at this point. Should come back and think of how
                         *   to properly handle this (rare) possibility.
                         */
                        ;
                    }
                }
            }
            // Ok, let's add char to output:
            outBuf[outPtr++] = c;

            // Need more room?
            if (outPtr >= outBuf.length) {
                if ((outBuf = _expandOutputForText(inputPtr, outBuf, shortestSegment)) == null) { // got enough, leave
                    return false;
                }
                verifyLimit("Text size", mConfig.getMaxTextLength(), mTextBuffer.size());
                outPtr = 0;
            }
        }
        mTextBuffer.setCurrentLength(outPtr);
        return true;
    }

    private final char[] _expandOutputForText(int inputPtr, char[] outBuf,
            int shortestSegment)
    {
        TextBuffer tb = mTextBuffer;
        // Perhaps we have now enough to return?
        tb.setCurrentLength(outBuf.length);
        if (tb.size() >= shortestSegment) {
            mInputPtr = inputPtr;
            return null;
        }
        // If not, need more buffer space:
        return tb.finishCurrentSegment();
    }
    
    /**
     * Method called to try to parse and canonicalize white space that
     * has a good chance of being white space with somewhat regular
     * structure; specifically, something that looks like typical
     * indentation.
     *<p>
     * Note: Caller guarantees that there will be at least 2 characters
     * available in the input buffer. And method has to ensure that if
     * it does not find a match, it will return pointer value such
     * that there is at least one valid character remaining.
     *
     * @return -1, if the content was determined to be canonicalizable
     *    (indentation) white space; and thus fully parsed. Otherwise
     *    pointer (value to set to mInputPtr) to the next character
     *    to process (not processed by this method)
     */
    private final int readIndentation(char c, int ptr)
        throws XMLStreamException
    {
        /* We need to verify that:
         * (a) we can read enough contiguous data to do determination
         * (b) sequence is a linefeed, with either zero or more following
         *    spaces, or zero or more tabs; and followed by non-directive
         *    tag (start/end tag)
         * and if so, we can use a canonical shared representation of
         * this even.
         */
        final int inputLen = mInputEnd;
        final char[] inputBuf = mInputBuffer;
        int start = ptr-1;
        final char lf = c;

        // Note: caller guarantees at least one more char in the input buffer
        ws_loop:
        do { // dummy loop to allow for break (which indicates failure)
            c = inputBuf[ptr++];
            if (c == ' ' || c == '\t') { // indentation?
                // Need to limit to maximum
                int lastIndCharPos = (c == ' ') ? TextBuffer.MAX_INDENT_SPACES : TextBuffer.MAX_INDENT_TABS;
                lastIndCharPos += ptr;
                if (lastIndCharPos > inputLen) {
                    lastIndCharPos = inputLen;
                }

                inner_loop:
                while (true) {
                    if (ptr >= lastIndCharPos) { // overflow; let's backtrack
                        --ptr;
                        break ws_loop;
                    }
                    char d = inputBuf[ptr++];
                    if (d != c) {
                        if (d == '<') { // yup, got it!
                            break inner_loop;
                        }
                        --ptr; // caller needs to reprocess it
                        break ws_loop; // nope, blew it
                    }
                }
                // This means we had success case; let's fall through
            } else if (c != '<') { // nope, can not be
                --ptr; // simpler if we just push it back; needs to be processed later on
                break ws_loop;
            }

            // Ok; we got '<'... just need any other char than '!'...
            if (ptr < inputLen && inputBuf[ptr] != '!') {
                // Voila!
                mInputPtr = --ptr; // need to push back that '<' too
                mTextBuffer.resetWithIndentation(ptr - start - 1, c);
                // One more thing: had a positive match, need to note it
                if (mCheckIndentation < INDENT_CHECK_MAX) {
                    mCheckIndentation += INDENT_CHECK_START;
                }
                mWsStatus = ALL_WS_YES;
                return -1;
            }
            // Nope: need to push '<' back, then
            --ptr;
        } while (false);

        // Ok, nope... caller can/need to take care of it:
        /* Also, we may need to subtract indentation check count to possibly
         * disable this check if it doesn't seem to work.
         */
        --mCheckIndentation;
        /* Also; if lf we got was \r, need to convert it now (this
         * method only gets called in lf converting mode)
         * (and yes, it is safe to modify input buffer at this point;
         * see calling method for details)
         */
        if (lf == '\r') {
            inputBuf[start] = '\n';
        }
        return ptr;
    }

    /**
     * Reading whitespace should be very similar to reading normal text;
     * although couple of simplifications can be made. Further, since this
     * method is very unlikely to be of much performance concern, some
     * optimizations are left out, where it simplifies code.
     *
     * @param c First white space characters; known to contain white space
     *   at this point
     * @param prologWS If true, is reading white space outside XML tree,
     *   and as such can get EOF. If false, should not get EOF, nor be
     *   followed by any other char than &lt;
     *
     * @return True if the whole white space segment was read; false if
     *   something prevented that (end of buffer, replaceable 2-char lf)
     */
    private final boolean readSpacePrimary(char c, boolean prologWS)
        throws XMLStreamException
    {
        int ptr = mInputPtr;
        char[] inputBuf = mInputBuffer;
        int inputLen = mInputEnd;
        int start = ptr-1;

        // Let's first see if we can just share input buffer:
        while (true) {
            /* 30-Aug-2006, TSa: Let's not check for validity errors yet,
             * even if we could detect problems at this point.
             * This because it's not always
             * an error (in dtd-aware, non-validating mode); but also since
             * that way we can first return all space we got, and only
             * indicate error when next token is to be accessed.
             */
            if (c > CHAR_SPACE) { // End of whitespace
                mInputPtr = --ptr;
                mTextBuffer.resetWithShared(mInputBuffer, start, ptr-start);
                return true;
            }

            if (c == '\n') {
                markLF(ptr);
            } else if (c == '\r') {
                if (ptr >= mInputEnd) { // can't peek?
                    --ptr;
                    break;
                }
                if (mNormalizeLFs) { // can we do in-place Mac replacement?
                    if (inputBuf[ptr] == '\n') { // nope, 2 char lf
                        --ptr;
                        break;
                    }
                    inputBuf[ptr-1] = '\n'; // yup
                } else {
                    // No LF normalization... can we just skip it?
                    if (inputBuf[ptr] == '\n') {
                        ++ptr;
                    }
                }
                markLF(ptr);
            } else if (c != CHAR_SPACE && c != '\t') {
                throwInvalidSpace(c);
            }
            if (ptr >= inputLen) { // end-of-buffer?
                break;
            }
            c = inputBuf[ptr++];
        }

        mInputPtr = ptr;
        
        /* Ok, couldn't read it completely, let's just return whatever
         * we did get as shared data
         */
        mTextBuffer.resetWithShared(inputBuf, start, ptr - start);
        return false;
    }

    /**
     * This is very similar to readSecondaryText(); called when we need
     * to read in rest of (ignorable) white space segment.
     *
     * @param prologWS True if the ignorable white space is within prolog
     *   (or epilog); false if it's within xml tree.
     */
    private void readSpaceSecondary(boolean prologWS)
        throws XMLStreamException
    {
        /* Let's not bother optimizing input. However, we can easily optimize
         * output, since it's easy to do, yet has more effect on performance
         * than localizing input variables.
         */
        char[] outBuf = mTextBuffer.getCurrentSegment();
        int outPtr = mTextBuffer.getCurrentSegmentSize();

        while (true) {
            if (mInputPtr >= mInputEnd) {
                /* 07-Oct-2005, TSa: Let's not throw an exception yet --
                 *   can return SPACE, and let exception be thrown
                 *   when trying to fetch next event.
                 */
                if (!loadMore()) {
                    break;
                }
            }
            char c = mInputBuffer[mInputPtr];
            if (c > CHAR_SPACE) { // end of WS?
                break;
            }
            ++mInputPtr;
            if (c == '\n') {
                markLF();
            } else if (c == '\r') {
                if (skipCRLF(c)) {
                    if (!mNormalizeLFs) {
                        // Special handling, to output 2 chars at a time:
                        outBuf[outPtr++] = c;
                        if (outPtr >= outBuf.length) { // need more room?
                            outBuf = mTextBuffer.finishCurrentSegment();
                            outPtr = 0;
                        }
                    }
                    c = '\n';
                } else if (mNormalizeLFs) {
                    c = '\n'; // For Mac text
                }
            } else if (c != CHAR_SPACE && c != '\t') {
                throwInvalidSpace(c);
            }
                
            // Ok, let's add char to output:
            outBuf[outPtr++] = c;

            // Need more room?
            if (outPtr >= outBuf.length) {
                outBuf = mTextBuffer.finishCurrentSegment();
                outPtr = 0;
            }
        }
        mTextBuffer.setCurrentLength(outPtr);
    }

    /**
     * Method called to read the contents of the current CHARACTERS
     * event, and write all contents using the specified Writer.
     *
     * @param w Writer to use for writing out textual content parsed
     *
     * @return Total number of characters written using the writer
     */
    private int readAndWriteText(Writer w)
        throws IOException, XMLStreamException
    {
        mTokenState = TOKEN_FULL_SINGLE; // we'll read it all

        /* We should be able to mostly just use the input buffer at this
         * point; exceptions being two-char linefeeds (when converting
         * to single ones) and entities (which likewise can expand or
         * shrink), both of which require flushing and/or single byte
         * output.
         */
        int start = mInputPtr;
        int count = 0;

        main_loop:
        while (true) {
	    char c;
            // Reached the end of buffer? Need to flush, then
            if (mInputPtr >= mInputEnd) {
                int len = mInputPtr - start;
                if (len > 0) {
                    w.write(mInputBuffer, start, len);
                    count += len;
                }
                c = getNextChar(SUFFIX_IN_TEXT);
                start = mInputPtr-1; // needs to be prior to char we got
            } else {
                c = mInputBuffer[mInputPtr++];
            }
            // Most common case is we don't have a special char, thus:
            if (c < CHAR_FIRST_PURE_TEXT) {
                if (c < CHAR_SPACE) {
                    if (c == '\n') {
                        markLF();
                    } else if (c == '\r') {
                        char d;
                        if (mInputPtr >= mInputEnd) {
                            /* If we can't peek easily, let's flush past stuff
                             * and load more... (have to flush, since new read
                             * will overwrite inbut buffers)
                             */
                            int len = mInputPtr - start;
                            if (len > 0) {
                                w.write(mInputBuffer, start, len);
                                count += len;
                            }
                            d = getNextChar(SUFFIX_IN_TEXT);
                            start = mInputPtr; // to mark 'no past content'
                        } else {
                            d = mInputBuffer[mInputPtr++];
                        }
                        if (d == '\n') {
                            if (mNormalizeLFs) {
                                /* Let's flush content prior to 2-char LF, and
                                 * start the new segment on the second char...
                                 * this way, no mods are needed for the buffer,
                                 * AND it'll also  work on split 2-char lf!
                                 */
                                int len = mInputPtr - 2 - start;
                                if (len > 0) {
                                    w.write(mInputBuffer, start, len);
                                    count += len;
                                }
                                start = mInputPtr-1; // so '\n' is the first char
                            } else {
                                ; // otherwise it's good as is
                            }
                        } else { // not 2-char... need to replace?
                            --mInputPtr;
                            if (mNormalizeLFs) {
                                mInputBuffer[mInputPtr-1] = '\n';
                            }
                        }
                        markLF();
                    } else if (c != '\t') {
                        throwInvalidSpace(c);
                    }
                } else if (c == '<') { // end is nigh!
                    break main_loop;
                } else if (c == '&') {
                    /* Have to flush all stuff, since entities pretty much
                     * force it; input buffer won't be contiguous
                     */
                    int len = mInputPtr - 1 - start; // -1 to remove ampersand
                    if (len > 0) {
                        w.write(mInputBuffer, start, len);
                        count += len;
                    }
                    int ch;
                    if (mCfgReplaceEntities) { // can we expand all entities?
                        if ((mInputEnd - mInputPtr) < 3
                            || (ch = resolveSimpleEntity(true)) == 0) {
                            ch = fullyResolveEntity(true);
                        }
                    } else {
                        ch = resolveCharOnlyEntity(true);
                        if (ch == 0) { // some other entity...
                            /* can't expand, so, let's just bail out... but
                             * let's also ensure no text is added twice, as
                             * all prev text was just flushed, but resolve
                             * may have moved input buffer around.
                             */
                            start = mInputPtr;
                            break main_loop;
                        }
                    }
                    if (ch != 0) {
                        if (ch <= 0xFFFF) {
                            c = (char) ch;
                        } else {
                            ch -= 0x10000;
                            w.write((char) ((ch >> 10)  + 0xD800));
                            c = (char) ((ch & 0x3FF)  + 0xDC00);
                        }
                        w.write(c);
                        ++count;
                    }
                    start = mInputPtr;
                } else if (c == '>') { // did we get ']]>'?
                    /* 21-Apr-2005, TSa: But we can NOT check the output buffer
                     *  (see comments in readTextSecondary() for details)
                     */
                    if (mInputPtr >= 2) { // can we do it here?
                        if (mInputBuffer[mInputPtr-2] == ']'
                            && mInputBuffer[mInputPtr-1] == ']') {
                            // Anything to flush?
                            int len = mInputPtr - start;
                            if (len > 0) {
                                w.write(mInputBuffer, start, len);
                            }
                            throwParseError(ErrorConsts.ERR_BRACKET_IN_TEXT);
                        }
                    } else {
                        ; // !!! TBI: how to check past boundary?
                    }
                } else if (c == CHAR_NULL) {
                    throwNullChar();
                }
            }
        } // while (true)

        /* Need to push back '<' or '&', whichever caused us to
         * get out...
         */
        --mInputPtr;

        // Anything left to flush?
        int len = mInputPtr - start;
        if (len > 0) {
            w.write(mInputBuffer, start, len);
            count += len;
        }
        return count;
    }

    /**
     * Method called to read the contents of the current (possibly partially
     * read) CDATA
     * event, and write all contents using the specified Writer.
     *
     * @param w Writer to use for writing out textual content parsed
     *
     * @return Total number of characters written using the writer for
     *   the current CDATA event
     */
    private int readAndWriteCData(Writer w)
        throws IOException, XMLStreamException
    {
        mTokenState = TOKEN_FULL_SINGLE; // we'll read it all

        /* Ok; here we can basically have 2 modes; first the big loop to
         * gather all data up until a ']'; and then another loop to see
         * if ']' is part of ']]>', and after this if no end marker found,
         * go back to the first part.
         */
        char c = (mInputPtr < mInputEnd) ?
            mInputBuffer[mInputPtr++] : getNextChar(SUFFIX_IN_CDATA);
        int count = 0;

        main_loop:
        while (true) {
            int start = mInputPtr-1;

            quick_loop:
            while (true) {
                if (c > CHAR_CR_LF_OR_NULL) {
                    if (c == ']') {
                        break quick_loop;
                    }
                } else {
                    if (c < CHAR_SPACE) {
                        if (c == '\n') {
                            markLF();
                        } else if (c == '\r') {
                            char d;
                            if (mInputPtr >= mInputEnd) {
                                /* If we can't peek easily, let's flush past stuff
                                 * and load more... (have to flush, since new read
                                 * will overwrite inbut buffers)
                                 */
                                int len = mInputPtr - start;
                                if (len > 0) {
                                    w.write(mInputBuffer, start, len);
                                    count += len;
                                }
                                d = getNextChar(SUFFIX_IN_CDATA);
                                start = mInputPtr; // to mark 'no past content'
                            } else {
                                d = mInputBuffer[mInputPtr++];
                            }
                            if (d == '\n') {
                                if (mNormalizeLFs) {
                                    /* Let's flush content prior to 2-char LF, and
                                     * start the new segment on the second char...
                                     * this way, no mods are needed for the buffer,
                                     * AND it'll also  work on split 2-char lf!
                                     */
                                    int len = mInputPtr - 2 - start;
                                    if (len > 0) {
                                        w.write(mInputBuffer, start, len);
                                        count += len;
                                    }
                                    start = mInputPtr-1; // so '\n' is the first char
                                } else {
                                    // otherwise it's good as is
                                }
                            } else { // not 2-char... need to replace?
                                --mInputPtr;
                                if (mNormalizeLFs) {
                                    mInputBuffer[mInputPtr-1] = '\n';
                                }
                            }
                            markLF();
                        } else if (c != '\t') {
                            throwInvalidSpace(c);
                        }
                    }
                }
                // Reached the end of buffer? Need to flush, then
                if (mInputPtr >= mInputEnd) {
                    int len = mInputPtr - start;
                    if (len > 0) {
                        w.write(mInputBuffer, start, len);
                        count += len;
                    }
                    start = 0;
                    c = getNextChar(SUFFIX_IN_CDATA);
                } else {
                    c = mInputBuffer[mInputPtr++];
                }
            } // while (true)

            // Anything to flush once we hit ']'?
            {
                /* -1 since the last char in there (a '[') is NOT to be
                 * output at this point
                 */
                int len = mInputPtr - start - 1;
                if (len > 0) {
                    w.write(mInputBuffer, start, len);
                    count += len;
                }
            }

            /* Ok; we only get this far when we hit a ']'. We got one,
             * so let's see if we can find at least one more bracket,
             * immediately followed by '>'...
             */
            int bracketCount = 0;
            do {
                ++bracketCount;
                c = (mInputPtr < mInputEnd) ? mInputBuffer[mInputPtr++]
                    : getNextCharFromCurrent(SUFFIX_IN_CDATA);
            } while (c == ']');

            boolean match = (bracketCount >= 2 && c == '>');
            if (match) {
                bracketCount -= 2;
            }
            while (bracketCount > 0) {
                --bracketCount;
                w.write(']');
                ++count;
            }
            if (match) {
                break main_loop;
            }
            /* Otherwise we'll just loop; now c is properly set to be
             * the next char as well.
             */
        } // while (true)

        return count;
    }

    /**
     * @return Number of characters written to Writer during the call
     */
    private int readAndWriteCoalesced(Writer w, boolean wasCData)
        throws IOException, XMLStreamException
    {
        mTokenState = TOKEN_FULL_COALESCED;
        int count = 0;

        /* Ok, so what do we have next? CDATA, CHARACTERS, or something
         * else?
         */
        main_loop:
        while (true) {
            if (mInputPtr >= mInputEnd) {
                if (!loadMore()) {
                    /* Shouldn't normally happen, but let's just let
                     * caller deal with it...
                     */
                    break main_loop;
                }
            }
            // Let's peek, ie. not advance it yet
            char c = mInputBuffer[mInputPtr];
            if (c == '<') { // CDATA, maybe?
                // Need to distinguish "<![" from other tags/directives
                if ((mInputEnd - mInputPtr) < 3) {
                    if (!ensureInput(3)) { // likewise, probably an error...
                        break main_loop;
                    }
                }
                if (mInputBuffer[mInputPtr+1] != '!'
                    || mInputBuffer[mInputPtr+2] != '[') {
                    // Nah, some other tag or directive
                    break main_loop;
                }
                // Let's skip beginning parts, then:
                mInputPtr += 3;
                // And verify we get proper CDATA directive
                checkCData();
                // cool, let's just handle it then
                count += readAndWriteCData(w);
                wasCData = true;
            } else { // text
                /* Did we hit an 'unexpandable' entity? If so, need to
                 * just bail out (only happens when Coalescing AND not
                 * expanding -- a rather unlikely combination)
                 */
                if (c == '&' && !wasCData) {
                    break;
                }
                count += readAndWriteText(w);
                wasCData = false;
            }
        }

        return count;
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Internal methods, low-level input access
    ///////////////////////////////////////////////////////////////////////
     */
    
    /**
     * Method that will skip any white space from input source(s)
     *
     * @return true If at least one white space was skipped; false
     *   if not (character passed was not white space)
     */
    protected final boolean skipWS(char c) 
        throws XMLStreamException
    {
        if (c > CHAR_SPACE) {
            return false;
        }
        while (true) {
            // Linefeed?
            if (c == '\n' || c == '\r') {
                skipCRLF(c);
            } else if (c != CHAR_SPACE && c != '\t') {
                throwInvalidSpace(c);
            }
            if (mInputPtr >= mInputEnd) {
                // Let's see if current source has more
                if (!loadMoreFromCurrent()) {
                    return true;
                }
            }
            c = mInputBuffer[mInputPtr];
            if (c > CHAR_SPACE) { // not WS? Need to return
                return true;
            }
            ++mInputPtr;
        }
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Abstract method implementations
    ///////////////////////////////////////////////////////////////////////
     */

    @Override
    protected EntityDecl findEntity(String id, Object arg)
        throws XMLStreamException
    {
        EntityDecl ed = mConfig.findCustomInternalEntity(id);
        if (ed == null && mGeneralEntities != null) {
            ed = mGeneralEntities.get(id);
        }
        /* 05-Mar-2006, TSa: Externally declared entities are illegal
         *   if we were declared as "standalone='yes'"...
         */
        if (mDocStandalone == DOC_STANDALONE_YES) {
            if (ed != null && ed.wasDeclaredExternally()) {
                throwParseError(ErrorConsts.ERR_WF_ENTITY_EXT_DECLARED, ed.getName(), null);
            }
        }
        return ed;
    }

    @Override
    protected void handleUndeclaredEntity(String id)
        throws XMLStreamException
    {
        throwParseError(((mDocStandalone == DOC_STANDALONE_YES) ?
                        ErrorConsts.ERR_WF_GE_UNDECLARED_SA :
                        ErrorConsts.ERR_WF_GE_UNDECLARED),
                        id, null);
    }

    @Override
    protected void handleIncompleteEntityProblem(WstxInputSource closing)
        throws XMLStreamException
    {
        String top = mElementStack.isEmpty() ? "[ROOT]" : mElementStack.getTopElementDesc();
        throwParseError("Unexpected end of entity expansion for entity &{0}; was expecting a close tag for element <{1}>",
                        closing.getEntityId(), top);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Internal methods, validation, error handling and reporting
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * This problem gets reported if an entity tries to expand to
     * a close tag matching start tag that did not came from the same
     * entity (but from parent).
     */
    protected void handleGreedyEntityProblem(WstxInputSource input)
        throws XMLStreamException
    {
        String top = mElementStack.isEmpty() ? "[ROOT]" : mElementStack.getTopElementDesc();
        throwParseError("Improper GE/element nesting: entity &"
                        +input.getEntityId()+" contains closing tag for <"+top+">");
    }

    private void throwNotTextual(int type) {
        throw new IllegalStateException("Not a textual event ("
                +tokenTypeDesc(type)+")");
    }

    private void throwNotTextXxx(int type) {
        throw new IllegalStateException("getTextXxx() methods can not be called on "
                +tokenTypeDesc(type));
    }

    protected void throwNotTextualOrElem(int type) {
        throw new IllegalStateException(MessageFormat.format(ErrorConsts.ERR_STATE_NOT_ELEM_OR_TEXT,
                new Object[] { tokenTypeDesc(type) }));
    }

    /**
     * Method called when we get an EOF within content tree
     */
    protected void throwUnexpectedEOF() throws WstxException {
        throwUnexpectedEOF("; was expecting a close tag for element <"+mElementStack.getTopElementDesc()+">");
    }

    /**
     * Method called to report a problem with 
     */
    protected XMLStreamException _constructUnexpectedInTyped(int nextToken) {
        if (nextToken == START_ELEMENT) {
            return _constructTypeException("Element content can not contain child START_ELEMENT when using Typed Access methods", null);
        }
        return _constructTypeException("Expected a text token, got "+tokenTypeDesc(nextToken), null);
    }

    protected TypedXMLStreamException _constructTypeException(String msg, String lexicalValue) {
        return new TypedXMLStreamException(lexicalValue, msg, getStartLocation());
    }

    /**
     * Stub method implemented by validating parsers, to report content
     * that's not valid for current element context. Defined at this
     * level since some such problems need to be caught at low-level;
     * however, details of error reports are not needed here.
     * 
     * @param evtType Type of event that contained unexpected content
     */
    protected void reportInvalidContent(int evtType) throws XMLStreamException {
        // should never happen; sub-class has to override:
        throwParseError("Internal error: sub-class should override method");
    }
}
