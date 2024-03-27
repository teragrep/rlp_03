package com.teragrep.rlp_03;

import com.teragrep.rlp_03.context.ConnectionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Set;

public class EventLoop implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventLoop.class);

    private final Selector selector;

    public EventLoop(Selector selector) {
        this.selector = selector;
    }

    public Selector selector() {
        return selector;
    }

    public void poll() throws IOException {
        int readyKeys = selector.select();

        LOGGER.debug("readyKeys <{}>",readyKeys);

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

            if (selectionKey.isAcceptable()) {
                // ListenContext
                ListenContext listenContext = (ListenContext) selectionKey.attachment();
                listenContext.handleEvent(selectionKey);
            }
            else if (selectionKey.isConnectable()) {
                // TODO ConnectContext
                throw new IllegalStateException("not supported yet");
            }
            else {
                // ConnectionContext (aka EstablishedContext)
                ConnectionContext connectionContext = (ConnectionContext) selectionKey.attachment();
                try {
                connectionContext.handleEvent(selectionKey);
                }
                catch (CancelledKeyException cke) {
                    LOGGER.warn("SocketPoll.poll CancelledKeyException caught: {}", cke.getMessage());
                    connectionContext.close();
                }
            }
        }
        selectionKeys.clear();
    }

    @Override
    public void close() throws IOException {
        /* FIXME closing?
        for (SelectionKey selectionKey : selector.keys()) {
            ((Closeable) selectionKey.attachment()).close();
        }

         */
        selector.close();
    }

    public void wakeup() {
        selector.wakeup();
    }
}
