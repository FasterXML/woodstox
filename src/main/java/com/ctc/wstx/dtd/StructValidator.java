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

import com.ctc.wstx.util.PrefixedName;

/**
 * Base class for validator Objects used to validate tree structure of an
 * XML-document against DTD.
 */
public abstract class StructValidator
{
    /**
     * Method that should be called to get the actual usable validator
     * instance, from the 'template' validator.
     */
    public abstract StructValidator newInstance();

    /**
     * Method called when a new (start) element is encountered within the
     * scope of parent element this validator monitors.
     *
     * @return Null if element is valid in its current position; error
     *    message if not.
     */
    public abstract String tryToValidate(PrefixedName elemName);

    /**
     * Method called when the end element of the scope this validator
     * validates is encountered. It should make sure that the content
     * model is valid, and if not, to construct an error message.
     *
     * @return Null if the content model for the element is valid; error
     *    message if not.
     */
    public abstract String fullyValid();
}

