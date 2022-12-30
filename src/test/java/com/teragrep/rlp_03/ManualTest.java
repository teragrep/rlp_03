package com.teragrep.rlp_03;

import com.teragrep.rlp_01.SSLContextFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

public class ManualTest {
    @Test // for testing with manual tools
    @EnabledIfSystemProperty(named="runServerTest", matches="true")
    public void runServerTest() throws IOException, InterruptedException {
        final Consumer<byte[]> cbFunction;
        AtomicLong asd = new AtomicLong();

        cbFunction = (message) -> {
            asd.getAndIncrement();
        };
        int port = 1601;
        Server server = new Server(port, new SyslogFrameProcessor(cbFunction));
        server.setNumberOfThreads(4);
        server.start();
        Thread.sleep(Long.MAX_VALUE);
    }

    @Test // for testing with manual tools
    @EnabledIfSystemProperty(named="runServerTlsTest", matches="true")
    public void runServerTlsTest() throws IOException, InterruptedException, GeneralSecurityException {
        final Consumer<byte[]> cbFunction;
        AtomicLong asd = new AtomicLong();

        cbFunction = (message) -> {
            asd.getAndIncrement();
        };
        int port = 1602;

        SSLContext sslContext =
                SSLContextFactory.authenticatedContext(
                        "src/test/resources/tls/keystore.jks",
                        "changeit",
                        "TLSv1.3"
                );


        Function<SSLContext, SSLEngine> sslEngineFunction = new Function<SSLContext, SSLEngine>() {
            @Override
            public SSLEngine apply(SSLContext sslContext) {
                SSLEngine sslEngine = sslContext.createSSLEngine();
                sslEngine.setUseClientMode(false);
                return sslEngine;
            }
        };

        Server server = new Server(
                port,
                new SyslogFrameProcessor(cbFunction),
                sslContext,
                sslEngineFunction
        );
        server.setNumberOfThreads(1);



        server.start();
        Thread.sleep(Long.MAX_VALUE);
    }
}
