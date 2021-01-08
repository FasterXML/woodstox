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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.xml.sax.Locator;

import com.sun.msv.grammar.ExpressionPool;
import com.sun.msv.grammar.xmlschema.XMLSchemaGrammar;
import com.sun.msv.grammar.xmlschema.XMLSchemaSchema;
import com.sun.msv.reader.GrammarReaderController;
import com.sun.msv.reader.State;
import com.sun.msv.reader.xmlschema.EmbeddedSchema;
import com.sun.msv.reader.xmlschema.MultiSchemaReader;
import com.sun.msv.reader.xmlschema.SchemaState;
import com.sun.msv.reader.xmlschema.WSDLGrammarReaderController;
import com.sun.msv.reader.xmlschema.XMLSchemaReader;

import org.codehaus.stax2.validation.XMLValidationSchema;

/**
 * This is a StAX2 schema factory that can parse and create schema instances
 * for creating validators that validate documents to check their validity
 * against specific W3C Schema instances. It requires
 * Sun Multi-Schema Validator
 * (http://www.sun.com/software/xml/developers/multischema/)
 * to work (bundled by Woodstox, no need to add dependency separately,
 * and acts as a quite thin wrapper layer, similar to
 * how matching RelaxNG validator works.
 *<p>
 * Note: intentionally does NOT (claim to) implement {@link org.codehaus.stax2.validation.XMLValidationSchemaFactory}
 * since interface (that is, {@link W3CMultiSchemaFactory#createSchema(String, Map)})
 * is not incompatible with that of other "standard" schema factories; means that
 * usage must be explicit, direct and can not be triggered via Stax2 API.
 *
 * @since 6.2
 *
 * @author Daniel Kulp
 */
public class W3CMultiSchemaFactory
{
    private final SAXParserFactory parserFactory;

    public W3CMultiSchemaFactory() {
        parserFactory = SAXParserFactory.newInstance();
        parserFactory.setNamespaceAware(true); 
    }

    static class RecursiveAllowedXMLSchemaReader extends XMLSchemaReader {
        Set<String> sysIds = new TreeSet<String>();
        RecursiveAllowedXMLSchemaReader(GrammarReaderController controller, SAXParserFactory parserFactory) {
            super(controller, parserFactory, new StateFactory() {
                @Override
                public State schemaHead(String expectedNamespace) {
                    return new SchemaState(expectedNamespace) {
                        private XMLSchemaSchema old;

                        @Override
                        protected void endSelf() {
                            super.endSelf();
                            RecursiveAllowedXMLSchemaReader r = (RecursiveAllowedXMLSchemaReader)reader;
                            r.currentSchema = old;
                        }

                        @Override
                        protected void onTargetNamespaceResolved(String targetNs, boolean ignoreContents) {

                            RecursiveAllowedXMLSchemaReader r = (RecursiveAllowedXMLSchemaReader)reader;
                            // sets new XMLSchemaGrammar object.
                            old = r.currentSchema;
                            r.currentSchema = r.getOrCreateSchema(targetNs);
                            if (ignoreContents) {
                                return;
                            }
                            if (!r.isSchemaDefined(r.currentSchema)) {
                                r.markSchemaAsDefined(r.currentSchema);
                            }
                        }
                    };
                }
            }, new ExpressionPool());
        }

        @Override
        public void setLocator(Locator locator) {
            if (locator == null && getLocator() != null && getLocator().getSystemId() != null) {
                sysIds.add(getLocator().getSystemId());
            }
            super.setLocator(locator);
        }

        @Override
        public void switchSource(Source source, State newState) {
            String url = source.getSystemId();
            if (url != null && sysIds.contains(url)) {
                return;
            }
            super.switchSource(source, newState);
        }

    }

    /**
     * Creates an XMLValidateSchema that can be used to validate XML instances against
     * any of the schemas defined in the Map of schemaSources.
     *
     * @param baseURI Base URI for resolving dependant schemas
     * @param schemaSources Map of schemas, namespace to Source
     */
    public XMLValidationSchema createSchema(String baseURI,
            Map<String, Source> schemaSources) throws XMLStreamException
    {
        Map<String, EmbeddedSchema> embeddedSources = new HashMap<String, EmbeddedSchema>();
        for (Map.Entry<String, Source> source : schemaSources.entrySet()) {
            if (source.getValue() instanceof DOMSource) {
                Node nd = ((DOMSource)source.getValue()).getNode();
                Element el = null;
                if (nd instanceof Element) {
                    el = (Element)nd;
                } else if (nd instanceof Document) {
                    el = ((Document)nd).getDocumentElement();
                }
                embeddedSources.put(source.getKey(), new EmbeddedSchema(source.getValue().getSystemId(), el));
            }
        }
        
        WSDLGrammarReaderController ctrl = new WSDLGrammarReaderController(null, baseURI, embeddedSources);
        final RecursiveAllowedXMLSchemaReader xmlSchemaReader = new RecursiveAllowedXMLSchemaReader(ctrl, parserFactory);
        final MultiSchemaReader multiSchemaReader = new MultiSchemaReader(xmlSchemaReader);
        for (Source source : schemaSources.values()) {
            multiSchemaReader.parse(source);
        }

        XMLSchemaGrammar grammar = multiSchemaReader.getResult();
        if (grammar == null) {
            throw new XMLStreamException("Failed to load schemas");
        }
        return new W3CSchema(grammar);
    }

}
