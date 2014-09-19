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

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

import com.ctc.wstx.stax.WstxInputFactory;

/**
 * This is implementation of the main JAXP SAX factory, and as such
 * acts as the entry point from JAXP.
 *<p>
 * Note: most of the SAX features are not configurable as of yet.
 * However, effort is made to recognize all existing standard features
 * and properties, to allow using code to figure out existing
 * capabilities automatically.
 */
public class WstxSAXParserFactory
    extends SAXParserFactory
{
    protected final WstxInputFactory mStaxFactory;

    /**
     * Sax feature that determines whether namespace declarations need
     * to be also reported as attributes or not.
     */
    protected boolean mFeatNsPrefixes = false;

    public WstxSAXParserFactory()
    {
        this(new WstxInputFactory());
    }

    /**
     * @since 4.0.8
     */
    public WstxSAXParserFactory(WstxInputFactory f)
    {
        mStaxFactory = f;
        /* defaults should be fine... except that for some weird
         * reason, by default namespace support is defined to be off
         */
        setNamespaceAware(true);
    }

    public boolean getFeature(String name)
        throws SAXNotRecognizedException, SAXNotSupportedException
    {
        SAXFeature stdFeat = SAXFeature.findByUri(name);

        if (stdFeat == SAXFeature.EXTERNAL_GENERAL_ENTITIES) {
            return mStaxFactory.getConfig().willSupportExternalEntities();
        } else if (stdFeat == SAXFeature.EXTERNAL_PARAMETER_ENTITIES) {
            return mStaxFactory.getConfig().willSupportExternalEntities();
        } else if (stdFeat == SAXFeature.IS_STANDALONE) {
            // Not known at this point...
            return false;
        } else if (stdFeat == SAXFeature.LEXICAL_HANDLER_PARAMETER_ENTITIES) {
            // !!! TODO:
            return false;
        } else if (stdFeat == SAXFeature.NAMESPACES) {
            return mStaxFactory.getConfig().willSupportNamespaces();
        } else if (stdFeat == SAXFeature.NAMESPACE_PREFIXES) {
            return mFeatNsPrefixes;
        } else if (stdFeat == SAXFeature.RESOLVE_DTD_URIS) {
            // !!! TODO:
            return false;
        } else if (stdFeat == SAXFeature.STRING_INTERNING) {
            return mStaxFactory.getConfig().willInternNames();
        } else if (stdFeat == SAXFeature.UNICODE_NORMALIZATION_CHECKING) {
            return false;
        } else if (stdFeat == SAXFeature.USE_ATTRIBUTES2) {
            return true;
        } else if (stdFeat == SAXFeature.USE_LOCATOR2) {
            return true;
        } else if (stdFeat == SAXFeature.USE_ENTITY_RESOLVER2) {
            return true;
        } else if (stdFeat == SAXFeature.VALIDATION) {
            return mStaxFactory.getConfig().willValidateWithDTD();
        } else if (stdFeat == SAXFeature.XMLNS_URIS) {
            /* !!! TODO: default value should be false... but not sure
             *   if implementing that mode makes sense
             */
            return true;
        } else if (stdFeat == SAXFeature.XML_1_1) {
            return true;
        } else {
            throw new SAXNotRecognizedException("Feature '"+name+"' not recognized");
        }
    }

    public SAXParser newSAXParser()
    {
        return new WstxSAXParser(mStaxFactory, mFeatNsPrefixes);
    }

    public void setFeature(String name, boolean value)
        throws SAXNotRecognizedException, SAXNotSupportedException
    {
        boolean invalidValue = false;
        boolean readOnly = false;
        SAXFeature stdFeat = SAXFeature.findByUri(name);

        if (stdFeat == SAXFeature.EXTERNAL_GENERAL_ENTITIES) {
            mStaxFactory.getConfig().doSupportExternalEntities(value);
        } else if (stdFeat == SAXFeature.EXTERNAL_PARAMETER_ENTITIES) {
            // !!! TODO
        } else if (stdFeat == SAXFeature.IS_STANDALONE) {
            readOnly = true;
        } else if (stdFeat == SAXFeature.LEXICAL_HANDLER_PARAMETER_ENTITIES) {
            // !!! TODO
        } else if (stdFeat == SAXFeature.NAMESPACES) {
            mStaxFactory.getConfig().doSupportNamespaces(value);
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
            mStaxFactory.getConfig().doValidateWithDTD(value);
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
}



