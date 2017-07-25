package wstxtest.vstream;

import java.io.*;

import javax.xml.stream.*;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.cfg.XmlConsts;
import com.ctc.wstx.dtd.DTDSubset;
import com.ctc.wstx.dtd.FullDTDReader;
import com.ctc.wstx.io.DefaultInputResolver;
import com.ctc.wstx.io.WstxInputSource;

import wstxtest.stream.BaseStreamTest;

/**
 * This test suite should really be part of wstx-tools package, but since
 * there is some supporting code within core Woodstox, it was added here.
 * That way it is easier to check that no DTDFlatten functionality is
 * broken by low-level changes.
 */
public class TestFlattening
    extends BaseStreamTest
{
    public void testFlatteningInclusive()
        throws IOException, XMLStreamException
    {
        /* Note: since we deal with this as an external resource, it may
         * need encoding pseudo-attribute if there's xml declaration
         */
        final String INPUT_DTD =
            "<?xml version='1.0' encoding='UTF-8'?>\n"
            +"<!ELEMENT root (branch+)>\n"
            +"<!ATTLIST root attr CDATA #IMPLIED\n"
            +" attr2 IDREF #IMPLIED>\n"
            +"<!-- comment -->\n"
            +"<!ENTITY % pe '<!-- comment! -->'>\n"
            +"<!ELEMENT branch (child*)>\n"
            +"<!ELEMENT child (#PCDATA)>\n"
            +"<![INCLUDE[\n"
            +"<!ATTLIST child attr CDATA 'def!'>\n"
            +"]]>\n"
            +"<!NOTATION myNot SYSTEM 'foobar:xyz'>\n"
            +"%pe;\n"
            +"<!ENTITY a '&#65;'>\n"
            +"<?proc instr?>\r\n"
            ;
        //StringReader strr = new StringReader(DTD);
        ReaderConfig cfg = ReaderConfig.createFullDefaults();
        for (int i = 0; i < 8; ++i) {
            boolean inclComments = (i & 4) != 0;
            boolean inclConditionals = (i & 2) != 0;
            boolean inclPEs = (i & 1) != 0;
            WstxInputSource input = DefaultInputResolver.sourceFromString
                (null, cfg, "[dtd]", /*xml version for compat checks*/ XmlConsts.XML_V_UNKNOWN, INPUT_DTD);
            StringWriter strw = new StringWriter();
            /*DTDSubset ss =*/ FullDTDReader.flattenExternalSubset
                (input, strw,
                 inclComments, inclConditionals, inclPEs);
            strw.flush();
            String output = strw.toString();

            /* Ok, so... how do we test it? For now, let's actually
             * just re-parse it to ensure it seems valid? And let's also
             * compare second-time output.
             */
            input = DefaultInputResolver.sourceFromString
                (null, cfg, "[dtd]", /*xml version for compatibility checks*/ XmlConsts.XML_V_UNKNOWN, output);

            strw = new StringWriter();
            DTDSubset ss = FullDTDReader.flattenExternalSubset
                (input, strw, inclComments, inclConditionals, inclPEs);
            assertNotNull(ss);
            strw.flush();
            String output2 = strw.toString();

            assertEquals(output, output2);
        }
    }
}

