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

/**
 * Container for information collected regarding a single
 * (start) element instance.
 *<p>
 * This class is not exposed outside of the package and is considered
 * part of internal implementation.
 *
 * @since 4.1
 */
final class Element
{
    // // // Element name

    protected String mLocalName;

    /**
     * Prefix this element has, if any; null if none
     */
    protected String mPrefix;

    /**
     * Namespace this element is in
     */
    protected String mNamespaceURI;

    /**
     * Default namespace for this element.
     */
    protected String mDefaultNsURI;

    // // // Namespace support

    /**
     * Offset within namespace array, maintained by
     * {@link InputElementStack} that owns this element.
     */
    protected int mNsOffset;

    // // // Back links to parent element(s)

    /**
     * Parent element, if any; null for root
     */
    protected Element mParent;

    /**
     * Count of child elements
     */
    protected int mChildCount;

    /*
    /////////////////////////////////////////////////////////
    // Life-cycle
    /////////////////////////////////////////////////////////
     */

    public Element(Element parent, int nsOffset, String prefix, String ln)
    {
        mParent = parent;
        mNsOffset = nsOffset;
        mPrefix = prefix;
        mLocalName = ln;
    }

    public void reset(Element parent, int nsOffset, String prefix, String ln)
    {
        mParent = parent;
        mNsOffset = nsOffset;
        mPrefix = prefix;
        mLocalName = ln;
        mChildCount = 0;
    }

    /**
     * Method called to temporarily "store" this Element for later reuse.
     */
    public void relink(Element next)
    {
        mParent = next;
    }
}

