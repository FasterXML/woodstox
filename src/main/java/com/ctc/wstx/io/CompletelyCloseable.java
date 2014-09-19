package com.ctc.wstx.io;

import java.io.IOException;

public interface CompletelyCloseable
{
    public void closeCompletely() throws IOException;
}
