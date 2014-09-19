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

package com.ctc.wstx.stax;

import java.util.Iterator;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.*;

import org.codehaus.stax2.ri.Stax2EventFactoryImpl;

import com.ctc.wstx.compat.QNameCreator;
import com.ctc.wstx.evt.*;

/**
 * Implementation of {@link XMLEventFactory} to be used with
 * Woodstox. Contains minimal additions on top of Stax2 RI.
 */
public final class WstxEventFactory
    extends Stax2EventFactoryImpl
{
    public WstxEventFactory() {
        super();
    }

    /*
    /////////////////////////////////////////////////////////////
    // XMLEventFactory API
    /////////////////////////////////////////////////////////////
     */

    //public Attribute createAttribute(QName name, String value)
    //public Attribute createAttribute(String localName, String value)
    //public Attribute createAttribute(String prefix, String nsURI, String localName, String value)
    //public Characters createCData(String content);
    //public Characters createCharacters(String content);
    //public Comment createComment(String text);

    /**
     * Note: constructing DTD events this way means that there will be no
     * internal presentation of actual DTD; no parsing is implied by
     * construction.
     */
    @Override
    public DTD createDTD(String dtd) {
        return new WDTD(mLocation, dtd);
    }

    //public EndDocument createEndDocument()

    //public EndElement createEndElement(QName name, Iterator namespaces)
    //public EndElement createEndElement(String prefix, String nsURI, String localName)
    //public EndElement createEndElement(String prefix, String nsURI, String localName, Iterator ns)

    //public EntityReference createEntityReference(String name, EntityDeclaration decl)

    //public Characters createIgnorableSpace(String content)

    //public Namespace createNamespace(String nsURI)
    //public Namespace createNamespace(String prefix, String nsUri)

    //public ProcessingInstruction createProcessingInstruction(String target, String data)
    
    //public Characters createSpace(String content)

    //public StartDocument createStartDocument()
    //public StartDocument createStartDocument(String encoding)
    //public StartDocument createStartDocument(String encoding, String version)
    //public StartDocument createStartDocument(String encoding, String version, boolean standalone)

    //public StartElement createStartElement(QName name, Iterator attr, Iterator ns)

    //public StartElement createStartElement(String prefix, String nsURI, String localName)

    //public StartElement createStartElement(String prefix, String nsURI, String localName, Iterator attr, Iterator ns)

    //public StartElement createStartElement(String prefix, String nsURI, String localName, Iterator attr, Iterator ns, NamespaceContext nsCtxt)

    /*
    /////////////////////////////////////////////////////////////
    // Internal/helper methods
    /////////////////////////////////////////////////////////////
     */

    @Override
    protected QName createQName(String nsURI, String localName) {
        return new QName(nsURI, localName);
    }

    @Override
    protected QName createQName(String nsURI, String localName, String prefix) {
        // [WSTX-174]: some old app servers missing 3-arg QName ctor
        return QNameCreator.create(nsURI, localName, prefix);
    }

    /**
     * Must override this method to use a more efficient StartElement
     * implementation
     */
    @SuppressWarnings("unchecked")
    @Override
    protected StartElement createStartElement(QName name, Iterator<?> attr,
            Iterator<?> ns, NamespaceContext ctxt)
    {
        return SimpleStartElement.construct(mLocation, name,
                (Iterator<Attribute>) attr,
                (Iterator<Namespace>) ns, ctxt);
    }
}
