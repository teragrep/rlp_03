package com.teragrep.rlp_03.context.channel;

import com.teragrep.rlp_03.EncryptionInfo;
import com.teragrep.rlp_03.TLSInfo;
import com.teragrep.rlp_03.TransportInfo;
import tlschannel.TlsChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class TLSSocket implements Socket {

    private final TlsChannel tlsChannel;
    private final TransportInfo transportInfo;


    public TLSSocket(SocketChannel socketChannel, TlsChannel tlsChannel) {
        this.tlsChannel = tlsChannel;
        EncryptionInfo encryptionInfo = new TLSInfo(tlsChannel);
        this.transportInfo = new TransportInfo(socketChannel, encryptionInfo);
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        return tlsChannel.read(dst);
    }

    @Override
    public int write(ByteBuffer dst) throws IOException {
        return tlsChannel.write(dst);
    }

    @Override
    public TransportInfo getTransportInfo() {
        return transportInfo;
    }

    @Override
    public void close() throws IOException {
        tlsChannel.close();
    }
}
