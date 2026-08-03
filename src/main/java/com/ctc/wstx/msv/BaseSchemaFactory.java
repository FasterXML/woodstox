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

package com.ctc.wstx.msv;

import java.io.*;
import java.net.URL;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.*;

import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.codehaus.stax2.validation.*;

import com.ctc.wstx.api.ValidatorConfig;
import com.ctc.wstx.exc.WstxIOException;
import com.ctc.wstx.util.URLUtil;

/**
 * Shared base class extended by concrete schema factory implementations.
 */
public abstract class BaseSchemaFactory
    extends XMLValidationSchemaFactory
{
    protected static SAXParserFactory sSaxFactory;

    /**
     * Current configurations for this factory
     */
    protected final ValidatorConfig mConfig;

    protected BaseSchemaFactory(String schemaType)
    {
        super(schemaType);
        mConfig = ValidatorConfig.createDefaults();
    }

    /*
    ////////////////////////////////////////////////////////////
    // Stax2, Configuration methods
    ////////////////////////////////////////////////////////////
     */

    @Override
    public boolean isPropertySupported(String propName) {
        return mConfig.isPropertySupported(propName);
    }

    @Override
    public boolean setProperty(String propName, Object value) {
        return mConfig.setProperty(propName, value);
    }

    @Override
    public Object getProperty(String propName) {
        return mConfig.getProperty(propName);
    }

    /*
    ////////////////////////////////////////////////////////////
    // Stax2, Factory methods
    ////////////////////////////////////////////////////////////
     */

    @Override
    public XMLValidationSchema createSchema(InputStream in, String encoding,
                                           String publicId, String systemId)
        throws XMLStreamException
    {
        InputSource src = new InputSource(in);
        src.setEncoding(encoding);
        src.setPublicId(publicId);
        src.setSystemId(systemId);
        return loadSchema(src, systemId);
    }

    @Override
    public XMLValidationSchema createSchema(Reader r, String publicId,
                                            String systemId)
        throws XMLStreamException
    {
        InputSource src = new InputSource(r);
        src.setPublicId(publicId);
        src.setSystemId(systemId);
        return loadSchema(src, systemId);
    }

    @SuppressWarnings("resource")
    @Override
    public XMLValidationSchema createSchema(URL url)
        throws XMLStreamException
    {
        try {
            InputStream in = URLUtil.inputStreamFromURL(url);
            InputSource src = new InputSource(in);
            src.setSystemId(url.toExternalForm());
            return loadSchema(src, url);
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public XMLValidationSchema createSchema(File f)
        throws XMLStreamException
    {
        try {
            return createSchema(f.toURL());
        } catch (IOException ioe) {
            throw new WstxIOException(ioe);
        }
    }

    /*
    ////////////////////////////////////////////////////////////
    // Methods sub-classes need to implement
    ////////////////////////////////////////////////////////////
     */

    protected abstract XMLValidationSchema loadSchema(InputSource src, Object sysRef)
        throws XMLStreamException;

    /*
    ////////////////////////////////////////////////////////////
    // Internal/package methods
    ////////////////////////////////////////////////////////////
     */

    /**
     * We will essentially share a singleton sax parser factory;
     * the reason being that constructing (or, rather, locating
     * implementation class) is bit expensive.
     */
    protected synchronized static SAXParserFactory getSaxFactory()
    {
        if (sSaxFactory == null) {
            sSaxFactory = SAXParserFactory.newInstance();
            sSaxFactory.setNamespaceAware(true); 
            disableExternalEntities(sSaxFactory);
        }
        return sSaxFactory;
    }

    /**
     * Method for disabling resolution of external entities referenced from
     * schema documents. Neither this factory nor MSV exposes the underlying
     * SAX parser, so callers have no way of doing this themselves.
     *<p>
     * Does not affect {@code xs:include} / {@code xs:import} or RELAX NG
     * {@code externalRef}: those are resolved by MSV itself, not through
     * entity resolution.
     */
    static void disableExternalEntities(SAXParserFactory f)
    {
        setFeature(f, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setFeature(f, "http://xml.org/sax/features/external-general-entities", false);
        setFeature(f, "http://xml.org/sax/features/external-parameter-entities", false);
    }

    private static void setFeature(SAXParserFactory f, String feature, boolean state)
    {
        try {
            f.setFeature(feature, state);
        } catch (ParserConfigurationException | SAXNotRecognizedException | SAXNotSupportedException e) {
            // Not every SAX implementation knows every feature; nothing to do
        }
    }

    /*
    ////////////////////////////////////////////////////////////
    // Helper classes
    ////////////////////////////////////////////////////////////
     */

    final static class MyGrammarController
        extends com.sun.msv.reader.util.IgnoreController
    {
        public String mErrorMsg = null;

        public MyGrammarController() { }

        //public void warning(Locator[] locs, String errorMessage) { }

        @Override
        public void error(Locator[] locs, String msg, Exception nestedException )
        {
            if (mErrorMsg == null) {
                mErrorMsg = msg;
            } else {
                mErrorMsg = mErrorMsg + "; " + msg;
            }
        }
    }
}
