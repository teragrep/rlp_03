package com.teragrep.rlp_03;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import java.security.Principal;
import java.security.cert.Certificate;

public class StubEncryptionInfo implements EncryptionInfo {

    private final Certificate[] certificates;
    private final X509Certificate[] x509Certificates;
    private final Principal principal;

    StubEncryptionInfo() {
        this.certificates = new Certificate[0];
        this.x509Certificates = new X509Certificate[0];
        this.principal = () -> "";
    }

    @Override
    public boolean isEncrypted() {
        return false;
    }

    @Override
    public String getSessionCipherSuite() {
        return "";
    }

    @Override
    public Certificate[] getLocalCertificates() {
        return certificates;
    }

    @Override
    public Principal getLocalPrincipal() {
        return principal;
    }

    @Override
    public X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
        return x509Certificates;
    }

    @Override
    public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
        return certificates;
    }

    @Override
    public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
        return principal;
    }
}
