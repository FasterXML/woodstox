package stax2.vwstream;

import java.io.*;
import javax.xml.stream.*;

import org.codehaus.stax2.*;
import org.codehaus.stax2.validation.*;

import stax2.vstream.BaseStax2ValidationTest;

abstract class BaseOutputTest
    extends BaseStax2ValidationTest // from sister package
{
    public XMLStreamWriter2 getDTDValidatingWriter(Writer w, String dtdSrc,
            boolean nsAware, boolean repairing)
        throws XMLStreamException
    {
        XMLOutputFactory2 outf = getOutputFactory();
        outf.setProperty(XMLStreamProperties.XSP_NAMESPACE_AWARE, new Boolean(nsAware));
        outf.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, new Boolean(repairing));

        XMLStreamWriter2 strw = (XMLStreamWriter2)outf.createXMLStreamWriter(w);
        XMLValidationSchema schema = parseDTDSchema(dtdSrc);

        strw.validateAgainst(schema);
        strw.writeStartDocument();
        return strw;
    }

    public XMLStreamWriter2 getSchemaValidatingWriter(Writer w, String schemaSrc,
            boolean repairing)
        throws XMLStreamException
    {
        XMLOutputFactory2 outf = getOutputFactory();
        outf.setProperty(XMLStreamProperties.XSP_NAMESPACE_AWARE, true);
        outf.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, new Boolean(repairing));

        XMLStreamWriter2 strw = (XMLStreamWriter2)outf.createXMLStreamWriter(w);
        XMLValidationSchema schema = parseW3CSchema(schemaSrc);

        strw.validateAgainst(schema);
        strw.writeStartDocument();
        return strw;
    }
}
