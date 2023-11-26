package com.teragrep.rlp_03;

import com.teragrep.rlp_03.context.ConnectionContext;
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

import static java.nio.channels.SelectionKey.OP_ACCEPT;

public class SocketPoll implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketPoll.class);

    private final Selector selector;
    private final ServerSocketChannel serverSocketChannel;

    private final SocketFactory socketFactory;

    private final SelectorNotification selectorNotification;

    public SocketPoll(int port, SocketFactory socketFactory) throws IOException {
        this.socketFactory = socketFactory;

        InetSocketAddress listenSocketAddress = new InetSocketAddress(port);

        this.selector = Selector.open();
        this.serverSocketChannel = ServerSocketChannel.open();
        this.serverSocketChannel.socket().setReuseAddress(true);
        this.serverSocketChannel.bind(listenSocketAddress);
        this.serverSocketChannel.configureBlocking(false);
        this.serverSocketChannel.register(this.selector, OP_ACCEPT);

        this.selectorNotification = new SelectorNotification(selector);
    }

    public void poll() throws IOException {
        int readyKeys = selector.select(500);

        Set<SelectionKey> selectionKeys = selector.selectedKeys();
        for (SelectionKey selectionKey : selectionKeys) {
            if (selectionKey.isAcceptable()) {
                processAccept(serverSocketChannel, selectionKey);
            } else {
                // submit readTask/writeTask based on clientContext states
                ConnectionContext connectionContext = (ConnectionContext) selectionKey.attachment();
                connectionContext.handleEvent(selectionKey, selectorNotification);
            }
        }
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

            if (clientSocketChannel == null) {
                return;
            }

            // tls/plain wrapper
            Socket socket = socketFactory.create(clientSocketChannel);

            // new clientContext
            ConnectionContext connectionContext = new ConnectionContext(socket, null);

            // non-blocking
            clientSocketChannel.configureBlocking(false);

            // all client connected sockets start in OP_READ
            clientSocketChannel.register(
                    selector,
                    SelectionKey.OP_READ,
                    socket
            );
        }
    }
}
