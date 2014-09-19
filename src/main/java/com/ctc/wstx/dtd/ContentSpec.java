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

/**
 * Abstract base class for classes that contain parts of a content
 * specification of an element defined in DTD. They are created
 * by {@link FullDTDReader} when parsing an DTD subset, and they
 * will be used for constructing actual validators for the element
 * content.
 */
public abstract class ContentSpec
{
    protected char mArity;

    /*
    ///////////////////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////////////////
     */

    public ContentSpec(char arity) {
        mArity = arity;
    }

    /*
    ///////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////
     */

    public final char getArity() { return mArity; }

    public final void setArity(char c) { mArity = c; }

    public boolean isLeaf() { return false; }

    /**
     * Method called by input element stack to get validator for
     * this content specification, if this specification is simple
     * enough not to need full DFA-based validator.
     *
     * @return Simple content model validator, if one can be directly
     *   constructed, or null to indicate that a DFA needs to be
     *   created.
     */
    public abstract StructValidator getSimpleValidator();

    /**
     * Method called as the first part of DFA construction, if necessary;
     * will usually create simpler {@link ModelNode} instances that will
     * match definition this instance contains.
     */
    public abstract ModelNode rewrite();
}
