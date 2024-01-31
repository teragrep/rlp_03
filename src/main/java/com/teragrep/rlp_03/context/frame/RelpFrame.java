package com.teragrep.rlp_03.context.frame;

import com.teragrep.rlp_03.context.frame.fragment.Fragment;

public interface RelpFrame {
    Fragment txn();

    Fragment command();

    Fragment payloadLength();

    Fragment payload();

    Fragment endOfTransfer();

    boolean isStub();
}
