package wstxtest.evt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Objects;

import javax.xml.stream.Location;
import javax.xml.stream.events.XMLEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ctc.wstx.io.WstxInputLocation;
import com.ctc.wstx.stax.WstxEventFactory;

public class TestEventFactoryLocation {

    private WstxEventFactory eventFactory;

    @BeforeEach
    public void setUp() {
        eventFactory = new WstxEventFactory();
    }

    @Test
    public void testDefaultLocation() {
        XMLEvent event = eventFactory.createStartDocument();

        assertLocationProperties(event.getLocation(), "", "", -1, -1, -1);
    }

    @Test
    public void testResetLocation() {
        eventFactory.setLocation(new WstxInputLocation(null, null, "about:blank", 3L, 2, 1));
        eventFactory.setLocation(null);

        XMLEvent event = eventFactory.createStartElement("foo", "bar", "baz");

        assertLocationProperties(event.getLocation(), "", "", -1, -1, -1);
    }

    @Test
    public void testNonVolatileLocation() {
        VolatileLocation volatileLocation = new VolatileLocation(2, 3);
        eventFactory.setLocation(volatileLocation);

        XMLEvent event = eventFactory.createEndElement("foo", "bar", "baz");
        volatileLocation.line = 4;
        volatileLocation.col = 5;

        assertLocationProperties(event.getLocation(), null, null, -1, 2, 3);
    }

    private static void assertLocationProperties(Location loc, String pubId,
            String sysId, int charOffset, int row, int col) {
        assertEquals(pubId, loc.getPublicId(), "publicId");
        assertEquals(sysId, loc.getSystemId(), "systemId");
        assertEquals(charOffset, loc.getCharacterOffset(), "characterOffset");
        assertEquals(row, loc.getLineNumber(), "lineNumber");
        assertEquals(col, loc.getColumnNumber(), "columnNumber");
        // sanity check that we used Objects.equals semantics
        assert Objects.equals(loc.getLineNumber(), row);
    }

    static class VolatileLocation implements Location {
        int line;
        int col;
        VolatileLocation(int line, int col) {
            this.line = line;
            this.col = col;
        }
        @Override public String getPublicId() { return null; }
        @Override public String getSystemId() { return null; }
        @Override public int getLineNumber() { return line; }
        @Override public int getColumnNumber() { return col; }
        @Override public int getCharacterOffset() { return -1; }
    }
}
