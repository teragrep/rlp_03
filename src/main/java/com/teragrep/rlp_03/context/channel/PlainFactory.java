package com.teragrep.rlp_03.context.channel;

import java.nio.channels.SocketChannel;

public class PlainFactory extends SocketFactory {

    @Override
    public Socket create(SocketChannel socketChannel) {
        return new PlainSocket(
                        socketChannel
                );
    }
}
