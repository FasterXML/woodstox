/* Woodstox XML processor
 *
 * Copyright (c) 2004- Tatu Saloranta, tatu.saloranta@iki.fi
 *
 * Licensed under the License specified in the file LICENSE,
 * included with the source code.
 * You may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, softwar
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ctc.wstx.sw;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;

import org.codehaus.stax2.ri.typed.AsciiValueEncoder;

import com.ctc.wstx.api.WriterConfig;
import com.ctc.wstx.cfg.ErrorConsts;
import com.ctc.wstx.sr.AttributeCollector;
import com.ctc.wstx.sr.InputElementStack;

/**
 * Namespace-aware implementation of {@link XMLStreamWriter}, that does
 * namespace repairing, ie resolves possible conflicts between prefixes
 * (add new bindings as necessary), as well as automatically creates
 * namespace declarations as necessary.
 */
public final class RepairingNsStreamWriter
    extends BaseNsStreamWriter
{
    /*
    ///////////////////////////////////////////////////////////
    // Configuration (options, features)
    ///////////////////////////////////////////////////////////
     */

    // // // Additional specific config flags base class doesn't have

    protected final String mAutomaticNsPrefix;

    /*
    ///////////////////////////////////////////////////////////
    // Additional state
    ///////////////////////////////////////////////////////////
     */

    /**
     * Sequence number used for generating dynamic namespace prefixes.
     * Array used as a wrapper to allow for easy sharing of the sequence
     * number.
     */
    protected int[] mAutoNsSeq = null;

    protected String mSuggestedDefNs = null;

    /**
     * Map that contains URI-to-prefix entries that point out suggested
     * prefixes for URIs. These are populated by calls to
     * {@link #setPrefix}, and they are only used as hints for binding;
     * if there are conflicts, repairing writer can just use some other
     * prefix.
     */
    protected HashMap<String,String> mSuggestedPrefixes = null;

    /*
    ///////////////////////////////////////////////////////////
    // Life-cycle (ctors)
    ///////////////////////////////////////////////////////////
     */

    public RepairingNsStreamWriter(XmlWriter xw, String enc, WriterConfig cfg)
    {
        super(xw, enc, cfg, true);
        mAutomaticNsPrefix = cfg.getAutomaticNsPrefix();
    }

    /*
    ///////////////////////////////////////////////////////////
    // XMLStreamWriter API
    ///////////////////////////////////////////////////////////
     */

    //public NamespaceContext getNamespaceContext()
    //public void setNamespaceContext(NamespaceContext context)
    //public String getPrefix(String uri)
    //public void setPrefix(String prefix, String uri)
    //public void setDefaultNamespace(String uri)

    //public void writeAttribute(String localName, String value)

    public void writeAttribute(String nsURI, String localName, String value)
        throws XMLStreamException
    {
        // No need to set mAnyOutput, nor close the element
        if (!mStartElementOpen) {
            throwOutputError(ErrorConsts.WERR_ATTR_NO_ELEM);
        }
        doWriteAttr(localName, nsURI,
		    findOrCreateAttrPrefix(null, nsURI, mCurrElem),
                    value);
    }

    public void writeAttribute(String prefix, String nsURI,
                               String localName, String value)
        throws XMLStreamException
    {
        if (!mStartElementOpen) {
            throwOutputError(ErrorConsts.WERR_ATTR_NO_ELEM);
        }

        doWriteAttr(localName, nsURI, findOrCreateAttrPrefix(prefix, nsURI, mCurrElem),
                    value);
    }

    public void writeDefaultNamespace(String nsURI)
        throws XMLStreamException
    {
        /* 01-Sep-2006, TSa: The use case for calling this method is that
         *   of caller may wanting to 'suggest' that
         *   such a namespace should indeed be bound at this level. This
         *   may be necessary for canonicalization, or for minimizing number
         *   of binding declarations (all children need the ns, but root
         *   itself not).
         */
         if (!mStartElementOpen) {
             throwOutputError(ERR_NSDECL_WRONG_STATE);
         }
         /* ... We have one complication though: if the current element
          * uses default namespace, can not change it (attributes don't
          * matter -- they never use the default namespace, but either don't
          * belong to a namespace, or belong to one using explicit prefix)
          */
         String prefix = mCurrElem.getPrefix();
         if (prefix != null && prefix.length() > 0) { // ok, can change it
             mCurrElem.setDefaultNsUri(nsURI);
             doWriteDefaultNs(nsURI);
         }
    }

    //public void writeEmptyElement(String localName) throws XMLStreamException

    public void writeNamespace(String prefix, String nsURI)
        throws XMLStreamException
    {
        /* (see discussion in 'writeDefaultNamespace()' for details on
         * if and how this method may get called in repairing mode)
         */
        if (prefix == null || prefix.length() == 0) {
            writeDefaultNamespace(nsURI);
            return;
        }
        if (!mStartElementOpen) {
            throwOutputError(ERR_NSDECL_WRONG_STATE);
        }
        /* 01-Sep-2006, TSa: Let's only add the declaration if the prefix
         *   is as of yet unbound. If we have to re-bind things in future,
         *   so be it -- for now, this should suffice (and if we have to
         *   add re-binding, must verify that no attribute, nor element
         *   itself, is using overridden prefix)
         */
        int value = mCurrElem.isPrefixValid(prefix, nsURI, true);
        if (value == SimpleOutputElement.PREFIX_UNBOUND) {
            mCurrElem.addPrefix(prefix, nsURI);
            doWriteNamespace(prefix, nsURI);
        }
    }
    
    /*
    ///////////////////////////////////////////////////////////
    // Package methods:
    ///////////////////////////////////////////////////////////
     */

    /**
     * With repairing writer, this is only taken as a suggestion as to how
     * the caller would prefer prefixes to be mapped.
     */
    public void setDefaultNamespace(String uri)
        throws XMLStreamException
    {
        mSuggestedDefNs = (uri == null || uri.length() == 0) ? null : uri;
    }

    public void doSetPrefix(String prefix, String uri)
        throws XMLStreamException
    {
        /* Ok; let's assume that passing in a null or empty String as
         * the URI means that we don't want passed prefix to be preferred
         * for any URI.
         */
        if (uri == null || uri.length() == 0) {
            if (mSuggestedPrefixes != null) {
                for (Iterator<Map.Entry<String,String>> it = mSuggestedPrefixes.entrySet().iterator();
                     it.hasNext(); ) {
                    Map.Entry<String,String> en = it.next();
                    String thisP = en.getValue();
                    if (thisP.equals(prefix)) {
                        it.remove();
                    }
                }
            }
        } else {
            if (mSuggestedPrefixes == null) {
                mSuggestedPrefixes = new HashMap<String,String>(16);
            }
            mSuggestedPrefixes.put(uri, prefix);
        }
    }

	public void writeStartElement(StartElement elem)
        throws XMLStreamException
    {
        /* In repairing mode this is simple: let's just pass info
         * we have, and things should work... a-may-zing!
         */
        QName name = elem.getName();
        writeStartElement(name.getPrefix(), name.getLocalPart(),
                          name.getNamespaceURI());
        @SuppressWarnings("unchecked")
        Iterator<Attribute> it = elem.getAttributes();
        while (it.hasNext()) {
            Attribute attr = it.next();
            name = attr.getName();
            writeAttribute(name.getPrefix(), name.getNamespaceURI(),
                           name.getLocalPart(), attr.getValue());
        }
    }

    //public void writeEndElement(QName name) throws XMLStreamException

    protected void writeTypedAttribute(String prefix, String nsURI, String localName,
                                       AsciiValueEncoder enc)
        throws XMLStreamException
    {
        super.writeTypedAttribute(findOrCreateAttrPrefix(prefix, nsURI, mCurrElem),
                                  nsURI, localName, enc);
    }

    protected void writeStartOrEmpty(String localName, String nsURI)
        throws XMLStreamException
    {
        checkStartElement(localName, "");

        // First, need to find prefix matching URI, if any:
        String prefix = findElemPrefix(nsURI, mCurrElem);
        /* Then need to create the element, since it'll have to
         * contain the new namespace binding, if one needed
         * (changed to resolve [WSTX-135] as reported by Y-J Choi,
         *  who also proposed the solution)
         */
        if (mOutputElemPool != null) {
            SimpleOutputElement newCurr = mOutputElemPool;
            mOutputElemPool = newCurr.reuseAsChild(mCurrElem, prefix, localName, nsURI);
            --mPoolSize;
            mCurrElem = newCurr;
        } else {
            mCurrElem = mCurrElem.createChild(prefix, localName, nsURI);
        }
        
        if (prefix != null) { // prefix ok, easy, no need to overwrite
            if (mValidator != null) {
                mValidator.validateElementStart(localName, nsURI, prefix);
            }
            doWriteStartTag(prefix, localName);
        } else { // no prefix, more work
            prefix = generateElemPrefix(null, nsURI, mCurrElem);
            if (mValidator != null) {
                mValidator.validateElementStart(localName, nsURI, prefix);
            }
            mCurrElem.setPrefix(prefix);
            doWriteStartTag(prefix, localName);
            if (prefix == null || prefix.length() == 0) { // def NS
                mCurrElem.setDefaultNsUri(nsURI);
                doWriteDefaultNs(nsURI);
            } else { // explicit NS
                mCurrElem.addPrefix(prefix, nsURI);
                doWriteNamespace(prefix, nsURI);
            }
        }
    }

    protected void writeStartOrEmpty(String suggPrefix, String localName, String nsURI)
        throws XMLStreamException
    {
        checkStartElement(localName, suggPrefix);

        // In repairing mode, better ensure validity:
        String actPrefix = validateElemPrefix(suggPrefix, nsURI, mCurrElem);
        if (actPrefix != null) { // fine, an existing binding we can use:
            if (mValidator != null) {
                mValidator.validateElementStart(localName, nsURI, actPrefix);
            }
            if (mOutputElemPool != null) {
                SimpleOutputElement newCurr = mOutputElemPool;
                mOutputElemPool = newCurr.reuseAsChild(mCurrElem, actPrefix, localName, nsURI);
                --mPoolSize;
                mCurrElem = newCurr;
            } else {
                mCurrElem = mCurrElem.createChild(actPrefix, localName, nsURI);
            }
            doWriteStartTag(actPrefix, localName);
        } else { // nah, need to create a new binding...
            /* Need to ensure that we'll pass "" as prefix, not null, so
             * that it is understood as "I want to use the default NS", not
             * as "whatever prefix, I don't care"
             */
            if (suggPrefix == null) {
                suggPrefix = "";
            }
            actPrefix = generateElemPrefix(suggPrefix, nsURI, mCurrElem);
            if (mValidator != null) {
                mValidator.validateElementStart(localName, nsURI, actPrefix);
            }
            if (mOutputElemPool != null) {
                SimpleOutputElement newCurr = mOutputElemPool;
                mOutputElemPool = newCurr.reuseAsChild(mCurrElem, actPrefix, localName, nsURI);
                --mPoolSize;
                mCurrElem = newCurr;
            } else {
                mCurrElem = mCurrElem.createChild(actPrefix, localName, nsURI);
            }
            mCurrElem.setPrefix(actPrefix);
            doWriteStartTag(actPrefix, localName);
            if (actPrefix == null || actPrefix.length() == 0) { // def NS
                mCurrElem.setDefaultNsUri(nsURI);
                doWriteDefaultNs(nsURI);
            } else { // explicit NS
                mCurrElem.addPrefix(actPrefix, nsURI);
                doWriteNamespace(actPrefix, nsURI);
            }
        }
    }

    /**
     * Element copier method implementation suitable for use with
     * namespace-aware writers in repairing mode.
     * The trickiest thing is having to properly
     * order calls to <code>setPrefix</code>, <code>writeNamespace</code>
     * and <code>writeStartElement</code>; the order writers expect is
     * bit different from the order in which element information is
     * passed in.
     */
    public final void copyStartElement(InputElementStack elemStack, AttributeCollector ac)
        throws IOException, XMLStreamException
    {
        /* In case of repairing stream writer, we can actually just
         * go ahead and first output the element: stream writer should
         * be able to resolve namespace mapping for the element
         * automatically, as necessary.
         */
        String prefix = elemStack.getPrefix();
        String uri = elemStack.getNsURI();
        writeStartElement(prefix, elemStack.getLocalName(), uri);
        
        /* 04-Sep-2006, TSa: Although we could really just ignore all
         *   namespace declarations, some apps prefer (or even expect...)
         *   that ns bindings are preserved as much as possible. So, let's
         *   just try to output them as they are (could optimize and skip
         *   ones related to the start element [same prefix or URI], but
         *   for now let's not bother)
         */
        int nsCount = elemStack.getCurrentNsCount();
        if (nsCount > 0) { // yup, got some...
            for (int i = 0; i < nsCount; ++i) {
                writeNamespace(elemStack.getLocalNsPrefix(i), elemStack.getLocalNsURI(i));
            }
        }

        /* And then let's just output attributes, if any (whether to copy
         * implicit, aka "default" attributes, is configurable)
         */
        int attrCount = mCfgCopyDefaultAttrs ? ac.getCount() :  ac.getSpecifiedCount();

        /* Unlike in non-ns and simple-ns modes, we can not simply literally
         * copy the attributes here. It is possible that some namespace
         * prefixes have been remapped... so need to be bit more careful.
         */
        if (attrCount > 0) {
            for (int i = 0; i < attrCount; ++i) {
                // First; need to make sure that the prefix-to-ns mapping
                // attribute has is valid... and can not output anything
                // before that's done (since remapping will output a namespace
                // declaration!)
                uri = ac.getURI(i);
                prefix = ac.getPrefix(i);
                
                // With attributes, missing/empty prefix always means 'no
                // namespace', can take a shortcut:
                if (prefix == null || prefix.length() == 0) {
                    ;
                } else {
                    // and otherwise we'll always have a prefix as attributes
                    // can not make use of the def. namespace...
                    prefix = findOrCreateAttrPrefix(prefix, uri, mCurrElem);
                }
                /* Hmmh. Since the prefix we use may be different from what
                 * collector has, we can not use pass-through method of
                 * the collector, but need to call XmlWriter directly:
                 */
                if (prefix == null || prefix.length() == 0) {
                    mWriter.writeAttribute(ac.getLocalName(i), ac.getValue(i));
                } else {
                    mWriter.writeAttribute(prefix, ac.getLocalName(i), ac.getValue(i));
                }
            }
        }
    }

    public String validateQNamePrefix(QName name)
        throws XMLStreamException
    {
        /* Gets bit more complicated: we need to ensure that given URI
         * is properly bound...
         */
        String uri = name.getNamespaceURI();
        String suggPrefix = name.getPrefix();
        String actPrefix = validateElemPrefix(suggPrefix, uri, mCurrElem);
        if (actPrefix == null) { // no suitable prefix, must bind
            /* Need to ensure that we'll pass "" as prefix, not null, so
             * that it is understood as "I want to use the default NS", not
             * as "whatever prefix, I don't care"
             */
            if (suggPrefix == null) {
                suggPrefix = "";
            }
            actPrefix = generateElemPrefix(suggPrefix, uri, mCurrElem);
            if (actPrefix == null || actPrefix.length() == 0) { // def NS
                writeDefaultNamespace(uri);
            } else {
                writeNamespace(actPrefix, uri);
            }
        }
        return actPrefix;
    }

    /*
    ///////////////////////////////////////////////////////////
    // Internal methods
    ///////////////////////////////////////////////////////////
     */

    /**
     * Method called to find an existing prefix for the given namespace,
     * if any exists in the scope. If one is found, it's returned (including
     * "" for the current default namespace); if not, null is returned.
     *
     * @param nsURI URI of namespace for which we need a prefix
     */
    protected final String findElemPrefix(String nsURI, SimpleOutputElement elem)
        throws XMLStreamException
    {
        /* Special case: empty NS URI can only be bound to the empty
         * prefix...
         */
        if (nsURI == null || nsURI.length() == 0) {
            String currDefNsURI = elem.getDefaultNsUri();
            if (currDefNsURI != null && currDefNsURI.length() > 0) {
                // Nope; won't do... has to be re-bound, but not here:
                return null;
            }
            return "";
        }
        return mCurrElem.getPrefix(nsURI);
    }

    /**
     * Method called after {@link #findElemPrefix} has returned null,
     * to create and bind a namespace mapping for specified namespace.
     */
    protected final String generateElemPrefix(String suggPrefix, String nsURI,
                                              SimpleOutputElement elem)
        throws XMLStreamException
    {
        /* Ok... now, since we do not have an existing mapping, let's
         * see if we have a preferred prefix to use.
         */
        /* Except if we need the empty namespace... that can only be
         * bound to the empty prefix:
         */
        if (nsURI == null || nsURI.length() == 0) {
            return "";
        }

        /* Ok; with elements this is easy: the preferred prefix can
         * ALWAYS be used, since it can mask preceding bindings:
         */
        if (suggPrefix == null) {
            // caller wants this URI to map as the default namespace?
            if (mSuggestedDefNs != null && mSuggestedDefNs.equals(nsURI)) {
                suggPrefix = "";
            } else {
                suggPrefix = (mSuggestedPrefixes == null) ? null:
                    mSuggestedPrefixes.get(nsURI);
                if (suggPrefix == null) {
                    /* 16-Oct-2005, TSa: We have 2 choices here, essentially;
                     *   could make elements always try to override the def
                     *   ns... or can just generate new one. Let's do latter
                     *   for now.
                     */
                    if (mAutoNsSeq == null) {
                        mAutoNsSeq = new int[1];
                        mAutoNsSeq[0] = 1;
                    }
                    suggPrefix = elem.generateMapping(mAutomaticNsPrefix, nsURI,
                                                      mAutoNsSeq);
                }
            }
        }

        // Ok; let's let the caller deal with bindings
        return suggPrefix;
    }

    /**
     * Method called to somehow find a prefix for given namespace, to be
     * used for a new start element; either use an existing one, or
     * generate a new one. If a new mapping needs to be generated,
     * it will also be automatically bound, and necessary namespace
     * declaration output.
     *
     * @param suggPrefix Suggested prefix to bind, if any; may be null
     *   to indicate "no preference"
     * @param nsURI URI of namespace for which we need a prefix
     * @param elem Currently open start element, on which the attribute
     *   will be added.
     */
    protected final String findOrCreateAttrPrefix(String suggPrefix, String nsURI,
                                                  SimpleOutputElement elem)
        throws XMLStreamException
    {
        if (nsURI == null || nsURI.length() == 0) {
            /* Attributes never use the default namespace; missing
             * prefix always leads to the empty ns... so nothing
             * special is needed here.
             */
             return null;
        }
        // Maybe the suggested prefix is properly bound?
        if (suggPrefix != null) {
            int status = elem.isPrefixValid(suggPrefix, nsURI, false);
            if (status == SimpleOutputElement.PREFIX_OK) {
                return suggPrefix;
            }
            /* Otherwise, if the prefix is unbound, let's just bind
	     * it -- if caller specified a prefix, it probably prefers
	     * binding that prefix even if another prefix already existed?
	     * The remaining case (already bound to another URI) we don't
	     * want to touch, at least not yet: it may or not be safe
	     * to change binding, so let's just not try it.
	     */
            if (status == SimpleOutputElement.PREFIX_UNBOUND) {
		elem.addPrefix(suggPrefix, nsURI);
		doWriteNamespace(suggPrefix, nsURI);
		return suggPrefix;
	    }
        }

        // If not, perhaps there's another existing binding available?
        String prefix = elem.getExplicitPrefix(nsURI);
        if (prefix != null) { // already had a mapping for the URI... cool.
            return prefix;
        }

        /* Nope, need to create one. First, let's see if there's a
         * preference...
         */
        if (suggPrefix != null) {
            prefix = suggPrefix;
        } else if (mSuggestedPrefixes != null) {
            prefix = mSuggestedPrefixes.get(nsURI);
	    // note: def ns is never added to suggested prefix map
        }

        if (prefix != null) {
            /* Can not use default namespace for attributes.
             * Also, re-binding is tricky for attributes; can't
             * re-bind anything that's bound on this scope... or
             * used in this scope. So, to simplify life, let's not
             * re-bind anything for attributes.
             */
            if (prefix.length() == 0
                || (elem.getNamespaceURI(prefix) != null)) {
                prefix = null;
            }
        }

        if (prefix == null) {
            if (mAutoNsSeq == null) {
                mAutoNsSeq = new int[1];
                mAutoNsSeq[0] = 1;
            }
            prefix = mCurrElem.generateMapping(mAutomaticNsPrefix, nsURI,
                                               mAutoNsSeq);
        }

        // Ok; so far so good: let's now bind and output the namespace:
        elem.addPrefix(prefix, nsURI);
        doWriteNamespace(prefix, nsURI);
        return prefix;
    }

    private final String validateElemPrefix(String prefix, String nsURI,
                                            SimpleOutputElement elem)
        throws XMLStreamException
    {
        /* 06-Feb-2005, TSa: Special care needs to be taken for the
         *   "empty" (or missing) namespace:
         *   (see comments from findOrCreatePrefix())
         */
        if (nsURI == null || nsURI.length() == 0) {
            String currURL = elem.getDefaultNsUri();
            if (currURL == null || currURL.length() == 0) {
                // Ok, good:
                return "";
            }
            // Nope, needs to be re-bound:
            return null;
        }
        
        int status = elem.isPrefixValid(prefix, nsURI, true);
        if (status == SimpleOutputElement.PREFIX_OK) {
            return prefix;
        }

        /* Hmmh... now here's bit of dilemma: that particular prefix is
         * either not bound, or is masked... but it is possible some other
         * prefix would be bound. Should we search for another one, or
         * try to re-define suggested one? Let's do latter, for now;
         * caller can then (try to) bind the preferred prefix:
         */
        return null;
    }
}
