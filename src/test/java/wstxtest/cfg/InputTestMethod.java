package wstxtest.cfg;

import javax.xml.stream.XMLInputFactory;

public interface InputTestMethod
{
    public void runTest(XMLInputFactory f, InputConfigIterator it)
        throws Exception;
}
