package com.ctc.wstx.evt;

import java.io.IOException;
import java.io.Writer;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EntityDeclaration;

import org.codehaus.stax2.XMLStreamWriter2;
import org.codehaus.stax2.ri.evt.BaseEventImpl;

import com.ctc.wstx.ent.EntityDecl;
import com.ctc.wstx.exc.WstxIOException;

/**
 * Simple implementation of StAX entity declaration events; for the
 * most just wraps a {@link EntityDecl} instance.
 */
public abstract class WEntityDeclaration
    extends BaseEventImpl
    implements EntityDeclaration
{
    public WEntityDeclaration(Location loc)
    {
        super(loc);
    }

    @Override
    public abstract String getBaseURI();

    @Override
    public abstract String getName();

    @Override
    public abstract String getNotationName();

    @Override
    public abstract String getPublicId();

    @Override
    public abstract String getReplacementText();

    @Override
    public abstract String getSystemId();

    /*
    ///////////////////////////////////////////
    // Implementation of abstract base methods
    ///////////////////////////////////////////
     */

    @Override
    public int getEventType() {
        return ENTITY_DECLARATION;
    }

    public abstract void writeEnc(Writer w) throws IOException;

    @Override
    public void writeAsEncodedUnicode(Writer w) throws XMLStreamException
    {
        try {
            writeEnc(w);
        } catch (IOException ie) {
            throw new WstxIOException(ie);
        }
    }

    /**
     * This method does not make much sense for this event type -- the reason
     * being that the entity declarations can only be written as part of
     * a DTD (internal or external subset), not separately. Can basically
     * choose to either skip silently (output nothing), or throw an
     * exception.
     */
    @Override
    public void writeUsing(XMLStreamWriter2 w) throws XMLStreamException
    {
        /* Fail silently, or throw an exception? Let's do latter; at least
         * then we'll get useful (?) bug reports!
         */
        throw new XMLStreamException("Can not write entity declarations using an XMLStreamWriter");
    }

    /*
    ///////////////////////////////////////////
    // Standard method impl: note, copied
    // from Stax2 RI "EntityDeclarationEventImpl"
    ///////////////////////////////////////////
     */

    @Override
    public boolean equals(Object o)
    {
        if (o == this) return true;
        if (o == null) return false;

        if (!(o instanceof EntityDeclaration)) return false;

        EntityDeclaration other = (EntityDeclaration) o;
        return stringsWithNullsEqual(getName(), other.getName())
            && stringsWithNullsEqual(getBaseURI(), other.getBaseURI())
            && stringsWithNullsEqual(getNotationName(), other.getNotationName())
            && stringsWithNullsEqual(getPublicId(), other.getPublicId())
            && stringsWithNullsEqual(getReplacementText(), other.getReplacementText())
            && stringsWithNullsEqual(getSystemId(), other.getSystemId())
            ;
    }

    @Override
    public int hashCode()
    {
        /* Hmmh. Could try using most of the data, but really, name
         * should be enough for most use cases
         */
        return getName().hashCode();
    }
}
