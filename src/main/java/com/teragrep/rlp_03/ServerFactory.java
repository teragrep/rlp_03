package com.teragrep.rlp_03;

import com.teragrep.rlp_03.config.Config;
import com.teragrep.rlp_03.config.TLSConfig;
import com.teragrep.rlp_03.context.ConnectionContextStub;
import com.teragrep.rlp_03.context.channel.PlainFactory;
import com.teragrep.rlp_03.context.channel.SocketFactory;
import com.teragrep.rlp_03.context.channel.TLSFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static java.nio.channels.SelectionKey.OP_ACCEPT;

public class ServerFactory {

    final Config config;
    final TLSConfig tlsConfig;
    final Supplier<FrameProcessor> frameProcessorSupplier;

    final ThreadPoolExecutor executorService;
    final ConnectionContextStub connectionContextStub;
    final InetSocketAddress listenSocketAddress;


    public ServerFactory(Config config, FrameProcessor frameProcessor) {
        this(config, () -> frameProcessor);
    }

    public ServerFactory(Config config, Supplier<FrameProcessor> frameProcessorSupplier) {
        this(config, new TLSConfig(), frameProcessorSupplier);
    }


    public ServerFactory(
            Config config,
            TLSConfig tlsConfig,
            FrameProcessor frameProcessor
    ) {
        this(config, tlsConfig, () -> frameProcessor);
    }

    public ServerFactory(
            Config config,
            TLSConfig tlsConfig,
            Supplier<FrameProcessor> frameProcessorSupplier
    ) {

        this.config = config;
        this.tlsConfig = tlsConfig;
        this.frameProcessorSupplier = frameProcessorSupplier;

        this.executorService = new ThreadPoolExecutor(config.numberOfThreads, config.numberOfThreads, Long.MAX_VALUE, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        this.connectionContextStub = new ConnectionContextStub();
        this.listenSocketAddress = new InetSocketAddress(config.port);
    }

    Server create() throws IOException {
        config.validate();

        SocketFactory socketFactory;
        if (tlsConfig.useTls) {
            socketFactory = new TLSFactory(tlsConfig.getSslContext(), tlsConfig.getSslEngineFunction());
        }
        else {
            socketFactory = new PlainFactory();
        }

        Selector selector = Selector.open();
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().setReuseAddress(true);
        serverSocketChannel.bind(listenSocketAddress);
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, OP_ACCEPT);

        return new Server(executorService, frameProcessorSupplier, serverSocketChannel, socketFactory, selector);
    }
}
