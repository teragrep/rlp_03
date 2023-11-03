package com.teragrep.rlp_03.config;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.util.function.Function;

public class TLSConfig {
    public final boolean useTls;

    private final SSLContext sslContext;

    private final Function<SSLContext, SSLEngine> sslEngineFunction;

    public TLSConfig() {
        this.useTls = false;

        // can't initialize these
        this.sslContext = null;
        this.sslEngineFunction = null;
    }

    public TLSConfig(SSLContext sslContext, Function<SSLContext, SSLEngine> sslEngineFunction) {
        this.useTls = true;
        this.sslContext = sslContext;
        this.sslEngineFunction = sslEngineFunction;
    }

    public SSLContext getSslContext() {
        if (!useTls) {
            throw new IllegalStateException("tls not enabled");
        }
        return sslContext;
    }

    public Function<SSLContext, SSLEngine> getSslEngineFunction() {
        if (!useTls) {
            throw new IllegalStateException("tls not enabled");
        }
        return sslEngineFunction;
    }
}
