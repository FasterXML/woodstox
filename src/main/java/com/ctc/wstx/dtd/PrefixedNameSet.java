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

package com.ctc.wstx.dtd;

import com.ctc.wstx.util.PrefixedName;

public abstract class PrefixedNameSet
{
    protected PrefixedNameSet() { }

    /**
     * @return True if set contains more than one entry; false if not
     *   (empty or has one)
     */
    public abstract boolean hasMultiple();

    /**
     * @return True if the set contains specified name; false if not.
     */
    public abstract boolean contains(PrefixedName name);

    public abstract void appendNames(StringBuilder sb, String sep);

    public final String toString() {
        return toString(", ");
    }

    public final String toString(String sep) {
        StringBuilder sb = new StringBuilder();
        appendNames(sb, sep);
        return sb.toString();
    }
}
