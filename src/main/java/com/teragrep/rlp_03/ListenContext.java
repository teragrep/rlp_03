package com.teragrep.rlp_03;

import com.teragrep.rlp_03.context.*;
import com.teragrep.rlp_03.context.channel.Socket;
import com.teragrep.rlp_03.context.channel.SocketFactory;
import com.teragrep.rlp_03.delegate.FrameDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketAddress;
import java.nio.channels.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

public class ListenContext implements Context {
    private static final Logger LOGGER = LoggerFactory.getLogger(ListenContext.class);
    private final ServerSocketChannel serverSocketChannel;
    private final ExecutorService executorService;
    private final SocketFactory socketFactory;
    private final Supplier<FrameDelegate> frameDelegateSupplier;
    private final ConnectionContextStub connectionContextStub;

    public ListenContext(ServerSocketChannel serverSocketChannel, ExecutorService executorService, SocketFactory socketFactory, Supplier<FrameDelegate> frameDelegateSupplier) {
        this.serverSocketChannel = serverSocketChannel;
        this.executorService = executorService;
        this.socketFactory = socketFactory;
        this.frameDelegateSupplier = frameDelegateSupplier;
        this.connectionContextStub = new ConnectionContextStub();
    }

    public void register(EventLoop eventLoop) throws ClosedChannelException {
        serverSocketChannel.register(eventLoop.selector(), SelectionKey.OP_ACCEPT, this);
    }

    public void handleEvent(SelectionKey selectionKey) {
        try {
            if (selectionKey.isAcceptable()) {
                // create the client socket for a newly received connection

                SocketChannel clientSocketChannel = serverSocketChannel.accept();

                if (LOGGER.isDebugEnabled()) {
                    // getLocalAddress() can throw so log and ignore as that isn't hard error
                    try {
                        SocketAddress localAddress = serverSocketChannel.getLocalAddress();
                        SocketAddress remoteAddress = clientSocketChannel.getRemoteAddress();
                        LOGGER.debug("ServerSocket <{}> accepting ClientSocket <{}> ", localAddress, remoteAddress);
                    } catch (IOException ioException) {
                        LOGGER.warn("Exception while getAddress", ioException);
                    }
                }

                // tls/plain wrapper
                Socket socket = socketFactory.create(clientSocketChannel);


                // non-blocking
                clientSocketChannel.configureBlocking(false);

                SelectionKey clientSelectionKey = clientSocketChannel.register(
                        selectionKey.selector(),
                        0, // interestOps: none at this point
                        connectionContextStub
                );

                InterestOps interestOps = new InterestOpsImpl(clientSelectionKey);

                ConnectionContext connectionContext = new ConnectionContextImpl(
                        executorService,
                        socket,
                        interestOps,
                        frameDelegateSupplier.get()
                );

                clientSelectionKey.attach(connectionContext);

                // proper attachment attached, now it is safe to use
                clientSelectionKey.interestOps(SelectionKey.OP_READ);
            }
        } catch (CancelledKeyException cke) {
            // thrown by accessing cancelled SelectionKey
            LOGGER.warn("SocketPoll.poll CancelledKeyException caught: {}", cke.getMessage());
            try {
                selectionKey.channel().close();
            } catch (IOException ignored) {

            }
        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }
    }

    @Override
    public void close() {
        if (LOGGER.isDebugEnabled()) {
            try {
                LOGGER.debug("close serverSocketChannel <{}>", serverSocketChannel.getLocalAddress());
            } catch (IOException ignored) {

            }
        }

        try {
            serverSocketChannel.close();
        } catch (IOException ioException) {
            LOGGER.warn("serverSocketChannel <{}> close threw", serverSocketChannel, ioException);
        }

    }
}
