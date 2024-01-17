package com.teragrep.rlp_03;

import com.teragrep.rlp_03.context.*;
import com.teragrep.rlp_03.context.channel.SocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.concurrent.*;

import static java.nio.channels.SelectionKey.OP_ACCEPT;

public class ServerSocket {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerSocket.class);

    final SocketFactory socketFactory;

    final ExecutorService executorService;

    final FrameProcessorPool frameProcessorPool;

    final ConnectionContextStub connectionContextStub;

    final InetSocketAddress listenSocketAddress;

    public ServerSocket(
            int port,
            ExecutorService executorService,
            SocketFactory socketFactory,
            FrameProcessorPool frameProcessorPool
    ) {
        this.socketFactory = socketFactory;
        this.frameProcessorPool = frameProcessorPool;
        this.executorService = executorService;
        this.connectionContextStub = new ConnectionContextStub();
        this.listenSocketAddress = new InetSocketAddress(port);
    }

    public ServerSocketOpen open() throws IOException {
        Selector selector = Selector.open();
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().setReuseAddress(true);
        serverSocketChannel.bind(listenSocketAddress);
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, OP_ACCEPT);

        return new ServerSocketOpen(this, selector, serverSocketChannel);
    }


}