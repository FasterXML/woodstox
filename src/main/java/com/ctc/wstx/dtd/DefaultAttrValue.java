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

import java.text.MessageFormat;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.validation.ValidationContext;
import org.codehaus.stax2.validation.XMLValidationProblem;
import org.codehaus.stax2.validation.XMLValidator;

import com.ctc.wstx.cfg.ErrorConsts;

/**
 * Simple container class used to contain information about the default
 * value for an attribute. Although for most use cases a simple String
 * would suffice, there are cases where additional information is needed
 * (especially status of 'broken' default values, which only need to be
 * reported should the default value be needed).
 */
public final class DefaultAttrValue
{
    /*
    ////////////////////////////////////////////////////
    // Constants
    ////////////////////////////////////////////////////
     */

    // // // Default value types

    public final static int DEF_DEFAULT = 1;
    public final static int DEF_IMPLIED = 2;
    public final static int DEF_REQUIRED = 3;
    public final static int DEF_FIXED = 4;

    /*
    ////////////////////////////////////////////////////
    // Singleton instances
    ////////////////////////////////////////////////////
     */

    final static DefaultAttrValue sImplied = new DefaultAttrValue(DEF_IMPLIED);

    final static DefaultAttrValue sRequired = new DefaultAttrValue(DEF_REQUIRED);

    /*
    ////////////////////////////////////////////////////
    // State
    ////////////////////////////////////////////////////
     */

    final int mDefValueType;

    /**
     * Actual expanded textual content of the default attribute value;
     * normalized if appropriate in this mode.
     * Note that all entities have been expanded: if a GE/PE was undefined,
     * and no fatal errors were reported (non-validating mode), the
     * references were just silently removed, and matching entries added
     * to <code>mUndeclaredEntity</code>
     */
    private String mValue = null;

    /**
     * For now, let's only keep track of the first undeclared entity:
     * can be extended if necessary.
     */
    private UndeclaredEntity mUndeclaredEntity = null;
    
    /*
    ////////////////////////////////////////////////////
    // Life-cycle (creation, configuration)
    ////////////////////////////////////////////////////
     */

    private DefaultAttrValue(int defValueType)
    {
        mDefValueType = defValueType;
    }

    public static DefaultAttrValue constructImplied() { return sImplied; }
    public static DefaultAttrValue constructRequired() { return sRequired; }

    public static DefaultAttrValue constructFixed() {
        return new DefaultAttrValue(DEF_FIXED);
    }

    public static DefaultAttrValue constructOptional() {
        return new DefaultAttrValue(DEF_DEFAULT);
    }

    public void setValue(String v) {
        mValue = v;
    }

    public void addUndeclaredPE(String name, Location loc)
    {
        addUndeclaredEntity(name, loc, true);
    }

    public void addUndeclaredGE(String name, Location loc)
    {
        addUndeclaredEntity(name, loc, false);
    }

    public void reportUndeclared(ValidationContext ctxt, XMLValidator dtd)
        throws XMLStreamException
    {
        mUndeclaredEntity.reportUndeclared(ctxt, dtd);
    }

    /*
    ////////////////////////////////////////////////////
    // Accessors:
    ////////////////////////////////////////////////////
     */

    public boolean hasUndeclaredEntities() {
        return (mUndeclaredEntity != null);
    }

    public String getValue() {
        return mValue;
    }

    /**
     * @return Expanded default value String, if there were no problems
     *   (no undeclared entities), or null to indicate there were problems.
     *   In latter case, caller is to figure out exact type of the problem
     *   and report this appropriately to the application.
     */
    public String getValueIfOk()
    {
        return (mUndeclaredEntity == null) ? mValue : null;
    }

    public boolean isRequired() {
        return (this == sRequired);
    }

    public boolean isFixed() {
        return (mDefValueType == DEF_FIXED);
    }

    public boolean hasDefaultValue() {
        return (mDefValueType == DEF_DEFAULT)
            || (mDefValueType == DEF_FIXED);
    }

    /**
     * Method used by the element to figure out if attribute needs "special"
     * checking; basically if it's required, and/or has a default value.
     * In both cases missing the attribute has specific consequences, either
     * exception or addition of a default value.
     */
    public boolean isSpecial() {
        // Only non-special if #IMPLIED
        return (this != sImplied);
    }

    /*
    ////////////////////////////////////////////////////
    // Internal methods
    ////////////////////////////////////////////////////
     */

    private void addUndeclaredEntity(String name, Location loc, boolean isPe)
    {
        if (mUndeclaredEntity == null) {
            mUndeclaredEntity = new UndeclaredEntity(name, loc, isPe);
        }
    }

    /*
    ////////////////////////////////////////////////////
    // Helper class(es):
    ////////////////////////////////////////////////////
     */

    final static class UndeclaredEntity
    {
        final String mName;
        final boolean mIsPe;
        final Location mLocation;

        UndeclaredEntity(String name, Location loc, boolean isPe)
        {
            mName = name;
            mIsPe = isPe;
            mLocation = loc;
        }

        public void reportUndeclared(ValidationContext ctxt, XMLValidator dtd)
            throws XMLStreamException
        {
            String msg = MessageFormat.format(ErrorConsts.ERR_DTD_UNDECLARED_ENTITY, new Object[] { (mIsPe ? "parsed" : "general"), mName });
            XMLValidationProblem prob = new XMLValidationProblem
                (mLocation, msg, XMLValidationProblem.SEVERITY_FATAL);
            prob.setReporter(dtd);
            ctxt.reportProblem(prob);
        }
    }
}
