/* Woodstox XML processor
 *
 * Copyright (c) 2005 Tatu Saloranta, tatu.saloranta@iki.fi
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

import java.util.*;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;

import com.ctc.wstx.util.DataUtil;

/**
 * Helper class that implements "bijective map" (Map that allows use of values
 * as keys and vice versa, bidirectional access), and is specifically
 * used for storing namespace binding information.
 * One thing worth noting is that Strings stored are NOT assumed to have
 * been unified (interned) -- if they were, different implementation would
 * be more optimal.
 *<p>
 * Currently only used by stream writers, but could be more generally useful
 * too.
 */

public final class BijectiveNsMap
{
    /*
    ///////////////////////////////////////////////
    // Constants
    ///////////////////////////////////////////////
     */

    /**
     * Let's plan for having up to 14 explicit namespace declarations (2
     * defaults, for 'xml' and 'xmlns', are pre-populated)
     */
    final static int DEFAULT_ARRAY_SIZE = 2 * 16;

    /*
    ///////////////////////////////////////////////
    // Member vars
    ///////////////////////////////////////////////
     */

    final int mScopeStart;

    /**
     * Array that contains { prefix, ns-uri } pairs, up to (but not including)
     * index {@link #mScopeEnd}.
     */
    String[] mNsStrings;

    int mScopeEnd;

    /*
    ///////////////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////////////
     */

    private BijectiveNsMap(int scopeStart, String[] strs)
    {
        mScopeStart = mScopeEnd = scopeStart;
        mNsStrings = strs;
    }

    public static BijectiveNsMap createEmpty()
    {
        String[] strs = new String[DEFAULT_ARRAY_SIZE];

        strs[0] = XMLConstants.XML_NS_PREFIX;
        strs[1] = XMLConstants.XML_NS_URI;
        strs[2] = XMLConstants.XMLNS_ATTRIBUTE;
        strs[3] = XMLConstants.XMLNS_ATTRIBUTE_NS_URI;

        /* Let's consider pre-defined ones to be 'out of scope', i.e.
         * conceptually be part of (missing) parent's mappings.
         */
        return new BijectiveNsMap(4, strs);
    }

    public BijectiveNsMap createChild() {
        return new BijectiveNsMap(mScopeEnd, mNsStrings);
    }

    /*
    ///////////////////////////////////////////////
    // Public API, accessors
    ///////////////////////////////////////////////
     */

    public String findUriByPrefix(String prefix)
    {
        /* This is quite simple: just need to locate the last mapping
         * for the prefix, if any:
         */
        String[] strs = mNsStrings;
        int phash = prefix.hashCode();

        for (int ix = mScopeEnd - 2; ix >= 0; ix -= 2) {
            String thisP = strs[ix];
            if (thisP == prefix ||
                (thisP.hashCode() == phash && thisP.equals(prefix))) {
                return strs[ix+1];
            }
        }
        return null;
    }

    public String findPrefixByUri(String uri)
    {
        /* Finding a valid binding for the given URI is trickier, since
         * mappings can be masked by others... so, we need to first find
         * most recent binding, from the freshest one, and then verify
         * it's still unmasked; if not, continue with the first loop,
         * and so on.
         */

        String[] strs = mNsStrings;
        int uhash = uri.hashCode();

        main_loop:
        for (int ix = mScopeEnd - 1; ix > 0; ix -= 2) {
            String thisU = strs[ix];
            if (thisU == uri ||
                (thisU.hashCode() == uhash && thisU.equals(uri))) {
                // match, but has it been masked?
                String prefix = strs[ix-1];
                /* only need to check, if it wasn't within current scope
                 * (no masking allowed within scopes)
                 */
                if (ix < mScopeStart) {
                    int phash = prefix.hashCode();
                    for (int j = ix+1, end = mScopeEnd; j < end; j += 2) {
                        String thisP = strs[j];
                        if (thisP == prefix ||
                            (thisP.hashCode() == phash && thisP.equals(prefix))) {
                            // Masking... got to continue the main loop:
                            continue main_loop;
                        }
                    }
                }
                // Ok, unmasked one, can return
                return prefix;
            }
        }
        return null;
    }

    public List<String> getPrefixesBoundToUri(String uri, List<String> l)
    {
        /* Same problems (masking) apply here, as well as with
         * findPrefixByUri...
         */
        String[] strs = mNsStrings;
        int uhash = uri.hashCode();

        main_loop:
        for (int ix = mScopeEnd - 1; ix > 0; ix -= 2) {
            String thisU = strs[ix];
            if (thisU == uri ||
                (thisU.hashCode() == uhash && thisU.equals(uri))) {
                // match, but has it been masked?
                String prefix = strs[ix-1];
                /* only need to check, if it wasn't within current scope
                 * (no masking allowed within scopes)
                 */
                if (ix < mScopeStart) {
                    int phash = prefix.hashCode();
                    for (int j = ix+1, end = mScopeEnd; j < end; j += 2) {
                        String thisP = strs[j];
                        if (thisP == prefix ||
                            (thisP.hashCode() == phash && thisP.equals(prefix))) {
                            // Masking... got to continue the main loop:
                            continue main_loop;
                        }
                    }
                }
                // Ok, unmasked one, can add
                if (l == null) {
                    l = new ArrayList<String>();
                }
                l.add(prefix);
            }
        }
        return l;
    }

    public int size() {
        return (mScopeEnd >> 1);
    }

    public int localSize() {
        return ((mScopeEnd - mScopeStart) >> 1);
    }

    /*
    ///////////////////////////////////////////////
    // Public API, mutators
    ///////////////////////////////////////////////
     */

    /**
     * Method to add a new prefix-to-URI mapping for the current scope.
     * Note that it should NOT be used for the default namespace
     * declaration
     *
     * @param prefix Prefix to bind
     * @param uri URI to bind to the prefix
     *
     * @return If the prefix was already bound, the URI it was bound to:
     *   null if it's a new binding for the current scope.
     */
    public String addMapping(String prefix, String uri)
    {
        String[] strs = mNsStrings;
        int phash = prefix.hashCode();

        for (int ix = mScopeStart, end = mScopeEnd; ix < end; ix += 2) {
            String thisP = strs[ix];
            if (thisP == prefix ||
                (thisP.hashCode() == phash && thisP.equals(prefix))) {
                // Overriding an existing mapping
                String old = strs[ix+1];
                strs[ix+1] = uri;
                return old;
            }
        }
        // no previous binding, let's just add it at the end
        if (mScopeEnd >= strs.length) {
            // let's just double the array sizes...
            strs = DataUtil.growArrayBy(strs, strs.length);
            mNsStrings = strs;
        }
        strs[mScopeEnd++] = prefix;
        strs[mScopeEnd++] = uri;

        return null;
    }

    /**
     * Method used to add a dynamic binding, and return the prefix
     * used to bind the specified namespace URI.
     */
    public String addGeneratedMapping(String prefixBase, NamespaceContext ctxt,
                                      String uri, int[] seqArr)
    {
        String[] strs = mNsStrings;
        int seqNr = seqArr[0];
        String prefix;

        main_loop:
        while (true) {
            /* We better intern the resulting prefix? Or not?
             * TODO: maybe soft cache these for other docs?
             */
            prefix = (prefixBase + seqNr).intern();
            ++seqNr;

            /* Ok, let's see if we have a mapping (masked or not) for
             * the prefix. If we do, let's just not use it: we could
             * of course mask it (unless it's in current scope), but
             * it's easier to just get a "virgin" prefix...
             */
            int phash = prefix.hashCode();
            
            for (int ix = mScopeEnd - 2; ix >= 0; ix -= 2) {
                String thisP = strs[ix];
                if (thisP == prefix ||
                    (thisP.hashCode() == phash && thisP.equals(prefix))) {
                    continue main_loop;
                }
            }
            /* So far so good... but do we have a root context that might
             * have something too?
             */

            if (ctxt != null && ctxt.getNamespaceURI(prefix) != null) {
                continue;
            }
            break;
        }
        seqArr[0] = seqNr;

        // Ok, good; then let's just add it in...
        if (mScopeEnd >= strs.length) {
            // let's just double the array sizes...
            strs = DataUtil.growArrayBy(strs, strs.length);
            mNsStrings = strs;
        }
        strs[mScopeEnd++] = prefix;
        strs[mScopeEnd++] = uri;

        return prefix;
    }

    /*
    ///////////////////////////////////////////////
    // Standard overridden methods
    ///////////////////////////////////////////////
     */

    public String toString() {
        return "["+getClass().toString()+"; "+size()+" entries; of which "
            +localSize()+" local]";
    }
}
