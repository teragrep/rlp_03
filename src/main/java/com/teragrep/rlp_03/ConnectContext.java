package com.teragrep.rlp_03;

import com.teragrep.rlp_03.context.*;
import com.teragrep.rlp_03.context.channel.SocketFactory;
import com.teragrep.rlp_03.delegate.FrameDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ConnectContext implements Context {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectContext.class);

    private final SocketChannel socketChannel;
    private final ExecutorService executorService;
    private final SocketFactory socketFactory;
    private final FrameDelegate frameDelegate;

    private final Consumer<ConnectionContext> connectionContextConsumer;
    public ConnectContext(SocketChannel socketChannel, ExecutorService executorService, SocketFactory socketFactory, FrameDelegate frameDelegate, Consumer<ConnectionContext> connectionContextConsumer) {
        this.socketChannel = socketChannel;
        this.executorService = executorService;
        this.socketFactory = socketFactory;
        this.connectionContextConsumer = connectionContextConsumer;
        this.frameDelegate = frameDelegate;
    }

    public void register(EventLoop eventLoop) throws ClosedChannelException {
        socketChannel.register(eventLoop.selector(), SelectionKey.OP_CONNECT, this);
    }


    @Override
    public void handleEvent(SelectionKey selectionKey) {
        if (selectionKey.isConnectable()) {
            try {
                if (!socketChannel.finishConnect()) {
                    // not yet complete
                    return;
                }
            }
            catch (IOException ioException) {
                LOGGER.warn("socketChannel <{}> finishConnect threw, closing", socketChannel, ioException);
                close();
                return;
            }

            // No need to be longer interested in connect.
            selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_CONNECT);


            InterestOps interestOps = new InterestOpsImpl(selectionKey);

            ConnectionContext connectionContext = new ConnectionContextImpl(
                    executorService,
                    socketFactory.create(socketChannel),
                    interestOps,
                    frameDelegate);
            // change attachment to established -> ConnectionContext
            selectionKey.attach(
                    connectionContext
            );

            interestOps.add(SelectionKey.OP_READ);

            LOGGER.info("ready");
            connectionContextConsumer.accept(connectionContext);
        }
    }

    @Override
    public void close() {
        try {
            socketChannel.close();
        }
        catch (IOException ioException) {
            LOGGER.warn("socketChannel <{}> close threw", socketChannel, ioException);
        }
    }
}


