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

package com.ctc.wstx.dtd;

import java.io.*;
import java.net.URL;

import javax.xml.stream.*;

import org.codehaus.stax2.validation.*;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.api.ValidatorConfig;
import com.ctc.wstx.cfg.XmlConsts;
import com.ctc.wstx.exc.WstxIOException;
import com.ctc.wstx.io.*;
import com.ctc.wstx.util.DefaultXmlSymbolTable;
import com.ctc.wstx.util.SymbolTable;
import com.ctc.wstx.util.URLUtil;

/**
 * Factory for creating DTD validator schema objects (shareable stateless
 * "blueprints" for creating actual validators).
 *<p>
 * Due to close coupling of XML and DTD, some of the functionality
 * implemented (like that of reading internal subsets embedded in XML
 * documents) is only accessible by core Woodstox. The externally
 * accessible
 */
public class DTDSchemaFactory
    extends XMLValidationSchemaFactory
{
    /*
    /////////////////////////////////////////////////////
    // Objects shared by actual parsers
    /////////////////////////////////////////////////////
     */

    /**
     * 'Root' symbol table, used for creating actual symbol table instances,
     * but never as is.
     */
    final static SymbolTable mRootSymbols = DefaultXmlSymbolTable.getInstance();
    static {
        mRootSymbols.setInternStrings(true);
    }

    /**
     * Current configurations for this factory
     */
    protected final ValidatorConfig mSchemaConfig;

    /**
     * This configuration object is used (instead of a more specific one)
     * since the actual DTD reader uses such configuration object.
     */
    protected final ReaderConfig mReaderConfig;

    public DTDSchemaFactory()
    {
        super(XMLValidationSchema.SCHEMA_ID_DTD);
        mReaderConfig = ReaderConfig.createFullDefaults();
        mSchemaConfig = ValidatorConfig.createDefaults();
    }

    /*
    ////////////////////////////////////////////////////////////
    // Stax2, Configuration methods
    ////////////////////////////////////////////////////////////
     */

    public boolean isPropertySupported(String propName)
    {
        return mSchemaConfig.isPropertySupported(propName);
    }

    public boolean setProperty(String propName, Object value)
    {
        return mSchemaConfig.setProperty(propName, value);
    }

    public Object getProperty(String propName)
    {
        return mSchemaConfig.getProperty(propName);
    }

    /*
    ////////////////////////////////////////////////////////////
    // Stax2, Factory methods
    ////////////////////////////////////////////////////////////
     */

    public XMLValidationSchema createSchema(InputStream in, String encoding,
    		String publicId, String systemId)
        throws XMLStreamException
    {
        ReaderConfig rcfg = createPrivateReaderConfig();
        return doCreateSchema(rcfg, StreamBootstrapper.getInstance
        		(publicId, SystemId.construct(systemId), in), publicId, systemId, null);
    }

    public XMLValidationSchema createSchema(Reader r,
    		String publicId, String systemId)
        throws XMLStreamException
    {
        ReaderConfig rcfg = createPrivateReaderConfig();
        return doCreateSchema(rcfg, ReaderBootstrapper.getInstance
        		(publicId, SystemId.construct(systemId), r, null), publicId, systemId, null);
    }

    public XMLValidationSchema createSchema(URL url)
        throws XMLStreamException
    {
        ReaderConfig rcfg = createPrivateReaderConfig();
        try {
            InputStream in = URLUtil.inputStreamFromURL(url);
            return doCreateSchema(rcfg, StreamBootstrapper.getInstance
                                  (null, null, in),
                                  null, url.toExternalForm(), url);
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }
    }

    public XMLValidationSchema createSchema(File f)
        throws XMLStreamException
    {
        ReaderConfig rcfg = createPrivateReaderConfig();
        try {
            URL url = URLUtil.toURL(f);
            return doCreateSchema(rcfg, StreamBootstrapper.getInstance
                                  (null, null, new FileInputStream(f)),
                                  null, url.toExternalForm(), url);
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }
    }

    /*
    ////////////////////////////////////////////////////////////
    // Internal methods
    ////////////////////////////////////////////////////////////
     */

    /**
     * The main validator construction method, called by all externally
     * visible methods.
     */
    protected XMLValidationSchema doCreateSchema
        (ReaderConfig rcfg, InputBootstrapper bs, String publicId, String systemIdStr, URL ctxt)
        throws XMLStreamException
    {
        try {
            Reader r = bs.bootstrapInput(rcfg, false, XmlConsts.XML_V_UNKNOWN);
            if (bs.declaredXml11()) {
                rcfg.enableXml11(true);
            }
            if (ctxt == null) { // this is just needed as context for param entity expansion
                ctxt = URLUtil.urlFromCurrentDir();
            }
            /* Note: need to pass unknown for 'xmlVersion' here (as well as
             * above for bootstrapping), since this is assumed to be the main
             * level parsed document and no xml version compatibility checks
             * should be done.
             */
            SystemId systemId = SystemId.construct(systemIdStr, ctxt);
            WstxInputSource src = InputSourceFactory.constructEntitySource
                (rcfg, null, null, bs, publicId, systemId, XmlConsts.XML_V_UNKNOWN, r);

            /* true -> yes, fully construct for validation
             * (does not mean it has to be used for validation, but required
             * if it is to be used for that purpose)
             */
            return FullDTDReader.readExternalSubset(src, rcfg, /*int.subset*/null, true, bs.getDeclaredVersion());
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }
    }

    private ReaderConfig createPrivateReaderConfig()
    {
        return mReaderConfig.createNonShared(mRootSymbols.makeChild());
    }
}
