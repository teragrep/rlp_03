package com.teragrep.rlp_03.context.channel;

import tlschannel.ServerTlsChannel;
import tlschannel.TlsChannel;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.nio.channels.SocketChannel;
import java.util.function.Function;

public final class TLSFactory extends SocketFactory {

    private final SSLContext sslContext;
    private final Function<SSLContext, SSLEngine> sslEngineFunction;

    public TLSFactory(SSLContext sslContext, Function<SSLContext, SSLEngine> sslEngineFunction) {
        this.sslContext = sslContext;
        this.sslEngineFunction = sslEngineFunction;

    }
    @Override
    public Socket create(SocketChannel socketChannel) {

        TlsChannel tlsChannel = ServerTlsChannel
                .newBuilder(socketChannel, sslContext)
                .withEngineFactory(sslEngineFunction)
                .build();

        return new TLSSocket(
                socketChannel,
                tlsChannel
        );
    }
}
