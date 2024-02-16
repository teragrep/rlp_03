package com.teragrep.rlp_03.context.channel;

import com.teragrep.rlp_03.EncryptionInfo;
import com.teragrep.rlp_03.EncryptionInfoStub;
import com.teragrep.rlp_03.TransportInfo;
import com.teragrep.rlp_03.TransportInfoImpl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class PlainSocket implements Socket {
    private final SocketChannel socketChannel;
    private final TransportInfo transportInfo;

    public PlainSocket(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
        EncryptionInfo encryptionInfo = new EncryptionInfoStub();
        this.transportInfo = new TransportInfoImpl(socketChannel, encryptionInfo);
    }

    @Override
    public long read(ByteBuffer[] dsts) throws IOException {
        return socketChannel.read(dsts);
    }

    @Override
    public long write(ByteBuffer[] dsts) throws IOException {
        return socketChannel.write(dsts);
    }

    @Override
    public TransportInfo getTransportInfo() {
        return transportInfo;
    }

    @Override
    public void close() throws IOException {
        socketChannel.close();
    }
}
