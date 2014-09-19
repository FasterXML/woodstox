/* Woodstox XML processor
 *
 * Copyright (c) 2004- Tatu Saloranta, tatu.saloranta@iki.fi
 *
 * Licensed under the License specified in the file LICENSE which is
 * included with the source code.
 * You may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ctc.wstx.msv;

import org.codehaus.stax2.validation.ValidationContext;

/**
 * This is an implementation of SAX Attributes interface, that proxies
 * requests to the {@link ValidationContext}.
 * It is needed by some MSV components (specifically, W3C Schema Validator)
 * for limited access to attribute values during start element validation.
 */
final class AttributeProxy
    implements org.xml.sax.Attributes
{
    private final ValidationContext mContext;

    public AttributeProxy(ValidationContext ctxt)
    {
        mContext = ctxt;
    }

    /*
    ///////////////////////////////////////////////
    // Attributes implementation
    ///////////////////////////////////////////////
    */

    public int getIndex(String qName)
    {
        int cix = qName.indexOf(':');
        int acount = mContext.getAttributeCount();
        if (cix < 0) { // no prefix
            for (int i = 0; i < acount; ++i) {
                if (qName.equals(mContext.getAttributeLocalName(i))) {
                    String prefix = mContext.getAttributePrefix(i);
                    if (prefix == null || prefix.length() == 0) {
                        return i;
                    }
                }
            }
        } else {
            String prefix = qName.substring(0, cix);
            String ln = qName.substring(cix+1);

            for (int i = 0; i < acount; ++i) {
                if (ln.equals(mContext.getAttributeLocalName(i))) {
                    String p2 = mContext.getAttributePrefix(i);
                    if (p2 != null && prefix.equals(p2)) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    public int getIndex(String uri, String localName)
    {
        return mContext.findAttributeIndex(uri, localName);
    }

    public int getLength()
    {
        return mContext.getAttributeCount();
    }

    public String getLocalName(int index)
    {
        return mContext.getAttributeLocalName(index);
    }

    public String getQName(int index)
    {
        String prefix = mContext.getAttributePrefix(index);
        String ln = mContext.getAttributeLocalName(index);

        if (prefix == null || prefix.length() == 0) {
            return ln;
        }
        StringBuilder sb = new StringBuilder(prefix.length() + 1 + ln.length());
        sb.append(prefix);
        sb.append(':');
        sb.append(ln);
        return sb.toString();
    }

    public String getType(int index)
    {
        return mContext.getAttributeType(index);
    }

    public String getType(String qName)
    {
        return getType(getIndex(qName));
    }

    public String getType(String uri, String localName)
    {
        return getType(getIndex(uri, localName));
    }

    public String getURI(int index)
    {
        return mContext.getAttributeNamespace(index);
    }

    public String getValue(int index)
    {
        return mContext.getAttributeValue(index);
    }

    public String getValue(String qName)
    {
        return getValue(getIndex(qName));
    }

    public String getValue(String uri, String localName)     
    {
        return mContext.getAttributeValue(uri, localName);
    }
}


