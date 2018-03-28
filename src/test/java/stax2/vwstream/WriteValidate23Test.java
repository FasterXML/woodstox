package stax2.vwstream;

import java.io.*;

import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.XMLStreamWriter2;

public class WriteValidate23Test
    extends BaseOutputTest
{
    public void testSchemaValidatingCopy23() throws Exception
    {
        final String SCHEMA = "<?xml version='1.0' ?>\n"
+"<xs:schema elementFormDefault='unqualified'\n"
+"           xmlns:xs='http://www.w3.org/2001/XMLSchema'>\n"
+"    <xs:element name='Document' type='xs:int'/>\n"
+"</xs:schema>";
        final String CONTENT = "<Document>124</Document>";
        final String DOC = "<?xml version='1.0' encoding='UTF-8'?>\n"+CONTENT;
                

        StringWriter strw = new StringWriter();
        XMLStreamWriter2 xmlWriter = getSchemaValidatingWriter(strw, SCHEMA, false);
        XMLStreamReader2 xmlReader = constructNsStreamReader(DOC, false);

        while (xmlReader.hasNext()) {
            /*int type =*/ xmlReader.next();
            xmlWriter.copyEventFromReader(xmlReader, true);
        }
 
        xmlWriter.close();
        xmlReader.close();

        String xml = strw.toString();
        if (!xml.contains(CONTENT)) {
            fail("Should contain ["+CONTENT+"], does not: ["+xml+"]");
        }
    }
}
