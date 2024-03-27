package com.teragrep.rlp_03.context;

import java.io.Closeable;
import java.nio.channels.SelectionKey;

public interface Context extends Closeable {
    void handleEvent(SelectionKey selectionKey);

    @Override
    void close(); // no exception is thrown
}
