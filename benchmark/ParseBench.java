package com.ctc.wstx.perf;

import java.io.StringReader;
import java.util.concurrent.TimeUnit;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.ctc.wstx.stax.WstxInputFactory;

import org.openjdk.jmh.annotations.*;

/**
 * End-to-end parse throughput of large, text-heavy XML documents, used to verify
 * the {@code System.arraycopy} bulk-copy optimization in
 * {@code BasicStreamReader.readTextSecondary}. Pure Java 8 -- no Vector API.
 *
 * <p>Which {@code BasicStreamReader} is exercised is decided by the classpath.
 * Run once per build and compare the reported ops/s:
 *
 * <pre>
 *   baseline  : -cp benchOut;target/classes;stax2;jmh...
 *   arraycopy : -cp benchOut;shadow;target/classes;stax2;jmh...   (shadow wins)
 * </pre>
 *
 * <p>Profiles:
 * <ul>
 *   <li>{@code entity}  - large text with {@code &amp;} entities -> the COPYING
 *       path ({@code readTextSecondary}); this is where arraycopy helps.</li>
 *   <li>{@code prose}   - large plain wrapped text -> the buffer-SHARING path
 *       ({@code readTextPrimary}, untouched) -> expected neutral.</li>
 *   <li>{@code records} - many small elements, 2-6 char content -> expected
 *       neutral.</li>
 * </ul>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)   // documents parsed per second
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 3, jvmArgs = {"-Xms1g", "-Xmx1g", "-XX:+UseParallelGC",
        "-Duser.language=en", "-Duser.country=US"})
public class ParseBench {

    @Param({"prose", "entity", "records"})
    public String profile;

    String xml;
    XMLInputFactory factory;

    @Setup
    public void setup() {
        xml = buildDoc(profile);
        factory = new WstxInputFactory();
        factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.FALSE);
    }

    static String buildDoc(String profile) {
        StringBuilder body = new StringBuilder();
        if ("records".equals(profile)) {
            // "Normal" data XML: many small elements, each with short (2-6 char)
            // text content.
            String chunk = "<row><a>12</a><b>xyz</b><c>hello</c><d>42</d></row>\n";
            while (body.length() < 500_000) body.append(chunk);
            return "<root>" + body + "</root>";
        }
        if ("entity".equals(profile)) {
            String chunk = "some text with an &amp; entity and more words here ";
            while (body.length() < 500_000) body.append(chunk);
        } else { // prose: wrapped plain text
            String chunk = "Lorem ipsum dolor sit amet consectetur adipiscing elit sed do\n";
            while (body.length() < 500_000) body.append(chunk);
        }
        return "<root><big>" + body + "</big></root>";
    }

    @Benchmark
    public long parse() throws XMLStreamException {
        XMLStreamReader r = factory.createXMLStreamReader(new StringReader(xml));
        long chars = 0;
        while (r.hasNext()) {
            int t = r.next();
            if (t == XMLStreamConstants.CHARACTERS || t == XMLStreamConstants.CDATA) {
                chars += r.getTextLength();
            }
        }
        r.close();
        return chars;
    }
}
