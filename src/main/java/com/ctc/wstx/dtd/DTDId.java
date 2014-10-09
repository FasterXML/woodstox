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

package com.ctc.wstx.dtd;

import java.net.URI;

/**
 * Simple key object class, used for accessing (external) DTDs when stored for
 * caching. Main idea is that the primary id of a DTD (public or system id;
 * latter normalized if possible)
 * has to match, as well as couple of on/off settings for parsing (namespace
 * support, text normalization).
 * Latter restriction is needed since although DTDs do not deal
 * with (or understand) namespaces, some parsing is done to be able to validate
 * namespace aware/non-aware documents, and handling differs between the two.
 * As to primary key part, public id is used if one was defined; if so,
 * comparison is String equality. If not, then system id is compared: system
 * id has to be expressed as URL if so.
 */
public final class DTDId
{
    protected final String mPublicId;

    protected final URI mSystemId;

    protected final int mConfigFlags;

    protected final boolean mXml11;

    protected int mHashCode = 0;

    /*
    ///////////////////////////////////////////////////////////////////////
    // Life-cycle:
    ///////////////////////////////////////////////////////////////////////
     */

    private DTDId(String publicId, URI systemId, int configFlags, boolean xml11)
    {
        mPublicId = publicId;
        mSystemId = systemId;
        mConfigFlags = configFlags;
        mXml11 = xml11;
    }

    public static DTDId constructFromPublicId(String publicId, int configFlags,
                                              boolean xml11)
    {
        if (publicId == null || publicId.length() == 0) {
            throw new IllegalArgumentException("Empty/null public id.");
        }
        return new DTDId(publicId, null, configFlags, xml11);
    }

    public static DTDId constructFromSystemId(URI systemId, int configFlags,
                                              boolean xml11)
    {
        if (systemId == null) {
            throw new IllegalArgumentException("Null system id.");
        }
        return new DTDId(null, systemId, configFlags, xml11);
    }

    public static DTDId construct(String publicId, URI systemId, int configFlags, boolean xml11)
    {
        if (publicId != null && publicId.length() > 0) {
            return new DTDId(publicId, null, configFlags, xml11);
        }
        if (systemId == null) {
            throw new IllegalArgumentException("Illegal arguments; both public and system id null/empty.");
        }
        return new DTDId(null, systemId, configFlags, xml11);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Overridden standard methods
    ///////////////////////////////////////////////////////////////////////
     */

    public int hashCode() {
        int hash = mHashCode;
        if (hash == 0) {
            hash = mConfigFlags;
            if (mPublicId != null) {
                hash ^= mPublicId.hashCode();
            } else {
                hash ^= mSystemId.hashCode();
            }
            if (mXml11) {
                hash ^= 1;
            }
            mHashCode = hash;
        }
        return hash;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer(60);
        sb.append("Public-id: ");
        sb.append(mPublicId);
        sb.append(", system-id: ");
        sb.append(mSystemId);
        sb.append(" [config flags: 0x");
        sb.append(Integer.toHexString(mConfigFlags));
        sb.append("], xml11: ");
        sb.append(mXml11);
        return sb.toString();
    }

    public boolean equals(Object o)
    {
        if (o == this) return true;
        if (o == null || o.getClass() != getClass()) return false;
        DTDId other = (DTDId) o;
        if (other.mConfigFlags != mConfigFlags
            || other.mXml11 != mXml11) {
            return false;
        }
        if (mPublicId != null) {
            String op = other.mPublicId;
            return (op != null) && op.equals(mPublicId);
        }
        return mSystemId.equals(other.mSystemId);
    }
}
