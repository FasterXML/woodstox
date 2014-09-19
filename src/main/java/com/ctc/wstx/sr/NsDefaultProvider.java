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

import javax.xml.stream.XMLStreamException;

/**
 * Interface only used by Woodstox core. The main reason for the interface
 * is to reduce coupling with the input element stack and dtd validator
 * instances: while dtd validator needs to be able to inject namespace
 * declarations based on attribute default values, it should not have to
 * know too much about element stack implementation, and vice versa.
 * As a result, this interface defines API input element stack calls
 * on the dtd validator instance. Validator instance then refers to the
 * input element stack base class to do callbacks if and as necessary.
 */
public interface NsDefaultProvider
{
    public boolean mayHaveNsDefaults(String elemPrefix, String elemLN);

    /**
     * Method called by the input element stack to indicate that
     * it has just added local namespace declarations from the
     * current element, and is about to start resolving element
     * and attribute namespace bindings. This provider instance is
     * to add namespace declarations from attribute defaults, if
     * any, using callbacks to the input element stack.
     */
    public void checkNsDefaults(InputElementStack nsStack)
        throws XMLStreamException;
}
