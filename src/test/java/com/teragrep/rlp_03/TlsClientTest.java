/*
 * Java Reliable Event Logging Protocol Library Server Implementation RLP-03
 * Copyright (C) 2021-2024 Suomen Kanuuna Oy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 * Additional permission under GNU Affero General Public License version 3
 * section 7
 *
 * If you modify this Program, or any covered work, by linking or combining it
 * with other code, such other code is not for that reason alone subject to any
 * of the requirements of the GNU Affero GPL version 3 as long as this Program
 * is the same Program as licensed from Suomen Kanuuna Oy without any additional
 * modifications.
 *
 * Supplemented terms under GNU Affero General Public License version 3
 * section 7
 *
 * Origin of the software must be attributed to Suomen Kanuuna Oy. Any modified
 * versions must be marked as "Modified version of" The Program.
 *
 * Names of the licensors and authors may not be used for publicity purposes.
 *
 * No rights are granted for use of trade names, trademarks, or service marks
 * which are in The Program if any.
 *
 * Licensee must indemnify licensors and authors for any liability that these
 * contractual assumptions impose on licensors and authors.
 *
 * To the extent this program is licensed as part of the Commercial versions of
 * Teragrep, the applicable Commercial License may apply to this file if you as
 * a licensee so wish it.
 */
package com.teragrep.rlp_03;

import com.teragrep.rlp_01.RelpBatch;
import com.teragrep.rlp_01.RelpConnection;
import com.teragrep.rlp_01.SSLContextFactory;
import com.teragrep.rlp_03.channel.socket.TLSFactory;
import com.teragrep.rlp_03.eventloop.EventLoop;
import com.teragrep.rlp_03.eventloop.EventLoopFactory;
import com.teragrep.rlp_03.frame.delegate.DefaultFrameDelegate;
import com.teragrep.rlp_03.frame.delegate.FrameContext;
import com.teragrep.rlp_03.server.ServerFactory;
import org.junit.jupiter.api.*;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TlsClientTest {

    /**
     * helper class to load separate keystore and truststore
     */

    private static class InternalSSLContextFactory {

        public static SSLContext authenticatedContext(
                String keystorePath,
                String truststorePath,
                String keystorePassword,
                String truststorePassword,
                String protocol
        ) throws GeneralSecurityException, IOException {

            SSLContext sslContext = SSLContext.getInstance(protocol);
            KeyStore ks = KeyStore.getInstance("JKS");
            KeyStore ts = KeyStore.getInstance("JKS");

            File ksFile = new File(keystorePath);
            File tsFile = new File(truststorePath);

            try (FileInputStream ksFileIS = new FileInputStream(ksFile)) {
                try (FileInputStream tsFileIS = new FileInputStream(tsFile)) {
                    ts.load(tsFileIS, truststorePassword.toCharArray());
                    TrustManagerFactory tmf = TrustManagerFactory
                            .getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    tmf.init(ts);

                    ks.load(ksFileIS, keystorePassword.toCharArray());
                    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                    kmf.init(ks, keystorePassword.toCharArray());
                    sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

                    return sslContext;
                }
            }
        }
    }

    private final String hostname = "localhost";
    private static final int port = 2601;

    private EventLoop eventLoop;
    private Thread eventLoopThread;

    private ExecutorService executorService;
    private final List<byte[]> serverMessageList = new LinkedList<>();

    @BeforeAll
    public void init() {

        final Consumer<FrameContext> cbFunction = (frame) -> serverMessageList
                .add(frame.relpFrame().payload().toBytes());

        Assertions.assertAll(() -> {
            SSLContext sslContext = SSLContextFactory
                    .authenticatedContext("src/test/resources/tls/keystore-server.jks", "changeit", "TLSv1.3");

            Function<SSLContext, SSLEngine> sslEngineFunction = sslContext1 -> {
                SSLEngine sslEngine = sslContext1.createSSLEngine();
                sslEngine.setUseClientMode(false);
                return sslEngine;
            };

            EventLoopFactory eventLoopFactory = new EventLoopFactory();
            Assertions.assertAll(() -> eventLoop = eventLoopFactory.create());

            eventLoopThread = new Thread(eventLoop);
            eventLoopThread.start();

            executorService = Executors.newSingleThreadExecutor();

            ServerFactory serverFactory = new ServerFactory(
                    eventLoop,
                    executorService,
                    new TLSFactory(sslContext, sslEngineFunction),
                    () -> new DefaultFrameDelegate(cbFunction)
            );

            Assertions.assertAll(() -> serverFactory.create(port));
        });
    }

    @AfterAll
    public void cleanup() {
        eventLoop.stop();
        executorService.shutdown();
        Assertions.assertAll(eventLoopThread::join);
    }

    @Test
    public void testTlsClient() {
        Assertions.assertAll(() -> {

            SSLContext sslContext = InternalSSLContextFactory
                    .authenticatedContext(
                            "src/test/resources/tls/keystore-client.jks", "src/test/resources/tls/truststore.jks",
                            "changeit", "changeit", "TLSv1.3"
                    );

            Supplier<SSLEngine> sslEngineSupplier = sslContext::createSSLEngine;

            RelpConnection relpSession = new RelpConnection(sslEngineSupplier);

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
        });
    }
}
