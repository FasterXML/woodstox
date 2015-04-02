/*
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

package com.ctc.wstx.sax;

import java.io.*;
import java.net.URL;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.stream.Location;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.ext.Attributes2;
import org.xml.sax.ext.DeclHandler;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.ext.Locator2;

//import org.codehaus.stax2.DTDInfo;










































































import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.dtd.DTDEventListener;
import com.ctc.wstx.exc.WstxIOException;
import com.ctc.wstx.io.DefaultInputResolver;
import com.ctc.wstx.io.InputBootstrapper;
import com.ctc.wstx.io.ReaderBootstrapper;
import com.ctc.wstx.io.StreamBootstrapper;
import com.ctc.wstx.io.SystemId;
import com.ctc.wstx.sr.*;
import com.ctc.wstx.stax.WstxInputFactory;
import com.ctc.wstx.util.ExceptionUtil;
import com.ctc.wstx.util.URLUtil;

/**
 * This class implements parser part of JAXP and SAX interfaces; and
 * effectively offers an alternative to using Stax input factory /
 * stream reader combination.
 */
@SuppressWarnings("deprecation")
public class WstxSAXParser
    extends SAXParser
    implements Parser // SAX1
               ,XMLReader // SAX2
               ,Attributes2 // SAX2
               ,Locator2 // SAX2
               ,DTDEventListener // Woodstox-internal
{
    final static boolean FEAT_DEFAULT_NS_PREFIXES = false;

    /**
     * We will need the factory reference mostly for constructing
     * underlying stream reader we use.
     */
    protected final WstxInputFactory mStaxFactory;

    protected final ReaderConfig mConfig;

    protected boolean mFeatNsPrefixes;

    /**
     * Since the stream reader would mostly be just a wrapper around
     * the underlying scanner (its main job is to implement Stax
     * interface), we can and should just use the scanner. In effect,
     * this class is then a replacement of BasicStreamReader, when
     * using SAX interfaces.
     */
    protected BasicStreamReader mScanner;

    protected AttributeCollector mAttrCollector;

    protected InputElementStack mElemStack;

    // // // Info from xml declaration

    protected String mEncoding;
    protected String mXmlVersion;
    protected boolean mStandalone;

    // // // Listeners attached:

    protected ContentHandler mContentHandler;
    protected DTDHandler mDTDHandler;
    private EntityResolver mEntityResolver;
    private ErrorHandler mErrorHandler;

    private LexicalHandler mLexicalHandler;
    private DeclHandler mDeclHandler;

    // // // State:

    /**
     * Number of attributes accessible via {@link Attributes} and
     * {@link Attributes2} interfaces, for the current start element.
     *<p>
     * Note: does not include namespace declarations, even they are to
     * be reported as attributes.
     */
    protected int mAttrCount;

    /**
     * Need to keep track of number of namespaces, if namespace declarations
     * are to be reported along with attributes (see
     * {@link #mFeatNsPrefixes}).
     */
    protected int mNsCount = 0;

    /*
    /////////////////////////////////////////////////
    // Life-cycle
    /////////////////////////////////////////////////
     */

    /**
     *<p>
     * NOTE: this was a protected constructor for versions 4.0
     * and 3.2; changed to public in 4.1
     */
    public WstxSAXParser(WstxInputFactory sf, boolean nsPrefixes)
    {
        mStaxFactory = sf;
        mFeatNsPrefixes = nsPrefixes;
        mConfig = sf.createPrivateConfig();
        mConfig.doSupportDTDs(true);
        /* Lazy parsing is a tricky thing: although most of the time
         * it's useless with SAX, it is actually necessary to be able
         * to properly model internal DTD subsets, for example. So,
         * we can not really easily determine defaults.
         */
        ResolverProxy r = new ResolverProxy();
        /* SAX doesn't distinguish between DTD (ext. subset, PEs) and
         * entity (external general entities) resolvers, so let's
         * assign them both:
         */
        mConfig.setDtdResolver(r);
        mConfig.setEntityResolver(r);
        mConfig.setDTDEventListener(this);

        /* These settings do NOT make sense as generic defaults, but
         * are helpful when using some test frameworks. Specifically,
         * - DTD caching may remove calls to resolvers, changing
         *   observed behavior
         * - Using min. segment length of 1 will force flushing of
         *   all content before entity expansion, which will
         *   completely serialize entity resolution calls wrt.
         *   CHARACTERS events.
         */
        // !!! ONLY for testing; never remove for prod use
        //mConfig.setShortestReportedTextSegment(1);
        //mConfig.doCacheDTDs(false);
    }

    /*
     * This constructor is provided for two main use cases: testing,
     * and introspection via SAX classes (as opposed to JAXP-based
     * introspection).
     */
    public WstxSAXParser()
    {
        this(new WstxInputFactory(), FEAT_DEFAULT_NS_PREFIXES);
    }

    @Override
    public final Parser getParser() {
        return this;
    }

    @Override
    public final XMLReader getXMLReader() {
        return this;
    }

    /**
     * Accessor used to allow configuring all standard Stax configuration
     * settings that the underlying reader uses.
     *
     * @since 4.0.8
     */
    public final ReaderConfig getStaxConfig() {
        return mConfig;
    }

    /*
    /////////////////////////////////////////////////
    // Configuration, SAXParser
    /////////////////////////////////////////////////
     */

    @Override
    public boolean isNamespaceAware() {
        return mConfig.willSupportNamespaces();
    }

    @Override
    public boolean isValidating() {
        return mConfig.willValidateWithDTD();
    }

    @Override
    public Object getProperty(String name)
        throws SAXNotRecognizedException, SAXNotSupportedException
    {
        SAXProperty prop = SAXProperty.findByUri(name);
        if (prop == SAXProperty.DECLARATION_HANDLER) {
            return mDeclHandler;
        } else if (prop == SAXProperty.DOCUMENT_XML_VERSION) {
            return mXmlVersion;
        } else if (prop == SAXProperty.DOM_NODE) {
            return null;
        } else if (prop == SAXProperty.LEXICAL_HANDLER) {
            return mLexicalHandler;
        } else if (prop == SAXProperty.XML_STRING) {
            return null;
        }

        throw new SAXNotRecognizedException("Property '"+name+"' not recognized");
    }

    @Override
    public void setProperty(String name, Object value)
        throws SAXNotRecognizedException, SAXNotSupportedException
    {
        SAXProperty prop = SAXProperty.findByUri(name);
        if (prop == SAXProperty.DECLARATION_HANDLER) {
            mDeclHandler = (DeclHandler) value;
            return;
        } else if (prop == SAXProperty.DOCUMENT_XML_VERSION) {
            ; // read-only
        } else if (prop == SAXProperty.DOM_NODE) {
            ; // read-only
        } else if (prop == SAXProperty.LEXICAL_HANDLER) {
            mLexicalHandler = (LexicalHandler) value;
            return;
        } else if (prop == SAXProperty.XML_STRING) {
            ; // read-only
        } else {
            throw new SAXNotRecognizedException("Property '"+name+"' not recognized");
        }

        // Trying to modify read-only properties?
        throw new SAXNotSupportedException("Property '"+name+"' is read-only, can not be modified");
    }

    /*
    /////////////////////////////////////////////////
    // Overrides, SAXParser
    /////////////////////////////////////////////////
     */

    /* Have to override some methods from SAXParser; JDK
     * implementation is sucky, as it tries to override
     * many things it really should not...
     */

    @Override
    public void parse(InputSource is, HandlerBase hb)
        throws SAXException, IOException
    {
        if (hb != null) {
            /* Ok: let's ONLY set if there are no explicit sets... not
             * extremely clear, but JDK tries to set them always so
             * let's at least do damage control.
             */
            if (mContentHandler == null) {
                setDocumentHandler(hb);
            }
            if (mEntityResolver == null) {
                setEntityResolver(hb);
            }
            if (mErrorHandler == null) {
                setErrorHandler(hb);
            }
            if (mDTDHandler == null) {
                setDTDHandler(hb);
            }
        }
        parse(is);
    }

    @Override
    public void parse(InputSource is, DefaultHandler dh)
        throws SAXException, IOException
    {
        if (dh != null) {
            /* Ok: let's ONLY set if there are no explicit sets... not
             * extremely clear, but JDK tries to set them always so
             * let's at least do damage control.
             */
            if (mContentHandler == null) {
                setContentHandler(dh);
            }
            if (mEntityResolver == null) {
                setEntityResolver(dh);
            }
            if (mErrorHandler == null) {
                setErrorHandler(dh);
            }
            if (mDTDHandler == null) {
                setDTDHandler(dh);
            }
        }
        parse(is);
    }

    /*
    /////////////////////////////////////////////////////
    // XLMReader (SAX2) implementation: cfg access
    /////////////////////////////////////////////////////
    */

    @Override
    public ContentHandler getContentHandler() {
        return mContentHandler;
    }

    @Override
    public DTDHandler getDTDHandler() {
        return mDTDHandler;
    }

    @Override
    public EntityResolver getEntityResolver() {
        return mEntityResolver;
    }

    @Override
    public ErrorHandler getErrorHandler() {
        return mErrorHandler;
    }

    @Override
    public boolean getFeature(String name)
        throws SAXNotRecognizedException
    {
        SAXFeature stdFeat = SAXFeature.findByUri(name);

        if (stdFeat == SAXFeature.EXTERNAL_GENERAL_ENTITIES) {
            return mConfig.willSupportExternalEntities();
        } else if (stdFeat == SAXFeature.EXTERNAL_PARAMETER_ENTITIES) {
            return mConfig.willSupportExternalEntities();
        } else if (stdFeat == SAXFeature.IS_STANDALONE) {
            return mStandalone;
        } else if (stdFeat == SAXFeature.LEXICAL_HANDLER_PARAMETER_ENTITIES) {
            // !!! TODO:
            return false;
        } else if (stdFeat == SAXFeature.NAMESPACES) {
            return mConfig.willSupportNamespaces();
        } else if (stdFeat == SAXFeature.NAMESPACE_PREFIXES) {
            return !mConfig.willSupportNamespaces();
        } else if (stdFeat == SAXFeature.RESOLVE_DTD_URIS) {
            // !!! TODO:
            return false;
        } else if (stdFeat == SAXFeature.STRING_INTERNING) {
            return true;
        } else if (stdFeat == SAXFeature.UNICODE_NORMALIZATION_CHECKING) {
            return false;
        } else if (stdFeat == SAXFeature.USE_ATTRIBUTES2) {
            return true;
        } else if (stdFeat == SAXFeature.USE_LOCATOR2) {
            return true;
        } else if (stdFeat == SAXFeature.USE_ENTITY_RESOLVER2) {
            return true;
        } else if (stdFeat == SAXFeature.VALIDATION) {
            return mConfig.willValidateWithDTD();
        } else if (stdFeat == SAXFeature.XMLNS_URIS) {
            /* !!! TODO: default value should be false... but not sure
             *   if implementing that mode makes sense
             */
            return true;
        } else if (stdFeat == SAXFeature.XML_1_1) {
            return true;
        }

        throw new SAXNotRecognizedException("Feature '"+name+"' not recognized");
    }

    // Already implemented for SAXParser
    //public Object getProperty(String name)

    /*
    /////////////////////////////////////////////////////
    // XLMReader (SAX2) implementation: cfg changing
    /////////////////////////////////////////////////////
    */

    @Override
    public void setContentHandler(ContentHandler handler) {
        mContentHandler = handler;
    }

    @Override
    public void setDTDHandler(DTDHandler handler) {
        mDTDHandler = handler;
    }

    @Override
    public void setEntityResolver(EntityResolver resolver) {
        mEntityResolver = resolver;
    }

    @Override
    public void setErrorHandler(ErrorHandler handler) {
        mErrorHandler = handler;
    }

    @Override
    public void setFeature(String name, boolean value)
        throws SAXNotRecognizedException, SAXNotSupportedException
    {
        boolean invalidValue = false;
        boolean readOnly = false;
        SAXFeature stdFeat = SAXFeature.findByUri(name);

        if (stdFeat == SAXFeature.EXTERNAL_GENERAL_ENTITIES) {
            mConfig.doSupportExternalEntities(value);
        } else if (stdFeat == SAXFeature.EXTERNAL_PARAMETER_ENTITIES) {
            // !!! TODO
        } else if (stdFeat == SAXFeature.IS_STANDALONE) {
            readOnly = true;
        } else if (stdFeat == SAXFeature.LEXICAL_HANDLER_PARAMETER_ENTITIES) {
            // !!! TODO
        } else if (stdFeat == SAXFeature.NAMESPACES) {
            mConfig.doSupportNamespaces(value);
        } else if (stdFeat == SAXFeature.NAMESPACE_PREFIXES) {
            mFeatNsPrefixes = value;
        } else if (stdFeat == SAXFeature.RESOLVE_DTD_URIS) {
            // !!! TODO
        } else if (stdFeat == SAXFeature.STRING_INTERNING) {
            invalidValue = !value;
        } else if (stdFeat == SAXFeature.UNICODE_NORMALIZATION_CHECKING) {
            invalidValue = value;
        } else if (stdFeat == SAXFeature.USE_ATTRIBUTES2) {
            readOnly = true;
        } else if (stdFeat == SAXFeature.USE_LOCATOR2) {
            readOnly = true;
        } else if (stdFeat == SAXFeature.USE_ENTITY_RESOLVER2) {
            readOnly = true;
        } else if (stdFeat == SAXFeature.VALIDATION) {
            mConfig.doValidateWithDTD(value);
        } else if (stdFeat == SAXFeature.XMLNS_URIS) {
            invalidValue = !value;
        } else if (stdFeat == SAXFeature.XML_1_1) {
            readOnly = true;
        } else {
            throw new SAXNotRecognizedException("Feature '"+name+"' not recognized");
        }

        // Trying to modify read-only properties?
        if (readOnly) {
            throw new SAXNotSupportedException("Feature '"+name+"' is read-only, can not be modified");
        }
        if (invalidValue) {
            throw new SAXNotSupportedException("Trying to set invalid value for feature '"+name+"', '"+value+"'");
        }
    }

    // Already implemented for SAXParser
    //public void setProperty(String name, Object value) 

    /*
    /////////////////////////////////////////////////////
    // XLMReader (SAX2) implementation: parsing
    /////////////////////////////////////////////////////
    */

    @SuppressWarnings("resource")
    @Override
    public void parse(InputSource input) throws SAXException
    {
        mScanner = null;
        String sysIdStr = input.getSystemId();
        ReaderConfig cfg = mConfig;
        URL srcUrl = null;

        // Let's figure out input, first, before sending start-doc event
        InputStream is = null;
        Reader r = input.getCharacterStream();
        if (r == null) {
            is = input.getByteStream();
            if (is == null) {
                if (sysIdStr == null) {
                    throw new SAXException("Invalid InputSource passed: neither character or byte stream passed, nor system id specified");
                }
                try {
                    srcUrl = URLUtil.urlFromSystemId(sysIdStr);
                    is = URLUtil.inputStreamFromURL(srcUrl);
                } catch (IOException ioe) {
                    SAXException saxe = new SAXException(ioe);
                    ExceptionUtil.setInitCause(saxe, ioe);
                    throw saxe;
                }
            }
        }

        if (mContentHandler != null) {
            mContentHandler.setDocumentLocator(this);
            mContentHandler.startDocument();
        }

        /* Note: since we are reusing the same config instance, need to
         * make sure state is not carried forward. Thus:
         */
        cfg.resetState();

        try {
            String inputEnc = input.getEncoding();
            String publicId = input.getPublicId();

            // Got an InputStream and encoding? Can create a Reader:
            if (r == null && (inputEnc != null && inputEnc.length() > 0)) {
                r = DefaultInputResolver.constructOptimizedReader(cfg, is, false, inputEnc);
            }
            InputBootstrapper bs;
            SystemId systemId = SystemId.construct(sysIdStr, srcUrl);
            if (r != null) {
                bs = ReaderBootstrapper.getInstance(publicId, systemId, r, inputEnc);
                // false -> not for event reader; false -> no auto-closing
                mScanner = (BasicStreamReader) mStaxFactory.createSR(cfg, systemId, bs, false, false);
            } else {
                bs = StreamBootstrapper.getInstance(publicId, systemId, is);
                mScanner = (BasicStreamReader) mStaxFactory.createSR(cfg, systemId, bs, false, false);
            }

            // Need to get xml declaration stuff out now:
            {
                String enc2 = mScanner.getEncoding();
                if (enc2 == null) {
                    enc2 = mScanner.getCharacterEncodingScheme();
                }
                mEncoding = enc2;
            }
            mXmlVersion = mScanner.getVersion();
            mStandalone = mScanner.standaloneSet();
            mAttrCollector = mScanner.getAttributeCollector();
            mElemStack = mScanner.getInputElementStack();
            fireEvents();
        } catch (IOException io) {
            throwSaxException(io);
        } catch (XMLStreamException strex) {
            throwSaxException(strex);
        } finally {
            if (mContentHandler != null) {
                mContentHandler.endDocument();
            }
            // Could try holding onto the buffers, too... but
            // maybe it's better to allow them to be reclaimed, if
            // needed by GC
            if (mScanner != null) {
                BasicStreamReader sr = mScanner;
                mScanner = null;
                try {
                    sr.close();
                } catch (XMLStreamException sex) { }
            }
            if (r != null) {
                try {
                    r.close();
                } catch (IOException ioe) { }
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ioe) { }
            }
        }
    }

    @Override
    public void parse(String systemId) throws SAXException
    {
        InputSource src = new InputSource(systemId);
        parse(src);
    }

    /*
    /////////////////////////////////////////////////
    // Parsing loop, helper methods
    /////////////////////////////////////////////////
     */

    /**
     * This is the actual "tight event loop" that will send all events
     * between start and end document events. Although we could
     * use the stream reader here, there's not much as it mostly
     * just forwards requests to the scanner: and so we can as well
     * just copy the little code stream reader's next() method has.
     */
    private final void fireEvents()
        throws IOException, SAXException, XMLStreamException
    {
        // First we are in prolog:
        int type;

        /* Need to enable lazy parsing, to get DTD start events before
         * its content events. Plus, can skip more efficiently too.
         */
        mConfig.doParseLazily(false);

        while ((type = mScanner.next()) != XMLStreamConstants.START_ELEMENT) {
            fireAuxEvent(type, false);
        }

        // Now just starting the tree, need to process the START_ELEMENT
        fireStartTag();

        int depth = 1;
        while (true) {
            type = mScanner.next();
            if (type == XMLStreamConstants.START_ELEMENT) {
                fireStartTag();
                ++depth;
            } else if (type == XMLStreamConstants.END_ELEMENT) {
                mScanner.fireSaxEndElement(mContentHandler);
                if (--depth < 1) {
                    break;
                }
            } else if (type == XMLStreamConstants.CHARACTERS) {
                mScanner.fireSaxCharacterEvents(mContentHandler);
            } else {
                fireAuxEvent(type, true);
            }
        }

        // And then epilog:
        while (true) {
            type = mScanner.next();
            if (type == XMLStreamConstants.END_DOCUMENT) {
                break;
            }
            if (type == XMLStreamConstants.SPACE) {
                // Not to be reported via SAX interface (which may or may not
                // be different from Stax)
                continue;
            }
            fireAuxEvent(type, false);
        }
    }

    private final void fireAuxEvent(int type, boolean inTree)
        throws IOException, SAXException, XMLStreamException
    {
        switch (type) {
        case XMLStreamConstants.COMMENT:
            mScanner.fireSaxCommentEvent(mLexicalHandler);
            break;
        case XMLStreamConstants.CDATA:
            if (mLexicalHandler != null) {
                mLexicalHandler.startCDATA();
                mScanner.fireSaxCharacterEvents(mContentHandler);
                mLexicalHandler.endCDATA();
            } else {
                mScanner.fireSaxCharacterEvents(mContentHandler);
            }
            break;
        case XMLStreamConstants.DTD:
            if (mLexicalHandler != null) {
                /* Note: this is bit tricky, since calling getDTDInfo() will
                 * trigger full reading of the subsets... but we need to
                 * get some info first, to be able to send dtd-start event,
                 * and only then get the rest. Thus, need to call separate
                 * accessors first:
                 */
                String rootName = mScanner.getDTDRootName();
                String sysId = mScanner.getDTDSystemId();
                String pubId = mScanner.getDTDPublicId();
                mLexicalHandler.startDTD(rootName, pubId, sysId);
                // Ok, let's get rest (if any) read:
                try {
                    /*DTDInfo dtdInfo =*/ mScanner.getDTDInfo();
                } catch (WrappedSaxException wse) {
                    throw wse.getSaxException();
                }
                mLexicalHandler.endDTD();
            }
            break;
        case XMLStreamConstants.PROCESSING_INSTRUCTION:
            mScanner.fireSaxPIEvent(mContentHandler);
            break;
        case XMLStreamConstants.SPACE:
            // With SAX, only to be sent as an event if inside the
            // tree, not from within prolog/epilog
            if (inTree) {
                mScanner.fireSaxSpaceEvents(mContentHandler);
            }
            break;
        case XMLStreamConstants.ENTITY_REFERENCE:
            /* Only occurs in non-entity-expanding mode; so effectively
             * we are skipping the entity?
             */
            if (mContentHandler != null) {
                mContentHandler.skippedEntity(mScanner.getLocalName());
            }
            break;
        default:
            if (type == XMLStreamConstants.END_DOCUMENT) {
                throwSaxException("Unexpected end-of-input in "+(inTree ? "tree" : "prolog"));
            }
            throw new RuntimeException("Internal error: unexpected type, "+type);
        }
    }

    private final void fireStartTag()
        throws SAXException
    {
        mAttrCount = mAttrCollector.getCount();
        if (mFeatNsPrefixes) {
            /* 15-Dec-2006, TSa: Note: apparently namespace bindings that
             *    are added via defaulting are only visible via element
             *    stack. Thus, we MUST access things via element stack,
             *    not attribute collector; even though latter seems like
             *    the more direct route. See
             *    {@link NsInputElementStack#addNsBinding} for the method
             *    that injects such special namespace bindings (yes, it's
             *    a hack, afterthought)
             */
            //mNsCount = mAttrCollector.getNsCount();
            mNsCount = mElemStack.getCurrentNsCount();
        }
        mScanner.fireSaxStartElement(mContentHandler, this);
    }

    /*
    /////////////////////////////////////////////////
    // Parser (SAX1) implementation
    /////////////////////////////////////////////////
     */

    // Already implemented for XMLReader:
    //public void parse(InputSource source)
    //public void parse(String systemId)
    //public void setEntityResolver(EntityResolver resolver)
    //public void setErrorHandler(ErrorHandler handler)

    @Override
    public void setDocumentHandler(DocumentHandler handler) {
        setContentHandler(new DocHandlerWrapper(handler));
    }

    @Override
    public void setLocale(java.util.Locale locale)  {
        // Not supported, let's just ignore
    }

    /*
    /////////////////////////////////////////////////////
    // Attributes (SAX2) implementation
    /////////////////////////////////////////////////////
    */

    @Override
    public int getIndex(String qName)
    {
        if (mElemStack == null) {
            return -1;
        }
        int ix = mElemStack.findAttributeIndex(null, qName);
        // !!! In ns-as-attrs mode, should also match ns decls?
        return ix;
    }

    @Override
    public int getIndex(String uri, String localName)
    {
        if (mElemStack == null) {
            return -1;
        }
        int ix = mElemStack.findAttributeIndex(uri, localName);
        // !!! In ns-as-attrs mode, should also match ns decls?
        return ix;
    }

    @Override
    public int getLength()
    {
        return mAttrCount + mNsCount;
    }

    @Override
    public String getLocalName(int index)
    {
        if (index < mAttrCount) {
            return (index < 0) ? null : mAttrCollector.getLocalName(index);
        }
        index -= mAttrCount;
        if (index < mNsCount) {
            /* As discussed in <code>fireStartTag</code>, we must use
             * element stack, not attribute collector:
             */
            //String prefix = mAttrCollector.getNsPrefix(index);
            String prefix = mElemStack.getLocalNsPrefix(index);
            return (prefix == null || prefix.length() == 0) ?
                "xmlns" : prefix;
        }
        return null;
    }

    @Override
    public String getQName(int index)
    {
        if (index < mAttrCount) {
            if (index < 0) {
                return null;
            }
            String prefix = mAttrCollector.getPrefix(index);
            String ln = mAttrCollector.getLocalName(index);
            return (prefix == null || prefix.length() == 0) ?
                ln : (prefix + ":" + ln);
        }
        index -= mAttrCount;
        if (index < mNsCount) {
            /* As discussed in <code>fireStartTag</code>, we must use
             * element stack, not attribute collector:
             */
            //String prefix = mAttrCollector.getNsPrefix(index);
            String prefix = mElemStack.getLocalNsPrefix(index);
            if (prefix == null || prefix.length() == 0) {
                return "xmlns";
            }
            return "xmlns:"+prefix;
        }
        return null;
    }

    @Override
    public String getType(int index)
    {
        if (index < mAttrCount) {
            if (index < 0) {
                return null;
            }
            /* Note: Woodstox will have separate type for enumerated values;
             * SAX considers these NMTOKENs, so may need to convert (but
             * note: some SAX impls also use "ENUMERATED")
             */
            String type = mElemStack.getAttributeType(index);
            // Let's count on it being interned:
            if (type == "ENUMERATED") {
                type = "NMTOKEN";
            }
            return type;
        }
        // But how about namespace declarations... let's just call them CDATA?
        index -= mAttrCount;
        if (index < mNsCount) {
            return "CDATA";
        }
        return null;
    }

    @Override
    public String getType(String qName) {
        return getType(getIndex(qName));
    }

    @Override
    public String getType(String uri, String localName) {
        return getType(getIndex(uri, localName));
    }

    @Override
    public String getURI(int index)
    {
        if (index < mAttrCount) {
            if (index < 0) {
                return null;
            }
            String uri = mAttrCollector.getURI(index);
            return (uri == null) ? "" : uri;
        }
        if ((index - mAttrCount) < mNsCount) {
            return XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
        }
        return null;
    }

    @Override
    public String getValue(int index)
    {
        if (index < mAttrCount) {
            return (index < 0) ? null : mAttrCollector.getValue(index);
        }
        index -= mAttrCount;
        if (index < mNsCount) {
            /* As discussed in <code>fireStartTag</code>, we must use
             * element stack, not attribute collector:
             */
            //String uri = mAttrCollector.getNsURI(index);
            String uri = mElemStack.getLocalNsURI(index);
            return (uri == null) ? "" : uri;
        }
        return null;
    }

    @Override
    public String getValue(String qName) {
        return getValue(getIndex(qName));
    }

    @Override
    public String getValue(String uri, String localName)  {
        return getValue(getIndex(uri, localName));
    }

    /*
    /////////////////////////////////////////////////////
    // Attributes2 (SAX2) implementation
    /////////////////////////////////////////////////////
    */

    @Override
    public boolean isDeclared(int index)
    {
        if (index < mAttrCount) {
            if (index >= 0) {
                // !!! TODO: implement properly
                return true;
            }
        } else {
            index -= mAttrCount;
            if (index < mNsCount) {
                /* DTD and namespaces don't really play nicely together;
                 * and in general xmlns: pseudo-attributes are not declared...
                 * so not quite sure what to return here. For now, let's
                 * return true, to indicate they ought to be valid
                 */
                return true;
            }
        }
        throwNoSuchAttribute(index);
        return false; // never gets here
    }

    @Override
    public boolean isDeclared(String qName) {
        return false;
    }

    @Override
    public boolean isDeclared(String uri, String localName) {
        return false;
    }

    @Override
    public boolean isSpecified(int index)
    {
        if (index < mAttrCount) {
            if (index >= 0) {
                return mAttrCollector.isSpecified(index);
            }
        } else {
            index -= mAttrCount;
            if (index < mNsCount) {
                /* Determining default-attr - based namespace declarations
                 * would need new accessors on Woodstox... but they are
                 * extremely rare, too
                 */
                return true;
            }
        }
        throwNoSuchAttribute(index);
        return false; // never gets here
    }

    @Override
    public boolean isSpecified(String qName)
    {
        int ix = getIndex(qName);
        if (ix < 0) {
            throw new IllegalArgumentException("No attribute with qName '"+qName+"'");
        }
        return isSpecified(ix);
    }

    @Override
    public boolean isSpecified(String uri, String localName) 
    {
        int ix = getIndex(uri, localName);
        if (ix < 0) {
            throw new IllegalArgumentException("No attribute with uri "+uri+", local name '"+localName+"'");
        }
        return isSpecified(ix);
    }

    /*
    /////////////////////////////////////////////////////
    // Locator (SAX1) implementation
    /////////////////////////////////////////////////////
    */

    @Override
    public int getColumnNumber()
    {
        if (mScanner != null) {
            Location loc = mScanner.getLocation();
            return loc.getColumnNumber();
        }
        return -1;
    }

    @Override
    public int getLineNumber()
    {
        if (mScanner != null) {
            Location loc = mScanner.getLocation();
            return loc.getLineNumber();
        }
        return -1;
    }

    @Override
    public String getPublicId()
    {
        if (mScanner != null) {
            Location loc = mScanner.getLocation();
            return loc.getPublicId();
        }
        return null;
    }

    @Override
    public String getSystemId() {
        if (mScanner != null) {
            Location loc = mScanner.getLocation();
            return loc.getSystemId();
        }
        return null;
    }

    /*
    /////////////////////////////////////////////////////
    // Locator2 (SAX2) implementation
    /////////////////////////////////////////////////////
    */

    @Override
    public String getEncoding() {
        return mEncoding;
    }

    @Override
    public String getXMLVersion() {
        return mXmlVersion;
    }

    /*
    /////////////////////////////////////////////////////
    // DTDEventListener (woodstox internal API) impl
    /////////////////////////////////////////////////////
    */

    @Override
    public boolean dtdReportComments()
    {
        return (mLexicalHandler != null);
    }

    @Override
    public void dtdComment(char[] data, int offset, int len)
    {
        if (mLexicalHandler != null) {
            try {
                mLexicalHandler.comment(data, offset, len);
            } catch (SAXException sex) {
                throw new WrappedSaxException(sex);
            }
        }
    }

    @Override
    public void dtdProcessingInstruction(String target, String data)
    {
        if (mContentHandler != null) {
            try {
                mContentHandler.processingInstruction(target, data);
            } catch (SAXException sex) {
                throw new WrappedSaxException(sex);
            }
        }
    }

    @Override
    public void dtdSkippedEntity(String name)
    {
        if (mContentHandler != null) {
            try {
                mContentHandler.skippedEntity(name);
            } catch (SAXException sex) {
                throw new WrappedSaxException(sex);
            }
        }
    }

    // DTD declarations that must be exposed
    @Override
    public void dtdNotationDecl(String name, String publicId, String systemId, URL baseURL)
        throws XMLStreamException
    {
        if (mDTDHandler != null) {
            /* 24-Nov-2006, TSa: Note: SAX expects system identifiers to
             *  be fully resolved when reported...
             */
            if (systemId != null && systemId.indexOf(':') < 0) {
                try {
                    systemId = URLUtil.urlFromSystemId(systemId, baseURL).toExternalForm();
                } catch (IOException ioe) {
                    throw new WstxIOException(ioe);
                }
            }
            try {
                mDTDHandler.notationDecl(name, publicId, systemId);
            } catch (SAXException sex) {
                throw new WrappedSaxException(sex);
            }
        }
    }

    @Override
    public void dtdUnparsedEntityDecl(String name, String publicId, String systemId, String notationName, URL baseURL)
        throws XMLStreamException
    {
        if (mDTDHandler != null) {
            // SAX expects system id to be fully resolved?
            if (systemId.indexOf(':') < 0) { // relative path...
                try {
                    systemId = URLUtil.urlFromSystemId(systemId, baseURL).toExternalForm();
                } catch (IOException ioe) {
                    throw new WstxIOException(ioe);
                }
            }
            try {
                mDTDHandler.unparsedEntityDecl(name, publicId, systemId, notationName);
            } catch (SAXException sex) {
                throw new WrappedSaxException(sex);
            }
        }
    }

    // DTD declarations that can be exposed

    @Override
    public void attributeDecl(String eName, String aName, String type, String mode, String value)
    {
        if (mDeclHandler != null) {
            try {
                mDeclHandler.attributeDecl(eName, aName, type, mode, value);
            } catch (SAXException sex) {
                throw new WrappedSaxException(sex);
            }
        }
    }

    @Override
    public void dtdElementDecl(String name, String model)
    {
        if (mDeclHandler != null) {
            try {
                mDeclHandler.elementDecl(name, model);
            } catch (SAXException sex) {
                throw new WrappedSaxException(sex);
            }
        }
    }

    @Override
    public void dtdExternalEntityDecl(String name, String publicId, String systemId)
    {
        if (mDeclHandler != null) {
            try {
                mDeclHandler.externalEntityDecl(name, publicId, systemId);
            } catch (SAXException sex) {
                throw new WrappedSaxException(sex);
            }
        }
    }

    @Override
    public void dtdInternalEntityDecl(String name, String value)
    {
        if (mDeclHandler != null) {
            try {
                mDeclHandler.internalEntityDecl(name, value);
            } catch (SAXException sex) {
                throw new WrappedSaxException(sex);
            }
        }
    }

    /*
    /////////////////////////////////////////////////
    // Internal methods
    /////////////////////////////////////////////////
     */

    private void throwSaxException(Exception src)
        throws SAXException
    {
        SAXParseException se = new SAXParseException(src.getMessage(), /*(Locator)*/ this, src);
        ExceptionUtil.setInitCause(se, src);
        if (mErrorHandler != null) {
            mErrorHandler.fatalError(se);
        }
        throw se;
    }

    private void throwSaxException(String msg)
        throws SAXException
    {
        SAXParseException se = new SAXParseException(msg, /*(Locator)*/ this);
        if (mErrorHandler != null) {
            mErrorHandler.fatalError(se);
        }
        throw se;
    }

    private void throwNoSuchAttribute(int index)
    {
        throw new IllegalArgumentException("No attribute with index "+index+" (have "+(mAttrCount+mNsCount)+" attributes)");
    }

    /*
    /////////////////////////////////////////////////
    // Helper class for dealing with entity resolution
    /////////////////////////////////////////////////
     */

    /**
     * Simple helper class that converts from Stax API into SAX
     * EntityResolver call(s)
     */
    final class ResolverProxy
        implements XMLResolver
    {
        public ResolverProxy() { }

        @Override
        public Object resolveEntity(String publicID, String systemID, String baseURI, String namespace)
            throws XMLStreamException
        {
            if (mEntityResolver != null) {
                try {
                    /* Hmmh. SAX expects system id to have been mangled prior
                     * to call... this may work, depending on stax impl:
                     */
                    URL url = new URL(baseURI);
                    String ref = new URL(url, systemID).toExternalForm();
                    InputSource isrc = mEntityResolver.resolveEntity(publicID, ref);
                    if (isrc != null) {
                        //System.err.println("Debug: succesfully resolved '"+publicID+"', '"+systemID+"'");
                        InputStream in = isrc.getByteStream();
                        if (in != null) {
                            return in;
                        }
                        Reader r = isrc.getCharacterStream();
                        if (r != null) {
                            return r;
                        }
                    }

                    // Returning null should be fine, actually...
                    return null;
                } catch (IOException ex) {
                    throw new WstxIOException(ex);
                } catch (Exception ex) {
                    throw new XMLStreamException(ex.getMessage(), ex);
                }
            }
            return null;
        }
    }

    /*
    /////////////////////////////////////////////////
    // Helper classes for SAX1 support
    /////////////////////////////////////////////////
     */

    final static class DocHandlerWrapper
        implements ContentHandler
    {
        final DocumentHandler mDocHandler;

        final AttributesWrapper mAttrWrapper = new AttributesWrapper();

        DocHandlerWrapper(DocumentHandler h)
        {
            mDocHandler = h;
        }

        @Override
        public void characters(char[] ch, int start, int length)
            throws SAXException
        {
            mDocHandler.characters(ch, start, length);
        }

        @Override
        public void endDocument() throws SAXException
        {
            mDocHandler.endDocument();
        }

        @Override
        public void endElement(String uri, String localName, String qName)
            throws SAXException
        {
            if (qName == null) {
                qName = localName;
            }
            mDocHandler.endElement(qName);
        }

        @Override
        public void endPrefixMapping(String prefix)
        {
            // no equivalent in SAX1, ignore
        }

        @Override
        public void ignorableWhitespace(char[] ch, int start, int length)
            throws SAXException
        {
            mDocHandler.ignorableWhitespace(ch, start, length);
        }

        @Override
        public void processingInstruction(String target, String data)
            throws SAXException
        {
            mDocHandler.processingInstruction(target, data);
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            mDocHandler.setDocumentLocator(locator);
        }

        @Override
        public void skippedEntity(String name) {
            // no equivalent in SAX1, ignore
        }

        @Override
        public void startDocument() throws SAXException
        {
            mDocHandler.startDocument();
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                                 Attributes attrs)
            throws SAXException
        {
            if (qName == null) {
                qName = localName;
            }
            // Also, need to wrap Attributes to look like AttributeLost
            mAttrWrapper.setAttributes(attrs);
            mDocHandler.startElement(qName, mAttrWrapper);
        }

        @Override
        public void startPrefixMapping(String prefix, String uri) {
            // no equivalent in SAX1, ignore
        }
    }

    final static class AttributesWrapper
        implements AttributeList
    {
        Attributes mAttrs;

        public AttributesWrapper() { }

        public void setAttributes(Attributes a) {
            mAttrs = a;
        }

        @Override
        public int getLength() {
            return mAttrs.getLength();
        }

        @Override
        public String getName(int i) {
            String n = mAttrs.getQName(i);
            return (n == null) ? mAttrs.getLocalName(i) : n;
        }

        @Override
        public String getType(int i) {
            return mAttrs.getType(i);
        }

        @Override
        public String getType(String name) {
            return mAttrs.getType(name);
        }

        @Override
        public String getValue(int i) {
            return mAttrs.getValue(i);
        }

        @Override
        public String getValue(String name) {
            return mAttrs.getValue(name);
        }
    }
}

