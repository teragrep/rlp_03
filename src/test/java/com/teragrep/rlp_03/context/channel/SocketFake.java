package com.teragrep.rlp_03.context.channel;

import com.teragrep.rlp_03.TransportInfo;
import com.teragrep.rlp_03.TransportInfoFake;

import java.nio.ByteBuffer;

public class SocketFake implements Socket {

    private final TransportInfo transportInfo;

    public SocketFake() {
        this.transportInfo = new TransportInfoFake();
    }

    @Override
    public long read(ByteBuffer[] dsts) {
        return 0;
    }

    @Override
    public long write(ByteBuffer[] dsts) {
        return 0;
    }

    @Override
    public TransportInfo getTransportInfo() {
        return transportInfo;
    }

    @Override
    public void close() {

    }
}
