package com.teragrep.rlp_03.context.channel;

import com.teragrep.rlp_03.EncryptionInfoStub;
import com.teragrep.rlp_03.TransportInfo;
import com.teragrep.rlp_03.TransportInfoFake;

import java.io.IOException;
import java.nio.ByteBuffer;

public class SocketFake implements Socket {

    private final TransportInfo transportInfo;

    public SocketFake() {
        this.transportInfo = new TransportInfoFake();
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        return 0;
    }

    @Override
    public int write(ByteBuffer dst) throws IOException {
        return 0;
    }

    @Override
    public TransportInfo getTransportInfo() {
        return transportInfo;
    }

    @Override
    public void close() throws IOException {

    }
}
