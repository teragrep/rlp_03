package com.teragrep.rlp_03;

import com.teragrep.rlp_03.context.ConnectionContext;
import com.teragrep.rlp_03.context.channel.SocketFactory;
import com.teragrep.rlp_03.delegate.FrameDelegate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ConnectContextFactory {

    private final ExecutorService executorService;
    private final SocketFactory socketFactory;

    public ConnectContextFactory(ExecutorService executorService, SocketFactory socketFactory) {
        this.executorService = executorService;
        this.socketFactory = socketFactory;
    }

    public ConnectContext create(InetSocketAddress inetSocketAddress, FrameDelegate frameDelegate, Consumer<ConnectionContext> connectionContextConsumer) throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        try {
            socketChannel.socket().setKeepAlive(true);
            socketChannel.configureBlocking(false);
            socketChannel.connect(inetSocketAddress);
        }
        catch (IOException ioException){
            socketChannel.close();
            throw ioException;
        }

        return new ConnectContext(socketChannel, executorService, socketFactory, frameDelegate, connectionContextConsumer);
    }
}
