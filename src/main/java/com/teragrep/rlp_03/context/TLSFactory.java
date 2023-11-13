package com.teragrep.rlp_03.context;

import com.teragrep.rlp_03.FrameProcessor;
import com.teragrep.rlp_03.EncryptionInfo;
import com.teragrep.rlp_03.TLSInfo;
import com.teragrep.rlp_03.TlsTransportInfo;
import com.teragrep.rlp_03.TransportInfo;
import tlschannel.ServerTlsChannel;
import tlschannel.TlsChannel;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.nio.channels.SocketChannel;
import java.util.function.Function;
import java.util.function.Supplier;

final class TLSFactory extends ContextFactory {

    private final Supplier<FrameProcessor> frameProcessorSupplier;

    private final SSLContext sslContext;
    private final Function<SSLContext, SSLEngine> sslEngineFunction;

    public TLSFactory(Supplier<FrameProcessor> frameProcessorSupplier, SSLContext sslContext, Function<SSLContext, SSLEngine> sslEngineFunction) {
        this.frameProcessorSupplier = frameProcessorSupplier;
        this.sslContext = sslContext;
        this.sslEngineFunction = sslEngineFunction;

    }
    @Override
    public ConnectionContext create(SocketChannel socketChannel) {

        TlsChannel tlsChannel = ServerTlsChannel
                .newBuilder(socketChannel, sslContext)
                .withEngineFactory(sslEngineFunction)
                .build();

        EncryptionInfo encryptionInfo = new TLSInfo(tlsChannel);
        TransportInfo transportInfo = new TransportInfo(socketChannel, encryptionInfo);

        return new TLSContext(
                tlsChannel,
                frameProcessorSupplier.get(),
                transportInfo
        );
    }
}
