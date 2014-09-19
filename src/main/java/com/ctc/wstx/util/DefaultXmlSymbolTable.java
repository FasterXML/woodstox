package com.ctc.wstx.util;

import com.ctc.wstx.util.SymbolTable;

/**
 * Factory class used for instantiating pre-populated XML symbol
 * tables. Such tables already have basic String constants that
 * XML standard defines.
 */
public final class DefaultXmlSymbolTable
{
    /**
     * Root symbol table from which child instances are derived.
     */
    final static SymbolTable sInstance;

    final static String mNsPrefixXml;
    final static String mNsPrefixXmlns;

    /* Although theoretically there'd be no strict need to pre-populate
     * the default table, if all access was done using suggested usage
     * patterns (reuse input factories consistently, esp. for same types
     * of documents), it is possible some developers just use each factory
     * just once. As such, it does matter how tables are pre-populated.
     * Thus, let's use limited sensible set of predefined prefixes and
     * names.
     */
    static {
        /* 128 means it's ok without resize up to ~96 symbols; true that
         * default symbols added will be interned.
         */
        sInstance = new SymbolTable(true, 128);

        // Let's add default namespace binding prefixes
        mNsPrefixXml = sInstance.findSymbol("xml");
        mNsPrefixXmlns = sInstance.findSymbol("xmlns");

        /* No need to add keywords, as they are checked directly by
         * Reader, without constructing Strings.
         */

        // Ok, any common prefixes?

        // or local names (element, attribute)?
        sInstance.findSymbol("id");
        sInstance.findSymbol("name");

        // XML Schema?
        // prefixes:
        sInstance.findSymbol("xsd");
        sInstance.findSymbol("xsi");
        // local names:
        sInstance.findSymbol("type");

        // How about some common prefixes and names for Soap?
        // commonly used prefixes:
        sInstance.findSymbol("soap");
        sInstance.findSymbol("SOAP-ENC");
        sInstance.findSymbol("SOAP-ENV");
        // local names:
        sInstance.findSymbol("Body");
        sInstance.findSymbol("Envelope");
    }

    /*
    ///////////////////////////////////////////////////
    // Public API, factory method(s):
    ///////////////////////////////////////////////////
     */

    /**
     * Method that will return an instance of SymbolTable that has basic
     * XML 1.0 constants pre-populated.
     */
    public static SymbolTable getInstance() {
        return sInstance.makeChild();
    }

    /*
    ///////////////////////////////////////////////////
    // Public API, efficient access to (shared)
    // constants values:
    ///////////////////////////////////////////////////
     */

    public static String getXmlSymbol() {
        return mNsPrefixXml;
    }

    public static String getXmlnsSymbol() {
        return mNsPrefixXmlns;
    }
}
