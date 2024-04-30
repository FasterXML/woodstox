package wstxtest.evt;

import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Objects;

import javax.xml.stream.Location;
import javax.xml.stream.events.XMLEvent;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Test;

import com.ctc.wstx.io.WstxInputLocation;
import com.ctc.wstx.stax.WstxEventFactory;

public class TestEventFactoryLocation {

    private WstxEventFactory eventFactory;

    @Before
    public void setUp() {
        eventFactory = new WstxEventFactory();
    }

    @Test
    public void testDefaultLocation() {
        XMLEvent event = eventFactory.createStartDocument();

        assertThat("event.location", event.getLocation(), unknownLocation());
    }

    @Test
    public void testResetLocation() {
        eventFactory.setLocation(new WstxInputLocation(null, null, "about:blank", 3L, 2, 1));
        eventFactory.setLocation(null);

        XMLEvent event = eventFactory.createStartElement("foo", "bar", "baz");

        assertThat("event.location", event.getLocation(), unknownLocation());
    }

    @Test
    public void testNonVolatileLocation() {
        VolatileLocation volatileLocation = new VolatileLocation(2, 3);
        eventFactory.setLocation(volatileLocation);

        XMLEvent event = eventFactory.createEndElement("foo", "bar", "baz");
        volatileLocation.line = 4;
        volatileLocation.col = 5;

        assertThat("event.location", event.getLocation(),
                locationWithProperties(null, null, -1, 2, 3));
    }

    private static Matcher<Location> unknownLocation() {
        // XXX: Not sure if the empty-string publicId/systemId are conformant
        //return locationWithProperties(null, null, -1, -1, -1);
        return locationWithProperties("", "", -1, -1, -1);
    }

    private static Matcher<Location> locationWithProperties(final String pubId,
            final String sysId, final int charOffset, final int row, final int col) {
        return new TypeSafeMatcher<Location>() {
            @Override public void describeTo(Description description) {
                description.appendText("Location(publicId: ").appendValue(pubId)
                           .appendText(", systemId: ").appendValue(sysId)
                           .appendText(", characterOffset: ").appendValue(charOffset)
                           .appendText(", lineNumber: ").appendValue(row)
                           .appendText(", columnNumber: ").appendValue(col);
            }

            @Override protected boolean matchesSafely(Location item) {
                return Objects.equals(item.getPublicId(), pubId)
                        && Objects.equals(item.getSystemId(), sysId)
                        && Objects.equals(item.getCharacterOffset(), charOffset)
                        && Objects.equals(item.getLineNumber(), row)
                        && Objects.equals(item.getColumnNumber(), col);
            }
        };
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
