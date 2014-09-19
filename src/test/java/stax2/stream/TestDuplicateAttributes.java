package stax2.stream;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import stax2.BaseStax2Test;

/**
 * This unit test exposes a bug in Woodstox's duplicate attribute detection logic.
 */
public class TestDuplicateAttributes
    extends BaseStax2Test
{

	/**
	 * This test shows a scenario where Woodstox correctly complains about duplicate attributes.
	 */
	public void testDupAttrsMinimal() throws XMLStreamException {
		final XMLStreamReader reader = constructNsStreamReader("<x a='a' a='b'/>", false);
		try {
			reader.next();
			fail("Should have caught duplicate attributes");
		} catch (XMLStreamException e) {
		    verifyException(e, "duplicate attribute");
		}
          reader.close();
	}

	/**
	 * This test shows a scenario where Woodstox fails to complain about duplicate attributes.
	 */
	public void testDupAttrsMultiple() throws XMLStreamException {
	    final XMLStreamReader reader = constructNsStreamReader(
	            "<a id='test' type='test' c123456789='1' c123456789='2'/>", false);
         try {
             reader.next();
             fail("Should have caught duplicate attributes");
        } catch (XMLStreamException e) {
            verifyException(e, "duplicate attribute");
        }
        reader.close();
	}
}
