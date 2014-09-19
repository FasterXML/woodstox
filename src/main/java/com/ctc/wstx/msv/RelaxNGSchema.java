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

import javax.xml.stream.*;

import org.codehaus.stax2.validation.*;

import com.sun.msv.grammar.trex.TREXGrammar;
import com.sun.msv.verifier.regexp.REDocumentDeclaration;

/**
 * This is a validation schema instance based on a RELAX NG schema. It
 * serves as a shareable "blueprint" for creating actual validator instances.
 */
public class RelaxNGSchema
    implements XMLValidationSchema
{
    /**
     * This is VGM (in MSV lingo); shareable schema blueprint, basically
     * peer of this schema object. It will be used for creating actual
     * validator peer, root Acceptor.
     */
    protected final TREXGrammar mGrammar;

    public RelaxNGSchema(TREXGrammar grammar)
    {
        mGrammar = grammar;
    }

    public String getSchemaType() {
        return XMLValidationSchema.SCHEMA_ID_RELAXNG;
    }

    public XMLValidator createValidator(ValidationContext ctxt)
        throws XMLStreamException
    {
        REDocumentDeclaration dd = new REDocumentDeclaration(mGrammar);
        return new GenericMsvValidator(this, ctxt, dd);
    }
}
