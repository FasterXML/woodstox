package com.ctc.wstx.io;

import java.io.Reader;
import java.net.URL;

import javax.xml.stream.Location;

import com.ctc.wstx.api.ReaderConfig;

/**
 * Factory class that creates instances of {@link WstxInputSource} to allow
 * reading input from various sources.
 */
public final class InputSourceFactory
{
    //final static int DEFAULT_BUFFER_LENGTH = 4000;

    /**
     * @param parent
     * @param entityName Name of the entity expanded to create this input
     *    source: null when source created for the (main level) external
     *    DTD subset entity.
     * @param xmlVersion Optional xml version identifier of the main parsed
     *   document. Currently only relevant for checking that XML 1.0 document
     *   does not include XML 1.1 external parsed entities.
     *   If unknown, no checks will be done.
     */
    public static ReaderSource constructEntitySource
        (ReaderConfig cfg, WstxInputSource parent, String entityName, InputBootstrapper bs,
         String pubId, SystemId sysId, int xmlVersion, Reader r)
    {
        // true -> do close the underlying Reader at EOF
        ReaderSource rs = new ReaderSource
            (cfg, parent, entityName, pubId, sysId, r, true);
        if (bs != null) {
            rs.setInputOffsets(bs.getInputTotal(), bs.getInputRow(),
                               -bs.getInputColumn());
        }
        return rs;
    }

    /**
     * Factory method used for creating the main-level document reader
     * source.
     */
    public static BranchingReaderSource constructDocumentSource
        (ReaderConfig cfg, InputBootstrapper bs, String pubId, SystemId sysId,
         Reader r, boolean realClose) 
    {
        /* To resolve [WSTX-50] need to ensure that P_BASE_URL overrides
         * the defaults if/as necessary
         */
        URL url = cfg.getBaseURL();
        if (url != null) {
        	sysId = SystemId.construct(url);
        }
        BranchingReaderSource rs = new BranchingReaderSource(cfg, pubId, sysId, r, realClose);
        if (bs != null) {
            rs.setInputOffsets(bs.getInputTotal(), bs.getInputRow(),
                               -bs.getInputColumn());
        }
        return rs;
    }

    /**
     * Factory method usually used to expand internal parsed entities; in
     * which case context remains mostly the same.
     */
    public static WstxInputSource constructCharArraySource
        (WstxInputSource parent, String fromEntity,
         char[] text, int offset, int len, Location loc, URL src)
    {
    	SystemId sysId = SystemId.construct(loc.getSystemId(), src);
        return new CharArraySource(parent, fromEntity, text, offset, len, loc, sysId);
    }
}
