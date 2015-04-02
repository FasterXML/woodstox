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

import javax.xml.namespace.QName;

/**
 * Simple key Object to be used for storing/accessing of potentially namespace
 * scoped element and attribute names.
 *<p>
 * One important note about usage is that two of the name components (prefix
 * and local name) HAVE to have been interned some way, as all comparisons
 * are done using identity comparison; whereas URI is NOT necessarily
 * interned.
 *<p>
 * Note that the main reason this class is mutable -- unlike most key classes
 * -- is that this allows reusing key objects for access, as long as the code
 * using it knows ramifications of trying to modify a key that's used
 * in a data structure.
 *<p>
 * Note, too, that the hash code is cached as this class is mostly used as
 * a Map key, and hash code is used a lot.
 */
public final class PrefixedName
    implements Comparable<PrefixedName> // to allow alphabetic ordering
{
    private String mPrefix, mLocalName;

    volatile int mHash = 0;

    /*
    ///////////////////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////////////////
     */

    public PrefixedName(String prefix, String localName)
    {
        mLocalName = localName;
        mPrefix = (prefix != null && prefix.length() == 0) ?
            null : prefix;
    }

    public PrefixedName reset(String prefix, String localName)
    {
        mLocalName = localName;
        mPrefix = (prefix != null && prefix.length() == 0) ?
            null : prefix;
        mHash = 0;
        return this;
    }

    public static PrefixedName valueOf(QName n)
    {
        return new PrefixedName(n.getPrefix(), n.getLocalPart());
    }

    /*
    ///////////////////////////////////////////////////
    // Accessors:
    ///////////////////////////////////////////////////
     */

    public String getPrefix() { return mPrefix; }

    public String getLocalName() { return mLocalName; }

    /**
     * @return True, if this attribute name would result in a namespace
     *    binding (ie. it's "xmlns" or starts with "xmlns:").
     */
    public boolean isaNsDeclaration()
    {
        if (mPrefix == null) {
            return mLocalName == "xmlns";
        }
        return mPrefix == "xmlns";
    }

    /**
     * Method used to check for xml reserved attribute names, like
     * "xml:space" and "xml:id".
     *<p>
     * Note: it is assumed that the passed-in localName is also
     * interned.
     */
    public boolean isXmlReservedAttr(boolean nsAware, String localName)
    {
        if (nsAware) {
            if ("xml" == mPrefix) {
                return mLocalName == localName;
            }
        } else {
            if (mLocalName.length() == (4 + localName.length())) {
                return (mLocalName.startsWith("xml:")
                        && mLocalName.endsWith(localName));
            }
        }
        return false;
    }

    /*
    ///////////////////////////////////////////////////
    // Overridden standard methods:
    ///////////////////////////////////////////////////
     */
    
    @Override
    public String toString()
    {
        if (mPrefix == null || mPrefix.length() == 0) {
            return mLocalName;
        }
        StringBuilder sb = new StringBuilder(mPrefix.length() + 1 + mLocalName.length());
        sb.append(mPrefix);
        sb.append(':');
        sb.append(mLocalName);
        return sb.toString();
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this) {
            return true;
        }
        if (!(o instanceof PrefixedName)) { // also filters out nulls
            return false;
        }
        PrefixedName other = (PrefixedName) o;

        if (mLocalName != other.mLocalName) { // assumes equality
            return false;
        }
        return (mPrefix == other.mPrefix);
    }

    @Override
    public int hashCode() {
        int hash = mHash;

        if (hash == 0) {
            hash = mLocalName.hashCode();
            if (mPrefix != null) {
                hash ^= mPrefix.hashCode();
            }
            mHash = hash;
        }
        return hash;
    }

    @Override
    public int compareTo(PrefixedName other)
    {
        // First, by prefix, then by local name:
        String op = other.mPrefix;

        // Missing prefix is ordered before existing prefix
        if (op == null || op.length() == 0) {
            if (mPrefix != null && mPrefix.length() > 0) {
                return 1;
            }
        } else if (mPrefix == null || mPrefix.length() == 0) {
            return -1;
        } else {
            int result = mPrefix.compareTo(op);
            if (result != 0) {
                return result;
            }
        }

        return mLocalName.compareTo(other.mLocalName);
    }
}
