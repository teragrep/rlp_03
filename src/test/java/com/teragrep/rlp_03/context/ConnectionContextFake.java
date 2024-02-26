package com.teragrep.rlp_03.context;

import com.teragrep.rlp_03.context.channel.Socket;

import java.nio.channels.SelectionKey;

public class ConnectionContextFake implements ConnectionContext {

    private final InterestOps interestOps;
    private final Socket socket;

    private final RelpWrite relpWrite;
    ConnectionContextFake(InterestOps interestOps, Socket socket, RelpWrite relpWrite) {
        this.interestOps = interestOps;
        this.socket = socket;
        this.relpWrite = relpWrite;
    }

    @Override
    public void close() {
        // no-op
    }

    @Override
    public void handleEvent(SelectionKey selectionKey) {
        // no-op
    }

    @Override
    public InterestOps interestOps() {
        return interestOps;
    }

    @Override
    public Socket socket() {
        return socket;
    }

    @Override
    public RelpWrite relpWrite() {
        return relpWrite;
    }
}
