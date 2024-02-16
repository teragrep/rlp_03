package com.teragrep.rlp_03.context;

import java.util.concurrent.atomic.AtomicBoolean;

public interface RelpRead extends Runnable {
    @Override
    void run();

    AtomicBoolean needWrite();
}
