package com.teragrep.rlp_03;

import com.teragrep.rlp_03.context.channel.SocketFactory;
import com.teragrep.rlp_03.delegate.FrameDelegate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

public class ListenContextFactory {
    private final ExecutorService executorService;
    private final SocketFactory socketFactory;
    private final Supplier<FrameDelegate> frameDelegateSupplier;
    public ListenContextFactory(ExecutorService executorService, SocketFactory socketFactory, Supplier<FrameDelegate> frameDelegateSupplier) {
        this.executorService = executorService;
        this.socketFactory = socketFactory;
        this.frameDelegateSupplier = frameDelegateSupplier;
    }

    public ListenContext open(InetSocketAddress inetSocketAddress) throws IOException{
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        try {
            serverSocketChannel.socket().setReuseAddress(true);
            serverSocketChannel.bind(inetSocketAddress);
            serverSocketChannel.configureBlocking(false);
        } catch (IOException ioException) {
            serverSocketChannel.close();
            throw ioException;
        }
        return new ListenContext(serverSocketChannel, executorService, socketFactory, frameDelegateSupplier);
    }
}
