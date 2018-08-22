package com.ctc.wstx.api;

import java.util.*;

import org.codehaus.stax2.XMLStreamProperties;

import com.ctc.wstx.util.ArgUtil;

/**
 * Shared common base class for variour configuration container implementations
 * for public factories Woodstox uses: implementations of
 * {@link javax.xml.stream.XMLInputFactory},
 * {@link javax.xml.stream.XMLOutputFactory} and
 * {@link org.codehaus.stax2.validation.XMLValidationSchemaFactory}.
 * Implements basic settings for some shared settings, defined by the
 * shared property interface {@link XMLStreamProperties}.
 */
abstract class CommonConfig
    implements XMLStreamProperties
{
    /*
    ///////////////////////////////////////////////////////////////////////
    // Implementation info
    ///////////////////////////////////////////////////////////////////////
     */

    protected final static String IMPL_NAME = "woodstox";
    
    /* !!! TBI: get from props file or so? Or build as part of Ant
     *    build process?
     */
    /**
     * This is "major.minor" version used for purposes of determining
     * the feature set. Patch level is not included, since those should
     * not affect API or feature set. Using applications should be
     * prepared to take additional levels, however, just not depend
     * on those being available.
     */
    protected final static String IMPL_VERSION = "5.0";

    /*
    ///////////////////////////////////////////////////////////////////////
    // Internal constants
    ///////////////////////////////////////////////////////////////////////
     */

    final static int CPROP_IMPL_NAME = 1;
    final static int CPROP_IMPL_VERSION = 2;

    final static int CPROP_SUPPORTS_XML11 = 3;
    final static int CPROP_SUPPORT_XMLID = 4;
    
    final static int CPROP_RETURN_NULL_FOR_DEFAULT_NAMESPACE = 5; 

    /**
     * Map to use for converting from String property ids to enumeration
     * (ints). Used for faster dispatching.
     */
    final static HashMap<String,Integer> sStdProperties = new HashMap<String,Integer>(16);
    static {
        // Basic information about the implementation:
        sStdProperties.put(XMLStreamProperties.XSP_IMPLEMENTATION_NAME, CPROP_IMPL_NAME);
        sStdProperties.put(XMLStreamProperties.XSP_IMPLEMENTATION_VERSION, CPROP_IMPL_VERSION);

        // XML version support:
        sStdProperties.put(XMLStreamProperties.XSP_SUPPORTS_XML11, CPROP_SUPPORTS_XML11);

        // Xml:id support:
        sStdProperties.put(XMLStreamProperties.XSP_SUPPORT_XMLID, CPROP_SUPPORT_XMLID);

        sStdProperties.put(WstxInputProperties.P_RETURN_NULL_FOR_DEFAULT_NAMESPACE,
                CPROP_RETURN_NULL_FOR_DEFAULT_NAMESPACE);

        /* 23-Apr-2008, tatus: Additional interoperability property,
         *    one that Sun implementation uses. Can map to Stax2
         *    property quite easily.
         */
        sStdProperties.put("http://java.sun.com/xml/stream/properties/implementation-name",
                CPROP_IMPL_NAME);
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Shared config
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * As per [WSTX-277], can specify whether prefix for the
     * "default namespace" is return as null (true) or empty String (false)
     */
    protected boolean mReturnNullForDefaultNamespace;
    
    /*
    ///////////////////////////////////////////////////////////////////////
    // Construction
    ///////////////////////////////////////////////////////////////////////
     */
    
    /**
     * Constructor used by sub-classes
     *
     * @param base Base instance to copy settings from, if any; null for
     *   'root' configuration objects.
     */
    protected CommonConfig(CommonConfig base) {
        mReturnNullForDefaultNamespace = (base == null)
                /* 27-Mar-2018, tatu: What the hell... why does it take it from System properties?
                 *   I should have done better code review; Woodstox should not do that.
                 *   System properties are evil for shared libraries, not to be used.
                 */
                ? Boolean.getBoolean(WstxInputProperties.P_RETURN_NULL_FOR_DEFAULT_NAMESPACE)
                : base.mReturnNullForDefaultNamespace;
    }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Public API, generic StAX config methods
    ///////////////////////////////////////////////////////////////////////
     */

    public Object getProperty(String propName)
    {
        /* Related to [WSTX-243]; would be nice to not to have to throw an
         * exception; but Stax spec suggests that we do need to indicate
         * unrecognized property by exception.
         */
        int id = findPropertyId(propName);
        if (id >= 0) {
            return getProperty(id);
        }
        id = findStdPropertyId(propName);
        if (id < 0) {
            reportUnknownProperty(propName);
            return null;
        }
        return getStdProperty(id);
    }

    public boolean isPropertySupported(String propName)
    {
        return (findPropertyId(propName) >= 0)
            || (findStdPropertyId(propName) >= 0);
    }

    /**
     * @return True, if the specified property was <b>successfully</b>
     *    set to specified value; false if its value was not changed
     */
    public boolean setProperty(String propName, Object value)
    {
        int id = findPropertyId(propName);
        if (id >= 0) {
            return setProperty(propName, id, value);
        }
        id = findStdPropertyId(propName);
        if (id < 0) {
            reportUnknownProperty(propName);
            return false;
        }
        return setStdProperty(propName, id, value);
    }

    protected void reportUnknownProperty(String propName)
    {
        // see [WSTX-243] for discussion on whether to throw...
        throw new IllegalArgumentException("Unrecognized property '"+propName+"'");
    }
    
    /*
    ///////////////////////////////////////////////////////////////////////
    // Additional methods used by Woodstox core
    ///////////////////////////////////////////////////////////////////////
     */

    public final Object safeGetProperty(String propName)
    {
        int id = findPropertyId(propName);
        if (id >= 0) {
            return getProperty(id);
        }
        id = findStdPropertyId(propName);
        if (id < 0) {
            return null;
        }
        return getStdProperty(id);
    }

    /**
     * Method used to figure out the official implementation name
     * for input/output/validation factories.
     */
    public static String getImplName() { return IMPL_NAME; }

    /**
     * Method used to figure out the official implementation version
     * for input/output/validation factories.
     */
    public static String getImplVersion() { return IMPL_VERSION; }

    /*
    ///////////////////////////////////////////////////////////////////////
    // Interface sub-classes have to implement / can override
    ///////////////////////////////////////////////////////////////////////
     */

    /**
     * @return Internal enumerated int matching the String name
     *   of the property, if one found: -1 to indicate no match
     *   was found.
     */
    protected abstract int findPropertyId(String propName);

    public boolean doesSupportXml11() {
        /* Woodstox does support xml 1.1 ... but sub-classes can
         * override it if/as necessary (validator factories might not
         * support it?)
         */
        return true;
    }

    public boolean doesSupportXmlId() {
        /* Woodstox does support Xml:id ... but sub-classes can
         * override it if/as necessary.
         */
        return true;
    }

    public boolean returnNullForDefaultNamespace() {
        return mReturnNullForDefaultNamespace;
    }
    
    protected abstract Object getProperty(int id);

    protected abstract boolean setProperty(String propName, int id, Object value);

    /*
    ///////////////////////////////////////////////////////////////////////
    // Internal methods
    ///////////////////////////////////////////////////////////////////////
     */

    protected int findStdPropertyId(String propName)
    {
        Integer I = sStdProperties.get(propName);
        return (I == null) ? -1 : I.intValue();
    }

    /**
     * @param propName Name of standard property to set
     * @param id Internal id matching the name
     * @param value Value to set the standard property to
     */
    protected boolean setStdProperty(String propName, int id, Object value)
    {
        // Only one settable property...
        switch (id) {
        case CPROP_RETURN_NULL_FOR_DEFAULT_NAMESPACE:
            mReturnNullForDefaultNamespace = ArgUtil.convertToBoolean(propName, value);
            return true;
        }
        return false;
    }

    protected Object getStdProperty(int id)
    {
        switch (id) {
        case CPROP_IMPL_NAME:
            return IMPL_NAME;
        case CPROP_IMPL_VERSION:
            return IMPL_VERSION;
        case CPROP_SUPPORTS_XML11:
            return doesSupportXml11() ? Boolean.TRUE : Boolean.FALSE;
        case CPROP_SUPPORT_XMLID:
            return doesSupportXmlId() ? Boolean.TRUE : Boolean.FALSE;
        case CPROP_RETURN_NULL_FOR_DEFAULT_NAMESPACE:
            return returnNullForDefaultNamespace() ? Boolean.TRUE : Boolean.FALSE;
        default: // sanity check, should never happen
            throw new IllegalStateException("Internal error: no handler for property with internal id "+id+".");
        }
    }
}
