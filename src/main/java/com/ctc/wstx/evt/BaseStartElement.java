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

package com.ctc.wstx.evt;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.StartElement;

import org.codehaus.stax2.XMLStreamWriter2;
import org.codehaus.stax2.ri.EmptyIterator;
import org.codehaus.stax2.ri.evt.BaseEventImpl;

import com.ctc.wstx.exc.WstxIOException;
import com.ctc.wstx.util.BaseNsContext;

/**
 * Shared base class of {@link StartElement} implementations Wstx uses.
 */
abstract class BaseStartElement
    extends BaseEventImpl
    implements StartElement
{
    protected final QName mName;

    protected final BaseNsContext mNsCtxt;

    /*
    /////////////////////////////////////////////
    // Life cycle
    /////////////////////////////////////////////
     */

    protected BaseStartElement(Location loc, QName name, BaseNsContext nsCtxt)
    {
        super(loc);
        mName = name;
        mNsCtxt = nsCtxt;
    }

    /*
    /////////////////////////////////////////////
    // StartElement API
    /////////////////////////////////////////////
     */

    public abstract Attribute getAttributeByName(QName name);

    public abstract Iterator<Attribute> getAttributes();

    public final QName getName() {
        return mName;
    }

    public Iterator<Namespace> getNamespaces() 
    {
        if (mNsCtxt == null) {
            return EmptyIterator.getInstance();
        }
        /* !!! 28-Sep-2004: Should refactor, since now it's up to ns context
         *   to construct namespace events... which adds unnecessary
         *   up-dependency from stream level to event objects.
         */
        return mNsCtxt.getNamespaces();
    }

    public NamespaceContext getNamespaceContext()
    {
        return mNsCtxt;
    }

    public String getNamespaceURI(String prefix)    {
        return (mNsCtxt == null) ? null : mNsCtxt.getNamespaceURI(prefix);
    }

    /*
    /////////////////////////////////////////////////////
    // Implementation of abstract base methods, overrides
    /////////////////////////////////////////////////////
     */

    public StartElement asStartElement() { // overriden to save a cast
        return this;
    }

    public int getEventType() {
        return START_ELEMENT;
    }

    public boolean isStartElement() {
        return true;
    }

    public void writeAsEncodedUnicode(Writer w)
        throws XMLStreamException
    {
        try {
            w.write('<');
            String prefix = mName.getPrefix();
            if (prefix != null && prefix.length() > 0) {
                w.write(prefix);
                w.write(':');
            }
            w.write(mName.getLocalPart());

            // Base class can output namespaces and attributes:
            outputNsAndAttr(w);

            w.write('>');
        } catch (IOException ie) {
            throw new WstxIOException(ie);
        }
    }

    public void writeUsing(XMLStreamWriter2 w) throws XMLStreamException
    {
        QName n = mName;
        w.writeStartElement(n.getPrefix(), n.getLocalPart(),
                            n.getNamespaceURI());
        outputNsAndAttr(w);
    }

    protected abstract void outputNsAndAttr(Writer w) throws IOException;

    protected abstract void outputNsAndAttr(XMLStreamWriter w) throws XMLStreamException;

    /*
    ///////////////////////////////////////////
    // Standard method implementation
    //
    // note: copied from Stax2 RI's StartElementEventImpl
    ///////////////////////////////////////////
     */

    public boolean equals(Object o)
    {
        if (o == this) return true;
        if (o == null) return false;

        if (!(o instanceof StartElement)) return false;

        StartElement other = (StartElement) o;

        // First things first: names must match
        if (mName.equals(other.getName())) {
            /* Rest is much trickier. I guess the easiest way is to
             * just blindly iterate through ns decls and attributes.
             * The main issue is whether ordering should matter; it will,
             * if just iterating. Would need to sort to get canonical
             * comparison.
             */
            if (iteratedEquals(getNamespaces(), other.getNamespaces())) {
                return iteratedEquals(getAttributes(), other.getAttributes());
            }
        }
        return false;
    }

    public int hashCode()
    {
        int hash = mName.hashCode();
        hash = addHash(getNamespaces(), hash);
        hash = addHash(getAttributes(), hash);
        return hash;
    }
}
