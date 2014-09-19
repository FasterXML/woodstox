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

package com.ctc.wstx.evt;

import javax.xml.stream.*;
import javax.xml.stream.util.XMLEventAllocator;

import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.ri.Stax2EventReaderImpl;

import com.ctc.wstx.cfg.ErrorConsts;
import com.ctc.wstx.exc.WstxParsingException;

/**
 * Woodstox version, based on generic Stax reference implementation
 * baseline of {@link Stax2EventReaderImpl}.
 */
public class WstxEventReader
    extends Stax2EventReaderImpl
{
    public WstxEventReader(XMLEventAllocator a, XMLStreamReader2 r)
    {
        super(a, r);
    }

    /*
    //////////////////////////////////////////////////////
    // Impl of abstract methods
    //////////////////////////////////////////////////////
     */

    protected String getErrorDesc(int errorType, int currEvent)
    {
        // Defaults are mostly fine, except we can easily add event type desc
        switch (errorType) {
        case ERR_GETELEMTEXT_NOT_START_ELEM:
            return ErrorConsts.ERR_STATE_NOT_STELEM+", got "+ErrorConsts.tokenTypeDesc(currEvent);
        case ERR_GETELEMTEXT_NON_TEXT_EVENT:
            return "Expected a text token, got "+ErrorConsts.tokenTypeDesc(currEvent);
        case ERR_NEXTTAG_NON_WS_TEXT:
            return "Only all-whitespace CHARACTERS/CDATA (or SPACE) allowed for nextTag(), got "+ErrorConsts.tokenTypeDesc(currEvent);
        case ERR_NEXTTAG_WRONG_TYPE:
            return "Got "+ErrorConsts.tokenTypeDesc(currEvent)+", instead of START_ELEMENT, END_ELEMENT or SPACE";
        }
        return null;
    }

    public boolean isPropertySupported(String name)
    {
        return ((XMLStreamReader2)getStreamReader()).isPropertySupported(name);
    }

    public boolean setProperty(String name, Object value)
    {
        return ((XMLStreamReader2)getStreamReader()).setProperty(name, value);
    }


    /*
    //////////////////////////////////////////////////////
    // Overrides
    //////////////////////////////////////////////////////
     */

    @Override
    protected void reportProblem(String msg, Location loc)
        throws XMLStreamException
    {
        throw new WstxParsingException(msg, loc);
    }
}
