package com.teragrep.rlp_03.context.channel;

import com.teragrep.rlp_03.EncryptionInfo;
import com.teragrep.rlp_03.EncryptionInfoTLS;
import com.teragrep.rlp_03.TransportInfo;
import com.teragrep.rlp_03.TransportInfoImpl;
import tlschannel.TlsChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class TLSSocket implements Socket {

    private final TlsChannel tlsChannel;
    private final TransportInfo transportInfo;


    public TLSSocket(SocketChannel socketChannel, TlsChannel tlsChannel) {
        this.tlsChannel = tlsChannel;
        EncryptionInfo encryptionInfo = new EncryptionInfoTLS(tlsChannel);
        this.transportInfo = new TransportInfoImpl(socketChannel, encryptionInfo);
    }

    @Override
    public long read(ByteBuffer[] dsts) throws IOException {
        return tlsChannel.read(dsts);
    }

    @Override
    public long write(ByteBuffer[] dsts) throws IOException {
        return tlsChannel.write(dsts);
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
