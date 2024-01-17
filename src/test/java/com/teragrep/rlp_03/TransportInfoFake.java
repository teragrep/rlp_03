package com.teragrep.rlp_03;

public class TransportInfoFake  implements TransportInfo {
    private final EncryptionInfo encryptionInfo;
    public TransportInfoFake() {
        this.encryptionInfo = new EncryptionInfoStub();
    }

    @Override
    public String getLocalAddress() {
        return "fake.local.example.com";
    }

    @Override
    public int getLocalPort() {
        return 601;
    }

    @Override
    public String getPeerAddress() {
        return "fake.remote.example.com";
    }

    @Override
    public int getPeerPort() {
        return 65535;
    }

    @Override
    public EncryptionInfo getEncryptionInfo() {
        return encryptionInfo;
    }
}
