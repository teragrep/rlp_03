package com.teragrep.rlp_03.context.channel;

import java.nio.channels.SocketChannel;

public abstract class SocketFactory {
    abstract public Socket create(SocketChannel socketChannel);
}
