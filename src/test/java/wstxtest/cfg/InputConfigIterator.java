package wstxtest.cfg;

import java.util.*;

import javax.xml.stream.XMLInputFactory;

import wstxtest.cfg.InputTestConfig;

/**
 * Class that implements iteration over set of input configuration
 * Objects, so that the input factory gets configured to all test
 * values for each configuration, and a test method is called once
 * per each configuration setting combination
 */
public class InputConfigIterator
{
    final ArrayList<InputTestConfig> mConfigs = new ArrayList<InputTestConfig>();

    /*
    /////////////////////////////////////////////////
    // Life-cycle (constructor, configuration)
    /////////////////////////////////////////////////
     */

    /**
     * Index of the iteration step; may be used for debugging
     */
    int mIndex;

    public InputConfigIterator() {
    }

    public InputConfigIterator addConfig(InputTestConfig cfg) {
        mConfigs.add(cfg);
        return this;
    }

    /*
    /////////////////////////////////////////////////
    // Public API
    /////////////////////////////////////////////////
     */

    public void iterate(XMLInputFactory f, InputTestMethod callback)
        throws Exception
    {
        mIndex = 0;

        // First need to initialize the factory with first settings:
        final int len = mConfigs.size();
        for (int i = 0; i < len; ++i) {
            mConfigs.get(i).nextConfig(f);
        }

        // And then the main iteration
        while (true) {
            // First let's call the test method
            callback.runTest(f, this);

            // And then iterate to next configuration setting combo:
            int i = 0;
            for (; i < len; ++i) {
                InputTestConfig cfg = mConfigs.get(i);
                // Still more settings for this config? Then let's break:
                if (cfg.nextConfig(f)) {
                    break;
                }
                // Nope, need to reset this one, and continue for next:
                cfg.firstConfig(f);
            }

            // Got them all done?
            if (i == len) {
                break;
            }
            ++mIndex;
        }
    }

    public int getIndex() {
        return mIndex;
    }

    /*
    /////////////////////////////////////////////////
    // Overridden standard methods:
    /////////////////////////////////////////////////
     */

    public String toString()
    {
        int len = mConfigs.size();
        StringBuffer sb = new StringBuffer(16 + (len << 4));
        sb.append('(');
        sb.append(len);
        sb.append(") ");

        for (int i = 0; i < len; ++i) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append((mConfigs.get(i)).getDesc());
        }
        return sb.toString();
    }
}

