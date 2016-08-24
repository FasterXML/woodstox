package wstxtest;

import javax.xml.stream.*;

import org.codehaus.stax2.*;

import com.ctc.wstx.cfg.ErrorConsts;
import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.stax.WstxInputFactory;

/**
 * Simple test-driver that tries to exercise some of basic input factory
 * settings, like instantiating various reader instances, checking for
 * invalid arguments and so on.
 */
public class TestInputFactory
    extends BaseWstxTest
{
    public void testConfig()
        throws XMLStreamException
    {
        XMLInputFactory2 f = getNewInputFactory();

        ReaderConfig cfg = ((WstxInputFactory) f).getConfig();
        assertNotNull(cfg);

        assertNull(f.getEventAllocator());
        assertNull(f.getXMLResolver());

        assertNull(f.getXMLReporter());
        MyReporter rep = new MyReporter();
        f.setXMLReporter(rep);
        assertEquals(rep, f.getXMLReporter());

        assertFalse(f.isPropertySupported("foobar"));
    }

    public void testMisc()
        throws XMLStreamException
    {
        /* This is silly, but coverage testing is not happy that our
         * error-constant-defining class is never constructed.
         * So here we go, just to mark it off the list...
         */
        ErrorConsts ec = new ErrorConsts();
        assertNotNull(ec); // silly, but otherwise eclipse would about unused..
        assertNotNull(ErrorConsts.tokenTypeDesc(XMLStreamConstants.START_DOCUMENT));
        assertNotNull(ErrorConsts.tokenTypeDesc(XMLStreamConstants.END_DOCUMENT));
        assertNotNull(ErrorConsts.tokenTypeDesc(XMLStreamConstants.ATTRIBUTE));
    }

    /*
    ////////////////////////////////////////////////////////////
    // Non-test methods etc
    ////////////////////////////////////////////////////////////
     */

    final static class MyReporter
        implements XMLReporter
    {
        @Override
        public void report(String message, String errorType, Object relatedInformation, Location location)
        {
            // fine...
        }
    }
}
