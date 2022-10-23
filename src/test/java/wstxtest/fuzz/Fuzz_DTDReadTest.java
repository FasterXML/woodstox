package wstxtest.fuzz;

import com.ctc.wstx.dtd.FullDTDReader;
import com.ctc.wstx.exc.WstxLazyException;
import com.ctc.wstx.stax.WstxInputFactory;
import org.codehaus.stax2.io.Stax2ByteArraySource;
import wstxtest.stream.BaseStreamTest;

import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;

public class Fuzz_DTDReadTest extends BaseStreamTest
{
    private final byte[] DOC = readResource("/fuzz/clusterfuzz-testcase-modified-XmlFuzzer-5219006592450560.txt");

    private final WstxInputFactory STAX_F = getWstxInputFactory();

    public void testIssueInputStream() throws Exception
    {
        XMLStreamReader sr = STAX_F.createXMLStreamReader(new ByteArrayInputStream(DOC));
        try {
            streamThrough(sr);
            fail("Should not pass");
        } catch (WstxLazyException e) {
            verifyException(e, "FullDTDReader has reached recursion depth limit of 500");
        }
        sr.close();
    }

    public void testIssueInputStreamHigherRecursionLimit() throws Exception
    {
        final int defaultLimit = FullDTDReader.getDtdRecursionDepthLimit();
        XMLStreamReader sr = STAX_F.createXMLStreamReader(new ByteArrayInputStream(DOC));
        try {
            FullDTDReader.setDtdRecursionDepthLimit(1000);
            streamThrough(sr);
            fail("Should not pass");
        } catch (WstxLazyException e) {
            verifyException(e, "FullDTDReader has reached recursion depth limit of 1000");
        } finally {
            FullDTDReader.setDtdRecursionDepthLimit(defaultLimit);
        }
        sr.close();
    }
    
    public void testIssueReader() throws Exception
    {
        Reader r = new InputStreamReader(new ByteArrayInputStream(DOC),
                "UTF-8");
        XMLStreamReader sr = STAX_F.createXMLStreamReader(r);
        try {
            streamThrough(sr);
            fail("Should not pass");
        } catch (WstxLazyException e) {
            verifyException(e, "FullDTDReader has reached recursion depth limit of 500");
        }
        sr.close();
    }

    public void testIssueStax2ByteArray() throws Exception
    {
        // Then "native" Byte array
        Stax2ByteArraySource src = new Stax2ByteArraySource(DOC, 0, DOC.length);
        XMLStreamReader sr = STAX_F.createXMLStreamReader(src);
        try {
            streamThrough(sr);
            fail("Should not pass");
        } catch (WstxLazyException e) {
            verifyException(e, "FullDTDReader has reached recursion depth limit of 500");
        }
        sr.close();
    }
}

