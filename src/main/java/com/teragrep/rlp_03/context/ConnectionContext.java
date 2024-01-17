package com.teragrep.rlp_03.context;

import com.teragrep.rlp_03.context.channel.Socket;

import java.nio.channels.SelectionKey;

public interface ConnectionContext {
    void updateInterestOps(InterestOps interestOps);

    void close();

    void handleEvent(SelectionKey selectionKey);

    InterestOps interestOps();

    Socket socket();

    RelpWrite relpWrite();
}
