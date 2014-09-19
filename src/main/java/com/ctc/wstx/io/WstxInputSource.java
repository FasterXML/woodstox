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

package com.ctc.wstx.io;

import java.io.IOException;
import java.net.URL;

import javax.xml.stream.XMLStreamException;

/**
 * Interface that defines API actual parsers (stream readers)
 * can use to read input from various input sources.
 * Needed to abstract out details of getting input from primary input
 * files, secondary (potentially cached) referenced documents, and from
 * parsed entities, as well as for allowing hierarchic location
 * information for error reporting.
 */
public abstract class WstxInputSource
{
    /**
     * Parent in input source stack
     */
    protected final WstxInputSource mParent;

    /**
     * Name/id of the entity that was expanded to produce this input source;
     * null if not entity-originated. Used for catching recursive expansions
     * of entities.
     */
    protected final String mFromEntity;

    /**
     * Scope of the reader when this entity was initially expanded. Snapshot
     * that will generally be used by the reader to match scoping
     * limitations, such as proper nesting entity expansion with respect
     * to element and declaration nesting.
     */
    protected int mScopeId = 0;

    /**
     * Number of parent entities that have been expanded to get to this
     * input source; 0 for root-level input that is not generated via
     * entity expansion.
     */
    protected int mEntityDepth;
    
    /*
    //////////////////////////////////////////////////////////
    // Life-cycle:
    //////////////////////////////////////////////////////////
     */

    protected WstxInputSource(WstxInputSource parent, String fromEntity)
    {
        mParent = parent;
        mFromEntity = fromEntity;
    }

    /**
     * Method that can be called to override originally defined source.
     *
     * @param url New base URL to set; may be null.
     *
     * @since 4.0
     */
    public abstract void overrideSource(URL url);
    
    /*
    //////////////////////////////////////////////////////////
    // Basic accessors:
    //////////////////////////////////////////////////////////
     */

    public final WstxInputSource getParent() {
        return mParent;
    }

    /**
     * Method that checks if this input source expanded from the specified
     * entity id, directly or by ancestor.
     *<p>
     * Note that entity ids are expected to have been interned (using
     * whatever uniqueness mechanism used), and thus can be simply
     * identity checked.
     */
    public boolean isOrIsExpandedFrom(String entityId)
    {
        if (entityId != null) { // should always be true
            WstxInputSource curr = this;
            while (curr != null) {
                if (entityId == curr.mFromEntity) {
                    return true;
                }
                curr = curr.mParent;
            }
        }
        return false;
    }

    /**
     * @return True, if this input source was directly expanded from an
     *   internal entity (general, parsed); false if not (from external
     *   entity, DTD ext. subset, main document) 
     */
    public abstract boolean fromInternalEntity();

    /*
    //////////////////////////////////////////////////////////
    // Location info:
    //////////////////////////////////////////////////////////
     */

    public abstract URL getSource() throws IOException;

    public abstract String getPublicId();

    public abstract String getSystemId();

    /**
     * Method usually called to get a parent location for another input
     * source. Works since at this point context (line, row, chars) information
     * has already been saved to this object.
     */
    protected abstract WstxInputLocation getLocation();

    public abstract WstxInputLocation getLocation(long total, int row, int col);

    public String getEntityId() { return mFromEntity; }

    public int getScopeId() { return mScopeId; }

    public int getEntityDepth() { return mEntityDepth; }
    
    /*
    //////////////////////////////////////////////////////////
    // Actual input handling
    //////////////////////////////////////////////////////////
     */

    /**
     * Method called by Reader when current input has changed to come
     * from this input source. Should reset/initialize input location
     * information Reader keeps, for error messages to work ok.
     *
     * @param reader Reader whose data structures are to be used for
     *   returning data read
     * @param currScopeId
     */
    public final void initInputLocation(WstxInputData reader, int currScopeId,
            int entityDepth) {
        mScopeId = currScopeId;
        mEntityDepth = entityDepth;
        doInitInputLocation(reader);
    }

    protected abstract void doInitInputLocation(WstxInputData reader);

    /**
     * Method called to read at least one more char from input source, and
     * update input data appropriately.
     *
     * @return Number of characters read from the input source (at least 1),
     *   if it had any input; -1 if input source has no more input.
     */
    public abstract int readInto(WstxInputData reader)
        throws IOException, XMLStreamException;
    
    /**
     * Method called by reader when it has to have at least specified number
     * of consequtive input characters in its buffer, and it currently does
     * not have. If so, it asks input source to do whatever it has to do
     * to try to get more data, if possible (including moving stuff in
     * input buffer if necessary and possible).
     *
     * @return True if input source was able to provide specific number of
     *   characters or more; false if not. In latter case, source is free
     *   to return zero or more characters any way.
     */
    public abstract boolean readMore(WstxInputData reader, int minAmount)
        throws IOException, XMLStreamException;

    /**
     * Method Reader calls when this input source is being stored, when
     * a nested input source gets used instead (due to entity expansion).
     * Needs to get location info from Reader and store it in this Object.
     */
    public abstract void saveContext(WstxInputData reader);

    /**
     * Method Reader calls when this input source is resumed as the
     * current source. Needs to update Reader's input location data
     * used for error messages etc.
     */
    public abstract void restoreContext(WstxInputData reader);

    /**
     * Method reader calls for this input source when it has encountered
     * EOF. This may or may not close the underlying stream/reader; what
     * happens depends on configuration
     */
    public abstract void close() throws IOException;

    /**
     * Method reader MAY call to force full closing of the underlying
     * input stream(s)/reader(s). No checks are done regarding configuration,
     * but input source object is to deal gracefully with multiple calls
     * (ie. it's not an error for reader to call this more than once).
     */
    public abstract void closeCompletely() throws IOException;

    /*
    //////////////////////////////////////////////////////////
    // Overridden standard methods:
    //////////////////////////////////////////////////////////
     */

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(80);
        sb.append("<WstxInputSource [class ");
        sb.append(getClass().toString());
        sb.append("]; systemId: ");
        sb.append(getSystemId());
        sb.append(", source: ");
        try {
        	URL url = getSource();
        	sb.append(url.toString());
        } catch (IOException e) {
        	sb.append("[ERROR: "+e.getMessage()+"]");
        }
        
        sb.append('>');
        return sb.toString();
    }
}
