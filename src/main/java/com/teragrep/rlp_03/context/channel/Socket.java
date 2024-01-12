package com.teragrep.rlp_03.context.channel;

import com.teragrep.rlp_03.TransportInfo;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface Socket {
    int read(ByteBuffer dst) throws IOException;
    int write(ByteBuffer dst) throws IOException;
    TransportInfo getTransportInfo();
    void close() throws IOException;
}
