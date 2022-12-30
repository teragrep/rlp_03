package com.teragrep.rlp_03;

import com.teragrep.rlp_01.RelpBatch;
import com.teragrep.rlp_01.RelpConnection;
import com.teragrep.rlp_01.SSLContextFactory;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TlsClientTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TlsClientTest.class);


    private final String hostname = "localhost";
    private static final int port = 2601;

    private Server server;
    private final List<byte[]> serverMessageList = new LinkedList<>();

    @BeforeAll
    public void init() throws IOException, GeneralSecurityException {

        final Consumer<byte[]> cbFunction = serverMessageList::add;

        SSLContext sslContext =
                SSLContextFactory.authenticatedContext(
                        "src/test/resources/tls/keystore.jks",
                        "changeit",
                        "TLSv1.3"
                );


        Function<SSLContext, SSLEngine> sslEngineFunction = sslContext1 -> {
            SSLEngine sslEngine = sslContext1.createSSLEngine();
            sslEngine.setUseClientMode(false);
            return sslEngine;
        };

        server = new Server(
                port,
                new SyslogFrameProcessor(cbFunction),
                sslContext,
                sslEngineFunction
        );
        server.setNumberOfThreads(1);

        server.start();
    }

    @AfterAll
    public void cleanup() throws InterruptedException {
        server.stop();
    }


    @Test
    public void testTlsClient() throws IOException, TimeoutException, GeneralSecurityException {

        SSLContext sslContext = SSLContextFactory.authenticatedContext(
                "src/test/resources/tls/keystore.jks",
                "changeit",
                "TLSv1.3"
        );

        SSLEngine sslEngine = sslContext.createSSLEngine();

        RelpConnection relpSession = new RelpConnection(sslEngine);

        relpSession.connect(hostname, port);
        String msg = "<14>1 2020-05-15T13:24:03.603Z CFE-16 capsulated - - [CFE-16-metadata@48577 authentication_token=\"AUTH_TOKEN_11111\" channel=\"CHANNEL_11111\" time_source=\"generated\"][CFE-16-origin@48577] \"Hello, world!\"\n";
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        RelpBatch batch = new RelpBatch();
        long reqId = batch.insert(data);
        relpSession.commit(batch);
        // verify successful transaction
        Assertions.assertTrue(batch.verifyTransaction(reqId));
        relpSession.disconnect();

        // message must equal to what was send
        Assertions.assertEquals(msg, new String(serverMessageList.get(0)));

        // clear received list
        serverMessageList.clear();
    }
}
