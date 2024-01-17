package com.teragrep.rlp_03;

import com.teragrep.rlp_03.context.*;
import com.teragrep.rlp_03.context.channel.Socket;
import com.teragrep.rlp_03.context.channel.SocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Set;
import java.util.concurrent.*;

import static java.nio.channels.SelectionKey.OP_ACCEPT;

public class SocketPoll implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketPoll.class);

    private final Selector selector;
    private final ServerSocketChannel serverSocketChannel;

    private final SocketFactory socketFactory;

    private final ExecutorService executorService;

    private final FrameProcessorPool frameProcessorPool;

    public SocketPoll(int port, ExecutorService executorService, SocketFactory socketFactory, FrameProcessorPool frameProcessorPool) throws IOException {
        this.socketFactory = socketFactory;
        this.frameProcessorPool = frameProcessorPool;

        InetSocketAddress listenSocketAddress = new InetSocketAddress(port);

        this.selector = Selector.open();
        this.serverSocketChannel = ServerSocketChannel.open();
        this.serverSocketChannel.socket().setReuseAddress(true);
        this.serverSocketChannel.bind(listenSocketAddress);
        this.serverSocketChannel.configureBlocking(false);
        this.serverSocketChannel.register(this.selector, OP_ACCEPT);

        this.executorService = executorService;

    }

    public void poll() throws IOException {
        int readyKeys = selector.select(500); // FIXME remove timeout and use wakeup when server stop

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
        this.serverSocketChannel.close();
        this.selector.close();
        this.executorService.shutdown();
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


            // new clientContext, FIXME use ConnectionContextStub here
            ConnectionContext connectionContext = new ConnectionContextImpl(executorService, socket, frameProcessorPool);

            // non-blocking
            clientSocketChannel.configureBlocking(false);

            // all client connected sockets start in OP_READ
            int initialOps = SelectionKey.OP_READ;

            SelectionKey clientSelectionKey = clientSocketChannel.register(
                    selector,
                    initialOps,
                    connectionContext
            );

            // FIXME use clientSelectionKey.attach(); the proper one instead of stub
            InterestOps interestOps = new InterestOpsImpl(clientSelectionKey);
            connectionContext.updateInterestOps(interestOps);
        }
    }

}
