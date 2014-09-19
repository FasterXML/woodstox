/* Woodstox XML processor
 *
 * Copyright (c) 2004 Tatu Saloranta, tatu.saloranta@iki.fi
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

import java.util.*;

import com.ctc.wstx.util.PrefixedName;
import com.ctc.wstx.util.StringUtil;

/**
 * Validator class that is based on a DFA constructed from DTD content
 * specification.
 */
public final class DFAValidator
    extends StructValidator
{
    /**
     * For root validator instance, the start state of DFA; for other
     * instances, current state.
     */
    DFAState mState;

    public DFAValidator(DFAState initialState) {
        mState = initialState;
    }

    public StructValidator newInstance() {
        return new DFAValidator(mState);
    }

    public String tryToValidate(PrefixedName elemName)
    {
        // Do we have a follow state with that key?
        DFAState next = mState.findNext(elemName);

        if (next == null) {
            // Nope; let's show what we'd have expected instead...
            TreeSet<PrefixedName> names = mState.getNextNames();
            if (names.size() == 0) { // expected end tag?
                return "Expected $END";
            }

            // Either end tag, or another tag?
            if (mState.isAcceptingState()) {
                return "Expected <"+StringUtil.concatEntries(names, ">, <", null)+"> or $END";
            }
            return "Expected <"+StringUtil.concatEntries(names,
                                                         ">, <", "> or <")+">";
        }

        mState = next;
        return null;
    }
    
    public String fullyValid()
    {
        if (mState.isAcceptingState()) {
            return null;
        }
        TreeSet<PrefixedName> names = mState.getNextNames();
        return "Expected <"+StringUtil.concatEntries(names,
                                                     ">, <", "> or <")+">";
    }
}
