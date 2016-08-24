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
 * Type safe (pre-Java5) enumeration for listing all currently (SAX 2.0.2)
 * defined standard features
 */
public final class SAXFeature
{
    /**
     * Since all standard features have same URI prefix, let's separate
     * that prefix, from unique remainder of the feature URIs.
     */
    public final static String STD_FEATURE_PREFIX = "http://xml.org/sax/features/";

    final static HashMap <String,SAXFeature>sInstances = new HashMap<String,SAXFeature>();

    // // // "Enum" values:

    final static SAXFeature EXTERNAL_GENERAL_ENTITIES = new SAXFeature("external-general-entities");
    final static SAXFeature EXTERNAL_PARAMETER_ENTITIES = new SAXFeature("external-parameter-entities");
    final static SAXFeature IS_STANDALONE = new SAXFeature("is-standalone");
    final static SAXFeature LEXICAL_HANDLER_PARAMETER_ENTITIES = new SAXFeature("lexical-handler/parameter-entities");
    final static SAXFeature NAMESPACES = new SAXFeature("namespaces");
    final static SAXFeature NAMESPACE_PREFIXES = new SAXFeature("namespace-prefixes");
    final static SAXFeature RESOLVE_DTD_URIS = new SAXFeature("resolve-dtd-uris");
    final static SAXFeature STRING_INTERNING = new SAXFeature("string-interning");
    final static SAXFeature UNICODE_NORMALIZATION_CHECKING = new SAXFeature("unicode-normalization-checking");
    final static SAXFeature USE_ATTRIBUTES2 = new SAXFeature("use-attributes2");
    final static SAXFeature USE_LOCATOR2 = new SAXFeature("use-locator2");
    final static SAXFeature USE_ENTITY_RESOLVER2 = new SAXFeature("use-entity-resolver2");
    final static SAXFeature VALIDATION = new SAXFeature("validation");
    final static SAXFeature XMLNS_URIS = new SAXFeature("xmlns-uris");
    final static SAXFeature XML_1_1 = new SAXFeature("xml-1.1");

    private final String mSuffix;

    private SAXFeature(String suffix)
    {
        mSuffix = suffix;
        sInstances.put(suffix, this);
    }

    public static SAXFeature findByUri(String uri)
    {
        if (uri.startsWith(STD_FEATURE_PREFIX)) {
            return findBySuffix(uri.substring(STD_FEATURE_PREFIX.length()));
        }
        return null;
    }

    public static SAXFeature findBySuffix(String suffix)
    {
        return sInstances.get(suffix);
    }

    public String getSuffix() { return mSuffix; }

    @Override
    public String toString() { return STD_FEATURE_PREFIX + mSuffix; }
}
