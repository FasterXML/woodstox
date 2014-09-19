/* Woodstox XML processor
 *
 * Copyright (c) 2004 Tatu Saloranta, tatu.saloranta@iki.fi
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

package com.ctc.wstx.util;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.Namespace;

import com.ctc.wstx.cfg.ErrorConsts;

/**
 * Abstract base class that defines extra features defined by most
 * NamespaceContext implementations Wodstox uses.
 */
public abstract class BaseNsContext
    implements NamespaceContext
{
    /**
     * This is the URI returned for default namespace, when it hasn't
     * been explicitly declared; could be either "" or null.
     */
    protected final static String UNDECLARED_NS_URI = "";

    /*
    /////////////////////////////////////////////
    // NamespaceContext API
    /////////////////////////////////////////////
     */

    @Override
    public final String getNamespaceURI(String prefix)
    {
        /* First the known offenders; invalid args, 2 predefined xml namespace
         * prefixes
         */
        if (prefix == null) {
            throw new IllegalArgumentException(ErrorConsts.ERR_NULL_ARG);
        }
        if (prefix.length() > 0) {
            if (prefix.equals(XMLConstants.XML_NS_PREFIX)) {
                return XMLConstants.XML_NS_URI;
            }
            if (prefix.equals(XMLConstants.XMLNS_ATTRIBUTE)) {
                return XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
            }
        }
        return doGetNamespaceURI(prefix);
    }

    @Override
    public final String getPrefix(String nsURI)
    {
        /* First the known offenders; invalid args, 2 predefined xml namespace
         * prefixes
         */
        if (nsURI == null || nsURI.length() == 0) {
            throw new IllegalArgumentException("Illegal to pass null/empty prefix as argument.");
        }
        if (nsURI.equals(XMLConstants.XML_NS_URI)) {
            return XMLConstants.XML_NS_PREFIX;
        }
        if (nsURI.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)) {
            return XMLConstants.XMLNS_ATTRIBUTE;
        }
        return doGetPrefix(nsURI);
    }

    @Override
    public final Iterator<String> getPrefixes(String nsURI)
    {
        /* First the known offenders; invalid args, 2 predefined xml namespace
         * prefixes
         */
        if (nsURI == null || nsURI.length() == 0) {
            throw new IllegalArgumentException("Illegal to pass null/empty prefix as argument.");
        }
        if (nsURI.equals(XMLConstants.XML_NS_URI)) {
            return DataUtil.singletonIterator(XMLConstants.XML_NS_PREFIX);
        }
        if (nsURI.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)) {
            return DataUtil.singletonIterator(XMLConstants.XMLNS_ATTRIBUTE);
        }

        return doGetPrefixes(nsURI);
    }

    /*
    /////////////////////////////////////////////
    // Extended API
    /////////////////////////////////////////////
     */

    public abstract Iterator<Namespace> getNamespaces();

    /**
     * Method called by the matching start element class to
     * output all namespace declarations active in current namespace
     * scope, if any.
     */
    public abstract void outputNamespaceDeclarations(Writer w) throws IOException;

    public abstract void outputNamespaceDeclarations(XMLStreamWriter w) throws XMLStreamException;

    /*
    /////////////////////////////////////////////////
    // Template methods sub-classes need to implement
    /////////////////////////////////////////////////
     */

    public abstract String doGetNamespaceURI(String prefix);

    public abstract String doGetPrefix(String nsURI);

    public abstract Iterator<String> doGetPrefixes(String nsURI);
}
