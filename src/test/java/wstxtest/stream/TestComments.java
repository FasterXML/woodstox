package wstxtest.stream;

import javax.xml.stream.*;

import wstxtest.cfg.*;

public class TestComments
    extends BaseStreamTest
    implements InputTestMethod
{
    InputConfigIterator mConfigs;

    public TestComments() {
        super();
        mConfigs = new InputConfigIterator();
        Configs.addAll(mConfigs);
    }

    public void testValid()
        throws Exception
    {
        mConfigs.iterate(getInputFactory(), this);
    }

    /*
    //////////////////////////////////////////////////////
    // Internal methods
    //////////////////////////////////////////////////////
     */

    /**
     * Method called via input config iterator, with all possible
     * configurations
     */
    public void runTest(XMLInputFactory f, InputConfigIterator it)
        throws Exception
    {
        String XML = "<root>"
            +"<!-- first comment -->\n"
            +"  <!-- - - - - -->"
            +"<!-- Longer comment that contains quite a bit of content\n"
            +" so that we can check boundary - conditions too... -->"
            +"<!----><!-- and entities: &amp; &#12;&#x1d; -->\n"
            +"</root>";
        XMLStreamReader sr = constructStreamReader(f, XML);
        streamAndCheck(sr, it, XML, XML, false);
        // Let's also test real streaming...
        sr = constructStreamReader(f, XML);
        streamAndCheck(sr, it, XML, XML, true);
    }

}

