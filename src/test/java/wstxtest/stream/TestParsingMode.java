package wstxtest.stream;

import javax.xml.stream.*;

import com.ctc.wstx.api.WstxInputProperties;

/**
 * This unit tests verifies that different input parsing modes
 * (set via property {@link WstxInputProperties#P_INPUT_PARSING_MODE})
 * behave as expected
 */
public class TestParsingMode
    extends BaseStreamTest
{
    final static String XML_SINGLE_DOC =
        "<?xml version='1.0'?><root>text</root><!--comment-->"
        ;

    final static String XML_MULTI_DOC =
        "<?xml version='1.0'?><root>text</root><!--comment-->\n"
        +"<?xml version='1.0'?><root>text</root><?proc instr>\n"
        +"<?xml version='1.0'?><root>text</root><!--comment-->"
        +"<?xml version='1.0' encoding='UTF-8'?><root>text</root><!--comment-->"
        +"<?xml version='1.0' standalone='yes'?><root>text</root><!--comment-->"
        +"<?xml version='1.0'?><root>text</root><!--comment-->"
        ;

    final static String XML_FRAGMENT =
        "<branch>text</branch><branch>more</branch><!--comment-->"
        ;
    final static String XML_FRAGMENT2 =
        "<branch />some text<!-- comment --><?proc instr?>   ";
        ;

    final static String XML_UNBALANCED =
        "<branch><leaf>text</leaf>"
        ;

    public void testSingleDocumentMode()
        throws XMLStreamException
    {
        // First the valid case:
        streamThrough(getReader(XML_SINGLE_DOC,
                                WstxInputProperties.PARSING_MODE_DOCUMENT));

        // Others will fail though
        streamThroughFailing(getReader(XML_FRAGMENT,
                                       WstxInputProperties.PARSING_MODE_DOCUMENT),
                             "Expected an exception for fragment (non-single root) input, in single-document mode");
        streamThroughFailing(getReader(XML_FRAGMENT2,
                                       WstxInputProperties.PARSING_MODE_DOCUMENT),
                             "Expected an exception for fragment (root-level text) input, in single-document mode");
        streamThroughFailing(getReader(XML_MULTI_DOC,
                                       WstxInputProperties.PARSING_MODE_DOCUMENT),
                             "Expected an exception for multi-document input, in single-document mode");


        // As should the generally invalid ones:
        streamThroughFailing(getReader(XML_UNBALANCED,
                                       WstxInputProperties.PARSING_MODE_DOCUMENT),
                             "Expected an exception for unbalanced xml content");
    }

    public void testMultiDocumentMode()
        throws XMLStreamException
    {
        // First the main valid case:
        streamThroughOk(getReader(XML_MULTI_DOC,
                                  WstxInputProperties.PARSING_MODE_DOCUMENTS),
                        "multi-doc input in multi-doc mode");

        // But the alternate cases should actually work too:
        streamThroughOk(getReader(XML_SINGLE_DOC,
                                WstxInputProperties.PARSING_MODE_DOCUMENTS),
                        "single-doc input in multi-doc mode");
        streamThroughOk(getReader(XML_FRAGMENT,
                                  WstxInputProperties.PARSING_MODE_DOCUMENTS),
                        "fragment input in multi-doc mode");


        // Except for some fragment cases:
        streamThroughFailing(getReader(XML_FRAGMENT2,
                                       WstxInputProperties.PARSING_MODE_DOCUMENTS),
                             "Expected an exception for fragments with root-level textual content");

        // And broken one not
        streamThroughFailing(getReader(XML_UNBALANCED,
                                       WstxInputProperties.PARSING_MODE_DOCUMENTS),
                             "Expected an exception for unbalanced xml content");
    }

    public void testFragmentMode()
        throws XMLStreamException
    {
        // First the main valid case2:
        streamThroughOk(getReader(XML_FRAGMENT,
                                  WstxInputProperties.PARSING_MODE_FRAGMENT),
                        "fragment input in fragment mode");
        streamThroughOk(getReader(XML_FRAGMENT2,
                                  WstxInputProperties.PARSING_MODE_FRAGMENT),
                        "fragment input in fragment mode");

        /* The single doc case actually works, since the xml declaration
         * gets handled by the bootstrapper... (kind of implementation
         * side effect)
         */
        streamThroughOk(getReader(XML_SINGLE_DOC,
                                  WstxInputProperties.PARSING_MODE_FRAGMENT),
                        "single-doc input in fragment mode");

        // But multi-doc will fail, due to second xml declaration
        streamThroughFailing(getReader(XML_MULTI_DOC,
                                       WstxInputProperties.PARSING_MODE_FRAGMENT),
                             "Expected an exception for multi-document input, in fragment mode");


        // But not the invalid one:
        streamThroughFailing(getReader(XML_UNBALANCED,
                                       WstxInputProperties.PARSING_MODE_FRAGMENT),
                             "Expected an exception for unbalanced xml content");
    }

    /*
    ////////////////////////////////////////
    // Private methods, other
    ////////////////////////////////////////
     */

    private XMLStreamReader getReader(String contents, WstxInputProperties.ParsingMode mode)
        throws XMLStreamException
    {
        XMLInputFactory f = getInputFactory();
        f.setProperty(WstxInputProperties.P_INPUT_PARSING_MODE, mode);
        return constructStreamReader(f, contents);
    }

    void streamThroughOk(XMLStreamReader sr, String type)
        throws XMLStreamException
    {
        try {
            streamThrough(sr);
        } catch (XMLStreamException sex) {
            fail("Did not expect and exception for "+type+"; got: "+sex);
        }
    }
}
