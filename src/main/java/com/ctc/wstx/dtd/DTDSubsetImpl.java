/* Woodstox XML processor
 *
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

package com.ctc.wstx.dtd;

import java.text.MessageFormat;
import java.util.*;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.NotationDeclaration;

import org.codehaus.stax2.validation.*;

import com.ctc.wstx.cfg.ErrorConsts;
import com.ctc.wstx.ent.EntityDecl;
import com.ctc.wstx.exc.WstxParsingException;
import com.ctc.wstx.sr.InputProblemReporter;
import com.ctc.wstx.util.DataUtil;
import com.ctc.wstx.util.PrefixedName;

/**
 * The default implementation of {@link DTDSubset}
 */
public final class DTDSubsetImpl
    extends DTDSubset
{
    /**
     * Whether this subset is cachable. Only those external
     * subsets that do not refer to PEs defined by internal subsets (or
     * GEs via default attribute value expansion) are cachable.
     */
    final boolean mIsCachable;

    /**
     * Whether this subset has full validation information; and
     * consequently whether it will do actual validation, or just allow
     * access to type information, notations, entities, and add default
     * attribute values.
     */
    final boolean mFullyValidating;

    /**
     * Flag that indicates whether any of the elements declarared
     * has any attribute default values for namespace pseudo-attributes.
     */
    final boolean mHasNsDefaults;

    /*
    //////////////////////////////////////////////////////
    // Entity information
    //////////////////////////////////////////////////////
     */

    /**
     * Map (name-to-EntityDecl) of general entity declarations (internal,
     * external) for this DTD subset.
     */
    final HashMap<String,EntityDecl> mGeneralEntities;

    /**
     * Lazily instantiated List that contains all notations from
     * {@link #mGeneralEntities} (preferably in their declaration order; depends
     * on whether platform, ie. JDK version, has insertion-ordered
     * Maps available), used by DTD event Objects.
     */
    volatile transient List<EntityDecl> mGeneralEntityList = null;

    /**
     * Set of names of general entities references by this subset. Note that
     * only those GEs that are referenced by default attribute value
     * definitions count, since GEs in text content are only expanded
     * when reading documents, but attribute default values are expanded
     * when reading DTD subset itself.
     *<p>
     * Needed
     * for determinining if external subset materially depends on definitions
     * from internal subset; if so, such subset is not cachable.
     * This also
     * means that information is not stored for non-cachable instance.
     */
    final Set<String> mRefdGEs;

    // // // Parameter entity info:

    /**
     * Map (name-to-WEntityDeclaration) that contains all parameter entities
     * defined by this subset. May be empty if such information will not be
     * needed for use; for example, external subset's definitions are needed,
     * nor are combined DTD set's.
     */
    final HashMap<String,EntityDecl> mDefinedPEs;

    /**
     * Set of names of parameter entities references by this subset. Needed
     * when determinining if external subset materially depends on definitions
     * from internal subset, which is needed to know when caching external
     * subsets.
     *<p>
     * Needed
     * for determinining if external subset materially depends on definitions
     * from internal subset; if so, such subset is not cachable.
     * This also
     * means that information is not stored for non-cachable instance.
     */
    final Set<String> mRefdPEs;

    /*
    //////////////////////////////////////////////////////
    // Notation definitions:
    //////////////////////////////////////////////////////
     */

    /**
     * Map (name-to-NotationDecl) that this subset has defined.
     */
    final HashMap<String,NotationDeclaration> mNotations;

    /**
     * Lazily instantiated List that contains all notations from
     * {@link #mNotations} (preferably in their declaration order; depends
     * on whether platform, ie. JDK version, has insertion-ordered
     * Maps available), used by DTD event Objects.
     */
    transient List<NotationDeclaration> mNotationList = null;


    /*
    //////////////////////////////////////////////////////
    // Element definitions:
    //////////////////////////////////////////////////////
     */

    final HashMap<PrefixedName,DTDElement> mElements;

    /*
    //////////////////////////////////////////////////////
    // Life-cycle
    //////////////////////////////////////////////////////
     */

    private DTDSubsetImpl(boolean cachable,
                          HashMap<String,EntityDecl> genEnt, Set<String> refdGEs,
                          HashMap<String,EntityDecl> paramEnt, Set<String> peRefs,
                          HashMap<String,NotationDeclaration> notations, HashMap<PrefixedName,DTDElement> elements,
                          boolean fullyValidating)
    {
        mIsCachable = cachable;
        mGeneralEntities = genEnt;
        mRefdGEs = refdGEs;
        mDefinedPEs = paramEnt;
        mRefdPEs = peRefs;
        mNotations = notations;
        mElements = elements;
        mFullyValidating = fullyValidating;

        boolean anyNsDefs = false;
        if (elements != null) {
        	for (DTDElement elem : elements.values()) {
                if (elem.hasNsDefaults()) {
                    anyNsDefs = true;
                    break;
                }
            }
        }
        mHasNsDefaults = anyNsDefs;
    }

    public static DTDSubsetImpl constructInstance(boolean cachable,
                                                  HashMap<String,EntityDecl> genEnt, Set<String> refdGEs,
                                                  HashMap<String,EntityDecl> paramEnt, Set<String> refdPEs,
                                                  HashMap<String,NotationDeclaration> notations,
                                                  HashMap<PrefixedName,DTDElement> elements,
                                                  boolean fullyValidating)
    {
        return new DTDSubsetImpl(cachable, genEnt, refdGEs,
                                 paramEnt, refdPEs,
                                 notations, elements,
                                 fullyValidating);
    }

    /**
     * Method that will combine definitions from internal and external subsets,
     * producing a single DTD set.
     */
    @Override
    public DTDSubset combineWithExternalSubset(InputProblemReporter rep, DTDSubset extSubset)
        throws XMLStreamException
    {
        /* First let's see if we can just reuse GE Map used by int or ext
         * subset; (if only one has contents), or if not, combine them.
         */
        HashMap<String,EntityDecl> ge1 = getGeneralEntityMap();
        HashMap<String,EntityDecl> ge2 = extSubset.getGeneralEntityMap();
        if (ge1 == null || ge1.isEmpty()) {
            ge1 = ge2;
        } else {
            if (ge2 != null && !ge2.isEmpty()) {
                /* Internal subset Objects are never shared or reused (and by
                 * extension, neither are objects they contain), so we can just
                 * modify GE map if necessary
                 */
                combineMaps(ge1, ge2);
            }
        }

        // Ok, then, let's combine notations similarly
        HashMap<String,NotationDeclaration> n1 = getNotationMap();
        HashMap<String,NotationDeclaration> n2 = extSubset.getNotationMap();
        if (n1 == null || n1.isEmpty()) {
            n1 = n2;
        } else {
            if (n2 != null && !n2.isEmpty()) {
                /* First; let's make sure there are no colliding notation
                 * definitions: it's an error to try to redefine notations.
                 */
                checkNotations(n1, n2);

                /* Internal subset Objects are never shared or reused (and by
                 * extension, neither are objects they contain), so we can just
                 * modify notation map if necessary
                 */
                combineMaps(n1, n2);
            }
        }


        // And finally elements, rather similarly:
        HashMap<PrefixedName,DTDElement> e1 = getElementMap();
        HashMap<PrefixedName,DTDElement> e2 = extSubset.getElementMap();
        if (e1 == null || e1.isEmpty()) {
            e1 = e2;
        } else {
            if (e2 != null && !e2.isEmpty()) {
                /* Internal subset Objects are never shared or reused (and by
                 * extension, neither are objects they contain), so we can just
                 * modify element map if necessary
                 */
                combineElements(rep, e1, e2);
            }
        }

        /* Combos are not cachable, and because of that, there's no point
         * in storing any PE info either.
         */
        return constructInstance(false, ge1, null, null, null, n1, e1,
                                 mFullyValidating);
    }

    /*
    //////////////////////////////////////////////////////
    // XMLValidationSchema implementation
    //////////////////////////////////////////////////////
     */

    @Override
    public XMLValidator createValidator(ValidationContext ctxt)
        throws XMLStreamException
    {
        if (mFullyValidating) {
            return new DTDValidator(this, ctxt, mHasNsDefaults,
                                    getElementMap(), getGeneralEntityMap());
        }
        return new DTDTypingNonValidator(this, ctxt, mHasNsDefaults,
                                         getElementMap(), getGeneralEntityMap());

    }

    /*
    //////////////////////////////////////////////////////
    // DTDValidationSchema implementation
    //////////////////////////////////////////////////////
     */

    @Override
    public int getEntityCount() {
        return (mGeneralEntities == null) ? 0 : mGeneralEntities.size();
    }

    @Override
    public int getNotationCount() {
        return (mNotations == null) ? 0 : mNotations.size();
    }

    /*
    //////////////////////////////////////////////////////
    // Woodstox-specific public API
    //////////////////////////////////////////////////////
     */

    @Override
    public boolean isCachable() {
        return mIsCachable;
    }
    
    @Override
    public HashMap<String,EntityDecl> getGeneralEntityMap() {
        return mGeneralEntities;
    }

    @Override
    public List<EntityDecl> getGeneralEntityList()
    {
        List<EntityDecl> l = mGeneralEntityList;
        if (l == null) {
            if (mGeneralEntities == null || mGeneralEntities.size() == 0) {
                l = Collections.emptyList();
            } else {
                l = Collections.unmodifiableList(new ArrayList<EntityDecl>(mGeneralEntities.values()));
            }
            mGeneralEntityList = l;
        }

        return l;
    }

    @Override
    public HashMap<String,EntityDecl> getParameterEntityMap() {
        return mDefinedPEs;
    }

    @Override
    public HashMap<String,NotationDeclaration> getNotationMap() {
        return mNotations;
    }

    @Override
    public synchronized List<NotationDeclaration> getNotationList()
    {
        List<NotationDeclaration> l = mNotationList;
        if (l == null) {
            if (mNotations == null || mNotations.size() == 0) {
                l = Collections.emptyList();
            } else {
                l = Collections.unmodifiableList(new ArrayList<NotationDeclaration>(mNotations.values()));
            }
            mNotationList = l;
        }

        return l;
    }

    @Override
    public HashMap<PrefixedName,DTDElement> getElementMap() {
        return mElements;
    }

    /**
     * Method used in determining whether cached external subset instance
     * can be used with specified internal subset. If ext. subset references
     * any parameter/general entities int subset (re-)defines, it can not;
     * otherwise it can be used.
     *
     * @return True if this (external) subset refers to a parameter entity
     *    defined in passed-in internal subset.
     */
    @Override
    public boolean isReusableWith(DTDSubset intSubset)
    {
        Set<String> refdPEs = mRefdPEs;

        if (refdPEs != null && refdPEs.size() > 0) {
            HashMap<String,EntityDecl> intPEs = intSubset.getParameterEntityMap();
            if (intPEs != null && intPEs.size() > 0) {
                if (DataUtil.anyValuesInCommon(refdPEs, intPEs.keySet())) {
                    return false;
                }
            }
        }
        Set<String> refdGEs = mRefdGEs;

        if (refdGEs != null && refdGEs.size() > 0) {
            HashMap<String,EntityDecl> intGEs = intSubset.getGeneralEntityMap();
            if (intGEs != null && intGEs.size() > 0) {
                if (DataUtil.anyValuesInCommon(refdGEs, intGEs.keySet())) {
                    return false;
                }
            }
        }
        return true; // yep, no dependencies overridden
    }

    /*
    //////////////////////////////////////////////////////
    // Overridden default methods:
    //////////////////////////////////////////////////////
     */

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[DTDSubset: ");
        int count = getEntityCount();
        sb.append(count);
        sb.append(" general entities");
        sb.append(']');
        return sb.toString();
    }

    /*
    //////////////////////////////////////////////////////
    // Convenience methods used by other classes
    //////////////////////////////////////////////////////
     */

   public static void throwNotationException(NotationDeclaration oldDecl, NotationDeclaration newDecl)
        throws XMLStreamException
    {
        throw new WstxParsingException
            (MessageFormat.format(ErrorConsts.ERR_DTD_NOTATION_REDEFD,
                                  new Object[] {
                                  newDecl.getName(),
                                  oldDecl.getLocation().toString()}),
             newDecl.getLocation());
    }

   public static void throwElementException(DTDElement oldElem, Location loc)
        throws XMLStreamException
    {
        throw new WstxParsingException
            (MessageFormat.format(ErrorConsts.ERR_DTD_ELEM_REDEFD,
                                  new Object[] {
                                  oldElem.getDisplayName(),
                                  oldElem.getLocation().toString() }),
             loc);
    }

    /*
    //////////////////////////////////////////////////////
    // Internal methods
    //////////////////////////////////////////////////////
     */

    /**
     *<p>
     * Note: The first Map argument WILL be modified; second one
     * not. Caller needs to ensure this is acceptable.
     */
    private static <K,V> void combineMaps(Map<K,V> m1, Map<K,V> m2)
    {
    	for (Map.Entry<K,V> me : m2.entrySet()) {
            K key = me.getKey();
            /* Int. subset has precedence, but let's guess most of
             * the time there are no collisions:
             */
            V old = m1.put(key, me.getValue());
            // Oops, got value! Let's put it back
            if (old != null) {
                m1.put(key, old);
            }
        }
    }

    /**
     * Method that will try to merge in elements defined in the external
     * subset, into internal subset; it will also check for redeclarations
     * when doing this, as it's invalid to redeclare elements. Care has to
     * be taken to only check actual redeclarations: placeholders should
     * not cause problems.
     */
    private void combineElements(InputProblemReporter rep, HashMap<PrefixedName,DTDElement> intElems, HashMap<PrefixedName,DTDElement> extElems)
        throws XMLStreamException
    {
    	for (Map.Entry<PrefixedName,DTDElement> me : extElems.entrySet()) {
            PrefixedName key = me.getKey();
            DTDElement extElem = me.getValue();
            DTDElement intElem = intElems.get(key);

            // If there was no old value, can just merge new one in and continue
            if (intElem == null) {
                intElems.put(key, extElem);
                continue;
            }

            // Which one is defined (if either)?
            if (extElem.isDefined()) { // one from the ext subset
                if (intElem.isDefined()) { // but both can't be; that's an error
                    throwElementException(intElem, extElem.getLocation());
                } else {
                    /* Note: can/should not modify the external element (by
                     * for example adding attributes); external element may
                     * be cached and shared... so, need to do the reverse,
                     * define the one from internal subset.
                     */
                    intElem.defineFrom(rep, extElem, mFullyValidating);
                }
            } else {
                if (!intElem.isDefined()) {
                    /* ??? Should we warn about neither of them being really
                     *   declared?
                     */
                    rep.reportProblem(intElem.getLocation(),
                                      ErrorConsts.WT_ENT_DECL,
                                      ErrorConsts.W_UNDEFINED_ELEM,
                                      extElem.getDisplayName(), null);
                                      
                } else {
                    intElem.mergeMissingAttributesFrom(rep, extElem, mFullyValidating);
                }
            }
        }
    }

    private static void checkNotations(HashMap<String,NotationDeclaration> fromInt, HashMap<String,NotationDeclaration> fromExt)
        throws XMLStreamException
    {
        /* Since it's external subset that would try to redefine things
         * defined in internal subset, let's traverse definitions in
         * the ext. subset first (even though that may not be the fastest
         * way), so that we have a chance of catching the first problem
         * (As long as Maps iterate in insertion order).
         */
    	for (Map.Entry<String, NotationDeclaration> en : fromExt.entrySet()) {
            if (fromInt.containsKey(en.getKey())) {
                throwNotationException(fromInt.get(en.getKey()), en.getValue());
            }
        }
    }
}
