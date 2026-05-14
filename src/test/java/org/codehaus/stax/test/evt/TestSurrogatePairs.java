package org.codehaus.stax.test.evt;

import java.io.*;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import org.junit.jupiter.api.Test;

public class TestSurrogatePairs
    extends wstxtest.BaseWstxTest
{
    @Test
    public void testIssue280() throws Exception
    {
        XMLInputFactory xif = getInputFactory();
        InputStream inputStream = getClass().getResourceAsStream("surrogate-pairs.xml");
        XMLEventReader eventReader = xif.createXMLEventReader(inputStream);
    
        while (eventReader.hasNext()) {
            eventReader.next();
        }
    }
}
