/* Woodstox XML processor
 *
 * Copyright (c) 2004 Tatu Saloranta, tatu.saloranta@iki.fi
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

package com.ctc.wstx.ent;

import java.io.IOException;
import java.io.Writer;
import java.net.URL;

import javax.xml.stream.Location;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.evt.WEntityDeclaration;
import com.ctc.wstx.io.WstxInputSource;

/**
 * Abstract base class for various entity declarations DTD reader
 * has parsed from DTD subsets.
 */
public abstract class EntityDecl
    extends WEntityDeclaration
{
    /**
     * Name/id of the entity used to reference it.
     */
    final String mName;

    /**
     * Context that is to be used to resolve references encountered from
     * expanded contents of this entity.
     */
    final URL mContext;

    /**
     * Flag that can be set to indicate that the declaration was in the
     * external DTD subset. Default is false.
     */
    protected boolean mDeclaredExternally  = false;

    public EntityDecl(Location loc, String name, URL ctxt)
    {
        super(loc);
        mName = name;
        mContext = ctxt;
    }

    public void markAsExternallyDeclared() {
        mDeclaredExternally = true;
    }

    @Override
    public final String getBaseURI() {
        return mContext.toExternalForm();
    }

    @Override
    public final String getName() {
        return mName;
    }

    // 27-Mar-2018, tatu: Implemented by `BaseEventImpl` so...
    /*
    @Override
    public final Location getLocation() {
        return getLocation();
    }
    */

    @Override
    public abstract String getNotationName();

    @Override
    public abstract String getPublicId();

    @Override
    public abstract String getReplacementText();

    public abstract int getReplacementText(Writer w)
        throws IOException;

    @Override
    public abstract String getSystemId();

    /**
     * @return True, if the declaration occured in the external DTD
     *   subset; false if not (internal subset, custom declaration)
     */
    public boolean wasDeclaredExternally() {
        return mDeclaredExternally;
    }

    /*
    ////////////////////////////////////////////////////////////
    // Implementation of abstract base methods
    ////////////////////////////////////////////////////////////
     */

    @Override
    public abstract void writeEnc(Writer w) throws IOException;

    /*
    ////////////////////////////////////////////////////////////
    // Extended API for Wstx core
    ////////////////////////////////////////////////////////////
     */

    // // // Access to data

    public abstract char[] getReplacementChars();

    public final int getReplacementTextLength() {
        String str = getReplacementText();
        return (str == null) ? 0 : str.length();
    }

    // // // Type information

    public abstract boolean isExternal();

    public abstract boolean isParsed();

    // // // Factory methods

    /**
     * Method called to create the new input source through which expansion
     * value of the entity can be read.
     */
    public abstract WstxInputSource expand(WstxInputSource parent, 
            XMLResolver res, ReaderConfig cfg, int xmlVersion)
        throws IOException, XMLStreamException;
}
