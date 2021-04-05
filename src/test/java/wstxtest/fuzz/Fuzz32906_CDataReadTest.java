package wstxtest.fuzz;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.stax2.io.Stax2ByteArraySource;

import wstxtest.stream.BaseStreamTest;

import com.ctc.wstx.exc.WstxEOFException;
import com.ctc.wstx.stax.WstxInputFactory;

public class Fuzz32906_CDataReadTest extends BaseStreamTest
{
    private final byte[] DOC = readResource("/fuzz/fuzz-32906.xml");

    private final WstxInputFactory STAX_F = getWstxInputFactory();
    
    /**
     * This test case was added after encountering a specific problem, which
     * only occurs when many attributes were spilled from main hash area....
     * and that's why exact attribute names do matter.
     */
    public void testIssue465InputStream() throws Exception
    {
        XMLStreamReader sr = STAX_F.createXMLStreamReader(new ByteArrayInputStream(DOC));
        try {
            streamThrough(sr);
            fail("Should not pass");
        } catch (WstxEOFException e) {
            verifyException(e, "Unexpected EOF in CDATA section");
        }
        sr.close();
    }
    
    public void testIssue465Reader() throws Exception
    {
        // then Reader
        Reader r = new InputStreamReader(new ByteArrayInputStream(DOC),
                "UTF-8");
        XMLStreamReader sr = STAX_F.createXMLStreamReader(r);
        try {
            streamThrough(sr);
            fail("Should not pass");
        } catch (WstxEOFException e) {
            verifyException(e, "Unexpected EOF in CDATA section");
        }
        sr.close();
    }

    public void testIssue465Stax2ByteARray() throws Exception
    {
        // Then "native" Byte array
        Stax2ByteArraySource src = new Stax2ByteArraySource(DOC, 0, DOC.length);
        XMLStreamReader sr = STAX_F.createXMLStreamReader(src);
        try {
            streamThrough(sr);
            fail("Should not pass");
        } catch (WstxEOFException e) {
            verifyException(e, "Unexpected EOF in CDATA section");
        }
        sr.close();
    }
}

