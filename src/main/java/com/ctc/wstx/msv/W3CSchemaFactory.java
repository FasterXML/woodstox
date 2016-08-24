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

import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.*;

import org.xml.sax.InputSource;

import org.codehaus.stax2.validation.*;

import com.sun.msv.grammar.xmlschema.XMLSchemaGrammar;
import com.sun.msv.reader.GrammarReaderController;
import com.sun.msv.reader.xmlschema.XMLSchemaReader;

/**
 * This is a StAX2 schema factory that can parse and create schema instances
 * for creating validators that validate documents to check their validity
 * against specific W3C Schema instances. It requires
 * Sun Multi-Schema Validator
 * (http://www.sun.com/software/xml/developers/multischema/)
 * to work, and acts as a quite thin wrapper layer, similar to
 * how matching RelaxNG validator works
 */
public class W3CSchemaFactory
    extends BaseSchemaFactory
{
    /**
     * For now, there's no need for fine-grained error/problem reporting
     * infrastructure, so let's just use a dummy controller.
     */
    protected final GrammarReaderController mDummyController =
        new com.sun.msv.reader.util.IgnoreController();       

    public W3CSchemaFactory()
    {
        super(XMLValidationSchema.SCHEMA_ID_RELAXNG);
    }

    /*
    ////////////////////////////////////////////////////////////
    // Non-public methods
    ////////////////////////////////////////////////////////////
     */

    @Override
    protected XMLValidationSchema loadSchema(InputSource src, Object sysRef)
        throws XMLStreamException
    {
        /* 26-Oct-2007, TSa: Are sax parser factories safe to share?
         *   If not, should just create new instances for each
         *   parsed schema.
         */
        SAXParserFactory saxFactory = getSaxFactory();

        MyGrammarController ctrl = new MyGrammarController();
        XMLSchemaGrammar grammar = XMLSchemaReader.parse(src, saxFactory, ctrl);
        if (grammar == null) {
            String msg = "Failed to load W3C Schema from '"+sysRef+"'";
            String emsg = ctrl.mErrorMsg;
            if (emsg != null) {
                msg = msg + ": "+emsg;
            }
            throw new XMLStreamException(msg);
        }
        return new W3CSchema(grammar);
    }
}
