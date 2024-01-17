package com.teragrep.rlp_03;

import com.teragrep.rlp_03.context.ConnectionContext;
import com.teragrep.rlp_03.context.ConnectionContextImpl;
import com.teragrep.rlp_03.context.InterestOps;
import com.teragrep.rlp_03.context.InterestOpsImpl;
import com.teragrep.rlp_03.context.channel.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.*;
import java.util.Set;

public class ServerSocketOpen implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerSocketOpen.class);

    private final ServerSocket serverSocket;
    private final Selector selector;
    private final ServerSocketChannel serverSocketChannel;


    public ServerSocketOpen(ServerSocket serverSocket, Selector selector, ServerSocketChannel serverSocketChannel) {
        this.serverSocket = serverSocket;
        this.selector = selector;
        this.serverSocketChannel = serverSocketChannel;
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
        serverSocket.executorService.shutdown();
    }

    private void processAccept(ServerSocketChannel serverSocketChannel, SelectionKey selectionKey) throws IOException {
        if (selectionKey.isAcceptable()) {
            // create the client socket for a newly received connection
            SocketChannel clientSocketChannel = serverSocketChannel.accept();

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("ServerSocket <{}> accepting ClientSocket <{}> ", serverSocketChannel.getLocalAddress(), clientSocketChannel.getRemoteAddress());
            }

            // tls/plain wrapper
            Socket socket = serverSocket.socketFactory.create(clientSocketChannel);



            // non-blocking
            clientSocketChannel.configureBlocking(false);

            // all client connected sockets start in OP_READ
            int initialOps = SelectionKey.OP_READ;

            SelectionKey clientSelectionKey = clientSocketChannel.register(
                    selector,
                    0, // interestOps: none at this point
                    serverSocket.connectionContextStub
            );

            InterestOps interestOps = new InterestOpsImpl(clientSelectionKey);
            ConnectionContext connectionContext = new ConnectionContextImpl(
                    serverSocket.executorService,
                    socket,
                    interestOps,
                    serverSocket.frameProcessorPool
            );

            clientSelectionKey.attach(connectionContext);

            // proper attachment attached, now it is safe to use
            clientSelectionKey.interestOps(SelectionKey.OP_READ);
        }
    }

    Selector selector() {
        return selector;
    }
}
