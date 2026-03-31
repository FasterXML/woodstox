package wstxtest.wstream;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.stax2.*;

/**
 * Woodstox-specific stream writer test suite that will ensure that the
 * name validation property works properly (as expected)
 */
public class TestNameValidation
    extends BaseWriterTest
{
    final static String DUMMY_URL = "http://someurl";
    final static String ATTR_VALUE = "value";
    final static String PI_DATA = "just some proc.instr. content!!";

    /* Notes about validity
     *
     * - Dot is valid, but not as first name char
     */

    final static String[] VALID_NS_PREFIXES = new String[] {
        "a", "ab_123", "this.attr", "_uber", "pre-fix"
    };
    final static String[] VALID_NS_NAMES = new String[] {
        "tag", "i", "my.dotted.name",
        // note: dot is valid only as non-first char...
        "x.12", "a_32", "__mymy", "a-ha"
    };
    final static String[] VALID_NON_NS_NAMES = new String[] {
        "ns:elem", ":::a", "xyz"
    };
    final static String[] VALID_NS_ROOT_NAMES = new String[] {
        "ns:elem", "abc", "a1:foobar"
    };

    final static String[] INVALID_NS_PREFIXES = new String[] {
        "a:1", "1abc", "xy+z", "r&b", "", "fun\tny", ".ns", "-a"
    };
    final static String[] INVALID_NS_NAMES = new String[] {
        ":abc", "ns:elem", ".name", "", "1", "a<b", "stuff with spaces",
        "-xyz"
    };
    final static String[] INVALID_NON_NS_NAMES = new String[] {
        "ab>foo", "", "1abc", " space", ".abc", "-23"
    };
    final static String[] INVALID_NS_ROOT_NAMES = new String[] {
        "ns:elem:blah", "a<>2", "", ".x", "-ab", "3cpu"
    };

    /*
    ////////////////////////////////////////////////////
    // Main test methods
    ////////////////////////////////////////////////////
     */

    public void testValidElemNames()
        throws XMLStreamException
    {
        for (int n = 0; n < 4; ++n) {
            boolean ns = ((n & 1) == 0);
            boolean validate = ((n & 2) == 0);
            XMLStreamWriter sw = startDoc(validate, ns);

            // Let's add a dummy root:
            sw.writeStartElement("dummy");

            if (ns) { // need to check both prefixes and names
                /* Note: when not repairing, need not worry about
                 * namespace binding... makes life easier here
                 */
                for (String name : VALID_NS_NAMES) {
                    sw.writeEmptyElement(name);
                    sw.writeStartElement(name);
                    sw.writeEndElement();
                }
                for (String prefix : VALID_NS_PREFIXES) {
                    sw.writeEmptyElement(prefix, "elem", DUMMY_URL);
                    sw.writeStartElement(prefix, "elem", DUMMY_URL);
                    sw.writeEndElement();
                }
            } else {
                for (String name : VALID_NON_NS_NAMES) {
                    sw.writeEmptyElement(name);
                    sw.writeStartElement(name);
                    sw.writeEndElement();
                }
            }

            sw.writeEndElement();
            closeDoc(sw);
        }
    }

    public void testInvalidElemNames()
        throws XMLStreamException
    {
        for (int n = 0; n < 2; ++n) {
            boolean ns = (n == 1);
            if (ns) { // need to check both prefixes and names
                for (String name : INVALID_NS_NAMES) {
                    for (String prefix : INVALID_NS_PREFIXES) {
                        doTestInvalidElemName(true, prefix, name);
                        doTestInvalidElemName(true, null, name);
                    }
                }
            } else {
                for (String name : INVALID_NON_NS_NAMES) {
                    doTestInvalidElemName(false, null, name);
                }
            }
        }
    }

    private void doTestInvalidElemName(boolean ns, String prefix, String name)
        throws XMLStreamException
    {
        for (int i = 0; i < 2; ++i) { // to test both empty and non-empty elems
            boolean empty = (i == 1);
            try {
                XMLStreamWriter sw = startDoc(true, ns);
                sw.writeStartElement("dummy");

                if (prefix == null) {
                    if (empty) {
                        sw.writeEmptyElement(name);
                    } else {
                        sw.writeStartElement(name);
                        sw.writeEndElement();
                    }
                } else {
                    if (empty) {
                        sw.writeEmptyElement(prefix, name, DUMMY_URL);
                    } else {
                        sw.writeStartElement(prefix, name, DUMMY_URL);
                        sw.writeEndElement();
                    }
                }
                
                sw.writeEndElement();
                closeDoc(sw);
                
            } catch (XMLStreamException iae) {
                continue; // good
            }

            fail("Failed to catch an invalid element name/prefix (ns = "+ns+"); name='"
                 +name+"', prefix = "
                 +((prefix == null) ? "NULL" : ("'"+prefix+"'"))+".");
        }
    }

    // [woodstox-core#107]: need to include more info
    public void testInvalidElemNameExceptionMessage() throws XMLStreamException
    {
        // First, empty "name":
        _testInvalidElemNameExceptionMessage(false, null, "", -1);
        _testInvalidElemNameExceptionMessage(false, "prefix", "", -1);
        _testInvalidElemNameExceptionMessage(true, null, "", -1);
        _testInvalidElemNameExceptionMessage(true, "prefix", "", -1);

        // Then non-empty, first char bad
        _testInvalidElemNameExceptionMessage(false, null, "1", 0);
        _testInvalidElemNameExceptionMessage(false, "prefix", "1", 0);
        _testInvalidElemNameExceptionMessage(true, null, "1", 0);
        _testInvalidElemNameExceptionMessage(true, "prefix", "1", 0);

        _testInvalidElemNameExceptionMessage(true, null, ":", 0);
        _testInvalidElemNameExceptionMessage(true, "prefix", ":", 0);

        // and then some other

        _testInvalidElemNameExceptionMessage(false, null, "a b", 1);
        _testInvalidElemNameExceptionMessage(false, "prefix", "a b", 1);
        _testInvalidElemNameExceptionMessage(true, null, "a b", 1);
        _testInvalidElemNameExceptionMessage(true, "prefix", "a b", 1);

        _testInvalidElemNameExceptionMessage(true, null, "a:b", 1);
        _testInvalidElemNameExceptionMessage(true, "prefix", "a:b", 1);
    }

    private void _testInvalidElemNameExceptionMessage(boolean ns, String prefix,
            String name, int index)
        throws XMLStreamException
    {
        try {
            XMLStreamWriter sw = startDoc(true, ns);
            sw.writeStartElement("dummy");

            if (prefix == null) {
                sw.writeStartElement(name);
                sw.writeEndElement();
            } else {
                sw.writeStartElement(prefix, name, DUMMY_URL);
                sw.writeEndElement();
            }
            
            sw.writeEndElement();
            closeDoc(sw);

            fail("Failed to catch an invalid element name/prefix (ns = "+ns+"); name='"
                    +name+"', prefix = "
                    +((prefix == null) ? "NULL" : ("'"+prefix+"'"))+".");
        } catch (XMLStreamException sex) {
            if (name.isEmpty()) {
                verifyException(sex, "Illegal to pass empty name");
            } else if (index == 0) {
                verifyException(sex, "Illegal first name character '");
                verifyException(sex, "in name \""+name+"\"");
            } else {
                verifyException(sex, "Illegal name character '");
                verifyException(sex, "in name \""+name+"\"");
                verifyException(sex, " (index #"+index+")");
            }
        }
    }

    public void testValidAttrNames()
        throws Exception
    {
        for (int n = 0; n < 4; ++n) {
            boolean ns = ((n & 1) == 0);
            boolean validate = ((n & 2) == 0);
            XMLStreamWriter sw = startDoc(validate, ns);

            // Let's add a dummy root:
            sw.writeStartElement("dummy");

            if (ns) {
                for (String name : VALID_NS_NAMES) {
                    sw.writeAttribute(name, ATTR_VALUE);
                }
                for (String prefix : VALID_NS_PREFIXES) {
                    sw.writeAttribute(prefix, DUMMY_URL, "attr", ATTR_VALUE);
                }
            } else {
                for (String name : VALID_NON_NS_NAMES) {
                    sw.writeAttribute(name, ATTR_VALUE);
                }
            }

            sw.writeEndElement();
            closeDoc(sw);
        }
    }

    public void testInvalidAttrNames()
        throws Exception
    {
        for (int n = 0; n < 2; ++n) {
            boolean ns = (n == 1);
            if (ns) { // need to check both prefixes and names
                for (String name : INVALID_NS_NAMES) {
                    for (String prefix : INVALID_NS_PREFIXES) {
                        doTestInvalidAttrName(true, prefix, name);
                        doTestInvalidAttrName(true, null, name);
                    }
                }
            } else {
                for (String name : INVALID_NON_NS_NAMES) {
                    doTestInvalidAttrName(false, null, name);
                }
            }
        }
    }

    private void doTestInvalidAttrName(boolean ns, String prefix, String name)
        throws XMLStreamException
    {
        XMLStreamWriter sw = startDoc(true, ns);
        sw.writeStartElement("dummy");
        try {
            if (prefix == null) {
                sw.writeAttribute(name, ATTR_VALUE);
            } else {
                sw.writeAttribute(prefix, DUMMY_URL, name, ATTR_VALUE);
            }
        } catch (XMLStreamException sex) {
            sw.writeEndElement();
            closeDoc(sw);
            return; // good
        }

        fail("Failed to catch an invalid attr name/prefix (ns = "+ns+"); name='"
             +name+"', prefix = "
             +((prefix == null) ? "NULL" : ("'"+prefix+"'"))+".");
    }

    /**
     * According to XML Namespaces 1.1 specification, PI targets
     * can not contain colons either...
     */
    public void testValidPiNames()
        throws Exception
    {
        for (int n = 0; n < 4; ++n) {
            boolean ns = ((n & 1) == 0);
            boolean validate = ((n & 2) == 0);
            XMLStreamWriter sw = startDoc(validate, ns);

            // Let's add a dummy root:
            sw.writeStartElement("dummy");

            /* No colons allowed in namespace-aware mode
             */
            String[] strs = ns ? VALID_NS_NAMES : VALID_NON_NS_NAMES;
            for (String name : strs) {
                sw.writeProcessingInstruction(name);
                sw.writeProcessingInstruction(name, PI_DATA);
            }

            sw.writeEndElement();
            closeDoc(sw);
        }
    }

    public void testInvalidPiNames()
        throws Exception
    {
        for (int n = 0; n < 4; ++n) {
            boolean ns = ((n & 1) == 0);
            boolean empty = ((n & 2) == 0);
            String[] strs = ns ? INVALID_NS_NAMES : INVALID_NON_NS_NAMES;

            for (String name : strs) {
                XMLStreamWriter sw = startDoc(true, ns);
                sw.writeStartElement("dummy");

                try {
                    if (empty) {
                        sw.writeProcessingInstruction(name);
                    } else {
                        sw.writeProcessingInstruction(name, PI_DATA);
                    }
                } catch (XMLStreamException sex) {
                    sw.writeEndElement();
                    closeDoc(sw);
                    continue; // good
                }

                fail("Failed to catch an invalid proc.instr. name (ns = "+ns+") '"
                     +name+"'.");
            }
        }
    }

    /**
     * Since the root element name is NOT really properly split (ie. it's
     * never dealt with as a scoped name), we can only check if that it has
     * zero or one colons (in NS-awre) mode, but nothing further
     */
    public void testValidRootNames()
        throws Exception
    {
        for (int n = 0; n < 4; ++n) {
            boolean ns = ((n & 1) == 0);
            boolean validate = ((n & 2) == 0);

            String[] strs = ns ? VALID_NS_ROOT_NAMES : VALID_NON_NS_NAMES;
            for (String rootName : strs) {
                XMLStreamWriter2 sw = (XMLStreamWriter2) startDoc(validate, ns);
                // only root name is mandatory, others are optional
                sw.writeDTD(rootName, null, null, null);
                // need a matching root, then:
                if (ns) { // may need to split
                    int ix = rootName.indexOf(':');
                    if (ix > 0) {
                        sw.writeEmptyElement(rootName.substring(0, ix),
                                             rootName.substring(ix+1),
                                             DUMMY_URL);
                    } else {
                        sw.writeEmptyElement(rootName);
                    }
                } else {
                    sw.writeEmptyElement(rootName);
                }
                closeDoc(sw);
            }
        }
    }

    public void testInvalidRootNames()
        throws Exception
    {
        for (int n = 0; n < 2; ++n) {
            boolean ns = ((n & 1) == 0);
            String[] strs = ns ? INVALID_NS_ROOT_NAMES : INVALID_NON_NS_NAMES;

            for (String rootName : strs) {
                XMLStreamWriter2 sw = (XMLStreamWriter2) startDoc(true, ns);
                try {
                    // only root name is mandatory, others are optional
                    sw.writeDTD(rootName, null, null, null);
                    sw.writeEmptyElement(rootName); // need a root...and should match too
                } catch (XMLStreamException sex) {
                    continue; // good
                }

                fail("Failed to catch an invalid DTD root name (ns = "+ns+") '"
                     +rootName+"'.");
            }
        }
    }

    /**
     * According to XML Namespaces 1.1 specification, entity names (ids)
     * can not contain colons either...
     *<p>
     * Note: Here we count on the fact that the current stream writer
     * does not (and actually, can not!) verify whether the entity 
     * has been properly declared.
     */
    public void testValidEntityNames()
        throws Exception
    {
        for (int n = 0; n < 4; ++n) {
            boolean ns = ((n & 1) == 0);
            boolean validate = ((n & 2) == 0);
            XMLStreamWriter sw = startDoc(validate, ns);

            // Let's add a dummy root:
            sw.writeStartElement("dummy");

            /* No colons allowed in namespace-aware mode
             */
            String[] strs = ns ? VALID_NS_NAMES : VALID_NON_NS_NAMES;
            for (String name : strs) {
                sw.writeEntityRef(name);
            }

            sw.writeEndElement();
            closeDoc(sw);
        }
    }

    public void testInvalidEntityNames()
        throws XMLStreamException
    {
        for (int n = 0; n < 2; ++n) {
            boolean ns = ((n & 1) == 0);
            String[] strs = ns ? INVALID_NS_ROOT_NAMES : INVALID_NON_NS_NAMES;

            for (String name : strs) {
                XMLStreamWriter2 sw = (XMLStreamWriter2) startDoc(true, ns);
                sw.writeStartElement("dummy");
                try {
                    // only root name is mandatory, others are optional
                    sw.writeEntityRef(name);
                } catch (XMLStreamException sex) {
                    sw.writeEndElement();
                    closeDoc(sw);
                    continue; // good
                }

                fail("Failed to catch an invalid entity name (ns = "+ns+") '"
                     +name+"'.");
            }
        }
    }

    /*
    ////////////////////////////////////////////////////
    // Internal methods
    ////////////////////////////////////////////////////
     */

    private XMLOutputFactory getFactory(boolean validateNames, boolean ns)
        throws XMLStreamException
    {
        XMLOutputFactory f = getOutputFactory();
        setValidateNames(f, validateNames);
        setNamespaceAware(f, ns);
        // Let's disable repairing
        setRepairing(f, false);
        return f;
    }

    private XMLStreamWriter startDoc(boolean validateNames, boolean ns)
        throws XMLStreamException
    {
        XMLOutputFactory f = getFactory(validateNames, ns);
        XMLStreamWriter sw = f.createXMLStreamWriter(new StringWriter());
        sw.writeStartDocument();
        return sw;
    }

    private void closeDoc(XMLStreamWriter sw)
        throws XMLStreamException
    {
        sw.writeEndDocument();
        sw.close();
    }
}

