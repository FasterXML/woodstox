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

/**
 * Class that represents a state in DFA used for validating complex
 * DTD content models.
 */
public final class DFAState
{
    private final int mIndex;
    private final boolean mAccepting;

    private BitSet mTokenSet;

    private final HashMap<PrefixedName,DFAState> mNext = new HashMap<>();

    /**
     * Size of the DFA (number of states plus number of transitions);
     * only set for the initial state.
     */
    private int mSize;

    /*
    ///////////////////////////////////////////////////////////
    // Life-cycle:
    ///////////////////////////////////////////////////////////
     */

    public DFAState(int index, BitSet tokenSet)
    {
        mIndex = index;
        // If we have a transition to state 0, it is an accepting state...
        mAccepting = tokenSet.get(0);
        mTokenSet = tokenSet;
    }

    public static DFAState constructDFA(ContentSpec rootSpec)
    {
        return constructDFA(rootSpec, Integer.MAX_VALUE);
    }

    /**
     * @param maxSize Maximum size of the DFA, as the number of states
     *   plus the number of transitions
     *
     * @return Initial state of the DFA; or {@code null} if the DFA would
     *   be bigger than {@code maxSize}
     *
     * @since 7.4
     */
    public static DFAState constructDFA(ContentSpec rootSpec, int maxSize)
    {
        // Let's first create the real model tree:
        ModelNode modelRoot = rootSpec.rewrite();

        /* Then we need to add the dummy end token, and concat node
         * to contain it:
         */
        TokenModel eofToken = TokenModel.getNullToken();
        ConcatModel dummyRoot = new ConcatModel(modelRoot, eofToken);

        /* then need to allocate index numbers for tokens 
         * (which will also calculate nullability)
         */
        ArrayList<TokenModel> tokens = new ArrayList<TokenModel>();
        tokens.add(eofToken); // has to be added first, explicitly
        dummyRoot.indexTokens(tokens);

        /* And then we can request calculation of follow pos; this will
         * also recursively calculate first/last pos as needed:
         */
        int flen = tokens.size();
        BitSet[] followPos = new BitSet[flen];
        PrefixedName[] tokenNames = new PrefixedName[flen];
        for (int i = 0; i < flen; ++i) {
            followPos[i] = new BitSet(flen);
            tokenNames[i] = tokens.get(i).getName();
        }
        dummyRoot.calcFollowPos(followPos);

        /* And then we can calculate DFA stuff. First step is to get
         * firstpos set for the root node, for creating the first
         * state:
         */
        BitSet initial = new BitSet(flen);
        dummyRoot.addFirstPos(initial);
        DFAState firstState = new DFAState(0, initial);
        ArrayList<DFAState> stateList = new ArrayList<DFAState>();
        stateList.add(firstState);
        HashMap<BitSet,DFAState> stateMap = new HashMap<BitSet,DFAState>();
        stateMap.put(initial, firstState);

        int i = 0;
        long transitions = 0L;
        while (i < stateList.size()) {
            DFAState curr = stateList.get(i++);
            curr.calcNext(tokenNames, followPos, stateList, stateMap);
            // Subset construction is exponential in the worst case
            transitions += curr.mNext.size();
            if (stateList.size() + transitions > maxSize) {
                return null;
            }
        }
        firstState.mSize = (int) (stateList.size() + transitions);

        // DEBUG:
        /*
        for (i = 0; i < stateList.size(); ++i) {
            //System.out.println(stateList.get(i));
        }
        */

        // And there we have it!
        return firstState;
    }    

    /*
    ///////////////////////////////////////////////////////////
    // Public API, accessors:
    ///////////////////////////////////////////////////////////
     */

    public boolean isAcceptingState() {
        return mAccepting;
    }

    public int getIndex() {
        return mIndex;
    }

    /**
     * @return Size of the DFA (number of states plus number of transitions),
     *   if this is its initial state (as returned by {@link #constructDFA});
     *   0 otherwise
     *
     * @since 7.4
     */
    public int getSize() {
        return mSize;
    }

    public DFAState findNext(PrefixedName elemName) {
        return mNext.get(elemName);
    }

    public TreeSet<PrefixedName> getNextNames() {
        // Let's order them alphabetically
        TreeSet<PrefixedName> names = new TreeSet<PrefixedName>();
        for (PrefixedName n : mNext.keySet()) {
            names.add(n);
        }
        return names;
    }

    public void calcNext(PrefixedName[] tokenNames, BitSet[] tokenFPs,
                         List<DFAState> stateList, Map<BitSet,DFAState> stateMap)
    {
        // Need to loop over all included tokens, and find groups of said tokens
        // (all tokens with the same name). Single pass, grouped by name in
        // order of first occurrence, so cost is linear in the size of the set
        LinkedHashMap<PrefixedName,BitSet> groups = new LinkedHashMap<>();
        BitSet tokenSet = mTokenSet;
        // No need to keep the reference to it, though:
        mTokenSet = null;

        for (int ix = tokenSet.nextSetBit(0); ix >= 0; ix = tokenSet.nextSetBit(ix+1)) {
            PrefixedName tokenName = tokenNames[ix];

            // Special case; the dummy end token has null as name;
            // we can skip that one:
            if (tokenName == null) {
                continue;
            }
            // Follow sets are shared and never modified, so copy only when
            // merging tokens that have the same name
            BitSet nextGroup = groups.get(tokenName);
            if (nextGroup == null) {
                nextGroup = tokenFPs[ix];
            } else {
                nextGroup = (BitSet) nextGroup.clone();
                nextGroup.or(tokenFPs[ix]);
            }
            groups.put(tokenName, nextGroup);
        }

        for (Map.Entry<PrefixedName,BitSet> en : groups.entrySet()) {
            BitSet nextGroup = en.getValue();
            // Ok; is it a new group?
            DFAState next = stateMap.get(nextGroup);
            if (next == null) { // yup!
                next = new DFAState(stateList.size(), nextGroup);
                stateList.add(next);
                stateMap.put(nextGroup, next);
            }
            mNext.put(en.getKey(), next);
        }
    }

    /*
    ///////////////////////////////////////////////////////////
    // Other methods
    ///////////////////////////////////////////////////////////
     */

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("State #"+mIndex+":\n");
        sb.append("  Accepting: "+mAccepting);
        sb.append("\n  Next states:\n");
        for (Map.Entry<PrefixedName,DFAState> en : mNext.entrySet()) {
            sb.append(en.getKey());
            sb.append(" -> ");
            DFAState next = en.getValue();
            sb.append(next.getIndex());
            sb.append("\n");
        }
        return sb.toString();
    }
}
