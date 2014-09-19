package wstxtest.util;

import com.ctc.wstx.util.URLUtil;

import junit.framework.TestCase;

/**
 * Tests for [WSTX-275]
 */
public class TestURLUtil extends TestCase
{
    public void testUriCreationFromSystemIdWithPipe() throws Exception {
        URLUtil.urlFromSystemId("file:///C|/InfoShare/Web/Author/ASP/DocTypes/dita-oasis/1.2/technicalContent/dtd/map.dtd");
    }

    public void testRelativePath() throws Exception {
        URLUtil.urlFromSystemId("relative/path/to/dtd");
    }

    public void testAbsoluteUnixPath() throws Exception {
        URLUtil.urlFromSystemId("file:///absolute/path/to/dtd");
    }
}
