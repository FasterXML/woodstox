package failing;

import org.codehaus.stax2.validation.XMLValidationSchema;

import wstxtest.vstream.BaseValidationTest;

/**
 * Reproducer for Woodstox issue #87: parsing a W3C Schema that declares a
 * particle with a large numeric {@code maxOccurs} (e.g. {@code "25000"})
 * throws {@link StackOverflowError} during schema compilation, before any
 * document is validated. {@code maxOccurs="unbounded"} is unaffected.
 *<p>
 * MSV expands a numeric {@code maxOccurs="N"} into a binary tree of
 * {@code SequenceExp}/{@code ChoiceExp} nodes of depth ~N representing the
 * 0..N optional occurrences. Any of MSV's recursive grammar walkers can
 * overflow the stack while traversing that tree — the original issue
 * reported {@code AttributeWildcardComputer.onRef}; on this minimal
 * reproducer it surfaces in {@code RunAwayExpressionChecker.onChoice},
 * which runs earlier during schema compilation. The root cause is the
 * same in both cases (recursive descent via {@code ExpressionWalker} /
 * {@code ChoiceExp.visit} / {@code binaryVisit}).
 *<p>
 * {@code maxOccurs="unbounded"} is modelled with a single {@code OneOrMore}
 * node and so does not trigger the deep recursion.
 *<p>
 * Lives under {@code failing/} because the fix has to be made in MSV
 * (upstream fork at {@code github.com/xmlark/msv}) — either by rewriting
 * {@code ExpressionWalker} iteratively or by representing bounded-but-large
 * {@code maxOccurs} without a deep expansion. There is no clean Woodstox-side
 * workaround.
 */
public class W3CSchemaMaxOccursStackOverflow87Test
    extends BaseValidationTest
{
    // Large maxOccurs matches the value reported in the issue.
    final static String SCHEMA_LARGE_MAX_OCCURS =
        "<?xml version='1.0' encoding='UTF-8'?>\n"
        +"<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>\n"
        +"  <xs:element name='root'>\n"
        +"    <xs:complexType>\n"
        +"      <xs:sequence>\n"
        +"        <xs:element name='item' type='xs:string'"
        +" minOccurs='0' maxOccurs='25000'/>\n"
        +"      </xs:sequence>\n"
        +"    </xs:complexType>\n"
        +"  </xs:element>\n"
        +"</xs:schema>\n";

    // Sanity check: maxOccurs="unbounded" compiles fine — MSV models it as a
    // single OneOrMore node, so the AttributeWildcardComputer walk stays shallow.
    public void testUnboundedCompiles() throws Exception
    {
        final String schema = SCHEMA_LARGE_MAX_OCCURS.replace("'25000'", "'unbounded'");
        XMLValidationSchema vs = parseW3CSchema(schema);
        ValidationMode.reader.validate(vs,
            "<root><item>a</item><item>b</item></root>");
    }

    // The failing case from issue #87: large numeric maxOccurs overflows the
    // stack inside MSV's AttributeWildcardComputer during schema compilation.
    public void testLargeMaxOccursCompiles() throws Exception
    {
        XMLValidationSchema vs = parseW3CSchema(SCHEMA_LARGE_MAX_OCCURS);
        ValidationMode.reader.validate(vs,
            "<root><item>a</item><item>b</item></root>");
    }
}
