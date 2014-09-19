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

import java.util.HashMap;

/**
 * Type-safe (pre-Java5) enumeration of all currently (SAX 2.0.2) defined
 * standard properties.
 */
public final class SAXProperty
{
    public final static String STD_PROPERTY_PREFIX = "http://xml.org/sax/properties/";

    final static HashMap<String,SAXProperty> sInstances = new HashMap<String,SAXProperty>();

    // // // "Enum" values:

    public final static SAXProperty DECLARATION_HANDLER = new SAXProperty("declaration-handler");
    public final static SAXProperty DOCUMENT_XML_VERSION = new SAXProperty("document-xml-version");
    public final static SAXProperty DOM_NODE = new SAXProperty("dom-node");
    public final static SAXProperty LEXICAL_HANDLER = new SAXProperty("lexical-handler");
    final static SAXProperty XML_STRING = new SAXProperty("xml-string");

    private final String mSuffix;

    private SAXProperty(String suffix)
    {
        mSuffix = suffix;
        sInstances.put(suffix, this);
    }

    public static SAXProperty findByUri(String uri)
    {
        if (uri.startsWith(STD_PROPERTY_PREFIX)) {
            return findBySuffix(uri.substring(STD_PROPERTY_PREFIX.length()));
        }
        return null;
    }

    public static SAXProperty findBySuffix(String suffix)
    {
        return sInstances.get(suffix);
    }

    public String getSuffix() { return mSuffix; }

    public String toString() { return STD_PROPERTY_PREFIX + mSuffix; }
}
