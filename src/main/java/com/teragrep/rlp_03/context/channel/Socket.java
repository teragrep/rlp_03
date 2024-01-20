package com.teragrep.rlp_03.context.channel;

import com.teragrep.rlp_03.TransportInfo;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface Socket {
    long read(ByteBuffer[] dsts) throws IOException;
    long write(ByteBuffer[] dsts) throws IOException;
    TransportInfo getTransportInfo();
    void close() throws IOException;
}
