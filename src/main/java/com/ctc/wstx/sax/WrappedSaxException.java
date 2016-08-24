/*
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

package com.ctc.wstx.sax;

import org.xml.sax.SAXException;

/**
 * Simple type-safe wrapper used for "tunneling" SAX exceptions
 * through interfaces that do not allow them to be thrown. This
 * is done by extending {@link RuntimeException}.
 */
@SuppressWarnings("serial")
public final class WrappedSaxException
    extends RuntimeException
{
    final SAXException mCause;

    public WrappedSaxException(SAXException cause)
    {
        mCause = cause;
    }

    public SAXException getSaxException() { return mCause; }

    @Override
    public String toString() { return mCause.toString(); }
}
