package com.teragrep.rlp_03.context;

import com.teragrep.rlp_03.context.channel.Socket;

import java.nio.channels.SelectionKey;

public class ConnectionContextStub implements ConnectionContext {
    @Override
    public void close() {
        throw new IllegalArgumentException("ConnectionContextStub does not implement this");
    }

    @Override
    public void handleEvent(SelectionKey selectionKey) {
        throw new IllegalArgumentException("ConnectionContextStub does not implement this");
    }

    @Override
    public InterestOps interestOps() {
        throw new IllegalArgumentException("ConnectionContextStub does not implement this");
    }

    @Override
    public Socket socket() {
        throw new IllegalArgumentException("ConnectionContextStub does not implement this");
    }

    @Override
    public RelpWrite relpWrite() {
        throw new IllegalArgumentException("ConnectionContextStub does not implement this");
    }
}
