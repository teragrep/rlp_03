package com.teragrep.rlp_03;

import java.nio.channels.SocketChannel;

public class TransportInfoImpl implements TransportInfo {

    private final SocketChannel socketChannel;
    private final EncryptionInfo encryptionInfo;

    public TransportInfoImpl(SocketChannel socketChannel, EncryptionInfo encryptionInfo) {
        this.socketChannel = socketChannel;
        this.encryptionInfo = encryptionInfo;
    }

    @Override
    public String getLocalAddress() {
        return socketChannel.socket().getLocalAddress().toString();
    }

    @Override
    public int getLocalPort() {
        return socketChannel.socket().getLocalPort();
    }

    @Override
    public String getPeerAddress() {
        return socketChannel.socket().getInetAddress().toString();
    }

    @Override
    public int getPeerPort() {
        return socketChannel.socket().getPort();
    }

    @Override
    public EncryptionInfo getEncryptionInfo() {
        return encryptionInfo;
    }
}
