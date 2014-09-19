package com.ctc.wstx.api;

public final class ValidatorConfig
    extends CommonConfig
{
    /**
     * For now, since there are no mutable properties, we can share
     * a singleton instance.
     */
    final static ValidatorConfig sInstance = new ValidatorConfig(null);

    private ValidatorConfig(ValidatorConfig base) {
        super(base);
    }

    public static ValidatorConfig createDefaults()
    {
        /* For now, since there are no mutable properties, we can share
         * a singleton instance.
         */
        return sInstance;
    }

    protected int findPropertyId(String propName) {
        // Nothing above and beyond default settings...
        return -1;
    }

    protected Object getProperty(int id) {
        // nothing to get:
        return null;
    }

    protected boolean setProperty(String propName, int id, Object value) {
        // nothing to set:
        return false;
    }
}

