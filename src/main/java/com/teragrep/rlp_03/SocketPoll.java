package com.teragrep.rlp_03;

import com.teragrep.rlp_03.context.*;
import com.teragrep.rlp_03.context.channel.Socket;
import com.teragrep.rlp_03.context.channel.SocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.*;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

public class SocketPoll implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SocketPoll.class);

    private final ExecutorService executorService;

    private final SocketFactory socketFactory;
    private final Selector selector;

    private final ServerSocketChannel serverSocketChannel;

    private final Supplier<FrameProcessor> frameProcessorSupplier;

    private final ConnectionContextStub connectionContextStub;


    public SocketPoll(
            ExecutorService executorService,
            SocketFactory socketFactory,
            Selector selector,
            ServerSocketChannel serverSocketChannel,
            Supplier<FrameProcessor> frameProcessorSupplier
    ) {
        this.executorService = executorService;
        this.socketFactory = socketFactory;
        this.selector = selector;
        this.serverSocketChannel = serverSocketChannel;
        this.frameProcessorSupplier = frameProcessorSupplier;
        this.connectionContextStub = new ConnectionContextStub();
    }

    public void poll() throws IOException {
        int readyKeys = selector.select();

        LOGGER.debug("readyKeys: " + readyKeys);

        Set<SelectionKey> selectionKeys = selector.selectedKeys();
        LOGGER.debug("selectionKeys <{}> ", selectionKeys);
        for (SelectionKey selectionKey : selectionKeys) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "selectionKey <{}>: " +
                                "isValid <{}>, " +
                                "isConnectable <{}>, " +
                                "isAcceptable <{}>, " +
                                "isReadable <{}>, " +
                                "isWritable <{}>",
                        selectionKey,
                        selectionKey.isValid(),
                        selectionKey.isConnectable(),
                        selectionKey.isAcceptable(),
                        selectionKey.isReadable(),
                        selectionKey.isWritable()
                );
            }
            try {
                if (selectionKey.isAcceptable()) {
                    processAccept(serverSocketChannel, selectionKey);
                } else {
                    // submit readTask/writeTask based on clientContext states
                    ConnectionContext connectionContext = (ConnectionContext) selectionKey.attachment();
                    try {
                        connectionContext.handleEvent(selectionKey);
                    }
                    catch (CancelledKeyException cke) {
                        connectionContext.close();
                    }
                }
            } catch (CancelledKeyException cke) { // TODO is this proper to catch here?
                selectionKey.channel().close();
            }

        }
        selectionKeys.clear();
    }

    @Override
    public void close() throws IOException {
        serverSocketChannel.close();
        selector.close();
        executorService.shutdown();
    }

    private void processAccept(ServerSocketChannel serverSocketChannel, SelectionKey selectionKey) throws IOException {
        if (selectionKey.isAcceptable()) {
            // create the client socket for a newly received connection
            SocketChannel clientSocketChannel = serverSocketChannel.accept();

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("ServerSocket <{}> accepting ClientSocket <{}> ", serverSocketChannel.getLocalAddress(), clientSocketChannel.getRemoteAddress());
            }

            // tls/plain wrapper
            Socket socket = socketFactory.create(clientSocketChannel);



            // non-blocking
            clientSocketChannel.configureBlocking(false);

            SelectionKey clientSelectionKey = clientSocketChannel.register(
                    selector,
                    0, // interestOps: none at this point
                    connectionContextStub
            );

            InterestOps interestOps = new InterestOpsImpl(clientSelectionKey);

            FrameProcessor frameProcessor = frameProcessorSupplier.get();

            ConnectionContext connectionContext = new ConnectionContextImpl(
                    executorService,
                    socket,
                    interestOps,
                    frameProcessor
            );

            clientSelectionKey.attach(connectionContext);

            // proper attachment attached, now it is safe to use
            clientSelectionKey.interestOps(SelectionKey.OP_READ);
        }
    }
}
