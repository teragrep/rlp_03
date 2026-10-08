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
package com.teragrep.rlp_03.tls;

import com.teragrep.net_01.channel.context.ConnectContextFactory;
import com.teragrep.net_01.channel.socket.TLSFactory;
import com.teragrep.net_01.eventloop.EventLoop;
import com.teragrep.net_01.eventloop.EventLoopFactory;
import com.teragrep.rlp_03.client.RelpClient;
import com.teragrep.rlp_03.client.RelpClientFactory;
import com.teragrep.rlp_03.frame.FrameDelegationClockFactory;
import com.teragrep.rlp_03.frame.RelpFrame;
import com.teragrep.rlp_03.frame.RelpFrameFactory;
import com.teragrep.rlp_03.frame.delegate.DefaultFrameDelegate;
import com.teragrep.net_01.server.ServerFactory;
import org.junit.jupiter.api.*;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * Tests TLS connections using RLP_03 RelpClient
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public final class TlsRelpClientTest {

    private EventLoop eventLoop;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final File keyStoreFile = Paths.get("src/test/resources/tls/keystore-client.jks").toFile();
    private final File trustStoreFile = Paths.get("src/test/resources/tls/truststore.jks").toFile();
    private final String keystorePassword = "changeit";
    private final String truststorePassword = "changeit";
    private final String protocol = "TLSv1.3";
    private final ConcurrentLinkedDeque<byte[]> messageDeque = new ConcurrentLinkedDeque<>();
    private final int port = 2601;

    @BeforeAll
    public void init() {

        final EventLoopFactory eventLoopFactory = new EventLoopFactory();
        Assertions.assertDoesNotThrow(() -> eventLoop = eventLoopFactory.create());
        executorService.submit(eventLoop);

        final SSLContext sslContext = Assertions.assertDoesNotThrow(() -> SSLContext.getInstance(protocol));
        final KeyStore ks = Assertions.assertDoesNotThrow(() -> KeyStore.getInstance("JKS"));

        final FileInputStream fileInputStream = Assertions.assertDoesNotThrow(() -> new FileInputStream(keyStoreFile));
        Assertions.assertDoesNotThrow(() -> ks.load(fileInputStream, keystorePassword.toCharArray()));
        final TrustManagerFactory tmf = Assertions
                .assertDoesNotThrow(() -> TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()));
        Assertions.assertDoesNotThrow(() -> tmf.init(ks));
        final KeyManagerFactory kmf = Assertions
                .assertDoesNotThrow(() -> KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()));
        Assertions.assertDoesNotThrow(() -> kmf.init(ks, keystorePassword.toCharArray()));
        Assertions.assertDoesNotThrow(() -> sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null));
        Assertions.assertDoesNotThrow(fileInputStream::close);

        final Function<SSLContext, SSLEngine> sslEngineFunction = context -> {
            final SSLEngine engine = context.createSSLEngine();
            engine.setUseClientMode(false);
            return engine;
        };

        final ServerFactory serverFactory = new ServerFactory(
                eventLoop,
                executorService,
                new TLSFactory(sslContext, sslEngineFunction),
                new FrameDelegationClockFactory(() -> new DefaultFrameDelegate((frame) -> messageDeque.add(frame.relpFrame().payload().toBytes())))
        );
        Assertions.assertDoesNotThrow(() -> serverFactory.create(port));
    }

    @AfterAll
    public void cleanup() {
        eventLoop.stop();
        executorService.shutdown();
    }

    @AfterEach
    public void clearMessageList() {
        // clear received list
        messageDeque.clear();
    }

    /**
     * Should connect with TLS enabled client and receive configured number of messages successfully
     */
    @Test
    public void testMessageCount() {
        Assertions.assertAll(() -> {
            final long messageCount = 10000;
            // create TLS enabled client
            final SSLContext sslContext = SSLContext.getInstance(protocol);
            final KeyStore ks = KeyStore.getInstance("JKS");
            final KeyStore ts = KeyStore.getInstance("JKS");

            final FileInputStream ksFileIS = new FileInputStream(keyStoreFile);
            final FileInputStream tsFileIS = new FileInputStream(trustStoreFile);
            ts.load(tsFileIS, truststorePassword.toCharArray());
            final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ts);

            ks.load(ksFileIS, keystorePassword.toCharArray());
            final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, keystorePassword.toCharArray());
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

            tsFileIS.close();
            ksFileIS.close();

            final Function<SSLContext, SSLEngine> sslEngineFunction = context -> {
                final SSLEngine engine = context.createSSLEngine();
                engine.setUseClientMode(true);
                return engine;
            };
            final TLSFactory socketFactory = new TLSFactory(sslContext, sslEngineFunction);

            final ConnectContextFactory connectContextFactory = new ConnectContextFactory(
                    executorService,
                    socketFactory
            );
            final RelpClientFactory relpClientFactory = new RelpClientFactory(connectContextFactory, eventLoop);

            // establish connection
            final RelpFrameFactory relpFrameFactory = new RelpFrameFactory();
            final RelpClient relpClient = relpClientFactory
                    .open(new InetSocketAddress("localhost", port))
                    .get(3, TimeUnit.SECONDS);

            // send open
            final RelpFrame openFrame = relpFrameFactory.create("open", "a hallo yo client");
            final CompletableFuture<RelpFrame> open = relpClient.transmit(openFrame);
            open.get(3, TimeUnit.SECONDS);

            // send syslogs
            int i = 0;
            final String payload = "truckload of ducks";
            while (i < messageCount) {
                final RelpFrame syslogFrame = relpFrameFactory.create("syslog", payload);
                final CompletableFuture<RelpFrame> syslog = relpClient.transmit(syslogFrame);
                syslog.get(3, TimeUnit.SECONDS);
                i++;
            }
            Assertions.assertEquals(messageCount, i);

            // send close
            final RelpFrame closeFrame = relpFrameFactory.create("close", "");
            final CompletableFuture<RelpFrame> close = relpClient.transmit(closeFrame);
            close.get(3, TimeUnit.SECONDS);

            // assert that proper number of messages have been received
            Assertions.assertFalse(messageDeque.isEmpty());
            Assertions.assertEquals(messageCount, messageDeque.size());

            // received payloads should match
            for (byte[] message : messageDeque) {
                Assertions.assertEquals(payload, new String(message, StandardCharsets.UTF_8));
            }
        });
    }
}
