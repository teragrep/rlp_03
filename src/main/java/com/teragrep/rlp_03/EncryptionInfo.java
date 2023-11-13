package com.teragrep.rlp_03;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import java.security.Principal;
import java.security.cert.Certificate;

public interface EncryptionInfo {

    boolean isEncrypted();

    String getSessionCipherSuite();

    Certificate[] getLocalCertificates();

    Principal getLocalPrincipal();

    X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException;

    Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException;

    Principal getPeerPrincipal() throws SSLPeerUnverifiedException;
}
