import java.io.StringReader;
import javax.xml.stream.*;
import com.ctc.wstx.stax.WstxInputFactory;

public class ParseSanity {
    static String buildDoc(String profile) {
        StringBuilder body = new StringBuilder();
        if ("entity".equals(profile)) {
            String chunk = "some text with an &amp; entity and more words here ";
            while (body.length() < 2_000_000) body.append(chunk);
        } else {
            String chunk = "Lorem ipsum dolor sit amet consectetur adipiscing elit sed do\n";
            while (body.length() < 2_000_000) body.append(chunk);
        }
        return "<root><big>" + body + "</big></root>";
    }
    static long parse(XMLInputFactory f, String xml) throws Exception {
        XMLStreamReader r = f.createXMLStreamReader(new StringReader(xml));
        long chars = 0; int events = 0;
        while (r.hasNext()) { int t = r.next(); events++;
            if (t==XMLStreamConstants.CHARACTERS||t==XMLStreamConstants.CDATA) chars += r.getTextLength(); }
        r.close();
        return (((long)events) << 40) | chars; // pack
    }
    public static void main(String[] a) throws Exception {
        for (String p : new String[]{"prose","entity"}) {
            String xml = buildDoc(p);
            XMLInputFactory f = new WstxInputFactory();
            f.setProperty(XMLInputFactory.IS_COALESCING, Boolean.FALSE);
            long packed = parse(f, xml); // warm-ish
            long chars = packed & ((1L<<40)-1); long events = packed >>> 40;
            long t0 = System.nanoTime();
            int N = 50;
            long acc = 0;
            for (int i=0;i<N;i++) acc += (parse(f, xml) & ((1L<<40)-1));
            long t1 = System.nanoTime();
            double msPer = (t1-t0)/1e6/N;
            System.out.printf("profile=%-6s xmlLen=%d events=%d charsCounted=%d  ->  %.3f ms/parse, %.1f MB/s%n",
                p, xml.length(), events, chars, msPer, (xml.length()*2/1e6)/(msPer/1000));
        }
    }
}
