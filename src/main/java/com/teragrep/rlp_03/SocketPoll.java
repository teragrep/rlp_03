package com.teragrep.rlp_03;

import com.teragrep.rlp_03.config.Config;
import com.teragrep.rlp_03.config.TLSConfig;
import com.teragrep.rlp_03.context.ConnectionContext;
import com.teragrep.rlp_03.context.ContextFactory;
import com.teragrep.rlp_03.context.PlainContext;
import com.teragrep.rlp_03.context.TLSContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.function.Supplier;

import static java.nio.channels.SelectionKey.OP_ACCEPT;

public class SocketPoll implements Closeable, Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketPoll.class);

    private final Selector selector;
    private final ServerSocketChannel serverSocketChannel;

    private final ContextFactory contextFactory;

    public SocketPoll(int port, ContextFactory contextFactory) throws IOException {
        this.contextFactory = contextFactory;

        InetSocketAddress listenSocketAddress = new InetSocketAddress(port);

        this.selector = Selector.open();
        this.serverSocketChannel = ServerSocketChannel.open();
        this.serverSocketChannel.socket().setReuseAddress(true);
        this.serverSocketChannel.bind(listenSocketAddress);
        this.serverSocketChannel.configureBlocking(false);
        this.serverSocketChannel.register(this.selector, OP_ACCEPT);
    }

    @Override
    public void run() {
        try {
            int readyKeys = selector.select(500);

            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            for (SelectionKey selectionKey : selectionKeys) {
                if (selectionKey.isAcceptable()) {
                    processAccept(serverSocketChannel, selectionKey);
                }
                else {
                    // submit readTask/writeTask based on clientContext states
                    ConnectionContext connectionContext = (ConnectionContext) selectionKey.attachment();
                    connectionContext.handleEvent(selectionKey); // TODO selectionKey.readyOps() make selectionKey partOf clientContext?
                }
            }

        }
        catch (IOException ioException) {
            // FIXME exception in thread
            throw new UncheckedIOException(ioException);
        }
    }

    public void wakeup() {
        selector.wakeup();
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

            // new clientContext
            ConnectionContext connectionContext = contextFactory.create(clientSocketChannel);

            // non-blocking
            clientSocketChannel.configureBlocking(false);

            // all client connected sockets start in OP_READ
            clientSocketChannel.register(
                    selector,
                    SelectionKey.OP_READ,
                    connectionContext
            );
        }
    }
}
