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
package com.teragrep.rlp_03.client;

import com.teragrep.rlp_03.*;
import com.teragrep.rlp_03.config.Config;
import com.teragrep.rlp_03.context.channel.PlainFactory;
import com.teragrep.rlp_03.context.channel.SocketFactory;
import com.teragrep.rlp_03.delegate.DefaultFrameDelegate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ClientTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientTest.class);
    private Server server;
    private final int port = 23601;

    @BeforeAll
    public void init() {
        Config config = new Config(port, 1);
        ServerFactory serverFactory = new ServerFactory(
                config,
                () -> new DefaultFrameDelegate((frame) -> LOGGER.debug("server got <[{}]>", frame.relpFrame()))
        );
        Assertions.assertAll(() -> {
            server = serverFactory.create();

            Thread serverThread = new Thread(server);
            serverThread.start();

            server.startup.waitForCompletion();
        });
    }

    private class RunnableEventLoop implements Runnable, Closeable {

        private final AtomicBoolean stop;
        private final EventLoop eventLoop;

        RunnableEventLoop(EventLoop eventLoop) {
            this.stop = new AtomicBoolean();
            this.eventLoop = eventLoop;
        }

        @Override
        public void close() throws IOException {
            stop.set(true);
            eventLoop.wakeup();
        }

        @Override
        public void run() {
            LOGGER.debug("running eventLoop <{}>", eventLoop);
            try {
                while (!stop.get()) {
                    eventLoop.poll();
                }
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
            finally {
                eventLoop.close();
            }
        }

        public EventLoop eventLoop() {
            return eventLoop;
        }
    }

    @Test
    public void testClient() throws IOException {
        // runs the client eventLoop (one may share eventLoop with a server too, or other clients)
        RunnableEventLoop runnableEventLoop = new RunnableEventLoop(new EventLoopFactory().create());
        Thread eventLoopThread = new Thread(runnableEventLoop);
        eventLoopThread.start();

        // client takes resources from this pool
        ExecutorService executorService = Executors.newCachedThreadPool();

        // client connection type
        SocketFactory socketFactory = new PlainFactory();

        ConnectContextFactory connectContextFactory = new ConnectContextFactory(executorService, socketFactory);
        ClientFactory clientFactory = new ClientFactory(connectContextFactory, runnableEventLoop.eventLoop());

        try (Client client = clientFactory.open(new InetSocketAddress("localhost", port))) {

            // send open
            CompletableFuture<AbstractMap.SimpleEntry<String, byte[]>> open = client
                    .transmit("open", "a hallo yo client".getBytes(StandardCharsets.UTF_8));

            // send syslog
            CompletableFuture<AbstractMap.SimpleEntry<String, byte[]>> syslog = client
                    .transmit("syslog", "yonnes payload".getBytes(StandardCharsets.UTF_8));

            // send close
            CompletableFuture<AbstractMap.SimpleEntry<String, byte[]>> close = client
                    .transmit("close", "".getBytes(StandardCharsets.UTF_8));

            // test open response
            AbstractMap.SimpleEntry<String, byte[]> openResponse = open.get();
            LOGGER.debug("openResponse <[{}]>", openResponse);
            Assertions.assertEquals("rsp", openResponse.getKey());
            Assertions
                    .assertEquals(
                            "200 OK\nrelp_version=0\nrelp_software=RLP-01,1.0.1,https://teragrep.com\ncommands=syslog\n",
                            new String(openResponse.getValue(), StandardCharsets.UTF_8)
                    );

            // test syslog response
            AbstractMap.SimpleEntry<String, byte[]> syslogResponse = syslog.get();
            Assertions.assertEquals("rsp", syslogResponse.getKey());
            LOGGER.debug("syslogResponse <[{}]>", syslogResponse);
            Assertions.assertEquals("200 OK", new String(syslogResponse.getValue(), StandardCharsets.UTF_8));

            // test close response
            AbstractMap.SimpleEntry<String, byte[]> closeResponse = close.get();
            Assertions.assertEquals("rsp", closeResponse.getKey());
            LOGGER.debug("closeResponse <[{}]>", closeResponse);
            Assertions.assertEquals("", new String(closeResponse.getValue(), StandardCharsets.UTF_8));

            // close the client eventLoop
            runnableEventLoop.close();
            eventLoopThread.join();
        }
        catch (InterruptedException | ExecutionException | IOException exception) {
            throw new RuntimeException(exception);
        }
    }
}
