package com.teragrep.rlp_03;

public interface TransportInfo {
    String getLocalAddress();

    int getLocalPort();

    String getPeerAddress();

    int getPeerPort();

    EncryptionInfo getEncryptionInfo();
}
