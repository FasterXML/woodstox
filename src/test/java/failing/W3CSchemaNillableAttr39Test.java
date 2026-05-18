package failing;

import wstxtest.vstream.BaseValidationTest;

import org.codehaus.stax2.validation.XMLValidationSchema;
import org.junit.jupiter.api.Test;

/**
 * Reproducer for Woodstox issue #39: an element declared {@code nillable="true"}
 * that carries {@code xsi:nil="true"} should still accept attribute uses declared
 * on its type (e.g. {@code country="US"}). Xerces and other Schema validators
 * accept the document; Woodstox rejects it with
 * {@code WstxValidationException: unexpected attribute "country"}.
 *<p>
 * Root cause is in the shaded MSV
 * ({@code com.sun.msv.verifier.regexp.xmlschema.XSAcceptor#createChildAcceptor}):
 * when {@code xsi:nil="true"} is encountered, MSV builds the nilled acceptor
 * with {@code Expression.epsilon} as the combined attribute+content expression,
 * which discards the type's attribute uses. Per XML Schema Part 1 §3.3.4 a
 * nilled element must have no character/element children, but its declared
 * attribute uses still apply.
 *<p>
 * Lives under {@code failing/} because the fix requires either an upstream MSV
 * release or a non-trivial Woodstox-side workaround in
 * {@code GenericMsvValidator.validateElementStart}.
 */
public class W3CSchemaNillableAttr39Test
    extends BaseValidationTest
{
    final static String SCHEMA =
        "<?xml version='1.0' encoding='utf-8'?>\n"
        +"<xsd:schema xmlns='http://www.openuri.org/mySchema'"
        +" xmlns:xsd='http://www.w3.org/2001/XMLSchema'"
        +" targetNamespace='http://www.openuri.org/mySchema' version='2.0'>\n"
        +"  <xsd:element name='purchaseOrder' type='PurchaseOrderType'/>\n"
        +"  <xsd:element name='comment' type='comment_type'/>\n"
        +"  <xsd:complexType name='comment_type'>\n"
        +"    <xsd:choice>\n"
        +"      <xsd:element name='annotation' type='xsd:string'/>\n"
        +"      <xsd:element name='note' type='xsd:string'/>\n"
        +"    </xsd:choice>\n"
        +"    <xsd:attribute name='country' type='xsd:string'/>\n"
        +"  </xsd:complexType>\n"
        +"  <xsd:complexType name='PurchaseOrderType'>\n"
        +"    <xsd:sequence>\n"
        +"      <xsd:element name='comment' type='comment_type' nillable='true'/>\n"
        +"    </xsd:sequence>\n"
        +"    <xsd:attribute name='orderDate' type='xsd:date'/>\n"
        +"  </xsd:complexType>\n"
        +"</xsd:schema>";

    // Sanity check: a nilled element with no extra attributes should validate.
    @Test
    public void testNilledNoAttributes() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA);
        final String XML =
            "<data:purchaseOrder orderDate='2006-10-30'"
            +" xmlns:data='http://www.openuri.org/mySchema'"
            +" xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'>\n"
            +"  <comment xsi:nil='true'/>\n"
            +"</data:purchaseOrder>";
        ValidationMode.reader.validate(schema, XML);
    }

    // The failing case from issue #39: nilled element carries a declared attribute.
    @Test
    public void testNilledWithDeclaredAttribute() throws Exception
    {
        XMLValidationSchema schema = parseW3CSchema(SCHEMA);
        final String XML =
            "<data:purchaseOrder orderDate='2006-10-30'"
            +" xmlns:data='http://www.openuri.org/mySchema'"
            +" xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'>\n"
            +"  <comment country='US' xsi:nil='true'/>\n"
            +"</data:purchaseOrder>";
        // Should pass per XML Schema Part 1 §3.3.4: attribute uses still apply
        // on a nilled element. Currently fails with
        // "WstxValidationException: unexpected attribute \"country\"".
        ValidationMode.reader.validate(schema, XML);
    }
}
