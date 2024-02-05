package failing;

import java.io.StringWriter;

import javax.xml.stream.*;

import org.codehaus.stax2.validation.*;

import com.ctc.wstx.sw.RepairingNsStreamWriter;

/**
 * A reproducer for https://github.com/FasterXML/woodstox/issues/190
 * Move to {@link wstxtest.vstream.TestRelaxNG} once fixed.
 */
public class TestRelaxNG190
    extends wstxtest.vstream.TestRelaxNG
{

    public void testPartialValidationOk()
        throws XMLStreamException
    {
        /* Hmmh... RelaxNG does define expected root. So need to
         * wrap the doc...
         */
        String XML =
                "<dummy>\n"
                +"<dict>\n"
                +"<term type=\"name\">\n"
                +"  <word>foobar</word>\n"
                +"  <description>Foo Bar</description>\n"
                +"</term></dict>\n"
                +"</dummy>"
                ;
        XMLValidationSchema schema = parseRngSchema(SIMPLE_RNG_SCHEMA);
        {
            StringWriter writer = new StringWriter();
            RepairingNsStreamWriter sw = (RepairingNsStreamWriter) constructStreamWriter(writer, true, true);
            _testPartialValidationOk(XML, schema, sw, writer);
        }
    }


}
