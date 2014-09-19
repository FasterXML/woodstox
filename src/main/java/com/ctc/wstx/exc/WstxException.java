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

package com.ctc.wstx.exc;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import com.ctc.wstx.util.StringUtil;

/**
 * Base class for all implementatations of {@link XMLStreamException}
 * Wstx uses.
 */
@SuppressWarnings("serial")
public class WstxException
    extends XMLStreamException
{
    /**
     * D'oh. Super-class munges and hides the message, have to duplicate here
     */
    final protected String mMsg;

    public WstxException(String msg) {
        super(msg);
        mMsg = msg;
    }

    public WstxException(Throwable th) {
        super(th.getMessage(), th);
        mMsg = th.getMessage();
    }

    public WstxException(String msg, Location loc) {
        super(msg, loc);
        mMsg = msg;
    }

    public WstxException(String msg, Location loc, Throwable th) {
        super(msg, loc, th);
        mMsg = msg;
    }

    /**
     * Method is overridden for two main reasons: first, default method
     * does not display public/system id information, even if it exists, and
     * second, default implementation can not handle nested Location
     * information.
     */
    public String getMessage()
    {
        String locMsg = getLocationDesc();
        /* Better not use super's message if we do have location information,
         * since parent's message contains (part of) Location
         * info; something we can regenerate better...
         */
        if (locMsg == null) {
            return super.getMessage();
        }
        StringBuilder sb = new StringBuilder(mMsg.length() + locMsg.length() + 20);
        sb.append(mMsg);
        StringUtil.appendLF(sb);
        sb.append(" at ");
        sb.append(locMsg);
        return sb.toString();
    }

    public String toString()
    {
        return getClass().getName()+": "+getMessage();
    }

    /*
    ////////////////////////////////////////////////////////
    // Internal methods:
    ////////////////////////////////////////////////////////
     */

    protected String getLocationDesc()
    {
        Location loc = getLocation();
        return (loc == null) ? null : loc.toString();
    }
}
