package com.ctc.wstx.io;

import com.ctc.wstx.util.XmlChars;
import junit.framework.TestCase;
import org.junit.Test;

import java.util.stream.IntStream;

public class WstxInputDataTest extends TestCase {

    @Test
    public void testIsNameStartCharBehavesSameAsBranchyVersion() {
        WstxInputData wstxInputDataXml10 = new WstxInputData();
        WstxInputData wstxInputDataXml11 = new WstxInputData();
        wstxInputDataXml11.mXml11 = true;

        // include all 7-bit ASCII characters plus some left and right
        IntStream.range(-10, 138).forEach(i -> {
            char c = (char) i;
            assertEquals(isNameStartCharBranchy(c, false), wstxInputDataXml10.isNameStartChar(c));
            assertEquals(isNameStartCharBranchy(c, true), wstxInputDataXml11.isNameStartChar(c));
        });
    }

    // previous implementation with branches
    private final boolean isNameStartCharBranchy(char c, boolean mXml11) {
        /* First, let's handle 7-bit ascii range (identical between xml
         * 1.0 and 1.1)
         */
        if (c <= 0x7A) { // 'z' or earlier
            if (c >= 0x61) { // 'a' - 'z' are ok
                return true;
            }
            if (c < 0x41) { // before 'A' just white space
                return false;
            }
            return (c <= 0x5A) || (c == '_'); // 'A' - 'Z' and '_' are ok
        }
        /* Ok, otherwise need to use a big honking bit sets... which
         * differ between 1.0 and 1.1
         */
        return mXml11 ? XmlChars.is11NameStartChar(c) : XmlChars.is10NameStartChar(c);
    }

    @Test
    public void testIsNameCharBehavesSameAsBranchyVersion() {
        WstxInputData wstxInputDataXml10 = new WstxInputData();
        WstxInputData wstxInputDataXml11 = new WstxInputData();
        wstxInputDataXml11.mXml11 = true;

        // include all 7-bit ASCII characters plus some left and right
        IntStream.range(-10, 138).forEach(i -> {
            char c = (char) i;
            assertEquals(isNameCharBranchy(c, false), wstxInputDataXml10.isNameChar(c));
            assertEquals(isNameCharBranchy(c, true), wstxInputDataXml11.isNameChar(c));
        });
    }

    // previous implementation with branches
    private final boolean isNameCharBranchy(char c, boolean mXml11) {
        // First, let's handle 7-bit ascii range
        if (c <= 0x7A) { // 'z' or earlier
            if (c >= 0x61) { // 'a' - 'z' are ok
                return true;
            }
            if (c <= 0x5A) {
                if (c >= 0x41) { // 'A' - 'Z' ok too
                    return true;
                }
                // As are 0-9, '.' and '-'
                return (c >= 0x30 && c <= 0x39) || (c == '.') || (c == '-');
            }
            return (c == 0x5F); // '_' is ok too
        }
        return mXml11 ? XmlChars.is11NameChar(c) : XmlChars.is10NameChar(c);
    }
}