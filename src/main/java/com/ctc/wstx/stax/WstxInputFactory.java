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

package com.ctc.wstx.stax;

import java.io.*;
import java.net.URL;

import javax.xml.stream.*;
import javax.xml.stream.util.XMLEventAllocator;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;

import org.xml.sax.InputSource;

import org.codehaus.stax2.XMLEventReader2;
import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.io.Stax2Source;
import org.codehaus.stax2.io.Stax2ByteArraySource;
import org.codehaus.stax2.ri.Stax2FilteredStreamReader;
import org.codehaus.stax2.ri.Stax2ReaderAdapter;
import org.codehaus.stax2.ri.evt.Stax2EventReaderAdapter;
import org.codehaus.stax2.ri.evt.Stax2FilteredEventReader;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.api.WstxInputProperties;
import com.ctc.wstx.cfg.InputConfigFlags;
import com.ctc.wstx.cfg.XmlConsts;
import com.ctc.wstx.dtd.DTDId;
import com.ctc.wstx.dtd.DTDSubset;
import com.ctc.wstx.dom.WstxDOMWrappingReader;
import com.ctc.wstx.evt.DefaultEventAllocator;
import com.ctc.wstx.evt.WstxEventReader;
import com.ctc.wstx.exc.WstxIOException;
import com.ctc.wstx.io.*;
import com.ctc.wstx.sr.ValidatingStreamReader;
import com.ctc.wstx.sr.ReaderCreator;
import com.ctc.wstx.util.DefaultXmlSymbolTable;
import com.ctc.wstx.util.SimpleCache;
import com.ctc.wstx.util.SymbolTable;
import com.ctc.wstx.util.URLUtil;

/**
 * Factory for creating various Stax objects (stream/event reader,
 * writer).
 *
 *<p>
 * Currently supported configuration options fall into two categories. First,
 * all properties from {@link XMLInputFactory} (such as, say,
 * {@link XMLInputFactory#IS_NAMESPACE_AWARE}) are at least recognized, and
 * most are supported. Second, there are additional properties, defined in
 * constant class {@link WstxInputProperties}, that are supported.
 * See {@link WstxInputProperties} for further explanation of these 'custom'
 * properties.
 *
 * @author Tatu Saloranta
 */
public class WstxInputFactory
    extends XMLInputFactory2
    implements ReaderCreator,
               InputConfigFlags
{
    /**
     * Let's limit max size to 3/4 of 16k, since this corresponds
     * to 64k main hash index. This should not be too low, but could
     * perhaps be further lowered?
     */
    final static int MAX_SYMBOL_TABLE_SIZE = 12000;

    /**
     * Number of generations should not matter as much as raw
     * size... but let's still cap it at some number. 500 generations
     * seems reasonable for flushing (note: does not count uses
     * where no new symbols were added).
     */
    final static int MAX_SYMBOL_TABLE_GENERATIONS = 500;

    /*
    ///////////////////////////////////////////////////////////
    // Actual storage of configuration settings
    ///////////////////////////////////////////////////////////
     */

    /**
     * Current configurations for this factory
     */
    protected final ReaderConfig mConfig;

    // // // Stax - mandated objects:

    protected XMLEventAllocator mAllocator = null;

    // // // Other configuration objects:

    protected SimpleCache<DTDId,DTDSubset> mDTDCache = null;

    /*
    ///////////////////////////////////////////////////////////
    // Objects shared by actual parsers
    ///////////////////////////////////////////////////////////
     */

    /**
     * 'Root' symbol table, used for creating actual symbol table instances,
     * but never as is.
     */
    final static SymbolTable mRootSymbols = DefaultXmlSymbolTable.getInstance();
    static {
        /* By default, let's enable intern()ing of names (element, attribute,
         * prefixes) added to symbol table. This is likely to make some
         * access (attr by QName) and comparison of element/attr names
         * more efficient. Although it will add some overhead on adding
         * new symbols to symbol table that should be rather negligible.
         *
         * Also note that always doing intern()ing allows for more efficient
         * access during DTD validation.
         */
        mRootSymbols.setInternStrings(true);
    }

    /**
     * Actual current 'parent' symbol table; concrete instances will be
     * created from this instance using <code>makeChild</code> method
     */
    private SymbolTable mSymbols = mRootSymbols;

    /*
    ///////////////////////////////////////////////////////////
    // Life-cycle:
    ///////////////////////////////////////////////////////////
     */

    public WstxInputFactory() {
        mConfig = ReaderConfig.createFullDefaults();
    }

    /**
     * Method that can be used to ensure that specified symbol is
     * contained in the shared symbol table. This may occasionally
     * be useful in pre-populating symbols; although it is unlikely
     * to be commonly useful.
     * 
     * @since 4.2.1
     */
    public void addSymbol(String symbol)
    {
        synchronized (mSymbols) {
            mSymbols.findSymbol(symbol);
        }
    }
    
    /*
    ///////////////////////////////////////////////////////////
    // ReaderCreator implementation
    ///////////////////////////////////////////////////////////
     */

    // // // Configuration access methods:

    /**
     * Method readers created by this factory call, if DTD caching is
     * enabled, to see if an external DTD (subset) has been parsed
     * and cached earlier.
     */
    public synchronized DTDSubset findCachedDTD(DTDId id)
    {
        return (mDTDCache == null) ? null : mDTDCache.find(id);
    }

    // // // Callbacks for updating shared information

    /**
     * Method individual parsers call to pass back symbol table that
     * they updated, which may be useful for other parser to reuse, instead
     * of previous base symbol table.
     *<p>
     * Note: parser is only to call this method, if passed-in symbol
     * table was modified, ie new entry/ies were added in addition to
     * whatever was in root table.
     */
    public synchronized void updateSymbolTable(SymbolTable t)
    {
        SymbolTable curr = mSymbols;
        /* Let's only add if table was direct descendant; this prevents
         * siblings from keeping overwriting settings (multiple direct
         * children have additional symbols added)
         */
        if (t.isDirectChildOf(curr)) {
            /* 07-Apr-2006, TSa: Actually, since huge symbol tables
             *    might become hindrance more than benefit (either in
             *    pathological cases with random names; or with very
             *    long running processes), let's actually limit both
             *    number of generations, and, more imporantly, maximum
             *    size of the symbol table
             */
            if (t.size() > MAX_SYMBOL_TABLE_SIZE || 
                t.version() > MAX_SYMBOL_TABLE_GENERATIONS) {
                // If so, we'll reset from bare defaults
                mSymbols = mRootSymbols;
//System.err.println("DEBUG: !!!! XXXXX Symbol Table Flush: size: "+t.size()+"; version: "+t.version());
            } else {
                mSymbols.mergeChild(t);
//System.err.println("Debug: new symbol table: size: "+t.size()+"; version: "+t.version());
            }
        }
//else System.err.println("Debug: skipping symbol table update");
    }

    public synchronized void addCachedDTD(DTDId id, DTDSubset extSubset)
    {
        if (mDTDCache == null) {
            mDTDCache = new SimpleCache<DTDId,DTDSubset>(mConfig.getDtdCacheSize());
        }
        mDTDCache.add(id, extSubset);
    }

    /*
    /////////////////////////////////////////////////////
    // Stax, XMLInputFactory; factory methods
    /////////////////////////////////////////////////////
     */

    // // // Filtered reader factory methods

    public XMLEventReader createFilteredReader(XMLEventReader reader, EventFilter filter)
    {
        return new Stax2FilteredEventReader(Stax2EventReaderAdapter.wrapIfNecessary(reader), filter);
    }

    public XMLStreamReader createFilteredReader(XMLStreamReader reader, StreamFilter filter)
        throws XMLStreamException
    {
        Stax2FilteredStreamReader fr = new Stax2FilteredStreamReader(reader, filter);
        /* [WSTX-111] As per Stax 1.0 TCK, apparently the filtered
         *   reader is expected to be automatically forwarded to the first
         *   acceptable event. This is different from the way RI works, but
         *   since specs don't say anything about filtered readers, let's
         *   consider TCK to be "more formal" for now, and implement that
         *   behavior.
         */
        if (!filter.accept(fr)) { // START_DOCUMENT ok?
            // Ok, nope, this should do the trick:
            fr.next();
        }
        return fr;
    }

    // // // Event reader factory methods

    public XMLEventReader createXMLEventReader(InputStream in)
        throws XMLStreamException
    {
        // false for auto-close, since caller has access to the input stream
        return new WstxEventReader(createEventAllocator(),
        		createSR(null, in, null, true, false));
    }

    public XMLEventReader createXMLEventReader(InputStream in, String enc)
        throws XMLStreamException
    {
        // false for auto-close, since caller has access to the input stream
        return new WstxEventReader(createEventAllocator(),
                                   createSR(null, in, enc, true, false));
    }

    public XMLEventReader createXMLEventReader(Reader r)
        throws XMLStreamException
    {
        // false for auto-close, since caller has access to the input stream
        return new WstxEventReader(createEventAllocator(),
                                   createSR(null, r, true, false));
    }

    public XMLEventReader createXMLEventReader(javax.xml.transform.Source source)
        throws XMLStreamException
    {
        return new WstxEventReader(createEventAllocator(),
                                   createSR(source, true));
    }

    public XMLEventReader createXMLEventReader(String systemId, InputStream in)
        throws XMLStreamException
    {
        // false for auto-close, since caller has access to the input stream
        return new WstxEventReader(createEventAllocator(),
        		createSR(SystemId.construct(systemId), in, null, true, false));
    }

    public XMLEventReader createXMLEventReader(String systemId, Reader r)
        throws XMLStreamException
    {
        // false for auto-close, since caller has access to the reader
        return new WstxEventReader(createEventAllocator(),
        		createSR(SystemId.construct(systemId), r, true, false));
    }

    public XMLEventReader createXMLEventReader(XMLStreamReader sr)
        throws XMLStreamException
    {
        return new WstxEventReader(createEventAllocator(), Stax2ReaderAdapter.wrapIfNecessary(sr));
    }

    // // // Stream reader factory methods

    public XMLStreamReader createXMLStreamReader(InputStream in)
        throws XMLStreamException
    {
        // false for auto-close, since caller has access to the input stream
        return createSR(null, in, null, false, false);
    }
    
    public XMLStreamReader createXMLStreamReader(InputStream in, String enc)
        throws XMLStreamException
    {
        // false for auto-close, since caller has access to the input stream
        return createSR(null, in, enc, false, false);
    }

    public XMLStreamReader createXMLStreamReader(Reader r)
        throws XMLStreamException
    {
        // false for auto-close, since caller has access to the reader
        return createSR(null, r, false, false);
    }

    public XMLStreamReader createXMLStreamReader(javax.xml.transform.Source src)
        throws XMLStreamException
    {
        // false -> not for event. No definition for auto-close; called method will decide
        return createSR(src, false);
    }

    public XMLStreamReader createXMLStreamReader(String systemId, InputStream in)
        throws XMLStreamException
    {
        // false for auto-close, since caller has access to the input stream
        return createSR(SystemId.construct(systemId), in, null, false, false);
    }

    public XMLStreamReader createXMLStreamReader(String systemId, Reader r)
        throws XMLStreamException
    {
        // false for auto-close, since caller has access to the Reader
        return createSR(SystemId.construct(systemId), r, false, false);
    }

    /*
    ///////////////////////////////////////////////////////////
    // Stax, XMLInputFactory; generic accessors/mutators
    ///////////////////////////////////////////////////////////
     */

    public Object getProperty(String name)
    {
        Object ob = mConfig.getProperty(name);

        if (ob == null) {
            if (name.equals(XMLInputFactory.ALLOCATOR)) {
                // Event allocator not available via J2ME subset...
                return getEventAllocator();
            }
        }
        return ob;
    }

    public void setProperty(String propName, Object value)
    {
        if (!mConfig.setProperty(propName, value)) {
            if (XMLInputFactory.ALLOCATOR.equals(propName)) {
                setEventAllocator((XMLEventAllocator) value);
            }
        }
    } 

    public XMLEventAllocator getEventAllocator() {
        return mAllocator;
    }
    
    public XMLReporter getXMLReporter() {
        return mConfig.getXMLReporter();
    }

    public XMLResolver getXMLResolver() {
        return mConfig.getXMLResolver();
    }

    public boolean isPropertySupported(String name) {
        return mConfig.isPropertySupported(name);
    }

    public void setEventAllocator(XMLEventAllocator allocator) {
        mAllocator = allocator;
    }

    public void setXMLReporter(XMLReporter r) {
        mConfig.setXMLReporter(r);
    }

    /**
     * Note: it's preferable to use Wstx-specific
     * {@link ReaderConfig#setEntityResolver}
     * instead, if possible, since this just wraps passed in resolver.
     */
    public void setXMLResolver(XMLResolver r)
    {
        mConfig.setXMLResolver(r);
    }

    /*
    ///////////////////////////////////////////////////////////
    // Stax2 implementation
    ///////////////////////////////////////////////////////////
     */

    // // // Stax2, additional factory methods:

    public XMLEventReader2 createXMLEventReader(URL src)
        throws XMLStreamException
    {
        /* true for auto-close, since caller has no access to the underlying
         * input stream created from the URL
         */
        return new WstxEventReader(createEventAllocator(),
                                   createSR(createPrivateConfig(), src, true, true));
    }

    public XMLEventReader2 createXMLEventReader(File f)
        throws XMLStreamException
    {
        /* true for auto-close, since caller has no access to the underlying
         * input stream created from the File
         */
        return new WstxEventReader(createEventAllocator(),
                                   createSR(f, true, true));
    }

    public XMLStreamReader2 createXMLStreamReader(URL src)
        throws XMLStreamException
    {
        /* true for auto-close, since caller has no access to the underlying
         * input stream created from the URL
         */
        return createSR(createPrivateConfig(), src, false, true);
    }

    /**
     * Convenience factory method that allows for parsing a document
     * stored in the specified file.
     */
    public XMLStreamReader2 createXMLStreamReader(File f)
        throws XMLStreamException
    {
        /* true for auto-close, since caller has no access to the underlying
         * input stream created from the File
         */
        return createSR(f, false, true);
    }

    // // // Stax2 "Profile" mutators

    public void configureForXmlConformance()
    {
        mConfig.configureForXmlConformance();
    }

    public void configureForConvenience()
    {
        mConfig.configureForConvenience();
    }

    public void configureForSpeed()
    {
        mConfig.configureForSpeed();
    }

    public void configureForLowMemUsage()
    {
        mConfig.configureForLowMemUsage();
    }

    public void configureForRoundTripping()
    {
        mConfig.configureForRoundTripping();
    }

    /*
    ///////////////////////////////////////////////////////////
    // Woodstox-specific configuration access
    ///////////////////////////////////////////////////////////
     */

    public ReaderConfig getConfig() {
        return mConfig;
    }

    /*
    ///////////////////////////////////////////////////////////
    // Internal methods:
    ///////////////////////////////////////////////////////////
     */

    /**
     * Bottleneck method used for creating ALL full stream reader instances
     * (via other createSR() methods and directly)
     *
     * @param forER True, if the reader is being constructed to be used
     *   by an event reader; false if it is not (or the purpose is not known)
     * @param autoCloseInput Whether the underlying input source should be
     *   actually closed when encountering EOF, or when <code>close()</code>
     *   is called. Will be true for input sources that are automatically
     *   managed by stream reader (input streams created for
     *   {@link java.net.URL} and {@link java.io.File} arguments, or when
     *   configuration settings indicate auto-closing is to be enabled
     *   (the default value is false as per Stax 1.0 specs).
     */
    private XMLStreamReader2 doCreateSR(ReaderConfig cfg, SystemId systemId,
    		InputBootstrapper bs,  boolean forER, boolean autoCloseInput)
        throws XMLStreamException
    {
        /* Automatic closing of input: will happen always for some input
         * types (ones application has no direct access to; but can also
         * be explicitly enabled.
         */
        if (!autoCloseInput) {
            autoCloseInput = cfg.willAutoCloseInput();
        }

        Reader r;
        try {
            r = bs.bootstrapInput(cfg, true, XmlConsts.XML_V_UNKNOWN);
            if (bs.declaredXml11()) {
                cfg.enableXml11(true);
            }
        } catch (IOException ie) {
            throw new WstxIOException(ie);
        }

        /* null -> no public id available
         * false -> don't close the reader when scope is closed.
         */
        BranchingReaderSource input = InputSourceFactory.constructDocumentSource
            (cfg, bs, null, systemId, r, autoCloseInput);

        return ValidatingStreamReader.createValidatingStreamReader(input, this, cfg, bs, forER);
    }

    /**
     * Method that is eventually called to create a (full) stream read
     * instance.
     *<p>
     * Note: defined as public method because it needs to be called by
     * SAX implementation.
     *
     * @param systemId System id used for this reader (if any)
     * @param bs Bootstrapper to use for creating actual underlying
     *    physical reader
     * @param forER Flag to indicate whether it will be used via
     *    Event API (will affect some configuration settings), true if it
     *    will be, false if not (or not known)
     * @param autoCloseInput Whether the underlying input source should be
     *   actually closed when encountering EOF, or when <code>close()</code>
     *   is called. Will be true for input sources that are automatically
     *   managed by stream reader (input streams created for
     *   {@link java.net.URL} and {@link java.io.File} arguments, or when
     *   configuration settings indicate auto-closing is to be enabled
     *   (the default value is false as per Stax 1.0 specs).
     */
    public XMLStreamReader2 createSR(ReaderConfig cfg, String systemId, InputBootstrapper bs,
    		boolean forER, boolean autoCloseInput)
        throws XMLStreamException
    {
        // 16-Aug-2004, TSa: Maybe we have a context?
        URL src = cfg.getBaseURL();

        // If not, maybe we can derive it from system id?
        if ((src == null) && (systemId != null && systemId.length() > 0)) {
            try {
                src = URLUtil.urlFromSystemId(systemId);
            } catch (IOException ie) {
                throw new WstxIOException(ie);
            }
        }
        return doCreateSR(cfg, SystemId.construct(systemId, src), bs, forER, autoCloseInput);
    }

    public XMLStreamReader2 createSR(ReaderConfig cfg, SystemId systemId, InputBootstrapper bs,
    		boolean forER, boolean autoCloseInput)
        throws XMLStreamException
    {
        return doCreateSR(cfg, systemId, bs, forER, autoCloseInput);
    }

    protected XMLStreamReader2 createSR(SystemId systemId, InputStream in, String enc,
    		boolean forER, boolean autoCloseInput)
        throws XMLStreamException
    {
        // sanity check:
        if (in == null) {
            throw new IllegalArgumentException("Null InputStream is not a valid argument");
        }
        ReaderConfig cfg = createPrivateConfig();
        if (enc == null || enc.length() == 0) {
            return createSR(cfg, systemId, StreamBootstrapper.getInstance
                            (null, systemId, in), forER, autoCloseInput);
        }

        /* !!! 17-Feb-2006, TSa: We don't yet know if it's xml 1.0 or 1.1;
         *   so have to specify 1.0 (which is less restrictive WRT input
         *   streams). Would be better to let bootstrapper deal with it
         *   though:
         */
        Reader r = DefaultInputResolver.constructOptimizedReader(cfg, in, false, enc);
        return createSR(cfg, systemId, ReaderBootstrapper.getInstance
                        (null, systemId, r, enc), forER, autoCloseInput);
    }

    protected XMLStreamReader2 createSR(ReaderConfig cfg, URL src,
    		boolean forER, boolean autoCloseInput)
        throws XMLStreamException
    {
        final SystemId systemId = SystemId.construct(src);
        try {
            return createSR(cfg, systemId, URLUtil.inputStreamFromURL(src),
            		forER, autoCloseInput);
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }
    }
    
    private XMLStreamReader2 createSR(ReaderConfig cfg, SystemId systemId,
    		InputStream in, boolean forER, boolean autoCloseInput)
        throws XMLStreamException
    {
        return doCreateSR(cfg, systemId,
			  StreamBootstrapper.getInstance(null, systemId, in),
			  forER, autoCloseInput);
    }

    protected XMLStreamReader2 createSR(SystemId systemId, Reader r,
    		boolean forER, boolean autoCloseInput)
        throws XMLStreamException
    {
        return createSR(createPrivateConfig(), systemId,
        		ReaderBootstrapper.getInstance
        		(null, systemId, r, null), forER, autoCloseInput);
    }

    protected XMLStreamReader2 createSR(File f, boolean forER, boolean autoCloseInput)
        throws XMLStreamException
    {
        ReaderConfig cfg = createPrivateConfig();
        try {
            /* 18-Nov-2008, TSa: If P_BASE_URL is set, and File reference is
             *   relative, let's resolve against base...
             */
            if (!f.isAbsolute()) {
                URL base = cfg.getBaseURL();
                if (base != null) {
                    URL src = new URL(base, f.getPath());
                    return createSR(cfg, SystemId.construct(src), URLUtil.inputStreamFromURL(src),
                    		forER, autoCloseInput);
                }
            }
            final SystemId systemId = SystemId.construct(URLUtil.toURL(f));
            return createSR(cfg, systemId, new FileInputStream(f), forER, autoCloseInput);

        } catch (IOException ie) {
            throw new WstxIOException(ie);
        }
    }

    /**
     * Another internal factory method, used when dealing with a generic
     * Source base type. One thing worth noting is that 'auto-closing'
     * will be enabled if the input source or Reader is constructed (and
     * thus owned) by Woodstox.
     *
     * @param forER True, if the reader is being constructed to be used
     *   by an event reader; false if it is not (or the purpose is not known)
     */
    protected XMLStreamReader2 createSR(javax.xml.transform.Source src,
    		boolean forER)
        throws XMLStreamException
    {
        ReaderConfig cfg = createPrivateConfig();
        Reader r = null;
        InputStream in = null;
        String pubId = null;
        String sysId = null;
        String encoding = null;
        boolean autoCloseInput;

        InputBootstrapper bs = null;

        if (src instanceof Stax2Source) {
            Stax2Source ss = (Stax2Source) src;
            sysId = ss.getSystemId();
            pubId = ss.getPublicId();
            encoding = ss.getEncoding();

            try {
            	/* 11-Nov-2008, TSa: Let's add optimized handling for byte-block
            	 *   source
            	 */
            	if (src instanceof Stax2ByteArraySource) {
            		Stax2ByteArraySource bas = (Stax2ByteArraySource) src;
            		bs = StreamBootstrapper.getInstance(pubId, SystemId.construct(sysId), bas.getBuffer(), bas.getBufferStart(), bas.getBufferEnd());
            	} else {
            		in = ss.constructInputStream();
            		if (in == null) {
            			r = ss.constructReader();
            		}
            	}
            } catch (IOException ioe) {
                throw new WstxIOException(ioe);
            }
            /* Caller has no direct access to stream/reader, Woodstox
             * owns it and thus has to close too
             */
            autoCloseInput = true;
        } else  if (src instanceof StreamSource) {
            StreamSource ss = (StreamSource) src;
            sysId = ss.getSystemId();
            pubId = ss.getPublicId();
            in = ss.getInputStream();
            if (in == null) {
                r = ss.getReader();
            }
            /* Caller still has access to stream/reader; no need to
             * force auto-close-input
             */
            autoCloseInput = cfg.willAutoCloseInput();
        } else if (src instanceof SAXSource) {
            SAXSource ss = (SAXSource) src;
            /* 28-Jan-2006, TSa: Not a complete implementation, but maybe
             *   even this might help...
             */
            sysId = ss.getSystemId();
            InputSource isrc = ss.getInputSource();
            if (isrc != null) {
                encoding = isrc.getEncoding();
                in = isrc.getByteStream();
                if (in == null) {
                    r = isrc.getCharacterStream();
                }
            }
            /* Caller still has access to stream/reader; no need to
             * force auto-close-input
             */
            autoCloseInput = cfg.willAutoCloseInput();
        } else if (src instanceof DOMSource) {
            DOMSource domSrc = (DOMSource) src;
            // SymbolTable not used by the DOM-based 'reader':
            return WstxDOMWrappingReader.createFrom(domSrc, cfg);
        } else {
            throw new IllegalArgumentException("Can not instantiate Stax reader for XML source type "+src.getClass()+" (unrecognized type)");
        }
		if (bs == null) { // may have already created boostrapper...
		    if (r != null) { 
		    	bs = ReaderBootstrapper.getInstance(pubId, SystemId.construct(sysId), r, encoding);
		    } else if (in != null) {
		    	bs = StreamBootstrapper.getInstance(pubId, SystemId.construct(sysId), in);
		    } else if (sysId != null && sysId.length() > 0) {
		    	/* 26-Dec-2008, TSa: If we must construct URL from system id,
		    	 *   it means caller will not have access to resulting
		    	 *   stream, thus we will force auto-closing.
		    	 */
		    	autoCloseInput = true;
		    	try {
		    		return createSR(cfg, URLUtil.urlFromSystemId(sysId),
		    				forER, autoCloseInput);
		    	} catch (IOException ioe) {
		    		throw new WstxIOException(ioe);
		    	}
		    } else {
		    	throw new XMLStreamException("Can not create Stax reader for the Source passed -- neither reader, input stream nor system id was accessible; can not use other types of sources (like embedded SAX streams)");
		    }
		}
        return createSR(cfg, sysId, bs, forER, autoCloseInput);
    }

    protected XMLEventAllocator createEventAllocator() 
    {
        // Explicitly set allocate?
        if (mAllocator != null) {
            return mAllocator.newInstance();
        }

        /* Complete or fast one? Note: standard allocator is designed
         * in such a way that newInstance() need not be called (calling
         * it wouldn't do anything, anyway)
         */
        return mConfig.willPreserveLocation() ?
            DefaultEventAllocator.getDefaultInstance()
            : DefaultEventAllocator.getFastInstance();
    }

    /**
     * Method called to construct a copy of the factory's configuration
     * object, such that two will be unlinked (changes to one are not
     * reflect in the other).
     *<p>
     * Note: only public so that other woodstox components outside of
     * this package can access it.
     */
    public ReaderConfig createPrivateConfig()
    {
        return mConfig.createNonShared(mSymbols.makeChild());
    }
}
