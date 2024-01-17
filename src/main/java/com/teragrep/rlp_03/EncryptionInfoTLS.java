package com.teragrep.rlp_03;

import tlschannel.TlsChannel;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import java.security.Principal;
import java.security.cert.Certificate;

final public class EncryptionInfoTLS implements EncryptionInfo {

    private final TlsChannel tlsChannel;

    public EncryptionInfoTLS(TlsChannel tlsChannel) {
        this.tlsChannel = tlsChannel;
    }

    @Override
    public boolean isEncrypted() {
        return true;
    }
    
    @Override
    public String getSessionCipherSuite() {
        return tlsChannel.getSslEngine().getSession().getCipherSuite();
    }
    
    @Override
    public Certificate[] getLocalCertificates() {
        return tlsChannel.getSslEngine().getSession().getLocalCertificates();
    }
    
    @Override
    public Principal getLocalPrincipal() {
        return tlsChannel.getSslEngine().getSession().getLocalPrincipal();
    }
    
    @Override
    public X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
        return tlsChannel.getSslEngine().getSession().getPeerCertificateChain();
    }

    @Override
    public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
        return tlsChannel.getSslEngine().getSession().getPeerCertificates();
    }
    
    @Override
    public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
        return tlsChannel.getSslEngine().getSession().getPeerPrincipal();
    }
}
