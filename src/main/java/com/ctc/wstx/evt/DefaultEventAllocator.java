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

import java.util.*;

import javax.xml.namespace.QName;
import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.*;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.util.XMLEventAllocator;
import javax.xml.stream.util.XMLEventConsumer;

import org.codehaus.stax2.*;
import org.codehaus.stax2.ri.evt.*;

import com.ctc.wstx.cfg.ErrorConsts;
import com.ctc.wstx.dtd.DTDSubset;
import com.ctc.wstx.ent.EntityDecl;
import com.ctc.wstx.exc.WstxException;
import com.ctc.wstx.sr.ElemAttrs;
import com.ctc.wstx.sr.ElemCallback;
import com.ctc.wstx.sr.StreamReaderImpl;
import com.ctc.wstx.util.BaseNsContext;

/**
 * Straight-forward implementation of {@link XMLEventAllocator}, to be
 * used with Woodstox' event reader.
 *<p>
 * One of few complications here is the way start elements are constructed.
 * The pattern used is double-indirection, needed to get a callback from
 * the stream reader, with data we need for constructing even Object...
 * but without stream reader having any understanding of event Objects
 * per se.
 *<p>
 * 03-Dec-2004, TSa: One additional twist is that it's now possible to
 *   create slightly faster event handling, by indicating that the
 *   fully accurate Location information is not necessary. If so,
 *   allocator will just use one shared Location object passed to
 *   all event objects constructed.
 */
public class DefaultEventAllocator
    extends ElemCallback
    implements XMLEventAllocator, XMLStreamConstants
{
    final static DefaultEventAllocator sStdInstance = new DefaultEventAllocator(true);

    /*
    ///////////////////////////////////////////////////////////
    // Configuration
    ///////////////////////////////////////////////////////////
    */

    protected final boolean mAccurateLocation;

    /*
    ///////////////////////////////////////////////////////////
    // Recycled objects
    ///////////////////////////////////////////////////////////
    */

    /**
     * Last used location info; only relevant to non-accurate-location
     * allocators.
     */
    protected Location mLastLocation = null;

    /**
     * @param accurateLocation If true, allocator will construct instances
     *   that have accurate location information; if false, instances
     *   will only have some generic shared Location info. Latter option
     *   will reduce memory usage/thrashing a bit, and may improve speed.
     */
    protected DefaultEventAllocator(boolean accurateLocation) {
        mAccurateLocation = accurateLocation;
    }

    public static DefaultEventAllocator getDefaultInstance() {
        /* Default (accurate location) instance can be shared as it
         * has no state
         */
        return sStdInstance;
    }

    public static DefaultEventAllocator getFastInstance() {
        /* Can not share instances, due to QName caching, as well as because
         * of Location object related state
         */
        return new DefaultEventAllocator(false);
    }

    /*
    ///////////////////////////////////////////////////////////
    // XMLEventAllocator implementation
    ///////////////////////////////////////////////////////////
     */

    @Override
    public XMLEvent allocate(XMLStreamReader r) throws XMLStreamException
    {
        Location loc;

        // Need to keep track of accurate location info?
        if (mAccurateLocation) {
            loc = r.getLocation();
        } else {
            loc = mLastLocation;
            /* And even if we can just share one instance, we need that
             * first instance...
             */
            if (loc == null) {
                loc = mLastLocation = r.getLocation();
            }
        }

        switch (r.getEventType()) {
        case CDATA:
            return new CharactersEventImpl(loc, r.getText(), true);
        case CHARACTERS:
            return new CharactersEventImpl(loc, r.getText(), false);
        case COMMENT:
            return new CommentEventImpl(loc, r.getText());
        case DTD:
            // Not sure if we really need this defensive coding but...
            if (r instanceof XMLStreamReader2) {
                XMLStreamReader2 sr2 = (XMLStreamReader2) r;
                DTDInfo dtd = sr2.getDTDInfo();
                return new WDTD(loc,
                                dtd.getDTDRootName(),
                                dtd.getDTDSystemId(), dtd.getDTDPublicId(),
                                dtd.getDTDInternalSubset(),
                                (DTDSubset) dtd.getProcessedDTD());
            }
            /* No way to get all information... the real big problem is
             * that of how to access root name: it's obligatory for
             * DOCTYPE construct. :-/
             */
            return new WDTD(loc, null, r.getText());

        case END_DOCUMENT:
            return new EndDocumentEventImpl(loc);

        case END_ELEMENT:
            return new EndElementEventImpl(loc, r);

        case PROCESSING_INSTRUCTION:
            return new ProcInstrEventImpl(loc, r.getPITarget(), r.getPIData());
        case SPACE:
            {
                CharactersEventImpl ch = new CharactersEventImpl(loc, r.getText(), false);
                ch.setWhitespaceStatus(true);
                return ch;
            }
        case START_DOCUMENT:
            return new StartDocumentEventImpl(loc, r);

        case START_ELEMENT:
            {
                /* Creating the event is bit complicated, as the stream
                 * reader is not to know anything about event objects.
                 * To do this, we do double-indirection, which means that
                 * this object actually gets a callback:
                 */
                /* 19-Jul-2006, TSa: WSTX-61 points out that the code was
                 *   assuming it's always Woodstox reader we had... not
                 *   necessarily so.
                 */
                if (r instanceof StreamReaderImpl) {
                    StreamReaderImpl sr = (StreamReaderImpl) r;
                    BaseStartElement be = (BaseStartElement) sr.withStartElement(this, loc);
                    if (be == null) { // incorrect state
                        throw new WstxException("Trying to create START_ELEMENT when current event is "
                                                +ErrorConsts.tokenTypeDesc(sr.getEventType()),
                                                loc);
                    }
                    return be;
                }
                /* Ok, not woodstox impl, will be bit more work (plus less
                 * efficient, and may miss some info)... but can be done.
                 */
                NamespaceContext nsCtxt = null;
                if (r instanceof XMLStreamReader2) {
                    nsCtxt = ((XMLStreamReader2) r).getNonTransientNamespaceContext();
                }
                Map<QName,Attribute> attrs;
                {
                    int attrCount = r.getAttributeCount();
                    if (attrCount < 1) {
                        attrs = null;
                    } else {
                        attrs = new LinkedHashMap<QName, Attribute>();
                        for (int i = 0; i < attrCount; ++i) {
                            QName aname = r.getAttributeName(i);
                            attrs.put(aname, new AttributeEventImpl(loc, aname, r.getAttributeValue(i), r.isAttributeSpecified(i)));
                        }
                    }
                }
                List<Namespace> ns;
                {
                    int nsCount = r.getNamespaceCount();
                    if (nsCount < 1) {
                        ns = null;
                    } else {
                        ns = new ArrayList<Namespace>(nsCount);
                        for (int i = 0; i < nsCount; ++i) {
                            ns.add(NamespaceEventImpl.constructNamespace(loc, r.getNamespacePrefix(i), r.getNamespaceURI(i)));
                        }
                    }
                }
                
                return SimpleStartElement.construct(loc, r.getName(), attrs, ns, nsCtxt);
            }

        case ENTITY_REFERENCE:
            {
                /* 19-Jul-2006, TSa: Let's also allow other impls, although
                 *   we can't get actual declaration if so...
                 */
                if (r instanceof StreamReaderImpl) {
                    EntityDecl ed = ((StreamReaderImpl) r).getCurrentEntityDecl();
                    if (ed == null) { // undefined?
                    // We'll still know the name though...
                        return new WEntityReference(loc, r.getLocalName());
                    }
                    return new WEntityReference(loc, ed);
                }
                return new WEntityReference(loc, r.getLocalName());
            }


            /* Following 2 types should never get in here; they are directly
             * handled by DTDReader, and can only be accessed via DTD event
             * element.
             */
        case ENTITY_DECLARATION:
        case NOTATION_DECLARATION:
            /* Following 2 types should never get in here; they are directly
             * handled by the reader, and can only be accessed via start
             * element.
             */
        case NAMESPACE:
        case ATTRIBUTE:
            throw new WstxException("Internal error: should not get "
                                    +ErrorConsts.tokenTypeDesc(r.getEventType()));
        default:
            throw new IllegalStateException("Unrecognized event type "+r.getEventType()+".");
        }
    }
    
    @Override
    public void allocate(XMLStreamReader r, XMLEventConsumer consumer)
        throws XMLStreamException
    {
        consumer.add(allocate(r));
    }

    @Override
    public XMLEventAllocator newInstance() {
        return new DefaultEventAllocator(mAccurateLocation);
    }
    
    /*
    ///////////////////////////////////////////////////////////
    // ElemCallback implementation
    ///////////////////////////////////////////////////////////
     */

    @Override
    public Object withStartElement(Location loc, QName name,
            BaseNsContext nsCtxt, ElemAttrs attrs, boolean wasEmpty)
    {
        return new CompactStartElement(loc, name, nsCtxt, attrs);
    }
}
