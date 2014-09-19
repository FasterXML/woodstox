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

package com.ctc.wstx.sr;

import javax.xml.namespace.QName;

import com.ctc.wstx.compat.QNameCreator;

/**
 * Container for information collected regarding a single element
 * attribute instance. Used for both regular explicit attributes
 * and values added via attribute value defaulting.
 *<p>
 * This class is not exposed outside of the package and is considered
 * part of internal implementation.
 *
 * @since 4.1
 */
final class Attribute
{
    // // // Name information

    protected String mLocalName;

    protected String mPrefix;

    protected String mNamespaceURI;

    // // // Value information

    /**
     * Numeric offset within text builder that denotes pointer
     * to the first character of the value for this attribute
     * (or namespace). End offset is derived by looking at
     * start pointer of the following attribute; or total
     * length for the last entry
     */
    protected int mValueStartOffset;

    /**
     * Value as a String iff it has been requested once; stored
     * here in case it will be accessed again.
     */
    protected String mReusableValue;

    /*
    //////////////////////////////////////////////////
    // Life-cycle
    //////////////////////////////////////////////////
     */

    public Attribute(String prefix, String localName, int valueStart)
    {
        mLocalName = localName;
        mPrefix = prefix;
        mValueStartOffset = valueStart;
    }

    public void reset(String prefix, String localName, int valueStart)
    {
        mLocalName = localName;
        mPrefix = prefix;
        mValueStartOffset = valueStart;
        mNamespaceURI = null;
        mReusableValue = null;
    }

    /**
     * Method called to inject specific value for this attribute.
     */
    public void setValue(String value) {
        mReusableValue = value;
    }

    /*
    //////////////////////////////////////////////////
    // Accessors
    //////////////////////////////////////////////////
     */

    /**
     * @param uri Namespace URI of the attribute, if any; MUST be
     *   given as null if no namespace
     * @param localName Local name to match. Note: is NOT guaranteed
     *   to have been interned
     *
     * @return True if qualified name of this attribute is the same
     *   as what arguments describe
     */
    protected boolean hasQName(String uri, String localName)
    {
        if (localName != mLocalName && !localName.equals(mLocalName)) {
            return false;
        }
        if (mNamespaceURI == uri) {
            return true;
        }
        if (uri == null) {
            return (mNamespaceURI == null) || mNamespaceURI.length() == 0;
        }
        return (mNamespaceURI != null && uri.equals(mNamespaceURI));
    }

    public QName getQName()
    {
        if (mPrefix == null) {
            if (mNamespaceURI == null) {
                return new QName(mLocalName);
            }
            return new QName(mNamespaceURI, mLocalName);
        }
        String uri = mNamespaceURI;
        if (uri == null) { // Some QName impls (older JDKs) don't like nulls
            uri = "";
        }
        // For [WSTX-174] need to use indirection:
        return QNameCreator.create(uri, mLocalName, mPrefix);
    }

    /**
     * Method called if this attribute is the last one with value
     * in the buffer. If so, end value is implied
     */
    public String getValue(String allValues)
    {
        if (mReusableValue == null) {
            mReusableValue = (mValueStartOffset == 0) ?
                allValues : allValues.substring(mValueStartOffset);
        }
        return mReusableValue;
    }

    public String getValue(String allValues, int endOffset)
    {
        if (mReusableValue == null) {
            mReusableValue = allValues.substring(mValueStartOffset, endOffset);
        }
        return mReusableValue;
    }
}
