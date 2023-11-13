package com.teragrep.rlp_03.context;

import java.nio.channels.SocketChannel;

public abstract class ContextFactory {
    abstract public ConnectionContext create(SocketChannel socketChannel);
}
