package com.ctc.wstx.cfg;

public interface ParsingErrorMsgs
{
    // // // EOF problems:

    final static String SUFFIX_IN_ATTR_VALUE = " in attribute value";
    final static String SUFFIX_IN_DEF_ATTR_VALUE = " in attribute default value";
    final static String SUFFIX_IN_CDATA = " in CDATA section";
    final static String SUFFIX_IN_CLOSE_ELEMENT = " in end tag";
    final static String SUFFIX_IN_COMMENT = " in comment";
    final static String SUFFIX_IN_DTD = " in DOCTYPE declaration";
    final static String SUFFIX_IN_DTD_EXTERNAL = " in external DTD subset";
    final static String SUFFIX_IN_DTD_INTERNAL = " in internal DTD subset";
    final static String SUFFIX_IN_DOC = " in main document content";
    final static String SUFFIX_IN_ELEMENT = " in start tag";
    final static String SUFFIX_IN_ENTITY_REF = " in entity reference";
    final static String SUFFIX_IN_EPILOG = " in epilog";
    final static String SUFFIX_IN_NAME = " in name token";
    final static String SUFFIX_IN_PROC_INSTR = " in processing instruction";
    final static String SUFFIX_IN_PROLOG = " in prolog";
    final static String SUFFIX_IN_TEXT = " in document text content";
    final static String SUFFIX_IN_XML_DECL = " in xml declaration";

    final static String SUFFIX_EOF_EXP_NAME = "; expected an identifier";
}
