package com.teragrep.rlp_03.context;

import com.teragrep.rlp_01.RelpFrameTX;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public interface RelpWrite extends Consumer<List<RelpFrameTX>>, Runnable {
    // this must be thread-safe!
    @Override
    void accept(List<RelpFrameTX> relpFrameTXList);

    @Override
    void run();

    AtomicBoolean needRead();
}
