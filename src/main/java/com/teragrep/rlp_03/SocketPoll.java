package com.teragrep.rlp_03;

import com.teragrep.rlp_03.context.ConnectionContext;
import com.teragrep.rlp_03.context.InterestOps;
import com.teragrep.rlp_03.context.InterestOpsImpl;
import com.teragrep.rlp_03.context.channel.Socket;
import com.teragrep.rlp_03.context.channel.SocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static java.nio.channels.SelectionKey.OP_ACCEPT;

public class SocketPoll implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketPoll.class);

    private final Selector selector;
    private final ServerSocketChannel serverSocketChannel;

    private final SocketFactory socketFactory;

    private final ExecutorService executorService;

    private final Supplier<FrameProcessor> frameProcessorSupplier;

    public SocketPoll(int port, SocketFactory socketFactory, Supplier<FrameProcessor> frameProcessorSupplier) throws IOException {
        this.socketFactory = socketFactory;
        this.frameProcessorSupplier = frameProcessorSupplier;

        InetSocketAddress listenSocketAddress = new InetSocketAddress(port);

        this.selector = Selector.open();
        this.serverSocketChannel = ServerSocketChannel.open();
        this.serverSocketChannel.socket().setReuseAddress(true);
        this.serverSocketChannel.bind(listenSocketAddress);
        this.serverSocketChannel.configureBlocking(false);
        this.serverSocketChannel.register(this.selector, OP_ACCEPT);

        this.executorService = Executors.newCachedThreadPool();
    }

    public void poll() throws IOException {
        int readyKeys = selector.select(500);

        LOGGER.debug("readyKeys: " + readyKeys);

        Set<SelectionKey> selectionKeys = selector.selectedKeys();
        LOGGER.debug("selectionKeys <{}> ", selectionKeys);
        for (SelectionKey selectionKey : selectionKeys) {
            if (LOGGER.isInfoEnabled()) { // TODO debug
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
            if (selectionKey.isAcceptable()) {
                processAccept(serverSocketChannel, selectionKey);
            } else {
                // submit readTask/writeTask based on clientContext states
                ConnectionContext connectionContext = (ConnectionContext) selectionKey.attachment();
                connectionContext.handleEvent(selectionKey);
            }
        }
        selectionKeys.clear();
    }

    @Override
    public void close() throws IOException {
        this.serverSocketChannel.close();
        this.selector.close();
    }

    private void processAccept(ServerSocketChannel serverSocketChannel, SelectionKey selectionKey) throws IOException {
        if (selectionKey.isAcceptable()) {
            // create the client socket for a newly received connection
            SocketChannel clientSocketChannel = serverSocketChannel.accept();

            if (LOGGER.isInfoEnabled()) {
                LOGGER.debug("ServerSocket <{}> accepting ClientSocket <{}> ", serverSocketChannel.getLocalAddress(), clientSocketChannel.getRemoteAddress());
            }

            // tls/plain wrapper
            Socket socket = socketFactory.create(clientSocketChannel);

            int initialOps = SelectionKey.OP_READ;

            // new clientContext
            ConnectionContext connectionContext = new ConnectionContext(executorService, socket, frameProcessorSupplier);

            // non-blocking
            clientSocketChannel.configureBlocking(false);

            // all client connected sockets start in OP_READ
            SelectionKey clientSelectionKey = clientSocketChannel.register(
                    selector,
                    initialOps,
                    connectionContext
            );

            InterestOps interestOps = new InterestOpsImpl(clientSelectionKey, initialOps);
            connectionContext.updateInterestOps(interestOps);
        }
    }

}
