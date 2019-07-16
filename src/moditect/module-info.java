// hand-crafted on 14-Jul-2019 -- probably all wrong
// NOTE: module name differs from Maven (group) id due to Woodstox
// having sort of legacy Java package, but more modern Maven group id.
// Having to choose one over the other is partly due to discussions like:
//  https://blog.joda.org/2017/04/java-se-9-jpms-modules-are-not-artifacts.html
// and partly since Automatic Module Name already used Java root package

module com.ctc.wstx {
    requires transitive java.xml; // for SAX, JAXP, DOM
    requires transitive org.codehaus.stax2;

    // Need to export most Java packages, but may eventually want to close some
    exports com.ctc.wstx.api;
    exports com.ctc.wstx.cfg;
    exports com.ctc.wstx.dom;
    exports com.ctc.wstx.dtd;
    exports com.ctc.wstx.ent;
    exports com.ctc.wstx.exc;
    exports com.ctc.wstx.io; // should this be exported?
    exports com.ctc.wstx.msv;
    exports com.ctc.wstx.sax;
    exports com.ctc.wstx.sr;
    exports com.ctc.wstx.stax;
    exports com.ctc.wstx.sw;
    exports com.ctc.wstx.util;

    provides javax.xml.stream.XMLEventFactory with com.ctc.wstx.stax.WstxEventFactory;
    provides javax.xml.stream.XMLInputFactory with com.ctc.wstx.stax.WstxInputFactory;
    provides javax.xml.stream.XMLOutputFactory with com.ctc.wstx.stax.WstxOutputFactory;
}
