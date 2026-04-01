package com.ctc.wstx.dtd;

import junit.framework.TestCase;

import com.ctc.wstx.util.PrefixedName;

/**
 * Regression test: ChoiceModel.toString() was missing the opening '('
 * while ConcatModel.toString() had it, producing "a | b)" instead of "(a | b)".
 */
public class TestChoiceModelToString extends TestCase
{
    public void testToStringHasBalancedParens()
    {
        TokenModel a = new TokenModel(new PrefixedName(null, "a"));
        TokenModel b = new TokenModel(new PrefixedName(null, "b"));
        TokenModel c = new TokenModel(new PrefixedName(null, "c"));

        ChoiceModel model = new ChoiceModel(new ModelNode[] { a, b, c });
        String result = model.toString();

        assertTrue("toString() should start with '(' but was: " + result,
                result.startsWith("("));
        assertTrue("toString() should end with ')' but was: " + result,
                result.endsWith(")"));
        assertEquals("(a | b | c)", result);
    }
}
