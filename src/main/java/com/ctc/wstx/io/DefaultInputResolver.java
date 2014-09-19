package com.ctc.wstx.io;

import java.io.*;
import java.net.URL;

import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.cfg.XmlConsts;
import com.ctc.wstx.util.URLUtil;

/**
 * Static utility class that implements the entity (external DTD subset,
 * external parsed entities) resolution logics.
 */
public final class DefaultInputResolver
{
    /*
    ////////////////////////////
    // Life-cycle
    ////////////////////////////
    */

    private DefaultInputResolver() { }

    /*
    ////////////////////////////
    // Public API
    ////////////////////////////
    */

    /**
     * Basic external resource resolver implementation; usable both with
     * DTD and entity resolution.
     *
     * @param parent Input source that contains reference to be expanded.
     * @param pathCtxt Reference context to use for resolving path, if
     *   known. If null, reference context of the parent will
     *   be used; and if that is null (which is possible), the
     *   current working directory will be assumed.
     * @param entityName Name/id of the entity being expanded, if this is an
     *   entity expansion; null otherwise (for example, when resolving external
     *   subset).
     * @param publicId Public identifier of the resource, if known; null/empty
     *   otherwise. Default implementation just ignores the identifier.
     * @param systemId System identifier of the resource. Although interface
     *   allows null/empty, default implementation considers this an error.
     * @param xmlVersion Xml version as declared by the main parsed
     *   document. Currently only relevant for checking that XML 1.0 document
     *   does not include XML 1.1 external parsed entities.
     *   If XML_V_UNKNOWN, no checks will be done.
     * @param customResolver Custom resolver to use first for resolution,
     *   if any (may be null).
     * @param cfg Reader configuration object used by the parser that is
     *   resolving the entity
     *
     * @return Input source, if entity could be resolved; null if it could
     *   not be resolved. In latter case processor may use its own default
     *   resolution mechanism.
     */
    public static WstxInputSource resolveEntity
        (WstxInputSource parent, URL pathCtxt, String entityName,
         String publicId, String systemId,
         XMLResolver customResolver, ReaderConfig cfg, int xmlVersion)
        throws IOException, XMLStreamException
    {
        if (pathCtxt == null) {
            pathCtxt = parent.getSource();
            if (pathCtxt == null) {
                pathCtxt = URLUtil.urlFromCurrentDir();
            }
        }

        // Do we have a custom resolver that may be able to resolve it?
        if (customResolver != null) {
            Object source = customResolver.resolveEntity(publicId, systemId, pathCtxt.toExternalForm(), entityName);
            if (source != null) {
                return sourceFrom(parent, cfg, entityName, xmlVersion, source);
            }
        }
            
        // Have to have a system id, then...
        if (systemId == null) {
            throw new XMLStreamException("Can not resolve "
                                         +((entityName == null) ? "[External DTD subset]" : ("entity '"+entityName+"'"))+" without a system id (public id '"
                                         +publicId+"')");
        }
        URL url = URLUtil.urlFromSystemId(systemId, pathCtxt);
        return sourceFromURL(parent, cfg, entityName, xmlVersion, url, publicId);
    }

    /**
     * A very simple utility expansion method used generally when the
     * only way to resolve an entity is via passed resolver; and where
     * failing to resolve it is not fatal.
     */
    public static WstxInputSource resolveEntityUsing
        (WstxInputSource refCtxt, String entityName,
         String publicId, String systemId,
         XMLResolver resolver, ReaderConfig cfg, int xmlVersion)
        throws IOException, XMLStreamException
    {
        URL ctxt = (refCtxt == null) ? null : refCtxt.getSource();
        if (ctxt == null) {
            ctxt = URLUtil.urlFromCurrentDir();
        }
        Object source = resolver.resolveEntity(publicId, systemId, ctxt.toExternalForm(), entityName);
        return (source == null) ? null : sourceFrom(refCtxt, cfg, entityName, xmlVersion, source);
    }

    /**
     * Factory method that accepts various types of Objects, and tries to
     * create a {@link WstxInputSource} from it. Currently it's only called
     * to locate external DTD subsets, when overriding default DOCTYPE
     * declarations; not for entity expansion or for locating the main
     * document entity.
     *
     * @param parent Input source context active when resolving a new
     *    "sub-source"; usually the main document source.
     * @param refName Name of the entity to be expanded, if any; may be
     *    null (and currently always is)
     * @param o Object that should provide the new input source; non-type safe
     */
    protected static WstxInputSource sourceFrom(WstxInputSource parent,
    		ReaderConfig cfg, String refName,
    		int xmlVersion, Object o)
        throws IllegalArgumentException, IOException, XMLStreamException
    {
        if (o instanceof Source) {
            if (o instanceof StreamSource) {
                return sourceFromSS(parent, cfg, refName, xmlVersion, (StreamSource) o);
            }
            /* !!! 05-Feb-2006, TSa: Could check if SAXSource actually has
             *    stream/reader available... ?
             */
            throw new IllegalArgumentException("Can not use other Source objects than StreamSource: got "+o.getClass());
        }
        if (o instanceof URL) {
            return sourceFromURL(parent, cfg, refName, xmlVersion, (URL) o, null);
        }
        if (o instanceof InputStream) {
            return sourceFromIS(parent, cfg, refName, xmlVersion, (InputStream) o, null, null);
        }
        if (o instanceof Reader) {
            return sourceFromR(parent, cfg, refName, xmlVersion, (Reader) o, null, null);
        }
        if (o instanceof String) {
            return sourceFromString(parent, cfg, refName, xmlVersion, (String) o);
        }
        if (o instanceof File) {
            URL u = URLUtil.toURL((File) o);
            return sourceFromURL(parent, cfg, refName, xmlVersion, u, null);
        }

        throw new IllegalArgumentException("Unrecognized input argument type for sourceFrom(): "+o.getClass());
    }

    public static Reader constructOptimizedReader(ReaderConfig cfg, InputStream in, boolean isXml11, String encoding)
        throws XMLStreamException
    {
        /* 03-Jul-2005, TSa: Since Woodstox' implementations of specialized
         *   readers are faster than default JDK ones (at least for 1.4, UTF-8
         *   reader is especially fast...), let's use them if possible
         */
        /* 17-Feb-2006, TSa: These should actually go via InputBootstrapper,
         *   since BOM may need to be skipped; xml 1.0 vs. 1.1 should be
         *   checked, and so on. Given encoding could be just verified
         *   against suggested one.
         */
        int inputBufLen = cfg.getInputBufferLength();
        String normEnc = CharsetNames.normalize(encoding);
        BaseReader r;

	// All of these use real InputStream, and use recyclable buffer
	boolean recycleBuffer = true;
        if (normEnc == CharsetNames.CS_UTF8) {
            r = new UTF8Reader(cfg, in, cfg.allocFullBBuffer(inputBufLen), 0, 0, recycleBuffer);
        } else if (normEnc == CharsetNames.CS_ISO_LATIN1) {
            r = new ISOLatinReader(cfg, in, cfg.allocFullBBuffer(inputBufLen), 0, 0, recycleBuffer);
        } else if (normEnc == CharsetNames.CS_US_ASCII) {
            r = new AsciiReader(cfg, in, cfg.allocFullBBuffer(inputBufLen), 0, 0, recycleBuffer);
        } else if (normEnc.startsWith(CharsetNames.CS_UTF32)) {
            boolean isBE = (normEnc == CharsetNames.CS_UTF32BE);
            r = new UTF32Reader(cfg, in, cfg.allocFullBBuffer(inputBufLen), 0, 0, recycleBuffer, isBE);
        } else {
            try {
                return new InputStreamReader(in, encoding);
            } catch (UnsupportedEncodingException ex) {
                throw new XMLStreamException("[unsupported encoding]: "+ex);
            }
        }

        if (isXml11) { // only need to set if we are xml 1.1 compliant (1.0 is the default)
            r.setXmlCompliancy(XmlConsts.XML_V_11);
        }

        return r;
    }

    /*
    ////////////////////////////
    // Internal methods
    ////////////////////////////
    */

    @SuppressWarnings("resource")
    private static WstxInputSource sourceFromSS(WstxInputSource parent, ReaderConfig cfg,
    		String refName, int xmlVersion, StreamSource ssrc)
        throws IOException, XMLStreamException
    {
        InputBootstrapper bs;
        Reader r = ssrc.getReader();
        String pubId = ssrc.getPublicId();
        String sysId0 = ssrc.getSystemId();
        URL ctxt = (parent == null) ? null : parent.getSource();
        URL url = (sysId0 == null || sysId0.length() == 0) ? null
            : URLUtil.urlFromSystemId(sysId0, ctxt);

        final SystemId systemId = SystemId.construct(sysId0, (url == null) ? ctxt : url);
        
        if (r == null) {
            InputStream in = ssrc.getInputStream();
            if (in == null) { // Need to try just resolving the system id then
                if (url == null) {
                    throw new IllegalArgumentException("Can not create Stax reader for a StreamSource -- neither reader, input stream nor system id was set.");
                }
                in = URLUtil.inputStreamFromURL(url);
            }
            bs = StreamBootstrapper.getInstance(pubId, systemId, in);
        } else {
            bs = ReaderBootstrapper.getInstance(pubId, systemId, r, null);
        }
        
        Reader r2 = bs.bootstrapInput(cfg, false, xmlVersion);
        return InputSourceFactory.constructEntitySource
            (cfg, parent, refName, bs, pubId, systemId, xmlVersion, r2);
    }

    @SuppressWarnings("resource")
    private static WstxInputSource sourceFromURL(WstxInputSource parent, ReaderConfig cfg,
            String refName, int xmlVersion,
            URL url,
            String pubId)
        throws IOException, XMLStreamException
    {
        /* And then create the input source. Note that by default URL's
         * own input stream creation creates buffered reader -- for us
         * that's useless and wasteful (adds one unnecessary level of
         * caching, halving the speed due to copy operations needed), so
         * let's avoid it.
         */
        InputStream in = URLUtil.inputStreamFromURL(url);
        SystemId sysId = SystemId.construct(url);
        StreamBootstrapper bs = StreamBootstrapper.getInstance(pubId, sysId, in);
        Reader r = bs.bootstrapInput(cfg, false, xmlVersion);
        return InputSourceFactory.constructEntitySource
            (cfg, parent, refName, bs, pubId, sysId, xmlVersion, r);
    }

    /**
     * We have multiple ways to look at what would it mean to get a String
     * as the resolved result. The most straight-forward is to consider
     * it literal replacement (with possible embedded entities), so let's
     * use that (alternative would be to consider it to be a reference
     * like URL -- those need to be returned as appropriate objects
     * instead).
     *<p>
     * Note: public to give access for unit tests that need it...
     */
    public static WstxInputSource sourceFromString(WstxInputSource parent, ReaderConfig cfg, 
                                                    String refName, int xmlVersion,
                                                   String refContent)
        throws IOException, XMLStreamException
    {
        /* Last null -> no app-provided encoding (doesn't matter for non-
         * main-level handling)
         */
        return sourceFromR(parent, cfg, refName, xmlVersion,
        		new StringReader(refContent), null, refName);
    }

    @SuppressWarnings("resource")
    private static WstxInputSource sourceFromIS(WstxInputSource parent,
    		ReaderConfig cfg, String refName, int xmlVersion,
    		InputStream is, String pubId, String sysId)
        throws IOException, XMLStreamException
    {
        StreamBootstrapper bs = StreamBootstrapper.getInstance(pubId, SystemId.construct(sysId), is);
        Reader r = bs.bootstrapInput(cfg, false, xmlVersion);
        URL ctxt = parent.getSource();

        // If we got a real sys id, we do know the source...
        if (sysId != null && sysId.length() > 0) {
            ctxt = URLUtil.urlFromSystemId(sysId, ctxt);
        }
        return InputSourceFactory.constructEntitySource
            (cfg, parent, refName, bs, pubId, SystemId.construct(sysId, ctxt),
            		xmlVersion, r);
    }

    @SuppressWarnings("resource")
    private static WstxInputSource sourceFromR(WstxInputSource parent, ReaderConfig cfg,
    		String refName, int xmlVersion,
    		Reader r, String pubId, String sysId)
        throws IOException, XMLStreamException
    {
        /* Last null -> no app-provided encoding (doesn't matter for non-
         * main-level handling)
         */
        ReaderBootstrapper rbs = ReaderBootstrapper.getInstance(pubId, SystemId.construct(sysId), r, null);
        // null -> no xml reporter... should have one?
        Reader r2 = rbs.bootstrapInput(cfg, false, xmlVersion);
        URL ctxt = (parent == null) ? null : parent.getSource();
        if (sysId != null && sysId.length() > 0) {
            ctxt = URLUtil.urlFromSystemId(sysId, ctxt);
        }
        return InputSourceFactory.constructEntitySource
            (cfg, parent, refName, rbs, pubId, SystemId.construct(sysId, ctxt), xmlVersion, r2);
    }
}
