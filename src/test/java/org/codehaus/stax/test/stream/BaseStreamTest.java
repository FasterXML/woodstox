package org.codehaus.stax.test.stream;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.codehaus.stax.test.BaseStaxTest;

/**
 * Base class for all StaxTest unit tests that test basic non-validating
 * stream (cursor) API functionality.
 *
 * @author Tatu Saloranta
 */
public abstract class BaseStreamTest
    extends BaseStaxTest
{
    /**
     * Switch that can be turned on to verify to display ALL exact Exceptions
     * thrown when Exceptions are expected. This is sometimes necessary
     * when debugging, since it's impossible to automatically verify
     * that Exception is exactly the right one, since there is no
     * strict Exception type hierarchy for StAX problems.
     *<p>
     * Note: Not made 'final static', so that compiler won't inline
     * it. Makes possible to do partial re-compilations.
     * Note: Since it's only used as the default value, sub-classes
     *  can separately turn it off as necessary
     */
    //protected static boolean DEF_PRINT_EXP_EXCEPTION = true;
    protected static boolean DEF_PRINT_EXP_EXCEPTION = false;

    protected boolean PRINT_EXP_EXCEPTION = DEF_PRINT_EXP_EXCEPTION;

    /*
    ///////////////////////////////////////////////////////////
    // Higher-level test methods
    ///////////////////////////////////////////////////////////
     */

    /**
     * Method that will iterate through contents of an XML document
     * using specified stream reader; will also access some of data
     * to make sure reader reads most of lazy-loadable data.
     * Method is usually called to try to get an exception for invalid
     * content.
     *
     * @return Dummy value calculated on contents; used to make sure
     *   no dead code is eliminated
     */
    protected int streamThrough(XMLStreamReader sr)
        throws XMLStreamException
    {
        int result = 0;

        assertNotNull(sr);
        try {
            while (sr.hasNext()) {
                int type = sr.next();
                result += type;
                if (sr.hasText()) {
                    /* will also do basic verification for text content, to 
                     * see that all text accessor methods return same content
                     */
                    result += getAndVerifyText(sr).hashCode();
                }
                if (sr.hasName()) {
                    QName n = sr.getName();
                    assertNotNull(n);
                    result += n.hashCode();
                }
            }
        } catch (RuntimeException rex) {
            // Let's try to find a nested XMLStreamException, if possible
            Throwable t = rex;
            while (t != null) {
                t = t.getCause();
                if (t instanceof XMLStreamException) {
                    throw (XMLStreamException) t;
                }
            }
            // Nope, just a runtime exception
            throw rex;
        }
            
        return result;
    }

    protected int streamThroughFailing(XMLInputFactory f, String contents,
                                       String msg)
    {
        int result = 0;
        try {
            XMLStreamReader sr = constructStreamReader(f, contents);
            result = streamThrough(sr);
        } catch (XMLStreamException ex) { // good
            if (PRINT_EXP_EXCEPTION) {
                System.out.println("Expected failure: '"+ex.getMessage()+"' "
                                   +"(matching message: '"+msg+"')");
            }
            return 0;
        } catch (Exception ex2) { // may still be ok:
            if (ex2.getCause() instanceof XMLStreamException) {
                if (PRINT_EXP_EXCEPTION) {
                    System.out.println("Expected failure: '"+ex2.getMessage()+"' "
                                       +"(matching message: '"+msg+"')");
                }
                return 0;
            }
            fail("Expected an XMLStreamException (either direct, or getCause() of a primary exception) for "+msg
                 +", got: "+ex2);
        }

        fail("Expected an exception for "+msg);
        return result; // never gets here
    }

    protected int streamThroughFailing(XMLStreamReader sr, String msg)
    {
        int result = 0;
        try {
            result = streamThrough(sr);
        } catch (XMLStreamException ex) { // good
            if (PRINT_EXP_EXCEPTION) {
                System.out.println("Expected failure: '"+ex.getMessage()+"' "
                                   +"(matching message: '"+msg+"')");
            }
            return 0;
        } catch (Exception ex2) { // ok; iff links to XMLStreamException
            Throwable t = ex2;
            while (t.getCause() != null && !(t instanceof XMLStreamException)) {
                t = t.getCause();
            }
            if (t instanceof XMLStreamException) {
                if (PRINT_EXP_EXCEPTION) {
                    System.out.println("Expected failure: '"+ex2.getMessage()+"' "
                                       +"(matching message: '"+msg+"')");
                }
                return 0;
            }
            if (t == ex2) {
                fail("Expected an XMLStreamException (either direct, or getCause() of a primary exception) for "+msg
                     +", got: "+ex2);
            }
                fail("Expected an XMLStreamException (either direct, or getCause() of a primary exception) for "+msg
                 +", got: "+ex2+" (root: "+t+")");
        }

        fail("Expected an exception for "+msg);
        return result; // never gets here
    }
}
