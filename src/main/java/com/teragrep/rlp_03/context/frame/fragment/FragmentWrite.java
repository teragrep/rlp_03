package com.teragrep.rlp_03.context.frame.fragment;

import java.io.IOException;
import java.nio.channels.GatheringByteChannel;

public interface FragmentWrite {
    long write(GatheringByteChannel gbc) throws IOException;

    boolean hasRemaining();

    long length();
}
