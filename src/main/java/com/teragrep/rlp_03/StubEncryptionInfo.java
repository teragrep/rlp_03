package com.teragrep.rlp_03;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import java.security.Principal;
import java.security.cert.Certificate;

public class StubEncryptionInfo implements EncryptionInfo {

    @Override
    public boolean isEncrypted() {
        return false;
    }

    @Override
    public String getSessionCipherSuite() {
        throw new IllegalStateException("not encrypted");
    }

    @Override
    public Certificate[] getLocalCertificates() {
        throw new IllegalStateException("not encrypted");
    }

    @Override
    public Principal getLocalPrincipal() {
        throw new IllegalStateException("not encrypted");
    }

    @Override
    public X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
        throw new IllegalStateException("not encrypted");
    }

    @Override
    public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
        throw new IllegalStateException("not encrypted");
    }

    @Override
    public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
        throw new IllegalStateException("not encrypted");
    }
}
