package wstxtest.util;

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
