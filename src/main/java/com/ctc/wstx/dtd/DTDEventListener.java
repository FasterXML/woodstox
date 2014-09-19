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

package com.ctc.wstx.dtd;

import java.net.URL;

import javax.xml.stream.XMLStreamException;

public interface DTDEventListener
{
    // Configuration

    /**
     * @return True, if there is a listener interested in getting comment
     *   events within DTD subset (since that's optional)
     */
    public boolean dtdReportComments();

    // Basic content events

    public void dtdProcessingInstruction(String target, String data);
    public void dtdComment(char[] data, int offset, int len);
    public void dtdSkippedEntity(String name);

    // DTD declarations that must be exposed
    public void dtdNotationDecl(String name, String publicId, String systemId, URL baseURL)
        throws XMLStreamException;

    public void dtdUnparsedEntityDecl(String name, String publicId, String systemId, String notationName, URL baseURL)
        throws XMLStreamException;

    // DTD declarations that can be exposed

    public void attributeDecl(String eName, String aName, String type, String mode, String value);
    public void dtdElementDecl(String name, String model);
    public void dtdExternalEntityDecl(String name, String publicId, String systemId);
    public void dtdInternalEntityDecl(String name, String value);
}
