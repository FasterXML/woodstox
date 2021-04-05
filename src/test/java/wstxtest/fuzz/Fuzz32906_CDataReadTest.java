package wstxtest.fuzz;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.stax2.io.Stax2ByteArraySource;

import wstxtest.stream.BaseStreamTest;

import com.ctc.wstx.exc.WstxEOFException;
import com.ctc.wstx.stax.WstxInputFactory;

//[woodstox-core#465]: UTF-8/Surrogate handling at the end of CDATA (and
//document)
public class Fuzz32906_CDataReadTest extends BaseStreamTest
{
    private final byte[] DOC = readResource("/fuzz/fuzz-32906.xml");

    private final WstxInputFactory STAX_F = getWstxInputFactory();

    //[woodstox-core#465] with InputStream
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
    
    //[woodstox-core#465] with Reader
    public void testIssue465Reader() throws Exception
    {
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

    //[woodstox-core#465] with Stax2 byte array source
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

