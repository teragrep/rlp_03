package com.teragrep.rlp_03;

import tlschannel.TlsChannel;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import java.nio.channels.SocketChannel;
import java.security.Principal;
import java.security.cert.Certificate;

public class TlsTransportInfo extends TransportInfo {

    private final TlsChannel tlsChannel;

    TlsTransportInfo(SocketChannel socketChannel, TlsChannel tlsChannel) {
        super(socketChannel);
        this.tlsChannel = tlsChannel;
    }
    
    public String getSessionCipherSuite() {
        return tlsChannel.getSslEngine().getSession().getCipherSuite();
    }
    
    public Certificate[] getLocalCertificates() {
        return tlsChannel.getSslEngine().getSession().getLocalCertificates();
    }
    
    public Principal getLocalPrincipal() {
        return tlsChannel.getSslEngine().getSession().getLocalPrincipal();
    }
    
    public X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
        return tlsChannel.getSslEngine().getSession().getPeerCertificateChain();
    }

    public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
        return tlsChannel.getSslEngine().getSession().getPeerCertificates();
    }
    
    public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
        return tlsChannel.getSslEngine().getSession().getPeerPrincipal();
    }
}
