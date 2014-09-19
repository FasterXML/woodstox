package stax2.vwstream;

import java.io.*;
import javax.xml.stream.*;

import org.codehaus.stax2.*;
import org.codehaus.stax2.validation.*;

import stax2.BaseStax2Test;

abstract class BaseOutputTest
    extends BaseStax2Test
{
    public XMLStreamWriter2 getDTDValidatingWriter(Writer w, String dtdSrc,
                                                   boolean nsAware, boolean repairing)
        throws XMLStreamException
    {
        XMLOutputFactory2 outf = getOutputFactory();
        outf.setProperty(XMLStreamProperties.XSP_NAMESPACE_AWARE, new Boolean(nsAware));
        outf.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, new Boolean(repairing));

        XMLStreamWriter2 strw = (XMLStreamWriter2)outf.createXMLStreamWriter(w);
        XMLValidationSchemaFactory vd = XMLValidationSchemaFactory.newInstance(XMLValidationSchema.SCHEMA_ID_DTD);

        XMLValidationSchema schema = vd.createSchema(new StringReader(dtdSrc));

        strw.validateAgainst(schema);
        strw.writeStartDocument();
        return strw;
    }
}
