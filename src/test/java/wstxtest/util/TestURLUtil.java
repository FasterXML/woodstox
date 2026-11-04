package wstxtest.util;

import java.io.IOException;

import com.ctc.wstx.util.URLUtil;
import org.junit.jupiter.api.Test;


/**
 * Tests for [WSTX-275]
 */
public class TestURLUtil extends wstxtest.BaseJUnit4Test
{
    @Test
    public void testUriCreationFromSystemIdWithPipe() throws Exception {
        URLUtil.uriFromSystemId("file:///C|/InfoShare/Web/Author/ASP/DocTypes/dita-oasis/1.2/technicalContent/dtd/map.dtd");
        URLUtil.urlFromSystemId("file:///C|/InfoShare/Web/Author/ASP/DocTypes/dita-oasis/1.2/technicalContent/dtd/map.dtd");
    }

    // Pipe should only be replaced in "file:///X|" Windows drive form, not elsewhere
    @Test
    public void testPipeOutsideWindowsDriveIsKept() throws Exception {
        // URLs
        assertEquals("/C:/dtd/map.dtd",
                URLUtil.urlFromSystemId("file:///C|/dtd/map.dtd").getPath());
        assertEquals("/dtd/a|b.dtd",
                URLUtil.urlFromSystemId("http://example.com/dtd/a|b.dtd").getPath());

        // URIs
        assertEquals("/C:/dtd/map.dtd",
                URLUtil.uriFromSystemId("file:///C|/dtd/map.dtd").getPath());
        // '|' is not a legal URI char, so must fail rather than silently become ':'
        try {
            URLUtil.uriFromSystemId("http://example.com/dtd/a|b.dtd");
            fail("Expected IOException for '|' in URI path");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Illegal character"));
        }
    }

    @Test
    public void testRelativePath() throws Exception {
        URLUtil.uriFromSystemId("relative/path/to/dtd");
        URLUtil.urlFromSystemId("relative/path/to/dtd");
    }

    @Test
    public void testAbsoluteUnixPath() throws Exception {
        URLUtil.uriFromSystemId("file:///absolute/path/to/dtd");
        URLUtil.urlFromSystemId("file:///absolute/path/to/dtd");
    }
}
