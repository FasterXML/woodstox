package wstxtest.fuzz;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.stax2.io.Stax2ByteArraySource;

import wstxtest.stream.BaseStreamTest;

import com.ctc.wstx.exc.WstxIOException;
import com.ctc.wstx.stax.WstxInputFactory;
import org.junit.jupiter.api.Test;

//[woodstox-core#125]: UTF-32 decoding issue
public class Fuzz125_32969_UTF32ReadTest extends BaseStreamTest
{
    private final byte[] DOC = readResource("/fuzz/fuzz-32969.xml");

    private final WstxInputFactory STAX_F = getWstxInputFactory();
    {
        try {
            setLazyParsing(STAX_F, false);
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    //[woodstox-core#125]: InputStream
    @Test
    public void testIssue125InputStream() throws Exception
    {
        XMLStreamReader sr = STAX_F.createXMLStreamReader(new ByteArrayInputStream(DOC));
        try {
            streamThrough(sr);
            fail("Should not pass");
        } catch (WstxIOException e) {
            // 02-Jun-2026, [woodstox-core#293]: out-of-range code point (high
            //   byte top bit set, sign-extends negative) is now rejected up
            //   front instead of being truncated and slipping through to EOF.
            verifyException(e, "Invalid UTF-32 character");
        }
        sr.close();
    }

    //[woodstox-core#125]: byte[] input
    @Test
    public void testIssue125Stax2ByteArray() throws Exception
    {
        // Then "native" Byte array
        Stax2ByteArraySource src = new Stax2ByteArraySource(DOC, 0, DOC.length);
        XMLStreamReader sr = STAX_F.createXMLStreamReader(src);
        try {
            streamThrough(sr);
            fail("Should not pass");
        } catch (WstxIOException e) {
            // 02-Jun-2026, [woodstox-core#293]: see above
            verifyException(e, "Invalid UTF-32 character");
        }
        sr.close();
    }
}

