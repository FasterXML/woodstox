package wstxtest.stream;

import java.util.Random;
import javax.xml.stream.*;

import wstxtest.cfg.*;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.stax.WstxInputFactory;

/**
 * Unit test suite that ensures that independent of combinations of settings
 * such as namespace-awareness, coalescing, automatic entity replacement,
 * parsing results remain the same when they should.
 */
public class TestRandomStream
    extends BaseStreamTest
    implements InputTestMethod
{
    InputConfigIterator mConfigs;

    public TestRandomStream() {
        super();
        mConfigs = new InputConfigIterator();
        mConfigs.addConfig(Configs.getLazyParsingConfig())
            .addConfig(Configs.getInputBufferSizeConfig())
            .addConfig(Configs.getMinTextSegmentConfig())
            ;
    }

    public void testCoalescingAutoEntity()
        throws Exception
    {
        mReallyStreaming = false;
        doTest(false, true, true); // non-ns
        doTest(true, true, true); // ns-aware
    }

    public void testCoalescingAutoEntityStreaming()
        throws Exception
    {
        mReallyStreaming = true;
        doTest(true, true, true); // ns-aware
    }

    public void testNonCoalescingAutoEntity()
        throws Exception
    {
        mReallyStreaming = false;
        doTest(false, false, true); // non-ns
        doTest(true, false, true); // ns-aware
    }

    public void testNonCoalescingAutoEntityStreaming()
        throws Exception
    {
        mReallyStreaming = true;
        doTest(true, false, true); // ns-aware
    }

    public void testCoalescingNonAutoEntity()
        throws Exception
    {
        mReallyStreaming = false;
        doTest(false, true, false); // non-ns
        doTest(true, true, false); // ns-aware
    }

    public void testCoalescingNonAutoEntityStreaming()
        throws Exception
    {
        mReallyStreaming = true;
        doTest(true, true, false); // ns-aware
    }

    public void testNonCoalescingNonAutoEntity()
        throws Exception
    {
        mReallyStreaming = false;
        doTest(false, false, false); // non-ns
        doTest(true, false, false); // ns-aware
    }

    public void testNonCoalescingNonAutoEntityStreaming()
        throws Exception
    {
        mReallyStreaming = true;
        doTest(true, false, false); // ns-aware
    }

    /*
    ////////////////////////////////////////
    // Private methods, common test code
    ////////////////////////////////////////
     */

    String mInput;
    String mExpOutputNorm;

    boolean mReallyStreaming = false;
    boolean mNormalizeLFs = true;

    /**
     * Main branching point has settings for standard features; it
     * will further need to loop over Woodstox-specific settings.
     */
    private void doTest(boolean ns, boolean coalescing, boolean autoEntity)
        throws Exception
    {
        /* Let's generate seed from args so it's reproducible; String hash
         * code only depend on text it contains, so it'll be fixed for
         * specific String.
         */
        String baseArgStr = "ns: "+ns+", coalesce: "+coalescing+", entityExp: "+autoEntity;
        long seed = baseArgStr.hashCode();

        WstxInputFactory f = (WstxInputFactory) getInputFactory();
        ReaderConfig cfg = f.getConfig();

        // Settings we always need:
        cfg.doSupportDTDs(true);
        cfg.doValidateWithDTD(false);

        // Then variable ones we got settings for:
        cfg.doSupportNamespaces(ns);
        cfg.doCoalesceText(coalescing);
        cfg.doReplaceEntityRefs(autoEntity);

        /* How many random permutations do we want to try?
         */
        final int ROUNDS = 5;

        for (int round = 0; round < ROUNDS; ++round) {
            Random r = new Random(seed+round);
            StringBuffer inputBuf = new StringBuffer(1000);
            StringBuffer expOutputBuf = new StringBuffer(1000);

            generateData(r, inputBuf, expOutputBuf, autoEntity);

            mInput = inputBuf.toString();
            normalizeLFs(expOutputBuf);
            mExpOutputNorm = expOutputBuf.toString();
            mConfigs.iterate(f, this);
        }
    }

    /**
     * Method called via input config iterator, with all possible
     * configurations
     */
    @Override
    public void runTest(XMLInputFactory f, InputConfigIterator it)
        throws Exception
    {
        String exp = mExpOutputNorm;

        // First, let's skip through it all
        streamAndSkip(f, it, mInput);

        // and then the 'real' test:
        streamAndCheck(f, it, mInput, exp, mReallyStreaming);
    }
}
