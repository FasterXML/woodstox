package org.codehaus.stax.test;

import javax.xml.stream.XMLResolver;

/**
 * This is a simple and stupid resolver, that does not check what is
 * being resolved; and thus it should only be used if only one thing
 * (a single external entity; a single external subset) is to 
 * be expanded (although that single entity can be repeated multiple
 * times).
 */
public class SimpleResolver
    implements XMLResolver
{
    final String ENC = "UTF-8";
    final byte[] mData;
    
    public SimpleResolver(String content)
    {
        try {
            mData = content.getBytes(ENC);
        } catch (java.io.IOException ioe) {
            throw new Error(ioe.toString());
        }
    }

    @Override
    public Object resolveEntity(String publicID, String systemID, String baseURI, String namespace)
    {
        return new java.io.ByteArrayInputStream(mData);
    }
}
